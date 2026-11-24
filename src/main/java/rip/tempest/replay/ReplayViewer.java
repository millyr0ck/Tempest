package rip.tempest.replay;

import rip.tempest.Tempest;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.modules.Module;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.modules.impl.client.ReplayModule;
import rip.tempest.modules.impl.visuals.FreeLookModule;
import rip.tempest.modules.impl.visuals.FreecamModule;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plays a replay back, as ReplayMod does: a pretend connection (an embedded channel that drops
 * everything the game sends) feeds the recorded packets to the game's own network handler, so
 * the world is rebuilt exactly as it arrived. You join as an invisible spectator camera with an
 * id no server uses; your recorded self is just another player in the world.
 *
 * <p>Unlike ReplayMod, packets are sent from the game thread, at the start of each frame, up to the
 * replay's current time, so there's no sender thread to keep in step. The replay's clock follows
 * the game's tick timer, whose speed is the playback speed (0 while paused, which freezes the
 * world). Input is read every frame through an input-only game tick, so the camera and the keys
 * work at any speed. Going back in time starts the replay again and runs through to that point.
 *
 * <p>Based on ReplayMod (GPL-3.0-or-later): ReplayHandler, FullReplaySender, CameraEntity,
 * ClassicCameraController and InputReplayTimer.
 */
public final class ReplayViewer {
    static final int CAMERA_ID = -1789435;

    private static final double TELEPORT_LIMIT = 128;

    private static final int SKIP = 5000;

    private static final int TERRAIN_SCREEN_LIMIT = 1000;

    private static final double CHASE_DISTANCE = 3.0, CHASE_RISE = 0.4;
    private static final double CHASE_GLIDE = 550, CHASE_LIMIT = 15000, CHASE_BACK_MIN = 300, CHASE_BACK_MAX = 800, CHASE_BACK_PER_BLOCK = 8;

    public static final float[] SPEEDS = {0.1f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 8f};
    private static final int NORMAL_SPEED = 4;
    static final float CAMERA_SPEED_MIN = 0.25f, CAMERA_SPEED_MAX = 200f;

    private static final Set<String> CLIENT_KEPT = Set.of("Client Settings", "Discord RPC", "Hit Sounds", "Anti Bot",
            "Murder Detector", "No Crash", "Replay", "Weed Hack Mode");

    private static final Set<Class<?>> DROPPED = new HashSet<>(List.of(
            S00PacketKeepAlive.class, S06PacketUpdateHealth.class, S09PacketHeldItemChange.class,
            S1FPacketSetExperience.class, S2DPacketOpenWindow.class, S2EPacketCloseWindow.class,
            S2FPacketSetSlot.class, S30PacketWindowItems.class, S31PacketWindowProperty.class,
            S32PacketConfirmTransaction.class, S36PacketSignEditorOpen.class, S37PacketStatistics.class,
            S39PacketPlayerAbilities.class, S3APacketTabComplete.class, S40PacketDisconnect.class,
            S43PacketCamera.class, S46PacketSetCompressionLevel.class, S48PacketResourcePackSend.class));

    private static final Set<Integer> DROPPED_IDS = new HashSet<>();

    static {
        for (Class<?> type : DROPPED) {
            try {
                Integer id = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, (Packet<?>) type.getDeclaredConstructor().newInstance());
                if (id != null) DROPPED_IDS.add(id);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static final Set<Class<?>> NOISY = Set.of(S29PacketSoundEffect.class, S28PacketEffect.class,
            S2APacketParticles.class, S27PacketExplosion.class, S45PacketTitle.class);

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static ReplayViewer current;

    public static boolean inputOnly;

    final ReplayFile replay;
    final int duration;
    final String title;

    private EmbeddedChannel channel;
    private NetworkManager connection;
    private ReplayFile.Packets packets;
    private ReplayFile.Packet next;
    private EnumConnectionState state;

    private int sentTime, joinTime = -1;
    private boolean ended;

    private double time;
    private boolean paused;
    private int speed = NORMAL_SPEED;
    private float lastPartial;
    private boolean starting = true, closing;
    private int pendingJump = -1;

    private boolean allowMovement;

    private boolean hurrying;
    private final List<Module> stoodDown = new ArrayList<>();
    private final Set<Class<?>> failedPackets = new HashSet<>();

    private float cameraSpeed = 10f;
    private double velocityX, velocityY, velocityZ;
    private long lastFrame = System.nanoTime();
    long cameraSpeedChanged;
    private Entity spectating;

    private int selfId = Integer.MIN_VALUE;
    private UUID selfUuid;

    private boolean followingSelf;

    private Entity chasing, chaseReturn;
    private boolean chaseFresh;
    private double chaseStarted, chaseLanded = -1, chaseBack = -1, chaseBackLength;
    private double chaseDirX, chaseDirY, chaseDirZ;

    private double fromX, fromY, fromZ, poseX, poseY, poseZ;
    private float fromYaw, fromPitch, poseYaw, posePitch;
    private long lastPose = System.nanoTime();

    private final Set<Integer> seenProjectiles = new HashSet<>();

    final CameraPath path;
    long keyframeAdded;
    int keyframeIndex;

    private CameraPath.Sample pendingCamera;

    private long previewStart = -1;
    private boolean previewPending;
    private double previewSpeed;
    private boolean renderPending, rendering;

    File lastRender;
    String renderProblem;

    private ReplayViewer(ReplayFile replay) {
        this.replay = replay;

        this.duration = replay.meta.duration > 0 ? replay.meta.duration : replay.lastPacketTime();
        String name = replay.file.getName();
        this.title = name.endsWith(".mcpr") ? name.substring(0, name.length() - 5) : name;
        this.path = CameraPath.load(replay.file);
    }

    public static boolean active() {
        return current != null;
    }

    public static ReplayViewer current() {
        return current;
    }

    public static void open(File file) {
        if (current != null || mc.theWorld != null) return;
        ReplayFile replay;
        try {
            replay = ReplayFile.open(file);
        } catch (Exception e) {
            e.printStackTrace();
            NotificationManager.post(NotificationType.WARNING, "Replay", "Couldn't open " + file.getName());
            return;
        }
        ReplayWriter.Metadata meta = replay.meta;
        boolean oneEight = meta.protocol == 47 || (meta.protocol == 0 && meta.mcversion != null && meta.mcversion.startsWith("1.8"));
        if (!oneEight) {
            closeQuietly(replay);
            NotificationManager.post(NotificationType.WARNING, "Replay", "Recorded on Minecraft " + meta.mcversion + ", not 1.8");
            return;
        }
        ReplayViewer viewer = new ReplayViewer(replay);
        current = viewer;
        viewer.standDown();
        mc.displayGuiScreen(new ReplayLoadingScreen(viewer.title));
    }

    public void exit() {
        if (current != this || closing) return;
        closing = true;
        if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
        else if (connection != null) connection.closeChannel(new ChatComponentText("Quitting"));
        mc.loadWorld(null);
        cleanUp();
        mc.displayGuiScreen(new rip.tempest.interfaces.menu.replay.GuiReplays());
    }

    private void cleanUp() {
        if (current == this) current = null;
        if (paused) mc.getSoundHandler().resumeSounds();
        mc.timer.timerSpeed = 1f;
        if (channel != null && channel.isOpen()) channel.close();
        closeQuietly(packets);
        closeQuietly(replay);
        standUp();
    }

    private void fail(String message, Exception e) {
        if (e != null) e.printStackTrace();
        NotificationManager.post(NotificationType.WARNING, "Replay", message);
        try {
            exit();
        } catch (Exception again) {
            again.printStackTrace();
            cleanUp();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (IOException ignored) {
        }
    }

    public static boolean blocks(Module module) {
        return current != null && !kept(module);
    }

    private static boolean kept(Module module) {
        return switch (module.getCategory()) {
            case VISUALS -> !(module instanceof FreecamModule) && !(module instanceof FreeLookModule);
            case CLIENT -> CLIENT_KEPT.contains(module.getLabel());
            case CONFIGS -> true;
            default -> false;
        };
    }

    private void standDown() {
        for (Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (module.isEnabled() && (!kept(module) || ReplayModule.hidesInReplays(module))) {
                stoodDown.add(module);
                toggle(module, false);
            }
        }
    }

    private void standUp() {
        for (Module module : stoodDown) toggle(module, true);
        stoodDown.clear();
    }

    private static void toggle(Module module, boolean on) {
        try {
            module.setEnabled(on);
        } catch (Exception e) {
            System.err.println("[Replay] " + module.getLabel() + " threw while turning " + (on ? "on" : "off") + ": " + e);
        }
    }

    @ChannelHandler.Sharable
    private static final class DropOutbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            promise.trySuccess();
        }
    }

    private static final DropOutbound DROP_OUTBOUND = new DropOutbound();

    private void connect() throws IOException {
        closeQuietly(packets);
        packets = replay.packets();
        next = null;
        sentTime = 0;
        joinTime = -1;
        ended = false;

        channel = new EmbeddedChannel(DROP_OUTBOUND);
        connection = new NetworkManager(EnumPacketDirection.CLIENTBOUND) {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                cause.printStackTrace();
            }
        };
        connection.setNetHandler(new NetHandlerLoginClient(connection, mc, null));
        channel.pipeline().addLast("packet_handler", connection);
        channel.pipeline().fireChannelActive();
        connection.setConnectionState(EnumConnectionState.LOGIN);
        state = EnumConnectionState.LOGIN;

        if (replay.meta.fileFormatVersion < 14) {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("ReplayCamera".getBytes(StandardCharsets.UTF_8)), "Camera");
            process(new S02PacketLoginSuccess(profile));
            state = EnumConnectionState.PLAY;
        }
    }

    private void restart() throws IOException {
        if (connection != null) connection.closeChannel(new ChatComponentText("Rewinding"));
        spectating = null;
        seenProjectiles.clear();

        mc.displayGuiScreen(new ReplayLoadingScreen(title));
        mc.loadWorld(null);
        connect();
    }

    private ReplayFile.Packet peek() throws IOException {
        if (next == null && !ended) {
            next = packets.next();
            if (next == null) ended = true;
        }
        return next;
    }

    private void send(ReplayFile.Packet packet) {
        next = null;
        sentTime = packet.time();
        Packet<?> decoded;
        try {
            decoded = decode(packet.data());
        } catch (Exception e) {
            return;
        }
        if (decoded == null) return;
        if (state == EnumConnectionState.LOGIN) {
            if (decoded instanceof S02PacketLoginSuccess login) {
                selfUuid = login.getProfile().getId();
                process(decoded);
                state = EnumConnectionState.PLAY;
            }
            return;
        }
        decoded = filter(decoded);
        if (decoded != null) process(decoded);
    }

    private Packet<?> decode(byte[] data) throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
        int id = buffer.readVarIntFromBuffer();
        if (state == EnumConnectionState.PLAY && DROPPED_IDS.contains(id)) return null;
        Packet<?> packet = state.getPacket(EnumPacketDirection.CLIENTBOUND, id);
        if (packet != null) packet.readPacketData(buffer);
        return packet;
    }

    @SuppressWarnings("unchecked")
    private void process(Packet<?> packet) {
        INetHandler handler = connection.getNetHandler();
        try {
            ((Packet<INetHandler>) packet).processPacket(handler);
        } catch (ThreadQuickExitException ignored) {
        } catch (Exception e) {
            if (failedPackets.add(packet.getClass())) {
                System.err.println("[Replay] " + packet.getClass().getSimpleName() + " failed:");
                e.printStackTrace();
            }
        }
    }

    private Packet<?> filter(Packet<?> packet) {
        if (packet instanceof S01PacketJoinGame join) {
            allowMovement = true;
            joinTime = sentTime;
            selfId = join.getEntityId();
            return new S01PacketJoinGame(CAMERA_ID, WorldSettings.GameType.SPECTATOR, false, join.getDimension(),
                    join.getDifficulty(), join.getMaxPlayers(), join.getWorldType(), false);
        }
        if (packet instanceof S07PacketRespawn respawn) {
            allowMovement = true;
            return new S07PacketRespawn(respawn.getDimensionID(), respawn.getDifficulty(), respawn.getWorldType(), WorldSettings.GameType.SPECTATOR);
        }
        if (packet instanceof S08PacketPlayerPosLook look) {
            teleported(look);
            return null;
        }
        if (packet instanceof S2BPacketChangeGameState change) {
            int reason = change.getGameState();
            return reason == 1 || reason == 2 || reason == 7 || reason == 8 ? packet : null;
        }
        if (packet instanceof S0CPacketSpawnPlayer spawn && connection != null && connection.getNetHandler() instanceof NetHandlerPlayClient handler
                && handler.getPlayerInfo(spawn.getPlayer()) == null) {
            return null;
        }
        if (packet instanceof S3FPacketCustomPayload payload) {
            return "MC|Brand".equals(payload.getChannelName()) ? packet : null;
        }
        if (DROPPED.contains(packet.getClass())) return null;
        if (hurrying && NOISY.contains(packet.getClass())) return null;
        return packet;
    }

    private void teleported(S08PacketPlayerPosLook look) {
        if (mc.currentScreen instanceof GuiDownloadTerrain) mc.displayGuiScreen(null);
        Set<S08PacketPlayerPosLook.EnumFlags> flags = look.func_179834_f();
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X) || flags.contains(S08PacketPlayerPosLook.EnumFlags.Y)
                || flags.contains(S08PacketPlayerPosLook.EnumFlags.Z)) return;
        CameraEntity camera = camera();
        if (camera == null) return;
        boolean far = Math.abs(camera.posX - look.getX()) > TELEPORT_LIMIT || Math.abs(camera.posZ - look.getZ()) > TELEPORT_LIMIT;
        if (!allowMovement && !far) return;
        allowMovement = false;
        if (spectating != null) return;
        camera.moveTo(look.getX(), look.getY(), look.getZ());
        boolean relativeTurn = flags.contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT) || flags.contains(S08PacketPlayerPosLook.EnumFlags.X_ROT);
        if (!relativeTurn) camera.turnTo(look.getYaw(), look.getPitch());
        velocityX = velocityY = velocityZ = 0;
    }

    static CameraEntity camera() {
        return mc.thePlayer instanceof CameraEntity camera ? camera : null;
    }

    private boolean worldReady() {
        return mc.theWorld != null && mc.thePlayer != null && !(mc.currentScreen instanceof GuiDownloadTerrain);
    }

    private void closeTerrainScreen(int now) {
        if (mc.currentScreen instanceof GuiDownloadTerrain && mc.theWorld != null && joinTime >= 0 && now > joinTime + TERRAIN_SCREEN_LIMIT) {
            mc.displayGuiScreen(null);
        }
    }

    public static void onFrame() {
        ReplayViewer viewer = current;
        if (viewer == null) return;
        try {
            viewer.frame();
        } catch (Exception e) {
            viewer.fail("Replay stopped: " + e.getClass().getSimpleName(), e);
        }
    }

    private void frame() throws IOException {
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        if (starting) {
            starting = false;
            connect();
            catchUp(0, "Opening " + title);
            afterJump();
            if (!worldReady()) {
                fail("This replay has no world in it", null);
                return;
            }
            lastPartial = mc.timer.renderPartialTicks;
            applyTimer();
            followingSelf = ReplayModule.followYou.getValue();
            return;
        }
        if (mc.theWorld == null && !closing) {
            cleanUp();
            return;
        }
        if (renderPending) {
            renderPending = false;
            render();
            return;
        }
        if (pendingJump >= 0) {
            int target = pendingJump;
            pendingJump = -1;
            jump(target);
            if (current != this) return;
        }
        if (previewPending) {
            previewPending = false;
            previewStart = now;
            setPaused(false);
        }

        float progress = mc.timer.elapsedTicks + mc.timer.renderPartialTicks - lastPartial;
        lastPartial = mc.timer.renderPartialTicks;
        CameraPath.Sample sample = null;
        if (previewStart >= 0) {
            double along = (now - previewStart) / 1e9;
            if (along >= path.duration()) {
                stopPreview();
            } else {
                sample = path.sample(along);
                time = Math.max(time, sample.time());
                previewSpeed = sample.speed();
            }
        } else if (!paused) {
            time += progress * 50.0;
        }
        if (time >= duration) {
            time = duration;
            if (!paused) setPaused(true);
        }
        sendUntil((int) time);
        if (current != this) return;
        closeTerrainScreen((int) time);

        if (mc.theWorld != null && mc.thePlayer != null) {
            inputOnly = true;
            try {
                mc.runTick();
            } finally {
                inputOnly = false;
            }
        }
        if (current != this) return;
        if (sample != null) {
            CameraEntity camera = camera();
            if (camera != null) {
                camera.moveTo(sample.x(), sample.y(), sample.z());
                camera.turnTo(sample.yaw(), sample.pitch());
            }
        } else {
            updateCamera(seconds);
        }
        applyTimer();
    }

    void sendUntil(int target) throws IOException {
        while (current == this && !closing) {
            ReplayFile.Packet packet = peek();
            if (packet == null || packet.time() > target) return;
            send(packet);
        }
    }

    private void catchUp(int target, String message) throws IOException {
        hurrying = true;
        long started = System.currentTimeMillis(), lastDrawn = 0L;
        int from = sentTime;
        try {
            while (current == this && !closing) {
                ReplayFile.Packet packet = peek();
                if (packet == null) return;
                if (packet.time() > target && worldReady()) return;
                send(packet);
                closeTerrainScreen(packet.time());
                long now = System.currentTimeMillis();
                if (now - started > 150L && now - lastDrawn > 33L) {
                    lastDrawn = now;
                    float done = target > from ? (sentTime - from) / (float) (target - from) : 1f;
                    ReplayLoadingScreen.drawFrame(message, clock(sentTime) + " / " + clock(target), done);
                }
            }
        } finally {
            hurrying = false;
        }
    }

    private void applyTimer() {
        mc.timer.timerSpeed = previewStart >= 0 ? (float) previewSpeed : paused ? 0f : SPEEDS[speed];
    }

    public int time() {
        return (int) time;
    }

    public int shownTime() {
        return pendingJump >= 0 ? pendingJump : (int) time;
    }

    public String server() {
        ReplayWriter.Metadata meta = replay.meta;
        if (meta.customServerName != null && !meta.customServerName.isEmpty()) return meta.customServerName;
        if (meta.serverName != null && !meta.serverName.isEmpty()) return meta.serverName;
        return meta.singleplayer ? "Singleplayer" : title;
    }

    public long date() {
        return replay.meta.date > 0 ? replay.meta.date : replay.file.lastModified();
    }

    public int duration() {
        return duration;
    }

    public boolean paused() {
        return paused;
    }

    public float speed() {
        return SPEEDS[speed];
    }

    public void togglePause() {
        if (paused && time >= duration) {
            requestJump(0);
            setPaused(false);
            return;
        }
        setPaused(!paused);
    }

    private void setPaused(boolean paused) {
        if (this.paused == paused) return;
        this.paused = paused;
        if (paused) mc.getSoundHandler().pauseSounds();
        else mc.getSoundHandler().resumeSounds();
        applyTimer();
    }

    public void changeSpeed(int steps) {
        speed = Math.max(0, Math.min(SPEEDS.length - 1, speed + steps));
        applyTimer();
    }

    public void requestJump(int target) {
        pendingJump = Math.max(0, Math.min(duration, target));
    }

    void requestJump(int target, CameraPath.Sample camera) {
        requestJump(target);
        pendingCamera = camera;
    }

    public void skip(int millis) {
        requestJump((pendingJump >= 0 ? pendingJump : (int) time) + millis);
    }

    void seek(int target) throws IOException {
        jump(Math.max(0, Math.min(duration, target)));
    }

    void renderedAt(double time) {
        this.time = time;
    }

    private void jump(int target) throws IOException {
        net.minecraft.client.gui.GuiScreen before = mc.currentScreen;
        boolean keepScreen = before instanceof ReplayScreen || before instanceof PathPreviewScreen;
        CameraEntity camera = camera();
        double x = 0, y = 0, z = 0;
        float yaw = 0, pitch = 0;
        Entity follow = spectating != null ? spectating : chasing != null ? chaseReturn : null;
        int dimension = Integer.MIN_VALUE, followed = follow != null ? follow.getEntityId() : Integer.MIN_VALUE;
        clearChase();
        if (camera != null) {
            x = camera.posX;
            y = camera.posY;
            z = camera.posZ;
            yaw = camera.rotationYaw;
            pitch = camera.rotationPitch;
            dimension = camera.dimension;
        }

        if (target < sentTime) restart();
        catchUp(target, "Jumping to " + clock(target));
        if (current != this) return;
        time = Math.max(target, 0);
        afterJump();

        CameraEntity now = camera();
        CameraPath.Sample place = pendingCamera;
        pendingCamera = null;
        if (now != null && place != null) {
            spectating = null;
            mc.setRenderViewEntity(now);
            now.moveTo(place.x(), place.y(), place.z());
            now.turnTo(place.yaw(), place.pitch());
            velocityX = velocityY = velocityZ = 0;
        } else if (now != null && camera != null && now.dimension == dimension) {
            now.moveTo(x, y, z);
            now.turnTo(yaw, pitch);
            if (followed != Integer.MIN_VALUE) {
                Entity entity = mc.theWorld.getEntityByID(followed);
                if (entity != null && entity != now) spectate(entity);
            }
        }
        if (keepScreen && mc.currentScreen != before) mc.displayGuiScreen(before);
        if (time >= duration) setPaused(true);
    }

    private void afterJump() {
        lastPartial = mc.timer.renderPartialTicks;
        if (mc.theWorld == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof CameraEntity) continue;
            if (entity instanceof EntityLivingBase living) {
                living.skipInterpolation();
                living.prevRotationYawHead = living.rotationYawHead;
                living.prevRenderYawOffset = living.renderYawOffset;
            }
            entity.lastTickPosX = entity.prevPosX = entity.posX;
            entity.lastTickPosY = entity.prevPosY = entity.posY;
            entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
            entity.prevRotationYaw = entity.rotationYaw;
            entity.prevRotationPitch = entity.rotationPitch;
            if (isProjectile(entity)) seenProjectiles.add(entity.getEntityId());
        }
    }

    public static String clock(int millis) {
        int seconds = Math.max(0, millis) / 1000;
        int hours = seconds / 3600, minutes = seconds / 60 % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds % 60) : String.format("%d:%02d", minutes, seconds % 60);
    }

    public int addKeyframe() {
        CameraEntity camera = camera();
        if (camera == null || rendering) return -1;
        float yaw = spectating != null ? spectating.rotationYaw : camera.rotationYaw;
        float pitch = spectating != null ? spectating.rotationPitch : camera.rotationPitch;
        keyframeIndex = path.add(shownTime(), camera.posX, camera.posY, camera.posZ, yaw, pitch);
        keyframeAdded = System.currentTimeMillis();
        return keyframeIndex;
    }

    public int updateKeyframe(int index) {
        CameraEntity camera = camera();
        if (camera == null) return index;
        float yaw = spectating != null ? spectating.rotationYaw : camera.rotationYaw;
        float pitch = spectating != null ? spectating.rotationPitch : camera.rotationPitch;
        return path.replace(index, shownTime(), camera.posX, camera.posY, camera.posZ, yaw, pitch);
    }

    public void goToKeyframe(int index) {
        if (index < 0 || index >= path.size()) return;
        CameraPath.Keyframe keyframe = path.get(index);
        requestJump(keyframe.time, new CameraPath.Sample(keyframe.time, keyframe.x, keyframe.y, keyframe.z, keyframe.yaw, keyframe.pitch, 0));
    }

    public void startPreview() {
        if (path.size() < 2 || rendering) return;
        stopSpectating();
        CameraPath.Sample first = path.sample(0);
        requestJump((int) first.time(), first);
        previewPending = true;
        mc.displayGuiScreen(new PathPreviewScreen());
    }

    public void stopPreview() {
        if (previewStart < 0 && !previewPending) return;
        previewStart = -1;
        previewPending = false;
        setPaused(true);
        applyTimer();
        if (mc.currentScreen == null || mc.currentScreen instanceof PathPreviewScreen) mc.displayGuiScreen(new ReplayScreen());
    }

    public boolean previewing() {
        return previewStart >= 0 || previewPending;
    }

    public double previewSeconds() {
        return previewStart >= 0 ? Math.min(path.duration(), (System.nanoTime() - previewStart) / 1e9) : 0;
    }

    public boolean rendering() {
        return rendering;
    }

    public void requestRender() {
        if (path.size() >= 2 && !previewing() && !rendering) renderPending = true;
    }

    private void render() {
        rendering = true;
        renderProblem = null;
        try {
            File made = new VideoRenderer(this, path).run();
            if (made != null) {
                lastRender = made;
                NotificationManager.post(NotificationType.SUCCESS, "Replay", "Saved " + made.getName());
            } else {
                NotificationManager.post(NotificationType.INFO, "Replay", "Render stopped");
            }
        } catch (Exception e) {
            e.printStackTrace();
            renderProblem = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            NotificationManager.post(NotificationType.WARNING, "Replay", "Render failed: " + renderProblem);
        } finally {
            rendering = false;
            if (current == this && mc.theWorld != null) {
                lastPartial = mc.timer.renderPartialTicks;
                lastFrame = System.nanoTime();
                setPaused(true);
                applyTimer();
                mc.displayGuiScreen(new ReplayScreen());
            }
        }
    }

    public static String preciseClock(int millis) {
        int tenths = Math.max(0, millis) / 100;
        return clock(tenths * 100) + "." + tenths % 10;
    }

    public float cameraSpeed() {
        return cameraSpeed;
    }

    public Entity spectating() {
        return spectating;
    }

    private void updateCamera(float seconds) {
        CameraEntity camera = camera();
        if (camera == null) return;

        noticeProjectiles();
        if (chasing != null) {
            chaseStages();
            velocityX = velocityY = velocityZ = 0;
            return;
        }

        if (followingSelf && spectating == null) {
            Entity you = self();
            if (you != null) spectate(you);
        }

        if (spectating != null) {
            boolean gone = spectating.isDead || mc.theWorld.getEntityByID(spectating.getEntityId()) != spectating
                    || mc.getRenderViewEntity() != spectating;
            if (gone) {
                spectating = null;
                mc.setRenderViewEntity(camera);
            } else {
                camera.moveTo(spectating.posX, spectating.posY + spectating.getEyeHeight() - camera.getEyeHeight(), spectating.posZ);
                velocityX = velocityY = velocityZ = 0;
                return;
            }
        }

        GameSettings settings = mc.gameSettings;
        float forward = 0, strafe = 0, up = 0;
        if (mc.currentScreen == null && mc.inGameHasFocus) {
            if (settings.keyBindForward.isKeyDown()) forward++;
            if (settings.keyBindBack.isKeyDown()) forward--;
            if (settings.keyBindLeft.isKeyDown()) strafe++;
            if (settings.keyBindRight.isKeyDown()) strafe--;
            if (settings.keyBindJump.isKeyDown()) up++;
            if (settings.keyBindSneak.isKeyDown()) up--;
        }

        double yaw = Math.toRadians(camera.rotationYaw), pitch = Math.toRadians(camera.rotationPitch);
        double dx = -Math.sin(yaw) * Math.cos(pitch) * forward + Math.cos(yaw) * strafe;
        double dy = -Math.sin(pitch) * forward + up;
        double dz = Math.cos(yaw) * Math.cos(pitch) * forward + Math.sin(yaw) * strafe;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double target = cameraSpeed * (settings.keyBindSprint.isKeyDown() ? 2.5 : 1.0);
        if (length > 1e-6) {
            dx = dx / length * target;
            dy = dy / length * target;
            dz = dz / length * target;
        }

        double blend = 1.0 - Math.exp(-seconds * 9.0);
        velocityX += (dx - velocityX) * blend;
        velocityY += (dy - velocityY) * blend;
        velocityZ += (dz - velocityZ) * blend;
        if (Math.abs(velocityX) + Math.abs(velocityY) + Math.abs(velocityZ) < 1e-3) {
            velocityX = velocityY = velocityZ = 0;
            return;
        }
        camera.moveTo(camera.posX + velocityX * seconds, camera.posY + velocityY * seconds, camera.posZ + velocityZ * seconds);
    }

    private void spectate(Entity entity) {
        clearChase();
        spectating = entity;
        followingSelf = entity == self();
        mc.setRenderViewEntity(entity);
    }

    Entity self() {
        if (mc.theWorld == null || selfId == Integer.MIN_VALUE) return null;
        Entity entity = mc.theWorld.getEntityByID(selfId);
        if (entity instanceof CameraEntity || entity == null || entity.isDead) return null;
        return selfUuid == null || selfUuid.equals(entity.getUniqueID()) ? entity : null;
    }

    public void followSelf() {
        Entity you = self();
        if (you != null) spectate(you);
        else followingSelf = true;
    }

    public void stopSpectating() {
        Entity entity = spectating;
        CameraEntity camera = camera();
        spectating = null;
        followingSelf = false;
        clearChase();
        if (camera == null) return;
        if (entity != null) {
            camera.moveTo(entity.posX, entity.posY + entity.getEyeHeight() - camera.getEyeHeight(), entity.posZ);
            camera.turnTo(entity.rotationYaw, entity.rotationPitch);
        }
        mc.setRenderViewEntity(camera);
    }

    public Entity chasing() {
        return chasing;
    }

    public boolean chaseLanded() {
        return chasing != null && chaseLanded >= 0;
    }

    static boolean isProjectile(Entity entity, ReplayModule.Projectiles mode) {
        if (entity instanceof EntityArrow) return true;
        if (mode == ReplayModule.Projectiles.OFF || mode == ReplayModule.Projectiles.ARROWS) return false;
        if (entity instanceof EntityThrowable || entity instanceof EntityFireball) return true;
        return mode == ReplayModule.Projectiles.EVERYTHING && entity instanceof EntityFishHook;
    }

    static boolean isProjectile(Entity entity) {
        return isProjectile(entity, ReplayModule.Projectiles.EVERYTHING);
    }

    static String projectileName(Entity entity) {
        if (entity instanceof EntityArrow) return "arrow";
        if (entity instanceof EntitySnowball) return "snowball";
        if (entity instanceof EntityEgg) return "egg";
        if (entity instanceof EntityEnderPearl) return "ender pearl";
        if (entity instanceof EntityPotion) return "potion";
        if (entity instanceof EntityFireball) return "fireball";
        if (entity instanceof EntityFishHook) return "rod";
        return "projectile";
    }

    private void noticeProjectiles() {
        ReplayModule.Projectiles mode = ReplayModule.followProjectiles.getValue();
        boolean prune = seenProjectiles.size() > 256;
        Set<Integer> present = prune ? new HashSet<>() : null;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!isProjectile(entity)) continue;
            if (present != null) present.add(entity.getEntityId());
            if (!seenProjectiles.add(entity.getEntityId())) continue;
            if (!isProjectile(entity, mode) || atOwnFeet(entity)) continue;
            if (chasing == null && spectating != null && shotBy(entity, spectating)) {
                startChase(entity, spectating);
            } else if (chasing != null && chaseLanded >= 0 && chaseReturn != null && shotBy(entity, chaseReturn)) {
                startChase(entity, chaseReturn);
            }
        }
        if (present != null) seenProjectiles.retainAll(present);
    }

    private static boolean atOwnFeet(Entity entity) {
        if (!(entity instanceof EntityPotion)) return false;
        double across = Math.sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ);
        return entity.motionY < 0 && across < -entity.motionY * 0.6;
    }

    private static boolean shotBy(Entity projectile, Entity shooter) {
        if (projectile instanceof EntityArrow arrow && arrow.shootingEntity != null) return arrow.shootingEntity == shooter;
        if (projectile instanceof EntityFireball fireball && fireball.shootingEntity != null) return fireball.shootingEntity == shooter;
        if (projectile instanceof EntityFishHook hook && hook.angler != null) return hook.angler == shooter;
        double theirs = closestPass(projectile, shooter);
        if (theirs > 2.5 * 2.5) return false;
        for (net.minecraft.entity.player.EntityPlayer other : mc.theWorld.playerEntities) {
            if (other != shooter && !(other instanceof CameraEntity) && closestPass(projectile, other) < theirs) return false;
        }
        return true;
    }

    private static double closestPass(Entity projectile, Entity entity) {
        double eyeX = entity.posX, eyeY = entity.posY + entity.getEyeHeight(), eyeZ = entity.posZ;
        double best = Double.MAX_VALUE;
        for (double ticks = 0; ticks <= 10; ticks += 0.25) {
            double x = projectile.posX - projectile.motionX * ticks - eyeX;
            double y = projectile.posY - projectile.motionY * ticks - eyeY;
            double z = projectile.posZ - projectile.motionZ * ticks - eyeZ;
            best = Math.min(best, x * x + y * y + z * z);
        }
        return best;
    }

    private static boolean alive(Entity entity) {
        return entity != null && !entity.isDead && mc.theWorld != null && mc.theWorld.getEntityByID(entity.getEntityId()) == entity;
    }

    private void startChase(Entity projectile, Entity shooter) {
        CameraEntity camera = camera();
        if (camera == null) return;
        chasing = projectile;
        chaseReturn = shooter;
        chaseFresh = true;
        chaseLanded = chaseBack = -1;
        double mx = projectile.motionX, my = projectile.motionY, mz = projectile.motionZ, length = Math.sqrt(mx * mx + my * my + mz * mz);
        if (length < 1e-6) {
            Vec3 look = (shooter != null ? shooter : camera).getLook(1f);
            mx = look.xCoord;
            my = look.yCoord;
            mz = look.zCoord;
            length = 1;
        }
        chaseDirX = mx / length;
        chaseDirY = my / length;
        chaseDirZ = mz / length;
        spectating = null;
    }

    private void clearChase() {
        chasing = null;
        chaseReturn = null;
        chaseFresh = false;
        chaseLanded = chaseBack = -1;
    }

    private void chaseStages() {
        if (chaseFresh) return;
        if (chaseBack >= 0) {
            if (!alive(chaseReturn)) {
                clearChase();
            } else if (time >= chaseBack + chaseBackLength) {
                Entity back = chaseReturn;
                clearChase();
                spectate(back);
            }
            return;
        }
        if (chaseLanded < 0) {
            Entity projectile = chasing;
            double dx = projectile.posX - projectile.lastTickPosX, dy = projectile.posY - projectile.lastTickPosY, dz = projectile.posZ - projectile.lastTickPosZ;
            boolean still = dx * dx + dy * dy + dz * dz < 1e-6 && time > chaseStarted + 100;
            boolean stuck = projectile instanceof EntityArrow arrow && arrow.arrowShake > 0;
            boolean hooked = projectile instanceof EntityFishHook hook && hook.caughtEntity != null;
            if (!alive(projectile) || still || stuck || hooked || time > chaseStarted + CHASE_LIMIT) chaseLanded = time;
            return;
        }
        if (time >= chaseLanded + ReplayModule.landingPause.getValue() * 1000.0) {
            if (!alive(chaseReturn) && followingSelf) chaseReturn = self();
            if (!alive(chaseReturn)) {
                clearChase();
                return;
            }

            chaseBack = time;
            fromX = poseX;
            fromY = poseY;
            fromZ = poseZ;
            fromYaw = poseYaw;
            fromPitch = posePitch;
            double dx = poseX - chaseReturn.posX, dy = poseY - (chaseReturn.posY + chaseReturn.getEyeHeight()), dz = poseZ - chaseReturn.posZ;
            chaseBackLength = Math.max(CHASE_BACK_MIN, Math.min(CHASE_BACK_MAX, Math.sqrt(dx * dx + dy * dy + dz * dz) * CHASE_BACK_PER_BLOCK));
        }
    }

    public static void beforeRender(float partialTicks) {
        ReplayViewer viewer = current;
        if (viewer == null || viewer.chasing == null || viewer.previewing() || viewer.rendering) return;
        viewer.placeChaseCamera(partialTicks);
    }

    private void placeChaseCamera(float partial) {
        CameraEntity camera = camera();
        if (camera == null || mc.theWorld == null) return;
        long nanos = System.nanoTime();
        float seconds = Math.min(0.1f, (nanos - lastPose) / 1e9f);
        lastPose = nanos;

        if (chaseFresh) {
            chaseFresh = false;
            chaseStarted = time;
            Entity from = chaseReturn;
            if (alive(from) && mc.getRenderViewEntity() == from) {
                fromX = lerp(from.prevPosX, from.posX, partial);
                fromY = lerp(from.prevPosY, from.posY, partial) + from.getEyeHeight();
                fromZ = lerp(from.prevPosZ, from.posZ, partial);
                fromYaw = (float) lerp(from.prevRotationYaw, from.rotationYaw, partial);
                fromPitch = (float) lerp(from.prevRotationPitch, from.rotationPitch, partial);
            } else {
                fromX = camera.posX;
                fromY = camera.posY + camera.getEyeHeight();
                fromZ = camera.posZ;
                fromYaw = camera.rotationYaw;
                fromPitch = camera.rotationPitch;
            }
            poseX = fromX;
            poseY = fromY;
            poseZ = fromZ;
            poseYaw = fromYaw;
            posePitch = fromPitch;
        }

        double x = poseX, y = poseY, z = poseZ;
        float yaw = poseYaw, pitch = posePitch;
        if (chaseBack >= 0 && alive(chaseReturn)) {
            Entity to = chaseReturn;
            float u = smooth((float) ((time - chaseBack) / chaseBackLength));
            x = lerp(fromX, lerp(to.prevPosX, to.posX, partial), u);
            y = lerp(fromY, lerp(to.prevPosY, to.posY, partial) + to.getEyeHeight(), u);
            z = lerp(fromZ, lerp(to.prevPosZ, to.posZ, partial), u);
            yaw = turn(fromYaw, (float) lerp(to.prevRotationYaw, to.rotationYaw, partial), u);
            pitch = (float) lerp(fromPitch, lerp(to.prevRotationPitch, to.rotationPitch, partial), u);
        } else if (chaseLanded < 0) {
            Entity projectile = chasing;
            double ax = lerp(projectile.lastTickPosX, projectile.posX, partial);
            double ay = lerp(projectile.lastTickPosY, projectile.posY, partial) + projectile.height / 2f;
            double az = lerp(projectile.lastTickPosZ, projectile.posZ, partial);

            double vx = projectile.posX - projectile.lastTickPosX, vy = projectile.posY - projectile.lastTickPosY, vz = projectile.posZ - projectile.lastTickPosZ;
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed > 1e-4) {
                double blend = 1.0 - Math.exp(-seconds * 6.0);
                chaseDirX += (vx / speed - chaseDirX) * blend;
                chaseDirY += (vy / speed - chaseDirY) * blend;
                chaseDirZ += (vz / speed - chaseDirZ) * blend;
                double length = Math.sqrt(chaseDirX * chaseDirX + chaseDirY * chaseDirY + chaseDirZ * chaseDirZ);
                if (length > 1e-6) {
                    chaseDirX /= length;
                    chaseDirY /= length;
                    chaseDirZ /= length;
                }
            }

            float u = smooth((float) ((time - chaseStarted) / CHASE_GLIDE));
            x = lerp(fromX, ax - chaseDirX * CHASE_DISTANCE, u);
            y = lerp(fromY, ay - chaseDirY * CHASE_DISTANCE + CHASE_RISE, u);
            z = lerp(fromZ, az - chaseDirZ * CHASE_DISTANCE, u);
            double lookX = ax + chaseDirX * 1.5 - x, lookY = ay + chaseDirY * 1.5 - y, lookZ = az + chaseDirZ * 1.5 - z;
            float lookYaw = (float) Math.toDegrees(Math.atan2(-lookX, lookZ));
            float lookPitch = (float) Math.toDegrees(-Math.atan2(lookY, Math.sqrt(lookX * lookX + lookZ * lookZ)));
            yaw = turn(fromYaw, lookYaw, u);
            pitch = (float) lerp(fromPitch, lookPitch, u);
        }

        camera.moveTo(x, y - camera.getEyeHeight(), z);
        camera.turnTo(yaw, pitch);
        poseX = x;
        poseY = y;
        poseZ = z;
        poseYaw = yaw;
        posePitch = pitch;
        if (mc.getRenderViewEntity() != camera) mc.setRenderViewEntity(camera);
    }

    static float cameraFov() {
        ReplayViewer viewer = current;
        if (viewer == null || viewer.chasing == null || !(viewer.chaseReturn instanceof AbstractClientPlayer shooter) || !alive(shooter)) return 1f;
        float theirs = shooter.getFovModifier();
        if (viewer.chaseFresh) return theirs;
        if (viewer.chaseBack >= 0) return (float) lerp(1f, theirs, smooth((float) ((viewer.time - viewer.chaseBack) / viewer.chaseBackLength)));
        if (viewer.chaseLanded < 0) return (float) lerp(theirs, 1f, smooth((float) ((viewer.time - viewer.chaseStarted) / CHASE_GLIDE)));
        return 1f;
    }

    public static boolean hidesEntity(Entity entity) {
        ReplayViewer viewer = current;
        if (viewer == null || viewer.chasing == null || entity != viewer.chaseReturn) return false;
        double dx = viewer.poseX - entity.posX, dy = viewer.poseY - (entity.posY + entity.getEyeHeight()), dz = viewer.poseZ - entity.posZ;
        return viewer.chaseFresh || dx * dx + dy * dy + dz * dz < 1.6 * 1.6;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static float turn(float from, float to, float amount) {
        float change = (to - from) % 360f;
        if (change >= 180f) change -= 360f;
        if (change < -180f) change += 360f;
        return from + change * amount;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private Entity pick() {
        CameraEntity camera = camera();
        if (camera == null || mc.theWorld == null) return null;
        Vec3 eye = camera.getPositionEyes(1f), look = camera.getLook(1f);
        double range = 128;
        Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        Entity best = null;
        double bestDistance = range;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            boolean projectile = isProjectile(entity);
            if (entity instanceof CameraEntity || !(entity instanceof EntityLivingBase || projectile) || AntiBotModule.isHologram(entity)) continue;

            double grow = projectile ? 0.8 : 0.3;
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(grow, grow, grow);
            double distance;
            if (box.isVecInside(eye)) {
                distance = 0;
            } else {
                MovingObjectPosition hit = box.calculateIntercept(eye, end);
                if (hit == null) continue;
                distance = eye.distanceTo(hit.hitVec);
            }
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static boolean onKey(int key) {
        ReplayViewer viewer = current;
        if (viewer == null || mc.currentScreen != null) return false;
        return viewer.key(key);
    }

    boolean key(int key) {
        if (key == org.lwjgl.input.Keyboard.KEY_P) {
            togglePause();
        } else if (key == org.lwjgl.input.Keyboard.KEY_LEFT) {
            skip(-SKIP);
        } else if (key == org.lwjgl.input.Keyboard.KEY_RIGHT) {
            skip(SKIP);
        } else if (key == org.lwjgl.input.Keyboard.KEY_UP) {
            changeSpeed(1);
        } else if (key == org.lwjgl.input.Keyboard.KEY_DOWN) {
            changeSpeed(-1);
        } else if (key == org.lwjgl.input.Keyboard.KEY_F) {
            followSelf();
        } else if (key == org.lwjgl.input.Keyboard.KEY_H) {
            cycleOverlay();
        } else if (key == org.lwjgl.input.Keyboard.KEY_K) {
            addKeyframe();
        } else {
            return false;
        }
        return true;
    }

    public static boolean onScroll(int wheel) {
        ReplayViewer viewer = current;
        if (viewer == null) return false;
        float factor = wheel > 0 ? 1.2f : 1f / 1.2f;
        viewer.cameraSpeed = Math.max(CAMERA_SPEED_MIN, Math.min(CAMERA_SPEED_MAX, viewer.cameraSpeed * factor));
        viewer.cameraSpeedChanged = System.currentTimeMillis();
        return true;
    }

    public static void consumeKeyBinds() {
        ReplayViewer viewer = current;
        if (viewer == null) return;
        GameSettings settings = mc.gameSettings;
        for (KeyBinding key : settings.keyBindsHotbar) drain(key);
        drain(settings.keyBindInventory);
        drain(settings.keyBindDrop);
        drain(settings.keyBindUseItem);
        drain(settings.keyBindPickBlock);
        drain(settings.keyBindCommand);
        boolean controls = drain(settings.keyBindChat);
        while (settings.keyBindAttack.isPressed()) {
            Entity entity = viewer.pick();
            if (entity != null && isProjectile(entity)) viewer.startChase(entity, viewer.spectating);
            else if (entity != null) viewer.spectate(entity);
        }
        if (drain(settings.keyBindSneak) && (viewer.spectating != null || viewer.chasing != null)) viewer.stopSpectating();
        if (controls) openMenu();
    }

    private static boolean drain(KeyBinding key) {
        boolean pressed = false;
        while (key.isPressed()) pressed = true;
        return pressed;
    }

    public static boolean openMenu() {
        if (current == null) return false;
        if (current.rendering) return true;
        if (current.previewing()) {
            current.stopPreview();
            return true;
        }
        if (mc.currentScreen == null && mc.theWorld != null) mc.displayGuiScreen(new ReplayScreen());
        return true;
    }

    public static boolean hidesHud() {
        ReplayViewer viewer = current;
        return viewer != null && (viewer.previewing() || viewer.rendering || ReplayModule.overlay.getValue() == ReplayModule.Overlay.NOTHING);
    }

    void cycleOverlay() {
        ReplayModule.Overlay[] modes = ReplayModule.Overlay.values();
        ReplayModule.overlay.setValue(modes[(ReplayModule.overlay.getValue().ordinal() + 1) % modes.length]);
    }

    public static void drawHud(net.minecraft.client.gui.ScaledResolution resolution) {
        ReplayViewer viewer = current;
        if (viewer == null || mc.currentScreen instanceof ReplayScreen || mc.gameSettings.hideGUI) return;
        if (ReplayModule.overlay.getValue() != ReplayModule.Overlay.BAR) return;
        ReplayHud.draw(viewer, resolution.getScaledWidth(), resolution.getScaledHeight(), -1000, -1000, false);
    }
}
