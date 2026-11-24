package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.render.CapeTexture;
import dev.tr7zw.waveycapes.WaveyCape;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.util.ResourceLocation;

@ModuleInfo(label = "Custom Cape", category = ModuleCategory.VISUALS)
public final class CustomCapeModule extends Module {
    public static ModeProperty<Cape> cape = new ModeProperty<>("Cape", Cape.TempestClassic);

    public static Property<Boolean> wavey = new Property<>("Wavey", true);
    public static NumberProperty gravity = new NumberProperty("Gravity", 25, () -> wavey.getValue(), 5, 50, 1);
    public static NumberProperty heightMultiplier = new NumberProperty("Height Multiplier", 6, () -> wavey.getValue(), 2, 10, 1);
    public static NumberProperty strafeMultiplier = new NumberProperty("Strafe Multiplier", 2, () -> wavey.getValue(), 0, 10, 1);
    public static Property<Boolean> wind = new Property<>("Wind", false, () -> wavey.getValue());

    private static final WaveyCape.Settings WAVEY_SETTINGS = new WaveyCape.Settings();

    public static WaveyCape.Settings waveySettings() {
        WAVEY_SETTINGS.gravity = gravity.getValue().floatValue();
        WAVEY_SETTINGS.heightMultiplier = heightMultiplier.getValue().floatValue();
        WAVEY_SETTINGS.straveMultiplier = strafeMultiplier.getValue().floatValue();
        WAVEY_SETTINGS.wind = wind.getValue();
        return WAVEY_SETTINGS;
    }

    public enum Cape {
        TempestClassic("TempestClassicCape.png"),
        Tempest("TempestCape.png"),
        TempestVoid("TempestVoidCape.png"),
        TempestMono("TempestMonoCape.png"),
        TempestOutline("TempestOutlineCape.png"),
        TempestBand("TempestBandCape.png"),
        TempestEmber("TempestEmberCape.png"),
        Rise("RiseCape.png"),
        M2011("M2011.png"),
        Employee("Employee.png"),
        Israel("israel.png"),
        Blonde("blonde.png"),
        Hellokitty("hellokitty.png"),
        Epstein("epstein.png"),
        M2012("M2012.png"),
        M2013("M2013.png"),
        Gato("GatoCape.png"),
        Turtle("Turtle.png"),
        MineCon("MineCon.png"),
        OMG("OMGCape.png"),
        Vape("vape.png"),
        Anime1("anime1.png"),
        Girl2("girl2.png"),
        Button("button.png"),
        Sabrina("sabrina.png"),
        Monster("monster.png"),
        Egirl("egirl.png"),
        Cat("cat.png"),
        Mommy("mommy.png"),
        Goth("goth.png"),
        Butt("butt.png"),
        Hentai2("henti2.png"),
        Ass("ahhhhh.png"),
        Anime2("anime.png"),
        Puh("pussy.png"),
        Titties("boobs.png"),
        Hentai("hentai3.png"),
        Lowquality("lowquality.png");

        private final String file;
        private ResourceLocation texture;

        Cape(String file) {
            this.file = file;
        }

        public ResourceLocation texture() {
            if (texture == null) texture = CapeTexture.load(file);
            return texture;
        }

        @Override
        public String toString() {
            String name = name();
            return name.startsWith("Tempest") && name.length() > 7 ? "Tempest " + name.substring(7) : name;
        }
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(cape.getValue().toString());
    };
}
