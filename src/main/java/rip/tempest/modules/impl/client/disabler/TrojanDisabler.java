package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

public final class TrojanDisabler extends SubMode {
    private final Queue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private int counter;

    @Override
    protected void onEnable() {
        held.clear();
        counter = 0;
    }

    @Override
    protected void onDisable() {
        release();
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        Packet<?> packet = e.getPacket();
        if (packet instanceof C00PacketKeepAlive
                || packet instanceof C0FPacketConfirmTransaction transaction && transaction.getWindowId() == 0) {
            held.add(packet);
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        counter++;
        if (counter == 1) {
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX, mc.thePlayer.posY - 11.0, mc.thePlayer.posZ, false));
        } else if (counter == 2) {
            release();
        } else if (counter >= 150) {
            counter = 0;
        }
    };

    private void release() {
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            if (inGame()) PacketUtils.sendPacketNoEvent(packet);
        }
    }
}
