package rip.tempest.modules.impl.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.RenderUtils;

import java.awt.Color;
import java.awt.Font;

final class TickerWatermark {
    private static final long HOLD_MS = 2500, SLIDE_MS = 450, SAMPLE_MS = 500;
    private static final float HEIGHT = 14, PAD = 5, GAP = 4.5F, DIVIDER_W = 1, RADIUS = 2.3F, RISE = 5;
    private static final Color BOX = new Color(12, 12, 15, 199);
    private static final int TEXT = 0xFFFFFF, MUTED = 0x9DA1AB;

    private final long start = System.currentTimeMillis();
    private long lastSample;
    private int fps, ping;

    private static CustomFontRenderer nameFont() {
        return FontProcess.getStyledFont("manropeextrabold", 16, Font.PLAIN);
    }

    private static CustomFontRenderer infoFont() {
        return FontProcess.getStyledFont("manropebold", 15, Font.PLAIN);
    }

    private void sample(int currentPing) {
        long now = System.currentTimeMillis();
        if (now - lastSample >= SAMPLE_MS || lastSample == 0) {
            fps = Minecraft.getDebugFPS();
            ping = currentPing;
            lastSample = now;
        }
    }

    private String[][] readings(String server, String time) {
        return new String[][]{{String.valueOf(fps), " fps"}, {String.valueOf(ping), " ms ping"}, {server, ""}, {time, ""}};
    }

    private static float width(String[] reading) {
        return infoFont().getStringWidth(reading[0]) + infoFont().getStringWidth(reading[1]);
    }

    private static float slotWidth(String server, String time) {
        float w = Math.max(width(new String[]{"999", " fps"}), width(new String[]{"999", " ms ping"}));
        w = Math.max(w, width(new String[]{server, ""}));
        return Math.max(w, width(new String[]{time, ""}));
    }

    float[] size(String name, String server, String time) {
        return new float[]{2 + PAD + nameFont().getStringWidth(name) + GAP + DIVIDER_W + GAP + slotWidth(server, time) + PAD + 2, HEIGHT + 4};
    }

    void draw(String name, String server, String time, int currentPing, boolean shapeOnly) {
        sample(currentPing);
        Color client = ColorProcess.getColor();
        float x = 2, y = 2;
        float slot = slotWidth(server, time);
        float w = PAD + nameFont().getStringWidth(name) + GAP + DIVIDER_W + GAP + slot + PAD;

        if (shapeOnly) {
            RenderUtils.drawRoundedRect(x, y, w, HEIGHT, RADIUS, client);
            return;
        }
        RenderUtils.drawRoundedRect(x, y, w, HEIGHT, RADIUS, BOX);

        float nameX = x + PAD;
        drawText(nameFont(), name, nameX, mid(nameFont(), y), TEXT, 1);
        float divX = nameX + nameFont().getStringWidth(name) + GAP;
        RenderUtils.drawRect(divX, y + 4, DIVIDER_W, HEIGHT - 8, client);

        String[][] readings = readings(server, time);
        long elapsed = System.currentTimeMillis() - start;
        long period = HOLD_MS + SLIDE_MS;
        int index = (int) ((elapsed / period) % readings.length);
        long into = elapsed % period;
        float t = into < HOLD_MS ? 0 : (into - HOLD_MS) / (float) SLIDE_MS;
        float e = 1 - (float) Math.pow(1 - t, 5);

        float slotX = divX + DIVIDER_W + GAP, textY = mid(infoFont(), y);
        drawReading(readings[index], slotX, textY - RISE * e, 1 - e);
        if (t > 0) drawReading(readings[(index + 1) % readings.length], slotX, textY + RISE * (1 - e), e);
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawReading(String[] reading, float x, float y, float a) {
        drawText(infoFont(), reading[0], x, y, TEXT, a);
        if (!reading[1].isEmpty()) drawText(infoFont(), reading[1], x + infoFont().getStringWidth(reading[0]), y, MUTED, a);
    }

    private static float mid(CustomFontRenderer font, float y) {
        return y + (HEIGHT - font.getHeight()) / 2F;
    }

    private static void drawText(CustomFontRenderer font, String s, float x, float y, int rgb, float a) {
        if (a < 0.03F || s.isEmpty()) return;
        GlStateManager.enableBlend();
        int alpha = Math.max(5, Math.round(255 * Math.min(1, a)));
        font.drawString(s, x, y, (alpha << 24) | rgb);
    }
}
