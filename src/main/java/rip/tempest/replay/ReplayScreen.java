package rip.tempest.replay;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.modules.impl.client.ReplayModule;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.RoundedEffects;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Desktop;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class ReplayScreen extends GuiScreen {
    private static final String[][] KEYS = {
            {"P, Space", "Play or pause"},
            {"Left, Right", "Back or ahead 5 s"},
            {"Up, Down", "Playback speed"},
            {"Scroll", "Camera speed"},
            {"Sprint", "Fly faster"},
            {"Click", "Follow a player or arrow"},
            {"F", "Follow yourself"},
            {"Sneak", "Stop following"},
            {"K", "Add a camera keyframe"},
            {"H", "Bar, no bar, no HUD"},
            {"Esc", "Back to the camera"},
    };

    private static final float PANEL_WIDTH = 204f, PAD = 8f, ROW = 20f, SMALL_BUTTON = 14f;

    private final float[] exitButton = new float[4], optionsButton = new float[4];
    private final float[] addButton = new float[4], previewButton = new float[4], renderButton = new float[4];
    private final float[] sizeChip = new float[4], fpsChip = new float[4], qualityChip = new float[4], formatChip = new float[4];
    private final float[] clearButton = new float[4], folderLink = new float[4], list = new float[4];
    private final Map<String, Float> hovers = new HashMap<>();
    private boolean dragging, ffmpegFound;
    private int selected = -1;
    private float scroll, maxScroll;
    private long clearArmed = -1L;
    private String tooltip;
    private final long opened = System.currentTimeMillis();
    private long lastFrame = opened;
    private float delta;

    @Override
    public void initGui() {
        ffmpegFound = VideoRenderer.findFfmpeg() != null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ReplayViewer viewer = ReplayViewer.current();
        if (viewer == null) return;
        long now = System.currentTimeMillis();
        delta = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float intro = MenuTheme.easeOut((now - opened) / 220f);
        tooltip = null;
        MenuTheme.beginFrame();
        if (selected >= viewer.path.size()) selected = viewer.path.size() - 1;

        drawCard(viewer, intro);

        CustomFontRenderer font = MenuTheme.body();
        float exitWidth = font.getStringWidth("Exit replay") + 34f, optionsWidth = font.getStringWidth("Options") + 34f;
        float top = ReplayHud.MARGIN - (1f - intro) * 6f;
        MenuTheme.set(exitButton, width - ReplayHud.MARGIN - exitWidth, top, exitWidth, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(optionsButton, exitButton[0] - 6f - optionsWidth, top, optionsWidth, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.button(optionsButton, "Options", MenuIcons.SLIDERS, MenuTheme.Kind.PLAIN, hover("options", optionsButton, mouseX, mouseY), true, intro);
        MenuTheme.button(exitButton, "Exit replay", MenuIcons.POWER, MenuTheme.Kind.DANGER, hover("exit", exitButton, mouseX, mouseY), true, intro);

        drawPath(viewer, mouseX, mouseY, top + MenuTheme.BUTTON_HEIGHT + 6f, intro);

        if (dragging) ReplayHud.dragTime = ReplayHud.timeAt(viewer, mouseX);
        ReplayHud.draw(viewer, width, height, mouseX, mouseY, true);

        if (!dragging && MenuTheme.contains(ReplayHud.speedButton, mouseX, mouseY)) tooltip = "Click: faster\nRight-click: slower";
        if (tooltip != null) MenuTheme.tooltip(tooltip, mouseX, mouseY, width, height);
    }

    private float hover(String key, float[] bounds, int mouseX, int mouseY) {
        float value = MenuTheme.approach(hovers.getOrDefault(key, 0f), MenuTheme.contains(bounds, mouseX, mouseY) ? 1f : 0f, delta * 9f);
        hovers.put(key, value);
        return value;
    }

    private void drawCard(ReplayViewer viewer, float intro) {
        CustomFontRenderer small = MenuTheme.small();
        String server = viewer.server();
        String when = new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(viewer.date()));
        float keyColumn = 0f, actionColumn = 0f;
        for (String[] row : KEYS) {
            keyColumn = Math.max(keyColumn, small.getStringWidth(row[0]));
            actionColumn = Math.max(actionColumn, small.getStringWidth(row[1]));
        }
        float pad = 10f, rowHeight = 11f;
        float w = Math.max(Math.max(fontRendererObj.getStringWidth(server), small.getStringWidth(when)), keyColumn + 12f + actionColumn) + pad * 2f;
        float h = pad + 10f + 4f + 9f + 9f + KEYS.length * rowHeight + pad - 2f;
        float x = ReplayHud.MARGIN, y = ReplayHud.MARGIN - (1f - intro) * 6f;

        panelBackground(x, y, w, h, intro);
        float textY = y + pad;
        fontRendererObj.drawString(server, x + pad, textY, MenuTheme.text(MenuTheme.TEXT, intro), false);
        textY += 10f + 4f;
        small.drawString(when + "  ·  " + ReplayViewer.clock(viewer.duration()), x + pad, textY, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
        textY += 9f + 9f;
        for (String[] row : KEYS) {
            small.drawString(row[0], x + pad, textY, MenuTheme.text(MenuTheme.TEXT, intro));
            small.drawString(row[1], x + pad + keyColumn + 12f, textY, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
            textY += rowHeight;
        }
    }

    private static void panelBackground(float x, float y, float w, float h, float alpha) {
        RoundedEffects.drawShadow(x, y, w, h, 8f, 14f, 0f, 0f, 3f, new Color(0, 0, 0, Math.round(90 * alpha)));
        RenderUtils.drawRoundedRect(x, y, w, h, 8f, new Color(16, 16, 23, Math.round(222 * alpha)));
        RenderUtils.drawRoundOutline(x, y, w, h, 8f, 1f, MenuTheme.CLEAR, MenuTheme.white(0.08f * alpha));
    }

    private void drawPath(ReplayViewer viewer, int mouseX, int mouseY, float top, float intro) {
        CameraPath path = viewer.path;
        CustomFontRenderer bold = MenuTheme.bold(), small = MenuTheme.small();
        float x = width - ReplayHud.MARGIN - PANEL_WIDTH, w = PANEL_WIDTH, inner = w - PAD * 2f;
        float bottomLimit = height - ReplayHud.MARGIN - ReplayHud.HEIGHT - 26f;
        int count = path.size();
        float listHeight = Math.max(ROW, Math.min(count, Math.max(1, (int) ((bottomLimit - top - 150f) / ROW))) * ROW);
        String status = status(viewer);
        float h = PAD + 13f + 13f + MenuTheme.BUTTON_HEIGHT + 8f + listHeight + 8f + 14f + (status != null ? 12f : 0f) + 6f + 12f + PAD;
        panelBackground(x, top, w, h, intro);

        float y = top + PAD;
        bold.drawString("Camera path", x + PAD, y, MenuTheme.text(MenuTheme.TEXT, intro));
        String counted = count + (count == 1 ? " keyframe" : " keyframes");
        small.drawString(counted, x + w - PAD - small.getStringWidth(counted), y + 1f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
        y += 13f;
        String line = count < 2 ? "Add two or more keyframes (K)" : "Video " + ReplayViewer.preciseClock((int) (path.duration() * 1000));
        small.drawString(line, x + PAD, y, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
        y += 13f;

        boolean playable = count >= 2;
        float third = (inner - 8f) / 3f;
        MenuTheme.set(addButton, x + PAD, y, third, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(previewButton, x + PAD + third + 4f, y, third, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(renderButton, x + PAD + (third + 4f) * 2f, y, third, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.button(addButton, "Add", MenuIcons.PLUS, MenuTheme.Kind.PLAIN, hover("add", addButton, mouseX, mouseY), true, intro);
        MenuTheme.button(previewButton, "Preview", MenuIcons.PLAY, MenuTheme.Kind.PLAIN, hover("preview", previewButton, mouseX, mouseY), playable, intro);
        MenuTheme.button(renderButton, "Render", MenuIcons.FILM, MenuTheme.Kind.PRIMARY, hover("render", renderButton, mouseX, mouseY), playable && renderReady(), intro);
        if (MenuTheme.contains(addButton, mouseX, mouseY)) tooltip = "Add a keyframe here, now (K)";
        y += MenuTheme.BUTTON_HEIGHT + 8f;

        MenuTheme.set(list, x + 2f, y, w - 4f, listHeight);
        maxScroll = Math.max(0f, count * ROW - listHeight);
        scroll = Math.max(0f, Math.min(maxScroll, scroll));
        if (count == 0) {
            small.drawCenteredString("No keyframes yet", x + w / 2f, y + (ROW - small.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
        } else {
            RenderUtils.startScissor(list[0], list[1], list[2], list[3]);
            for (int i = 0; i < count; i++) {
                float rowY = y + i * ROW - scroll;
                if (rowY + ROW < y || rowY > y + listHeight) continue;
                drawKeyframe(viewer, i, x, rowY, w, mouseX, mouseY, intro);
            }
            RenderUtils.endScissor();
            if (maxScroll > 0f) MenuTheme.scrollbar(x + w - 3f, y, y + listHeight, scroll, maxScroll, false, intro);
        }
        y += listHeight + 8f;

        float chipX = x + PAD;
        chipX = chip(sizeChip, "size", ReplayModule.renderSize.getValue().toString(), chipX, y, mouseX, mouseY, intro, "Video size");
        chipX = chip(fpsChip, "fps", ReplayModule.renderFps.getValue().toString(), chipX, y, mouseX, mouseY, intro, "Frame rate");
        chipX = chip(qualityChip, "quality", ReplayModule.renderQuality.getValue().toString(), chipX, y, mouseX, mouseY, intro, "Quality (MP4)");
        chip(formatChip, "format", ReplayModule.renderFormat.getValue().toString(), chipX, y, mouseX, mouseY, intro,
                "MP4 needs FFmpeg; PNG frames go\ninto a folder for your editor");
        y += 14f;

        MenuTheme.set(folderLink, 0, 0, 0, 0);
        if (status != null) {
            boolean problem = !status.startsWith("Saved");
            String shown = trim(small, status, inner - (status.startsWith("Saved") ? small.getStringWidth("Open folder") + 6f : 0f));
            small.drawString(shown, x + PAD, y + 2f, MenuTheme.text(problem ? MenuTheme.DANGER : MenuTheme.mix(MenuTheme.SUCCESS, Color.WHITE, 0.2f), intro));
            if (!problem) {
                String link = "Open folder";
                float linkX = x + w - PAD - small.getStringWidth(link);
                MenuTheme.set(folderLink, linkX, y, small.getStringWidth(link), 11f);
                float lit = hover("folder", folderLink, mouseX, mouseY);
                small.drawString(link, linkX, y + 2f, MenuTheme.text(MenuTheme.mix(MenuTheme.accent(), Color.WHITE, lit * 0.5f), intro));
            }
            y += 12f;
        }
        y += 6f;

        boolean armed = clearArmed >= 0 && System.currentTimeMillis() - clearArmed < 3000L;
        String clear = armed ? "Click again to clear" : "Clear path";
        MenuTheme.set(clearButton, x + PAD, y, small.getStringWidth(clear), 11f);
        float lit = count > 0 ? hover("clear", clearButton, mouseX, mouseY) : 0f;
        Color clearTone = armed ? MenuTheme.DANGER : MenuTheme.mix(new Color(MenuTheme.TEXT_MUTED), MenuTheme.DANGER, lit);
        small.drawString(clear, x + PAD, y + 1f, MenuTheme.text(clearTone, intro * (count > 0 ? 1f : 0.4f)));
    }

    private void drawKeyframe(ReplayViewer viewer, int index, float x, float y, float w, int mouseX, int mouseY, float intro) {
        CameraPath.Keyframe keyframe = viewer.path.get(index);
        CustomFontRenderer small = MenuTheme.small();
        boolean overList = MenuTheme.contains(list, mouseX, mouseY);
        float rowX = x + 4f, rowW = w - 8f;
        boolean overRow = overList && MenuTheme.contains(rowX, y, rowW, ROW, mouseX, mouseY);
        float lit = MenuTheme.approach(hovers.getOrDefault("row" + index, 0f), overRow ? 1f : 0f, delta * 9f);
        hovers.put("row" + index, lit);
        if (index == selected) {
            RenderUtils.drawRoundedRect(rowX, y + 1f, rowW, ROW - 2f, 5f, MenuTheme.alpha(MenuTheme.accent(), 0.16f * intro));
        } else if (lit > 0.01f) {
            RenderUtils.drawRoundedRect(rowX, y + 1f, rowW, ROW - 2f, 5f, MenuTheme.white(0.05f * lit * intro));
        }
        float textY = y + (ROW - small.getHeight()) / 2f;
        small.drawString(String.valueOf(index + 1), x + PAD + 1f, textY, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
        small.drawString(ReplayViewer.preciseClock(keyframe.time), x + PAD + 14f, textY, MenuTheme.text(MenuTheme.TEXT, intro));
        if (overRow) tooltip = "Go to this keyframe";

        float buttonY = y + (ROW - SMALL_BUTTON) / 2f;
        if (index > 0) {
            float[] minus = {x + 74f, buttonY, SMALL_BUTTON, SMALL_BUTTON}, plus = {x + 74f + SMALL_BUTTON + 36f, buttonY, SMALL_BUTTON, SMALL_BUTTON};
            MenuTheme.iconButton(minus, MenuIcons.MINUS, MenuTheme.Kind.PLAIN, overList ? hover("minus" + index, minus, mouseX, mouseY) : 0f, intro);
            MenuTheme.iconButton(plus, MenuIcons.PLUS, MenuTheme.Kind.PLAIN, overList ? hover("plus" + index, plus, mouseX, mouseY) : 0f, intro);
            String seconds = String.format("%.1f s", keyframe.seconds);
            small.drawCenteredString(seconds, x + 74f + SMALL_BUTTON + 18f, textY, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
            if (overList && (MenuTheme.contains(minus, mouseX, mouseY) || MenuTheme.contains(plus, mouseX, mouseY))) {
                tooltip = "Seconds of video since keyframe " + index + "\nShift: steps of 0.1 s";
            }
        }
        float[] update = {x + w - PAD - SMALL_BUTTON * 2f - 4f, buttonY, SMALL_BUTTON, SMALL_BUTTON};
        float[] delete = {x + w - PAD - SMALL_BUTTON, buttonY, SMALL_BUTTON, SMALL_BUTTON};
        MenuTheme.iconButton(update, MenuIcons.REFRESH, MenuTheme.Kind.PLAIN, overList ? hover("update" + index, update, mouseX, mouseY) : 0f, intro);
        MenuTheme.iconButton(delete, MenuIcons.TRASH, MenuTheme.Kind.DANGER, overList ? hover("delete" + index, delete, mouseX, mouseY) : 0f, intro);
        if (overList && MenuTheme.contains(update, mouseX, mouseY)) tooltip = "Move it to the camera and time now";
        if (overList && MenuTheme.contains(delete, mouseX, mouseY)) tooltip = "Delete";
    }

    private float chip(float[] bounds, String key, String label, float x, float y, int mouseX, int mouseY, float intro, String help) {
        CustomFontRenderer small = MenuTheme.small();
        float w = small.getStringWidth(label) + 10f, h = 12f;
        MenuTheme.set(bounds, x, y, w, h);
        float lit = hover(key, bounds, mouseX, mouseY);
        RenderUtils.drawRoundedRect(x, y, w, h, 4f, MenuTheme.white((0.06f + 0.06f * lit) * intro));
        small.drawString(label, x + 5f, y + (h - small.getHeight()) / 2f, MenuTheme.text(MenuTheme.mix(new Color(MenuTheme.TEXT_SECONDARY), Color.WHITE, lit), intro));
        if (MenuTheme.contains(bounds, mouseX, mouseY)) tooltip = help + "\nClick to change";
        return x + w + 4f;
    }

    private static String trim(CustomFontRenderer font, String text, float width) {
        if (font.getStringWidth(text) <= width) return text;
        while (text.length() > 1 && font.getStringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private boolean renderReady() {
        return ReplayModule.renderFormat.getValue() != ReplayModule.Format.MP4 || ffmpegFound;
    }

    private String status(ReplayViewer viewer) {
        if (!renderReady()) return "MP4 needs FFmpeg: put it in Tempest/ffmpeg";
        if (viewer.renderProblem != null) return "Render failed: " + viewer.renderProblem;
        if (viewer.lastRender != null) return "Saved " + viewer.lastRender.getName();
        return null;
    }

    private static <T extends Enum<T>> void step(rip.tempest.api.properties.impl.ModeProperty<T> property, int by) {
        T[] values = property.getValues();
        int next = Math.floorMod(property.getValue().ordinal() + by, values.length);
        property.setValue(values[next]);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth, mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (MenuTheme.contains(list, mouseX, mouseY)) scroll = Math.max(0f, Math.min(maxScroll, scroll - Math.signum(wheel) * ROW * 2f));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        ReplayViewer viewer = ReplayViewer.current();
        if (viewer == null) return;
        CameraPath path = viewer.path;
        if (mouseButton == 0 && MenuTheme.contains(exitButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            viewer.exit();
        } else if (mouseButton == 0 && MenuTheme.contains(optionsButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
        } else if (mouseButton == 0 && MenuTheme.contains(addButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            selected = viewer.addKeyframe();
            showRow(selected);
        } else if (mouseButton == 0 && MenuTheme.contains(previewButton, mouseX, mouseY) && path.size() >= 2) {
            MenuTheme.playPress();
            viewer.startPreview();
        } else if (mouseButton == 0 && MenuTheme.contains(renderButton, mouseX, mouseY) && path.size() >= 2 && renderReady()) {
            MenuTheme.playPress();
            viewer.requestRender();
        } else if (MenuTheme.contains(sizeChip, mouseX, mouseY)) {
            step(ReplayModule.renderSize, mouseButton == 1 ? -1 : 1);
        } else if (MenuTheme.contains(fpsChip, mouseX, mouseY)) {
            step(ReplayModule.renderFps, mouseButton == 1 ? -1 : 1);
        } else if (MenuTheme.contains(qualityChip, mouseX, mouseY)) {
            step(ReplayModule.renderQuality, mouseButton == 1 ? -1 : 1);
        } else if (MenuTheme.contains(formatChip, mouseX, mouseY)) {
            step(ReplayModule.renderFormat, mouseButton == 1 ? -1 : 1);
            ffmpegFound = VideoRenderer.findFfmpeg() != null;
        } else if (mouseButton == 0 && MenuTheme.contains(folderLink, mouseX, mouseY)) {
            try {
                Desktop.getDesktop().open(VideoRenderer.folder());
            } catch (Exception ignored) {
            }
        } else if (mouseButton == 0 && MenuTheme.contains(clearButton, mouseX, mouseY) && path.size() > 0) {
            if (clearArmed >= 0 && System.currentTimeMillis() - clearArmed < 3000L) {
                path.clear();
                selected = -1;
                clearArmed = -1L;
            } else {
                clearArmed = System.currentTimeMillis();
            }
        } else if (mouseButton == 0 && MenuTheme.contains(list, mouseX, mouseY)) {
            clickKeyframe(viewer, mouseX, mouseY);
        } else if (mouseButton == 0 && MenuTheme.contains(ReplayHud.playButton, mouseX, mouseY)) {
            viewer.togglePause();
        } else if (MenuTheme.contains(ReplayHud.speedButton, mouseX, mouseY)) {
            viewer.changeSpeed(mouseButton == 1 ? -1 : 1);
        } else if (mouseButton == 0 && ReplayHud.overTimeline(mouseX, mouseY)) {
            dragging = true;
            ReplayHud.dragTime = ReplayHud.timeAt(viewer, mouseX);
        }
    }

    private void clickKeyframe(ReplayViewer viewer, int mouseX, int mouseY) {
        int index = (int) ((mouseY - list[1] + scroll) / ROW);
        if (index < 0 || index >= viewer.path.size()) return;
        float x = width - ReplayHud.MARGIN - PANEL_WIDTH, w = PANEL_WIDTH, y = list[1] + index * ROW - scroll;
        float buttonY = y + (ROW - SMALL_BUTTON) / 2f;
        float step = isShiftKeyDown() ? 0.1f : 0.5f;
        if (index > 0 && MenuTheme.contains(x + 74f, buttonY, SMALL_BUTTON, SMALL_BUTTON, mouseX, mouseY)) {
            viewer.path.changeSeconds(index, -step);
        } else if (index > 0 && MenuTheme.contains(x + 74f + SMALL_BUTTON + 36f, buttonY, SMALL_BUTTON, SMALL_BUTTON, mouseX, mouseY)) {
            viewer.path.changeSeconds(index, step);
        } else if (MenuTheme.contains(x + w - PAD - SMALL_BUTTON * 2f - 4f, buttonY, SMALL_BUTTON, SMALL_BUTTON, mouseX, mouseY)) {
            MenuTheme.playPress();
            selected = viewer.updateKeyframe(index);
            showRow(selected);
        } else if (MenuTheme.contains(x + w - PAD - SMALL_BUTTON, buttonY, SMALL_BUTTON, SMALL_BUTTON, mouseX, mouseY)) {
            MenuTheme.playPress();
            viewer.path.remove(index);
            selected = Math.min(index, viewer.path.size() - 1);
        } else {
            selected = index;
            viewer.goToKeyframe(index);
        }
    }

    private void showRow(int index) {
        if (index < 0) return;
        float top = index * ROW, view = list[3];
        if (top < scroll) scroll = top;
        else if (top + ROW > scroll + view) scroll = top + ROW - view;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (!dragging) return;
        dragging = false;
        ReplayViewer viewer = ReplayViewer.current();
        if (viewer != null) viewer.requestJump(ReplayHud.timeAt(viewer, mouseX));
        ReplayHud.dragTime = -1;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        ReplayViewer viewer = ReplayViewer.current();
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindChat.getKeyCode()) {
            mc.displayGuiScreen(null);
        } else if (viewer != null && keyCode == Keyboard.KEY_SPACE) {
            viewer.togglePause();
        } else if (viewer != null && keyCode == Keyboard.KEY_DELETE && selected >= 0 && selected < viewer.path.size()) {
            viewer.path.remove(selected);
            selected = Math.min(selected, viewer.path.size() - 1);
        } else if (viewer != null && keyCode == Keyboard.KEY_K) {
            selected = viewer.addKeyframe();
            showRow(selected);
        } else if (viewer != null && keyCode > 0) {
            viewer.key(keyCode);
        }
    }

    @Override
    public void onGuiClosed() {
        dragging = false;
        ReplayHud.dragTime = -1;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
