package rip.tempest.utils.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import static rip.tempest.utils.Util.mc;

public final class TargetMarks {
    private TargetMarks() {
    }

    public static void renderTargetBox(EntityLivingBase entity, float red, float green, float blue, float alpha) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * mc.timer.renderPartialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * mc.timer.renderPartialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * mc.timer.renderPartialTicks;

        AxisAlignedBB box = entity.getEntityBoundingBox()
                .offset(-entity.posX, -entity.posY, -entity.posZ)
                .offset(x - mc.getRenderManager().viewerPosX,
                        y - mc.getRenderManager().viewerPosY,
                        z - mc.getRenderManager().viewerPosZ)
                .expand(0.05D, 0.05D, 0.05D);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        drawFilledBoundingBox(box, red, green, blue, 0.32F);

        GL11.glLineWidth(2.0F);

        RenderGlobal.drawOutlinedBoundingBox(
                box,
                (int) (red * 255.0F),
                (int) (green * 255.0F),
                (int) (blue * 255.0F),
                (int) (alpha * 255.0F)
        );

        GL11.glLineWidth(2.0F);

        RenderGlobal.drawOutlinedBoundingBox(
                box,
                (int) (red * 255.0F),
                (int) (green * 255.0F),
                (int) (blue * 255.0F),
                (int) (alpha * 255.0F)
        );

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.resetColor();
    }

    public static void renderTargetRing(EntityLivingBase entity, float red, float green, float blue) {
        final double rad = entity.width * 1.3D;
        final boolean shade = true;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
        GlStateManager.depthMask(false);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
        if (shade) GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableCull();
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

        final float partial = mc.timer.renderPartialTicks;
        final double viewerX = mc.getRenderManager().viewerPosX;
        final double viewerY = mc.getRenderManager().viewerPosY;
        final double viewerZ = mc.getRenderManager().viewerPosZ;

        final double x = (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial) - viewerX;
        final double y = (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial) - viewerY
                + Math.sin(System.currentTimeMillis() / 4E+2) + 1;
        final double z = (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial) - viewerZ;

        for (float i = 0; i < Math.PI * 2; i += (float) (Math.PI * 2 / 64)) {
            final double vecX = x + rad * Math.cos(i);
            final double vecZ = z + rad * Math.sin(i);

            if (shade) {
                GL11.glColor4f(red, green, blue, 0F);
                GL11.glVertex3d(vecX, y - Math.cos(System.currentTimeMillis() / 4E+2) / 2, vecZ);
                GL11.glColor4f(red, green, blue, 0.85F);
            }
            GL11.glVertex3d(vecX, y, vecZ);
        }

        GL11.glEnd();
        if (shade) GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableCull();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_POINT_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();

        GlStateManager.resetColor();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void drawFilledBoundingBox(AxisAlignedBB box, float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);

        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);

        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);

        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);

        GL11.glEnd();
    }
}
