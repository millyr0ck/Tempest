package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class KrsHypixelLowHopSpeed extends SpeedBase {
    private boolean canLowHop;
    private int fallTicks;

    @Override
    protected void onEnable() {
        super.onEnable();
        canLowHop = false;
        fallTicks = 0;
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        if (!inGame()) return;
        fallTicks = mc.thePlayer.onGround ? 0 : fallTicks + 1;

        if (mc.thePlayer.isInWater()) {
            canLowHop = false;
            return;
        }

        if (mc.thePlayer.onGround) {
            if (MovementUtils.isMoving()) mc.thePlayer.jump();
            HopUtil.strafeOrStop(speedAdjusted(0.472F, 0.036F, 0.12F));
            canLowHop = false;
        } else if (!mc.thePlayer.isPotionActive(Potion.jump)) {
            switch (fallTicks) {
                case 1 -> {
                    BlockPos above = new BlockPos(mc.thePlayer).up(3);
                    if (mc.theWorld.isAirBlock(above) && MovementUtils.isMoving() && !mc.thePlayer.isCollidedHorizontally) {
                        mc.thePlayer.motionY += 0.0568D;
                        canLowHop = true;
                    } else {
                        canLowHop = false;
                    }
                }
                case 3 -> {
                    if (canLowHop) mc.thePlayer.motionY -= 0.13D;
                }
                case 4 -> {
                    if (canLowHop) mc.thePlayer.motionY -= 0.2D;
                }
                case 7 -> {
                    if (canLowHop && lowStrafeCheck() && hasCollisionBelow()) {
                        HopUtil.strafeOrStop(speedAdjusted(0.285F, 0.036F, 0.044F));
                    }
                    canLowHop = false;
                }
            }
        }
    };

    private static float speedAdjusted(float base, float baseBonus, float amplifierBonus) {
        if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) return base;
        int amplifier = mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier();
        return base + (amplifier == 0 ? baseBonus : amplifierBonus);
    }

    private static boolean lowStrafeCheck() {
        Block below = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer).down()).getBlock();
        return below != Blocks.air && !(below instanceof BlockSlab) && !(below instanceof BlockStairs);
    }

    private static boolean hasCollisionBelow() {
        for (int i = 0; i <= 1; i++) {
            BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - i, mc.thePlayer.posZ);
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block == Blocks.air || block.getMaterial() == Material.water || block.getMaterial() == Material.lava) continue;
            if (HopUtil.solid(pos)) return true;
        }
        return false;
    }
}
