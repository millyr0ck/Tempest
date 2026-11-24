package rip.tempest.replay;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.modules.impl.client.ReplayModule;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.RoundedEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;

import java.awt.Color;

final class ReplayHud {
    static final float MARGIN = 10f, HEIGHT = 26f;
    private static final float RADIUS = 8f;

    static final float[] bar = new float[4], playButton = new float[4], timeline = new float[4], speedButton = new float[4];

    static int dragTime = -1;

    private static float playHover, speedHover, lineHover;
    private static long lastFrame;

    private ReplayHud() {
    }

    static void draw(ReplayViewer viewer, int width, int height, int mouseX, int mouseY, boolean interactive) {
        MenuTheme.beginFrame();
        long now = System.currentTimeMillis();
        float delta = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Color accent = MenuTheme.accent();

        float x = MARGIN, w = width - MARGIN * 2f, y = height - MARGIN - HEIGHT;
        MenuTheme.set(bar, x, y, w, HEIGHT);
        RoundedEffects.drawShadow(x, y, w, HEIGHT, RADIUS, 14f, 0f, 0f, 3f, new Color(0, 0, 0, 90));
        RenderUtils.drawRoundedRect(x, y, w, HEIGHT, RADIUS, new Color(16, 16, 23, 222));
        RenderUtils.drawRoundOutline(x, y, w, HEIGHT, RADIUS, 1f, MenuTheme.CLEAR, MenuTheme.white(0.08f));

        MenuTheme.set(playButton, x + 3f, y + 3f, HEIGHT - 6f, HEIGHT - 6f);
        playHover = MenuTheme.approach(playHover, interactive && MenuTheme.contains(playButton, mouseX, mouseY) ? 1f : 0f, delta * 10f);
        if (playHover > 0.01f) {
            RenderUtils.drawRoundedRect(playButton[0], playButton[1], playButton[2], playButton[3], 6f, MenuTheme.white(0.08f * playHover));
        }
        float playX = playButton[0] + playButton[2] / 2f, playY = playButton[1] + playButton[3] / 2f;
        Color glyph = MenuTheme.mix(MenuTheme.white(0.82f), Color.WHITE, playHover);
        (viewer.paused() ? MenuIcons.PLAY : MenuIcons.PAUSE).draw(playX, playY, 5.5f, glyph);

        CustomFontRenderer font = MenuTheme.body();
        int shown = dragTime >= 0 ? dragTime : viewer.shownTime();
        String total = ReplayViewer.clock(viewer.duration());
        String clock = ReplayViewer.clock(shown), separator = " / ";
        float textX = playButton[0] + playButton[2] + 6f, textY = y + (HEIGHT - font.getHeight()) / 2f;
        font.drawString(clock, textX, textY, MenuTheme.text(MenuTheme.TEXT, 1f));
        font.drawString(separator + total, textX + font.getStringWidth(clock), textY, MenuTheme.text(MenuTheme.TEXT_MUTED, 1f));
        float timeWidth = font.getStringWidth(total) * 2f + font.getStringWidth(separator);

        String speed = speedLabel(viewer.speed());
        float speedWidth = font.getStringWidth("0.25x") + 14f;
        MenuTheme.set(speedButton, x + w - 3f - speedWidth, y + 3f, speedWidth, HEIGHT - 6f);
        speedHover = MenuTheme.approach(speedHover, interactive && MenuTheme.contains(speedButton, mouseX, mouseY) ? 1f : 0f, delta * 10f);
        if (speedHover > 0.01f) {
            RenderUtils.drawRoundedRect(speedButton[0], speedButton[1], speedButton[2], speedButton[3], 6f, MenuTheme.white(0.08f * speedHover));
        }
        boolean normal = viewer.speed() == 1f;
        Color speedTone = normal ? MenuTheme.mix(new Color(MenuTheme.TEXT_SECONDARY), Color.WHITE, speedHover) : accent;
        font.drawCenteredString(speed, speedButton[0] + speedButton[2] / 2f, textY, MenuTheme.text(speedTone, 1f));

        float lineLeft = textX + timeWidth + 12f, lineRight = speedButton[0] - 12f;
        MenuTheme.set(timeline, lineLeft, y, Math.max(1f, lineRight - lineLeft), HEIGHT);
        boolean overLine = interactive && MenuTheme.contains(lineLeft - 4f, y, lineRight - lineLeft + 8f, HEIGHT, mouseX, mouseY);
        lineHover = MenuTheme.approach(lineHover, overLine || dragTime >= 0 ? 1f : 0f, delta * 10f);
        float thickness = 3f + lineHover, lineY = y + HEIGHT / 2f;
        Glyphs.stroke(lineLeft, lineY, lineRight, lineY, thickness, MenuTheme.white(0.13f));
        float progress = viewer.duration() > 0 ? MenuTheme.clamp(shown / (float) viewer.duration()) : 0f;
        float cursor = lineLeft + (lineRight - lineLeft) * progress;
        if (cursor > lineLeft + 0.5f) Glyphs.stroke(lineLeft, lineY, cursor, lineY, thickness, accent);

        if (viewer.duration() > 0) {
            Color mark = MenuTheme.mix(MenuTheme.accentSecond(), Color.WHITE, 0.35f);
            for (CameraPath.Keyframe keyframe : viewer.path.keyframes()) {
                float at = lineLeft + (lineRight - lineLeft) * MenuTheme.clamp(keyframe.time() / (float) viewer.duration());
                Glyphs.disc(at, lineY - thickness / 2f - 3.5f, 1.6f, mark);
            }
        }
        if (overLine && dragTime < 0) {
            float hoverX = Math.max(lineLeft, Math.min(lineRight, mouseX));
            Glyphs.disc(hoverX, lineY, 1.5f + lineHover, MenuTheme.white(0.5f));
            timeLabel(ReplayViewer.clock(timeAt(viewer, mouseX)), hoverX, y - 4f);
        } else if (dragTime >= 0) {
            timeLabel(clock, cursor, y - 4f);
        }
        Glyphs.disc(cursor, lineY, 3.5f + lineHover, Color.WHITE);

        drawFollowing(viewer, width);
        boolean speedShown = now - viewer.cameraSpeedChanged <= 1500L;
        drawCameraSpeed(viewer, width, y - 22f, now);
        drawKeyframeNote(viewer, width, speedShown ? y - 42f : y - 22f, now);
    }

    private static void drawKeyframeNote(ReplayViewer viewer, int width, float bottom, long now) {
        long since = now - viewer.keyframeAdded;
        if (since > 1500L || viewer.keyframeIndex < 0) return;
        float alpha = since < 1100L ? 1f : 1f - (since - 1100L) / 400f;
        if (alpha < 0.03f) return;
        CustomFontRenderer font = MenuTheme.body();
        String label = "Keyframe " + (viewer.keyframeIndex + 1) + " added", value = viewer.path.size() < 2 ? "" : "  ·  video "
                + ReplayViewer.preciseClock((int) (viewer.path.duration() * 1000));
        float w = font.getStringWidth(label + value) + 16f, h = 16f;
        float x = (width - w) / 2f, y = bottom - h;
        RenderUtils.drawRoundedRect(x, y, w, h, 5f, new Color(16, 16, 23, Math.round(210 * alpha)));
        font.drawString(label, x + 8f, y + (h - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT, alpha));
        if (!value.isEmpty()) font.drawString(value, x + 8f + font.getStringWidth(label), y + (h - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));
    }

    static int timeAt(ReplayViewer viewer, int mouseX) {
        float t = MenuTheme.clamp((mouseX - timeline[0]) / timeline[2]);
        return Math.round(t * viewer.duration());
    }

    static boolean overTimeline(int mouseX, int mouseY) {
        return MenuTheme.contains(timeline[0] - 4f, timeline[1], timeline[2] + 8f, timeline[3], mouseX, mouseY);
    }

    static String speedLabel(float speed) {
        String text = Float.toString(speed);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + "x";
    }

    private static void timeLabel(String text, float centreX, float bottom) {
        CustomFontRenderer font = MenuTheme.small();
        float w = font.getStringWidth(text) + 10f, h = 13f;
        float x = centreX - w / 2f, y = bottom - h;
        RenderUtils.drawRoundedRect(x, y, w, h, 4f, new Color(18, 18, 25, 240));
        RenderUtils.drawRoundOutline(x, y, w, h, 4f, 1f, MenuTheme.CLEAR, MenuTheme.white(0.12f));
        font.drawString(text, x + 5f, y + (h - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT, 1f));
    }

    private static void drawFollowing(ReplayViewer viewer, int width) {
        Entity entity = viewer.spectating(), chased = viewer.chasing();
        if (entity == null && chased == null) return;
        FontRenderer names = Minecraft.getMinecraft().fontRendererObj;
        CustomFontRenderer font = MenuTheme.body(), small = MenuTheme.small();
        String lead = chased == null ? "Following " : viewer.chaseLanded() ? "Landed: " : "Following ";
        String name = chased == null ? entity.getName() : ReplayViewer.projectileName(chased);

        ReplayModule.Projectiles mode = ReplayModule.followProjectiles.getValue();
        String hint = chased != null || mode == ReplayModule.Projectiles.OFF ? "Sneak to stop"
                : "Sneak to stop  ·  follows their " + (mode == ReplayModule.Projectiles.ARROWS ? "arrows" : "projectiles");
        float nameWidth = names.getStringWidth(name);
        float w = Math.max(font.getStringWidth(lead) + nameWidth, small.getStringWidth(hint)) + 20f, h = 30f;
        float x = (width - w) / 2f, y = MARGIN;
        RenderUtils.drawRoundedRect(x, y, w, h, RADIUS, new Color(16, 16, 23, 210));
        RenderUtils.drawRoundOutline(x, y, w, h, RADIUS, 1f, MenuTheme.CLEAR, MenuTheme.white(0.08f));
        float lineX = x + (w - font.getStringWidth(lead) - nameWidth) / 2f;
        font.drawString(lead, lineX, y + 6f, MenuTheme.text(MenuTheme.TEXT_MUTED, 1f));
        names.drawString(name, lineX + font.getStringWidth(lead), y + 5f, 0xFFFFFFFF, false);
        small.drawCenteredString(hint, x + w / 2f, y + 19f, MenuTheme.text(MenuTheme.TEXT_MUTED, 1f));
    }

    private static void drawCameraSpeed(ReplayViewer viewer, int width, float bottom, long now) {
        long since = now - viewer.cameraSpeedChanged;
        if (since > 1500L) return;
        float alpha = since < 1100L ? 1f : 1f - (since - 1100L) / 400f;
        if (alpha < 0.03f) return;
        CustomFontRenderer font = MenuTheme.body();
        String label = "Camera speed ", value = String.format("%.1f", viewer.cameraSpeed());
        float w = font.getStringWidth(label + value) + 16f, h = 16f;
        float x = (width - w) / 2f, y = bottom - h;
        RenderUtils.drawRoundedRect(x, y, w, h, 5f, new Color(16, 16, 23, Math.round(210 * alpha)));
        font.drawString(label, x + 8f, y + (h - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));
        font.drawString(value, x + 8f + font.getStringWidth(label), y + (h - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT, alpha));
    }
}
