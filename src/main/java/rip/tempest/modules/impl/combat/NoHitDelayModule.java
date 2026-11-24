package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Hit Delay", category = ModuleCategory.COMBAT)
public final class NoHitDelayModule extends Module {

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        if (mc.theWorld != null && mc.thePlayer != null) {
            if (!mc.inGameHasFocus) return;

            mc.leftClickCounter = 0;
        }
    };
}