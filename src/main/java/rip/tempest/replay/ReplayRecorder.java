package rip.tempest.replay;

import rip.tempest.Tempest;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.modules.impl.client.ReplayModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.util.BlockPos;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Records the game into a ReplayMod-style replay, as ReplayMod does: from the moment the server
 * accepts the login, a handler sitting just before Minecraft's packet decoder copies every packet
 * the server sends (after decompression, and after ViaMCP has turned it into the 1.8 protocol, so
 * a replay is always 1.8.9 whatever the server runs). The server never sends you your own
 * movement, so {@link OwnPlayerRecorder} writes that in as if it had.
 *
 * <p>Packets are given their time on the game thread, in the order the game handles them, so the
 * packets made up for your own player land in the right places among the server's. The recording
 * ends when the connection closes (or Replay is switched off) and is saved to Tempest/replays.
 *
 * <p>Based on ReplayMod (GPL-3.0-or-later): its PacketListener, ConnectionEventHandler and the
 * 1.8 paths of its recording mixins.
 */
@ChannelHandler.Sharable
public final class ReplayRecorder extends ChannelInboundHandlerAdapter {
    static final String RAW_KEY = "replay_recorder_raw";
    private static final String DECODER_KEY = "decoder";

    private static final int KEEP_ALIVE = 0x00, SET_COMPRESSION = 0x46, RESOURCE_PACK = 0x48, SPAWN_PLAYER = 0x0C;

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static volatile ReplayRecorder current;

    private final Channel channel;
    private final ReplayWriter writer;
    private final String name;
    final OwnPlayerRecorder self = new OwnPlayerRecorder(this);

    private final long startTime;
    private int lastTime;
    private long pausedTime;
    private boolean wasPaused;
    private volatile boolean finished;

    private ReplayRecorder(Channel channel, ReplayWriter writer, String name) {
        this.channel = channel;
        this.writer = writer;
        this.name = name;
        this.startTime = writer.meta.date;
    }

    public static File folder() {
        return new File(Tempest.NAME, "replays");
    }

    public static int recoverLeftovers() {
        File folder = folder();
        return folder.isDirectory() ? ReplayWriter.recoverLeftovers(folder) : 0;
    }

    public static boolean recording() {
        return current != null;
    }

    public static int elapsed() {
        ReplayRecorder recorder = current;
        return recorder == null ? 0 : recorder.lastTime;
    }

    public static void onLoginSuccess(NetworkManager connection, S02PacketLoginSuccess packet) {
        if (current != null || ReplayViewer.active() || !ReplayModule.active()) return;
        boolean local = connection.isLocalChannel();
        if (local ? !ReplayModule.recordSingleplayer() : !ReplayModule.recordMultiplayer()) return;

        try {
            ReplayWriter.Metadata meta = new ReplayWriter.Metadata();
            meta.singleplayer = local;
            meta.date = System.currentTimeMillis();
            meta.generator = Tempest.NAME;
            if (local) {
                String world = mc.getIntegratedServer() != null ? mc.getIntegratedServer().getWorldName() : "Singleplayer";
                meta.serverName = world;
                meta.customServerName = world;
            } else {
                ServerData server = mc.getCurrentServerData();
                if (server == null) return;
                meta.serverName = server.serverIP;
                if (server.serverName != null && !server.serverName.isEmpty() && !"Minecraft Server".equals(server.serverName)) {
                    meta.customServerName = server.serverName;
                }
            }

            File folder = folder();
            if (!folder.isDirectory()) folder.mkdirs();
            String name = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
            ReplayWriter writer = new ReplayWriter(folder, name, meta);
            Channel channel = connection.getChannel();
            ReplayRecorder recorder = new ReplayRecorder(channel, writer, name);

            if (channel.pipeline().get(DECODER_KEY) != null) {
                channel.pipeline().addBefore(DECODER_KEY, RAW_KEY, recorder);
            } else {
                channel.pipeline().addFirst(RAW_KEY, recorder);
            }
            current = recorder;
            recorder.save(encode(EnumConnectionState.LOGIN, packet));
            mc.addScheduledTask(() -> NotificationManager.post(NotificationType.INFO, "Replay", "Recording started"));
        } catch (Exception e) {
            e.printStackTrace();
            mc.addScheduledTask(() -> NotificationManager.post(NotificationType.WARNING, "Replay", "Couldn't start recording"));
        }
    }

    public static void onPipelineChanged(Channel channel) {
        ReplayRecorder recorder = current;
        if (recorder == null || recorder.channel != channel || channel.pipeline().get(DECODER_KEY) == null) return;
        java.util.List<String> names = channel.pipeline().names();
        if (names.indexOf(RAW_KEY) + 1 != names.indexOf(DECODER_KEY)) {
            channel.pipeline().remove(RAW_KEY);
            channel.pipeline().addBefore(DECODER_KEY, RAW_KEY, recorder);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!finished) {
            try {
                byte[] data = null;
                if (msg instanceof ByteBuf buf) {
                    if (buf.readableBytes() > 0) {
                        data = new byte[buf.readableBytes()];
                        buf.getBytes(buf.readerIndex(), data);
                    }
                } else if (msg instanceof Packet<?> packet) {
                    EnumConnectionState state = ctx.channel().attr(NetworkManager.attrKeyConnectionState).get();
                    data = encode(state != null ? state : EnumConnectionState.PLAY, packet);
                }
                if (data != null) incoming(data);
            } catch (Exception e) {
            }
        }
        super.channelRead(ctx, msg);
    }

    private void incoming(byte[] data) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
        int id = buffer.readVarIntFromBuffer();
        if (id == KEEP_ALIVE || id == SET_COMPRESSION || id == RESOURCE_PACK) return;
        if (id == SPAWN_PLAYER) {
            buffer.readVarIntFromBuffer();
            notePlayer(buffer.readUuid());
        }
        save(data);
    }

    private void notePlayer(UUID uuid) {
        synchronized (writer.meta) {
            Set<String> players = new LinkedHashSet<>(Arrays.asList(writer.meta.players));
            if (!players.add(uuid.toString())) return;
            writer.meta.players = players.toArray(new String[0]);
        }
        writer.saveMetadata();
    }

    void save(Packet<?> packet) {
        try {
            save(encode(EnumConnectionState.PLAY, packet));
        } catch (Exception e) {
        }
    }

    private void save(byte[] data) {
        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(() -> save(data));
            return;
        }
        if (finished) return;
        long now = System.currentTimeMillis();
        if (wasPaused) {
            pausedTime = now - startTime - lastTime;
            wasPaused = false;
        }
        int time = (int) (now - startTime - pausedTime);
        lastTime = time;
        writer.write(time, data);
    }

    static byte[] encode(EnumConnectionState state, Packet<?> packet) throws Exception {
        Integer id = state.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
        if (id == null) throw new IllegalArgumentException("not a " + state + " packet: " + packet.getClass().getSimpleName());
        ByteBuf buf = Unpooled.buffer();
        try {
            PacketBuffer buffer = new PacketBuffer(buf);
            buffer.writeVarIntToBuffer(id);
            packet.writePacketData(buffer);
            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), data);
            return data;
        } finally {
            buf.release();
        }
    }

    public static void onPlayerListItem(S38PacketPlayerListItem packet, java.util.Map<UUID, ?> known) {
        ReplayRecorder recorder = current;
        if (recorder == null || mc.thePlayer == null || packet.getAction() != S38PacketPlayerListItem.Action.ADD_PLAYER) return;
        UUID self = mc.thePlayer.getGameProfile().getId();
        for (S38PacketPlayerListItem.AddPlayerData entry : packet.getEntries()) {
            if (entry.getProfile().getId().equals(self) && !known.containsKey(self)) recorder.self.spawn();
        }
    }

    public static void onRespawn() {
        ReplayRecorder recorder = current;
        if (recorder != null) recorder.self.spawn(false);
    }

    public static void onBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
        ReplayRecorder recorder = current;
        if (recorder != null) recorder.self.blockBreak(breakerId, pos, progress);
    }

    public static void onTick() {
        ReplayRecorder recorder = current;
        if (recorder == null) return;
        if (mc.isSingleplayer() && mc.isGamePaused()) recorder.wasPaused = true;
        recorder.self.tick();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        stop();
        super.channelInactive(ctx);
    }

    public static void stopRecording() {
        ReplayRecorder recorder = current;
        if (recorder == null) return;
        recorder.channel.eventLoop().execute(() -> {
            if (recorder.channel.pipeline().get(RAW_KEY) != null) recorder.channel.pipeline().remove(RAW_KEY);
        });
        recorder.stop();
    }

    private synchronized void stop() {
        if (finished) return;
        finished = true;
        if (current == this) current = null;
        int duration = lastTime;
        Thread saver = new Thread(() -> {
            try {
                File file = writer.finish(duration);
                mc.addScheduledTask(() -> NotificationManager.post(NotificationType.SUCCESS, "Replay", "Saved " + file.getName()));
            } catch (Exception e) {
                e.printStackTrace();
                mc.addScheduledTask(() -> NotificationManager.post(NotificationType.WARNING, "Replay", "Couldn't save " + name));
            }
        }, "Replay saver");
        saver.setDaemon(false);
        saver.start();
    }
}
