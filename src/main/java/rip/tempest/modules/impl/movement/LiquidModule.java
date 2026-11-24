package rip.tempest.modules.impl.movement;

import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.potion.Potion;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.MovementUtils;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Liquid", category = ModuleCategory.MOVEMENT, description = "Moves faster in water and lava")
public final class LiquidModule extends Module {
    private enum Mode { Watchdog }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Watchdog);

    private final Property<Boolean> motion = new Property<>("Motion", true, () -> mode.getValue() == Mode.Watchdog);

    private static final double[] SPEED_EVEN = {0.425D, 0.41D, 0.39D, 0.38D};
    private static final double[] SPEED_ODD = {0.22D, 0.21D, 0.22D, 0.22D};

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!mc.thePlayer.isInLava() && !mc.thePlayer.isInWater()) return;

        if (motion.getValue()) {
            if (mc.gameSettings.keyBindJump.isKeyDown()) {
                if (mc.thePlayer.ticksExisted % 3 == 0) mc.thePlayer.motionY = 0.2D;
            } else {
                mc.thePlayer.motionY = -0.2D;
            }
        }

        if (mc.thePlayer.onGround) {
            boolean even = mc.thePlayer.ticksExisted % 2 == 0;
            double speed;
            if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
                int level = Math.min(mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier(), SPEED_EVEN.length - 1);
                speed = MovementUtils.getBaseMoveSpeed(even ? SPEED_EVEN[level] : SPEED_ODD[level]);
            } else {
                speed = even ? 0.43D : 0.22D;
            }
            MovementUtils.setSpeed(e, speed);
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        setSuffix(mode.getValue().name());
        if (e.isPre() && mc.thePlayer.isInWater()) e.setOnGround(false);
    };
}
