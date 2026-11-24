package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.potion.Potion;

import java.util.concurrent.ThreadLocalRandom;

import static rip.tempest.utils.Util.mc;

public final class OpenB4SevenTickSpeed extends SpeedBase {
    private static final double FALL_MOTION = -0.0784000015258789D;

    private boolean active;
    private int jumps;

    @Override
    protected void onEnable() {
        super.onEnable();
        active = false;
        jumps = 0;
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (active && inGame()) stopped();
        jumps = 0;
        active = false;
    }

    @EventLink
    public final Listener<MoveEvent> onMoveInput = e -> {
        if (inGame() && canBhop()) e.setJump(false);
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        boolean was = active;
        active = false;
        if (canBhop() && MovementUtils.isMoving()) {
            active = bhop();
        }
        if (was && !active) stopped();
    };

    private static void stopped() {
        if (mc.thePlayer.motionY == 0.38999999363422366D) {
            mc.thePlayer.motionY -= 0.0568D;
        }
    }

    private boolean canBhop() {
        return !mc.thePlayer.capabilities.isFlying
                && !scaffold()
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isPotionActive(Potion.jump)
                && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                && mc.currentScreen == null;
    }

    private boolean bhop() {
        boolean setback = mc.thePlayer.ticksSinceTeleport < 10;

        if (mc.thePlayer.onGround) {
            if (mc.thePlayer.motionY == FALL_MOTION) {
                mc.thePlayer.jump();
                ++jumps;
                double current = MovementUtils.getSpeed();
                double d = (0.48475D + potionBoost()) - ThreadLocalRandom.current().nextDouble() * 1.0E-4D;
                boolean forward = mc.thePlayer.movementInput.moveForward > 0.0F;
                if (!forward) d += 0.03D;
                if (setback) {
                    d = current;
                } else if (jumps == 1) {
                    d *= 0.95D;
                }
                HopUtil.strafe(d);
            }
            return true;
        }

        if (setback) return false;

        switch ((int) Math.round(mc.thePlayer.motionY % 10.0D * 10000.0D)) {
            case 3332 -> mc.thePlayer.motionY += 0.0568D;
            case 2193 -> mc.thePlayer.motionY -= 0.13D;
            case 91 -> mc.thePlayer.motionY -= 0.2D + Double.parseDouble("0.000000" + idOffset());
        }
        return true;
    }

    private static double potionBoost() {
        int level = HopUtil.speedLevel();
        return switch (level) {
            case 0 -> 0.0D;
            case 1 -> 0.03D;
            case 2 -> 1.1D / 10.0D;
            default -> 0.06D * level;
        };
    }

    private static int idOffset() {
        int id = Math.abs(mc.thePlayer.getEntityId());
        return id > 999 ? id + 213 : id * 99;
    }
}
