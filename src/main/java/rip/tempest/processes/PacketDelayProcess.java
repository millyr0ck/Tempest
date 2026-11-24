package rip.tempest.processes;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.world.PostTickEvent;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import static rip.tempest.utils.Util.mc;

public final class PacketDelayProcess {
    private static final Deque<HeldPacket> queuedPackets = new ConcurrentLinkedDeque<>();
    private static int delayTicks;
    private static boolean flushing;
    private static Vec3 lastReleasedPosition = new Vec3(0.0, 0.0, 0.0);

    private static final class HeldPacket {
        private final Packet<?> packet;
        private int ageTicks;

        private HeldPacket(Packet<?> packet) {
            this.packet = packet;
        }
    }

    private static void releaseExpiredPackets() {
        if (mc.getNetHandler() == null) {
            queuedPackets.clear();
            return;
        }

        flushing = true;
        try {
            while (!queuedPackets.isEmpty()) {
                HeldPacket held = queuedPackets.peek();
                if (delayTicks > 0 && held.ageTicks <= delayTicks) break;
                PacketUtils.sendPacketNoEvent(held.packet);
                updateLastReleasedPosition(held.packet);
                queuedPackets.poll();
            }
        } finally {
            flushing = false;
        }
    }

    private static void updateLastReleasedPosition(Packet<?> packet) {
        if (packet instanceof C03PacketPlayer movement && movement.isMoving()) {
            lastReleasedPosition = new Vec3(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
        }
    }

    public static boolean buffer(Packet<?> packet) {
        if (flushing) return false;
        releaseExpiredPackets();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) return false;
        if (delayTicks > 0) {
            queuedPackets.offer(new HeldPacket(packet));
            return true;
        }
        updateLastReleasedPosition(packet);
        return false;
    }

    public static void setDelayTicks(int ticks) {
        delayTicks = ticks;
    }

    public static Vec3 lastReleasedPosition() {
        return lastReleasedPosition;
    }

    @EventLink
    public final Listener<PostTickEvent> onPostTick = event -> {
        if (mc.thePlayer.isDead) setDelayTicks(0);
        for (HeldPacket held : queuedPackets) held.ageTicks++;
        releaseExpiredPackets();
    };

    @EventLink
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C00Handshake || packet instanceof C00PacketLoginStart || packet instanceof C00PacketServerQuery
                || packet instanceof C01PacketPing || packet instanceof C01PacketEncryptionResponse) {
            setDelayTicks(0);
        }
    };
}
