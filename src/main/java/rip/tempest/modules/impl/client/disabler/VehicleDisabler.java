package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0CPacketInput;

import static rip.tempest.utils.Util.mc;

public final class VehicleDisabler extends SubMode {
    private Packet<?> handled;

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        Packet<?> packet = e.getPacket();
        if (!inGame() || !(packet instanceof C03PacketPlayer) || packet == handled) return;
        handled = packet;

        PacketUtils.sendPacketNoEvent(new C0CPacketInput(
                mc.thePlayer.moveStrafing, mc.thePlayer.moveForward,
                mc.thePlayer.movementInput.jump, mc.thePlayer.movementInput.sneak));
    };
}
