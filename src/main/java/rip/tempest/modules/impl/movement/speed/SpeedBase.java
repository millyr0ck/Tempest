package rip.tempest.modules.impl.movement.speed;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.utils.mc.MovementUtils;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;

import static rip.tempest.utils.Util.mc;

abstract class SpeedBase extends SubMode {
    protected int stage;
    protected double moveSpeed;
    protected double lastDist;

    @Override
    protected void onEnable() {
        lastDist = 0.0D;
        moveSpeed = 0.0D;
        stage = 0;
    }

    @Override
    protected void onDisable() {
        if (inGame()) mc.timer.timerSpeed = 1.0F;
    }

    protected void move(MovePlayerEvent e) {
    }

    protected void update(MotionEvent e) {
        if (!e.isPre() || !inGame()) return;

        double xDist = mc.thePlayer.posX - mc.thePlayer.prevPosX;
        double zDist = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        lastDist = Math.sqrt(xDist * xDist + zDist * zDist);
        if (lastDist > 5.0D) lastDist = 0.0D;
    }

    protected void receive(PacketReceiveEvent e) {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            lastDist = 0.0D;
        }
    }

    protected final void hopCycle(MovePlayerEvent e, float jump, double groundMultiplier, double friction, double coastDivisor) {
        switch (stage) {
            case 0 -> {
                stage++;
                lastDist = 0.0D;
            }
            case 2 -> {
                lastDist = 0.0D;
                if (MovementUtils.isMoving() && mc.thePlayer.onGround) {
                    e.setY(mc.thePlayer.motionY = jumpMotion(jump));
                    moveSpeed *= groundMultiplier;
                }
            }
            case 3 -> moveSpeed = lastDist - friction * (lastDist - baseSpeed());
            default -> {
                stage++;
                if (blocked()) stage = MovementUtils.isMoving() ? 1 : 0;
                moveSpeed = lastDist - lastDist / coastDivisor;
            }
        }

        moveSpeed = Math.max(moveSpeed, baseSpeed());
        setSpeed(e, moveSpeed);
        stage++;
    }

    protected static void offGroundHeight(MotionEvent e) {
        if (e.getPosY() % 0.015625D == 0.0D) {
            e.setPosY(e.getPosY() + 5.3424E-4D);
            e.setOnGround(false);
        }
        if (mc.thePlayer.motionY > 0.3D) {
            e.setOnGround(true);
        }
    }

    protected static void setSpeed(MovePlayerEvent e, double speed) {
        MovementUtils.setSpeed(e, speed, MovementUtils.hypixelYawJitter());
    }

    protected static boolean blocked() {
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().offset(0.0D, mc.thePlayer.motionY, 0.0D)).size() > 0
                || mc.thePlayer.isCollidedVertically;
    }

    protected static float jumpMotion(float base) {
        if (jumpPotion()) {
            base += (mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.101F;
        }
        return base;
    }

    protected static double baseSpeed() {
        double baseSpeed = 0.2873D;
        if (speedPotion()) {
            baseSpeed *= 1.0D + 0.2D * mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier();
        }
        return baseSpeed;
    }

    protected static double baseSpeed(double speed) {
        if (speedPotion()) {
            speed *= 1.0D + 0.2D * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        }
        return speed;
    }

    protected static boolean speedPotion() {
        return mc.thePlayer.isPotionActive(Potion.moveSpeed);
    }

    protected static boolean jumpPotion() {
        return mc.thePlayer.isPotionActive(Potion.jump);
    }

    protected static boolean scaffold() {
        return Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled();
    }
}
