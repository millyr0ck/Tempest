package rip.tempest.interfaces.click.bankroll;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

final class BankrollGLCanvas implements BankrollCanvas {
    private static final int NONE = -1, COLOR = 0, TEXT = 1;

    private DynamicTexture atlas;
    private BankrollFont atlasFont;

    private int batch = NONE;
    private int clipX0, clipY0, clipX1, clipY1;
    private int displayHeight;

    void begin(BankrollFont font, int displayHeight) {
        this.displayHeight = displayHeight;
        if (atlas == null || atlasFont != font) {
            if (atlas != null) atlas.deleteGlTexture();
            atlas = new DynamicTexture(font.atlas);
            atlasFont = font;
        }
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        batch = NONE;
        clipX0 = clipY0 = clipX1 = clipY1 = Integer.MIN_VALUE;
    }

    void end() {
        flush();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void flush() {
        if (batch != NONE) Tessellator.getInstance().draw();
        batch = NONE;
    }

    private WorldRenderer prepare(int kind, int cx0, int cy0, int cx1, int cy1) {
        boolean clipChanged = cx0 != clipX0 || cy0 != clipY0 || cx1 != clipX1 || cy1 != clipY1;
        if (batch == kind && !clipChanged) return Tessellator.getInstance().getWorldRenderer();
        flush();
        if (clipChanged) {
            clipX0 = cx0;
            clipY0 = cy0;
            clipX1 = cx1;
            clipY1 = cy1;

            GL11.glScissor(cx0, displayHeight - cy1, Math.max(0, cx1 - cx0), Math.max(0, cy1 - cy0));
        }
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        if (kind == TEXT) {
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(atlas.getGlTextureId());
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        } else {
            GlStateManager.disableTexture2D();
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        }
        batch = kind;
        return renderer;
    }

    @Override
    public void quad(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft,
                     int clipX0, int clipY0, int clipX1, int clipY1) {
        WorldRenderer r = prepare(COLOR, clipX0, clipY0, clipX1, clipY1);

        vertex(r, x0, y1, bottomLeft);
        vertex(r, x1, y1, bottomRight);
        vertex(r, x1, y0, topRight);
        vertex(r, x0, y0, topLeft);
    }

    private static void vertex(WorldRenderer r, float x, float y, int c) {
        r.pos(x, y, 0.0D).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    @Override
    public void glyph(BankrollFont font, int x, int y, int width, int height, int u, int v, int color,
                      int clipX0, int clipY0, int clipX1, int clipY1) {
        WorldRenderer r = prepare(TEXT, clipX0, clipY0, clipX1, clipY1);
        float w = font.atlas.getWidth(), h = font.atlas.getHeight();
        float u0 = u / w, v0 = v / h, u1 = (u + width) / w, v1 = (v + height) / h;
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255, alpha = color >>> 24;
        r.pos(x, y + height, 0.0D).tex(u0, v1).color(red, green, blue, alpha).endVertex();
        r.pos(x + width, y + height, 0.0D).tex(u1, v1).color(red, green, blue, alpha).endVertex();
        r.pos(x + width, y, 0.0D).tex(u1, v0).color(red, green, blue, alpha).endVertex();
        r.pos(x, y, 0.0D).tex(u0, v0).color(red, green, blue, alpha).endVertex();
    }
}
