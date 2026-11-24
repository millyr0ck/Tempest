package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.Notification;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Notifications", category = ModuleCategory.VISUALS)
public final class NotificationsModule extends Module {
    public static final ModeProperty<Style> style = new ModeProperty<>("Style", Style.Simple);

    public static final ModeProperty<Corner> position = new ModeProperty<>("Position", Corner.BottomRight);

    public static final ModeProperty<Accent> accent = new ModeProperty<>("Accent", Accent.Type, () -> style.getValue() == Style.Radium || style.getValue() == Style.Exhibition);
    public static final ModeProperty<Typeface> font = new ModeProperty<>("Font", Typeface.Clean, () -> style.getValue() == Style.Simple);

    public static final NumberProperty duration = new NumberProperty("Duration", 2.5, 1, 8, 0.25);

    public static final NumberProperty maxVisible = new NumberProperty("Max Visible", 5, 1, 10, 1);

    private static final float MARGIN = 6f;
    private static final float GAP = 5f;
    private static final float LINE_GAP = 3f;
    private static final float MAX_TEXT_WIDTH = 170f;

    private static final int TITLE_RGB = 0xF0F0F4;
    private static final int BODY_RGB = 0xA9A9B6;

    private static final float PAD = 7f;
    private static final float PLAIN_GAP = 3f;
    private static final float PLAIN_MIN_WIDTH = 84f;

    private static final float SLIDE = 14f;
    private static final Color BACKDROP = new Color(10, 10, 13, 185);
    private static final Color TRACK = new Color(255, 255, 255, 22);
    private static final int OFF_RGB = 0x85858F;

    private float chatLift;

    private long lastFrame;
    private CustomFontRenderer titleFont, bodyFont;

    @EventLink
    public final Listener<Render2DEvent> render2DEventListener = e -> render();

    @Override
    public void onDisable() {
        super.onDisable();
        NotificationManager.getNotifications().clear();
    }

    private void render() {
        CopyOnWriteArrayList<Notification> all = NotificationManager.getNotifications();
        if (all.isEmpty()) {
            lastFrame = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        float delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        switch (font.getValue()) {
            case Clean -> {
                titleFont = FontProcess.getFont("bold");
                bodyFont = FontProcess.getFont("tempest");
            }
            case Client -> titleFont = bodyFont = FontProcess.getCurrentFont();
            default -> titleFont = bodyFont = FontProcess.getFont("mc");
        }

        retire(all, now);
        if (style.getValue() == Style.Simple) {
            drawPlain(all, now, delta);
            return;
        }
        if (style.getValue() == Style.Line) {
            drawStrips(all, now, delta);
            return;
        }
        drawSimple(all, now);
    }

    private void retire(CopyOnWriteArrayList<Notification> all, long now) {
        int showing = 0;
        for (Notification notification : all) {
            if (!notification.isExiting()) showing++;
        }

        int limit = maxVisible.getValue().intValue();

        for (Notification notification : all) {
            if (notification.isExiting()) {
                if (notification.exit(now) >= 1f) all.remove(notification);
            } else if (notification.stale(now)) {
                all.remove(notification);
                showing--;
            } else if (notification.expired(now) || showing > limit) {
                notification.beginExit(now);
                showing--;
            }
        }
    }

    private void drawStrips(CopyOnWriteArrayList<Notification> all, long now, float delta) {
        boolean line = true;
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth(), screenHeight = sr.getScaledHeight();
        Corner corner = position.getValue();
        float height = Style.Line.height, pad = line ? 5.5f : 6f, gap = 3f;
        float smooth = delta <= 0f ? 1f : 1f - (float) Math.exp(-delta * 14f);
        float liftTarget = !corner.top && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat ? 14f : 0f;
        chatLift += (liftTarget - chatLift) * smooth;

        CustomFontRenderer head = FontProcess.getStyledFont("manropeextrabold", 15, java.awt.Font.PLAIN);
        CustomFontRenderer side = FontProcess.getStyledFont("manropebold", 14, java.awt.Font.PLAIN);
        Color client = ColorProcess.getColor();
        float stack = 0f;

        for (Notification notification : all) {
            float in = notification.enter(now), out = notification.exit(now);
            boolean toggle = notification.hasAccentDescription() && notification.getType() != NotificationType.WARNING;
            String title = fit(head, notification.getTitle());
            String desc = notification.getDescription() == null ? "" : notification.getDescription();
            String right = fit(side, toggle ? desc.toLowerCase() : desc);
            String key = line && toggle ? keyFor(notification.getTitle()) : "";
            int stateRgb = stateRgb(notification, client) & 0xFFFFFF;

            float content = head.getStringWidth(title) + 10f + (key.isEmpty() ? 0f : side.getStringWidth(key) + 6f) + side.getStringWidth(right);
            float target = Math.max(line ? 110f : 125f, pad * 2f + content);
            if (notification.shownWidth < 0f || delta <= 0f) notification.shownWidth = target;
            else notification.shownWidth += (target - notification.shownWidth) * smooth;
            float width = notification.shownWidth;

            float x = corner.left ? MARGIN : corner.center ? (screenWidth - width) / 2f : screenWidth - MARGIN - width;
            float y = corner.top ? MARGIN + stack : screenHeight - MARGIN - chatLift - stack - height;
            stack += (height + gap) * easeOutCubic(in) * (1f - easeInOutCubic(out));

            float open = easeOutCubic(clamp(in * 1.4f)) * (1f - easeInOutCubic(out));
            if (open < 0.02f) continue;
            float shown = width * open;
            float sx = corner.left ? x : corner.center ? x + (width - shown) / 2f : x + width - shown;
            float textA = clamp((open - 0.35f) / 0.65f);

            Color tone = notification.getType() == NotificationType.WARNING ? NotificationType.WARNING.getColor()
                    : toggle && notification.getType() == NotificationType.DISABLE ? new Color(OFF_RGB) : client;
            float left = notification.remaining(now);

            RenderUtils.startScissor(sx, y, shown, height);
            Glyphs.bar(x, y, width, height, BACKDROP);
            if (line) {
                Glyphs.bar(x + pad, y + height - 3f, width - pad * 2f, 0.5f, TRACK);
                Glyphs.bar(x + pad, y + height - 3f, (width - pad * 2f) * left, 0.5f, tone);
            } else {
                Glyphs.bar(x, y, width * left, height, withAlpha(tone, 0.22f));
                Glyphs.bar(x, y + height - 1f, width * left, 1f, tone);
            }
            if (textA >= 0.04f) {
                float rowH = line ? height - 3f : height;
                head.drawString(title, x + pad, y + (rowH - head.getHeight()) / 2f, text(TITLE_RGB, textA));
                float rx = x + width - pad - side.getStringWidth(right);
                side.drawString(right, rx, y + (rowH - side.getHeight()) / 2f, text(toggle ? stateRgb : BODY_RGB, textA));
                if (!key.isEmpty()) side.drawString(key, rx - 6f - side.getStringWidth(key), y + (rowH - side.getHeight()) / 2f, text(BODY_RGB, textA));
            }
            RenderUtils.endScissor();
        }
        RenderUtils.resetColor();
    }

    private static String keyFor(String moduleLabel) {
        for (rip.tempest.modules.Module m : rip.tempest.Tempest.INSTANCE.getModuleManager().getModules()) {
            if (m.getLabel().equals(moduleLabel)) {
                return m.getKey() > 0 ? "[" + org.lwjgl.input.Keyboard.getKeyName(m.getKey()) + "]" : "";
            }
        }
        return "";
    }

    private void drawPlain(CopyOnWriteArrayList<Notification> all, long now, float delta) {
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth(), screenHeight = sr.getScaledHeight();
        Corner corner = position.getValue();
        float height = Style.Simple.height;
        float smooth = delta <= 0f ? 1f : 1f - (float) Math.exp(-delta * 14f);

        float liftTarget = !corner.top && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat ? 14f : 0f;
        chatLift += (liftTarget - chatLift) * smooth;

        Color client = ColorProcess.getColor();
        float titleHeight = lineHeight(titleFont), bodyHeight = lineHeight(bodyFont);
        float stack = 0f;

        for (Notification notification : all) {
            float in = notification.enter(now), out = notification.exit(now);

            String title = fit(titleFont, notification.getTitle());
            String line = fit(bodyFont, notification.getDescription());
            boolean hasLine = !line.isEmpty();

            float target = Math.max(PLAIN_MIN_WIDTH, PAD * 2f + Math.max(titleFont.getStringWidth(title),
                    hasLine ? bodyFont.getStringWidth(line) : 0f));
            if (notification.shownWidth < 0f || delta <= 0f) notification.shownWidth = target;
            else notification.shownWidth += (target - notification.shownWidth) * smooth;
            float width = notification.shownWidth;

            float x = corner.left ? MARGIN
                    : corner.center ? (screenWidth - width) / 2f
                    : screenWidth - MARGIN - width;
            float y = corner.top ? MARGIN + stack : screenHeight - MARGIN - chatLift - stack - height;
            stack += (height + PLAIN_GAP) * easeOutCubic(in) * (1f - easeInOutCubic(out));

            float away = SLIDE * (1f - easeOutCubic(in) + easeInCubic(out));
            if (corner.center) y += corner.top ? -away : away;
            else x += corner.left ? -away : away;

            float a = easeOutCubic(clamp(in * 1.5f)) * (1f - easeInOutCubic(out));
            if (a < 0.02f) continue;

            Color timer = notification.getType() == NotificationType.WARNING ? NotificationType.WARNING.getColor() : client;
            Glyphs.bar(x, y, width, height, withAlpha(BACKDROP, a));
            Glyphs.bar(x, y + height - 1f, width, 1f, withAlpha(TRACK, a));
            Glyphs.bar(x, y + height - 1f, width * notification.remaining(now), 1f, withAlpha(timer, a));

            if (a >= 0.04f) {
                float block = hasLine ? titleHeight + LINE_GAP + bodyHeight : titleHeight;
                float top = y + (height - 1f - block) / 2f;
                titleFont.drawString(title, x + PAD, top, text(TITLE_RGB, a));
                if (hasLine) {
                    bodyFont.drawString(line, x + PAD, top + titleHeight + LINE_GAP, text(stateRgb(notification, client), a));
                }
            }
        }

        RenderUtils.resetColor();
    }

    private static int stateRgb(Notification notification, Color client) {
        if (notification.getType() == NotificationType.WARNING) return NotificationType.WARNING.getColor().getRGB();
        if (notification.hasAccentDescription()) {
            return notification.getType() == NotificationType.DISABLE ? OFF_RGB : client.getRGB();
        }
        return BODY_RGB;
    }

    private void drawSimple(CopyOnWriteArrayList<Notification> all, long now) {
        ScaledResolution sr = new ScaledResolution(mc);
        boolean radium = style.getValue() == Style.Radium;
        CustomFontRenderer title = radium ? FontProcess.getStyledFont("radium", 20, java.awt.Font.PLAIN)
                : FontProcess.getStyledFont("tahoma", 20, java.awt.Font.PLAIN);
        CustomFontRenderer body = radium ? FontProcess.getStyledFont("radium", 18, java.awt.Font.PLAIN)
                : FontProcess.getStyledFont("tahoma", 16, java.awt.Font.PLAIN);
        float height = style.getValue().height, gap = radium ? 2 : 1;

        float bottom = sr.getScaledHeight() - (!radium && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat ? 14 : 0);
        float stack = 0;

        for (Notification notification : all) {
            float in = notification.enter(now), out = notification.exit(now);
            String head = notification.getTitle() + (notification.repeats > 0 ? " (" + (notification.repeats + 1) + ")" : "");
            String line = notification.getDescription();
            boolean hasLine = line != null && !line.isEmpty();

            float textWidth = Math.max(title.getStringWidth(head), hasLine ? body.getStringWidth(line) : 0);
            float width = radium ? textWidth + 4 : textWidth + 10;
            float y = bottom - stack - height - (radium ? 2 : 0);
            stack += (height + gap) * easeOutCubic(in) * (1f - easeInOutCubic(out));
            float x = sr.getScaledWidth() - width + width * (1f - easeOutCubic(in) + easeInCubic(out));

            Color kind = accentFor(notification);
            if (radium) {
                Glyphs.bar(x, y, width, height, new Color(0, 0, 0, 0x78));

                float done = 1f - notification.remaining(now);
                Glyphs.bar(x, y + height - 2, width, 2, new Color((int) (kind.getRed() * 0.6f), (int) (kind.getGreen() * 0.6f), (int) (kind.getBlue() * 0.6f)));
                Glyphs.bar(x, y + height - 2, width * done, 2, kind);
                if (hasLine) {
                    title.drawStringWithShadow(head, x + 2, y + 2, -1);
                    body.drawStringWithShadow(line, x + 2, y + 14, -1);
                } else {
                    title.drawStringWithShadow(head, x + 2, y + 9, -1);
                }
            } else {
                Glyphs.bar(x, y, width, height, new Color(0, 0, 0, 200));
                Glyphs.bar(x, y, 2, height, kind);
                title.drawStringWithShadow(head, x + 6, y + 3, -1);
                if (hasLine) body.drawStringWithShadow(line, x + 6, y + 13, 0xFFAAAAAA);
            }
        }
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
    }

    private static Color accentFor(Notification notification) {
        return accent.getValue() == Accent.Type ? notification.getType().getColor() : rip.tempest.processes.ColorProcess.getColor();
    }

    private static String fit(CustomFontRenderer font, String text) {
        if (text == null) return "";
        if (font.getStringWidth(text) <= MAX_TEXT_WIDTH) return text;

        String trimmed = text;
        while (trimmed.length() > 1 && font.getStringWidth(trimmed + "..") > MAX_TEXT_WIDTH) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    private static float lineHeight(CustomFontRenderer font) {
        return "mc".equalsIgnoreCase(font.getNameFontTTF()) ? 7f : font.getHeight();
    }

    private static Color withAlpha(Color color, float factor) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * clamp(factor)));
    }

    private static int text(int rgb, float alpha) {
        return (Math.max(4, Math.round(255 * clamp(alpha))) << 24) | (rgb & 0xFFFFFF);
    }

    private static Color mix(Color from, Color to, float amount) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeInOutCubic(float t) {
        if (t < 0.5f) return 4f * t * t * t;
        float inverse = -2f * t + 2f;
        return 1f - inverse * inverse * inverse / 2f;
    }

    private static float easeOutBack(float t, float overshoot) {
        float shifted = t - 1f;
        return 1f + (overshoot + 1f) * shifted * shifted * shifted + overshoot * shifted * shifted;
    }

    public enum Style {
        Simple(24f, 0f),

        Radium(27f, 0f),
        Exhibition(23f, 0f),
        Line(16f, 0f);

        private final float height;

        Style(float height, float radius) {
            this.height = height;
        }
    }

    public enum Corner {
        BottomRight("Bottom Right", false, false, false),
        TopRight("Top Right", true, false, false),
        BottomLeft("Bottom Left", false, true, false),
        TopLeft("Top Left", true, true, false),
        TopCenter("Top Center", true, false, true);

        private final String display;
        private final boolean top, left, center;

        Corner(String display, boolean top, boolean left, boolean center) {
            this.display = display;
            this.top = top;
            this.left = left;
            this.center = center;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum Accent {
        Type,
        Client
    }

    public enum Typeface {
        Clean,
        Client,
        Minecraft
    }
}
