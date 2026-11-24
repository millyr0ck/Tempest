package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;

import static rip.tempest.utils.Util.mc;

public final class SlothDisabler extends SubMode {
    private boolean launched;

    @Override
    protected void onEnable() {
        launched = false;
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame() || !(e.getPacket() instanceof C03PacketPlayer packet) || !packet.isMoving()) return;
        e.setCancelled();

        double y = packet.getPositionY() + 1.0E-4;
        if (packet.getRotating()) {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    packet.getPositionX(), y, packet.getPositionZ(), packet.getYaw(), packet.getPitch(), packet.isOnGround()));
        } else {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    packet.getPositionX(), y, packet.getPositionZ(), packet.isOnGround()));
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame() || launched) return;
        launched = true;
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY + 11.0, mc.thePlayer.posZ, false));
    };
}
