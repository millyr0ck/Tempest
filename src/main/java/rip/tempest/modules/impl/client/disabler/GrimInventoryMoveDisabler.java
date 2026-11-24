package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import static rip.tempest.utils.Util.mc;

public final class GrimInventoryMoveDisabler extends SubMode {
    private boolean resending;
    private boolean sprinting;
    private boolean sprintStateKnown;

    @Override
    protected void onEnable() {
        resending = false;
        sprintStateKnown = false;
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame()) return;
        Packet<?> packet = e.getPacket();

        if (packet instanceof C0BPacketEntityAction action) {
            if (action.getAction() == C0BPacketEntityAction.Action.START_SPRINTING) {
                if (sprintStateKnown && sprinting) {
                    e.setCancelled();
                    return;
                }
                sprinting = true;
                sprintStateKnown = true;
            } else if (action.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
                if (sprintStateKnown && !sprinting) {
                    e.setCancelled();
                    return;
                }
                sprinting = false;
                sprintStateKnown = true;
            }
            return;
        }

        if (resending || !(packet instanceof C0EPacketClickWindow || packet instanceof C0DPacketCloseWindow)) return;
        if (!sprintStateKnown || !sprinting) return;

        e.setCancelled();
        PacketUtils.sendPacketNoEvent(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        sprinting = false;

        resending = true;
        try {
            PacketUtils.sendPacket(packet);
        } finally {
            resending = false;
        }

        if (mc.thePlayer.isSprinting()) {
            PacketUtils.sendPacketNoEvent(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            sprinting = true;
        }
    };
}
