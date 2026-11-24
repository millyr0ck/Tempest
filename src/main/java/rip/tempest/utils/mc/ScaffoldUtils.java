package rip.tempest.utils.mc;

import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.client.EnumFacingOffset;
import rip.tempest.utils.client.MathUtils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;
import net.minecraft.util.Vector3d;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static rip.tempest.utils.Util.mc;

/**
 * Scaffold's block search, rotation search and hit-vector code, from Yuri (AGPL-3.0): its
 * ScaffoldUtils plus the wall-avoiding versions of PlayerUtils' place and face searches. The
 * block counter drawing it also had is left out; Tempest's Scaffold keeps its own counters.
 */
public final class ScaffoldUtils {
    private ScaffoldUtils() {
    }

    private static Vector2f serverRotations() {
        return RotationProcess.rotations != null ? RotationProcess.rotations : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    public static void computeNormalRotations(BlockPos blockFace, EnumFacingOffset enumFacing, float[] target) {
        if (ScaffoldModule.rotations.getValue() == ScaffoldModule.Rotations.Old) {
            computeOldRotations(blockFace, enumFacing, target);
            return;
        }

        double difference = mc.thePlayer.posY + mc.thePlayer.getEyeHeight()
                - blockFace.getY() - 0.5 - (Math.random() - 0.5) * 0.1;

        for (int offset = -180; offset <= 180; offset += 45) {
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY - difference, mc.thePlayer.posZ);
            MovingObjectPosition mop = RayCastUtils.rayCast(new Vector2f(mc.thePlayer.rotationYaw + (offset * 3), 0), 4.5);
            mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + difference, mc.thePlayer.posZ);

            if (mop == null || mop.hitVec == null) return;

            Vector2f rotations = RotationUtils.calculate(mop.hitVec);
            if (RayCastUtils.overBlock(rotations, blockFace, enumFacing.getEnumFacing())) {
                target[0] = rotations.x;
                target[1] = rotations.y;
                return;
            }
        }

        final Vector2f rotations = RotationUtils.calculate(
                new Vector3d(blockFace.getX(), blockFace.getY(), blockFace.getZ()), enumFacing.getEnumFacing());

        if (!RayCastUtils.overBlock(new Vector2f(target[0], target[1]), blockFace, enumFacing.getEnumFacing())) {
            target[0] = rotations.x;
            target[1] = rotations.y;
        }
    }

    public static void computeWatchdogRotations(BlockPos blockFace, EnumFacingOffset enumFacing, float[] target) {
        float yawBase = RotationUtils.getMovementYaw() + 90.0f;
        float pitchBase = 90.0f;

        for (float yawOffset : offsets(90)) {
            for (float pitchOffset : offsets(20)) {
                float testYaw = MathHelper.wrapAngleTo180_float(yawBase + yawOffset);
                float testPitch = MathHelper.clamp_float(pitchBase + pitchOffset, -90.0F, 90.0F);
                if (RayCastUtils.overBlock(new Vector2f(testYaw, testPitch), enumFacing.getEnumFacing(), blockFace, true)) {
                    target[0] = testYaw;
                    target[1] = testPitch;
                    return;
                }
            }
        }

        computeNormalRotations(blockFace, enumFacing, target);
    }

    private static float[] offsets(int limit) {
        List<Float> values = new ArrayList<>();
        values.add(0f);
        for (int step = 1; step <= Math.min(limit, 20); step++) {
            values.add((float) -step);
            values.add((float) step);
        }
        for (int step = 22; step <= limit; step += 2) {
            values.add((float) -step);
            values.add((float) step);
        }
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }

    public static void computeOldRotations(BlockPos blockFace, EnumFacingOffset enumFacing, float[] target) {
        double x = blockFace.getX() + 0.5;
        double y = blockFace.getY() + 0.5;
        double z = blockFace.getZ() + 0.5;

        switch (enumFacing.getEnumFacing()) {
            case DOWN -> y = blockFace.getY();
            case UP -> y = blockFace.getY() + 1.0;
            case NORTH -> z = blockFace.getZ();
            case EAST -> x = blockFace.getX() + 1.0;
            case SOUTH -> z = blockFace.getZ() + 1.0;
            case WEST -> x = blockFace.getX();
        }

        final double xDif = x - mc.thePlayer.posX;
        final double zDif = z - mc.thePlayer.posZ;
        final double yDif = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        final double xzDist = StrictMath.sqrt(xDif * xDif + zDif * zDif);

        target[0] = (float) (StrictMath.atan2(zDif, xDif) * 180.0D / StrictMath.PI) - 90.0F;
        target[1] = (float) (-(StrictMath.atan2(yDif, xzDist) * 180.0D / StrictMath.PI));
    }

    public static Vec3 computeHitVec(BlockPos blockFace, EnumFacingOffset enumFacing) {
        EnumFacing facing = enumFacing.getEnumFacing();

        double minMargin = 0.1, maxMargin = 0.9;
        double randX = minMargin + Math.random() * (maxMargin - minMargin);
        double randY = minMargin + Math.random() * (maxMargin - minMargin);
        double randZ = minMargin + Math.random() * (maxMargin - minMargin);
        double x = facing.getAxis() == EnumFacing.Axis.X ? (facing == EnumFacing.EAST ? blockFace.getX() + 1.0 : blockFace.getX()) : blockFace.getX() + randX;
        double y = facing.getAxis() == EnumFacing.Axis.Y ? (facing == EnumFacing.UP ? blockFace.getY() + 1.0 : blockFace.getY()) : blockFace.getY() + randY;
        double z = facing.getAxis() == EnumFacing.Axis.Z ? (facing == EnumFacing.SOUTH ? blockFace.getZ() + 1.0 : blockFace.getZ()) : blockFace.getZ() + randZ;
        Vec3 hitVec = new Vec3(x, y, z);

        final MovingObjectPosition mop = RayCastUtils.rayCast(serverRotations(), mc.playerController.getBlockReachDistance());
        if (mop != null && mop.getBlockPos() != null && mop.hitVec != null && mop.getBlockPos().equals(blockFace) && mop.sideHit == facing) {
            double jitter = 0.03;
            double jX = (Math.random() - 0.5) * (jitter * 2);
            double jY = (Math.random() - 0.5) * (jitter * 2);
            double jZ = (Math.random() - 0.5) * (jitter * 2);
            double mopX = facing.getAxis() == EnumFacing.Axis.X ? mop.hitVec.xCoord : Math.max(blockFace.getX() + minMargin, Math.min(blockFace.getX() + maxMargin, mop.hitVec.xCoord + jX));
            double mopY = facing.getAxis() == EnumFacing.Axis.Y ? mop.hitVec.yCoord : Math.max(blockFace.getY() + minMargin, Math.min(blockFace.getY() + maxMargin, mop.hitVec.yCoord + jY));
            double mopZ = facing.getAxis() == EnumFacing.Axis.Z ? mop.hitVec.zCoord : Math.max(blockFace.getZ() + minMargin, Math.min(blockFace.getZ() + maxMargin, mop.hitVec.zCoord + jZ));
            hitVec = new Vec3(mopX, mopY, mopZ);
        }
        return hitVec;
    }

    public static boolean doesNotContainBlock(Vec3i offset, int down) {
        return PlayerUtils.blockRelativeToPlayer(offset.getX(), -down + offset.getY(), offset.getZ())
                .isReplaceable(mc.theWorld, new BlockPos(mc.thePlayer).down(down));
    }

    public static int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock && itemStack.stackSize > 0
                    && isBlockValid(((ItemBlock) itemStack.getItem()).getBlock())) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isBlockValid(final Block block) {
        return (block.isFullBlock() || block == Blocks.glass || block == Blocks.stained_glass)
                && block != Blocks.sand
                && block != Blocks.gravel
                && block != Blocks.dispenser
                && block != Blocks.command_block
                && block != Blocks.noteblock
                && block != Blocks.furnace
                && block != Blocks.crafting_table
                && block != Blocks.dropper
                && block != Blocks.pumpkin
                && block != Blocks.lit_pumpkin
                && block != Blocks.beacon;
    }

    public static Vec3 getPlacePossibility(double offsetX, double offsetY, double offsetZ, Integer plane, boolean downwards, boolean avoidWalls) {
        final List<Vec3> possibilities = new ArrayList<>();
        final int range = (int) (6.0 + (Math.abs(offsetX) + Math.abs(offsetZ)));

        for (int x = -range; x <= range; ++x) {
            for (int y = -range; y <= range; ++y) {
                for (int z = -range; z <= range; ++z) {
                    final Block block = PlayerUtils.blockRelativeToPlayer(x, y, z);

                    if (!block.isReplaceable(mc.theWorld, new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z))) {
                        for (int x2 = -1; x2 <= 1; x2 += 2)
                            possibilities.add(new Vec3(mc.thePlayer.posX + x + x2, mc.thePlayer.posY + y, mc.thePlayer.posZ + z));
                        for (int y2 = -1; y2 <= 1; y2 += 2)
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y + y2, mc.thePlayer.posZ + z));
                        for (int z2 = -1; z2 <= 1; z2 += 2)
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z + z2));
                    }
                }
            }
        }

        possibilities.removeIf(vec3 -> mc.thePlayer.getDistance(vec3.xCoord, vec3.yCoord, vec3.zCoord) > 5
                || !PlayerUtils.block(vec3.xCoord, vec3.yCoord, vec3.zCoord).isReplaceable(mc.theWorld, new BlockPos(vec3.xCoord, vec3.yCoord, vec3.zCoord)));
        if (possibilities.isEmpty()) return null;

        if (plane != null) possibilities.removeIf(vec3 -> Math.floor(vec3.yCoord + 1) != plane);

        if (avoidWalls) {
            final int maxY = (int) Math.floor(mc.thePlayer.posY - 1 + offsetY + 1.0E-3);
            possibilities.removeIf(vec3 -> (int) Math.floor(vec3.yCoord) > maxY);
        }

        possibilities.sort(Comparator.comparingDouble(vec3 -> {
            final double d0 = (mc.thePlayer.posX + offsetX) - vec3.xCoord;
            final double d1 = (mc.thePlayer.posY - 1 + offsetY) - vec3.yCoord;
            final double d2 = (mc.thePlayer.posZ + offsetZ) - vec3.zCoord;
            return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
        }));

        if (!avoidWalls) return possibilities.isEmpty() ? null : possibilities.get(0);

        for (Vec3 candidate : possibilities) {
            if (getEnumFacing(candidate, downwards, true) != null) return candidate;
        }
        return null;
    }

    public static EnumFacingOffset getEnumFacing(final Vec3 position, boolean downwards, boolean avoidWalls) {
        List<EnumFacingOffset> possibleFacings = new ArrayList<>();

        int posX = (int) Math.floor(position.xCoord);
        int posY = (int) Math.floor(position.yCoord);
        int posZ = (int) Math.floor(position.zCoord);

        addFacing(possibleFacings, posX, posY, posZ - 1, EnumFacing.SOUTH, 0, 0, -1, avoidWalls);
        addFacing(possibleFacings, posX, posY, posZ + 1, EnumFacing.NORTH, 0, 0, 1, avoidWalls);
        addFacing(possibleFacings, posX - 1, posY, posZ, EnumFacing.EAST, -1, 0, 0, avoidWalls);
        addFacing(possibleFacings, posX + 1, posY, posZ, EnumFacing.WEST, 1, 0, 0, avoidWalls);

        if (!possibleFacings.isEmpty()) {
            double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 30.0;
            double currentYaw = (serverRotations().x % 360 + 90) + jitter;

            possibleFacings.sort(Comparator.comparingDouble(enumFacing -> {
                double facingAngle = Math.toDegrees(Math.atan2(enumFacing.getOffset().zCoord, enumFacing.getOffset().xCoord)) % 360;
                return Math.abs(MathUtils.wrappedDifference(facingAngle, currentYaw));
            }));

            if (possibleFacings.size() > 1 && ThreadLocalRandom.current().nextDouble() < 0.25) {
                return possibleFacings.get(1);
            }
            return possibleFacings.get(0);
        }

        if (solid(posX, posY - 1, posZ)) {
            return new EnumFacingOffset(EnumFacing.UP, new Vec3(0, -1, 0));
        } else if (downwards && solid(posX, posY + 1, posZ)) {
            return new EnumFacingOffset(EnumFacing.DOWN, new Vec3(0, 1, 0));
        }
        return null;
    }

    private static void addFacing(List<EnumFacingOffset> list, int x, int y, int z, EnumFacing facing, int offX, int offY, int offZ, boolean avoidWalls) {
        if (!solid(x, y, z)) return;
        if (avoidWalls && solid(x, y + 1, z)) return;
        list.add(new EnumFacingOffset(facing, new Vec3(offX, offY, offZ)));
    }

    private static boolean solid(int x, int y, int z) {
        return !PlayerUtils.block(x, y, z).isReplaceable(mc.theWorld, new BlockPos(x, y, z));
    }
}
