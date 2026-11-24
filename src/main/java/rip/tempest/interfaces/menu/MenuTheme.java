package rip.tempest.interfaces.menu;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.RoundedEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.awt.Font;

public final class MenuTheme {
    public static final Color CLEAR = new Color(0, 0, 0, 0);
    public static final Color DANGER = new Color(244, 104, 104);
    public static final Color SUCCESS = new Color(92, 224, 140);
    public static final Color WARNING = new Color(250, 196, 70);

    public static final int TEXT = 0xFFFFFF;
    public static final int TEXT_SECONDARY = 0xB4B4C3;
    public static final int TEXT_MUTED = 0x71718A;

    public static final float RADIUS = 6f;
    public static final float BUTTON_HEIGHT = 20f;

    public static final float MARGIN = 24f;

    private static final ResourceLocation PRESS_SOUND = new ResourceLocation("gui.button.press");

    private static Color accent = new Color(118, 124, 255), accentSecond = new Color(168, 104, 255);
    private static long frame = -1;

    private MenuTheme() {
    }

    public static boolean astolfo() {
        try {
            return rip.tempest.modules.impl.client.ClientSettingsModule.menuStyle.getValue()
                    == rip.tempest.modules.impl.client.ClientSettingsModule.MenuStyle.Astolfo;
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static void beginFrame() {
        long now = System.currentTimeMillis();
        if (now == frame) return;
        frame = now;
        try {
            ColorProcess.update();
            Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
            accent = readable(ColorProcess.getColor());
            accentSecond = readable(second.equals(first) ? ColorProcess.getColor() : second);
        } catch (RuntimeException ignored) {
        }
    }

    public static Color accent() {
        return accent;
    }

    public static Color accentSecond() {
        return accentSecond;
    }

    private static Color readable(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (hsb[2] >= 0.55f) return new Color(color.getRed(), color.getGreen(), color.getBlue());
        return Color.getHSBColor(hsb[0], hsb[1], 0.55f);
    }

    public static Color alpha(Color color, float factor) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * clamp(factor)));
    }

    public static Color white(float alpha) {
        return new Color(255, 255, 255, Math.round(255 * clamp(alpha)));
    }

    public static Color mix(Color from, Color to, float amount) {
        float t = clamp(amount);
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    public static int text(int rgb, float alpha) {
        return (Math.max(4, Math.round(255 * clamp(alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    public static int text(Color color, float alpha) {
        return text(color.getRGB(), alpha * color.getAlpha() / 255f);
    }

    private static CustomFontRenderer display, title, large, small;

    public static CustomFontRenderer display() {
        if (display == null) display = FontProcess.getStyledFont("tempest-bold", 72, Font.PLAIN);
        return display;
    }

    public static CustomFontRenderer title() {
        if (astolfo()) return AstolfoMenu.regular28();
        if (title == null) title = FontProcess.getStyledFont("tempest-bold", 40, Font.PLAIN);
        return title;
    }

    public static CustomFontRenderer large() {
        if (astolfo()) return AstolfoMenu.light20();
        if (large == null) large = FontProcess.getStyledFont("tempest", 24, Font.PLAIN);
        return large;
    }

    public static CustomFontRenderer body() {
        if (astolfo()) return AstolfoMenu.regular18();
        return FontProcess.getFont("tempest");
    }

    public static CustomFontRenderer bold() {
        if (astolfo()) return FontProcess.getFont("robotomedium");
        return FontProcess.getFont("bold");
    }

    public static CustomFontRenderer small() {
        if (astolfo()) return AstolfoMenu.regular15();
        if (small == null) small = FontProcess.getStyledFont("tempest", 15, Font.PLAIN);
        return small;
    }

    public static FontRenderer minecraft() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    public static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    public static float smooth(float current, float target, float delta, float speed) {
        float next = current + (target - current) * (1f - (float) Math.exp(-delta * speed));
        return Math.abs(target - next) < 0.01f ? target : next;
    }

    public static float easeOut(float t) {
        float inverse = 1f - clamp(t);
        return 1f - inverse * inverse * inverse;
    }

    public static float easeOutBack(float t) {
        float x = clamp(t) - 1f;
        return 1f + 2.2f * x * x * x + 1.2f * x * x;
    }

    public static boolean contains(float x, float y, float w, float h, float px, float py) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public static boolean contains(float[] bounds, float px, float py) {
        return contains(bounds[0], bounds[1], bounds[2], bounds[3], px, py);
    }

    public static void set(float[] bounds, float x, float y, float w, float h) {
        bounds[0] = x;
        bounds[1] = y;
        bounds[2] = w;
        bounds[3] = h;
    }

    public static void playPress() {
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(PRESS_SOUND, 1.0F));
    }

    public static void surface(float x, float y, float w, float h, float radius, float hover, float alpha) {
        if (astolfo()) {
            float lit = easeOut(hover);
            Glyphs.bar(x, y, w, h, new Color(18, 18, 18, Math.round((0x77 + 0x22 * lit) * clamp(alpha))));
            frame(x, y, w, h, mix(new Color(51, 51, 51, Math.round(128 * clamp(alpha))), alpha(accent, alpha), lit));
            return;
        }
        RenderUtils.drawRoundedRect(x, y, w, h, radius, white((0.045f + 0.035f * hover) * alpha));
        RenderUtils.drawRoundOutline(x, y, w, h, radius, 1f, CLEAR, white((0.085f + 0.07f * hover) * alpha));
    }

    public static void selectedSurface(float x, float y, float w, float h, float radius, float amount, float alpha) {
        if (amount <= 0.01f) return;
        if (astolfo()) {
            Glyphs.bar(x, y, w, h, alpha(accent, 0.08f * amount * alpha));
            frame(x, y, w, h, alpha(accent, amount * alpha));
            return;
        }
        RoundedEffects.drawShadow(x, y, w, h, radius, 12f, 0f, 0f, 0f, alpha(accent, 0.18f * amount * alpha));
        RenderUtils.drawRoundedRect(x, y, w, h, radius, alpha(accent, 0.11f * amount * alpha));
        RenderUtils.drawRoundOutline(x, y, w, h, radius, 1f, CLEAR, alpha(accent, 0.7f * amount * alpha));
    }

    public enum Kind {PLAIN, PRIMARY, DANGER}

    public static void button(float[] bounds, String label, MenuIcons.Icon icon, Kind kind, float hover, boolean enabled, float alpha) {
        float x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        if (astolfo()) {
            astolfoButton(x, y, w, h, label, icon, kind, hover, enabled, alpha);
            return;
        }
        float lit = enabled ? easeOut(hover) : 0f;
        float fade = alpha * (enabled ? 1f : 0.4f);

        Color fill, border;
        int textColor;
        if (kind == Kind.PRIMARY) {
            fill = alpha(accent, (0.22f + 0.16f * lit) * fade);
            border = alpha(accent, (0.55f + 0.35f * lit) * fade);
            textColor = TEXT;
        } else if (kind == Kind.DANGER && lit > 0f) {
            fill = mix(white(0.05f * fade), alpha(DANGER, 0.2f * fade), lit);
            border = mix(white(0.1f * fade), alpha(DANGER, 0.75f * fade), lit);
            textColor = mix(new Color(TEXT_SECONDARY), new Color(255, 200, 200), lit).getRGB();
        } else {
            fill = white((0.05f + 0.04f * lit) * fade);
            border = mix(white(0.1f * fade), alpha(accent, 0.7f * fade), lit);
            textColor = mix(new Color(TEXT_SECONDARY), Color.WHITE, lit).getRGB();
        }
        RenderUtils.drawRoundedRect(x, y, w, h, RADIUS, fill);
        RenderUtils.drawRoundOutline(x, y, w, h, RADIUS, 1f, CLEAR, border);

        CustomFontRenderer font = body();
        float iconSize = icon != null ? 4.5f : 0f;
        float content = (label.isEmpty() ? 0f : font.getStringWidth(label)) + (icon != null ? iconSize * 2f + (label.isEmpty() ? 0f : 5f) : 0f);
        float cx = x + (w - content) / 2f;
        if (icon != null) {
            icon.draw(cx + iconSize, y + h / 2f, iconSize, alpha(new Color(textColor), fade));
            cx += iconSize * 2f + 5f;
        }
        if (!label.isEmpty()) font.drawString(label, cx, y + (h - font.getHeight()) / 2f, text(textColor, fade));
    }

    public static void iconButton(float[] bounds, MenuIcons.Icon icon, Kind kind, float hover, float alpha) {
        float x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];
        if (astolfo()) {
            astolfoButton(x, y, w, h, "", icon, kind, hover, true, alpha);
            return;
        }
        float lit = easeOut(hover);
        Color tone = kind == Kind.DANGER ? DANGER : accent;
        RenderUtils.drawRoundedRect(x, y, w, h, 5f, mix(white(0.05f * alpha), alpha(tone, 0.2f * alpha), lit));
        RenderUtils.drawRoundOutline(x, y, w, h, 5f, 1f, CLEAR, mix(white(0.09f * alpha), alpha(tone, 0.7f * alpha), lit));
        Color glyph = mix(white(0.7f * alpha), kind == Kind.DANGER ? alpha(new Color(255, 190, 190), alpha) : white(alpha), lit);
        icon.draw(x + w / 2f, y + h / 2f, Math.min(w, h) * 0.24f, glyph);
    }

    public static float badge(float x, float y, String label, Color tone, float alpha) {
        CustomFontRenderer font = small();
        float w = font.getStringWidth(label) + 8f, h = 10f;
        if (astolfo()) {
            Glyphs.bar(x, y, w, h, new Color(0, 0, 0, Math.round(120 * clamp(alpha))));
            frame(x, y, w, h, alpha(tone, 0.6f * alpha));
            font.drawString(label, x + 4f, y + (h - font.getHeight()) / 2f, text(mix(tone, Color.WHITE, 0.45f), alpha));
            return w;
        }
        RenderUtils.drawRoundedRect(x, y, w, h, 4f, alpha(tone, 0.16f * alpha));
        font.drawString(label, x + 4f, y + (h - font.getHeight()) / 2f, text(mix(tone, Color.WHITE, 0.45f), alpha));
        return w;
    }

    public static void fieldFrame(float x, float y, float w, float h, boolean focused, float alpha) {
        if (astolfo()) {
            Glyphs.bar(x, y, w, h, new Color(0, 0, 0, Math.round(170 * clamp(alpha))));
            frame(x, y, w, h, focused ? alpha(accent, alpha) : new Color(160, 160, 160, Math.round(200 * clamp(alpha))));
            return;
        }
        RenderUtils.drawRoundedRect(x, y, w, h, 5f, white((focused ? 0.07f : 0.045f) * alpha));
        RenderUtils.drawRoundOutline(x, y, w, h, 5f, 1f, CLEAR, focused ? alpha(accent, 0.8f * alpha) : white(0.1f * alpha));
    }

    public static void scrollbar(float x, float top, float bottom, float scroll, float maxScroll, boolean active, float alpha) {
        if (maxScroll <= 0f) return;
        float view = bottom - top;
        float thumb = Math.max(20f, view * view / (view + maxScroll));
        float thumbY = top + (view - thumb) * (scroll / maxScroll);
        if (astolfo()) {
            Glyphs.bar(x - 1f, top, 2f, view, new Color(0, 0, 0, Math.round(110 * clamp(alpha))));
            Glyphs.bar(x - 1f, thumbY, 2f, thumb, active ? alpha(accent, alpha) : new Color(128, 128, 128, Math.round(220 * clamp(alpha))));
            return;
        }
        Glyphs.stroke(x, top + 1.5f, x, bottom - 1.5f, 3f, white(0.05f * alpha));
        Glyphs.stroke(x, thumbY + 1.5f, x, thumbY + thumb - 1.5f, 3f, active ? alpha(accent, 0.9f * alpha) : white(0.3f * alpha));
    }

    public static float thumbHeight(float top, float bottom, float maxScroll) {
        float view = bottom - top;
        return Math.max(20f, view * view / (view + maxScroll));
    }

    public static void dialogPanel(int screenWidth, int screenHeight, float x, float y, float w, float h, float shown) {
        if (astolfo()) {
            Glyphs.bar(0f, 0f, screenWidth, screenHeight, new Color(0, 0, 0, Math.round(150 * shown)));
            Glyphs.bar(x, y, w, h, new Color(17, 17, 17, Math.round(240 * shown)));
            frame(x, y, w, h, new Color(51, 51, 51, Math.round(255 * shown)));
            Glyphs.bar(x, y, w, 1f, alpha(accent, shown));
            return;
        }
        Glyphs.bar(0f, 0f, screenWidth, screenHeight, new Color(0, 0, 0, Math.round(150 * shown)));
        RoundedEffects.drawShadow(x, y, w, h, 10f, 20f, 0f, 0f, 4f, new Color(0, 0, 0, Math.round(150 * shown)));
        RenderUtils.drawRoundedRect(x, y, w, h, 10f, new Color(17, 17, 24, Math.round(248 * shown)));
        RenderUtils.drawRoundOutline(x, y, w, h, 10f, 1f, CLEAR, white(0.11f * shown));

        Glyphs.gradient(x + 10f, y, w - 20f, 1f, alpha(accent, 0.9f * shown), alpha(accentSecond, 0.9f * shown));
    }

    public static void tooltip(String text, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        FontRenderer font = minecraft();
        String[] lines = text.split("\n");
        float boxWidth = 0f;
        for (String line : lines) boxWidth = Math.max(boxWidth, font.getStringWidth(line));
        boxWidth += 10f;
        float boxHeight = lines.length * 10f + 7f;
        float x = Math.max(4f, Math.min(screenWidth - boxWidth - 4f, mouseX + 10f));
        float y = Math.max(4f, Math.min(screenHeight - boxHeight - 4f, mouseY - 6f));
        if (astolfo()) {
            Glyphs.bar(x, y, boxWidth, boxHeight, new Color(16, 16, 16, 235));
            frame(x, y, boxWidth, boxHeight, alpha(accent, 0.8f));
            for (int i = 0; i < lines.length; i++) font.drawString(lines[i], x + 5f, y + 5f + i * 10f, 0xFFE0E0E0, false);
            return;
        }

        RoundedEffects.drawShadow(x, y, boxWidth, boxHeight, 4f, 8f, 0f, 0f, 2f, new Color(0, 0, 0, 120));
        RenderUtils.drawRoundedRect(x, y, boxWidth, boxHeight, 4f, new Color(18, 18, 25, 246));
        RenderUtils.drawRoundOutline(x, y, boxWidth, boxHeight, 4f, 1f, CLEAR, white(0.12f));
        for (int i = 0; i < lines.length; i++) {
            font.drawString(lines[i], x + 5f, y + 5f + i * 10f, text(TEXT_SECONDARY, 1f), false);
        }
    }

    public static void frame(float x, float y, float w, float h, Color color) {
        if (color.getAlpha() <= 0) return;
        Glyphs.bar(x, y, w, 1f, color);
        Glyphs.bar(x, y + h - 1f, w, 1f, color);
        Glyphs.bar(x, y + 1f, 1f, h - 2f, color);
        Glyphs.bar(x + w - 1f, y + 1f, 1f, h - 2f, color);
    }

    private static void astolfoButton(float x, float y, float w, float h, String label, MenuIcons.Icon icon, Kind kind,
                                      float hover, boolean enabled, float alpha) {
        float lit = enabled ? easeOut(hover) : 0f;
        float a = clamp(alpha);
        Color tone = kind == Kind.DANGER ? DANGER : accent;
        Color fill = !enabled ? new Color(0, 0, 0, Math.round(0x55 * a))
                : mix(new Color(18, 18, 18, Math.round(0x88 * a)), new Color(20, 20, 20, Math.round(0x99 * a)), lit);
        Glyphs.bar(x, y, w, h, fill);
        Color outline = !enabled ? new Color(25, 25, 25, Math.round(128 * a))
                : mix(new Color(51, 51, 51, Math.round((kind == Kind.PRIMARY ? 200 : 128) * a)), alpha(tone, a), lit);
        if (kind == Kind.PRIMARY && enabled) outline = mix(alpha(tone, 0.55f * a), alpha(tone, a), lit);
        frame(x, y, w, h, outline);

        int textRgb = !enabled ? 0xA0A0A0 : mix(new Color(0xE0E0E0), tone, lit).getRGB() & 0xFFFFFF;
        if (label.isEmpty()) {
            if (icon != null) icon.draw(x + w / 2f, y + h / 2f, Math.min(w, h) * 0.24f, alpha(new Color(textRgb), a));
            return;
        }
        FontRenderer font = minecraft();
        font.drawString(label, x + (w - font.getStringWidth(label)) / 2f, y + (h - 8f) / 2f, text(textRgb, a), false);
    }

    public static float pageHeader(String title, String subtitle, float x, float y, float intro) {
        CustomFontRenderer titleFont = title(), small = body();
        float top = y - (1f - intro) * 6f;
        titleFont.drawString(title, x, top, text(TEXT, intro));
        float under = top + titleFont.getHeight() + 6f;
        if (subtitle != null && !subtitle.isEmpty()) {
            small.drawString(subtitle, x, under, text(TEXT_MUTED, intro));
            under += small.getHeight() + 2f;
        }
        return under;
    }
}
