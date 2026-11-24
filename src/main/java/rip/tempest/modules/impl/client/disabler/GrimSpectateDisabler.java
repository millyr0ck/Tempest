package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

public final class GrimSpectateDisabler extends SubMode {
    private final Queue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private volatile boolean flagged;

    @Override
    protected void onDisable() {
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            if (inGame()) PacketUtils.receivePacketNoEvent(packet);
        }
        flagged = false;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame()) return;
        Packet<?> packet = e.getPacket();
        if (mc.thePlayer.ticksExisted < 20) {
            held.clear();
            return;
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            if (mc.thePlayer.capabilities.isFlying && !flagged) {
                flagged = true;
            } else if (flagged) {
                held.add(packet);
                e.setCancelled();
                mc.addScheduledTask(() -> Logger.chatPrint(true, "Grim Spectate: flagged, turn the Disabler off and on to release."));
            }
        }

        if (flagged && packet instanceof S32PacketConfirmTransaction) {
            held.add(packet);
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C0FPacketConfirmTransaction(0, (short) 0, true));
        }
    };
}
