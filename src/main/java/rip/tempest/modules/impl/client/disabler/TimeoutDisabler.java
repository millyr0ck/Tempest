package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

public final class TimeoutDisabler extends SubMode {
    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (e.getPacket() instanceof C0FPacketConfirmTransaction || e.getPacket() instanceof C00PacketKeepAlive) {
            e.setCancelled();
        }
    };
}
