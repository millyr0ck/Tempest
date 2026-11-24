package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import lombok.Getter;
import lombok.Setter;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Free Look", category = ModuleCategory.VISUALS)
public final class FreeLookModule extends Module {

    @Getter
    @Setter
    private float cameraYaw, cameraPitch;
    private int prevThirdPersonView;
    private boolean enableLook = false;

    @Override
    public void onDisable() {
        enableLook = false;
        mc.gameSettings.thirdPersonView = prevThirdPersonView;
        super.onDisable();
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        enableLook = false;
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.thePlayer != null && !enableLook) {
            prevThirdPersonView = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            enableLook = true;
        }
    };

    public boolean freeLooked() {
        return enableLook;
    }
}
