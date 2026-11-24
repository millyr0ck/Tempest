package rip.tempest.modules.impl.movement.speed;

import rip.tempest.utils.mc.MovementUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

final class HopUtil {
    private HopUtil() {
    }

    static void strafe(double speed) {
        if (!MovementUtils.isMoving()) return;
        double direction = MovementUtils.direction();
        mc.thePlayer.motionX = -Math.sin(direction) * speed;
        mc.thePlayer.motionZ = Math.cos(direction) * speed;
    }

    static void strafe() {
        strafe(MovementUtils.getSpeed());
    }

    static void strafeOrStop(double speed) {
        if (MovementUtils.isMoving()) {
            strafe(speed);
        } else {
            MovementUtils.stop();
        }
    }

    static void strafeAt(double speed, float yaw) {
        if (!MovementUtils.isMoving()) return;
        double radians = Math.toRadians(yaw);
        mc.thePlayer.motionX = -Math.sin(radians) * speed;
        mc.thePlayer.motionZ = Math.cos(radians) * speed;
    }

    static int speedLevel() {
        return mc.thePlayer.isPotionActive(Potion.moveSpeed) ? mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1 : 0;
    }

    static boolean collides(double dy) {
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, dy, 0.0D)).isEmpty();
    }

    static Block block(double x, double y, double z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    static boolean solid(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(mc.theWorld, pos, state) != null;
    }

    static boolean fullBlock(double x, double y, double z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = mc.theWorld.getBlockState(pos);
        return state.getBlock().isFullCube() && state.getBlock().getCollisionBoundingBox(mc.theWorld, pos, state) != null;
    }
}
