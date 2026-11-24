package rip.tempest.utils.render;

import rip.tempest.Tempest;
import rip.tempest.modules.ModuleManager;
import rip.tempest.modules.impl.visuals.ChamsModule;
import rip.tempest.modules.impl.visuals.ESPModule;
import rip.tempest.modules.impl.visuals.GlowESPModule;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public final class EntityCulling {
    private static final double ALWAYS_DRAWN = 4.0D;
    private static final double NEAR = 32.0D;

    private static final double INSET = 0.05D;

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    private static double eyeX, eyeY, eyeZ;

    private EntityCulling() {
    }

    public static boolean begin(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        Entity view = mc.getRenderViewEntity();
        if (world == null || view == null || mc.gameSettings.thirdPersonView != 0) return false;

        ModuleManager modules = Tempest.INSTANCE.getModuleManager();
        if (modules.getModule(ESPModule.class).isEnabled()
                || modules.getModule(ChamsModule.class).isEnabled()
                || modules.getModule(GlowESPModule.class).isEnabled()) {
            return false;
        }

        eyeX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        eyeY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks + view.getEyeHeight();
        eyeZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        return !opaque(world, MathHelper.floor_double(eyeX), MathHelper.floor_double(eyeY), MathHelper.floor_double(eyeZ));
    }

    public static boolean hidden(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (entity == view || entity == mc.thePlayer || entity.riddenByEntity == view || entity.ridingEntity == view) return false;

        double dx = entity.posX - eyeX, dy = entity.posY - eyeY, dz = entity.posZ - eyeZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < ALWAYS_DRAWN * ALWAYS_DRAWN) return false;

        long tick = mc.theWorld.getTotalWorldTime();
        boolean due = distanceSq < NEAR * NEAR
                ? entity.cullCheckTick != tick
                : tick - entity.cullCheckTick >= 4L || tick < entity.cullCheckTick;
        if (due) {
            entity.cullCheckTick = tick;
            entity.cullHidden = !visible(mc.theWorld, entity.getEntityBoundingBox());
        }
        return entity.cullHidden;
    }

    private static boolean visible(World world, AxisAlignedBB box) {
        if (box == null) return true;
        double minX = box.minX + INSET, minY = box.minY + INSET, minZ = box.minZ + INSET;
        double maxX = box.maxX - INSET, maxY = box.maxY - INSET, maxZ = box.maxZ - INSET;
        if (minX > maxX || minY > maxY || minZ > maxZ) return true;

        if (clear(world, (minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D)) return true;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minX : maxX;
            double y = (corner & 2) == 0 ? minY : maxY;
            double z = (corner & 4) == 0 ? minZ : maxZ;
            if (clear(world, x, y, z)) return true;
        }
        return false;
    }

    private static boolean clear(World world, double tx, double ty, double tz) {
        int x = MathHelper.floor_double(eyeX), y = MathHelper.floor_double(eyeY), z = MathHelper.floor_double(eyeZ);
        int endX = MathHelper.floor_double(tx), endY = MathHelper.floor_double(ty), endZ = MathHelper.floor_double(tz);

        double dx = tx - eyeX, dy = ty - eyeY, dz = tz - eyeZ;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;

        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0D / dz);

        double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - eyeX : eyeX - x) * deltaX;
        double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - eyeY : eyeY - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - eyeZ : eyeZ - z) * deltaZ;

        int steps = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        for (int i = 0; i < steps; i++) {
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            if (x == endX && y == endY && z == endZ) return true;
            if (opaque(world, x, y, z)) return false;
        }
        return true;
    }

    private static boolean opaque(World world, int x, int y, int z) {
        if (y < 0 || y > 255) return false;
        Block block = world.getBlockState(POS.set(x, y, z)).getBlock();
        return block.isOpaqueCube() && block.isFullCube();
    }
}
