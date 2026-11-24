package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockIce;
import net.minecraft.block.BlockPackedIce;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;

import static rip.tempest.utils.Util.mc;

public final class WatchdogRadiumSpeed extends SpeedBase {
    public enum FrictionMode {
        NCP,
        Fast
    }

    private static final double BUNNY_SLOPE = 0.72D;
    private static final double MAX_DIST = 1.85F;
    private static final double BUNNY_DIV_FRICTION = 160.0D - 1.0E-3D;
    private static final double ICE_MOD = 2.5F;
    private static final double AIR_FRICTION = 0.98F;
    private static final double WATER_FRICTION = 0.89F;
    private static final double LAVA_FRICTION = 0.535F;
    private static final double GROUND_PACKET_RAISE = 0.015625F;

    private ModeProperty<FrictionMode> frictionMode;
    private Property<Boolean> glide, explosionBoost, useTimer;
    private NumberProperty onGroundTimer;

    private boolean wasOnGround;
    private int ticksSinceLastPacket, damageBoostCooldown;
    private volatile double damageBoost;
    private volatile int ticksSinceDamage;

    public WatchdogRadiumSpeed(ModeProperty<FrictionMode> frictionMode, Property<Boolean> glide, Property<Boolean> explosionBoost,
                               Property<Boolean> useTimer, NumberProperty onGroundTimer) {
        this.frictionMode = frictionMode;
        this.glide = glide;
        this.explosionBoost = explosionBoost;
        this.useTimer = useTimer;
        this.onGroundTimer = onGroundTimer;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        wasOnGround = false;
        damageBoost = 0.0D;
        ticksSinceDamage = 0;
        damageBoostCooldown = 0;
        ticksSinceLastPacket = 0;
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
        if (explosionBoost.getValue() && e.getPacket() instanceof S27PacketExplosion packet) {
            double x = packet.func_149149_c();
            double z = packet.func_149147_e();
            e.setCancelled();
            damageBoost = Math.sqrt(x * x + z * z);
            ticksSinceDamage = 0;
        }
    }

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame()) {
            if (damageBoost > 0.0D) {
                ticksSinceDamage++;
            } else {
                damageBoostCooldown++;
            }
            ticksSinceLastPacket++;

            if (e.isOnGround() && ServerUtils.isOnHypixel()) {
                if (inPit()) {
                    e.setOnGround(false);
                    e.setPosY(e.getPosY() + GROUND_PACKET_RAISE);
                } else if (!blockAbove() && ticksSinceLastPacket > 5) {
                    PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                            e.getPosX(), e.getPosY() + GROUND_PACKET_RAISE, e.getPosZ(), false));
                    ticksSinceLastPacket = 0;
                }
            }

            if (glide.getValue() && mc.thePlayer.fallDistance < 1.0F) {
                mc.thePlayer.motionY += 0.005D;
            }
        }
        super.update(e);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        if (!MovementUtils.isMoving()) {
            if (useTimer.getValue()) mc.timer.timerSpeed = 1.0F;
            return;
        }

        double base = baseMoveSpeed();

        if (MovementUtils.isOnGround() && !wasOnGround) {
            wasOnGround = true;
            e.setY(mc.thePlayer.motionY = jumpHeight());

            if (onIce()) base *= ICE_MOD;

            moveSpeed = Math.max(base * 1.78D, Math.min(base * 1.9D, lastDist * MAX_DIST));

            if (useTimer.getValue()) mc.timer.timerSpeed = onGroundTimer.getValue().floatValue();
        } else if (wasOnGround) {
            double difference = (BUNNY_SLOPE + 0.02D * jumpBoostLevel()) * (lastDist - base);
            moveSpeed = lastDist - difference;
            wasOnGround = false;
        } else {
            moveSpeed = frictionMode.getValue() == FrictionMode.NCP
                    ? friction(moveSpeed, lastDist, base)
                    : lastDist - lastDist / BUNNY_DIV_FRICTION;

            if (useTimer.getValue()) mc.timer.timerSpeed = 1.085F;
        }

        if (explosionBoost.getValue() && damageBoost > 0.0D && damageBoostCooldown > 5) {
            if (ticksSinceDamage < 5) {
                moveSpeed += damageBoost;
                damageBoostCooldown = 0;
            }
            damageBoost = 0.0D;
        }

        MovementUtils.setSpeed(e, Math.max(base, moveSpeed));
    }

    private static double baseMoveSpeed() {
        final EntityPlayerSP player = mc.thePlayer;
        double base = player.isSneaking() ? MovementUtils.WALK_SPEED * MovementUtils.MOD_SNEAK
                : MovementUtils.canSprint(true) ? MovementUtils.WALK_SPEED * MovementUtils.MOD_SPRINTING
                : MovementUtils.WALK_SPEED;

        PotionEffect speed = player.getActivePotionEffect(Potion.moveSpeed);
        PotionEffect slowness = player.getActivePotionEffect(Potion.moveSlowdown);
        if (speed != null) base *= 1.0D + 0.2D * (speed.getAmplifier() + 1);
        if (slowness != null) base /= 1.0D + 0.2D * (slowness.getAmplifier() + 1);

        if (player.isInWater()) {
            base *= MovementUtils.MOD_SWIM;
            int depthStrider = EnchantmentHelper.getDepthStriderModifier(player);
            if (depthStrider > 0) {
                base *= MovementUtils.MOD_DEPTH_STRIDER[depthStrider];
            }
        } else if (player.isInLava()) {
            base *= MovementUtils.MOD_SWIM;
        }
        return base;
    }

    private static double jumpHeight() {
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return MovementUtils.WALK_SPEED * MovementUtils.MOD_SWIM + 0.02F;
        }
        return 0.42F + jumpBoostLevel() * 0.1F;
    }

    private static int jumpBoostLevel() {
        PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.jump);
        return effect != null ? effect.getAmplifier() + 1 : 0;
    }

    private static double friction(double moveSpeed, double lastDist, double base) {
        double bunny = lastDist - lastDist / BUNNY_DIV_FRICTION;
        double carry = lastDist - (moveSpeed - lastDist) / 33.3D;
        double materialFriction = mc.thePlayer.isInWater() ? WATER_FRICTION : mc.thePlayer.isInLava() ? LAVA_FRICTION : AIR_FRICTION;
        double drag = lastDist - base * (1.0D - materialFriction);
        return Math.min(bunny, Math.min(carry, drag));
    }

    private static boolean onIce() {
        Block under = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ)).getBlock();
        return under instanceof BlockIce || under instanceof BlockPackedIce;
    }

    private static boolean blockAbove() {
        return mc.theWorld.checkBlockCollision(mc.thePlayer.getEntityBoundingBox().addCoord(0.0D, 1.0D, 0.0D));
    }

    private static boolean inPit() {
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        return objective != null
                && "THE HYPIXEL PIT".equals(EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()).trim());
    }
}
