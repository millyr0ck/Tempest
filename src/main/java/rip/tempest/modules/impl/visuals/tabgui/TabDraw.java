package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.Glyphs;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

final class TabDraw {
    private TabDraw() {
    }

    static int accent() {
        return ColorProcess.getColor().getRGB() | 0xFF000000;
    }

    static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    static void rect(float x1, float y1, float x2, float y2, int color) {
        if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) return;
        Glyphs.bar(x1, y1, x2 - x1, y2 - y1, new Color(color, true));
    }

    static void box(float x, float y, float w, float h, int color) {
        rect(x, y, x + w, y + h, color);
    }

    static void bordered(float x, float y, float w, float h, float b, int border, int fill) {
        box(x, y, w, h, border);
        box(x + b, y + b, w - b * 2, h - b * 2, fill);
    }

    static void text(CustomFontRenderer font, String s, float x, float y, int color, boolean shadow) {
        GlStateManager.enableBlend();
        if (shadow) font.drawStringWithShadow(s, x, y, color);
        else font.drawString(s, x, y, color);
    }

    static void mcText(String s, float x, float y, int color) {
        GlStateManager.enableBlend();
        mc.fontRendererObj.drawStringWithShadow(s, x, y, color);
    }

    static String capitalise(String name) {
        if (name == null || name.isEmpty()) return "";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    static void scissor(float x, float y, float w, float h) {
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        int f = sr.getScaleFactor();
        java.nio.FloatBuffer m = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, m);
        float tx = m.get(12), ty = m.get(13);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) ((x + tx) * f), (int) (mc.displayHeight - (y + ty + h) * f), (int) Math.max(0, w * f), (int) Math.max(0, h * f));
    }

    static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
