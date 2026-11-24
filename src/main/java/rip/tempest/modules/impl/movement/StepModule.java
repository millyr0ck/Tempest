package rip.tempest.modules.impl.movement;

import net.minecraft.network.play.client.C03PacketPlayer;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StepEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Step", category = ModuleCategory.MOVEMENT)
public class StepModule extends Module {
    private enum Mode {
        Vanilla,

        NCP
    }

    public static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);
    public static NumberProperty stepHeight = new NumberProperty("Step Height", 1, () -> mode.getValue() == Mode.Vanilla, 1, 10, 0.5);

    private final Property<Boolean> ncpTimer = new Property<>("Timer", false, () -> mode.getValue() == Mode.NCP);
    private final NumberProperty ncpDelay = new NumberProperty("Delay", 150, () -> mode.getValue() == Mode.NCP, 0, 500, 50, Representation.MILLISECONDS);

    private final Property<Boolean> ncpSpeedCheck = new Property<>("Speed Check", false, () -> mode.getValue() == Mode.NCP);

    private static final double[] NCP_OFFSETS = {0.41999998688697815D, 0.7531999805212024D};

    private final Timer stepDelay = new Timer();
    private boolean active;
    private int timerTicks = -1;

    @EventLink
    private final Listener<MotionEvent> motionEventListener = event -> {
        if (mode.getValue() == Mode.Vanilla) {
            setSuffix(stepHeight.getValue().toString());
            mc.thePlayer.stepHeight = stepHeight.getValue().floatValue();
        } else {
            setSuffix(mode.getValue().name());
        }
    };

    @EventLink
    private final Listener<StepEvent> stepEventListener = event -> {
        if (mode.getValue() != Mode.NCP) return;
        if (event.isPre()) {
            mc.thePlayer.stepHeight = 0.6F;
            event.setStepHeight(0.6F);
            active = false;

            if (ncpSpeedCheck.getValue() && Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class).isEnabled()) return;
            if (mc.gameSettings.keyBindJump.isKeyDown()) return;
            if (Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class).isEnabled()) {
                event.setStepHeight(0.0F);
                return;
            }
            if (!mc.thePlayer.onGround || !mc.thePlayer.isCollidedVertically) return;

            if (stepDelay.hasTimeElapsed(ncpDelay.getValue().doubleValue())) {
                event.setStepHeight(1.0F);
                active = true;
            }
            return;
        }

        if (!active || event.getHeightStepped() <= 0.6D) return;
        if (ncpTimer.getValue()) {
            timerTicks = 1;
            mc.timer.timerSpeed = 0.5F;
        }
        for (double offset : NCP_OFFSETS) {
            PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + offset, mc.thePlayer.posZ, false));
        }
        stepDelay.reset();
        active = false;
    };

    @EventLink
    private final Listener<PreUpdateEvent> preUpdateListener = event -> {
        if (timerTicks < 0) return;
        timerTicks--;
        if (timerTicks <= 0) {
            mc.timer.timerSpeed = 1.0F;
            stepDelay.reset();
        }
    };

    @Override
    public void onEnable() {
        active = false;
        timerTicks = -1;
        stepDelay.reset();
        super.onEnable();
    }

    public void onDisable() {
        if (timerTicks > 0) mc.timer.timerSpeed = 1.0F;
        timerTicks = -1;
        active = false;
        mc.thePlayer.stepHeight = 0.5f;
        super.onDisable();
    }
}
