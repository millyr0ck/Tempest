package rip.tempest.modules.impl.client;

import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.interfaces.minigame.cases.CaseScreen;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Case Opening", category = ModuleCategory.CLIENT, description = "Opens CS2 cases, as a simulation")
public final class CaseOpeningModule extends Module {
    private final NumberProperty volume = new NumberProperty("Volume", 100, 0, 100, 5);

    private CaseScreen screen;

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.thePlayer == null || !CaseScreen.available()) {
            if (mc.thePlayer != null) System.err.println("Case Opening: " + CaseScreen.folder() + " has no cases.json; run tools/convert_cases.py");
            mc.addScheduledTask(() -> setEnabled(false));
            return;
        }
        screen = new CaseScreen(() -> volume.getValue() / 100.0, this::screenClosed);
        mc.displayGuiScreen(screen);
    }

    @Override
    public void onDisable() {
        final CaseScreen shown = screen;
        screen = null;
        if (shown != null && mc.currentScreen == shown) mc.displayGuiScreen(null);
        super.onDisable();
    }

    private void screenClosed() {
        if (screen == null) return;
        screen = null;
        setEnabled(false);
    }
}
