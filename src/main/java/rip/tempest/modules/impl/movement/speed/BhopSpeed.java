package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static rip.tempest.utils.Util.mc;

public final class BhopSpeed extends SpeedBase {
    public enum Kind {Bhop, LowHop, LowHopFast, Vanilla, VLAbuse, Zonecraft, Vulcan, Ghostly}

    public enum TicksMode {
        Equals0("ticks == 0"), NotEquals0("ticks != 0");

        private final String display;

        TicksMode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final double[] LOW_HOP_Y = {round(0.4, 0.001), round(0.71, 0.001), round(0.75, 0.001), round(0.55, 0.001), round(0.41, 0.001)};
    private static final long FLAG_MS = 2500;

    private Kind kind;
    private NumberProperty timer, randomTimer, above2Ticks, above2Start;
    private ModeProperty<TicksMode> above2Mode;

    private NumberProperty boost;

    private boolean shouldBoost, disabled;
    private long flaggedAt;
    private double lastReportedX, lastReportedZ;

    public BhopSpeed(Kind kind, NumberProperty timer, NumberProperty randomTimer, NumberProperty above2Ticks,
                        ModeProperty<TicksMode> above2Mode, NumberProperty above2Start) {
        this.kind = kind;
        this.timer = timer;
        this.randomTimer = randomTimer;
        this.above2Ticks = above2Ticks;
        this.above2Mode = above2Mode;
        this.above2Start = above2Start;
    }

    public BhopSpeed withBoost(NumberProperty boost) {
        this.boost = boost;
        return this;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (inGame()) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
        reset();
    }

    private void reset() {
        stage = 0;
        disabled = false;
        if (inGame()) mc.timer.timerSpeed = 1.0f;
        flaggedAt = 0;
        moveSpeed = baseMoveSpeed();
        shouldBoost = false;
    }

    @EventLink
    public final Listener<JumpEvent> onJump = e -> {
        if (!disabled) e.setCancelled();
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook && !disabled) {
            flaggedAt = System.currentTimeMillis();
            if (inGame()) mc.timer.timerSpeed = 1.0f;
            disabled = true;
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!inGame()) return;
        if (disabled && System.currentTimeMillis() - flaggedAt >= FLAG_MS) reset();
        if (disabled) return;
        if (e.isPre()) {
            lastReportedX = e.getPosX();
            lastReportedZ = e.getPosZ();
        }
        lastDist = Math.hypot(mc.thePlayer.posX - mc.thePlayer.prevPosX, mc.thePlayer.posZ - mc.thePlayer.prevPosZ);

        if (kind == Kind.Vulcan && e.isPre()) {
            if (MovementUtils.isMoving() && mc.thePlayer.onGround) {
                double speed = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
                boolean boost = mc.thePlayer.isPotionActive(Potion.moveSpeed);
                switch (stage) {
                    case 1 -> {
                        moveSpeed = 0.42f;
                        speed = boost ? speed + 0.2 : 0.48;
                        e.setOnGround(true);
                    }
                    case 2 -> {
                        speed = boost ? speed * 0.71 : 0.19;
                        moveSpeed -= 0.0784f;
                        e.setOnGround(false);
                    }
                    default -> {
                        stage = 0;
                        speed /= boost ? 0.64 : 0.66;
                        e.setOnGround(true);
                    }
                }
                MovementUtils.setSpeed(speed);
                stage++;
                e.setPosY(e.getPosY() + moveSpeed);
            } else {
                stage = 0;
            }
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame() || disabled) return;
        double base = baseMoveSpeed();
        boolean moving = MovementUtils.isMoving();
        switch (kind) {
            case Bhop -> {
                if (!moving) {
                    e.setFriction(0);
                    return;
                }
                if (above2Start.getValue() >= above2Ticks.getValue()) above2Start.setValue(above2Ticks.getValue() - 1);
                if (!onGround(1 / 64) && !mc.thePlayer.onGround && timerOn()) {
                    float amount = timer.getValue().floatValue();
                    if (amount >= 2.f) {
                        int every = above2Ticks.getValue().intValue();
                        boolean high = above2Mode.getValue() == TicksMode.Equals0
                                ? mc.thePlayer.ticksExisted % every == 0
                                : mc.thePlayer.ticksExisted % every != above2Start.getValue().intValue();
                        mc.timer.timerSpeed = (high ? amount : 1.6f) + randomTimer();
                    } else {
                        mc.timer.timerSpeed = amount + randomTimer();
                    }
                }
                if (mc.thePlayer.onGround) {
                    shouldBoost = false;
                    mc.thePlayer.motionY = jumpHeight(0.42F);
                    stage = 0;
                }
                hopStages(base, shouldBoost ? 1.73 : 2.145);
                partialStrafe(e, (float) Math.max(base, moveSpeed), (float) (0.235F + Math.random() / 500));
            }
            case LowHop, LowHopFast -> {
                float faster = kind == Kind.LowHopFast && boost != null ? boost.getValue().floatValue() : 1f;
                boolean lowHop = kind != Kind.LowHopFast && !mc.thePlayer.isPotionActive(Potion.jump) && !mc.thePlayer.isCollidedHorizontally && lowHopClear();
                if (!moving) {
                    e.setFriction(0);
                    return;
                }
                if (!mc.thePlayer.onGround && shouldBoost && mc.thePlayer.fallDistance < 0.54) {
                    mc.thePlayer.motionY = lowHopY(mc.thePlayer.motionY, round(mc.thePlayer.posY - (int) mc.thePlayer.posY, 0.001));
                }
                if (!onGround(1 / 64) && timer.getValue() != 0) mc.timer.timerSpeed = timer.getValue().floatValue();
                else if (onGround(1 / 64)) mc.timer.timerSpeed = 1.0f;
                if (mc.thePlayer.onGround) {
                    shouldBoost = lowHop;
                    mc.thePlayer.motionY = shouldBoost ? jumpHeight(0.4F) : jumpHeight(0.42F);
                    stage = 0;
                }
                hopStages(base, shouldBoost ? 1.7 : 2.145);
                partialStrafe(e, (float) Math.max(base, moveSpeed) * faster, (float) (0.235F + Math.random() / 500));
            }
            case Vanilla -> {
                if (!moving) return;
                if (mc.thePlayer.onGround) mc.thePlayer.motionY = 0.42;
                if (!onGround(1 / 64 * 4)) mc.thePlayer.motionY -= 0.22;
                mc.thePlayer.stepHeight = mc.thePlayer.isCollidedHorizontally ? 2f : 0.5f;
                setMotion(e, 2 * ThreadLocalRandom.current().nextDouble(0.93, 0.99));
            }
            case VLAbuse -> {
                if (!moving) return;
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42F;
                    moveSpeed = base * 2.1;
                    shouldBoost = true;
                    double[] xz = yawPos(direction(), -0.063);
                    for (int i = 0; i < 3; i++) {
                        PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX - xz[0], mc.thePlayer.posY, mc.thePlayer.posZ - xz[1], true));
                        PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true));
                    }
                } else if (shouldBoost) {
                    moveSpeed = base * 1.15D;
                    shouldBoost = false;
                } else if (mc.thePlayer.fallDistance < 0.9F) {
                    switch (stage) {
                        case 1 -> moveSpeed = base * 1.7D;
                        case 2 -> moveSpeed = base * 1.45D;
                        default -> {
                            moveSpeed = base * 1.05D;
                            stage = 0;
                        }
                    }
                    stage++;
                } else {
                    moveSpeed *= 0.905D;
                }
                setMotion(e, (float) moveSpeed);
                if (mc.thePlayer.ticksExisted % 2 == 0) moveSpeed += 1e-7;
            }
            case Zonecraft -> {
                C02PacketUseEntity attack = new C02PacketUseEntity(mc.thePlayer, C02PacketUseEntity.Action.ATTACK);
                attack.entityId = -1;
                PacketUtils.sendSilentPacket(attack);
                if (moving && mc.thePlayer.onGround) {
                    if (shouldBoost) {
                        mc.thePlayer.motionY = .42F;
                        shouldBoost = false;
                    } else {
                        shouldBoost = true;
                    }
                    moveSpeed += 0.525;
                } else if (mc.thePlayer.fallDistance == 0) {
                    mc.thePlayer.motionY = -.4804F;
                }
                moveSpeed = Math.min(moveSpeed, 6.5);
                moveSpeed *= 0.91;
                setMotion(e, Math.max(moveSpeed, applyPotion(0.2873)));
                if (!moving || mc.thePlayer.isCollidedHorizontally) moveSpeed = 0.26;
            }
            case Ghostly -> {
                stage++;
                if (moving) setMotion(e, stage % 3 == 0 ? 3 : baseMoveSpeed());
            }
            default -> {
            }
        }
    };

    private void hopStages(double base, double first) {
        switch (stage) {
            case 0 -> moveSpeed = base * first;
            case 1 -> moveSpeed *= 0.579;
            case 4 -> moveSpeed = base * 1.2;
            default -> moveSpeed = moveSpeed / 100 * 98.5f;
        }
        stage++;
    }

    private boolean timerOn() {
        return kind == Kind.Bhop && timer.getValue().floatValue() > 1.0f;
    }

    private float randomTimer() {
        float r = randomTimer.getValue().floatValue();
        return (float) ThreadLocalRandom.current().nextDouble(-r, r);
    }

    private static void setMotion(StrafeEvent e, double speed) {
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;
        speed *= e.getStrafe() != 0 && e.getForward() != 0 ? 0.975 : 1;
        e.setFriction((float) speed);
    }

    private static void partialStrafe(StrafeEvent e, float friction, float strafeComponent) {
        float remainder = 1F - strafeComponent;
        if (e.getForward() != 0 && e.getStrafe() != 0) friction *= 0.98F;
        if (mc.thePlayer.onGround) {
            setMotion(e, friction);
        } else {
            mc.thePlayer.motionX *= strafeComponent;
            mc.thePlayer.motionZ *= strafeComponent;
            e.setFriction(friction * remainder);
        }
    }

    private static double baseMoveSpeed() {
        double base = 0.2873;
        if (mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            base *= 1.0 + 0.2 * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        }
        return base;
    }

    private static double applyPotion(double base) {
        if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) base *= 1 + 0.2 * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        return base;
    }

    private static double jumpHeight(double height) {
        if (mc.thePlayer.isPotionActive(Potion.jump)) return height + (mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        return height;
    }

    private static boolean onGround(double height) {
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0, -height, 0)).isEmpty();
    }

    private static double round(double value, double inc) {
        double half = inc / 2.0, floored = Math.floor(value / inc) * inc;
        return value >= floored + half ? new BigDecimal(Math.ceil(value / inc) * inc).doubleValue() : new BigDecimal(floored).doubleValue();
    }

    private double lowHopY(double motionY, double fromGround) {
        if (fromGround == LOW_HOP_Y[0]) return 0.31;
        if (fromGround == LOW_HOP_Y[1]) return 0.04;
        if (fromGround == LOW_HOP_Y[2]) return -0.2;
        if (fromGround == LOW_HOP_Y[3]) return -0.14;
        if (fromGround == LOW_HOP_Y[4]) return -0.2;
        return motionY;
    }

    private boolean lowHopClear() {
        float yaw = yawFromTo(rip.tempest.modules.impl.combat.TargetStrafeModule.runYaw(), lastReportedX, lastReportedZ, mc.thePlayer.posX, mc.thePlayer.posZ);
        Vec3 start = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + LOW_HOP_Y[2], mc.thePlayer.posZ);
        double rad = Math.toRadians(yaw);
        Vec3 end = start.addVector(-Math.sin(rad) * 5, 0, Math.cos(rad) * 5);
        MovingObjectPosition ray = mc.theWorld.rayTraceBlocks(start, end, false, true, true);
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || ray.hitVec == null) return true;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();
        if (mc.theWorld.checkBlockCollision(bb.offset(bb.minX - ray.hitVec.xCoord, bb.minY - ray.hitVec.yCoord, bb.minZ - ray.hitVec.zCoord))) return false;
        return start.distanceTo(ray.hitVec) > 4.0;
    }

    private static float yawFromTo(float yaw, double srcX, double srcZ, double dstX, double dstZ) {
        float to = (float) (Math.atan2(dstZ - srcZ, dstX - srcX) * 180.0 / Math.PI) - 90.0F;
        return yaw + MathHelper.wrapAngleTo180_float(to - yaw);
    }

    private static float direction() {
        float yaw = rip.tempest.modules.impl.combat.TargetStrafeModule.runYaw();
        if (mc.thePlayer.moveForward < 0) yaw += 180;
        float forward = 1;
        if (mc.thePlayer.moveForward < 0) forward = -.5F;
        else if (mc.thePlayer.moveForward > 0) forward = .5F;
        if (mc.thePlayer.moveStrafing > 0) yaw -= 90 * forward;
        if (mc.thePlayer.moveStrafing < 0) yaw += 90 * forward;
        return (float) Math.toRadians(yaw);
    }

    private static double[] yawPos(float yaw, double value) {
        return new double[]{-MathHelper.sin(yaw) * value, MathHelper.cos(yaw) * value};
    }
}
