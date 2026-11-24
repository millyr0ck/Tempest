package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;

import static rip.tempest.utils.Util.mc;

public final class PosCancelDisabler extends SubMode {
    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (inGame() && e.getPacket() instanceof C03PacketPlayer && mc.thePlayer.ticksExisted % 3 != 0) {
            e.setCancelled();
        }
    };
}
