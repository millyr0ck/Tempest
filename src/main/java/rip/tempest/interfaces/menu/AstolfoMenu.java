package rip.tempest.interfaces.menu;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.FontProcess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import java.awt.Color;
import java.awt.Font;

public final class AstolfoMenu {
    private static final ResourceLocation[] PANORAMA = {
            new ResourceLocation("textures/gui/title/background/panorama_0.png"),
            new ResourceLocation("textures/gui/title/background/panorama_1.png"),
            new ResourceLocation("textures/gui/title/background/panorama_2.png"),
            new ResourceLocation("textures/gui/title/background/panorama_3.png"),
            new ResourceLocation("textures/gui/title/background/panorama_4.png"),
            new ResourceLocation("textures/gui/title/background/panorama_5.png")};

    public static final ResourceLocation CLOSE = icon("close"), OPTIONS = icon("options"), LANGUAGE = icon("language"), FOLDER = icon("folder");

    private static final long START = System.currentTimeMillis();
    private static ResourceLocation blurTexture;

    private AstolfoMenu() {
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("tempest/astolfo/" + name + ".png");
    }

    private static CustomFontRenderer regular28, light20, regular18, regular15;

    public static CustomFontRenderer regular28() {
        if (regular28 == null) regular28 = FontProcess.getStyledFont("roboto", 28, Font.PLAIN);
        return regular28;
    }

    public static CustomFontRenderer light20() {
        if (light20 == null) light20 = FontProcess.getStyledFont("roboto-light", 20, Font.PLAIN);
        return light20;
    }

    public static CustomFontRenderer regular18() {
        if (regular18 == null) regular18 = FontProcess.getStyledFont("roboto", 18, Font.PLAIN);
        return regular18;
    }

    public static CustomFontRenderer regular15() {
        if (regular15 == null) regular15 = FontProcess.getStyledFont("roboto", 15, Font.PLAIN);
        return regular15;
    }

    public static void drawPanorama(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (blurTexture == null) blurTexture = mc.getTextureManager().getDynamicTextureLocation("tempest_astolfo_background", new DynamicTexture(256, 256));
        float time = (System.currentTimeMillis() - START) / 50f;

        GlStateManager.disableAlpha();
        mc.getFramebuffer().unbindFramebuffer();
        GlStateManager.viewport(0, 0, 256, 256);
        drawCube(time);
        for (int i = 0; i < 7; i++) blur(width, height);
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);

        float scale = width > height ? 120.0F / width : 120.0F / height;
        float v = height * scale / 256.0F;
        float u = width * scale / 256.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        wr.pos(0.0, height, 0).tex(0.5F - v, 0.5F + u).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        wr.pos(width, height, 0).tex(0.5F - v, 0.5F - u).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        wr.pos(width, 0.0, 0).tex(0.5F + v, 0.5F - u).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        wr.pos(0.0, 0.0, 0).tex(0.5F + v, 0.5F + u).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        tessellator.draw();
        GlStateManager.enableAlpha();
    }

    private static void drawCube(float time) {
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int steps = 8;
        for (int k = 0; k < steps * steps; k++) {
            GlStateManager.pushMatrix();
            float dx = ((float) (k % steps) / steps - 0.5F) / 64.0F;
            float dy = ((float) (k / steps) / steps - 0.5F) / 64.0F;
            GlStateManager.translate(dx, dy, 0.0F);
            GlStateManager.rotate(MathHelper.sin(time / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-time * 0.1F, 0.0F, 1.0F, 0.0F);
            for (int side = 0; side < 6; side++) {
                GlStateManager.pushMatrix();
                if (side == 1) GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                if (side == 2) GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                if (side == 3) GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                if (side == 4) GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                if (side == 5) GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                mc.getTextureManager().bindTexture(PANORAMA[side]);
                wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int alpha = 255 / (k + 1);
                wr.pos(-1.0, -1.0, 1.0).tex(0.0, 0.0).color(255, 255, 255, alpha).endVertex();
                wr.pos(1.0, -1.0, 1.0).tex(1.0, 0.0).color(255, 255, 255, alpha).endVertex();
                wr.pos(1.0, 1.0, 1.0).tex(1.0, 1.0).color(255, 255, 255, alpha).endVertex();
                wr.pos(-1.0, 1.0, 1.0).tex(0.0, 1.0).color(255, 255, 255, alpha).endVertex();
                tessellator.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }
        wr.setTranslation(0.0, 0.0, 0.0);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private static void blur(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(blurTexture);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, 256, 256);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        GlStateManager.disableAlpha();
        int passes = 3;
        for (int i = 0; i < passes; i++) {
            float a = 1.0F / (i + 1);
            float shift = (i - passes / 2) / 256.0F;
            wr.pos(width, height, 0).tex(0.0F + shift, 1.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            wr.pos(width, 0.0, 0).tex(1.0F + shift, 1.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            wr.pos(0.0, 0.0, 0).tex(1.0F + shift, 0.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            wr.pos(0.0, height, 0).tex(0.0F + shift, 0.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableAlpha();
        GlStateManager.colorMask(true, true, true, true);
    }

    public static void menuButton(String label, float x, float y, float w, float h, boolean hover) {
        Gui.drawRect((int) x, (int) y, (int) (x + w), (int) (y + h), new Color(0, 0, 0, 100).getRGB());
        GlStateManager.enableBlend();
        centred(light20(), label, x + w / 2f, y + h / 2f, hover ? -1 : 0xA9FFFFFF);
    }

    public static void centred(CustomFontRenderer font, String text, float x, float y, int color) {
        font.drawString(text, (int) (x - font.getStringWidth(text) / 2f), (int) (y - font.getHeight() / 2f), color);
    }

    public static void icon(ResourceLocation icon, float x, float y, float size) {
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int) size, (int) size, size, size);
    }
}
