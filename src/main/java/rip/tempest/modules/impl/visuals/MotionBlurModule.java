package rip.tempest.modules.impl.visuals;

import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;

@ModuleInfo(label = "Motion Blur", category = ModuleCategory.VISUALS)
public final class MotionBlurModule extends Module {
    public NumberProperty blurAmount = new NumberProperty("Blur Amount", 7.0, 0.0, 10.0, 0.1);
}
