package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

public final class HalfTickDisabler extends SubMode {
    private final Queue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private boolean dropPings;

    public HalfTickDisabler(boolean dropPings) {
        this.dropPings = dropPings;
    }

    @Override
    protected void onEnable() {
        held.clear();
    }

    @Override
    protected void onDisable() {
        release();
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame() || mc.thePlayer.isDead) return;
        Packet<?> packet = e.getPacket();

        if (mc.thePlayer.ticksExisted % 2 != 0) {
            boolean sprint = packet instanceof C0BPacketEntityAction action
                    && (action.getAction() == C0BPacketEntityAction.Action.START_SPRINTING
                    || action.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING);
            boolean ping = dropPings && (packet instanceof C00PacketKeepAlive
                    || packet instanceof C0FPacketConfirmTransaction transaction && transaction.getWindowId() == 0);

            e.setCancelled();
            if (!sprint && !ping) held.add(packet);
            return;
        }

        release();
    };

    private void release() {
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            if (inGame()) PacketUtils.sendPacketNoEvent(packet);
        }
    }
}
