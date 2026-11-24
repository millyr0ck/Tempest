package rip.tempest.modules;

import static rip.tempest.utils.Util.mc;

public abstract class SubMode {
    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected static boolean inGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }
}
