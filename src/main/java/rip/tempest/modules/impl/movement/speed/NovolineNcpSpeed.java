package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockLiquid;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import static rip.tempest.utils.Util.mc;

public final class NovolineNcpSpeed extends SpeedBase {
    public enum JumpMode {
        Reduced, Vanilla
    }

    private static final double BASE_SPEED = 0.28630206268501246D;
    private static final double HOP_MULTIPLIER = 2.139999980926514D;
    private static final double AFTER_HOP_DROP = 0.81999D;
    private static final double JUMP_REDUCED = 0.399999986886978D, JUMP_VANILLA = 0.419999986886978D;
    private static final double DOWN_MOTION = -0.099999986886978D;

    private ModeProperty<JumpMode> jumpMode;
    private Property<Boolean> downMotion;
    private Property<Boolean> lagbackCheck;
    private Property<Boolean> damageBoost;
    private NumberProperty timer;
    private NumberProperty friction;
    private Runnable turnOff;

    private boolean groundLastMove, timerSet;
    private volatile boolean boostWaiting;
    private volatile double boost;

    public NovolineNcpSpeed(ModeProperty<JumpMode> jumpMode, Property<Boolean> downMotion, Property<Boolean> lagbackCheck,
                            Property<Boolean> damageBoost, NumberProperty timer, NumberProperty friction, Runnable turnOff) {
        this.jumpMode = jumpMode;
        this.downMotion = downMotion;
        this.lagbackCheck = lagbackCheck;
        this.damageBoost = damageBoost;
        this.timer = timer;
        this.friction = friction;
        this.turnOff = turnOff;
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @EventLink
    public final Listener<JumpEvent> onJump = e -> {
        if (inGame() && MovementUtils.isMoving() && !liquidAt(mc.thePlayer.posY + 0.01D)) {
            e.setCancelled();
        }
    };

    @Override
    protected void onEnable() {
        super.onEnable();
        moveSpeed = inGame() ? startSpeed() : BASE_SPEED;
        groundLastMove = false;
        timerSet = false;
        boostWaiting = false;
    }

    @Override
    protected void update(MotionEvent e) {
        super.update(e);
        if (!e.isPre() || !inGame()) return;

        if (groundSpoof() && (mc.thePlayer.ticksExisted % 2 == 0 || stage == 0)) {
            e.setOnGround(true);
        }

        double wanted = timer.getValue();
        if (MovementUtils.isMoving() && wanted > 1.0D) {
            mc.timer.timerSpeed = (float) wanted;
            timerSet = true;
        } else if (timerSet) {
            mc.timer.timerSpeed = 1.0F;
            timerSet = false;
        }
    }

    private boolean groundSpoof() {
        return ServerUtils.isOnHypixel() && lastDist > 0.0D && mc.thePlayer.fallDistance < 1 + effectLevel(Potion.jump);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        if (!MovementUtils.isMoving()) {
            e.setX(0.0D);
            e.setZ(0.0D);
            return;
        }

        final double base = currentBase();

        if (mc.thePlayer.onGround && !liquidAt(mc.thePlayer.posY - 0.01D)) {
            stage = 0;
            double jump = jumpMode.getValue() == JumpMode.Reduced ? JUMP_REDUCED : JUMP_VANILLA;
            e.setY(mc.thePlayer.motionY = jump + 0.1D * effectLevel(Potion.jump));
            moveSpeed = base * HOP_MULTIPLIER;
        }

        if (groundLastMove) {
            moveSpeed = lastDist - AFTER_HOP_DROP * (lastDist - base);
        }

        stage++;
        moveSpeed -= lastDist / (friction.getValue() - 1.0D);

        if (downMotion.getValue() && stage == 3 && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.motionY = DOWN_MOTION;
        }

        if (damageBoost.getValue() && boostWaiting && !mc.thePlayer.isPotionActive(Potion.poison) && !mc.thePlayer.isBurning()) {
            moveSpeed += boost;
            boostWaiting = false;
        }

        MovementUtils.setSpeed(e, Math.max(moveSpeed, base));
        groundLastMove = mc.thePlayer.onGround;
    }

    @Override
    protected void receive(PacketReceiveEvent e) {
        super.receive(e);
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            if (lagbackCheck.getValue()) mc.addScheduledTask(turnOff);
        } else if (e.getPacket() instanceof S27PacketExplosion explosion && explosion.getAffectedBlockPositions().isEmpty()) {
            boost = Math.hypot(0.15D + explosion.func_149149_c() / 8500.0F, 0.15D + explosion.func_149147_e() / 8500.0F);
            boostWaiting = true;
        }
    }

    private static double currentBase() {
        double multiplier = liquidAt(mc.thePlayer.posY + 0.01D) ? 0.5D : mc.thePlayer.movementInput.sneak ? 0.8D : 1.0D;
        return potionSpeed(BASE_SPEED) * multiplier;
    }

    private static double startSpeed() {
        return potionSpeed(mc.thePlayer.isSprinting() ? BASE_SPEED : 0.2202643217126144D);
    }

    private static double potionSpeed(double speed) {
        int level = effectLevel(Potion.moveSpeed) - effectLevel(Potion.moveSlowdown);

        return mc.thePlayer.isPotionActive(Potion.moveSpeed) ? speed * (1.0D + 0.2D * level) : speed;
    }

    private static int effectLevel(Potion potion) {
        return mc.thePlayer.isPotionActive(potion) ? mc.thePlayer.getActivePotionEffect(potion).getAmplifier() + 1 : 0;
    }

    private static boolean liquidAt(double y) {
        BlockPos pos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(y), MathHelper.floor_double(mc.thePlayer.posZ));
        return mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid;
    }
}
