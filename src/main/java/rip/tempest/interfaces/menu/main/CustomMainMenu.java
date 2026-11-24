package rip.tempest.interfaces.menu.main;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuDock;
import rip.tempest.interfaces.menu.MenuPage;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.utils.render.ClientLogo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CustomMainMenu extends MenuPage {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK);
    private static final float EDGE = 40f, TOP = 44f, LOGO = 34f;

    private static boolean introPlayed;
    private final boolean longIntro;

    public CustomMainMenu() {
        longIntro = !introPlayed;
        introPlayed = true;
    }

    @Override
    protected MenuDock.Page page() {
        return MenuDock.Page.HOME;
    }

    private static final String[] ASTOLFO_BUTTONS = {"SINGLEPLAYER", "MULTIPLAYER", "ALT MANAGER", "REPLAYS"};
    private static final MenuDock.Page[] ASTOLFO_PAGES = {MenuDock.Page.SINGLEPLAYER, MenuDock.Page.MULTIPLAYER, MenuDock.Page.ACCOUNTS, MenuDock.Page.REPLAYS};

    private static final String[] ASTOLFO_LINKS = {"", "Options", "Language", "Modules"};

    private float astolfoButtonsX() {
        return width / 2f - 65f;
    }

    private float astolfoButtonsY() {
        return height / 2f - 90f + 110f;
    }

    private void drawAstolfo(int mouseX, int mouseY) {
        rip.tempest.api.font.CustomFontRenderer light = rip.tempest.interfaces.menu.AstolfoMenu.light20();
        rip.tempest.api.font.CustomFontRenderer regular = rip.tempest.interfaces.menu.AstolfoMenu.regular18();

        regular.drawString(Tempest.NAME + " Client (" + Tempest.VERSION + ")", 8f, height - 12f, new java.awt.Color(255, 255, 255, 150).getRGB());

        float right = width - 10f, pad = 5f, iconHalf = 8f;
        for (int i = 0; i < ASTOLFO_LINKS.length; i++) {
            String label = ASTOLFO_LINKS[i];
            float w = light.getStringWidth(label) + pad * 2f + iconHalf * 2f;
            boolean hover = mouseX > right - w && mouseX < right && mouseY > 7.5f && mouseY < 35f;
            rip.tempest.interfaces.menu.AstolfoMenu.centred(light, label, right - w / 2f + iconHalf, 20f, hover ? -1 : 0xA9FFFFFF);
            float iconX = right - w - pad + iconHalf;
            if (i == 3) rip.tempest.interfaces.menu.MenuIcons.GRID.draw(iconX + 8f, 20f, 6f, hover ? java.awt.Color.WHITE : new java.awt.Color(255, 255, 255, 169));
            else rip.tempest.interfaces.menu.AstolfoMenu.icon(i == 0 ? rip.tempest.interfaces.menu.AstolfoMenu.CLOSE
                    : i == 1 ? rip.tempest.interfaces.menu.AstolfoMenu.OPTIONS : rip.tempest.interfaces.menu.AstolfoMenu.LANGUAGE, iconX, 12f, 16f);
            right -= w + 10f;
        }

        int logo = 76;
        rip.tempest.utils.render.ClientLogo.draw(width / 2f - 38f, height / 2f - height / 3f, logo, logo - 1);

        float bx = astolfoButtonsX(), by = astolfoButtonsY();
        for (int i = 0; i < ASTOLFO_BUTTONS.length; i++) {
            boolean hover = mouseX >= bx && mouseX <= bx + 130f && mouseY >= by && mouseY <= by + 19f;
            rip.tempest.interfaces.menu.AstolfoMenu.menuButton(ASTOLFO_BUTTONS[i], bx, by, 130f, 19f, hover);
            by += 21f;
        }

        String welcome = "Welcome, " + mc.getSession().getUsername();
        regular.drawString(welcome, width - regular.getStringWidth(welcome) - 8f, height - 12f, -1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        if (!MenuTheme.astolfo() || mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        rip.tempest.api.font.CustomFontRenderer light = rip.tempest.interfaces.menu.AstolfoMenu.light20();
        float right = width - 10f;
        for (int i = 0; i < ASTOLFO_LINKS.length; i++) {
            float w = light.getStringWidth(ASTOLFO_LINKS[i]) + 5f * 2f + 8f * 2f;
            if (mouseX > right - w && mouseX < right && mouseY > 7.5f && mouseY < 35f) {
                MenuTheme.playPress();
                switch (i) {
                    case 0 -> mc.shutdown();
                    case 1 -> mc.displayGuiScreen(new net.minecraft.client.gui.GuiOptions(this, mc.gameSettings));
                    case 2 -> mc.displayGuiScreen(new net.minecraft.client.gui.GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
                    default -> MenuDock.open(this, MenuDock.Page.MODULES);
                }
                return;
            }
            right -= w + 10f;
        }
        float bx = astolfoButtonsX(), by = astolfoButtonsY();
        for (int i = 0; i < ASTOLFO_BUTTONS.length; i++) {
            if (mouseX >= bx && mouseX <= bx + 130f && mouseY >= by && mouseY <= by + 19f) {
                MenuTheme.playPress();
                MenuDock.open(this, ASTOLFO_PAGES[i]);
                return;
            }
            by += 21f;
        }
    }

    @Override
    protected void drawPage(int pointerX, int pointerY) {
        if (MenuTheme.astolfo()) {
            drawAstolfo(pointerX, pointerY);
            return;
        }
        long elapsed = System.currentTimeMillis() - openTime;
        float stretch = longIntro ? 1f : 0.45f;
        float mark = MenuTheme.easeOut(elapsed / (700f * stretch));
        float greet = MenuTheme.easeOut((elapsed - 140f * stretch) / (600f * stretch));
        float clock = MenuTheme.easeOut((elapsed - 220f * stretch) / (600f * stretch));

        CustomFontRenderer display = MenuTheme.display(), large = MenuTheme.large(), body = MenuTheme.body();

        float x = EDGE - (1f - mark) * 14f;
        float textHeight = display.getHeight();
        float logoY = TOP + (textHeight - LOGO) / 2f;
        ClientLogo.draw(x, logoY, LOGO, LOGO);
        display.drawString(Tempest.NAME, x + LOGO + 10f, TOP, MenuTheme.text(MenuTheme.TEXT, mark));

        float greetY = TOP + textHeight + 16f + (1f - greet) * 6f;
        String hello = greeting() + ", ";
        String name = mc.getSession().getUsername();
        large.drawString(hello, EDGE, greetY, MenuTheme.text(MenuTheme.TEXT_SECONDARY, greet));
        large.drawString(name, EDGE + large.getStringWidth(hello), greetY, MenuTheme.text(MenuTheme.accent(), greet));
        body.drawString("Minecraft 1.8.9  ·  " + Tempest.NAME + " " + Tempest.VERSION, EDGE, greetY + large.getHeight() + 8f,
                MenuTheme.text(MenuTheme.TEXT_MUTED, greet));

        LocalDateTime now = LocalDateTime.now();
        CustomFontRenderer title = MenuTheme.title();
        String time = now.format(CLOCK), date = now.format(DATE);
        float right = width - EDGE;
        title.drawString(time, right - title.getStringWidth(time), TOP + 2f, MenuTheme.text(MenuTheme.TEXT, clock));
        body.drawString(date, right - body.getStringWidth(date), TOP + title.getHeight() + 9f, MenuTheme.text(MenuTheme.TEXT_MUTED, clock));
    }

    private static String greeting() {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 5 && hour < 12) return "Good morning";
        if (hour >= 12 && hour < 17) return "Good afternoon";
        if (hour >= 17 && hour < 22) return "Good evening";
        return "Good night";
    }
}
