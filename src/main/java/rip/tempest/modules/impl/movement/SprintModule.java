package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.SprintEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.api.properties.Property;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Sprint", category = ModuleCategory.MOVEMENT)
public final class SprintModule extends Module {


    public static Property<Boolean> omni = new Property<>("Omni", false);

    public SprintModule() {
        toggle();
    }

    @EventLink
    public final Listener<SprintEvent> onSprintEvent = event -> {
        if (!event.isSprinting()) {
            if (MovementUtils.isMoving() && !omni.getValue()) {
                mc.gameSettings.keyBindSprint.setPressed(true);
            }
        }
    };



    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (MovementUtils.isMoving() && omni.getValue()) {
            mc.thePlayer.setSprinting(MovementUtils.canSprint(true));
        }
    };

}
