package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.impl.combat.AutoPotModule;
import rip.tempest.modules.impl.combat.TargetStrafeModule;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import static rip.tempest.utils.Util.mc;

public final class ExhibitionHypixelLowSpeed extends SpeedBase {
    private static final double BASE_SPEED = 0.28730000691562896D;
    private static final int SETBACK_TICKS = -15;

    private Property<Boolean> strafeFix, fastFall, firstSlow;
    private NumberProperty steps, velBoost;

    private int hops, ticks;
    private boolean reset;
    private float currentYaw;
    private volatile boolean setback;
    private volatile double velocityBoost;

    public ExhibitionHypixelLowSpeed(Property<Boolean> strafeFix, Property<Boolean> fastFall, Property<Boolean> firstSlow,
                                     NumberProperty steps, NumberProperty velBoost) {
        this.strafeFix = strafeFix;
        this.fastFall = fastFall;
        this.firstSlow = firstSlow;
        this.steps = steps;
        this.velBoost = velBoost;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        stage = 1;
        hops = 0;
        ticks = 0;
        reset = false;
        setback = false;
        velocityBoost = 0.0D;
        if (inGame()) {
            moveSpeed = defaultSpeed();
            currentYaw = mc.thePlayer.rotationYaw;
        }
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink(Priorities.VERY_LOW)
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void receive(PacketReceiveEvent e) {
        super.receive(e);
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            setback = true;
        } else if (e.getPacket() instanceof S12PacketEntityVelocity packet) {
            if (inGame() && packet.getEntityID() == mc.thePlayer.getEntityId()) {
                addVelocity(packet.getMotionX() / 8000.0D, packet.getMotionY() / 8000.0D, packet.getMotionZ() / 8000.0D);
            }
        } else if (e.getPacket() instanceof S27PacketExplosion packet) {
            addVelocity(packet.func_149149_c(), packet.func_149144_d(), packet.func_149147_e());
        }
    }

    private void addVelocity(double x, double y, double z) {
        if (x != 0.0D && y != 0.0D && z != 0.0D && velocityBoost == 0.0D) {
            velocityBoost = MathHelper.sqrt_double(x * x + z * z) * velBoost.getValue();
        }
    }

    @Override
    protected void update(MotionEvent e) {
        super.update(e);
        if (!e.isPre() || !inGame()) return;

        if (strafeFix.getValue() && ServerUtils.isOnHypixel() && lastDist > 0.0D) {
            boolean ground = e.isOnGround();
            if (hops > 0) strafeFix(e);
            if (ground) hops++;
        }
    }

    private void strafeFix(MotionEvent e) {
        EntityPlayerSP player = mc.thePlayer;
        boolean hitNextTick = !mc.theWorld.getCollidingBoundingBoxes(player,
                player.getEntityBoundingBox().expand(0.2D, 0.0D, 0.2D).offset(0.0D, -0.6D, 0.0D)).isEmpty();

        if (!scaffold()) {
            double rounded = incremental(roundToPlace(e.getPosY(), 3) % 1.0D, 0.001D);
            if (e.isOnGround() && roundToPlace(player.lastTickPosY - player.posY, 3) == 0.375D) {
                e.setOnGround(false);
            } else {
                if (stage < 5 || player.fallDistance == 0.0F) return;
                if (hitNextTick && rounded % 0.015625D == 0.0D) {
                    e.setPosY(incremental(e.getPosY(), 0.015625D));
                    e.setOnGround(true);
                }
            }
        }
        if (!e.isOnGround() && incremental(e.getPosY(), 0.015625D) % 1.0D == 0.015625D && player.fallDistance > 0.0F) {
            e.setPosY(incremental(e.getPosY(), 0.015625D));
            e.setOnGround(true);
        }
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        if (setback) {
            setback = false;
            stage = SETBACK_TICKS;
            moveSpeed = 0.0D;
            velocityBoost = 0.0D;
            lastDist = 0.0D;
        }
        if (stage > 3) reset = false;

        if (stage < 0) {
            stage++;
            reset = true;
            lastDist = 0.0D;
            velocityBoost = 0.0D;
            hops = 0;
            return;
        }

        boolean moving = mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
        if (!moving) {
            velocityBoost = 0.0D;
        }

        if (AutoPotModule.isPotting()) {
            stage = 0;
            hops = 0;
            return;
        }

        double floor = moveSpeed = defaultSpeed() * surfaceFactor();
        int current = stage;

        if (stage == 1 && mc.thePlayer.isCollidedVertically && moving) {
            moveSpeed = lastDist;
            ticks = 1;
            hops = 0;
        } else if (stage == 2 && mc.thePlayer.isCollidedVertically && mc.thePlayer.onGround && moving) {
            double y = ServerUtils.isOnHypixel() && strafeFix.getValue() ? 0.39999998688698D : 0.42F;
            PotionEffect jump = mc.thePlayer.getActivePotionEffect(Potion.jump);
            if (jump != null) y += (jump.getAmplifier() + 1) * 0.1F;
            e.setY(mc.thePlayer.motionY = y);
            velocityBoost /= 5.0D;

            moveSpeed = lastDist * 2.13050398D;

            float yaw = TargetStrafeModule.runYaw();
            double forward = mc.thePlayer.movementInput.moveForward;
            double strafe = mc.thePlayer.movementInput.moveStrafe;
            if (forward != 0.0D) {
                if (forward < 0.0D) yaw -= 180.0F;
                if (strafe > 0.0D) yaw += forward > 0.0D ? -43.51F : 43.51F;
                else if (strafe < 0.0D) yaw += forward > 0.0D ? 43.51F : -43.51F;
            } else if (strafe > 0.0D) {
                yaw -= 88.58F;
            } else if (strafe < 0.0D) {
                yaw += 88.58F;
            }
            currentYaw += MathHelper.wrapAngleTo180_float(yaw - currentYaw);
        } else if (stage == 3) {
            moveSpeed = lastDist - 0.819999D * (lastDist - floor);
        } else {
            if (stage == 7 && fastFall.getValue()) {
                e.setY(mc.thePlayer.motionY += -(0.18999F + 0.00000004F * Math.random()));
            }

            if (blocked() && stage > 0) {
                stage = moving ? 1 : 0;
                ticks = stage == 0 ? 1 : 0;
            }

            moveSpeed = lastDist - lastDist / 50.0D;
        }

        if (stage == 7) {
            e.setY(mc.thePlayer.motionY = -0.18517061160392762D);
        }

        moveSpeed = Math.max(moveSpeed, floor);

        if (velocityBoost != 0.0D && velocityBoost <= 0.05D) velocityBoost = 0.0D;
        if (velocityBoost != 0.0D && stage > 2) moveSpeed += velocityBoost;
        if (stage > 2) velocityBoost *= 0.66D;

        if (current > 1 && stage > 0) {
            applyMove(e);
        }

        if (moving) stage++;
    }

    private void applyMove(MovePlayerEvent e) {
        double forward = mc.thePlayer.movementInput.moveForward;
        double strafe = mc.thePlayer.movementInput.moveStrafe;

        if (forward == 0.0D && strafe == 0.0D) {
            e.setX(0.0D);
            e.setZ(0.0D);
            if (mc.thePlayer.onGround) currentYaw = mc.thePlayer.rotationYaw;
            return;
        }

        float yaw = TargetStrafeModule.runYaw();
        boolean circling = yaw != mc.thePlayer.rotationYaw;
        double scale;

        if (forward != 0.0D) {
            scale = forward > 0.0D ? 1.0D : 0.85F;
            if (forward < 0.0D) yaw -= 179.83F;
            if (strafe != 0.0D) {
                scale = 0.985F;
                if (strafe > 0.0D) yaw += forward > 0.0D ? -44.5933F : 44.5933F;
                else yaw += forward > 0.0D ? 44.5933F : -44.5933F;
            }
        } else {
            yaw += strafe > 0.0D ? -89.453F : 89.453F;
            scale = 0.75F;
        }
        if (circling) scale = 0.985F;

        float cap = steps.getValue().floatValue();
        currentYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(yaw - currentYaw), -cap, cap);

        e.setX(scale * moveSpeed * -Math.sin(Math.toRadians(currentYaw)));
        e.setZ(scale * moveSpeed * Math.cos(Math.toRadians(currentYaw)));
    }

    private double surfaceFactor() {
        if (mc.thePlayer.isInsideOfMaterial(Material.vine)) return 0.5D;
        if (mc.thePlayer.isSneaking()) return 0.8D;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return 0.54D;
        if (reset) return 0.45D;
        if (mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.1D, mc.thePlayer.posZ)).getBlock().slipperiness == 0.98F) return 2.4D;
        if (mc.thePlayer.getFoodStats().getFoodLevel() < 6) return 0.765D;
        if (ticks == 1 && firstSlow.getValue()) return 0.793D;
        return scaffold() ? 0.8D : 1.0D;
    }

    private static double defaultSpeed() {
        double base = BASE_SPEED;
        if (speedPotion()) base *= 1.0D + 0.15D * (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        return base;
    }

    private static double roundToPlace(double value, int places) {
        double scale = Math.pow(10.0D, places);
        return Math.round(value * scale) / scale;
    }

    private static double incremental(double val, double inc) {
        double one = 1.0D / inc;
        return Math.round(val * one) / one;
    }
}
