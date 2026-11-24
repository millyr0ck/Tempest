package rip.tempest.utils.render.shaders;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;
import static org.lwjgl.opengl.GL11.*;

public final class RoundedEffects {
    private static final int MAX_BLUR_RADIUS = 24;

    private static final ShaderUtils BLUR_PASS_SHADER = new ShaderUtils("roundedBlurPass");
    private static final ShaderUtils BLUR_SHADER = new ShaderUtils("roundedBlur");
    private static final ShaderUtils SHADOW_SHADER = new ShaderUtils("roundedShadow");

    private static Framebuffer blurPing;
    private static Framebuffer blurPong;
    private static boolean blurReady;

    private RoundedEffects() {
    }

    public static boolean prepareBlur(float radius) {
        blurReady = false;
        if (!OpenGlHelper.isFramebufferEnabled())
            return false;

        int width = mc.displayWidth;
        int height = mc.displayHeight;
        int downscale = downscale(width, height);
        int blurWidth = Math.max(1, (width + downscale - 1) / downscale);
        int blurHeight = Math.max(1, (height + downscale - 1) / downscale);
        blurPing = resize(blurPing, blurWidth, blurHeight);
        blurPong = resize(blurPong, blurWidth, blurHeight);

        float pixelRadius = Math.min(MAX_BLUR_RADIUS, radius * new ScaledResolution(mc).getScaleFactor());
        if (pixelRadius <= 0.01f)
            return false;

        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        blurPass(mc.getFramebuffer().framebufferTexture, blurPing, 1f / width, 1f / height, pixelRadius, 1f, 0f);
        blurPass(blurPing.framebufferTexture, blurPong, 1f / blurWidth, 1f / blurHeight, pixelRadius / downscale, 0f, 1f);

        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.enableBlend();
        blurReady = true;
        return true;
    }

    public static void drawBlur(float x, float y, float width, float height, float radius, float alpha) {
        if (!blurReady || width <= 0f || height <= 0f || alpha <= 0f)
            return;

        float scale = new ScaledResolution(mc).getScaleFactor();

        float pad = 1f;
        float quadWidth = width + pad * 2f;
        float quadHeight = height + pad * 2f;

        beginEffect();
        BLUR_SHADER.init();
        BLUR_SHADER.setUniformi("textureIn", 0);
        BLUR_SHADER.setUniformf("screenSize", mc.displayWidth, mc.displayHeight);
        BLUR_SHADER.setUniformf("blurTexelSize", 1f / blurPong.framebufferWidth, 1f / blurPong.framebufferHeight);
        BLUR_SHADER.setUniformf("quadSize", quadWidth * scale, quadHeight * scale);
        BLUR_SHADER.setUniformf("boxHalfSize", width * 0.5f * scale, height * 0.5f * scale);
        BLUR_SHADER.setUniformf("radius", clampRadius(radius, width, height) * scale);
        BLUR_SHADER.setUniformf("alpha", Math.min(1f, alpha));
        GlStateManager.bindTexture(blurPong.framebufferTexture);
        ShaderUtils.drawQuads(x - pad, y - pad, quadWidth, quadHeight);
        BLUR_SHADER.unload();
        endEffect();
    }

    public static void drawShadow(float x, float y, float width, float height, float radius,
                                  float softness, float spread, float offsetX, float offsetY, Color color) {
        if (width <= 0f || height <= 0f || color == null || color.getAlpha() <= 0)
            return;

        float scale = new ScaledResolution(mc).getScaleFactor();
        softness = Math.max(0f, softness);
        spread = Math.max(0f, spread);
        float pad = softness + spread + 2f;
        float quadWidth = width + pad * 2f;
        float quadHeight = height + pad * 2f;

        beginEffect();
        SHADOW_SHADER.init();
        SHADOW_SHADER.setUniformf("quadSize", quadWidth * scale, quadHeight * scale);
        SHADOW_SHADER.setUniformf("boxHalfSize", (width * 0.5f + spread) * scale, (height * 0.5f + spread) * scale);
        SHADOW_SHADER.setUniformf("radius", (clampRadius(radius, width, height) + spread) * scale);
        SHADOW_SHADER.setUniformf("softness", Math.max(softness * scale, 0.001f));
        SHADOW_SHADER.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
        ShaderUtils.drawQuads(x + offsetX - pad, y + offsetY - pad, quadWidth, quadHeight);
        SHADOW_SHADER.unload();
        endEffect();
    }

    private static void blurPass(int source, Framebuffer target, float texelWidth, float texelHeight,
                                 float radius, float directionX, float directionY) {
        target.bindFramebuffer(true);
        BLUR_PASS_SHADER.init();
        BLUR_PASS_SHADER.setUniformi("textureIn", 0);
        BLUR_PASS_SHADER.setUniformf("texelSize", texelWidth, texelHeight);
        BLUR_PASS_SHADER.setUniformf("direction", directionX, directionY);
        BLUR_PASS_SHADER.setUniformf("radius", radius);
        GlStateManager.bindTexture(source);
        ShaderUtils.drawQuads();
        BLUR_PASS_SHADER.unload();
    }

    private static void beginEffect() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        GlStateManager.alphaFunc(GL_GREATER, 0f);
    }

    private static void endEffect() {
        GlStateManager.alphaFunc(GL_GREATER, 0.1f);
    }

    private static float clampRadius(float radius, float width, float height) {
        return Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));
    }

    private static int downscale(int width, int height) {
        long pixels = (long) width * height;
        if (pixels >= 7_000_000L)
            return 4;
        if (pixels >= 2_000_000L)
            return 3;
        return 2;
    }

    private static Framebuffer resize(Framebuffer framebuffer, int width, int height) {
        if (framebuffer != null && framebuffer.framebufferWidth == width && framebuffer.framebufferHeight == height)
            return framebuffer;

        if (framebuffer != null)
            framebuffer.deleteFramebuffer();

        Framebuffer created = new Framebuffer(width, height, false);
        created.setFramebufferFilter(GL_LINEAR);
        return created;
    }
}
