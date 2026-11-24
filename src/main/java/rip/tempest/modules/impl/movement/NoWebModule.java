package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Web", category = ModuleCategory.MOVEMENT)
public final class NoWebModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);

    private enum Mode {
        Vanilla,
        Intave,
    }

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        setSuffix(mode.getValue().toString());
        switch (mode.getValue()) {
            case Vanilla -> {
                if (mc.thePlayer.isInWeb && MovementUtils.isMoving()) mc.thePlayer.isInWeb = false;
            }
            case Intave -> {
                if (mc.thePlayer.isInWeb && MovementUtils.isMoving() && mc.thePlayer.onGround) {
                    if (mc.thePlayer.ticksExisted % 3 == 0) {
                        MovementUtils.strafe(0.734);
                    } else {
                        mc.thePlayer.jump();
                        MovementUtils.strafe(0.346);
                    }
                }
            }
        }
    };
}
