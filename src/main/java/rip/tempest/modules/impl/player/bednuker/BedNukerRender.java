package rip.tempest.modules.impl.player.bednuker;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

final class BedNukerRender {
    private BedNukerRender() {
    }

    static void fill(AxisAlignedBB box, int argb) {
        begin(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        quad(buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ);
        quad(buffer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ);

        quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ);
        quad(buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ);

        quad(buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ);
        quad(buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ);
        tessellator.draw();
        end();
    }

    static void outline(AxisAlignedBB box, int argb, float width) {
        begin(argb);
        GL11.glLineWidth(width);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (double y : ys) for (double z : zs) line(buffer, box.minX, y, z, box.maxX, y, z);
        for (double x : xs) for (double z : zs) line(buffer, x, box.minY, z, x, box.maxY, z);
        for (double x : xs) for (double y : ys) line(buffer, x, y, box.minZ, x, y, box.maxZ);
        tessellator.draw();
        GL11.glLineWidth(1.0F);
        end();
    }

    private static void begin(int argb) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.color((argb >> 16 & 255) / 255F, (argb >> 8 & 255) / 255F, (argb & 255) / 255F, (argb >>> 24) / 255F);
    }

    private static void end() {
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private static void quad(WorldRenderer buffer, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
        buffer.pos(x3, y3, z3).endVertex();
        buffer.pos(x4, y4, z4).endVertex();
    }

    private static void line(WorldRenderer buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
    }
}
