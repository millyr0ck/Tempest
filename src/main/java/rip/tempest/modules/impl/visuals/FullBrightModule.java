package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Full Bright", category = ModuleCategory.VISUALS)
public final class FullBrightModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.GAMMA);

    private enum Mode {
        GAMMA("Gamma"),
        POTION("Potion");

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
        switch (mode.getValue()) {
            case GAMMA:
                mc.gameSettings.gammaSetting = 100000;
                break;
            case POTION:
                mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, 5200, 1));
                break;
        }
    };

    @Override
    public void onDisable() {
        if (mc.thePlayer.isPotionActive(Potion.nightVision) && mode.getValue() == Mode.POTION) {
            mc.thePlayer.removePotionEffect(Potion.nightVision.id);
        }
        mc.gameSettings.gammaSetting = 1.0f;
    }
}