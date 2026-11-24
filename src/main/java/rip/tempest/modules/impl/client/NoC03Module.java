package rip.tempest.modules.impl.client;

import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;

@ModuleInfo(label = "No C03", category = ModuleCategory.CLIENT, description = "Cancels movement packets that carry no position or rotation")
public final class NoC03Module extends Module {
    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (e.getPacket() instanceof C03PacketPlayer packet && !packet.isMoving() && !packet.getRotating()) {
            e.setCancelled();
        }
    };
}
