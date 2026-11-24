package rip.tempest.interfaces.menu;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.click.exhibition.ExhibitionClickInterface;
import rip.tempest.interfaces.menu.alt.AltManagerGui;
import rip.tempest.interfaces.menu.main.CustomMainMenu;
import rip.tempest.interfaces.menu.replay.GuiReplays;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.RoundedEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;

import java.awt.Color;

public final class MenuDock {
    public enum Page {
        HOME("Home", MenuIcons.HOME, 0),
        SINGLEPLAYER("Singleplayer", MenuIcons.PERSON, 0),
        MULTIPLAYER("Multiplayer", MenuIcons.GLOBE, 0),
        REPLAYS("Replays", MenuIcons.FILM, 0),
        ACCOUNTS("Accounts", MenuIcons.PEOPLE, 0),
        MODULES("Modules", MenuIcons.GRID, 1),
        SETTINGS("Settings", MenuIcons.SLIDERS, 1),
        QUIT("Quit", MenuIcons.POWER, 2);

        final String label;
        final MenuIcons.Icon icon;

        final int group;

        Page(String label, MenuIcons.Icon icon, int group) {
            this.label = label;
            this.icon = icon;
            this.group = group;
        }
    }

    private static final float ITEM_WIDTH = 50f, ITEM_HEIGHT = 40f, GAP = 3f, DIVIDER = 13f;
    private static final float PAD = 5f, BOTTOM = 14f;
    public static final float HEIGHT = ITEM_HEIGHT + PAD * 2f;

    private static final Page[] PAGES = Page.values();
    private static final float[] hover = new float[PAGES.length];
    private static final float[][] bounds = new float[PAGES.length][4];
    private static float indicatorX = Float.NaN;
    private static boolean shown;
    private static long firstShown;

    private MenuDock() {
    }

    public static float top(int height) {
        if (MenuTheme.astolfo()) return height - ASTOLFO_BOTTOM - ASTOLFO_HEIGHT;
        return height - BOTTOM - HEIGHT;
    }

    private static float width() {
        float w = PAD * 2f + PAGES.length * ITEM_WIDTH + (PAGES.length - 1) * GAP;
        for (int i = 1; i < PAGES.length; i++) if (PAGES[i].group != PAGES[i - 1].group) w += DIVIDER - GAP;
        return w;
    }

    public static void draw(int screenWidth, int screenHeight, int mouseX, int mouseY, Page active, float delta) {
        if (MenuTheme.astolfo()) {
            drawAstolfo(screenWidth, screenHeight, mouseX, mouseY, active, delta);
            return;
        }
        if (!shown) {
            shown = true;
            firstShown = System.currentTimeMillis();
        }

        float rise = MenuTheme.easeOut((System.currentTimeMillis() - firstShown) / 520f);
        float w = width();
        float x = (screenWidth - w) / 2f;
        float y = top(screenHeight) + (1f - rise) * 24f;

        Color accent = MenuTheme.accent();
        RoundedEffects.drawShadow(x, y, w, HEIGHT, 12f, 18f, 0f, 0f, 4f, new Color(0, 0, 0, Math.round(110 * rise)));
        RenderUtils.drawRoundedRect(x, y, w, HEIGHT, 12f, new Color(16, 16, 23, Math.round(232 * rise)));
        RenderUtils.drawRoundOutline(x, y, w, HEIGHT, 12f, 1f, MenuTheme.CLEAR, MenuTheme.white(0.09f * rise));

        float itemX = x + PAD;
        int activeIndex = -1;
        for (int i = 0; i < PAGES.length; i++) {
            if (i > 0 && PAGES[i].group != PAGES[i - 1].group) {
                float dividerX = itemX - GAP + DIVIDER / 2f;
                Glyphs.bar(dividerX - 0.25f, y + PAD + 9f, 0.5f, ITEM_HEIGHT - 18f, MenuTheme.white(0.12f * rise));
                itemX += DIVIDER - GAP;
            }
            MenuTheme.set(bounds[i], itemX, y + PAD, ITEM_WIDTH, ITEM_HEIGHT);
            if (PAGES[i] == active) activeIndex = i;
            itemX += ITEM_WIDTH + GAP;
        }

        if (activeIndex >= 0) {
            float target = bounds[activeIndex][0];
            indicatorX = Float.isNaN(indicatorX) ? target : MenuTheme.smooth(indicatorX, target, delta, 14f);
            float iy = y + PAD;
            RenderUtils.drawRoundedRect(indicatorX, iy, ITEM_WIDTH, ITEM_HEIGHT, 8f, MenuTheme.alpha(accent, 0.15f * rise));
            Glyphs.gradient(indicatorX + 14f, iy + ITEM_HEIGHT - 2f, ITEM_WIDTH - 28f, 1.5f,
                    MenuTheme.alpha(accent, rise), MenuTheme.alpha(MenuTheme.accentSecond(), rise));
        }

        CustomFontRenderer font = MenuTheme.small();
        for (int i = 0; i < PAGES.length; i++) {
            float[] b = bounds[i];
            boolean over = MenuTheme.contains(b, mouseX, mouseY);
            hover[i] = MenuTheme.approach(hover[i], over ? 1f : 0f, delta * 9f);
            float lit = MenuTheme.easeOut(hover[i]);
            boolean isActive = i == activeIndex;

            if (!isActive && lit > 0.01f) {
                RenderUtils.drawRoundedRect(b[0], b[1], b[2], b[3], 8f, MenuTheme.white(0.05f * lit * rise));
            }

            float lift = lit * 1.5f;
            Color tone = isActive ? Color.WHITE
                    : PAGES[i] == Page.QUIT && lit > 0f ? MenuTheme.mix(new Color(MenuTheme.TEXT_SECONDARY), MenuTheme.DANGER, lit)
                    : MenuTheme.mix(new Color(MenuTheme.TEXT_SECONDARY), Color.WHITE, lit);
            Color iconColor = isActive ? MenuTheme.mix(accent, Color.WHITE, 0.35f) : tone;
            PAGES[i].icon.draw(b[0] + b[2] / 2f, b[1] + 15f - lift, 6.5f, MenuTheme.alpha(iconColor, rise));
            font.drawCenteredString(PAGES[i].label, b[0] + b[2] / 2f, b[1] + 27f - lift, MenuTheme.text(tone, rise * (isActive ? 1f : 0.85f + 0.15f * lit)));
        }
    }

    private static final float ASTOLFO_HEIGHT = 19f, ASTOLFO_BOTTOM = 8f, ASTOLFO_GAP = 4f;

    private static void drawAstolfo(int screenWidth, int screenHeight, int mouseX, int mouseY, Page active, float delta) {
        if (active == Page.HOME) {
            for (float[] b : bounds) MenuTheme.set(b, 0, 0, 0, 0);
            return;
        }
        int count = PAGES.length;
        float w = Math.min(72f, (screenWidth - 20f - ASTOLFO_GAP * (count - 1)) / count);
        float total = count * w + (count - 1) * ASTOLFO_GAP;
        float x = (screenWidth - total) / 2f, y = screenHeight - ASTOLFO_BOTTOM - ASTOLFO_HEIGHT;
        for (int i = 0; i < count; i++) {
            float[] b = bounds[i];
            MenuTheme.set(b, x + i * (w + ASTOLFO_GAP), y, w, ASTOLFO_HEIGHT);
            hover[i] = MenuTheme.approach(hover[i], MenuTheme.contains(b, mouseX, mouseY) ? 1f : 0f, delta * 9f);
            MenuTheme.Kind kind = PAGES[i] == active ? MenuTheme.Kind.PRIMARY : PAGES[i] == Page.QUIT ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PLAIN;
            MenuTheme.button(b, astolfoLabel(PAGES[i]), null, kind, PAGES[i] == active ? 1f : hover[i], true, 1f);
        }
    }

    private static String astolfoLabel(Page page) {
        return switch (page) {
            case HOME -> "Main Menu";
            case ACCOUNTS -> "Alt Manager";
            case SETTINGS -> "Options";
            default -> page.label;
        };
    }

    public static boolean mouseClicked(GuiScreen current, Page active, int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        for (int i = 0; i < PAGES.length; i++) {
            if (!MenuTheme.contains(bounds[i], mouseX, mouseY)) continue;
            if (PAGES[i] != active) {
                MenuTheme.playPress();
                open(current, PAGES[i]);
            }
            return true;
        }
        return false;
    }

    public static void open(GuiScreen current, Page page) {
        Minecraft mc = Minecraft.getMinecraft();
        switch (page) {
            case HOME -> mc.displayGuiScreen(new CustomMainMenu());
            case SINGLEPLAYER -> mc.displayGuiScreen(new GuiSelectWorld(new CustomMainMenu()));
            case MULTIPLAYER -> mc.displayGuiScreen(new GuiMultiplayer(new CustomMainMenu()));
            case REPLAYS -> mc.displayGuiScreen(new GuiReplays());
            case ACCOUNTS -> mc.displayGuiScreen(new AltManagerGui());
            case MODULES -> openClickGui(mc);
            case SETTINGS -> mc.displayGuiScreen(new GuiOptions(current, mc.gameSettings));
            case QUIT -> mc.shutdown();
        }
    }

    private static void openClickGui(Minecraft mc) {
        switch (ClientSettingsModule.clickInterface.getValue()) {
            case Tempest -> mc.displayGuiScreen(new rip.tempest.interfaces.click.tempest.TempestClickInterface());
            case Exhibition -> mc.displayGuiScreen(new ExhibitionClickInterface());
            case Bankroll -> mc.displayGuiScreen(new rip.tempest.interfaces.click.bankroll.BankrollClickInterface());
            case Astolfo -> mc.displayGuiScreen(new rip.tempest.interfaces.click.astolfo.AstolfoClickInterface());
            case Storm -> mc.displayGuiScreen(new rip.tempest.interfaces.click.storm.StormClickInterface());
            case Tiles -> mc.displayGuiScreen(new rip.tempest.interfaces.click.flat.TilesClickInterface());
            case Columns -> mc.displayGuiScreen(new rip.tempest.interfaces.click.flat.ColumnsClickInterface());
        }
    }
}
