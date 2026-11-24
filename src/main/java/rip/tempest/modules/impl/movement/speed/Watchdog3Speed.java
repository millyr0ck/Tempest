package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockIce;
import net.minecraft.block.BlockPackedIce;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;

import static rip.tempest.utils.Util.mc;

public final class Watchdog3Speed extends SpeedBase {
    private NumberProperty damageBooster, multiplyFactor, multiplyTicks, strafeAmount;
    private Property<Boolean> multiplyTick;

    private double lastDistance;
    private double lastX, lastZ;
    private boolean jumped, timerReset, wasSprinting;
    private int tickCounter;

    public Watchdog3Speed(NumberProperty damageBooster, NumberProperty multiplyFactor, NumberProperty multiplyTicks,
                                NumberProperty strafeAmount, Property<Boolean> multiplyTick) {
        this.damageBooster = damageBooster;
        this.multiplyFactor = multiplyFactor;
        this.multiplyTicks = multiplyTicks;
        this.strafeAmount = strafeAmount;
        this.multiplyTick = multiplyTick;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        lastDistance = 0;
        stage = 2;
        tickCounter = 0;
        jumped = false;
        timerReset = true;
        if (inGame()) {
            moveSpeed = baseSpeed3();
            lastX = mc.thePlayer.posX;
            lastZ = mc.thePlayer.posZ;
            wasSprinting = mc.thePlayer.isSprinting();
        }
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (inGame() && mc.thePlayer.isSprinting() && !wasSprinting) {
            PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            mc.thePlayer.serverSprintState = true;
        }
        moveSpeed = 0;
    }

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        lastDistance = Math.hypot(mc.thePlayer.posX - mc.thePlayer.prevPosX, mc.thePlayer.posZ - mc.thePlayer.prevPosZ);
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame()) return;
        double base = baseSpeed3();
        boolean moving = MovementUtils.isMoving();
        if (!moving) {
            mc.timer.timerSpeed = 1.0F;
            moveSpeed = base;
            return;
        }

        mc.thePlayer.setSprinting(true);
        switch (stage) {
            case 2 -> {
                if (mc.thePlayer.onGround) {
                    float jump = 0.4011F;
                    if (mc.thePlayer.isPotionActive(Potion.jump)) {
                        jump += (mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
                    }
                    e.setY(mc.thePlayer.motionY = jump);
                    jumped = true;
                    moveSpeed = base * 2.0356D;
                }
            }
            case 3 -> {
                double boost = this.damageBooster.getValue();
                double friction = boost > 0.1D && mc.thePlayer.hurtTime > 0 ? 1.0D - boost : 0.73D;
                moveSpeed = lastDistance - friction * (lastDistance - base);
            }
            default -> {
                if ((!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, mc.thePlayer.motionY, 0.0D)).isEmpty()
                        || mc.thePlayer.isCollidedVertically) && stage > 0) {
                    stage = 1;
                }
                moveSpeed = lastDistance - lastDistance / 100.0D;
            }
        }

        if (stage >= 6 && wasSprinting) {
            PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
            wasSprinting = false;
        }

        Block under = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.001D, mc.thePlayer.posZ)).getBlock();
        if (moveSpeed > 1.1D && (under instanceof BlockStairs || under instanceof BlockSlab)) moveSpeed = base;
        moveSpeed = Math.max(moveSpeed, base);

        if (!mc.thePlayer.isCollidedVertically && stage != 2) jumped = false;
        if (jumped) tickCounter = this.multiplyTicks.getValue().intValue();
        if (tickCounter > 0) {
            timerReset = false;
            float factor = this.multiplyFactor.getValue().floatValue();
            mc.timer.timerSpeed = this.multiplyTick.getValue() && mc.thePlayer.ticksExisted % 2 != 0 ? 1.0F : factor;
            tickCounter--;
        } else if (!timerReset) {
            mc.timer.timerSpeed = 1.0F;
            timerReset = true;
        }

        strafeTowards(e, moveSpeed, this.strafeAmount.getValue());
        stage++;
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame() || !(e.getPacket() instanceof C0BPacketEntityAction)) return;
        C0BPacketEntityAction.Action action = ((C0BPacketEntityAction) e.getPacket()).getAction();
        if (MovementUtils.isMoving() && (action == C0BPacketEntityAction.Action.START_SPRINTING || action == C0BPacketEntityAction.Action.STOP_SPRINTING)) {
            e.setCancelled(true);
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            moveSpeed = 0;
            stage = 2;
        }
    };

    private static double baseSpeed3() {
        double speed = 0.221D;
        if (mc.thePlayer.isInWeb) speed *= 0.4751131221719457D;
        if (mc.thePlayer.isInWater()) speed *= 0.5203619909502263D;
        if (mc.thePlayer.isSneaking()) speed *= 0.5882352941176471D;
        if (mc.thePlayer.isSprinting()) speed *= 1.3D;
        PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed);
        if (effect != null) speed *= 1.0D + 0.2D * (effect.getAmplifier() + 1);
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        Block block = mc.theWorld.getBlockState(below).getBlock();
        if (block instanceof BlockIce || block instanceof BlockPackedIce) speed *= 2.5D;
        return speed;
    }

    private void strafeTowards(MovePlayerEvent e, double speed, double amount) {
        double movedX = mc.thePlayer.posX - lastX, movedZ = mc.thePlayer.posZ - lastZ;
        double movedDistance = Math.sqrt(movedX * movedX + movedZ * movedZ);
        double[] moved = toVector(angleBetween(lastX, lastZ, mc.thePlayer.posX, mc.thePlayer.posZ), movedDistance);
        double yaw = moveYaw360();
        double[] wanted = toVector(yaw, speed);
        double[] result;
        if (Math.hypot(moved[0] - wanted[0], moved[1] - wanted[1]) > amount) {
            double[] step = toVector(yaw, amount);
            result = new double[]{moved[0] + step[0], moved[1] + step[1]};
        } else {
            result = wanted;
        }
        double length = Math.hypot(result[0], result[1]);
        if (length > speed && length > 0) {
            result[0] = result[0] / length * speed;
            result[1] = result[1] / length * speed;
        }
        e.setX(mc.thePlayer.motionX = result[0]);
        e.setZ(mc.thePlayer.motionZ = result[1]);
    }

    private static double angleBetween(double x1, double z1, double x2, double z2) {
        if (x1 == x2 && z1 == z2) return -1.0D;
        double angle = Math.atan2(x2 - x1, z1 - z2) + Math.PI;
        if (angle < 0) angle += Math.PI * 2;
        return Math.toDegrees(angle);
    }

    private static double[] toVector(double angle, double length) {
        return new double[]{length * Math.cos(Math.toRadians(angle + 90.0D)), length * Math.sin(Math.toRadians(angle + 90.0D))};
    }

    private static double moveYaw360() {
        float yaw = MathHelper.wrapAngleTo180_float(rip.tempest.modules.impl.combat.TargetStrafeModule.runYaw());
        MovementInput input = mc.thePlayer.movementInput;
        float strafe = input.moveStrafe, forward = input.moveForward;
        if (forward != 0.0F) {
            if (strafe < 0.0F) yaw += forward < 0.0F ? 135.0F : 45.0F;
            else if (strafe > 0.0F) yaw -= forward < 0.0F ? 135.0F : 45.0F;
            else if (forward < 0.0F) yaw -= 180.0F;
        } else if (strafe < 0.0F) {
            yaw += 90.0F;
        } else if (strafe > 0.0F) {
            yaw -= 90.0F;
        }
        double d = MathHelper.wrapAngleTo180_float(yaw);
        while (d < 0) d += 360.0D;
        return d;
    }
}
