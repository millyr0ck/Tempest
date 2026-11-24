package rip.tempest.interfaces.click;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class ColorPicker {
    public static final int WIDTH = 76;
    public static final int SV_SIZE = 50;
    public static final int BAR_HEIGHT = 7;
    public static final int SPACING = 4;
    public static final int PREVIEW_HEIGHT = 9;
    private static final int BUTTON_WIDTH = 10;
    private static final long FLASH_MS = 400L;
    public static final int TOTAL_HEIGHT = SV_SIZE + SPACING + BAR_HEIGHT + SPACING + PREVIEW_HEIGHT + 4;

    private float hue = 0.8f;
    private float saturation = 1f;
    private float brightness = 1f;
    private boolean draggingSV;
    private boolean draggingHue;

    private char flashButton;
    private boolean flashGood;
    private long flashAt;

    public void setColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    public int getColor() {
        return Color.HSBtoRGB(hue, saturation, brightness) | 0xFF000000;
    }

    public void render(int x, int y, int mouseX, int mouseY) {
        Gui.drawRect(x, y, x + WIDTH, y + TOTAL_HEIGHT, 0xFF141414);

        int svY = y + 2;
        renderSVSquare(x, svY, WIDTH, SV_SIZE, hue);

        int barY = svY + SV_SIZE + SPACING;
        renderSpectrumBar(x, barY, WIDTH, BAR_HEIGHT, hue);

        int previewY = barY + BAR_HEIGHT + SPACING;
        int previewEnd = x + WIDTH - (BUTTON_WIDTH + 1) * 2;
        Gui.drawRect(x, previewY, previewEnd, previewY + PREVIEW_HEIGHT, getColor());
        Gui.drawRect(x, previewY, previewEnd, previewY + 1, 0x55FFFFFF);

        renderButton('C', x + WIDTH - BUTTON_WIDTH * 2 - 1, previewY, mouseX, mouseY);
        renderButton('P', x + WIDTH - BUTTON_WIDTH, previewY, mouseX, mouseY);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void renderButton(char letter, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + PREVIEW_HEIGHT;
        Gui.drawRect(x, y, x + BUTTON_WIDTH, y + PREVIEW_HEIGHT, hovered ? 0xFF3A3A3A : 0xFF262626);

        int color = hovered ? 0xFFFFFFFF : 0xFFAAAAAA;
        if (flashButton == letter && System.currentTimeMillis() - flashAt < FLASH_MS) {
            color = flashGood ? 0xFF55FF55 : 0xFFFF5555;
        }
        String text = String.valueOf(letter);
        Minecraft.getMinecraft().fontRendererObj.drawString(text,
                x + (BUTTON_WIDTH - Minecraft.getMinecraft().fontRendererObj.getStringWidth(text)) / 2 + 1, y + 1, color);
    }

    private void flash(char button, boolean good) {
        flashButton = button;
        flashGood = good;
        flashAt = System.currentTimeMillis();
    }

    private void renderSVSquare(int x, int y, int width, int height, float hue) {
        Color hueColor = Color.getHSBColor(hue, 1f, 1f);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glVertex2f(x, y);
        GL11.glColor4f(hueColor.getRed() / 255f, hueColor.getGreen() / 255f, hueColor.getBlue() / 255f, 1f);
        GL11.glVertex2f(x + width, y);
        GL11.glColor4f(0f, 0f, 0f, 1f);
        GL11.glVertex2f(x + width, y + height);
        GL11.glColor4f(0f, 0f, 0f, 1f);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        int selX = x + (int) (saturation * width);
        int selY = y + (int) ((1f - brightness) * height);
        drawSelector(selX, selY);
    }

    private void renderSpectrumBar(int x, int y, int width, int height, float value) {
        Gui.drawRect(x, y, x + width, y + height, 0xFF1a1a1a);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < width; i++) {
            float ratio = i / (float) width;
            Color color = Color.getHSBColor(ratio, 1f, 1f);
            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f);
            GL11.glVertex2f(x + i, y);
            GL11.glVertex2f(x + i + 1, y);
            GL11.glVertex2f(x + i + 1, y + height);
            GL11.glVertex2f(x + i, y + height);
        }
        GL11.glEnd();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        int selectorX = x + (int) (value * width);
        Gui.drawRect(selectorX - 1, y, selectorX + 1, y + height, 0xFFFFFFFF);
    }

    private void drawSelector(int cx, int cy) {
        int r = 4;
        Gui.drawRect(cx - r - 1, cy - r - 1, cx + r + 1, cy - r, 0xFF000000);
        Gui.drawRect(cx - r - 1, cy + r, cx + r + 1, cy + r + 1, 0xFF000000);
        Gui.drawRect(cx - r - 1, cy - r - 1, cx - r, cy + r + 1, 0xFF000000);
        Gui.drawRect(cx + r, cy - r - 1, cx + r + 1, cy + r + 1, 0xFF000000);

        Gui.drawRect(cx - r, cy - r, cx + r, cy - r + 1, 0xFFFFFFFF);
        Gui.drawRect(cx - r, cy + r - 1, cx + r, cy + r, 0xFFFFFFFF);
        Gui.drawRect(cx - r, cy - r, cx - r + 1, cy + r, 0xFFFFFFFF);
        Gui.drawRect(cx + r - 1, cy - r, cx + r, cy + r, 0xFFFFFFFF);
    }

    public boolean mouseClicked(int x, int y, int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return false;
        if (mouseX < x || mouseX > x + WIDTH || mouseY < y || mouseY > y + TOTAL_HEIGHT) return false;

        int svY = y + 2;
        if (mouseY >= svY && mouseY <= svY + SV_SIZE) {
            draggingSV = true;
            updateFromSV(x, svY, WIDTH, SV_SIZE, mouseX, mouseY);
            return true;
        }

        int barY = svY + SV_SIZE + SPACING;
        if (mouseY >= barY && mouseY <= barY + BAR_HEIGHT) {
            draggingHue = true;
            updateFromHueBar(x, barY, WIDTH, BAR_HEIGHT, mouseX);
            return true;
        }

        int previewY = barY + BAR_HEIGHT + SPACING;
        if (mouseY >= previewY && mouseY < previewY + PREVIEW_HEIGHT) {
            int pasteX = x + WIDTH - BUTTON_WIDTH;
            int copyX = pasteX - BUTTON_WIDTH - 1;
            if (mouseX >= pasteX) {
                Integer pasted = ColorClipboard.paste();
                if (pasted != null) setColor(pasted);
                flash('P', pasted != null);
                return true;
            }
            if (mouseX >= copyX && mouseX < copyX + BUTTON_WIDTH) {
                ColorClipboard.copy(getColor());
                flash('C', true);
                return true;
            }
        }
        return false;
    }

    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
    }

    public void mouseDragged(int x, int y, int mouseX, int mouseY) {
        int svY = y + 2;
        int barY = svY + SV_SIZE + SPACING;
        if (draggingSV) updateFromSV(x, svY, WIDTH, SV_SIZE, mouseX, mouseY);
        if (draggingHue) updateFromHueBar(x, barY, WIDTH, BAR_HEIGHT, mouseX);
    }

    private void updateFromSV(int x, int y, int width, int height, int mouseX, int mouseY) {
        saturation = clamp01((mouseX - x) / (float) width);
        brightness = 1f - clamp01((mouseY - y) / (float) height);
    }

    private void updateFromHueBar(int x, int y, int width, int height, int mouseX) {
        hue = clamp01((mouseX - x) / (float) width);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
