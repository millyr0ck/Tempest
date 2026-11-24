package rip.tempest.jenny.render;

import rip.tempest.jenny.entity.NpcEntity;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public final class NpcShadow {
    private static final ResourceLocation SHADOW = new ResourceLocation("textures/misc/shadow.png");
    private static final float SIZE = 0.5f;

    private NpcShadow() {
    }

    public static void render(NpcEntity npc, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || !mc.gameSettings.entityShadows) return;
        double distSq = x * x + y * y + z * z;
        float alpha = (float) (1.0 - distSq / 256.0);
        if (alpha <= 0f) return;

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(SHADOW);
        GlStateManager.depthMask(false);

        double px = npc.posX, py = npc.posY, pz = npc.posZ;
        int x0 = MathHelper.floor_double(px - SIZE);
        int x1 = MathHelper.floor_double(px + SIZE);
        int y0 = MathHelper.floor_double(py - SIZE);
        int y1 = MathHelper.floor_double(py);
        int z0 = MathHelper.floor_double(pz - SIZE);
        int z1 = MathHelper.floor_double(pz + SIZE);
        double ox = x - px, oy = y - py, oz = z - pz;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (BlockPos pos : BlockPos.getAllInBoxMutable(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1))) {
            Block block = world.getBlockState(pos.down()).getBlock();
            if (block.getRenderType() != -1 && world.getLightFromNeighbors(pos) > 3) {
                shadowBlock(world, wr, block, x, y, z, pos, alpha, ox, oy, oz);
            }
        }
        tess.draw();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.depthMask(true);
    }

    private static void shadowBlock(World world, WorldRenderer wr, Block block, double x, double y, double z, BlockPos pos,
                                    float alpha, double ox, double oy, double oz) {
        if (!block.isFullCube()) return;
        double a = (alpha - (y - (pos.getY() + oy)) / 2.0) * 0.5 * world.getLightBrightness(pos);
        if (a < 0.0) return;
        if (a > 1.0) a = 1.0;
        double minX = pos.getX() + block.getBlockBoundsMinX() + ox;
        double maxX = pos.getX() + block.getBlockBoundsMaxX() + ox;
        double minY = pos.getY() + block.getBlockBoundsMinY() + oy + 0.015625;
        double minZ = pos.getZ() + block.getBlockBoundsMinZ() + oz;
        double maxZ = pos.getZ() + block.getBlockBoundsMaxZ() + oz;
        float u0 = (float) ((x - minX) / 2.0 / SIZE + 0.5);
        float u1 = (float) ((x - maxX) / 2.0 / SIZE + 0.5);
        float v0 = (float) ((z - minZ) / 2.0 / SIZE + 0.5);
        float v1 = (float) ((z - maxZ) / 2.0 / SIZE + 0.5);
        wr.pos(minX, minY, minZ).tex(u0, v0).color(1f, 1f, 1f, (float) a).endVertex();
        wr.pos(minX, minY, maxZ).tex(u0, v1).color(1f, 1f, 1f, (float) a).endVertex();
        wr.pos(maxX, minY, maxZ).tex(u1, v1).color(1f, 1f, 1f, (float) a).endVertex();
        wr.pos(maxX, minY, minZ).tex(u1, v0).color(1f, 1f, 1f, (float) a).endVertex();
    }
}
