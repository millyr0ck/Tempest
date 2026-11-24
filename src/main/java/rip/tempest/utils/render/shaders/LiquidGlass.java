package rip.tempest.utils.render.shaders;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.*;
import static rip.tempest.utils.Util.mc;

public final class LiquidGlass {
    private static final ShaderUtils COPY_SHADER = new ShaderUtils("roundedBlurPass");
    private static final ShaderUtils GLASS_SHADER = new ShaderUtils("liquidGlass");

    private static final float FROST_RADIUS = 2.5f, FROST = 0.45f;

    private static Framebuffer sharp, ping, pong;
    private static boolean ready;

    private LiquidGlass() {
    }

    public static boolean prepare() {
        ready = false;
        if (!OpenGlHelper.isFramebufferEnabled()) return false;

        int width = mc.displayWidth, height = mc.displayHeight;
        int half = Math.max(1, (width + 1) / 2), halfH = Math.max(1, (height + 1) / 2);
        sharp = resize(sharp, width, height);
        ping = resize(ping, half, halfH);
        pong = resize(pong, half, halfH);
        float radius = Math.min(24, FROST_RADIUS * new ScaledResolution(mc).getScaleFactor());

        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int screen = mc.getFramebuffer().framebufferTexture;

        pass(screen, sharp, 1f / width, 1f / height, 0f, 1f, 0f);
        pass(screen, ping, 1f / width, 1f / height, radius, 1f, 0f);
        pass(ping.framebufferTexture, pong, 1f / half, 1f / halfH, radius / 2f, 0f, 1f);

        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.enableBlend();
        ready = true;
        return true;
    }

    public static void draw(float x, float y, float width, float height, float radius, float alpha) {
        if (!ready || width <= 0f || height <= 0f || alpha <= 0f) return;

        float scale = new ScaledResolution(mc).getScaleFactor();
        float pad = 1f;
        float quadWidth = width + pad * 2f, quadHeight = height + pad * 2f;
        radius = Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(GL_GREATER, 0f);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(pong.framebufferTexture);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(sharp.framebufferTexture);

        GLASS_SHADER.init();
        GLASS_SHADER.setUniformi("sharpTex", 0);
        GLASS_SHADER.setUniformi("blurTex", 1);
        GLASS_SHADER.setUniformf("screenSize", mc.displayWidth, mc.displayHeight);
        GLASS_SHADER.setUniformf("quadSize", quadWidth * scale, quadHeight * scale);
        GLASS_SHADER.setUniformf("boxHalfSize", width * 0.5f * scale, height * 0.5f * scale);
        GLASS_SHADER.setUniformf("radius", radius * scale);
        GLASS_SHADER.setUniformf("alpha", Math.min(1f, alpha));
        GLASS_SHADER.setUniformf("scale", scale);
        GLASS_SHADER.setUniformf("frost", FROST);

        GLASS_SHADER.setUniformf("tint", 0.06f, 0.07f, 0.09f, 0.22f);
        ShaderUtils.drawQuads(x - pad, y - pad, quadWidth, quadHeight);
        GLASS_SHADER.unload();

        GlStateManager.alphaFunc(GL_GREATER, 0.1f);
    }

    public static void drawWithShadow(float x, float y, float width, float height, float radius, float alpha) {
        if (!ready) return;
        int shadow = Math.round(90 * Math.min(1f, alpha));
        RoundedEffects.drawShadow(x, y, width, height, radius, 6f, 0f, 0f, 1.5f, new Color(0, 0, 0, shadow));
        draw(x, y, width, height, radius, alpha);
    }

    private static final ShaderUtils SHAPE_SHADER = new ShaderUtils("liquidGlassShape");

    private static final float SHAPE_SOFTNESS = 6f;
    private static Framebuffer shapeMask, shapePing, shapePong;
    private static boolean shaping;
    private static float shapeX0, shapeY0, shapeX1, shapeY1;

    public static boolean beginShape() {
        if (!ready) return false;
        int half = Math.max(1, (mc.displayWidth + 1) / 2), halfH = Math.max(1, (mc.displayHeight + 1) / 2);
        shapeMask = resize(shapeMask, half, halfH);
        shapePing = resize(shapePing, half, halfH);
        shapePong = resize(shapePong, half, halfH);
        shapeMask.bindFramebuffer(true);
        GlStateManager.clearColor(0f, 0f, 0f, 0f);
        GlStateManager.clear(GL_COLOR_BUFFER_BIT);
        shapeX0 = shapeY0 = Float.MAX_VALUE;
        shapeX1 = shapeY1 = -Float.MAX_VALUE;
        shaping = true;
        return true;
    }

    public static void shapeRect(float x, float y, float width, float height, float alpha) {
        if (!shaping || width <= 0 || height <= 0 || alpha <= 0) return;
        int a = Math.round(Math.min(1f, alpha) * 255);
        Gui.drawRect(x, y, x + width, y + height, a << 24 | 0xFFFFFF);
        shapeX0 = Math.min(shapeX0, x);
        shapeY0 = Math.min(shapeY0, y);
        shapeX1 = Math.max(shapeX1, x + width);
        shapeY1 = Math.max(shapeY1, y + height);
    }

    public static void endShape(float alpha) {
        if (!shaping) return;
        shaping = false;
        int half = shapeMask.framebufferWidth, halfH = shapeMask.framebufferHeight;
        float scale = new ScaledResolution(mc).getScaleFactor();

        float radius = Math.min(24, SHAPE_SOFTNESS * scale / 2f);

        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        pass(shapeMask.framebufferTexture, shapePing, 1f / half, 1f / halfH, radius, 1f, 0f);
        pass(shapePing.framebufferTexture, shapePong, 1f / half, 1f / halfH, radius, 0f, 1f);
        mc.getFramebuffer().bindFramebuffer(true);
        if (shapeX1 <= shapeX0 || shapeY1 <= shapeY0 || alpha <= 0f) {
            GlStateManager.enableBlend();
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(GL_GREATER, 0f);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit + 1);
        GlStateManager.bindTexture(shapePong.framebufferTexture);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(pong.framebufferTexture);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(sharp.framebufferTexture);

        SHAPE_SHADER.init();
        SHAPE_SHADER.setUniformi("sharpTex", 0);
        SHAPE_SHADER.setUniformi("blurTex", 1);
        SHAPE_SHADER.setUniformi("shapeTex", 2);
        SHAPE_SHADER.setUniformf("screenSize", mc.displayWidth, mc.displayHeight);
        SHAPE_SHADER.setUniformf("shapeTexel", 1f / half, 1f / halfH);
        SHAPE_SHADER.setUniformf("alpha", Math.min(1f, alpha));
        SHAPE_SHADER.setUniformf("scale", scale);
        SHAPE_SHADER.setUniformf("frost", FROST);
        SHAPE_SHADER.setUniformf("bevel", 8f * scale);
        SHAPE_SHADER.setUniformf("tint", 0.06f, 0.07f, 0.09f, 0.22f);

        float pad = SHAPE_SOFTNESS + 4f;
        ShaderUtils.drawQuads(shapeX0 - pad, shapeY0 - pad, shapeX1 - shapeX0 + pad * 2, shapeY1 - shapeY0 + pad * 2);
        SHAPE_SHADER.unload();

        GlStateManager.alphaFunc(GL_GREATER, 0.1f);
    }

    private static void pass(int source, Framebuffer target, float texelWidth, float texelHeight,
                             float radius, float directionX, float directionY) {
        target.bindFramebuffer(true);
        COPY_SHADER.init();
        COPY_SHADER.setUniformi("textureIn", 0);
        COPY_SHADER.setUniformf("texelSize", texelWidth, texelHeight);
        COPY_SHADER.setUniformf("direction", directionX, directionY);
        COPY_SHADER.setUniformf("radius", radius);
        GlStateManager.bindTexture(source);
        ShaderUtils.drawQuads();
        COPY_SHADER.unload();
    }

    private static Framebuffer resize(Framebuffer framebuffer, int width, int height) {
        if (framebuffer != null && framebuffer.framebufferWidth == width && framebuffer.framebufferHeight == height)
            return framebuffer;
        if (framebuffer != null) framebuffer.deleteFramebuffer();
        Framebuffer created = new Framebuffer(width, height, false);
        created.setFramebufferFilter(GL_LINEAR);
        return created;
    }
}
