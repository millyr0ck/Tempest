package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.EntityPlayerSP;

import static rip.tempest.utils.Util.mc;

public final class ExireWatchdogSpeed extends SpeedBase {
    private int ticksSinceClimb;
    private boolean firstJump;

    @Override
    protected void onEnable() {
        super.onEnable();
        ticksSinceClimb = 0;
        firstJump = true;
    }

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        if (!MovementUtils.isMoving()) {
            MovementUtils.stop();
            return;
        }
        if (e.isOnGround()) {
            e.setPosY(e.getPosY() + 1.0E-4D);
        }
        ticksSinceClimb++;
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame()) return;
        EntityPlayerSP player = mc.thePlayer;

        if (player.onGroundTicks > 15 || player.ticksSinceTeleport < 5) {
            firstJump = true;
        } else if (player.onGround && player.onGroundTicks < 5) {
            firstJump = false;
        }

        if (!MovementUtils.isMoving()) {
            MovementUtils.stop();
            return;
        }

        if (player.onGround) {
            int speedAmplifier = HopUtil.speedLevel();
            HopUtil.strafe(ticksSinceClimb > 10 ? Math.max(0.475F + speedAmplifier * 0.04F, MovementUtils.getSpeed()) : 0.30000001192092896D);
            player.motionY = 0.41999998688697815D;
        } else if (player.offGroundTicks == 1) {
            HopUtil.strafe(MovementUtils.getBaseMoveSpeed(0.2873D) * 1.100000023841858D);
        }

        if (player.offGroundTicks == 1) {
            HopUtil.strafe(MovementUtils.getSpeed());
        }

        if (player.hurtTime == 0 && !player.isCollidedHorizontally && !firstJump) {
            switch (player.offGroundTicks) {
                case 4 -> player.motionY = 0.03700000047683716D;
                case 6 -> player.motionY -= 0.20000000298023224D;
            }
        }

        if (player.hurtTime == 8) {
            e.setSpeed(MovementUtils.getSpeed());
        }
    };
}
