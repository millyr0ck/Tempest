package rip.tempest.interfaces.menu.alt;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuDock;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuPage;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.interfaces.menu.alt.microsoft.AccountVault;
import rip.tempest.interfaces.menu.alt.microsoft.MicrosoftLogin;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class AltManagerGui extends MenuPage {
    private static final String CRACKED = "cracked|", MICROSOFT = "microsoftLogin|";
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", NUMBERS = "0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final float PANEL_WIDTH = 214f, PANEL_PAD = 12f, GAP = 12f;
    private static final float CARD_HEIGHT = 42f, CARD_GAP = 6f, HEAD = 26f, BIN = 18f;
    private static final int INFO = 0, GOOD = 1, BAD = 2;

    private enum Type {CRACKED, MICROSOFT, OLD}

    private static final class Account {
        final String line, name;
        final Type type;
        String uuid;
        float y = Float.NaN, hover, binHover;

        Account(String line, String name, Type type) {
            this.line = line;
            this.name = name;
            this.type = type;
        }
    }

    private final List<Account> accounts = new ArrayList<>();
    private GuiTextField nameField;

    private volatile String status = "";
    private volatile int statusKind = INFO;
    private volatile boolean busy, cancel;

    private volatile String busyName;

    private final float[] microsoftButton = new float[4], addButton = new float[4], randomButton = new float[4], fieldBox = new float[4];
    private float microsoftHover, addHover, randomHover;
    private float listX, listWidth, listTop, listBottom, scroll, scrollTarget, maxScroll;
    private int columns = 2;

    @Override
    protected MenuDock.Page page() {
        return MenuDock.Page.ACCOUNTS;
    }

    @Override
    protected float maxColumnWidth() {
        return 760f;
    }

    @Override
    public void initGui() {
        super.initGui();
        nameField = new GuiTextField(0, mc.fontRendererObj, 0, 0, (int) (PANEL_WIDTH - PANEL_PAD * 2f - 12f), 9);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setMaxStringLength(16);
        load();
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    private static File altsFile() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "Tempest");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "alts.txt");
    }

    private void load() {
        accounts.clear();
        File file = altsFile();
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                Account account = parse(line.trim());
                if (account != null) accounts.add(account);
            }
        } catch (IOException ignored) {
        }
        for (Account account : accounts) {
            if (account.type == Type.MICROSOFT) account.uuid = AccountVault.uuid(account.name);
        }
    }

    private static Account parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 2 || parts[1].isEmpty()) return null;
        return switch (parts[0]) {
            case "cracked" -> new Account(line, parts[1], Type.CRACKED);
            case "microsoftLogin" -> new Account(line, parts[1], Type.MICROSOFT);

            case "microsoftOAuth", "microsoft" -> new Account(line, parts[1], Type.OLD);
            default -> null;
        };
    }

    private void save() {
        try (PrintWriter out = new PrintWriter(altsFile())) {
            for (Account account : accounts) out.println(account.line);
        } catch (IOException ignored) {
        }
    }

    private void addAccount(String line) {
        for (Account account : accounts) if (account.line.equals(line)) return;
        Account account = parse(line);
        if (account == null) return;
        if (account.type == Type.MICROSOFT) account.uuid = AccountVault.uuid(account.name);
        accounts.add(account);
        save();
    }

    private void remove(Account account) {
        accounts.remove(account);
        if (account.type == Type.MICROSOFT) AccountVault.remove(account.name);
        save();
        setStatus("Removed " + account.name, INFO);
    }

    private void setStatus(String text, int kind) {
        status = text;
        statusKind = kind;
    }

    private void loginOffline(String name) {
        name = name.trim();
        if (name.isEmpty()) {
            setStatus("Type a name first", BAD);
            return;
        }
        String id = PlayerHeads.offlineId(name).toString().replace("-", "");
        mc.setSession(new Session(name, id, "0", "legacy"));
        addAccount(CRACKED + name);
        nameField.setText("");
        setStatus("Logged in as " + name + " (offline)", GOOD);
    }

    private void login(Account account) {
        switch (account.type) {
            case CRACKED -> loginOffline(account.name);
            case MICROSOFT -> {
                if (!busy) loginSavedMicrosoft(account.name);
            }
            case OLD -> setStatus("That's an old login. Sign in with Microsoft again.", BAD);
        }
    }

    private void signInWithMicrosoft() {
        busy = true;
        cancel = false;
        busyName = null;
        setStatus("Asking Microsoft for a code...", INFO);
        startLoginThread(() -> {
            MicrosoftLogin.DeviceCode code = MicrosoftLogin.requestCode();
            mc.addScheduledTask(() -> GuiScreen.setClipboardString(code.userCode));
            openBrowser(code.signInPage);
            setStatus("Click Allow access in your browser, then sign in", INFO);
            MicrosoftLogin.Account account = MicrosoftLogin.waitForSignIn(code, () -> cancel || mc.currentScreen != this);
            useMicrosoftAccount(account, null);
        });
    }

    private void loginSavedMicrosoft(String name) {
        busy = true;
        cancel = false;
        busyName = name;
        setStatus("Logging in as " + name + "...", INFO);
        startLoginThread(() -> {
            String refreshToken = AccountVault.load(name);
            if (refreshToken == null) throw new MicrosoftLogin.LoginException("No saved login for " + name + ". Sign in with Microsoft again.");
            useMicrosoftAccount(MicrosoftLogin.refresh(refreshToken), name);
        });
    }

    private interface LoginTask {
        void run() throws MicrosoftLogin.LoginException;
    }

    private void startLoginThread(LoginTask task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (MicrosoftLogin.LoginException e) {
                setStatus(e.getMessage(), BAD);
            } catch (RuntimeException e) {
                setStatus("Microsoft login failed (" + e.getClass().getSimpleName() + ")", BAD);
            } finally {
                busy = false;
                busyName = null;
            }
        }, "microsoft-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void useMicrosoftAccount(MicrosoftLogin.Account account, String oldName) {
        mc.addScheduledTask(() -> mc.setSession(new Session(account.name, account.uuid, account.accessToken, "mojang")));
        setStatus("Logged in as " + account.name + ", saving...", INFO);
        if (oldName != null && !oldName.equalsIgnoreCase(account.name)) AccountVault.remove(oldName);
        boolean saved = AccountVault.save(account.name, account.uuid, account.refreshToken);
        mc.addScheduledTask(() -> {
            if (oldName != null && !oldName.equalsIgnoreCase(account.name)) accounts.removeIf(a -> a.line.equals(MICROSOFT + oldName));
            if (saved) addAccount(MICROSOFT + account.name);
            else save();
        });
        setStatus("Logged in as " + account.name + (saved ? "" : " (couldn't save the login)"), saved ? GOOD : BAD);
    }

    private static void openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception | Error e) {
            try {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } catch (IOException ignored) {
            }
        }
    }

    private static String randomName() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 4; i++) name.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        for (int i = 0; i < 4; i++) name.append(NUMBERS.charAt(RANDOM.nextInt(NUMBERS.length())));
        return name.toString();
    }

    private boolean isCurrent(Account account) {
        return account.name.equalsIgnoreCase(mc.getSession().getUsername());
    }

    @Override
    protected void drawPage(int pointerX, int pointerY) {
        Session session = mc.getSession();
        boolean offline = "0".equals(session.getToken()) || session.getSessionType() == Session.Type.LEGACY;
        float top = MenuTheme.pageHeader("Accounts", "Signed in as " + session.getUsername() + (offline ? " (offline)" : " (Microsoft)"),
                columnX, 22f, intro) + 14f;

        float panelX = columnX, panelHeight = Math.min(contentBottom - top, 236f);
        drawPanel(panelX, top, panelHeight, pointerX, pointerY);

        listX = panelX + PANEL_WIDTH + GAP;
        listWidth = columnX + columnWidth - listX;
        listTop = top + 18f;
        listBottom = contentBottom;
        drawList(top, pointerX, pointerY);
    }

    private void drawPanel(float x, float y, float h, int pointerX, int pointerY) {
        float a = intro;
        MenuTheme.surface(x, y, PANEL_WIDTH, h, 10f, 0f, a);
        CustomFontRenderer small = MenuTheme.small(), body = MenuTheme.body();
        float inner = PANEL_WIDTH - PANEL_PAD * 2f, cx = x + PANEL_PAD, cy = y + PANEL_PAD;

        small.drawString("Microsoft account", cx, cy, MenuTheme.text(MenuTheme.TEXT_MUTED, a));
        cy += small.getHeight() + 7f;
        MenuTheme.set(microsoftButton, cx, cy, inner, 22f);
        microsoftHover = MenuTheme.approach(microsoftHover, MenuTheme.contains(microsoftButton, pointerX, pointerY) ? 1f : 0f, delta * 8f);
        boolean signingIn = busy && busyName == null;
        MenuTheme.button(microsoftButton, signingIn ? "Cancel" : "Sign in with Microsoft", signingIn ? MenuIcons.CROSS : MenuIcons.LOGIN,
                signingIn ? MenuTheme.Kind.PLAIN : MenuTheme.Kind.PRIMARY, microsoftHover, !busy || signingIn, a);
        cy += 22f + 9f;

        String message = status;
        if (!message.isEmpty()) {
            Color tone = statusKind == GOOD ? MenuTheme.SUCCESS : statusKind == BAD ? MenuTheme.DANGER : MenuTheme.accent();
            if (busy) {
                float spin = (System.currentTimeMillis() % 900L) / 900f * 360f;
                Glyphs.ring(cx + 3f, cy + 3.5f, 3f, 1.2f, spin, 260f, MenuTheme.alpha(tone, a));
            } else {
                Glyphs.disc(cx + 3f, cy + 3.5f, 2f, MenuTheme.alpha(tone, a));
            }
            List<String> lines = wrap(body, message, inner - 12f);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                body.drawString(lines.get(i), cx + 10f, cy + i * (body.getHeight() + 4f), MenuTheme.text(MenuTheme.TEXT_SECONDARY, a));
            }
        }
        cy += (body.getHeight() + 4f) * 2f + 6f;

        Glyphs.bar(cx, cy, inner, 0.5f, MenuTheme.white(0.09f * a));
        cy += 10f;

        small.drawString("Offline name", cx, cy, MenuTheme.text(MenuTheme.TEXT_MUTED, a));
        cy += small.getHeight() + 7f;
        MenuTheme.set(fieldBox, cx, cy, inner, 20f);
        MenuTheme.fieldFrame(cx, cy, inner, 20f, nameField.isFocused(), a);
        nameField.xPosition = Math.round(cx + 6f);
        nameField.yPosition = Math.round(cy + 6f);
        nameField.setTextColor(MenuTheme.text(0xE6E6EE, a));
        nameField.drawTextBox();
        if (nameField.getText().isEmpty() && !nameField.isFocused()) {
            mc.fontRendererObj.drawString("Username", cx + 6f, cy + 6f, MenuTheme.text(MenuTheme.TEXT_MUTED, a), false);
        }
        cy += 20f + 6f;

        float half = (inner - 6f) / 2f;
        MenuTheme.set(addButton, cx, cy, half, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(randomButton, cx + half + 6f, cy, half, MenuTheme.BUTTON_HEIGHT);
        addHover = MenuTheme.approach(addHover, MenuTheme.contains(addButton, pointerX, pointerY) ? 1f : 0f, delta * 8f);
        randomHover = MenuTheme.approach(randomHover, MenuTheme.contains(randomButton, pointerX, pointerY) ? 1f : 0f, delta * 8f);
        MenuTheme.button(addButton, "Add", MenuIcons.PLUS, MenuTheme.Kind.PLAIN, addHover, true, a);
        MenuTheme.button(randomButton, "Random", MenuIcons.DICE, MenuTheme.Kind.PLAIN, randomHover, true, a);
        cy += MenuTheme.BUTTON_HEIGHT + 10f;

        if (cy + small.getHeight() * 2f + 4f < y + h) {
            small.drawString("Saved Microsoft logins are encrypted", cx, cy, MenuTheme.text(MenuTheme.TEXT_MUTED, a * 0.8f));
            small.drawString("so only this Windows account can use them.", cx, cy + small.getHeight() + 4f, MenuTheme.text(MenuTheme.TEXT_MUTED, a * 0.8f));
        }
    }

    private void drawList(float top, int pointerX, int pointerY) {
        float a = intro;
        CustomFontRenderer bold = MenuTheme.bold(), small = MenuTheme.small();
        bold.drawString("Saved accounts", listX, top + 2f, MenuTheme.text(MenuTheme.TEXT, a));
        String count = Integer.toString(accounts.size());
        small.drawString(count, listX + bold.getStringWidth("Saved accounts") + 6f, top + 3.5f, MenuTheme.text(MenuTheme.TEXT_MUTED, a));

        if (accounts.isEmpty()) {
            float middle = (listTop + listBottom) / 2f - 14f;
            float cx = listX + listWidth / 2f;
            MenuIcons.PEOPLE.draw(cx, middle - 16f, 10f, MenuTheme.white(0.25f * a));
            bold.drawCenteredString("No saved accounts yet", cx, middle + 2f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, a));
            MenuTheme.body().drawCenteredString("Sign in with Microsoft or add an offline name.", cx, middle + 16f, MenuTheme.text(MenuTheme.TEXT_MUTED, a));
            return;
        }

        columns = listWidth >= 470f ? 3 : listWidth >= 300f ? 2 : 1;
        float cardWidth = (listWidth - 8f - (columns - 1) * CARD_GAP) / columns;
        int rows = (accounts.size() + columns - 1) / columns;
        maxScroll = Math.max(0f, rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP - (listBottom - listTop));
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll = MenuTheme.smooth(scroll, scrollTarget, delta, 16f);

        boolean pointerInList = pointerY >= listTop && pointerY <= listBottom;
        RenderUtils.startScissor(listX - 6f, listTop, listWidth + 12f, listBottom - listTop);
        long now = System.currentTimeMillis();
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            float slot = (i / columns) * (CARD_HEIGHT + CARD_GAP);
            account.y = Float.isNaN(account.y) ? slot : MenuTheme.smooth(account.y, slot, delta, 18f);
            float x = listX + (i % columns) * (cardWidth + CARD_GAP);
            float y = listTop + account.y - scroll;
            float appear = MenuTheme.easeOut((now - openTime - 60L - Math.min(i, 12) * 30L) / 320f);
            y += (1f - appear) * 8f;
            if (y + CARD_HEIGHT < listTop || y > listBottom) continue;
            drawCard(account, x, y, cardWidth, pointerInList, pointerX, pointerY, appear * a, now);
        }
        RenderUtils.endScissor();

        MenuTheme.scrollbar(listX + listWidth - 1f, listTop, listBottom, scroll, maxScroll, false, a);
    }

    private void drawCard(Account account, float x, float y, float w, boolean pointerInList, int pointerX, int pointerY, float a, long now) {
        boolean over = pointerInList && MenuTheme.contains(x, y, w, CARD_HEIGHT, pointerX, pointerY);
        account.hover = MenuTheme.approach(account.hover, over ? 1f : 0f, delta * 9f);
        float lit = MenuTheme.easeOut(account.hover);
        boolean current = isCurrent(account);

        if (current) MenuTheme.selectedSurface(x, y, w, CARD_HEIGHT, 8f, 1f, a);
        else MenuTheme.surface(x, y, w, CARD_HEIGHT, 8f, lit, a);

        float headX = x + 8f, headY = y + (CARD_HEIGHT - HEAD) / 2f;
        RenderUtils.drawRoundedRect(headX - 1f, headY - 1f, HEAD + 2f, HEAD + 2f, 4f, MenuTheme.white(0.08f * a));
        PlayerHeads.draw(PlayerHeads.skin(account.name, account.uuid), headX, headY, HEAD, a);

        if (account.name.equalsIgnoreCase(busyName)) {
            RenderUtils.drawRoundedRect(headX, headY, HEAD, HEAD, 3f, new Color(0, 0, 0, Math.round(150 * a)));
            float spin = (now % 900L) / 900f * 360f;
            Glyphs.ring(headX + HEAD / 2f, headY + HEAD / 2f, 6f, 1.6f, spin, 260f, MenuTheme.white(a));
        }

        float textX = headX + HEAD + 8f;
        float binSpace = BIN + 8f;
        String name = mc.fontRendererObj.trimStringToWidth(account.name, (int) (w - (textX - x) - binSpace));
        mc.fontRendererObj.drawString(name, textX, y + 10f, MenuTheme.text(MenuTheme.TEXT, a), false);

        Color tone = switch (account.type) {
            case MICROSOFT -> MenuTheme.accent();
            case CRACKED -> new Color(170, 170, 185);
            case OLD -> MenuTheme.WARNING;
        };
        String label = switch (account.type) {
            case MICROSOFT -> "Microsoft";
            case CRACKED -> "Offline";
            case OLD -> "Old login";
        };
        float badgeWidth = MenuTheme.badge(textX, y + 23f, label, tone, a);
        if (current) MenuTheme.badge(textX + badgeWidth + 4f, y + 23f, "In use", MenuTheme.SUCCESS, a);

        float[] bin = binBounds(x, y, w);
        boolean overBin = over && MenuTheme.contains(bin, pointerX, pointerY);
        account.binHover = MenuTheme.approach(account.binHover, overBin ? 1f : 0f, delta * 10f);
        if (lit > 0.02f) MenuTheme.iconButton(bin, MenuIcons.TRASH, MenuTheme.Kind.DANGER, account.binHover, lit * a);
    }

    private static float[] binBounds(float x, float y, float w) {
        return new float[]{x + w - BIN - 8f, y + (CARD_HEIGHT - BIN) / 2f, BIN, BIN};
    }

    private static List<String> wrap(CustomFontRenderer font, String text, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String attempt = line.length() == 0 ? word : line + " " + word;
            if (font.getStringWidth(attempt) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(attempt);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        boolean onField = MenuTheme.contains(fieldBox, mouseX, mouseY);
        nameField.mouseClicked(mouseX, mouseY, 0);
        nameField.setFocused(onField);
        if (onField) return;

        if (MenuTheme.contains(microsoftButton, mouseX, mouseY)) {
            if (busy && busyName == null) {
                MenuTheme.playPress();
                cancel = true;
            } else if (!busy) {
                MenuTheme.playPress();
                signInWithMicrosoft();
            }
            return;
        }
        if (MenuTheme.contains(addButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            loginOffline(nameField.getText());
            return;
        }
        if (MenuTheme.contains(randomButton, mouseX, mouseY)) {
            MenuTheme.playPress();
            loginOffline(randomName());
            return;
        }

        if (mouseY < listTop || mouseY > listBottom || accounts.isEmpty()) return;
        float cardWidth = (listWidth - 8f - (columns - 1) * CARD_GAP) / columns;
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            float x = listX + (i % columns) * (cardWidth + CARD_GAP);
            float y = listTop + account.y - scroll;
            if (!MenuTheme.contains(x, y, cardWidth, CARD_HEIGHT, mouseX, mouseY)) continue;
            MenuTheme.playPress();
            if (MenuTheme.contains(binBounds(x, y, cardWidth), mouseX, mouseY)) remove(account);
            else login(account);
            return;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - Math.signum(wheel) * (CARD_HEIGHT + CARD_GAP)));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (nameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                nameField.setFocused(false);
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                loginOffline(nameField.getText());
            } else {
                nameField.textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    public static String generateRandomString() {
        return randomName();
    }
}
