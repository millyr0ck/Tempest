package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

public final class SentinelMoonDisabler extends SubMode {
    private final Queue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private final Timer timer = new Timer();
    private int delay;

    private Packet<?> handled;

    @Override
    protected void onEnable() {
        held.clear();
        delay = randomDelay(2550, 2650);
        timer.reset();
    }

    @Override
    protected void onDisable() {
        release();
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            held.clear();
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame()) return;
        Packet<?> packet = e.getPacket();

        if (packet instanceof C0FPacketConfirmTransaction transaction) {
            e.setCancelled();
            held.add(packet);
            if (transaction.getWindowId() != 0) {
                release();
                delay = randomDelay(350, 400);
            }
        } else if (packet instanceof C00PacketKeepAlive) {
            e.setCancelled();
            held.add(packet);
        } else if (packet instanceof C03PacketPlayer && packet != handled) {
            handled = packet;
            PacketUtils.sendPacketNoEvent(new C0CPacketInput(
                    mc.thePlayer.moveStrafing, mc.thePlayer.moveForward,
                    mc.thePlayer.movementInput.jump, mc.thePlayer.movementInput.sneak));
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        if (timer.hasTimeElapsed(delay, true)) {
            release();
            delay = randomDelay(950, 1000);
        }
    };

    private void release() {
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            if (inGame()) PacketUtils.sendPacketNoEvent(packet);
        }
    }

    private static int randomDelay(int min, int max) {
        return (int) MathUtils.getRandom(min, max);
    }
}
