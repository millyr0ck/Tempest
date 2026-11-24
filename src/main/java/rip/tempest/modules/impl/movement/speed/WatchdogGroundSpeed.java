package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.impl.combat.TargetStrafeModule;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

public final class WatchdogGroundSpeed extends SpeedBase {
    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @Override
    protected void update(MotionEvent e) {
        if (!e.isPre() || !inGame()) return;
        if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, -1.0E-4D, 0.0D)).isEmpty()) return;

        mc.thePlayer.motionY -= 0.15D;

        final boolean oddTick = mc.thePlayer.ticksExisted % 2 != 0;
        if (MovementUtils.isMoving() && mc.thePlayer.onGround && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isCollidedHorizontally) {
            final boolean headroom = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                    mc.thePlayer.getEntityBoundingBox().offset(0.0D, 0.42D, 0.0D)).isEmpty();
            e.setPosY(mc.thePlayer.posY + (oddTick ? (headroom ? 0.42D : 0.2D) : 0.0D));
        }

        final double speed = Math.max(oddTick ? baseSpeed(1.35D) : baseSpeed(2.5D), baseSpeed());

        double forward = mc.thePlayer.movementInput.moveForward;
        double strafe = mc.thePlayer.movementInput.moveStrafe;
        double yaw = TargetStrafeModule.runYaw();

        if (forward == 0.0D && strafe == 0.0D) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            return;
        }

        if (forward != 0.0D) {
            if (strafe > 0.0D) {
                yaw += (forward > 0.0D) ? -45 : 45;
            } else if (strafe < 0.0D) {
                yaw += (forward > 0.0D) ? 45 : -45;
            }
            strafe = 0.0D;
            forward = forward > 0.0D ? 0.15000000596046448D : -0.15000000596046448D;
        }
        if (strafe > 0.0D) {
            strafe = 0.15000000596046448D;
        } else if (strafe < 0.0D) {
            strafe = -0.15000000596046448D;
        }

        final double rad = Math.toRadians(yaw + 90.0D);
        mc.thePlayer.motionX = forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad);
        mc.thePlayer.motionZ = forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad);
    }
}
