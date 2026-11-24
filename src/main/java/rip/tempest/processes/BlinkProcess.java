package rip.tempest.processes;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.List;

public class BlinkProcess {
    public static boolean enabled;
    private static boolean disable = false;
    public static final List<Packet<?>> blinkedSendPackets = new ArrayList<>();
    public static final List<Packet<?>> blinkedReceivePackets = new ArrayList<>();

    @EventLink
    public final Listener<PacketSendEvent> packetSendEventListener = event -> {
        if (enabled) {
            Packet<?> packet = event.getPacket();
            blinkedSendPackets.add(packet);
            event.setCancelled();
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        if (enabled && ClientSettingsModule.blinkCancelsIncoming.getValue()) {
            Packet<?> packet = event.getPacket();
            blinkedReceivePackets.add(packet);
            event.setCancelled();
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        if (enabled) {
            disable();
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (disable) {
            if (event.isPre()) return;

            enabled = false;
            disable = false;

            List<Packet<?>> sendPacketsCopy = new ArrayList<>(blinkedSendPackets);
            List<Packet<?>> receivePacketsCopy = new ArrayList<>(blinkedReceivePackets);

            blinkedSendPackets.clear();
            blinkedReceivePackets.clear();

            for (Packet<?> packet : sendPacketsCopy) {
                PacketUtils.sendPacket(packet);
            }
            for (Packet<?> packet : receivePacketsCopy) {
                PacketUtils.receivePacket(packet);
            }
        }
    };

    public static void enable() {
        blinkedSendPackets.clear();
        blinkedReceivePackets.clear();
        enabled = true;
    }

    public static void disable() {
        disable = true;
    }
}
