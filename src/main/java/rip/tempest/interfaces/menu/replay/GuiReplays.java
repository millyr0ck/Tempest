package rip.tempest.interfaces.menu.replay;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.interfaces.menu.MenuDock;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuPage;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.replay.CameraPath;
import rip.tempest.replay.ReplayFile;
import rip.tempest.replay.ReplayRecorder;
import rip.tempest.replay.ReplayViewer;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GuiReplays extends MenuPage {
    private static final float ROW_HEIGHT = 40f, ROW_GAP = 5f, TILE = 28f, ACTION = 20f, ACTION_GAP = 4f;
    private static final int WATCH = 0, RENAME = 1, DELETE = 2, ACTIONS = 3;
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    private static final Pattern RECORDED_NAME = Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}(_\\d+)?");
    private static final Color SINGLEPLAYER = new Color(92, 224, 140), MULTIPLAYER = new Color(110, 190, 255);

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private record Cached(long modified, long length, ReplayFile.Info info) {
    }

    private final List<Replay> replays = new ArrayList<>();
    private Replay selected;
    private float listTop, listBottom, scroll, scrollTarget, maxScroll;
    private final float[] folderButton = new float[4];
    private float folderHover;
    private String tooltip;
    private Dialog dialog;

    private static final class Replay {
        final File file;
        final ReplayFile.Info info;
        final String name, details;
        final boolean renamed;
        float y = Float.NaN, drawnY, hover, select;
        final float[] actionHover = new float[ACTIONS];
        long lastClick;

        Replay(File file, ReplayFile.Info info) {
            this.file = file;
            this.info = info;
            String base = file.getName().substring(0, file.getName().length() - ".mcpr".length());
            this.renamed = !RECORDED_NAME.matcher(base).matches();
            this.name = renamed ? base : info.server();
            long date = info.date() > 0 ? info.date() : file.lastModified();
            String when = new SimpleDateFormat("d MMM yyyy, HH:mm").format(new Date(date));
            this.details = (renamed ? info.server() + "  ·  " : "") + when + "  ·  " + ReplayViewer.clock(info.duration()) + "  ·  " + size(file.length());
        }

        long date() {
            return info.date() > 0 ? info.date() : file.lastModified();
        }
    }

    @Override
    protected MenuDock.Page page() {
        return MenuDock.Page.REPLAYS;
    }

    @Override
    protected boolean modal() {
        return dialog != null;
    }

    @Override
    public void initGui() {
        super.initGui();
        load();
    }

    @Override
    public void updateScreen() {
        if (dialog != null) dialog.tick();
    }

    private void load() {
        replays.clear();
        selected = null;
        File[] files = ReplayRecorder.folder().listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".mcpr"));
        if (files == null) return;
        for (File file : files) {
            String key = file.getAbsolutePath();
            Cached cached = CACHE.get(key);
            if (cached == null || cached.modified != file.lastModified() || cached.length != file.length()) {
                ReplayFile.Info info = ReplayFile.info(file);
                if (info == null) continue;
                cached = new Cached(file.lastModified(), file.length(), info);
                CACHE.put(key, cached);
            }
            replays.add(new Replay(file, cached.info));
        }
        replays.sort((a, b) -> Long.compare(b.date(), a.date()));
    }

    private void watch(Replay replay) {
        ReplayViewer.open(replay.file);
    }

    private void delete(Replay replay) {
        boolean gone = false;
        try {
            if (canTrash()) gone = Desktop.getDesktop().moveToTrash(replay.file);
        } catch (Exception ignored) {
        }
        if (!gone) gone = replay.file.delete();
        if (!gone) NotificationManager.post(NotificationType.WARNING, "Replays", "Couldn't delete " + replay.file.getName());

        File path = CameraPath.fileFor(replay.file);
        if (gone && path.isFile()) {
            boolean trashed = false;
            try {
                if (canTrash()) trashed = Desktop.getDesktop().moveToTrash(path);
            } catch (Exception ignored) {
            }
            if (!trashed) path.delete();
        }
        CACHE.remove(replay.file.getAbsolutePath());
        load();
    }

    private static boolean canTrash() {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (Exception e) {
            return false;
        }
    }

    private static String cleanName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        while (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).trim();
        return clean.isEmpty() ? null : clean;
    }

    private boolean rename(Replay replay, String name) {
        String clean = cleanName(name);
        if (clean == null) return false;
        File target = new File(replay.file.getParentFile(), clean + ".mcpr");
        if (target.equals(replay.file)) return true;
        File path = CameraPath.fileFor(replay.file);
        if (target.exists() || !replay.file.renameTo(target)) return false;

        if (path.isFile()) path.renameTo(CameraPath.fileFor(target));
        load();
        for (Replay r : replays) if (r.file.equals(target)) selected = r;
        return true;
    }

    private void openFolder() {
        File folder = ReplayRecorder.folder();
        if (!folder.isDirectory()) folder.mkdirs();
        try {
            Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            NotificationManager.post(NotificationType.WARNING, "Replays", "Couldn't open the folder");
        }
    }

    private static String size(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1L, bytes / 1024L) + " KB";
        return String.format("%.1f MB", bytes / (1024f * 1024f));
    }

    @Override
    protected void drawPage(int pointerX, int pointerY) {
        tooltip = null;
        String summary = replays.size() + (replays.size() == 1 ? " replay" : " replays");
        float under = MenuTheme.pageHeader("Replays", summary, columnX, 22f, intro);

        CustomFontRenderer font = MenuTheme.body();
        float folderWidth = font.getStringWidth("Open folder") + 30f;
        MenuTheme.set(folderButton, columnX + columnWidth - folderWidth, 26f - (1f - intro) * 6f, folderWidth, MenuTheme.BUTTON_HEIGHT);
        folderHover = MenuTheme.approach(folderHover, MenuTheme.contains(folderButton, pointerX, pointerY) ? 1f : 0f, delta * 8f);
        MenuTheme.button(folderButton, "Open folder", MenuIcons.FOLDER, MenuTheme.Kind.PLAIN, folderHover, true, intro);

        listTop = under + 12f;
        listBottom = contentBottom;
        maxScroll = Math.max(0f, replays.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP - (listBottom - listTop));
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll = MenuTheme.smooth(scroll, scrollTarget, delta, 16f);

        if (replays.isEmpty()) {
            float middle = (listTop + listBottom) / 2f - 10f, cx = columnX + columnWidth / 2f;
            MenuIcons.FILM.draw(cx, middle - 16f, 10f, MenuTheme.white(0.25f * intro));
            MenuTheme.bold().drawCenteredString("No replays yet", cx, middle + 2f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
            MenuTheme.body().drawCenteredString("Turn on Replay (Client) and play: your games show up here.", cx, middle + 16f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
            return;
        }

        boolean pointerInList = pointerY >= listTop && pointerY < listBottom;
        long now = System.currentTimeMillis();
        RenderUtils.startScissor(0f, listTop, width, listBottom - listTop);
        for (int i = 0; i < replays.size(); i++) {
            Replay replay = replays.get(i);
            float slot = i * (ROW_HEIGHT + ROW_GAP);
            replay.y = Float.isNaN(replay.y) ? slot : MenuTheme.smooth(replay.y, slot, delta, 18f);
            float appear = MenuTheme.easeOut((now - openTime - 40L - Math.min(i, 10) * 35L) / 360f);
            replay.drawnY = listTop - scroll + replay.y + (1f - appear) * 10f;

            boolean hovered = pointerInList && MenuTheme.contains(columnX, replay.drawnY, columnWidth, ROW_HEIGHT, pointerX, pointerY);
            replay.hover = MenuTheme.approach(replay.hover, hovered ? 1f : 0f, delta * 9f);
            replay.select = MenuTheme.approach(replay.select, replay == selected ? 1f : 0f, delta * 8f);
            if (replay.drawnY + ROW_HEIGHT < listTop - 14f || replay.drawnY > listBottom + 14f) continue;
            drawRow(replay, pointerX, pointerY, appear * intro);
        }
        RenderUtils.endScissor();

        MenuTheme.scrollbar(columnX + columnWidth + 8f, listTop, listBottom, scroll, maxScroll, false, intro);
    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (tooltip != null && dialog == null) MenuTheme.tooltip(tooltip, mouseX, mouseY, width, height);
        if (dialog != null) dialog.draw(mouseX, mouseY, System.currentTimeMillis());
    }

    private void drawRow(Replay replay, int pointerX, int pointerY, float alpha) {
        if (alpha < 0.02f) return;
        float x = columnX, y = replay.drawnY, w = columnWidth;
        float hover = MenuTheme.easeOut(replay.hover), select = MenuTheme.easeOut(replay.select);
        MenuTheme.surface(x, y, w, ROW_HEIGHT, 8f, hover * (1f - select), alpha * (1f - select));
        MenuTheme.selectedSurface(x, y, w, ROW_HEIGHT, 8f, select, alpha);

        float tileX = x + 6f, tileY = y + (ROW_HEIGHT - TILE) / 2f;
        Color tone = replay.info.singleplayer() ? SINGLEPLAYER : MULTIPLAYER;
        RenderUtils.drawRoundedRect(tileX, tileY, TILE, TILE, 6f, MenuTheme.alpha(tone, 0.2f * alpha));
        MenuIcons.FILM.draw(tileX + TILE / 2f, tileY + TILE / 2f, 6.5f, MenuTheme.alpha(MenuTheme.mix(tone, Color.WHITE, 0.3f), alpha));
        if (MenuTheme.contains(tileX, tileY, TILE, TILE, pointerX, pointerY)) {
            RenderUtils.drawRoundedRect(tileX, tileY, TILE, TILE, 6f, new Color(0, 0, 0, Math.round(150 * alpha)));
            Glyphs.play(tileX + TILE / 2f, tileY + TILE / 2f, 6f, MenuTheme.white(alpha));
        }

        float attention = Math.max(hover, select);
        float actionsLeft = x + w - 8f - ACTIONS * ACTION - (ACTIONS - 1) * ACTION_GAP;
        float textX = tileX + TILE + 9f;
        int textWidth = (int) (actionsLeft - 10f - textX);
        FontRenderer font = this.fontRendererObj;
        font.drawString(font.trimStringToWidth(replay.name, textWidth), textX, y + 8f, MenuTheme.text(MenuTheme.TEXT, alpha), false);
        font.drawString(font.trimStringToWidth(replay.details, textWidth), textX, y + 21f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha), false);

        float tagAlpha = alpha * (1f - attention);
        if (tagAlpha > 0.02f) {
            List<String> tags = new ArrayList<>();
            List<Color> tones = new ArrayList<>();
            tags.add(replay.info.singleplayer() ? "Singleplayer" : "Multiplayer");
            tones.add(tone);
            String version = replay.info.version();
            if (version != null && !version.startsWith("1.8")) {
                tags.add(version);
                tones.add(MenuTheme.WARNING);
            }
            CustomFontRenderer small = MenuTheme.small();
            float tagX = x + w - 10f;
            for (int i = tags.size() - 1; i >= 0; i--) {
                tagX -= small.getStringWidth(tags.get(i)) + 8f;
                MenuTheme.badge(tagX, y + (ROW_HEIGHT - 10f) / 2f, tags.get(i), tones.get(i), tagAlpha);
                tagX -= 4f;
            }
        }

        float actionsTop = y + (ROW_HEIGHT - ACTION) / 2f;
        for (int i = 0; i < ACTIONS; i++) {
            float[] b = actionBounds(actionsLeft, actionsTop, i);
            boolean over = MenuTheme.contains(b, pointerX, pointerY);
            replay.actionHover[i] = MenuTheme.approach(replay.actionHover[i], over ? 1f : 0f, delta * 10f);
            if (attention * alpha < 0.02f) continue;
            MenuIcons.Icon icon = i == WATCH ? MenuIcons.PLAY : i == RENAME ? MenuIcons.PENCIL : MenuIcons.TRASH;
            MenuTheme.iconButton(b, icon, i == DELETE ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PLAIN, replay.actionHover[i], attention * alpha);
            if (over) tooltip = i == WATCH ? "Watch" : i == RENAME ? "Rename" : "Delete";
        }
    }

    private static float[] actionBounds(float left, float top, int index) {
        return new float[]{left + index * (ACTION + ACTION_GAP), top, ACTION, ACTION};
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && dialog == null) {
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - Math.signum(wheel) * (ROW_HEIGHT + ROW_GAP) * 1.5f));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (dialog != null) {
            dialog.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        if (MenuTheme.contains(folderButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            openFolder();
            return;
        }
        if (mouseY < listTop || mouseY >= listBottom) return;

        for (Replay replay : replays) {
            if (!MenuTheme.contains(columnX, replay.drawnY, columnWidth, ROW_HEIGHT, mouseX, mouseY)) continue;
            selected = replay;

            float actionsLeft = columnX + columnWidth - 8f - ACTIONS * ACTION - (ACTIONS - 1) * ACTION_GAP;
            float actionsTop = replay.drawnY + (ROW_HEIGHT - ACTION) / 2f;
            for (int i = 0; i < ACTIONS; i++) {
                if (!MenuTheme.contains(actionBounds(actionsLeft, actionsTop, i), mouseX, mouseY)) continue;
                MenuTheme.playPress();
                act(replay, i);
                return;
            }

            long now = Minecraft.getSystemTime();
            float tileX = columnX + 6f, tileY = replay.drawnY + (ROW_HEIGHT - TILE) / 2f;
            if (MenuTheme.contains(tileX, tileY, TILE, TILE, mouseX, mouseY) || now - replay.lastClick < DOUBLE_CLICK_MILLIS) {
                MenuTheme.playPress();
                watch(replay);
            }
            replay.lastClick = now;
            return;
        }
    }

    private void act(Replay replay, int action) {
        switch (action) {
            case WATCH -> watch(replay);
            case RENAME -> dialog = new Dialog(replay, false);
            case DELETE -> dialog = new Dialog(replay, true);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (dialog != null) {
            dialog.keyTyped(typedChar, keyCode);
            return;
        }
        if ((keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) && !replays.isEmpty()) {
            int index = selected == null ? -1 : replays.indexOf(selected);
            int direction = keyCode == Keyboard.KEY_UP ? -1 : 1;
            int next = index < 0 ? (direction > 0 ? 0 : replays.size() - 1) : Math.max(0, Math.min(replays.size() - 1, index + direction));
            selected = replays.get(next);
            float top = next * (ROW_HEIGHT + ROW_GAP), view = listBottom - listTop;
            if (top < scrollTarget) scrollTarget = top;
            else if (top + ROW_HEIGHT > scrollTarget + view) scrollTarget = top + ROW_HEIGHT - view;
        } else if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && selected != null) {
            watch(selected);
        } else if (keyCode == Keyboard.KEY_DELETE && selected != null) {
            dialog = new Dialog(selected, true);
        } else if (keyCode == Keyboard.KEY_F2 && selected != null) {
            dialog = new Dialog(selected, false);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private final class Dialog {
        private static final float WIDTH = 260f, PAD = 16f;

        private final Replay replay;
        private final boolean deleting, trash;
        private final GuiTextField nameField;
        private final long opened = System.currentTimeMillis();
        private long closing = -1L, lastFrame = opened;
        private final float[] nameBox = new float[4], cancelBounds = new float[4], confirmBounds = new float[4];
        private float cancelHover, confirmHover;
        private String problem;

        Dialog(Replay replay, boolean deleting) {
            this.replay = replay;
            this.deleting = deleting;
            this.trash = deleting && canTrash();
            if (deleting) {
                nameField = null;
            } else {
                nameField = new GuiTextField(0, fontRendererObj, 0, 0, (int) (WIDTH - PAD * 2f - 12f), 9);
                nameField.setEnableBackgroundDrawing(false);
                nameField.setMaxStringLength(64);
                nameField.setText(replay.file.getName().substring(0, replay.file.getName().length() - ".mcpr".length()));
                nameField.setFocused(true);
            }
        }

        void tick() {
            if (nameField != null) nameField.updateCursorCounter();
        }

        private boolean valid() {
            return deleting || cleanName(nameField.getText()) != null;
        }

        void draw(int mouseX, int mouseY, long now) {
            float frameDelta = Math.min(0.1f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float shown = MenuTheme.easeOut((now - opened) / 200f);
            if (closing >= 0L) {
                float gone = MenuTheme.clamp((now - closing) / 140f);
                if (gone >= 1f) {
                    dialog = null;
                    return;
                }
                shown *= 1f - gone;
            }

            float panelHeight = PAD + 20f + 40f + MenuTheme.BUTTON_HEIGHT + PAD;
            float x = (width - WIDTH) / 2f, y = (height - panelHeight) / 2f + (1f - shown) * 8f, inner = WIDTH - PAD * 2f;
            MenuTheme.dialogPanel(width, height, x, y, WIDTH, panelHeight, shown);
            MenuTheme.bold().drawString(deleting ? "Delete replay" : "Rename replay", x + PAD, y + PAD, MenuTheme.text(MenuTheme.TEXT, shown));
            float top = y + PAD + 20f;

            FontRenderer font = fontRendererObj;
            if (deleting) {
                String what = trash ? "' goes to the Recycle Bin." : "' will be gone for good.";
                font.drawString(font.trimStringToWidth("'" + replay.name + what, (int) inner), x + PAD, top + 2f,
                        MenuTheme.text(MenuTheme.TEXT_SECONDARY, shown), false);
                font.drawString(trash ? "You can restore it from there." : "This can't be undone.", x + PAD, top + 14f,
                        MenuTheme.text(MenuTheme.TEXT_MUTED, shown), false);
            } else {
                MenuTheme.small().drawString(problem != null ? problem : "File name", x + PAD, top,
                        MenuTheme.text(problem != null ? MenuTheme.DANGER.getRGB() : MenuTheme.TEXT_MUTED, shown));
                MenuTheme.set(nameBox, x + PAD, top + 12f, inner, 20f);
                MenuTheme.fieldFrame(nameBox[0], nameBox[1], nameBox[2], nameBox[3], nameField.isFocused(), shown);
                nameField.xPosition = Math.round(nameBox[0] + 6f);
                nameField.yPosition = Math.round(nameBox[1] + 6f);
                nameField.setTextColor(MenuTheme.text(0xE6E6EE, shown));
                nameField.drawTextBox();
            }

            float buttonY = y + panelHeight - PAD - MenuTheme.BUTTON_HEIGHT, half = (inner - 6f) / 2f;
            MenuTheme.set(cancelBounds, x + PAD, buttonY, half, MenuTheme.BUTTON_HEIGHT);
            MenuTheme.set(confirmBounds, x + PAD + half + 6f, buttonY, half, MenuTheme.BUTTON_HEIGHT);
            boolean valid = valid();
            cancelHover = MenuTheme.approach(cancelHover, MenuTheme.contains(cancelBounds, mouseX, mouseY) ? 1f : 0f, frameDelta * 8f);
            confirmHover = MenuTheme.approach(confirmHover, valid && MenuTheme.contains(confirmBounds, mouseX, mouseY) ? 1f : 0f, frameDelta * 8f);
            MenuTheme.button(cancelBounds, "Cancel", null, MenuTheme.Kind.PLAIN, cancelHover, true, shown);
            MenuTheme.button(confirmBounds, deleting ? "Delete" : "Rename", null, deleting ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PRIMARY,
                    deleting ? Math.max(confirmHover, 0.6f) : confirmHover, valid, shown);
        }

        void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (closing >= 0L || mouseButton != 0) return;
            if (MenuTheme.contains(cancelBounds, mouseX, mouseY)) {
                MenuTheme.playPress();
                closing = System.currentTimeMillis();
            } else if (MenuTheme.contains(confirmBounds, mouseX, mouseY) && valid()) {
                MenuTheme.playPress();
                confirm();
            } else if (nameField != null && MenuTheme.contains(nameBox, mouseX, mouseY)) {
                nameField.mouseClicked(mouseX, mouseY, 0);
                nameField.setFocused(true);
            }
        }

        void keyTyped(char typedChar, int keyCode) {
            if (closing >= 0L) return;
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closing = System.currentTimeMillis();
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                if (valid()) confirm();
            } else if (nameField != null) {
                nameField.textboxKeyTyped(typedChar, keyCode);
                problem = null;
            }
        }

        private void confirm() {
            if (deleting) {
                delete(replay);
            } else if (!rename(replay, nameField.getText())) {
                problem = "That name is taken or can't be used";
                return;
            }
            closing = System.currentTimeMillis();
        }
    }
}
