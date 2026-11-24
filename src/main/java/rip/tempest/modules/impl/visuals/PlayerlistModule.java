package rip.tempest.modules.impl.visuals;

import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import org.lwjgl.input.Mouse;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Player List", category = ModuleCategory.VISUALS)
public final class PlayerlistModule extends Module {
    private static final String DRAG_KEY = "PlayerList";

    private static final float BASE_ROW = 8, BASE_HEADER = 9;
    private static final int NAME_GREY = 0xB9B9B9;

    private final NumberProperty size = new NumberProperty("Size", 1.6, 0.75, 3, 0.05);
    private final NumberProperty maxPlayers = new NumberProperty("Max Players", 20, 5, 50, 1);
    private final Property<Boolean> distanceColumn = new Property<>("Distance Column", true);
    private final Property<Boolean> healthColumn = new Property<>("HP Column", true);
    private final Property<Boolean> pingColumn = new Property<>("Ping Column", true);
    private final Property<Boolean> showSelf = new Property<>("Show Self", true);
    private final Property<Boolean> selfHighlighting = new Property<>("Self Highlighting", false);
    private final NumberProperty opacity = new NumberProperty("Background Opacity", 140, 0, 255, 1);

    private static PlayerlistModule instance;

    private static final class Entry {
        NetworkPlayerInfo info;
        String name;

        String shown;
        int teamColor = NAME_GREY;
        boolean self, loaded;
        String distance, health, ping;
        int pingColor;
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, Entry> byId = new HashMap<>();
    private float scroll, shownScroll, width, height;
    private float[] columnWidths = new float[0];
    private long lastFrame;

    public PlayerlistModule() {
        instance = this;
    }

    private float k() {
        return size.getValue().floatValue();
    }

    private float row() {
        return BASE_ROW * k();
    }

    private float header() {
        return BASE_HEADER * k();
    }

    private CustomFontRenderer font(float openAdjustSize) {
        return clientFont(Math.round(openAdjustSize * 1.2f * k() * 2) / 2f);
    }

    private static final java.util.Set<String> missingFonts = new java.util.HashSet<>(), foundFonts = new java.util.HashSet<>();

    private static CustomFontRenderer clientFont(float size) {
        CustomFontRenderer current = FontProcess.getCurrentFont();
        String file = current != null && current.getNameFontTTF() != null ? current.getNameFontTTF().toLowerCase() : null;
        if (file != null && !missingFonts.contains(file)) {
            try {
                if (foundFonts.add(file)) {
                    try {
                        mc.getResourceManager().getResource(new ResourceLocation("tempest/fonts/" + file + ".ttf")).getInputStream().close();
                    } catch (Exception missing) {
                        foundFonts.remove(file);
                        throw missing;
                    }
                }
                CustomFontRenderer f = FontProcess.getStyledFont(file, size, Font.PLAIN);
                if (f != null) return f;
            } catch (Exception e) {
                missingFonts.add(file);
            }
        }
        return current != null ? current : FontProcess.getFont("tempest");
    }

    @EventLink
    public Listener<PreUpdateEvent> preUpdateEventListener = e -> refresh();

    private static final Comparator<NetworkPlayerInfo> TAB_ORDER = (a, b) -> {
        boolean sa = a.getGameType() == WorldSettings.GameType.SPECTATOR, sb = b.getGameType() == WorldSettings.GameType.SPECTATOR;
        if (sa != sb) return sa ? 1 : -1;
        ScorePlayerTeam ta = a.getPlayerTeam(), tb = b.getPlayerTeam();
        int team = (ta != null ? ta.getRegisteredName() : "").compareTo(tb != null ? tb.getRegisteredName() : "");
        if (team != 0) return team;
        return a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName());
    };

    private void refresh() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            entries.clear();
            return;
        }
        List<NetworkPlayerInfo> infos = new ArrayList<>(mc.getNetHandler().getPlayerInfoMap());
        infos.sort(TAB_ORDER);
        List<Entry> fresh = new ArrayList<>();
        for (NetworkPlayerInfo info : infos) {
            UUID id = info.getGameProfile().getId();
            boolean self = id.equals(mc.thePlayer.getUniqueID());
            if (self && !showSelf.getValue()) continue;
            Entry entry = byId.computeIfAbsent(id, k -> new Entry());
            entry.info = info;
            entry.self = self;
            entry.name = info.getGameProfile().getName();
            entry.shown = tabName(info, entry.name);
            entry.teamColor = teamColor(info.getPlayerTeam());

            EntityPlayer player = mc.theWorld.getPlayerEntityByName(entry.name);
            entry.loaded = player != null;
            if (player != null) {
                entry.health = Math.round(player.getHealth() / Math.max(1, player.getMaxHealth()) * 100) + "%";
                entry.distance = self ? "0m" : (int) mc.thePlayer.getDistanceToEntity(player) + "m";
            } else {
                entry.health = "";
                entry.distance = "";
            }
            int ms = info.getResponseTime();
            entry.ping = ms <= 1 ? "-" : ms + "ms";
            entry.pingColor = ms <= 1 ? 0x8A8A8A : ms < 80 ? 0x6FDC97 : ms < 160 ? 0xE8D36A : ms < 250 ? 0xFFA65A : 0xFF6B6B;
            fresh.add(entry);
        }
        entries.clear();
        entries.addAll(fresh);
        byId.values().removeIf(e2 -> !entries.contains(e2));
        clampScroll();
    }

    private static String tabName(NetworkPlayerInfo info, String name) {
        if (info.getDisplayName() != null) return info.getDisplayName().getFormattedText();
        return ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(), name);
    }

    private static int teamColor(ScorePlayerTeam team) {
        if (team == null) return NAME_GREY;
        String format = net.minecraft.client.gui.FontRenderer.getFormatFromString(team.getColorPrefix());
        if (format.length() >= 2) {
            int c = mc.fontRendererObj.getColorCode(format.charAt(1));
            if (c != 0) return c & 0xFFFFFF;
        }
        return NAME_GREY;
    }

    private int maxRows() {
        return maxPlayers.getValue().intValue();
    }

    private void clampScroll() {
        float max = Math.max(0, entries.size() - maxRows()) * row();
        scroll = Math.max(0, Math.min(max, scroll));
    }

    public static boolean scroll(int wheel) {
        PlayerlistModule m = instance;
        if (m == null || !m.isEnabled() || m.entries.size() <= m.maxRows()) return false;
        DraggingProcess.DraggableComponent c = DraggingProcess.components.get(DRAG_KEY);
        if (c == null) return false;
        float[] mouse = mouse();
        if (mouse[0] < c.getX() || mouse[0] > c.getX() + c.getWidth() || mouse[1] < c.getY() || mouse[1] > c.getY() + c.getHeight()) return false;
        m.scroll += wheel > 0 ? -m.row() : m.row();
        m.clampScroll();
        return true;
    }

    private static float[] mouse() {
        ScaledResolution sr = new ScaledResolution(mc);
        return new float[]{Mouse.getX() * sr.getScaledWidth() / (float) mc.displayWidth,
                sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / (float) mc.displayHeight - 1};
    }

    private List<String> headers() {
        List<String> h = new ArrayList<>();
        h.add("Players");
        if (distanceColumn.getValue()) h.add("Dist.");
        if (healthColumn.getValue()) h.add("HP");
        if (pingColumn.getValue()) h.add("Ping");
        return h;
    }

    private String cell(Entry e, int column, List<String> headers) {
        switch (headers.get(column)) {
            case "Dist.":
                return e.distance;
            case "HP":
                return e.health;
            case "Ping":
                return e.ping;
            default:
                return e.shown;
        }
    }

    private static String title(String header) {
        return header.equals("Dist.") ? "DIST" : header.toUpperCase();
    }

    private void layout(List<String> headers) {
        CustomFontRenderer font = font(10), head = font(8);
        float k = k();
        columnWidths = new float[headers.size()];
        float total = 0;
        for (int i = 0; i < headers.size(); i++) {
            float widest = i == 0 ? head.getStringWidth("PLAYERS " + entries.size()) + 8 * k : head.getStringWidth(title(headers.get(i))) + 8 * k;
            for (Entry e : entries) {
                float w;
                if (i == 0) w = 3 * k + 6 * k + 4 * k + font.getStringWidth(e.shown) + 6 * k;
                else if ("Ping".equals(headers.get(i))) w = font.getStringWidth(cell(e, i, headers)) + 8 * k;
                else w = font.getStringWidth(cell(e, i, headers)) + 8 * k;
                widest = Math.max(widest, w);
            }
            columnWidths[i] = widest;
            total += widest;
        }
        width = total;
        height = Math.min(entries.size(), maxRows()) * row();
    }

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> render(false);

    @EventLink
    public Listener<ShaderEvent> shaderEventListener = e -> render(true);

    private DraggingProcess.DraggableComponent component() {
        return DraggingProcess.components.computeIfAbsent(DRAG_KEY, k -> new DraggingProcess.DraggableComponent(2, 51));
    }

    private void render(boolean shapeOnly) {
        if (mc.thePlayer == null || entries.isEmpty()) return;
        List<String> headers = headers();
        layout(headers);
        DraggingProcess.DraggableComponent c = component();
        c.setWidth(width);
        float ROW = row(), HEADER = header(), k = k();
        c.setHeight(height + HEADER);
        float x = (float) c.getX(), y = (float) c.getY();

        if (shapeOnly) {
            Glyphs.bar(x, y, width, height + HEADER, ColorProcess.getColor());
            return;
        }

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        shownScroll += (scroll - shownScroll) * (1 - (float) Math.exp(-dt / 45f));

        int bg = Math.max(0, Math.min(255, opacity.getValue().intValue()));
        Color client = ColorProcess.getColor();

        Glyphs.bar(x, y, width, height + HEADER, new Color(14, 15, 19, bg));
        Glyphs.bar(x, y, width, HEADER, new Color(255, 255, 255, Math.round(bg * 0.06f)));
        float line = Math.max(0.5f, 0.5f * k);
        Glyphs.bar(x, y + HEADER - line, width, line, client);

        CustomFontRenderer font = font(10), head = font(8);
        float top = y + HEADER;
        float pad = 4 * k;

        float cx = x;
        float titleY = y + (HEADER - head.getHeight()) / 2f - 0.25f * k;
        for (int i = 0; i < headers.size(); i++) {
            if (i == 0) {
                float tx = cx + 3 * k;
                head.drawStringWithShadow("PLAYERS", tx, titleY, 0xFF9DA1AB);
                head.drawStringWithShadow(String.valueOf(entries.size()), tx + head.getStringWidth("PLAYERS "), titleY, client.getRGB() | 0xFF000000);
            } else {
                String t = title(headers.get(i));
                head.drawStringWithShadow(t, cx + columnWidths[i] - pad - head.getStringWidth(t), titleY, 0xFF9DA1AB);
            }
            cx += columnWidths[i];
        }

        RenderUtils.startScissor(x, top, width, height);
        float rowY = top - shownScroll;
        int index = 0;
        for (Entry e : entries) {
            if (rowY + ROW >= top && rowY <= top + height) {
                if (index % 2 == 1) Glyphs.bar(x, rowY, width, ROW, new Color(255, 255, 255, 9));
                if (e.self) {
                    Glyphs.bar(x, rowY, width, ROW, new Color(client.getRed(), client.getGreen(), client.getBlue(), 26));
                    Glyphs.bar(x, rowY, Math.max(1, k), ROW, client);
                }

                float textY = rowY + (ROW - font.getHeight()) / 2f;
                int alpha = e.loaded ? 0xFF : 0xA0;
                float colX = x;
                for (int i = 0; i < headers.size(); i++) {
                    if (i == 0) {
                        float hs = 6 * k;
                        drawHead(e.info, colX + 3 * k, rowY + (ROW - hs) / 2f, hs, e.loaded || e.self ? 1 : 0.6f);
                        int nameRgb = e.self && selfHighlighting.getValue() ? client.getRGB() & 0xFFFFFF : e.teamColor;
                        int nameAlpha = e.loaded || e.self ? 0xFF : 0xB4;

                        String shownName = e.self && selfHighlighting.getValue() ? net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(e.shown) : e.shown;
                        font.drawStringWithShadow(shownName, colX + 3 * k + hs + 4 * k, textY, (nameAlpha << 24) | nameRgb);
                    } else {
                        String text = cell(e, i, headers);
                        float right = colX + columnWidths[i] - pad;
                        float tx = right - font.getStringWidth(text);
                        if ("Ping".equals(headers.get(i))) {
                            font.drawStringWithShadow(text, tx, textY, (alpha << 24) | 0xD5D7DD);
                        } else {
                            int rgb = "HP".equals(headers.get(i)) ? 0xE4E4E4 : 0xC8C8C8;
                            font.drawStringWithShadow(text, tx, textY, (alpha << 24) | rgb);
                        }
                    }
                    colX += columnWidths[i];
                }
            }
            rowY += ROW;
            index++;
        }
        RenderUtils.endScissor();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void drawHead(NetworkPlayerInfo info, float x, float y, float size, float alpha) {
        ResourceLocation skin = info.getLocationSkin();
        if (skin == null) skin = DefaultPlayerSkin.getDefaultSkin(info.getGameProfile().getId());
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, alpha);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(size / 8f, size / 8f, 1);
        Gui.drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        Gui.drawScaledCustomSizeModalRect(0, 0, 40, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }
}
