package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

import static rip.tempest.utils.Util.mc;

public final class NoPayloadDisabler extends SubMode {
    private Packet<?> handled;

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame()) return;
        Packet<?> packet = e.getPacket();

        if (packet instanceof C00PacketKeepAlive keepAlive) {
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C00PacketKeepAlive(keepAlive.getKey() - 1));
        } else if (packet instanceof C0FPacketConfirmTransaction transaction) {
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C0FPacketConfirmTransaction(
                    transaction.getWindowId(), (short) (transaction.getUid() - 6), transaction.isAccepted()));
        } else if (packet instanceof C03PacketPlayer player) {
            if (mc.thePlayer.ticksExisted % 3 == 0) {
                e.setCancelled();
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                        mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                        player.getRotating() ? player.getYaw() : mc.thePlayer.rotationYaw,
                        player.getRotating() ? player.getPitch() : mc.thePlayer.rotationPitch,
                        mc.thePlayer.onGround));
            } else if (mc.thePlayer.ticksExisted % 200 != 0) {
                e.setCancelled();
            } else if (player != handled) {
                handled = player;
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(-6.0, 256.0, -8.0, false));
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(-6.0, 256.0, -8.0, true));
            }
        }
    };
}
