package rip.tempest.interfaces.menu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.awt.Color;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

public final class MenuBackdrop {
    private static final int GLOW_RINGS = 12;
    private static final int GLOW_SEGMENTS = 48;

    private static final int DUST_COUNT = 28;
    private static final float[] DUST_X = new float[DUST_COUNT];
    private static final float[] DUST_Y_SEED = new float[DUST_COUNT];
    private static final float[] DUST_SPEED = new float[DUST_COUNT];
    private static final float[] DUST_SIZE = new float[DUST_COUNT];
    private static final float[] DUST_ALPHA = new float[DUST_COUNT];
    private static final float[] DUST_PHASE = new float[DUST_COUNT];

    private static final long START = System.currentTimeMillis();

    static {
        Random random = new Random();
        for (int i = 0; i < DUST_COUNT; i++) {
            DUST_X[i] = random.nextFloat();
            DUST_Y_SEED[i] = random.nextFloat();

            DUST_SPEED[i] = 0.006f + random.nextFloat() * 0.016f;
            DUST_SIZE[i] = 0.5f + random.nextFloat() * 0.75f;
            DUST_ALPHA[i] = 0.04f + random.nextFloat() * 0.1f;
            DUST_PHASE[i] = random.nextFloat() * (float) (Math.PI * 2);
        }
    }

    private MenuBackdrop() {
    }

    public static void draw(int width, int height) {
        if (MenuTheme.astolfo()) {
            AstolfoMenu.drawPanorama(width, height);
            return;
        }
        float seconds = (System.currentTimeMillis() - START) / 1000f;

        drawGradient(0, 0, width, height, 0xFF0B0B10, 0xFF07070A);

        float span = Math.max(width, height);
        drawGlow(width * (0.22f + 0.05f * (float) Math.sin(seconds * 0.11f)),
                height * (0.28f + 0.06f * (float) Math.cos(seconds * 0.09f)),
                span * 0.5f, MenuTheme.accent(), 0.085f);
        drawGlow(width * (0.8f + 0.05f * (float) Math.cos(seconds * 0.08f)),
                height * (0.72f + 0.06f * (float) Math.sin(seconds * 0.1f)),
                span * 0.45f, MenuTheme.accentSecond(), 0.065f);

        drawDust(width, height, seconds);

        drawGradient(0, height - height / 3f, width, height, 0x00000000, 0x66000000);
    }

    public static void drawGlow(float centerX, float centerY, float radius, Color color, float alpha) {
        if (alpha <= 0.002f || radius <= 0f) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        beginShapes();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int ring = 0; ring < GLOW_RINGS; ring++) {
            float inner = ring / (float) GLOW_RINGS;
            float outer = (ring + 1) / (float) GLOW_RINGS;
            float innerAlpha = alpha * glowFalloff(inner);
            float outerAlpha = alpha * glowFalloff(outer);

            for (int segment = 0; segment < GLOW_SEGMENTS; segment++) {
                double start = segment * Math.PI * 2 / GLOW_SEGMENTS;
                double end = (segment + 1) * Math.PI * 2 / GLOW_SEGMENTS;
                float cosStart = (float) Math.cos(start), sinStart = (float) Math.sin(start);
                float cosEnd = (float) Math.cos(end), sinEnd = (float) Math.sin(end);

                renderer.pos(centerX + cosStart * radius * inner, centerY + sinStart * radius * inner, 0).color(r, g, b, innerAlpha).endVertex();
                renderer.pos(centerX + cosEnd * radius * inner, centerY + sinEnd * radius * inner, 0).color(r, g, b, innerAlpha).endVertex();
                renderer.pos(centerX + cosEnd * radius * outer, centerY + sinEnd * radius * outer, 0).color(r, g, b, outerAlpha).endVertex();
                renderer.pos(centerX + cosStart * radius * outer, centerY + sinStart * radius * outer, 0).color(r, g, b, outerAlpha).endVertex();
            }
        }

        tessellator.draw();
        endShapes();
    }

    public static void drawGradient(float left, float top, float right, float bottom, int topColor, int bottomColor) {
        float topAlpha = (topColor >>> 24) / 255f, bottomAlpha = (bottomColor >>> 24) / 255f;

        beginShapes();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(right, top, 0).color((topColor >> 16 & 255) / 255f, (topColor >> 8 & 255) / 255f, (topColor & 255) / 255f, topAlpha).endVertex();
        renderer.pos(left, top, 0).color((topColor >> 16 & 255) / 255f, (topColor >> 8 & 255) / 255f, (topColor & 255) / 255f, topAlpha).endVertex();
        renderer.pos(left, bottom, 0).color((bottomColor >> 16 & 255) / 255f, (bottomColor >> 8 & 255) / 255f, (bottomColor & 255) / 255f, bottomAlpha).endVertex();
        renderer.pos(right, bottom, 0).color((bottomColor >> 16 & 255) / 255f, (bottomColor >> 8 & 255) / 255f, (bottomColor & 255) / 255f, bottomAlpha).endVertex();
        tessellator.draw();
        endShapes();
    }

    private static void drawDust(int width, int height, float seconds) {
        beginShapes();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < DUST_COUNT; i++) {
            float yFrac = 1f - (DUST_Y_SEED[i] + seconds * DUST_SPEED[i]) % 1f;
            float x = (DUST_X[i] + 0.012f * (float) Math.sin(seconds * 0.6f + DUST_PHASE[i])) * width;
            float y = yFrac * height;
            float size = DUST_SIZE[i];

            float alpha = DUST_ALPHA[i] * Math.min(1f, Math.min(yFrac, 1f - yFrac) * 6f);

            renderer.pos(x, y + size, 0).color(1f, 1f, 1f, alpha).endVertex();
            renderer.pos(x + size, y + size, 0).color(1f, 1f, 1f, alpha).endVertex();
            renderer.pos(x + size, y, 0).color(1f, 1f, 1f, alpha).endVertex();
            renderer.pos(x, y, 0).color(1f, 1f, 1f, alpha).endVertex();
        }

        tessellator.draw();
        endShapes();
    }

    private static float glowFalloff(float t) {
        return 1f - t * t * (3f - 2f * t);
    }

    private static void beginShapes() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();

        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager.shadeModel(GL_SMOOTH);
    }

    private static void endShapes() {
        GlStateManager.shadeModel(GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }
}
