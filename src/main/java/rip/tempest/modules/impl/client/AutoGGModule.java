package rip.tempest.modules.impl.client;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Timer;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.server.S45PacketTitle;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto GG", category = ModuleCategory.CLIENT, description = "Sends a GG message after a game ends")
public final class AutoGGModule extends Module {

    private final Property<String> words = new Property<>("Message", "gg");
    private final NumberProperty delay = new NumberProperty("Delay", 1000, 0, 5000, 100, Representation.MILLISECONDS);

    private final Timer sendTimer = new Timer();
    private String queued = null;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!(e.getPacket() instanceof S45PacketTitle title)) return;
        if (title.getType() != S45PacketTitle.Type.TITLE || title.getMessage() == null) return;

        String text = title.getMessage().getUnformattedText();

        if (text.contains("VICTORY") || text.contains("YOU WON")) {
            queue(words.getValue());
        } else if (text.contains("GAME OVER")) {
            AuraModule aura = Tempest.INSTANCE.getModuleManager().getModule(AuraModule.class);
            if (aura != null && aura.isEnabled()) queue("gg");
        }
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (queued == null || mc.thePlayer == null) return;
        if (!sendTimer.hasTimeElapsed(delay.getValue())) return;
        mc.thePlayer.sendChatMessage(queued);
        queued = null;
    };

    private void queue(String msg) {
        queued = msg;
        sendTimer.reset();
    }
}
