package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class FluxHypixelHopSpeed extends SpeedBase {
    private boolean wasOnGround, firstHop;
    private int airTicks;
    private Block lastCollidedBlock = Blocks.air;
    private volatile boolean takingKnockback;

    @Override
    protected void onEnable() {
        super.onEnable();
        wasOnGround = false;
        airTicks = 0;
        takingKnockback = true;
        firstHop = true;
        lastCollidedBlock = Blocks.air;
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void receive(PacketReceiveEvent e) {
        super.receive(e);
        if (e.getPacket() instanceof S12PacketEntityVelocity packet) {
            if (inGame() && packet.getEntityID() == mc.thePlayer.getEntityId()) takingKnockback = true;
        } else if (e.getPacket() instanceof S27PacketExplosion) {
            takingKnockback = true;
        }
    }

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame() && mc.thePlayer.onGround && mc.thePlayer.isCollidedVertically) {
            lastCollidedBlock = HopUtil.block(mc.thePlayer.posX, mc.thePlayer.posY - 0.01D, mc.thePlayer.posZ);
        }
        super.update(e);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;
        airTicks++;

        double base = baseMoveSpeed();
        boolean moving = MovementUtils.isMoving();

        if (mc.thePlayer.onGround && mc.thePlayer.isCollidedVertically) {
            if (moving) {
                double hopMod = 1.98D;
                if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) hopMod *= 0.93D;
                fluxSpeed(e, base * hopMod);
                e.setY(mc.thePlayer.motionY = jumpHeight(0.42F));
            }
            takingKnockback = false;
            airTicks = 0;
            wasOnGround = true;
        } else if (wasOnGround) {
            if (moving) {
                fluxSpeed(e, Math.max(base, lastDist - 0.66D * (lastDist - base)));
            }
            wasOnGround = false;
        } else {
            if (airTicks == 10) firstHop = false;

            boolean clearAbove = mc.theWorld.isAirBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 2.5D, mc.thePlayer.posZ));
            if (!takingKnockback && !firstHop && clearAbove && !mc.thePlayer.isPotionActive(Potion.jump) && airTicks >= 3 && airTicks <= 9) {
                if (airTicks == 3) e.setY(mc.thePlayer.motionY -= 0.0009D);
                if (airTicks == 4) e.setY(mc.thePlayer.motionY -= 0.03D);
                if (airTicks == 5) e.setY(mc.thePlayer.motionY -= 0.19D);

                BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ);
                IBlockState state = mc.theWorld.getBlockState(below);
                if (canStrafe(below) && state.getBlock().isFullBlock() && state.getBlock().isNormalCube() && moving) {
                    fluxSpeed(e, baseMoveSpeed());
                    e.setY(mc.thePlayer.motionY = mc.thePlayer.motionY * 0.8D);
                }
            }
        }
    }

    private static void fluxSpeed(MovePlayerEvent e, double speed) {
        MovementUtils.setSpeed(e, speed);
        mc.thePlayer.motionX = e.getX();
        mc.thePlayer.motionZ = e.getZ();
    }

    private boolean canStrafe(BlockPos below) {
        return !mc.theWorld.isAirBlock(below)
                && lastCollidedBlock.isFullBlock() && lastCollidedBlock.isNormalCube()
                && !(lastCollidedBlock instanceof BlockSlab) && !(lastCollidedBlock instanceof BlockStairs);
    }

    private static double jumpHeight(double height) {
        PotionEffect jump = mc.thePlayer.getActivePotionEffect(Potion.jump);
        if (jump != null) height += (jump.getAmplifier() + 1) * 0.1F;
        return height;
    }

    private static double baseMoveSpeed() {
        double walkSpeed = 0.221D;
        double baseSpeed = mc.thePlayer.isSprinting() ? 0.27D : walkSpeed;

        if (mc.thePlayer.isInWeb) baseSpeed *= 0.09D / walkSpeed;
        if (mc.thePlayer.isOnLadder()) baseSpeed *= 0.17D / walkSpeed;
        if (mc.theWorld.isAnyLiquid(mc.thePlayer.getEntityBoundingBox())) baseSpeed *= 0.115D / walkSpeed;

        PotionEffect effect = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed);
        if (effect != null) baseSpeed *= 1.0D + 0.2D * (effect.getAmplifier() + 1);
        effect = mc.thePlayer.getActivePotionEffect(Potion.moveSlowdown);
        if (effect != null) baseSpeed /= 1.0D + 0.2D * (effect.getAmplifier() + 1);
        return baseSpeed;
    }
}
