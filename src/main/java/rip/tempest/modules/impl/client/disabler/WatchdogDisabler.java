package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public final class WatchdogDisabler extends SubMode {
    private boolean dev;

    public WatchdogDisabler(boolean dev) {
        this.dev = dev;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame() || !ServerUtils.isOnHypixel()) return;
        if (e.getPacket() instanceof S32PacketConfirmTransaction transaction && transaction.getActionNumber() < 0) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!dev || !inGame() || !ServerUtils.isOnHypixel()) return;
        if (e.getPacket() instanceof C00PacketKeepAlive) {
            e.setCancelled();
        }
    };
}
