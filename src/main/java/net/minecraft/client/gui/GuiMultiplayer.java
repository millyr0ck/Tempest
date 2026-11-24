package net.minecraft.client.gui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuDock;
import rip.tempest.interfaces.menu.MenuIcons;
import rip.tempest.interfaces.menu.MenuPage;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.florianmichael.viamcp.ViaMCP;
import de.florianmichael.viamcp.gui.AsyncVersionSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.LanServerDetector;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The Multiplayer page, in the menus' style: a header with the version picker and the add,
 * direct connect and refresh buttons, then a row per server. Hovering a row (or selecting it)
 * shows its join, edit and delete buttons and the arrows that move it up and down the list.
 * Small dialogs stand in for vanilla's add, edit, direct connect and delete screens.
 *
 * <p>What is underneath is vanilla's: the saved {@link ServerList}, the {@link OldServerPinger}
 * and LAN discovery. Server names and descriptions are drawn with the Minecraft font, because
 * they use formatting codes and symbols the client's own fonts don't carry.
 */
public class GuiMultiplayer extends MenuPage {

    private static final Logger logger = LogManager.getLogger();
    private static final ThreadPoolExecutor PINGERS = new ScheduledThreadPoolExecutor(5,
            new ThreadFactoryBuilder().setNameFormat("Server Pinger #%d").setDaemon(true).build());
    private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("textures/misc/unknown_server.png");

    private static final int PING_GOOD = 0x5CE08C, PING_OKAY = 0xFAC446, PING_BAD = 0xF46868;

    private static final float CARD_HEIGHT = 44f, CARD_GAP = 5f, CARD_RADIUS = 8f, PADDING = 6f, ICON_SIZE = 32f;
    /** The right-hand column of a row: players, ping and version. */
    private static final float SIDE_WIDTH = 92f;
    /** Room at a row's right edge for the arrows that move it up and down the list. */
    private static final float ARROWS_WIDTH = 14f;
    /** The join, edit and delete buttons a row shows when it has your attention. */
    private static final float ACTION = 20f, ACTION_GAP = 4f, ACTIONS_WIDTH = ACTION * 3f + ACTION_GAP * 2f;
    private static final float SCAN_ROW_HEIGHT = 22f;
    private static final float SLIDER_WIDTH = 110f;
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    private static final int ADD = 0, DIRECT = 1, REFRESH = 2;
    private static final int JOIN_ACTION = 0, EDIT_ACTION = 1, DELETE_ACTION = 2;

    private final GuiScreen parentScreen;
    private final OldServerPinger oldServerPinger = new OldServerPinger();
    private ServerList savedServerList;
    private LanServerDetector.LanServerList lanServerList;
    private LanServerDetector.ThreadLanServerFind lanServerDetector;
    private boolean initialized;

    /** Saved servers in their saved order, then any LAN worlds. */
    private final List<Entry> entries = new ArrayList<>();
    private final Map<ServerData, Entry> savedEntries = new IdentityHashMap<>();
    private final Map<String, Entry> lanEntries = new LinkedHashMap<>();
    private Entry selected;

    private long listShown;
    private float listTop, listBottom;
    private float scroll, scrollTarget, maxScroll;
    private boolean draggingScrollbar;
    private float scrollGrab;
    private final float[][] headerButtons = new float[3][4];
    private final float[] headerHover = new float[3];
    private String tooltip;
    private Dialog dialog;

    public GuiMultiplayer(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    protected MenuDock.Page page() {
        return MenuDock.Page.MULTIPLAYER;
    }

    @Override
    protected boolean modal() {
        return dialog != null;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        if (!this.initialized) {
            this.initialized = true;
            listShown = System.currentTimeMillis();
            this.savedServerList = new ServerList(this.mc);
            this.savedServerList.loadServerList();
            this.lanServerList = new LanServerDetector.LanServerList();
            rebuildEntries();
        }

        // Closing the screen (which joining a server does too) stops the search, so pick it
        // back up whenever the screen is shown again.
        if (this.lanServerDetector == null) {
            try {
                this.lanServerDetector = new LanServerDetector.ThreadLanServerFind(this.lanServerList);
                this.lanServerDetector.start();
            } catch (Exception exception) {
                logger.warn("Unable to start LAN server detection: " + exception.getMessage());
            }
        }

        // The only real button: ViaMCP's version slider, which draws itself in this screen's style.
        AsyncVersionSlider slider = ViaMCP.INSTANCE.getAsyncVersionSlider();
        if (slider != null) this.buttonList.add(slider);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (this.lanServerList.getWasUpdated()) {
            List<LanServerDetector.LanServer> found = this.lanServerList.getLanServers();
            this.lanServerList.setWasNotUpdated();
            updateLanEntries(found);
        }

        this.oldServerPinger.pingPendingNetworks();
        if (dialog != null) dialog.tick();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (this.lanServerDetector != null) {
            this.lanServerDetector.interrupt();
            this.lanServerDetector = null;
        }

        this.oldServerPinger.clearPendingNetworks();
    }

    // ---------------------------------------------------------------- entries

    /** Rebuilds the list from the saved servers and LAN worlds, keeping the rows already made for them. */
    private void rebuildEntries() {
        entries.clear();
        Map<ServerData, Entry> kept = new IdentityHashMap<>();

        for (int i = 0; i < this.savedServerList.countServers(); i++) {
            ServerData server = this.savedServerList.getServerData(i);
            Entry entry = savedEntries.get(server);
            if (entry == null) entry = new Entry(server, null);
            kept.put(server, entry);
            entries.add(entry);
            ping(entry);
        }

        savedEntries.clear();
        savedEntries.putAll(kept);
        entries.addAll(lanEntries.values());

        if (selected != null && !entries.contains(selected)) selected = null;
    }

    private void updateLanEntries(List<LanServerDetector.LanServer> found) {
        Map<String, Entry> kept = new LinkedHashMap<>();
        for (LanServerDetector.LanServer lan : found) {
            Entry entry = lanEntries.get(lan.getServerIpPort());
            if (entry == null || !entry.lan.getServerMotd().equals(lan.getServerMotd())) {
                entry = new Entry(new ServerData(lan.getServerMotd(), lan.getServerIpPort(), true), lan);
            }
            kept.put(lan.getServerIpPort(), entry);
        }

        lanEntries.clear();
        lanEntries.putAll(kept);
        rebuildEntries();
    }

    /** Asks a saved server for its status, once. */
    private void ping(Entry entry) {
        final ServerData server = entry.server;
        if (!entry.saved() || server.field_78841_f) return;

        server.field_78841_f = true;
        server.pingToServer = -2L;
        server.serverMOTD = "";
        server.populationInfo = "";

        PINGERS.submit(() -> {
            try {
                this.oldServerPinger.ping(server);
            } catch (UnknownHostException exception) {
                server.pingToServer = -1L;
                server.serverMOTD = EnumChatFormatting.DARK_RED + "Can't resolve hostname";
            } catch (Exception exception) {
                server.pingToServer = -1L;
                server.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server.";
            }
        });
    }

    /** Reloads the saved list from disk and pings everything again. */
    private void refresh() {
        this.oldServerPinger.clearPendingNetworks();
        this.savedServerList.loadServerList();
        savedEntries.clear();
        selected = null;
        rebuildEntries();
        listShown = System.currentTimeMillis();
        scrollTarget = 0f;
    }

    /** Swaps a saved server with its neighbour, or with the first or last one when {@code toEnd}. */
    private void move(Entry entry, int direction, boolean toEnd) {
        int index = entries.indexOf(entry);
        int count = this.savedServerList.countServers();
        if (!entry.saved() || index < 0) return;

        int target = toEnd ? (direction < 0 ? 0 : count - 1) : index + direction;
        if (target < 0 || target >= count || target == index) return;

        this.savedServerList.swapServers(index, target);
        rebuildEntries();
    }

    /** Scrolls just far enough to bring a row fully into view. */
    private void reveal(Entry entry) {
        int index = entries.indexOf(entry);
        if (index < 0) return;

        float top = index * (CARD_HEIGHT + CARD_GAP);
        float view = listBottom - listTop;
        if (top < scrollTarget) scrollTarget = top;
        else if (top + CARD_HEIGHT > scrollTarget + view) scrollTarget = top + CARD_HEIGHT - view;
    }

    public void connectToSelected() {
        if (selected != null) connect(selected.server);
    }

    private void connect(ServerData server) {
        this.mc.displayGuiScreen(new GuiConnecting(this, this.mc, server));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawPage(int pointerX, int pointerY) {
        long now = System.currentTimeMillis();
        tooltip = null;

        float listStart = drawHeader(pointerX, pointerY) + 12f;
        listTop = listStart;
        listBottom = contentBottom;
        maxScroll = Math.max(0f, entries.size() * (CARD_HEIGHT + CARD_GAP) + SCAN_ROW_HEIGHT - (listBottom - listTop));
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll = MenuTheme.smooth(scroll, scrollTarget, delta, 16f);

        drawList(pointerX, pointerY, now);
        boolean barActive = draggingScrollbar || (pointerX >= scrollbarX() - 4f && pointerX <= scrollbarX() + 4f && pointerY >= listTop && pointerY <= listBottom);
        MenuTheme.scrollbar(scrollbarX(), listTop, listBottom, scroll, maxScroll, barActive, intro);
    }

    @Override
    protected void drawOverlay(int mouseX, int mouseY) {
        if (tooltip != null && dialog == null) MenuTheme.tooltip(tooltip, mouseX, mouseY, width, height);
        if (dialog != null) dialog.draw(mouseX, mouseY, System.currentTimeMillis());
    }

    /** The title and summary on the left, the version picker and buttons on the right. Returns the y under it. */
    private float drawHeader(int pointerX, int pointerY) {
        int saved = this.savedServerList.countServers(), online = 0;
        for (Entry entry : entries) {
            if (entry.saved() && entry.server.field_78841_f && entry.server.pingToServer >= 0L) online++;
        }
        String summary = saved + (saved == 1 ? " server" : " servers") + (online > 0 ? ", " + online + " online" : "");
        float under = MenuTheme.pageHeader("Multiplayer", summary, columnX, 22f, intro);

        float right = columnX + columnWidth, y = 26f - (1f - intro) * 6f;
        CustomFontRenderer font = MenuTheme.body();
        float refreshWidth = MenuTheme.BUTTON_HEIGHT, directWidth = font.getStringWidth("Direct") + 30f, addWidth = font.getStringWidth("Add server") + 30f;
        MenuTheme.set(headerButtons[REFRESH], right - refreshWidth, y, refreshWidth, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(headerButtons[DIRECT], right - refreshWidth - 6f - directWidth, y, directWidth, MenuTheme.BUTTON_HEIGHT);
        MenuTheme.set(headerButtons[ADD], right - refreshWidth - directWidth - 12f - addWidth, y, addWidth, MenuTheme.BUTTON_HEIGHT);
        for (int i = 0; i < 3; i++) {
            headerHover[i] = MenuTheme.approach(headerHover[i], MenuTheme.contains(headerButtons[i], pointerX, pointerY) ? 1f : 0f, delta * 8f);
        }
        MenuTheme.button(headerButtons[ADD], "Add server", MenuIcons.PLUS, MenuTheme.Kind.PRIMARY, headerHover[ADD], true, intro);
        MenuTheme.button(headerButtons[DIRECT], "Direct", MenuIcons.BOLT, MenuTheme.Kind.PLAIN, headerHover[DIRECT], true, intro);
        MenuTheme.iconButton(headerButtons[REFRESH], MenuIcons.REFRESH, MenuTheme.Kind.PLAIN, headerHover[REFRESH], intro);
        if (headerHover[REFRESH] > 0.5f) tooltip = "Refresh (F5)";

        AsyncVersionSlider slider = ViaMCP.INSTANCE.getAsyncVersionSlider();
        if (slider != null) {
            slider.xPosition = Math.round(headerButtons[ADD][0] - 10f - SLIDER_WIDTH);
            slider.yPosition = Math.round(y);
            slider.drawButton(this.mc, pointerX, pointerY);
            // Dropped on a narrow window, where it would run into the title.
            if (columnWidth >= 560f) {
                String label = "Version";
                font.drawString(label, slider.xPosition - font.getStringWidth(label) - 7f,
                        slider.yPosition + (MenuTheme.BUTTON_HEIGHT - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
            }
        }
        return under;
    }

    private void drawList(int pointerX, int pointerY, long now) {
        float originY = listTop - scroll;
        boolean pointerInList = pointerY >= listTop && pointerY < listBottom;

        // Clipped top and bottom only: the selected row's glow reaches past the list's sides.
        RenderUtils.startScissor(0f, listTop, this.width, listBottom - listTop);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);

            // Rows glide to their place in the list, so reordering and deleting read as movement.
            float slot = i * (CARD_HEIGHT + CARD_GAP);
            entry.y = Float.isNaN(entry.y) ? slot : MenuTheme.smooth(entry.y, slot, delta, 18f);

            // They arrive one after another when the list is first shown or refreshed.
            float appear = MenuTheme.easeOut((now - listShown - 40L - Math.min(i, 10) * 35L) / 360f);
            entry.drawnY = originY + entry.y + (1f - appear) * 10f;

            boolean hovered = pointerInList && MenuTheme.contains(columnX, entry.drawnY, columnWidth, CARD_HEIGHT, pointerX, pointerY);
            entry.hover = MenuTheme.approach(entry.hover, hovered ? 1f : 0f, delta * 9f);
            entry.select = MenuTheme.approach(entry.select, entry == selected ? 1f : 0f, delta * 8f);

            if (entry.drawnY + CARD_HEIGHT < listTop - 14f || entry.drawnY > listBottom + 14f) continue;
            drawRow(entry, i, pointerX, pointerY, hovered, now, appear * intro);
        }

        float scanY = originY + entries.size() * (CARD_HEIGHT + CARD_GAP);
        drawScanRow(scanY, now, intro);

        RenderUtils.endScissor();

        if (this.savedServerList.countServers() == 0) {
            float middle = Math.max(scanY + SCAN_ROW_HEIGHT + 24f, (listTop + listBottom) / 2f - 10f);
            float cx = columnX + columnWidth / 2f;
            MenuIcons.GLOBE.draw(cx, middle - 16f, 10f, MenuTheme.white(0.25f * intro));
            MenuTheme.bold().drawCenteredString("No servers yet", cx, middle + 2f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, intro));
            MenuTheme.body().drawCenteredString("Add one, or use Direct to join without saving it.", cx, middle + 16f, MenuTheme.text(MenuTheme.TEXT_MUTED, intro));
        }
    }

    private void drawRow(Entry entry, int index, int pointerX, int pointerY, boolean hovered, long now, float alpha) {
        if (alpha < 0.02f) return;

        ServerData server = entry.server;
        float x = columnX, y = entry.drawnY, w = columnWidth;
        float hover = MenuTheme.easeOut(entry.hover), select = MenuTheme.easeOut(entry.select);

        MenuTheme.surface(x, y, w, CARD_HEIGHT, CARD_RADIUS, hover * (1f - select), alpha * (1f - select));
        MenuTheme.selectedSurface(x, y, w, CARD_HEIGHT, CARD_RADIUS, select, alpha);

        // Icon, which doubles as a join button.
        float iconX = x + PADDING, iconY = y + PADDING;
        boolean overIcon = hovered && MenuTheme.contains(iconX, iconY, ICON_SIZE, ICON_SIZE, pointerX, pointerY);
        entry.iconHover = MenuTheme.approach(entry.iconHover, overIcon ? 1f : 0f, delta * 10f);
        drawIcon(entry, iconX, iconY, alpha);
        if (entry.iconHover > 0.01f) {
            float veil = MenuTheme.easeOut(entry.iconHover) * alpha;
            RenderUtils.drawRoundedRect(iconX, iconY, ICON_SIZE, ICON_SIZE, 4f, new Color(0, 0, 0, Math.round(150 * veil)));
            Glyphs.play(iconX + ICON_SIZE / 2f, iconY + ICON_SIZE / 2f, 7f, MenuTheme.white(veil));
        }

        float attention = Math.max(hover, select);
        float right = x + w - PADDING - (entry.saved() ? ARROWS_WIDTH : 0f);
        float textX = iconX + ICON_SIZE + 8f;
        float actionsLeft = right - (entry.saved() ? SIDE_WIDTH + 8f : 0f) - ACTIONS_WIDTH;
        int textWidth = (int) (actionsLeft - 8f - textX);
        FontRenderer font = this.fontRendererObj;

        String name = entry.saved() ? server.serverName : I18n.format("lanServer.title");
        font.drawString(font.trimStringToWidth(name == null ? "" : name, textWidth), textX, y + 7f, MenuTheme.text(MenuTheme.TEXT, alpha), false);

        if (!entry.saved()) {
            String address = this.mc.gameSettings.hideServerAddress ? I18n.format("selectServer.hiddenAddress") : entry.lan.getServerIpPort();
            font.drawString(font.trimStringToWidth(entry.lan.getServerMotd(), textWidth), textX, y + 18f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, alpha), false);
            font.drawString(font.trimStringToWidth(address, textWidth), textX, y + 28f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha), false);
            drawActions(entry, actionsLeft, y, pointerX, pointerY, attention * alpha, false);
            return;
        }

        boolean pinging = !server.field_78841_f || server.pingToServer == -2L;
        boolean online = !pinging && server.pingToServer >= 0L;

        if (pinging) {
            font.drawString("Pinging" + dots(now), textX, y + 18f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha), false);
        } else {
            List<String> lines = font.listFormattedStringToWidth(server.serverMOTD == null ? "" : server.serverMOTD, Math.max(20, textWidth));
            for (int i = 0; i < Math.min(lines.size(), 2); i++) {
                font.drawString(lines.get(i), textX, y + 18f + i * 10f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, alpha), false);
            }
        }

        drawActions(entry, actionsLeft, y, pointerX, pointerY, attention * alpha, true);

        // Right-hand column: players, then ping, then the server's version.
        if (online && server.populationInfo != null && !server.populationInfo.isEmpty()) {
            float populationWidth = font.getStringWidth(server.populationInfo);
            font.drawString(server.populationInfo, right - populationWidth, y + 7f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, alpha), false);
            if (hovered && server.playerList != null && !server.playerList.isEmpty()
                    && MenuTheme.contains(right - populationWidth, y + 6f, populationWidth, 10f, pointerX, pointerY)) {
                tooltip = server.playerList;
            }
        }

        String pingLabel = pinging ? "" : online ? server.pingToServer + "ms" : "Offline";
        float pingWidth = font.getStringWidth(pingLabel);
        font.drawString(pingLabel, right - pingWidth, y + 18f, MenuTheme.text(online ? MenuTheme.TEXT_SECONDARY : PING_BAD, alpha), false);
        drawSignal(right - pingWidth - (pingLabel.isEmpty() ? 0f : 4f), y + 25f, server, pinging, online, index, now, alpha);

        if (online && server.gameVersion != null) {
            // Not an error with ViaMCP on hand, but worth pointing out: it is the version to pick above.
            boolean differs = server.version != 47;
            String version = font.trimStringToWidth(EnumChatFormatting.getTextWithoutFormattingCodes(server.gameVersion), (int) SIDE_WIDTH);
            float versionWidth = font.getStringWidth(version);
            font.drawString(version, right - versionWidth, y + 29f, MenuTheme.text(differs ? PING_OKAY : MenuTheme.TEXT_MUTED, alpha * (differs ? 0.9f : 1f)), false);
            if (hovered && MenuTheme.contains(right - versionWidth, y + 28f, versionWidth, 10f, pointerX, pointerY)) {
                tooltip = EnumChatFormatting.getTextWithoutFormattingCodes(server.gameVersion) + "\nProtocol " + server.version;
            }
        }

        // The arrows that move it up and down, shown once the row has your attention.
        float arrowsAlpha = attention * alpha;
        if (arrowsAlpha > 0.02f) {
            float arrowX = x + w - PADDING - 5f;
            boolean overArrows = hovered && pointerX >= x + w - PADDING - ARROWS_WIDTH + 1f;
            boolean overUp = overArrows && pointerY < y + CARD_HEIGHT / 2f;
            boolean overDown = overArrows && !overUp;
            boolean canGoUp = index > 0, canGoDown = index < this.savedServerList.countServers() - 1;
            Glyphs.chevron(arrowX, y + 15f, 3.2f, 1.2f, true, MenuTheme.white(arrowsAlpha * (!canGoUp ? 0.12f : overUp ? 1f : 0.45f)));
            Glyphs.chevron(arrowX, y + CARD_HEIGHT - 15f, 3.2f, 1.2f, false, MenuTheme.white(arrowsAlpha * (!canGoDown ? 0.12f : overDown ? 1f : 0.45f)));
        }
    }

    /** Join, edit and delete, faded in with the row's attention. LAN worlds only get join. */
    private void drawActions(Entry entry, float left, float y, int pointerX, int pointerY, float alpha, boolean saved) {
        float top = y + (CARD_HEIGHT - ACTION) / 2f;
        for (int i = 0; i < 3; i++) {
            float[] b = actionBounds(left, top, i);
            boolean over = MenuTheme.contains(b, pointerX, pointerY);
            entry.actionHover[i] = MenuTheme.approach(entry.actionHover[i], over ? 1f : 0f, delta * 10f);
            if (alpha < 0.02f || (!saved && i != JOIN_ACTION)) continue;
            MenuIcons.Icon icon = i == JOIN_ACTION ? MenuIcons.PLAY : i == EDIT_ACTION ? MenuIcons.PENCIL : MenuIcons.TRASH;
            MenuTheme.Kind kind = i == DELETE_ACTION ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PLAIN;
            MenuTheme.iconButton(b, icon, kind, entry.actionHover[i], alpha);
            if (over) tooltip = i == JOIN_ACTION ? "Join" : i == EDIT_ACTION ? "Edit" : "Delete";
        }
    }

    private static float[] actionBounds(float left, float top, int index) {
        // Join sits nearest the stats, so a LAN world's lone button lines up with a saved server's.
        float x = left + (2 - index) * (ACTION + ACTION_GAP);
        return new float[]{x, top, ACTION, ACTION};
    }

    private void drawIcon(Entry entry, float x, float y, float alpha) {
        if (!entry.saved()) {
            RenderUtils.drawRoundedRect(x, y, ICON_SIZE, ICON_SIZE, 5f, MenuTheme.white(0.06f * alpha));
            MenuTheme.small().drawCenteredString("LAN", x + ICON_SIZE / 2f, y + (ICON_SIZE - MenuTheme.small().getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));
            return;
        }

        updateIcon(entry);
        this.mc.getTextureManager().bindTexture(entry.icon != null ? entry.iconLocation : UNKNOWN_SERVER);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, alpha);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Loads the server's icon when a ping has brought a new one, and saves the list so it is kept. */
    private void updateIcon(Entry entry) {
        String data = entry.server.getBase64EncodedIconData();
        if (data == null || data.equals(entry.iconData)) return;

        boolean fromPing = entry.iconData != null || !entry.iconFromDisk;
        entry.iconData = data;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(data)));
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IOException("Server icons must be 64 pixels square");
            }

            if (entry.icon != null) this.mc.getTextureManager().deleteTexture(entry.iconLocation);
            entry.icon = new DynamicTexture(image);
            this.mc.getTextureManager().loadTexture(entry.iconLocation, entry.icon);
            if (fromPing) this.savedServerList.saveServerList();
        } catch (Exception exception) {
            logger.error("Invalid icon for server " + entry.server.serverName + " (" + entry.server.serverIP + ")", exception);
            entry.server.setBase64EncodedIconData(null);
        }
    }

    /**
     * Four bars rising to the right, their right edge at {@code right} and feet on
     * {@code baseline}: how many are lit, and in what colour, follows the ping.
     */
    private void drawSignal(float right, float baseline, ServerData server, boolean pinging, boolean online,
                            int index, long now, float alpha) {
        int lit, rgb;
        if (pinging) {
            lit = -1;
            rgb = MenuTheme.TEXT_SECONDARY;
        } else if (!online) {
            lit = 0;
            rgb = PING_BAD;
        } else if (server.pingToServer < 150L) {
            lit = 4;
            rgb = PING_GOOD;
        } else if (server.pingToServer < 300L) {
            lit = 3;
            rgb = PING_GOOD;
        } else if (server.pingToServer < 600L) {
            lit = 2;
            rgb = PING_OKAY;
        } else {
            lit = 1;
            rgb = PING_BAD;
        }

        // While pinging, a single bar runs back and forth, out of step from row to row.
        int runner = (int) ((now / 150L + index * 2L) % 6L);
        if (runner > 3) runner = 6 - runner;

        float left = right - (4 * 2f + 3 * 1.5f);
        for (int bar = 0; bar < 4; bar++) {
            boolean on = pinging ? bar == runner : bar < lit;
            float barHeight = 3f + bar * 2f;
            Color color = on ? new Color(rgb) : !pinging && !online ? new Color(PING_BAD) : Color.WHITE;
            Glyphs.bar(left + bar * 3.5f, baseline - barHeight, 2f, barHeight, MenuTheme.alpha(color, (on ? 1f : 0.18f) * alpha));
        }
    }

    private void drawScanRow(float y, long now, float alpha) {
        CustomFontRenderer font = MenuTheme.body();
        String label = I18n.format("lanServer.scanning");
        float labelWidth = font.getStringWidth(label);
        float left = columnX + (columnWidth - labelWidth - 18f) / 2f;
        font.drawString(label, left, y + (SCAN_ROW_HEIGHT - font.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));

        // Three dots brightening in turn.
        for (int i = 0; i < 3; i++) {
            float pulse = Math.max(0f, (float) Math.sin(now / 240.0 - i * 0.9));
            Glyphs.disc(left + labelWidth + 7f + i * 5f, y + SCAN_ROW_HEIGHT / 2f, 1.1f, MenuTheme.white(alpha * (0.15f + 0.45f * pulse)));
        }
    }

    private float scrollbarX() {
        return columnX + columnWidth + 8f;
    }

    private void dragScrollbar(int mouseY) {
        float travel = listBottom - listTop - MenuTheme.thumbHeight(listTop, listBottom, maxScroll);
        if (travel <= 0f) return;

        // Dragging moves the list directly rather than easing after the pointer.
        scrollTarget = scroll = Math.max(0f, Math.min(maxScroll, (mouseY - scrollGrab - listTop) / travel * maxScroll));
    }

    // ------------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && dialog == null) {
            scrollTarget -= Math.signum(wheel) * (CARD_HEIGHT + CARD_GAP) * 1.5f;
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (dialog != null) {
            dialog.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        // The dock, then the version slider.
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        if (MenuTheme.contains(headerButtons[ADD], mouseX, mouseY)) {
            MenuTheme.playPress();
            dialog = new Dialog(DialogKind.ADD, null);
            return;
        }
        if (MenuTheme.contains(headerButtons[DIRECT], mouseX, mouseY)) {
            MenuTheme.playPress();
            dialog = new Dialog(DialogKind.DIRECT, null);
            return;
        }
        if (MenuTheme.contains(headerButtons[REFRESH], mouseX, mouseY)) {
            MenuTheme.playPress();
            refresh();
            return;
        }

        if (mouseY < listTop || mouseY >= listBottom) return;

        float barX = scrollbarX();
        if (maxScroll > 0f && mouseX >= barX - 4f && mouseX <= barX + 4f) {
            float thumbHeight = MenuTheme.thumbHeight(listTop, listBottom, maxScroll);
            float thumbY = listTop + (listBottom - listTop - thumbHeight) * (scroll / maxScroll);
            // Grabbing the thumb keeps hold of that spot; clicking the track centres the thumb there.
            scrollGrab = mouseY >= thumbY && mouseY <= thumbY + thumbHeight ? mouseY - thumbY : thumbHeight / 2f;
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (MenuTheme.contains(columnX, entry.drawnY, columnWidth, CARD_HEIGHT, mouseX, mouseY)) {
                clickEntry(entry, mouseX, mouseY);
                return;
            }
        }
    }

    private void clickEntry(Entry entry, int mouseX, int mouseY) {
        selected = entry;
        float localX = mouseX - columnX, localY = mouseY - entry.drawnY;

        // The row's buttons.
        float right = columnX + columnWidth - PADDING - (entry.saved() ? ARROWS_WIDTH : 0f);
        float actionsLeft = right - (entry.saved() ? SIDE_WIDTH + 8f : 0f) - ACTIONS_WIDTH;
        float actionsTop = entry.drawnY + (CARD_HEIGHT - ACTION) / 2f;
        for (int i = 0; i < 3; i++) {
            if (!entry.saved() && i != JOIN_ACTION) continue;
            if (!MenuTheme.contains(actionBounds(actionsLeft, actionsTop, i), mouseX, mouseY)) continue;
            MenuTheme.playPress();
            if (i == JOIN_ACTION) connectToSelected();
            else if (i == EDIT_ACTION) dialog = new Dialog(DialogKind.EDIT, entry);
            else dialog = new Dialog(DialogKind.DELETE, entry);
            return;
        }

        if (entry.saved() && localX >= columnWidth - PADDING - ARROWS_WIDTH + 1f) {
            MenuTheme.playPress();
            move(entry, localY < CARD_HEIGHT / 2f ? -1 : 1, isShiftKeyDown());
            return;
        }

        long now = Minecraft.getSystemTime();
        boolean onIcon = localX >= PADDING && localX <= PADDING + ICON_SIZE && localY >= PADDING && localY <= PADDING + ICON_SIZE;
        if (onIcon || now - entry.lastClick < DOUBLE_CLICK_MILLIS) {
            MenuTheme.playPress();
            connectToSelected();
        }
        entry.lastClick = now;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingScrollbar = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar) dragScrollbar(mouseY);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (dialog != null) {
            dialog.keyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == Keyboard.KEY_F5) {
            refresh();
        } else if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            int direction = keyCode == Keyboard.KEY_UP ? -1 : 1;
            if (isShiftKeyDown()) {
                // Shift moves the server itself rather than the selection.
                if (selected != null) {
                    move(selected, direction, false);
                    reveal(selected);
                }
            } else if (!entries.isEmpty()) {
                int index = selected == null ? -1 : entries.indexOf(selected);
                int next = index < 0 ? (direction > 0 ? 0 : entries.size() - 1)
                        : Math.max(0, Math.min(entries.size() - 1, index + direction));
                selected = entries.get(next);
                reveal(selected);
            }
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            connectToSelected();
        } else if (keyCode == Keyboard.KEY_DELETE) {
            if (selected != null && selected.saved()) dialog = new Dialog(DialogKind.DELETE, selected);
        } else {
            // Escape: home.
            super.keyTyped(typedChar, keyCode);
        }
    }

    // ------------------------------------------- kept for the vanilla list classes

    public OldServerPinger getOldServerPinger() {
        return this.oldServerPinger;
    }

    public ServerList getServerList() {
        return this.savedServerList;
    }

    public void setHoveringText(String text) {
        this.tooltip = text;
    }

    public void selectServer(int index) {
        selected = index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    public boolean func_175392_a(ServerListEntryNormal entry, int index) {
        return index > 0;
    }

    public boolean func_175394_b(ServerListEntryNormal entry, int index) {
        return index < this.savedServerList.countServers() - 1;
    }

    public void func_175391_a(ServerListEntryNormal entry, int index, boolean toTop) {
        if (index >= 0 && index < entries.size()) move(entries.get(index), -1, toTop);
    }

    public void func_175393_b(ServerListEntryNormal entry, int index, boolean toBottom) {
        if (index >= 0 && index < entries.size()) move(entries.get(index), 1, toBottom);
    }

    // ---------------------------------------------------------------- helpers

    private static String dots(long now) {
        int count = (int) (now / 350L % 4L);
        return count == 0 ? "" : count == 1 ? "." : count == 2 ? ".." : "...";
    }

    /** A row in the list: a saved server, or a LAN world when {@link #lan} is set. */
    private final class Entry {
        final ServerData server;
        final LanServerDetector.LanServer lan;
        final ResourceLocation iconLocation;
        DynamicTexture icon;
        /** The icon data last loaded, and whether the server came with icon data when its row was made. */
        String iconData;
        final boolean iconFromDisk;

        /** Its animated offset from the top of the list, and where that put it on screen last frame. */
        float y = Float.NaN, drawnY;
        float hover, select, iconHover;
        final float[] actionHover = new float[3];
        long lastClick;

        Entry(ServerData server, LanServerDetector.LanServer lan) {
            this.server = server;
            this.lan = lan;
            this.iconLocation = new ResourceLocation("servers/" + server.serverIP + "/icon");
            this.iconFromDisk = server.getBase64EncodedIconData() != null;

            // Reuse the texture if this server's icon is still loaded from an earlier visit.
            ITextureObject loaded = lan == null ? mc.getTextureManager().getTexture(this.iconLocation) : null;
            if (loaded instanceof DynamicTexture) this.icon = (DynamicTexture) loaded;
        }

        boolean saved() {
            return lan == null;
        }
    }

    private enum DialogKind {
        ADD, EDIT, DIRECT, DELETE
    }

    /** A small panel over the dimmed screen, standing in for vanilla's add, edit, direct connect and delete screens. */
    private final class Dialog {
        private static final float WIDTH = 260f;
        private static final float PAD = 16f;
        private static final float FIELD_HEIGHT = 20f;
        /** A field's label and box, plus the space under it. */
        private static final float FIELD_BLOCK = 38f;

        private final DialogKind kind;
        private final Entry entry;
        private final GuiTextField nameField, addressField;
        private ServerData.ServerResourceMode resourceMode = ServerData.ServerResourceMode.PROMPT;
        private final long opened = System.currentTimeMillis();
        private long closing = -1L, lastFrame = opened;

        private final float[] nameBox = new float[4], addressBox = new float[4];
        private final float[] packsBounds = new float[4], cancelBounds = new float[4], confirmBounds = new float[4];
        private float packsHover, cancelHover, confirmHover;

        Dialog(DialogKind kind, Entry entry) {
            this.kind = kind;
            this.entry = entry;

            boolean named = kind == DialogKind.ADD || kind == DialogKind.EDIT;
            this.nameField = named ? field(32) : null;
            this.addressField = kind != DialogKind.DELETE ? field(128) : null;

            if (kind == DialogKind.EDIT) {
                nameField.setText(entry.server.serverName);
                addressField.setText(entry.server.serverIP);
                resourceMode = entry.server.getResourceMode();
            } else if (kind == DialogKind.ADD) {
                nameField.setText(I18n.format("selectServer.defaultName"));
            } else if (kind == DialogKind.DIRECT) {
                addressField.setText(mc.gameSettings.lastServer == null ? "" : mc.gameSettings.lastServer);
            }

            if (nameField != null) nameField.setFocused(true);
            else if (addressField != null) addressField.setFocused(true);
        }

        /** A vanilla text field without its own box: it does the editing, the dialog draws the frame. */
        private GuiTextField field(int maxLength) {
            GuiTextField field = new GuiTextField(0, fontRendererObj, 0, 0, (int) (WIDTH - PAD * 2f - 12f), 9);
            field.setEnableBackgroundDrawing(false);
            field.setMaxStringLength(maxLength);
            return field;
        }

        void tick() {
            if (nameField != null) nameField.updateCursorCounter();
            if (addressField != null) addressField.updateCursorCounter();
        }

        private String title() {
            return switch (kind) {
                case ADD -> "Add server";
                case EDIT -> "Edit server";
                case DIRECT -> "Direct connect";
                case DELETE -> "Delete server";
            };
        }

        private String confirmLabel() {
            return switch (kind) {
                case ADD -> "Add";
                case EDIT -> "Save";
                case DIRECT -> "Join";
                case DELETE -> "Delete";
            };
        }

        private float panelHeight() {
            float body = switch (kind) {
                case ADD, EDIT -> FIELD_BLOCK * 2f + MenuTheme.BUTTON_HEIGHT + 12f;
                case DIRECT -> FIELD_BLOCK + 6f;
                case DELETE -> 36f;
            };
            return PAD + 20f + body + MenuTheme.BUTTON_HEIGHT + PAD;
        }

        private boolean valid() {
            if (kind == DialogKind.DELETE) return true;

            String address = addressField.getText();
            boolean addressOk = address.length() > 0 && address.split(":").length > 0;
            return addressOk && (nameField == null || nameField.getText().length() > 0);
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

            float panelHeight = panelHeight();
            float x = (width - WIDTH) / 2f, y = (height - panelHeight) / 2f + (1f - shown) * 8f;
            float inner = WIDTH - PAD * 2f;
            MenuTheme.dialogPanel(width, height, x, y, WIDTH, panelHeight, shown);

            MenuTheme.bold().drawString(title(), x + PAD, y + PAD, MenuTheme.text(MenuTheme.TEXT, shown));
            float top = y + PAD + 20f;

            if (kind == DialogKind.DELETE) {
                FontRenderer font = fontRendererObj;
                font.drawString("Remove this server from your list?", x + PAD, top + 2f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, shown), false);
                font.drawString(font.trimStringToWidth(entry.server.serverName == null ? "" : entry.server.serverName, (int) inner),
                        x + PAD, top + 14f, MenuTheme.text(MenuTheme.TEXT_MUTED, shown), false);
            } else {
                if (nameField != null) {
                    drawField(nameField, nameBox, I18n.format("addServer.enterName"), x + PAD, top, inner, shown);
                    top += FIELD_BLOCK;
                }
                drawField(addressField, addressBox, I18n.format("addServer.enterIp"), x + PAD, top, inner, shown);
                top += FIELD_BLOCK;

                if (nameField != null) {
                    MenuTheme.set(packsBounds, x + PAD, top, inner, MenuTheme.BUTTON_HEIGHT);
                    packsHover = MenuTheme.approach(packsHover, MenuTheme.contains(packsBounds, mouseX, mouseY) ? 1f : 0f, frameDelta * 8f);
                    MenuTheme.button(packsBounds, I18n.format("addServer.resourcePack") + ": " + resourceMode.getMotd().getUnformattedText(),
                            null, MenuTheme.Kind.PLAIN, packsHover, true, shown);
                }
            }

            float buttonY = y + panelHeight - PAD - MenuTheme.BUTTON_HEIGHT;
            float half = (inner - 6f) / 2f;
            MenuTheme.set(cancelBounds, x + PAD, buttonY, half, MenuTheme.BUTTON_HEIGHT);
            MenuTheme.set(confirmBounds, x + PAD + half + 6f, buttonY, half, MenuTheme.BUTTON_HEIGHT);

            boolean valid = valid();
            cancelHover = MenuTheme.approach(cancelHover, MenuTheme.contains(cancelBounds, mouseX, mouseY) ? 1f : 0f, frameDelta * 8f);
            confirmHover = MenuTheme.approach(confirmHover, valid && MenuTheme.contains(confirmBounds, mouseX, mouseY) ? 1f : 0f, frameDelta * 8f);
            MenuTheme.button(cancelBounds, "Cancel", null, MenuTheme.Kind.PLAIN, cancelHover, true, shown);
            MenuTheme.button(confirmBounds, confirmLabel(), null, kind == DialogKind.DELETE ? MenuTheme.Kind.DANGER : MenuTheme.Kind.PRIMARY,
                    kind == DialogKind.DELETE ? Math.max(confirmHover, 0.6f) : confirmHover, valid, shown);
        }

        private void drawField(GuiTextField field, float[] box, String label, float x, float y, float fieldWidth, float alpha) {
            MenuTheme.small().drawString(label, x, y, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));

            MenuTheme.set(box, x, y + 12f, fieldWidth, FIELD_HEIGHT);
            MenuTheme.fieldFrame(box[0], box[1], box[2], box[3], field.isFocused(), alpha);

            field.xPosition = Math.round(box[0] + 6f);
            field.yPosition = Math.round(box[1] + 6f);
            field.setTextColor(MenuTheme.text(0xE6E6EE, alpha));
            field.drawTextBox();
        }

        void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (closing >= 0L || mouseButton != 0) return;

            if (MenuTheme.contains(cancelBounds, mouseX, mouseY)) {
                MenuTheme.playPress();
                close();
            } else if (MenuTheme.contains(confirmBounds, mouseX, mouseY)) {
                if (valid()) {
                    MenuTheme.playPress();
                    confirm();
                }
            } else if (nameField != null && MenuTheme.contains(packsBounds, mouseX, mouseY)) {
                MenuTheme.playPress();
                ServerData.ServerResourceMode[] modes = ServerData.ServerResourceMode.values();
                resourceMode = modes[(resourceMode.ordinal() + 1) % modes.length];
            } else {
                // A click on a field's frame focuses it; a click elsewhere on the panel leaves the focus alone.
                boolean onName = nameField != null && MenuTheme.contains(nameBox, mouseX, mouseY);
                boolean onAddress = addressField != null && MenuTheme.contains(addressBox, mouseX, mouseY);
                if (onName || onAddress) {
                    focus(nameField, onName, mouseX, mouseY);
                    focus(addressField, onAddress, mouseX, mouseY);
                }
            }
        }

        private void focus(GuiTextField field, boolean focused, int mouseX, int mouseY) {
            if (field == null) return;
            // The field places its own cursor (and decides its focus by its text area, which is
            // smaller than the frame), so the focus is set again afterwards.
            field.mouseClicked(mouseX, mouseY, 0);
            field.setFocused(focused);
        }

        void keyTyped(char typedChar, int keyCode) {
            if (closing >= 0L) return;

            if (keyCode == Keyboard.KEY_ESCAPE) {
                close();
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                confirm();
            } else if (keyCode == Keyboard.KEY_TAB && nameField != null) {
                boolean toAddress = nameField.isFocused();
                nameField.setFocused(!toAddress);
                addressField.setFocused(toAddress);
            } else {
                if (nameField != null) nameField.textboxKeyTyped(typedChar, keyCode);
                if (addressField != null) addressField.textboxKeyTyped(typedChar, keyCode);
            }
        }

        private void confirm() {
            if (!valid()) return;

            switch (kind) {
                case ADD -> {
                    ServerData server = new ServerData(nameField.getText(), addressField.getText(), false);
                    server.setResourceMode(resourceMode);
                    savedServerList.addServerData(server);
                    savedServerList.saveServerList();
                    rebuildEntries();
                    selected = savedEntries.get(server);
                    if (selected != null) reveal(selected);
                }
                case EDIT -> {
                    ServerData server = entry.server;
                    boolean moved = !addressField.getText().equals(server.serverIP);
                    server.serverName = nameField.getText();
                    server.serverIP = addressField.getText();
                    server.setResourceMode(resourceMode);
                    savedServerList.saveServerList();
                    if (moved) {
                        // A different address is a different server: ask it again.
                        server.field_78841_f = false;
                        ping(entry);
                    }
                }
                case DIRECT -> {
                    rememberAddress();
                    dialog = null;
                    connect(new ServerData(I18n.format("selectServer.defaultName"), addressField.getText(), false));
                    return;
                }
                case DELETE -> {
                    int index = entries.indexOf(entry);
                    if (index >= 0 && index < savedServerList.countServers()) {
                        savedServerList.removeServerData(index);
                        savedServerList.saveServerList();
                        rebuildEntries();
                    }
                }
            }

            closing = System.currentTimeMillis();
        }

        private void close() {
            if (kind == DialogKind.DIRECT) rememberAddress();
            closing = System.currentTimeMillis();
        }

        /** As vanilla does, the address typed for a direct connection is kept for next time. */
        private void rememberAddress() {
            mc.gameSettings.lastServer = addressField.getText();
            mc.gameSettings.saveOptions();
        }
    }
}
