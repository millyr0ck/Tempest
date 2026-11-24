package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

@ModuleInfo(label = "Spoofer", category = ModuleCategory.CLIENT)
public final class SpooferModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Lunar);

    private enum Mode {
        Lunar("Lunar"),
        Feather("Feather");

        public final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
    };

    @EventLink
    public final Listener<PacketSendEvent> packetSendEventListener = e -> {
        if (e.getPacket() instanceof C17PacketCustomPayload) {
            String data;
            switch (mode.getValue()) {
                case Lunar:
                    data = "lunarclient:v2.14.5-2411";
                    break;
                case Feather:
                    data = "Feather Forge";
                    break;
                default:
                    data = "";
                    break;
            }

            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data.getBytes()));
            C17PacketCustomPayload spoofedPacket = new C17PacketCustomPayload("MC|Brand", buffer);

            e.setPacket(spoofedPacket);
        }
    };
}