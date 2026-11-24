package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.potion.Potion;

import static rip.tempest.utils.Util.mc;

public final class TenacityLowHopSpeed extends SpeedBase {
    private float speed;
    private int inAirTicks;

    @Override
    protected void onEnable() {
        super.onEnable();
        speed = 1.5F;
        inAirTicks = 0;
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (inGame()) MovementUtils.stop();
    }

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        if (MovementUtils.isMoving() && mc.thePlayer.fallDistance < 1.0F && mc.thePlayer.onGround) {
            mc.thePlayer.jump();
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame() || !MovementUtils.isMoving()) return;
        inAirTicks = mc.thePlayer.onGround ? 0 : inAirTicks + 1;
        if (inAirTicks == 5) {
            e.setY(mc.thePlayer.motionY = -0.19D);
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame() || mc.thePlayer.fallDistance >= 1.0F || mc.thePlayer.isPotionActive(Potion.jump)) return;

        if (MovementUtils.isMoving()) {
            if (mc.thePlayer.onGround) speed = 1.5F;
            speed -= 0.025F;
            applyMotion(e, baseMoveSpeed() * speed, 0.55F);
        } else {
            applyMotion(e, 0.0D, 0.0F);
        }
    };

    private static void applyMotion(StrafeEvent e, double speed, float strafeMotion) {
        float remainder = 1.0F - strafeMotion;
        strafeMotion *= 0.91F;
        if (mc.thePlayer.onGround) {
            MovementUtils.setSpeed(speed);
        } else {
            mc.thePlayer.motionX *= strafeMotion;
            mc.thePlayer.motionZ *= strafeMotion;
            e.setFriction((float) speed * remainder);
        }
    }

    private static double baseMoveSpeed() {
        double base = mc.thePlayer.capabilities.getWalkSpeed() * 2.873D;
        if (mc.thePlayer.isPotionActive(Potion.moveSlowdown)) {
            base /= 1.0D + 0.2D * (mc.thePlayer.getActivePotionEffect(Potion.moveSlowdown).getAmplifier() + 1);
        }
        if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0D + 0.2D * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        }
        return base;
    }
}
