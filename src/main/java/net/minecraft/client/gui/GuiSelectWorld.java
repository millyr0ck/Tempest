package net.minecraft.client.gui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuDock;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuPage;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.AnvilConverterException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveFormatComparator;
import net.minecraft.world.storage.WorldInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The Singleplayer page, in the menus' style: a header with a button to create a world, then a
 * row per world with its name, folder, when it was last played and tags for its game mode.
 * Hovering a row shows play, rename, re-create and delete; double-clicking it (or its tile)
 * plays it. Rename and delete are small dialogs on this screen; creating a world opens the game's
 * own Create World screen.
 */
public class GuiSelectWorld extends MenuPage implements GuiYesNoCallback {

    private static final Logger logger = LogManager.getLogger();
    private static final float ROW_HEIGHT = 40f, ROW_GAP = 5f, TILE = 28f, ACTION = 20f, ACTION_GAP = 4f;
    private static final int PLAY = 0, RENAME = 1, RECREATE = 2, DELETE = 3, ACTIONS = 4;
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    protected GuiScreen parentScreen;
    protected String screenTitle = "Singleplayer";

    private final List<World> worlds = new ArrayList<>();
    private World selected;
    private boolean loading;

    private float listTop, listBottom, scroll, scrollTarget, maxScroll;
    private final float[] createButton = new float[4];
    private float createHover;
    private String tooltip;
    private Dialog dialog;

    /** A world on the list, with what's drawn for it. */
    private static final class World {
        final SaveFormatComparator save;
        float y = Float.NaN, drawnY, hover, select;
        final float[] actionHover = new float[ACTIONS];
        long lastClick;
        /** The folder and last-played line, remade every few seconds rather than every frame. */
        String details;
        long detailsMade;

        World(SaveFormatComparator save) {
            this.save = save;
        }
    }

    public GuiSelectWorld(GuiScreen parentScreenIn) {
        this.parentScreen = parentScreenIn;
    }

    @Override
    protected MenuDock.Page page() {
        return MenuDock.Page.SINGLEPLAYER;
    }

    @Override
    protected boolean modal() {
        return dialog != null;
    }

    @Override
    public void initGui() {
        super.initGui();
        try {
            loadLevelList();
        } catch (AnvilConverterException exception) {
            logger.error("Couldn't load level list", exception);
            this.mc.displayGuiScreen(new GuiErrorScreen("Unable to load worlds", exception.getMessage()));
        }
    }

    @Override
    public void updateScreen() {
        if (dialog != null) dialog.tick();
    }

    private void loadLevelList() throws AnvilConverterException {
        ISaveFormat format = this.mc.getSaveLoader();
        List<SaveFormatComparator> saves = format.getSaveList();
        Collections.sort(saves);
        worlds.clear();
        for (SaveFormatComparator save : saves) worlds.add(new World(save));
        selected = null;
    }

    private String displayName(World world) {
        String name = world.save.getDisplayName();
        return StringUtils.isEmpty(name) ? I18n.format("selectWorld.world") + " " + (worlds.indexOf(world) + 1) : name;
    }

    /** Loads a world by its place in the list, as vanilla's screen did. */
    public void func_146615_e(int index) {
        if (index >= 0 && index < worlds.size()) play(worlds.get(index));
    }

    private void play(World world) {
        if (loading) return;
        loading = true;
        String folder = world.save.getFileName(), name = displayName(world);
        this.mc.displayGuiScreen(null);
        if (this.mc.getSaveLoader().canLoadWorld(folder)) this.mc.launchIntegratedServer(folder, name, null);
    }

    private void recreate(World world) {
        GuiCreateWorld create = new GuiCreateWorld(this);
        ISaveHandler handler = this.mc.getSaveLoader().getSaveLoader(world.save.getFileName(), false);
        WorldInfo info = handler.loadWorldInfo();
        handler.flush();
        if (info != null) create.recreateFromExistingWorld(info);
        this.mc.displayGuiScreen(create);
    }

    private void delete(World world) {
        ISaveFormat format = this.mc.getSaveLoader();
        format.flushCache();
        format.deleteWorldDirectory(world.save.getFileName());
        try {
            loadLevelList();
        } catch (AnvilConverterException exception) {
            logger.error("Couldn't load level list", exception);
        }
    }

    private void rename(World world, String name) {
        ISaveFormat format = this.mc.getSaveLoader();
        format.renameWorld(world.save.getFileName(), name.trim());
        String folder = world.save.getFileName();
        try {
            loadLevelList();
        } catch (AnvilConverterException exception) {
            logger.error("Couldn't load level list", exception);
        }
        for (World w : worlds) if (w.save.getFileName().equals(folder)) selected = w;
    }

    /** Vanilla's delete confirmation, still used by the old main menu's demo world. */
    public void confirmClicked(boolean result, int id) {
        if (result && id >= 0 && id < worlds.size()) delete(worlds.get(id));
        this.mc.displayGuiScreen(this);
    }

    public static GuiYesNo makeDeleteWorldYesNo(GuiYesNoCallback selectWorld, String name, int id) {
        String question = I18n.format("selectWorld.deleteQuestion");
        String warning = "'" + name + "' " + I18n.format("selectWorld.deleteWarning");
        return new GuiYesNo(selectWorld, question, warning, I18n.format("selectWorld.deleteButton"), I18n.format("gui.cancel"), id);
    }

    // ── Drawing ──────────────────────────────────────────────────────────────────

    @Override
    protected void drawPage(int pointerX, int pointerY) {
        tooltip = null;
        String summary = worlds.size() + (worlds.size() == 1 ? " world" : " worlds");
        float under = MenuTheme.pageHeader(screenTitle, summary, columnX, 22f, intro);

        CustomFontRenderer font = MenuTheme.body();
        float createWidth = font.getStringWidth("Create world") + 30f;
        MenuTheme.set(createButton, columnX + columnWidth - createWidth, 26f - (1f - intro) * 6f, createWidth, MenuTheme.BUTTON_HEIGHT);
        createHover = MenuTheme.approach(createHover, MenuTheme.contains(createButton, pointerX, pointerY) ? 1f : 0f, delta * 8f);
        MenuTheme.button(createButton, "Create world", MenuIcons.PLUS, MenuTheme.Kind.PRIMARY, createHover, true, intro);

        listTop = under + 12f;
        listBottom = contentBottom;
        maxScroll = Math.max(0f, worlds.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP - (listBottom - listTop));
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll = MenuTheme.smooth(scroll, scrollTarget, delta, 16f);

        if (worlds.isEmpty()) {
            float middle = (listTop + listBottom) / 2f - 10f, cx = columnX + columnWidth / 2f;
            MenuIcons.CUBE.draw(cx, middle - 16f, 10f, MenuTheme.white(0.25f * intro));
            MenuTheme.bold().drawCenteredString("Create your first world", cx, middle + 2f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
            MenuTheme.body().drawCenteredString("Your worlds will show up here.", cx, middle + 16f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
            return;
        }

        boolean pointerInList = pointerY >= listTop && pointerY < listBottom;
        long now = System.currentTimeMillis();
        RenderUtils.startScissor(0f, listTop, width, listBottom - listTop);
        for (int i = 0; i < worlds.size(); i++) {
            World world = worlds.get(i);
            float slot = i * (ROW_HEIGHT + ROW_GAP);
            world.y = Float.isNaN(world.y) ? slot : MenuTheme.smooth(world.y, slot, delta, 18f);
            float appear = MenuTheme.easeOut((now - openTime - 40L - Math.min(i, 10) * 35L) / 360f);
            world.drawnY = listTop - scroll + world.y + (1f - appear) * 10f;

            boolean hovered = pointerInList && MenuTheme.contains(columnX, world.drawnY, columnWidth, ROW_HEIGHT, pointerX, pointerY);
            world.hover = MenuTheme.approach(world.hover, hovered ? 1f : 0f, delta * 9f);
            world.select = MenuTheme.approach(world.select, world == selected ? 1f : 0f, delta * 8f);
            if (world.drawnY + ROW_HEIGHT < listTop - 14f || world.drawnY > listBottom + 14f) continue;
            drawRow(world, i, pointerX, pointerY, appear * intro);
        }
        RenderUtils.endScissor();

        MenuTheme.scrollbar(columnX + columnWidth + 8f, listTop, listBottom, scroll, maxScroll, false, intro);
    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (tooltip != null && dialog == null) MenuTheme.tooltip(tooltip, mouseX, mouseY, width, height);
        if (dialog != null) dialog.draw(mouseX, mouseY, System.currentTimeMillis());
    }

    private void drawRow(World world, int index, int pointerX, int pointerY, float alpha) {
        if (alpha < 0.02f) return;
        float x = columnX, y = world.drawnY, w = columnWidth;
        float hover = MenuTheme.easeOut(world.hover), select = MenuTheme.easeOut(world.select);
        MenuTheme.surface(x, y, w, ROW_HEIGHT, 8f, hover * (1f - select), alpha * (1f - select));
        MenuTheme.selectedSurface(x, y, w, ROW_HEIGHT, 8f, select, alpha);

        // A tile tinted by the world's game mode, holding a cube.
        float tileX = x + 6f, tileY = y + (ROW_HEIGHT - TILE) / 2f;
        Color tone = modeColour(world.save);
        RenderUtils.drawRoundedRect(tileX, tileY, TILE, TILE, 6f, MenuTheme.alpha(tone, 0.2f * alpha));
        MenuIcons.CUBE.draw(tileX + TILE / 2f, tileY + TILE / 2f, 6.5f, MenuTheme.alpha(MenuTheme.mix(tone, Color.WHITE, 0.3f), alpha));
        boolean overTile = MenuTheme.contains(tileX, tileY, TILE, TILE, pointerX, pointerY);
        if (overTile) {
            RenderUtils.drawRoundedRect(tileX, tileY, TILE, TILE, 6f, new Color(0, 0, 0, Math.round(150 * alpha)));
            Glyphs.play(tileX + TILE / 2f, tileY + TILE / 2f, 6f, MenuTheme.white(alpha));
        }

        float attention = Math.max(hover, select);
        float actionsLeft = x + w - 8f - ACTIONS * ACTION - (ACTIONS - 1) * ACTION_GAP;
        float textX = tileX + TILE + 9f;
        int textWidth = (int) (actionsLeft - 10f - textX);
        FontRenderer font = this.fontRendererObj;

        font.drawString(font.trimStringToWidth(displayName(world), textWidth), textX, y + 8f, MenuTheme.text(MenuTheme.TEXT, alpha), false);
        long now = System.currentTimeMillis();
        if (world.details == null || now - world.detailsMade > 5000L) {
            world.details = world.save.getFileName() + "  ·  " + lastPlayed(world.save.getLastTimePlayed());
            world.detailsMade = now;
        }
        font.drawString(font.trimStringToWidth(world.details, textWidth), textX, y + 21f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha), false);

        // Tags, from the right edge of the text area, faded out while the buttons show.
        float tagAlpha = alpha * (1f - attention);
        if (tagAlpha > 0.02f) {
            List<String> tags = new ArrayList<>();
            List<Color> tones = new ArrayList<>();
            if (world.save.requiresConversion()) {
                tags.add("Needs converting");
                tones.add(MenuTheme.WARNING);
            } else {
                tags.add(modeName(world.save.getEnumGameType()));
                tones.add(tone);
                if (world.save.isHardcoreModeEnabled()) {
                    tags.add("Hardcore");
                    tones.add(MenuTheme.DANGER);
                }
                if (world.save.getCheatsEnabled()) {
                    tags.add("Cheats");
                    tones.add(MenuTheme.WARNING);
                }
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
            world.actionHover[i] = MenuTheme.approach(world.actionHover[i], over ? 1f : 0f, delta * 10f);
            if (attention * alpha < 0.02f) continue;
            MenuIcons.Icon icon = i == PLAY ? MenuIcons.PLAY : i == RENAME ? MenuIcons.PENCIL : i == RECREATE ? MenuIcons.REFRESH : MenuIcons.TRASH;
            MenuTheme.iconButton(b, icon, i == DELETE ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PLAIN, world.actionHover[i], attention * alpha);
            if (over) tooltip = i == PLAY ? "Play" : i == RENAME ? "Rename" : i == RECREATE ? "Re-create" : "Delete";
        }
    }

    private static float[] actionBounds(float left, float top, int index) {
        return new float[]{left + index * (ACTION + ACTION_GAP), top, ACTION, ACTION};
    }

    private static Color modeColour(SaveFormatComparator save) {
        if (save.isHardcoreModeEnabled()) return MenuTheme.DANGER;
        return switch (save.getEnumGameType()) {
            case CREATIVE -> new Color(110, 190, 255);
            case ADVENTURE -> new Color(240, 170, 90);
            case SPECTATOR -> new Color(170, 170, 185);
            default -> MenuTheme.SUCCESS;
        };
    }

    private static String modeName(WorldSettings.GameType type) {
        return switch (type) {
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
            default -> "Survival";
        };
    }

    /** "Just now", "5 minutes ago", "Yesterday", then the date. */
    private static String lastPlayed(long time) {
        long minutes = (System.currentTimeMillis() - time) / 60000L;
        if (minutes < 1) return "Played just now";
        if (minutes < 60) return "Played " + minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return "Played " + hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days == 1) return "Played yesterday";
        if (days < 7) return "Played " + days + " days ago";
        return "Played " + new SimpleDateFormat("d MMM yyyy").format(new Date(time));
    }

    // ── Input ────────────────────────────────────────────────────────────────────

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

        if (MenuTheme.contains(createButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            this.mc.displayGuiScreen(new GuiCreateWorld(this));
            return;
        }
        if (mouseY < listTop || mouseY >= listBottom) return;

        for (World world : worlds) {
            if (!MenuTheme.contains(columnX, world.drawnY, columnWidth, ROW_HEIGHT, mouseX, mouseY)) continue;
            selected = world;

            float actionsLeft = columnX + columnWidth - 8f - ACTIONS * ACTION - (ACTIONS - 1) * ACTION_GAP;
            float actionsTop = world.drawnY + (ROW_HEIGHT - ACTION) / 2f;
            for (int i = 0; i < ACTIONS; i++) {
                if (!MenuTheme.contains(actionBounds(actionsLeft, actionsTop, i), mouseX, mouseY)) continue;
                MenuTheme.playPress();
                act(world, i);
                return;
            }

            long now = Minecraft.getSystemTime();
            float tileX = columnX + 6f, tileY = world.drawnY + (ROW_HEIGHT - TILE) / 2f;
            if (MenuTheme.contains(tileX, tileY, TILE, TILE, mouseX, mouseY) || now - world.lastClick < DOUBLE_CLICK_MILLIS) {
                MenuTheme.playPress();
                play(world);
            }
            world.lastClick = now;
            return;
        }
    }

    private void act(World world, int action) {
        switch (action) {
            case PLAY -> play(world);
            case RENAME -> dialog = new Dialog(world, false);
            case RECREATE -> recreate(world);
            case DELETE -> dialog = new Dialog(world, true);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (dialog != null) {
            dialog.keyTyped(typedChar, keyCode);
            return;
        }
        if ((keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) && !worlds.isEmpty()) {
            int index = selected == null ? -1 : worlds.indexOf(selected);
            int direction = keyCode == Keyboard.KEY_UP ? -1 : 1;
            int next = index < 0 ? (direction > 0 ? 0 : worlds.size() - 1) : Math.max(0, Math.min(worlds.size() - 1, index + direction));
            selected = worlds.get(next);
            float top = next * (ROW_HEIGHT + ROW_GAP), view = listBottom - listTop;
            if (top < scrollTarget) scrollTarget = top;
            else if (top + ROW_HEIGHT > scrollTarget + view) scrollTarget = top + ROW_HEIGHT - view;
        } else if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && selected != null) {
            play(selected);
        } else if (keyCode == Keyboard.KEY_DELETE && selected != null) {
            dialog = new Dialog(selected, true);
        } else if (keyCode == Keyboard.KEY_F2 && selected != null) {
            dialog = new Dialog(selected, false);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    /** Rename (a name field) or delete (a confirmation), over the dimmed screen. */
    private final class Dialog {
        private static final float WIDTH = 260f, PAD = 16f;

        private final World world;
        private final boolean deleting;
        private final GuiTextField nameField;
        private final long opened = System.currentTimeMillis();
        private long closing = -1L, lastFrame = opened;
        private final float[] nameBox = new float[4], cancelBounds = new float[4], confirmBounds = new float[4];
        private float cancelHover, confirmHover;

        Dialog(World world, boolean deleting) {
            this.world = world;
            this.deleting = deleting;
            if (deleting) {
                nameField = null;
            } else {
                nameField = new GuiTextField(0, fontRendererObj, 0, 0, (int) (WIDTH - PAD * 2f - 12f), 9);
                nameField.setEnableBackgroundDrawing(false);
                nameField.setMaxStringLength(32);
                nameField.setText(displayName(world));
                nameField.setFocused(true);
            }
        }

        void tick() {
            if (nameField != null) nameField.updateCursorCounter();
        }

        private boolean valid() {
            return deleting || !nameField.getText().trim().isEmpty();
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

            float panelHeight = PAD + 20f + (deleting ? 40f : 40f) + MenuTheme.BUTTON_HEIGHT + PAD;
            float x = (width - WIDTH) / 2f, y = (height - panelHeight) / 2f + (1f - shown) * 8f, inner = WIDTH - PAD * 2f;
            MenuTheme.dialogPanel(width, height, x, y, WIDTH, panelHeight, shown);
            MenuTheme.bold().drawString(deleting ? "Delete world" : "Rename world", x + PAD, y + PAD, MenuTheme.text(MenuTheme.TEXT, shown));
            float top = y + PAD + 20f;

            FontRenderer font = fontRendererObj;
            if (deleting) {
                font.drawString(font.trimStringToWidth("'" + displayName(world) + "' will be gone forever.", (int) inner), x + PAD, top + 2f,
                        MenuTheme.text(MenuTheme.TEXT_SECONDARY, shown), false);
                font.drawString("This can't be undone.", x + PAD, top + 14f, MenuTheme.text(MenuTheme.TEXT_MUTED, shown), false);
            } else {
                MenuTheme.small().drawString("World name", x + PAD, top, MenuTheme.text(MenuTheme.TEXT_MUTED, shown));
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
            }
        }

        private void confirm() {
            if (deleting) delete(world);
            else rename(world, nameField.getText());
            closing = System.currentTimeMillis();
        }
    }
}
