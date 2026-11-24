package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.combat.VelocityModule;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;


import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Play", category = ModuleCategory.CLIENT)
public class AutoPlayModule extends Module {

    public static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Hypixel);

    public enum Mode {
        Hypixel
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onPacketReceive = event -> {
        Packet<?> packet = event.getPacket();

        if (packet instanceof S02PacketChat) {
            S02PacketChat chat = ((S02PacketChat) packet);
            if (mode.getValue() == Mode.Hypixel) {
                if (chat.isChat()) return;
                if (chat.getChatComponent().getFormattedText().contains("play again?")) {
                    for (IChatComponent iChatComponent : chat.getChatComponent().getSiblings()) {
                        for (String value : iChatComponent.toString().split("'")) {
                            if (value.startsWith("/play") && !value.contains(".")) {
                                mc.thePlayer.sendChatMessage(value);
                                break;
                            }
                        }
                    }
                }
            }
        }
    };
}
