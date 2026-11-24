package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.visuals.cosmetics.Cosmetics;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Cosmetics", category = ModuleCategory.VISUALS)
public final class CosmeticsModule extends Module {
    public enum Tint { Natural, White, Client }

    public enum Ears { Off, Cat, Fox, Bunny }

    public enum Tail { Off, Cat, Dragon }

    public enum Hat {
        Off("Off"), Witch("Witch"), Santa("Santa"), TopHat("Top Hat"), Crown("Crown");

        private final String display;

        Hat(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum Mask { Off, Bandana, Mask }

    public enum BackItem { Off, Sword, Bow }

    public static final Property<Boolean> wings = new Property<>("Dragon Wings", true);
    public static final ModeProperty<Tint> wingColor = new ModeProperty<>("Wing Color", Tint.Natural, wings::getValue);
    public static final NumberProperty wingScale = new NumberProperty("Wing Scale", 1.0, wings::getValue, 0.5, 2.0, 0.05);
    public static final NumberProperty flapSpeed = new NumberProperty("Flap Speed", 1.0, wings::getValue, 0.2, 3.0, 0.1);

    public static final ModeProperty<Ears> ears = new ModeProperty<>("Ears", Ears.Off);
    public static final ModeProperty<Tint> earColor = new ModeProperty<>("Ear Color", Tint.Natural, () -> ears.getValue() != Ears.Off);

    public static final ModeProperty<Tail> tail = new ModeProperty<>("Tail", Tail.Off);
    public static final ModeProperty<Tint> tailColor = new ModeProperty<>("Tail Color", Tint.Natural, () -> tail.getValue() != Tail.Off);

    public static final ModeProperty<Hat> hat = new ModeProperty<>("Hat", Hat.Off);
    public static final ModeProperty<Tint> hatColor = new ModeProperty<>("Hat Color", Tint.Natural, () -> hat.getValue() != Hat.Off);

    public static final ModeProperty<Mask> mask = new ModeProperty<>("Face", Mask.Off);
    public static final ModeProperty<Tint> maskColor = new ModeProperty<>("Face Color", Tint.Natural, () -> mask.getValue() != Mask.Off);

    public static final ModeProperty<BackItem> backItem = new ModeProperty<>("Back Item", BackItem.Off);

    public static final Property<Boolean> aura = new Property<>("Aura", false);
    public static final ModeProperty<Tint> auraColor = new ModeProperty<>("Aura Color", Tint.Client, aura::getValue);
    public static final NumberProperty auraParticles = new NumberProperty("Aura Particles", 14, aura::getValue, 4, 30, 1);

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        if (e.isPre() && mc.thePlayer != null) Cosmetics.tick(mc.thePlayer);
    };
}
