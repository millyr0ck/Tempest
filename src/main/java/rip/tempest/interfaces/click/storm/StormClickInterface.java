package rip.tempest.interfaces.click.storm;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.MultiModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.interfaces.click.ColorPicker;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.misc.LocalConfigFetcher;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public final class StormClickInterface extends GuiScreen {
    private static final float WIN_W = 520, WIN_H = 356, RAIL_W = 96, LIST_W = 140;
    private static final float TOP = 30, TAB_H = 19, TAB_STEP = 20, ROW_H = 18, ROW_STEP = 19;
    private static final float BOX_PAD = 7, BOX_GAP = 6, CHECK = 6;

    private static final Color WINDOW = new Color(11, 11, 13, 250);
    private static final Color BOX = new Color(14, 14, 17, 255);
    private static final Color BOX_EDGE = new Color(36, 36, 42, 255);
    private static final Color RULE = new Color(27, 27, 32, 255);
    private static final Color SELECT = new Color(22, 22, 26, 255);
    private static final Color FIELD = new Color(27, 27, 32, 255);
    private static final Color FIELD_HOVER = new Color(33, 33, 39, 255);
    private static final Color FIELD_EDGE = new Color(46, 46, 53, 255);
    private static final Color CHECK_OFF = new Color(44, 44, 50, 255);
    private static final Color TRACK = new Color(38, 38, 43, 255);
    private static final int TEXT = 0xE6E6EA, TEXT_2 = 0xB9B9C1, TEXT_3 = 0x8C8C96, TEXT_4 = 0x6E6E78;

    private static final float FAST = 22f, MEDIUM = 14f, SLOW = 9f;

    private enum Tab {
        COMBAT("combat", ModuleCategory.COMBAT), MOVEMENT("movement", ModuleCategory.MOVEMENT),
        PLAYER("player", ModuleCategory.PLAYER), VISUALS("visuals", ModuleCategory.VISUALS),
        CLIENT("client", ModuleCategory.CLIENT), THEMES("themes", null), CONFIG("config", null);

        final String label;
        final ModuleCategory category;

        Tab(String label, ModuleCategory category) {
            this.label = label;
            this.category = category;
        }
    }

    private static Tab tab = Tab.COMBAT;
    private static String search = "";
    private static float winX = Float.NaN, winY;
    private static final Map<Tab, String> selectedModule = new EnumMap<>(Tab.class);
    private static String selectedSearch;
    private static String selectedConfig;
    private static final Map<String, Float> scrollTarget = new HashMap<>();

    private final Map<Object, Float> anims = new HashMap<>();
    private final Map<Property<?>, ColorPicker> pickers = new HashMap<>();
    private final Set<Property<?>> openColors = new HashSet<>();
    private final Set<Property<?>> openLists = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<float[]> clips = new ArrayDeque<>();
    private long lastFrame;
    private float delta;
    private boolean closing;
    private int mouseX, mouseY;

    private boolean searchFocused, configFieldFocused, bindingMenuKey;
    private Module binding;
    private Property<?> editing;
    private String editBuffer = "", configName = "";
    private String confirmDelete, armedDelete;
    private List<String> configs;
    private String configStatus = "";
    private long configStatusAt;

    private NumberProperty dragSlider;
    private float dragSliderX, dragSliderW;
    private ColorPicker dragPicker;
    private Property<?> dragPickerProperty;
    private int dragPickerX, dragPickerY;
    private boolean dragWindow;
    private float dragOffX, dragOffY;

    private String hoverText;
    private Object hoverKey;
    private long hoverSince;

    private float listX, listY, listH, listContent, paneX, paneY, paneH, paneContent;

    private CustomFontRenderer body, bold, small;

    private static final class Hit {
        final float x, y, w, h;
        final IntConsumer action;

        Hit(float x, float y, float w, float h, IntConsumer action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        closing = false;
        lastFrame = 0L;
        configs = null;
        body = font("verdana", 17, Font.PLAIN);
        bold = font("verdana", 17, Font.BOLD);
        small = font("verdana", 15, Font.PLAIN);
        anims.put("list", 0f);
        anims.put("pane", 0f);
    }

    private static CustomFontRenderer font(String file, float size, int style) {
        CustomFontRenderer f = null;
        try {
            f = FontProcess.getStyledFont(file, size, style);
        } catch (Exception ignored) {
        }
        return f != null ? f : FontProcess.getFont("tempest");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        binding = null;
        bindingMenuKey = false;
        editing = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private float anim(Object key, float target, float speed) {
        Float current = anims.get(key);
        if (current == null) {
            anims.put(key, target);
            return target;
        }
        float next = current + (target - current) * (1f - (float) Math.exp(-delta * speed));
        if (Math.abs(next - target) < 0.0005f) next = target;
        anims.put(key, next);
        return next;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOut(float t) {
        float u = 1f - clamp(t);
        return 1f - u * u * u;
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * clamp(a)));
    }

    private static Color mix(Color a, Color b, float t) {
        t = clamp(t);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static int text(int rgb, float a) {
        return (Math.max(5, Math.round(255 * clamp(a))) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mixRgb(int a, int b, float t) {
        return mix(new Color(a), new Color(b), t).getRGB() & 0xFFFFFF;
    }

    private Color accent() {
        Color c = ColorProcess.getColor();
        return c == null ? new Color(236, 184, 239) : c;
    }

    private void pushClip(float x, float y, float w, float h) {
        float[] r = {x, y, x + w, y + h};
        if (!clips.isEmpty()) {
            float[] p = clips.peek();
            r = new float[]{Math.max(r[0], p[0]), Math.max(r[1], p[1]), Math.min(r[2], p[2]), Math.min(r[3], p[3])};
        }
        clips.push(r);
        applyClip(r);
    }

    private void popClip() {
        clips.pop();
        if (clips.isEmpty()) RenderUtils.endScissor();
        else applyClip(clips.peek());
    }

    private static void applyClip(float[] r) {
        RenderUtils.startScissor(r[0], r[1], Math.max(0, r[2] - r[0]), Math.max(0, r[3] - r[1]));
    }

    private boolean over(float x, float y, float w, float h) {
        if (closing || mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) return false;
        if (clips.isEmpty()) return true;
        float[] c = clips.peek();
        return mouseX >= c[0] && mouseX <= c[2] && mouseY >= c[1] && mouseY <= c[3];
    }

    private void hit(float x, float y, float w, float h, IntConsumer action) {
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (!clips.isEmpty()) {
            float[] c = clips.peek();
            x0 = Math.max(x0, c[0]);
            y0 = Math.max(y0, c[1]);
            x1 = Math.min(x1, c[2]);
            y1 = Math.min(y1, c[3]);
        }
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, action));
    }

    private void tooltip(Object key, String text) {
        if (text == null || text.isEmpty()) return;
        if (key != hoverKey) {
            hoverKey = key;
            hoverSince = System.currentTimeMillis();
        }
        hoverText = text;
    }

    private static void fill(float x, float y, float w, float h, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, h, c);
    }

    private static void frame(float x, float y, float w, float h, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, 1, c);
        Glyphs.bar(x, y + h - 1, w, 1, c);
        Glyphs.bar(x, y + 1, 1, h - 2, c);
        Glyphs.bar(x + w - 1, y + 1, 1, h - 2, c);
    }

    private static void checkbox(float x, float y, float on, float a, Color accent, boolean hover) {
        fill(x, y, CHECK, CHECK, alpha(mix(CHECK_OFF, accent, easeOut(on)), a));
        if (hover && on < 0.5f) frame(x, y, CHECK, CHECK, alpha(new Color(74, 74, 82), a));
    }

    private void string(CustomFontRenderer f, String s, float x, float y, int rgb, float a) {
        if (a < 0.03f || s == null || s.isEmpty()) return;
        f.drawString(s, x, y, text(rgb, a));
    }

    private void stringMid(CustomFontRenderer f, String s, float x, float y, float h, int rgb, float a) {
        string(f, s, x, y + (h - f.getHeight()) / 2f, rgb, a);
    }

    private static String trim(CustomFontRenderer f, String s, float width) {
        if (f.getStringWidth(s) <= width) return s;
        String t = s;
        while (t.length() > 1 && f.getStringWidth(t + "...") > width) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private boolean caretOn() {
        return System.currentTimeMillis() % 1000 < 520;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = System.currentTimeMillis();
        delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        mouseX = mx;
        mouseY = my;
        hits.clear();
        hoverText = null;

        if (Float.isNaN(winX)) {
            winX = (width - WIN_W) / 2f;
            winY = (height - WIN_H) / 2f;
        }
        updateDrags();

        float open = anim("open", closing ? 0f : 1f, closing ? 18f : 11f);
        if (closing && open < 0.02f) {
            mc.displayGuiScreen(mc.theWorld == null ? new rip.tempest.interfaces.menu.main.CustomMainMenu() : null);
            return;
        }
        float a = easeOut(open);

        if (mc.theWorld == null) drawDefaultBackground();
        Glyphs.bar(0, 0, width, height, new Color(0, 0, 0, Math.round(90 * a)));

        float x = Math.round(winX), y = Math.round(winY + (1f - a) * 8f);
        Color accent = accent();

        fill(x - 1, y - 1, WIN_W + 2, WIN_H + 2, new Color(0, 0, 0, Math.round(90 * a)));
        fill(x, y, WIN_W, WIN_H, alpha(WINDOW, a));
        frame(x, y, WIN_W, WIN_H, alpha(new Color(30, 30, 35), a));
        fill(x, y, WIN_W, 1, alpha(accent, a));
        fill(x + RAIL_W, y + 1, 1, WIN_H - 2, alpha(RULE, a));
        fill(x + RAIL_W + LIST_W, y + 1, 1, WIN_H - 2, alpha(RULE, a));

        drawRail(x, y, a, accent);
        drawList(x + RAIL_W + 1, y, LIST_W - 1, a, accent);
        drawPane(x + RAIL_W + LIST_W + 1, y, WIN_W - RAIL_W - LIST_W - 1, a, accent);
        drawTooltip(a);

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void startWindowDrag() {
        dragWindow = true;
        dragOffX = mouseX - winX;
        dragOffY = mouseY - winY;
    }

    private void updateDrags() {
        if (!Mouse.isButtonDown(0)) {
            dragSlider = null;
            dragWindow = false;
            if (dragPicker != null) {
                dragPicker.mouseReleased();
                dragPicker = null;
            }
            return;
        }
        if (dragWindow) {
            winX = Math.max(-WIN_W + 60, Math.min(width - 60, mouseX - dragOffX));
            winY = Math.max(0, Math.min(height - 30, mouseY - dragOffY));
        }
        if (dragSlider != null) setSlider(dragSlider, (mouseX - dragSliderX) / dragSliderW);
        if (dragPicker != null) {
            dragPicker.mouseDragged(dragPickerX, dragPickerY, mouseX, mouseY);
            setColor(dragPickerProperty, dragPicker.getColor());
        }
    }

    private void drawRail(float x, float y, float a, Color accent) {
        hit(x, y, RAIL_W, 24, b -> startWindowDrag());
        fill(x + 10, y + 11, 4, 4, alpha(accent, a));
        stringMid(bold, "tempest", x + 18, y + 6, 14, TEXT, a);

        float sx = x + 6, sy = y + 26, sw = RAIL_W - 12, sh = 14;
        boolean hover = over(sx, sy, sw, sh);
        float focus = anim("searchF", searchFocused ? 1f : 0f, FAST);
        fill(sx, sy, sw, sh, alpha(new Color(20, 20, 24), a));
        frame(sx, sy, sw, sh, alpha(mix(new Color(42, 42, 48), new Color(74, 74, 82), Math.max(focus, hover ? 0.5f : 0f)), a));
        Glyphs.ring(sx + 6.5f, sy + sh / 2f - 0.5f, 2.6f, 1f, 0f, 360f, alpha(new Color(TEXT_3), a));
        Glyphs.stroke(sx + 8.4f, sy + sh / 2f + 1.4f, sx + 9.9f, sy + sh / 2f + 2.9f, 1f, alpha(new Color(TEXT_3), a));
        float tx = sx + 13;
        if (search.isEmpty() && !searchFocused) stringMid(small, "search", tx, sy, sh, TEXT_4, a);
        else {
            String shown = trim(small, search, sw - 26);
            stringMid(small, shown, tx, sy, sh, TEXT, a);
            if (searchFocused && caretOn()) fill(tx + small.getStringWidth(shown) + 1, sy + 3.5f, 1, sh - 7, alpha(new Color(TEXT), a));
        }
        hit(sx, sy, sw, sh, b -> focusOnly("search"));
        if (!search.isEmpty()) {
            float cx = sx + sw - 6, cy = sy + sh / 2f;
            Color c = alpha(new Color(over(cx - 5, cy - 5, 10, 10) ? TEXT : TEXT_3), a);
            Glyphs.stroke(cx - 2, cy - 2, cx + 2, cy + 2, 1f, c);
            Glyphs.stroke(cx - 2, cy + 2, cx + 2, cy - 2, 1f, c);
            hit(cx - 5, cy - 5, 10, 10, b -> {
                search = "";
                anims.put("list", 0f);
                anims.put("pane", 0f);
            });
        }

        boolean searching = !search.isEmpty();
        float tabsY = y + 48;
        Tab[] tabs = Tab.values();
        float indY = anim("tabInd", tab.ordinal() * TAB_STEP, MEDIUM);
        float indA = anim("tabIndA", searching ? 0f : 1f, FAST) * a;
        fill(x + 6, tabsY + indY, RAIL_W - 12, TAB_H, alpha(SELECT, indA));
        fill(x + 6, tabsY + indY + 5, 1, TAB_H - 10, alpha(accent, indA));
        for (Tab t : tabs) {
            float ty = tabsY + t.ordinal() * TAB_STEP;
            boolean cur = !searching && t == tab;
            boolean th = over(x + 6, ty, RAIL_W - 12, TAB_H);
            float sel = anim("tab:" + t, cur ? 1f : 0f, FAST);
            float hv = anim("tabH:" + t, th ? 1f : 0f, FAST);
            int rgb = mixRgb(mixRgb(TEXT_3, TEXT, hv * 0.7f), TEXT, sel);
            stringMid(sel > 0.5f ? bold : body, t.label, x + 14, ty, TAB_H, rgb, a);
            String count = count(t);
            stringMid(small, count, x + RAIL_W - 11 - small.getStringWidth(count), ty, TAB_H, TEXT_4, a);
            hit(x + 6, ty, RAIL_W - 12, TAB_H, b -> openTab(t));
        }

        int enabled = 0;
        for (Module m : Tempest.INSTANCE.getModuleManager().getModules()) if (m.isEnabled()) enabled++;
        String keyName = Keyboard.getKeyName(ClientSettingsModule.menuKey.getValue().intValue());
        string(small, enabled + " enabled", x + 10, y + WIN_H - 26, TEXT_3, a);
        string(small, lower(keyName == null ? "rshift" : keyName) + " to close", x + 10, y + WIN_H - 15, TEXT_4, a);
    }

    private String count(Tab t) {
        if (t == Tab.THEMES) return rip.tempest.processes.Themes.active() ? "on" : "off";
        if (t.category == null) {
            if (configs == null) configs = LocalConfigFetcher.fetchConfigList();
            return String.valueOf(configs.size());
        }
        List<Module> mods = Tempest.INSTANCE.getModuleManager().getModulesForCategory(t.category);
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;
        return on + "/" + mods.size();
    }

    private void openTab(Tab t) {
        if (t == tab && search.isEmpty()) return;
        tab = t;

        search = "";
        searchFocused = false;
        configs = null;
        anims.put("list", 0f);
        anims.put("pane", 0f);
    }

    private void focusOnly(String which) {
        searchFocused = "search".equals(which);
        configFieldFocused = "config".equals(which);
        if (editing != null && !"edit".equals(which)) commitEdit();
    }

    private List<Module> modules(ModuleCategory category) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(category));
        list.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return list;
    }

    private List<Module> searchResults() {
        String q = search.toLowerCase().replace(" ", "");
        List<Module> out = new ArrayList<>();
        for (Module m : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (m.getLabel().toLowerCase().replace(" ", "").contains(q)) out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return out;
    }

    private boolean showingConfigs() {
        return search.isEmpty() && tab == Tab.CONFIG;
    }

    private boolean showingThemes() {
        return search.isEmpty() && tab == Tab.THEMES;
    }

    private static final String THEMES_PANE = "pane:themes";

    private static List<Property<?>> themeSettings() {
        List<Property<?>> out = new ArrayList<>();
        out.add(ClientSettingsModule.themeSpeed);
        out.add(ClientSettingsModule.themeLength);
        out.add(ClientSettingsModule.themeSaturation);
        out.add(ClientSettingsModule.themeDirection);
        if (rip.tempest.processes.Themes.current() == rip.tempest.processes.Themes.Preset.Custom) {
            out.add(ClientSettingsModule.pickerColor1);
            out.add(ClientSettingsModule.pickerColor2);
        }
        return out;
    }

    private void drawThemeList(float x, float y, float w, float a, Color accent) {
        rip.tempest.processes.Themes.Preset[] presets = rip.tempest.processes.Themes.Preset.values();
        boolean active = rip.tempest.processes.Themes.active();
        String sub = active ? lower(rip.tempest.processes.Themes.current().toString()) : "off";
        hit(x, y, w, TOP - 4, b -> startWindowDrag());
        stringMid(bold, "themes", x + 10, y + 12, 14, TEXT, a);
        stringMid(small, trim(small, sub, w - 60), x + w - 9 - small.getStringWidth(trim(small, sub, w - 60)), y + 12, 14, active ? accent.getRGB() & 0xFFFFFF : TEXT_3, a);

        float top = y + TOP, h = WIN_H - TOP - 6;
        listX = x;
        listY = top;
        listH = h;
        listContent = presets.length * ROW_STEP + 4;
        float scroll = scroll(listKey(), listContent, h);
        int selIdx = active ? rip.tempest.processes.Themes.current().ordinal() : -1;

        float listIn = anim("list", 1f, SLOW);
        pushClip(x, top, w, h);
        float rowsY = top - scroll;
        if (selIdx >= 0) {
            float ind = anim("modInd:" + listKey(), selIdx * ROW_STEP, MEDIUM);
            fill(x + 5, rowsY + ind, w - 10, ROW_H, alpha(SELECT, a));
            frame(x + 5, rowsY + ind, w - 10, ROW_H, alpha(BOX_EDGE, a));
        }
        for (int i = 0; i < presets.length; i++) {
            float ry = rowsY + i * ROW_STEP;
            if (ry > top + h || ry + ROW_H < top) continue;
            float ra = a * easeOut(clamp(listIn * 1.8f - Math.min(i, 14) * 0.06f));
            float slide = (1f - ra / Math.max(a, 0.001f)) * -5f;
            drawPresetRow(presets[i], x + 5 + slide, ry, w - 10, i == selIdx, ra, accent);
        }
        popClip();
    }

    private void drawPresetRow(rip.tempest.processes.Themes.Preset preset, float x, float y, float w, boolean current, float a, Color accent) {
        boolean hover = over(x, y, w, ROW_H);
        float hv = anim("preH:" + preset, hover ? 1f : 0f, FAST);
        if (!current && hv > 0.01f) fill(x, y, w, ROW_H, alpha(new Color(255, 255, 255), 0.025f * hv * a));
        hit(x, y, w, ROW_H, b -> rip.tempest.processes.Themes.select(preset));
        checkbox(x + 6, y + (ROW_H - CHECK) / 2f, current ? 1f : 0f, a, accent, false);
        float sw = 30, sh = 5, sx = x + w - 6 - sw, sy = y + (ROW_H - sh) / 2f;
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            Color c0 = new Color(rip.tempest.processes.Themes.sample(preset, i / (double) steps * 0.5));
            Color c1 = new Color(rip.tempest.processes.Themes.sample(preset, (i + 1) / (double) steps * 0.5));
            Glyphs.gradient(sx + sw * i / steps, sy, sw / steps + 0.3f, sh, alpha(c0, a), alpha(c1, a));
        }
        CustomFontRenderer f = current ? bold : body;
        stringMid(f, trim(f, lower(preset.toString()), w - 30 - sw), x + 17, y, ROW_H, current ? TEXT : mixRgb(TEXT_3, TEXT, hv * 0.6f), a);
    }

    private void drawThemesPane(float x, float y, float w, float a, Color accent, float shift) {
        boolean active = rip.tempest.processes.Themes.active();
        stringMid(bold, active ? lower(rip.tempest.processes.Themes.current().toString()) : "theme", x + 10 + shift, y + 12, 14, TEXT, a);
        String state = active ? "in use" : "pick a preset to use it";
        stringMid(small, state, x + w - 10 - small.getStringWidth(state), y + 12, 14, active ? accent.getRGB() & 0xFFFFFF : TEXT_3, a);

        float top = y + TOP, h = WIN_H - TOP - 6;
        paneX = x;
        paneY = top;
        paneH = h;
        float scroll = scroll(THEMES_PANE, paneContent, h);

        pushClip(x, top, w - 1, h);
        float bx = x + 8 + shift, bw = w - 16, cy = top - scroll;
        cy += drawBox("settings", themeSettings(), 2, bx, cy, bw, boxA(a, 0), accent) + BOX_GAP;

        rip.tempest.modules.impl.visuals.ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
        if (list != null) cy += drawPatternBox(list, bx, cy, bw, boxA(a, 1), accent, top, top + h) + BOX_GAP;
        cy -= BOX_GAP;
        popClip();
        paneContent = cy - (top - scroll) + 6;
        scrollBar(THEMES_PANE, x + w - 4, top, h, paneContent, a, accent);
    }

    private float drawPatternBox(rip.tempest.modules.impl.visuals.ArrayListModule list, float x, float y, float w, float a, Color accent, float clipTop, float clipBottom) {
        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        int columns = 3;
        float gap = 6, cellH = 30;
        float inner = w - BOX_PAD * 2, cellW = (inner - gap * (columns - 1)) / columns;
        int rows = (patterns.length + columns - 1) / columns;
        float boxH = 16 + rows * (cellH + gap) - gap + BOX_PAD;

        fill(x, y, w, boxH, alpha(BOX, a));
        frame(x, y, w, boxH, alpha(BOX_EDGE, a));
        String title = "array list patterns";
        stringMid(small, title, x + (w - small.getStringWidth(title)) / 2f, y + 2, 12, TEXT_3, a);

        double t = list.patternTime();
        for (int i = 0; i < patterns.length; i++) {
            rip.tempest.utils.render.ArrayPattern pattern = patterns[i];
            float cx = x + BOX_PAD + (i % columns) * (cellW + gap), cy = y + 16 + (i / columns) * (cellH + gap);
            if (cy > clipBottom || cy + cellH < clipTop) continue;
            boolean chosen = list.pattern.getValue() == pattern;
            boolean hover = over(cx, cy, cellW, cellH);
            float hv = anim("patH:" + pattern, hover ? 1f : 0f, FAST);
            fill(cx, cy, cellW, cellH, alpha(mix(FIELD, FIELD_HOVER, hv), a));
            frame(cx, cy, cellW, cellH, alpha(chosen ? accent : FIELD_EDGE, a));

            float sx = cx + 3, sy = cy + 3, sw = cellW - 6, sh = 13;
            if (pattern == rip.tempest.utils.render.ArrayPattern.Off) {
                fill(sx, sy, sw, sh, alpha(TRACK, a));
            } else {
                float block = 2;
                for (float bx = 0; bx < sw; bx += block) {
                    for (float by = 0; by < sh; by += block) {
                        int c = pattern.color(bx + 1, by + 1, sw, sh, t, list.motion());
                        fill(sx + bx, sy + by, Math.min(block, sw - bx), Math.min(block, sh - by), alpha(new Color(c & 0xFFFFFF), a));
                    }
                }
            }
            String label = pattern == rip.tempest.utils.render.ArrayPattern.Off ? "none" : lower(pattern.toString());
            CustomFontRenderer f = chosen ? bold : small;
            stringMid(f, trim(f, label, cellW - 8), cx + 4, cy + 16, 13, chosen ? TEXT : mixRgb(TEXT_3, TEXT, hv * 0.6f), a);
            hit(cx, cy, cellW, cellH, b -> list.pattern.setValue(pattern));
        }
        return boxH;
    }

    private List<Module> shownModules() {
        return search.isEmpty() ? modules(tab.category) : searchResults();
    }

    private Module selected(List<Module> list) {
        String id = search.isEmpty() ? selectedModule.get(tab) : selectedSearch;
        for (Module m : list) if (m.getLabel().equals(id)) return m;
        return list.isEmpty() ? null : list.get(0);
    }

    private void select(Module m) {
        String before = search.isEmpty() ? selectedModule.get(tab) : selectedSearch;
        if (search.isEmpty()) selectedModule.put(tab, m.getLabel());
        else selectedSearch = m.getLabel();
        if (!m.getLabel().equals(before)) {
            anims.put("pane", 0f);
            openLists.clear();
            openColors.clear();
        }
    }

    private String listKey() {
        return search.isEmpty() ? "list:" + tab : "list:search";
    }

    private void drawList(float x, float y, float w, float a, Color accent) {
        if (showingThemes()) {
            drawThemeList(x, y, w, a, accent);
            return;
        }
        boolean searching = !search.isEmpty();
        boolean cfg = showingConfigs();
        List<Module> mods = cfg ? new ArrayList<>() : shownModules();
        if (cfg && configs == null) configs = LocalConfigFetcher.fetchConfigList();
        int size = cfg ? configs.size() : mods.size();

        String heading = searching ? "search" : tab.label;
        String sub;
        if (searching) sub = size + " found";
        else if (cfg) sub = size + " saved";
        else {
            int on = 0;
            for (Module m : mods) if (m.isEnabled()) on++;
            sub = on + " of " + size + " on";
        }
        hit(x, y, w, TOP - 4, b -> startWindowDrag());
        stringMid(bold, heading, x + 10, y + 12, 14, TEXT, a);
        stringMid(small, sub, x + w - 9 - small.getStringWidth(sub), y + 12, 14, TEXT_3, a);

        float top = y + TOP, h = WIN_H - TOP - 6;
        listX = x;
        listY = top;
        listH = h;
        listContent = size * ROW_STEP + 4;
        float scroll = scroll(listKey(), listContent, h);

        int selIdx = -1;
        Module sel = cfg ? null : selected(mods);
        if (cfg) {
            for (int i = 0; i < configs.size(); i++) if (configs.get(i).equals(selectedConfig)) selIdx = i;
            if (selIdx < 0 && !configs.isEmpty()) selIdx = 0;
        } else if (sel != null) selIdx = mods.indexOf(sel);

        float listIn = anim("list", 1f, SLOW);
        pushClip(x, top, w, h);
        float rowsY = top - scroll;
        if (selIdx >= 0) {
            float ind = anim("modInd:" + listKey(), selIdx * ROW_STEP, MEDIUM);
            fill(x + 5, rowsY + ind, w - 10, ROW_H, alpha(SELECT, a));
            frame(x + 5, rowsY + ind, w - 10, ROW_H, alpha(BOX_EDGE, a));
        }
        if (size == 0) stringMid(body, searching ? "nothing matches" : "nothing here yet", x + 12, top + 6, ROW_H, TEXT_3, a);

        for (int i = 0; i < size; i++) {
            float ry = rowsY + i * ROW_STEP;
            if (ry > top + h || ry + ROW_H < top) continue;

            float ra = a * easeOut(clamp(listIn * 1.8f - Math.min(i, 14) * 0.06f));
            float slide = (1f - ra / Math.max(a, 0.001f)) * -5f;
            if (cfg) drawConfigRow(configs.get(i), x + 5 + slide, ry, w - 10, i == selIdx, ra, accent);
            else drawModuleRow(mods.get(i), x + 5 + slide, ry, w - 10, mods.get(i) == sel, searching, ra, accent);
        }
        popClip();
    }

    private void drawModuleRow(Module m, float x, float y, float w, boolean current, boolean searching, float a, Color accent) {
        String id = m.getLabel();
        float on = anim("on:" + id, m.isEnabled() ? 1f : 0f, MEDIUM);
        boolean hover = over(x, y, w, ROW_H);
        float hv = anim("rowH:" + id, hover ? 1f : 0f, FAST);
        if (!current && hv > 0.01f) fill(x, y, w, ROW_H, alpha(new Color(255, 255, 255), 0.025f * hv * a));

        hit(x, y, w, ROW_H, b -> {
            if (b == 0) select(m);
            else if (b == 1) m.toggle();
            else if (b == 2) startBinding(m);
        });
        if (hover) tooltip(m, m.getDescription());

        float cx = x + 6, cy = y + (ROW_H - CHECK) / 2f;
        boolean checkHover = over(x, y, 17, ROW_H);
        checkbox(cx, cy, on, a, accent, checkHover);
        hit(x, y, 17, ROW_H, b -> m.toggle());

        String right = searching ? lower(categoryLabel(m)) : (binding == m ? "[...]" : m.getKey() > 0 ? "[" + Keyboard.getKeyName(m.getKey()) + "]" : "");
        float rw = right.isEmpty() ? 0 : small.getStringWidth(right) + 6;
        int nameRgb = mixRgb(TEXT_3, TEXT, Math.max(Math.max(on, current ? 1f : 0f), hv * 0.6f));
        stringMid(current ? bold : body, trim(current ? bold : body, lower(id), w - 24 - rw), x + 17, y, ROW_H, nameRgb, a);
        if (!right.isEmpty()) {
            int rgb = binding == m ? accent.getRGB() & 0xFFFFFF : TEXT_4;
            float ra = binding == m ? a * (0.55f + 0.45f * pulse()) : a;
            stringMid(small, right, x + w - 6 - small.getStringWidth(right), y, ROW_H, rgb, ra);
        }
    }

    private static String categoryLabel(Module m) {
        for (Tab t : Tab.values()) if (t.category != null && t.category == m.getCategory()) return t.label;
        return "";
    }

    private void drawConfigRow(String name, float x, float y, float w, boolean current, float a, Color accent) {
        boolean hover = over(x, y, w, ROW_H);
        float hv = anim("cfgH:" + name, hover ? 1f : 0f, FAST);
        if (!current && hv > 0.01f) fill(x, y, w, ROW_H, alpha(new Color(255, 255, 255), 0.025f * hv * a));
        hit(x, y, w, ROW_H, b -> {
            if (!name.equals(selectedConfig)) anims.put("pane", 0f);
            selectedConfig = name;
        });
        checkbox(x + 6, y + (ROW_H - CHECK) / 2f, current ? 1f : 0f, a, accent, false);
        stringMid(current ? bold : body, trim(body, lower(name), w - 24), x + 17, y, ROW_H, current ? TEXT : mixRgb(TEXT_3, TEXT, hv * 0.6f), a);
    }

    private float pulse() {
        return 0.5f + 0.5f * (float) Math.cos(System.currentTimeMillis() / 1000.0 * Math.PI * 2);
    }

    private void startBinding(Module m) {
        binding = binding == m ? null : m;
        bindingMenuKey = false;
        searchFocused = false;
    }

    private float scroll(String key, float content, float view) {
        float max = Math.max(0, content - view);
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(key, 0f)));
        scrollTarget.put(key, target);
        return anim("scroll:" + key, target, MEDIUM);
    }

    private void scrollBar(String key, float x, float y, float h, float content, float a, Color accent) {
        float max = Math.max(0, content - h);
        if (max <= 0) return;
        float s = anims.getOrDefault("scroll:" + key, 0f);
        float thumbH = Math.max(18, h * h / content);
        float thumbY = y + (h - thumbH) * (s / max);
        fill(x, thumbY, 2, thumbH, alpha(new Color(46, 46, 53), a));
    }

    private String paneKey(Module m) {
        return "pane:" + (m == null ? "cfg" : m.getLabel());
    }

    private void drawPane(float x, float y, float w, float a, Color accent) {
        float paneIn = easeOut(anim("pane", 1f, SLOW));
        float pa = a * paneIn;
        float shift = (1f - paneIn) * 8f;
        hit(x, y, w, TOP - 4, b -> startWindowDrag());

        if (showingConfigs()) {
            drawConfigPane(x + shift, y, w, pa, accent);
            return;
        }
        if (showingThemes()) {
            drawThemesPane(x, y, w, pa, accent, shift);
            return;
        }
        List<Module> mods = shownModules();
        Module m = selected(mods);
        if (m == null) return;

        float hx = x + 10 + shift;
        stringMid(bold, lower(m.getLabel()), hx, y + 12, 14, TEXT, pa);
        boolean binding = this.binding == m;
        String key = binding ? "[...]" : "[" + (m.getKey() > 0 ? Keyboard.getKeyName(m.getKey()) : "none") + "]";
        float kw = small.getStringWidth(key), kx = x + w - 10 - kw;
        boolean kHover = over(kx - 2, y + 10, kw + 4, 18);
        stringMid(small, key, kx, y + 12, 14, binding ? accent.getRGB() & 0xFFFFFF : kHover ? TEXT : TEXT_3, binding ? pa * (0.55f + 0.45f * pulse()) : pa);
        hit(kx - 2, y + 10, kw + 4, 18, b -> startBinding(m));

        float on = anim("on:" + m.getLabel(), m.isEnabled() ? 1f : 0f, MEDIUM);
        String en = "enabled";
        float ew = (on > 0.5f ? bold : body).getStringWidth(en), ex = kx - 12 - ew - CHECK - 5;
        boolean eHover = over(ex - 2, y + 10, ew + CHECK + 9, 18);
        checkbox(ex, y + 12 + (14 - CHECK) / 2f, on, pa, accent, eHover);
        stringMid(on > 0.5f ? bold : body, en, ex + CHECK + 5, y + 12, 14, mixRgb(TEXT_3, TEXT, Math.max(on, eHover ? 0.6f : 0f)), pa);
        hit(ex - 2, y + 10, ew + CHECK + 9, 18, b -> m.toggle());

        List<Property<?>> general = new ArrayList<>(), values = new ArrayList<>(), options = new ArrayList<>();
        for (Property<?> p : m.getElements()) {
            if (!p.isAvailable()) continue;
            if (isColor(p) || p.getValue() instanceof Boolean) options.add(p);
            else if (p instanceof NumberProperty) values.add(p);
            else if (isList(p) || p.getValue() instanceof String) general.add(p);
        }

        float top = y + TOP, h = WIN_H - TOP - 6;
        paneX = x;
        paneY = top;
        paneH = h;
        String sk = paneKey(m);
        float scroll = scroll(sk, paneContent, h);

        pushClip(x, top, w - 1, h);
        float bx = x + 8 + shift, bw = w - 16, cy = top - scroll;
        if (general.isEmpty() && values.isEmpty() && options.isEmpty()) {
            string(body, "no settings", bx + 2, cy + 6, TEXT_3, pa);
            cy += 20;
        } else {
            int boxIndex = 0;
            boolean pair = !general.isEmpty() && !values.isEmpty();
            if (pair) {
                float half = (bw - BOX_GAP) / 2f;
                float h1 = drawBox("general", general, 1, bx, cy, half, boxA(pa, boxIndex++), accent);
                float h2 = drawBox("values", values, 1, bx + half + BOX_GAP, cy, half, boxA(pa, boxIndex++), accent);
                cy += Math.max(h1, h2) + BOX_GAP;
            } else {
                if (!general.isEmpty()) cy += drawBox("general", general, 2, bx, cy, bw, boxA(pa, boxIndex++), accent) + BOX_GAP;
                if (!values.isEmpty()) cy += drawBox("values", values, 2, bx, cy, bw, boxA(pa, boxIndex++), accent) + BOX_GAP;
            }
            if (!options.isEmpty()) cy += drawBox("options", options, 2, bx, cy, bw, boxA(pa, boxIndex), accent) + BOX_GAP;
            cy -= BOX_GAP;
        }
        popClip();
        paneContent = cy - (top - scroll) + 6;
        scrollBar(sk, x + w - 4, top, h, paneContent, a, accent);
    }

    private float boxA(float pa, int index) {
        float t = anims.getOrDefault("pane", 1f);
        return pa * easeOut(clamp(t * 1.6f - index * 0.18f));
    }

    private float drawBox(String title, List<Property<?>> props, int columns, float x, float y, float w, float a, Color accent) {
        float inner = w - BOX_PAD * 2;
        float colW = columns == 1 ? inner : (inner - 12) / 2f;
        float[] colH = new float[columns];
        for (int i = 0; i < props.size(); i++) colH[i % columns] += rowHeight(props.get(i)) + 4;
        float contentH = 0;
        for (float c : colH) contentH = Math.max(contentH, c);
        float boxH = 16 + contentH + BOX_PAD - 4;

        fill(x, y, w, boxH, alpha(BOX, a));
        frame(x, y, w, boxH, alpha(BOX_EDGE, a));
        stringMid(small, title, x + (w - small.getStringWidth(title)) / 2f, y + 2, 12, TEXT_3, a);

        pushClip(x + 1, y + 1, w - 2, boxH - 2);
        float[] rowY = {y + 16, y + 16};
        for (int i = 0; i < props.size(); i++) {
            int c = i % columns;
            float rx = x + BOX_PAD + c * (colW + 12);
            rowY[c] += drawSetting(props.get(i), rx, rowY[c], colW, a, accent) + 4;
        }
        popClip();
        return boxH;
    }

    private static boolean isColor(Property<?> p) {
        return p instanceof NumberProperty && ((NumberProperty) p).getMin() <= Integer.MIN_VALUE && ((NumberProperty) p).getMax() >= Integer.MAX_VALUE;
    }

    private static boolean isList(Property<?> p) {
        return p instanceof ModeProperty || p instanceof MultiModeProperty || p instanceof SelectProperty;
    }

    private float rowHeight(Property<?> p) {
        if (isColor(p)) return 11 + (ColorPicker.TOTAL_HEIGHT + 4) * easeOut(anims.getOrDefault("pick:" + System.identityHashCode(p), 0f));
        if (p instanceof NumberProperty) return 20;
        if (isList(p)) {
            float open = easeOut(anims.getOrDefault("list:" + System.identityHashCode(p), 0f));
            return 25 + (options(p).size() * 12 + 4) * open;
        }
        if (p.getValue() instanceof String) return 25;
        return 11;
    }

    private float drawSetting(Property<?> p, float x, float y, float w, float a, Color accent) {
        float h = rowHeight(p);
        if (isColor(p)) drawColor((NumberProperty) p, x, y, w, a);
        else if (p instanceof NumberProperty) drawSlider((NumberProperty) p, x, y, w, a, accent);
        else if (isList(p)) drawCombo(p, x, y, w, a, accent);
        else if (p.getValue() instanceof Boolean) drawBool(p, x, y, w, a, accent);
        else if (p.getValue() instanceof String) drawText(p, x, y, w, a);
        return h;
    }

    @SuppressWarnings("unchecked")
    private void drawBool(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean value = (Boolean) p.getValue();
        boolean hover = over(x - 2, y - 1, w + 4, 13);
        float on = anim(p, value ? 1f : 0f, MEDIUM);
        checkbox(x, y + (11 - CHECK) / 2f, on, a, accent, hover);
        CustomFontRenderer f = on > 0.5f ? bold : body;
        stringMid(f, trim(f, lower(p.getLabel()), w - 12), x + CHECK + 5, y, 11, mixRgb(TEXT_3, TEXT, Math.max(on, hover ? 0.6f : 0f)), a);
        hit(x - 2, y - 1, w + 4, 13, b -> ((Property<Boolean>) p).setValue(!value));
    }

    private void drawSlider(NumberProperty p, float x, float y, float w, float a, Color accent) {
        double min = p.getMin(), max = p.getMax(), value = p.getValue();
        float pct = max > min ? (float) ((value - min) / (max - min)) : 0f;
        boolean dragging = dragSlider == p;
        float shown = dragging ? pct : anim(p, pct, FAST);
        if (dragging) anims.put(p, pct);

        String v = format(value, p.getIncrement());
        stringMid(body, trim(body, lower(p.getLabel()), w - small.getStringWidth(v) - 24), x + 8, y, 10, TEXT_2, a);
        stringMid(small, v, x + w - 8 - small.getStringWidth(v), y, 10, dragging ? accent.getRGB() & 0xFFFFFF : TEXT, a);

        float by = y + 13, bh = 4, bx = x + 8, bw = w - 16;
        boolean barHover = over(bx - 2, by - 3, bw + 4, bh + 6) || dragging;
        fill(bx, by, bw, bh, alpha(TRACK, a));
        if (shown > 0.001f) fill(bx, by, bw * shown, bh, alpha(accent, a));
        float kx = Math.max(bx, Math.min(bx + bw - 4, bx + bw * shown - 2));
        fill(kx, by - (barHover ? 1 : 0), 4, bh + (barHover ? 2 : 0), alpha(new Color(TEXT), a));
        hit(bx - 2, by - 3, bw + 4, bh + 6, b -> {
            dragSlider = p;
            dragSliderX = bx;
            dragSliderW = bw;
            setSlider(p, (mouseX - bx) / bw);
        });

        boolean mh = over(x - 1, by - 3, 8, bh + 6), ph = over(x + w - 7, by - 3, 8, bh + 6);
        stringMid(small, "-", x + 1, by - 3, bh + 6, mh ? TEXT : TEXT_3, a);
        stringMid(small, "+", x + w - 5, by - 3, bh + 6, ph ? TEXT : TEXT_3, a);
        hit(x - 1, by - 3, 8, bh + 6, b -> step(p, -1));
        hit(x + w - 7, by - 3, 8, bh + 6, b -> step(p, 1));
    }

    private void step(NumberProperty p, int dir) {
        double inc = p.getIncrement() > 0 ? p.getIncrement() : (p.getMax() - p.getMin()) / 100.0;
        double v = Math.round((p.getValue() + dir * inc) / inc) * inc;
        p.setValue(Math.max(p.getMin(), Math.min(p.getMax(), v)));
    }

    private void setSlider(NumberProperty p, float pct) {
        double raw = p.getMin() + (p.getMax() - p.getMin()) * clamp(pct);
        double inc = p.getIncrement();
        if (inc > 0) raw = Math.round(raw / inc) * inc;
        p.setValue(Math.max(p.getMin(), Math.min(p.getMax(), raw)));
    }

    private static String format(double value, double increment) {
        int places = 0;
        if (increment > 0 && increment < 1) places = Math.min(3, BigDecimal.valueOf(increment).stripTrailingZeros().scale());
        return new BigDecimal(value).setScale(Math.max(0, places), RoundingMode.HALF_UP).toPlainString();
    }

    private List<String> options(Property<?> p) {
        List<String> out = new ArrayList<>();
        if (p instanceof ModeProperty) for (Object v : ((ModeProperty<?>) p).getValues()) out.add(v.toString());
        else if (p instanceof MultiModeProperty) for (Object v : ((MultiModeProperty<?>) p).getValues()) out.add(v.toString());
        else if (p instanceof SelectProperty) {
            out.addAll(((SelectProperty) p).getOptions());
            if (out.isEmpty()) out.add("(none)");
        }
        return out;
    }

    private boolean selected(Property<?> p, String option) {
        if (p instanceof MultiModeProperty) {
            MultiModeProperty<?> mp = (MultiModeProperty<?>) p;
            for (Object v : mp.getValues()) if (v.toString().equals(option)) return mp.getValue().contains(v);
            return false;
        }
        return option.equals(String.valueOf(p.getValue()));
    }

    private String comboValue(Property<?> p) {
        if (p instanceof MultiModeProperty) {
            List<String> chosen = new ArrayList<>();
            for (String o : options(p)) if (selected(p, o)) chosen.add(lower(o));
            return chosen.isEmpty() ? "-" : String.join(", ", chosen);
        }
        String v = String.valueOf(p.getValue());
        return v.isEmpty() || "null".equals(v) ? "-" : lower(v);
    }

    @SuppressWarnings("unchecked")
    private void pick(Property<?> p, String option) {
        if (p instanceof MultiModeProperty) {
            MultiModeProperty<?> mp = (MultiModeProperty<?>) p;
            Object[] values = mp.getValues();
            for (int i = 0; i < values.length; i++) if (values[i].toString().equals(option)) mp.setValue(i);
        } else if (p instanceof ModeProperty) {
            for (Object v : ((ModeProperty<?>) p).getValues()) if (v.toString().equals(option)) ((Property<Object>) p).setValueObj(v);
        } else if (p instanceof SelectProperty && !"(none)".equals(option)) {
            ((SelectProperty) p).setValue(option);
        }
    }

    private void drawCombo(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean isOpen = openLists.contains(p);
        float open = anim("list:" + System.identityHashCode(p), isOpen ? 1f : 0f, MEDIUM);
        stringMid(bold, trim(bold, lower(p.getLabel()), w), x, y, 10, TEXT, a);

        float cy = y + 11, ch = 13;
        boolean hover = over(x, cy, w, ch);
        float hv = anim("comboH:" + System.identityHashCode(p), hover || isOpen ? 1f : 0f, FAST);
        fill(x, cy, w, ch, alpha(mix(FIELD, FIELD_HOVER, hv), a));
        frame(x, cy, w, ch, alpha(mix(FIELD_EDGE, new Color(68, 68, 76), hv), a));
        stringMid(small, trim(small, comboValue(p), w - 18), x + 5, cy, ch, TEXT_2, a);
        Glyphs.chevron(x + w - 7, cy + ch / 2f, 2f, 1f, open > 0.5f, alpha(new Color(TEXT_2), a));
        hit(x, cy, w, ch, b -> {
            if (!openLists.remove(p)) {
                openLists.add(p);
                if (p instanceof SelectProperty) ((SelectProperty) p).onOpen();
            }
        });

        if (open > 0.002f) {
            List<String> opts = options(p);
            float full = opts.size() * 12 + 2;
            float ly = cy + ch + 1;
            pushClip(x, ly, w, (full + 2) * easeOut(open));
            float oa = a * clamp(open * 1.5f - 0.3f);
            fill(x, ly, w, full, alpha(new Color(22, 22, 26), a));
            frame(x, ly, w, full, alpha(FIELD_EDGE, a));
            float oy = ly + 1;
            for (String o : opts) {
                boolean sel = selected(p, o);
                boolean oh = over(x + 1, oy, w - 2, 12);
                if (oh) fill(x + 1, oy, w - 2, 12, alpha(new Color(38, 38, 44), oa));
                stringMid(small, trim(small, lower(o), w - 18), x + 5, oy, 12, sel ? accent.getRGB() & 0xFFFFFF : TEXT_2, oa);
                if (sel) Glyphs.check(x + w - 7, oy + 6, 2.6f, 1f, 1f, alpha(accent, oa));
                final String option = o;

                hit(x + 1, oy, w - 2, 12, b -> pick(p, option));
                oy += 12;
            }
            popClip();
        }
    }

    private void drawText(Property<?> p, float x, float y, float w, float a) {
        boolean isEditing = editing == p;
        stringMid(bold, trim(bold, lower(p.getLabel()), w), x, y, 10, TEXT, a);
        float fy = y + 11, fh = 13;
        boolean hover = over(x, fy, w, fh);
        fill(x, fy, w, fh, alpha(hover || isEditing ? FIELD_HOVER : new Color(20, 20, 24), a));
        frame(x, fy, w, fh, alpha(isEditing ? new Color(74, 74, 82) : FIELD_EDGE, a));
        String shown = trim(small, isEditing ? editBuffer : String.valueOf(p.getValue()), w - 10);
        stringMid(small, shown, x + 5, fy, fh, TEXT, a);
        if (isEditing && caretOn()) fill(x + 6 + small.getStringWidth(shown), fy + 3, 1, fh - 6, alpha(new Color(TEXT), a));
        hit(x, fy, w, fh, b -> {
            focusOnly("edit");
            editing = p;
            editBuffer = String.valueOf(p.getValue());
        });
    }

    private void drawColor(NumberProperty p, float x, float y, float w, float a) {
        String key = "pick:" + System.identityHashCode(p);
        boolean isOpen = openColors.contains(p);
        float open = anim(key, isOpen ? 1f : 0f, MEDIUM);
        int argb = p.getValue().intValue();
        boolean hover = over(x - 2, y - 1, w + 4, 13);
        stringMid(body, trim(body, lower(p.getLabel()), w - 30), x + CHECK + 5, y, 11, hover ? TEXT : TEXT_2, a);
        float sw = 13, sh = 6, sx = x + w - sw, sy = y + (11 - sh) / 2f;
        fill(sx, sy, sw, sh, alpha(new Color(argb & 0xFFFFFF), a));
        frame(sx - 1, sy - 1, sw + 2, sh + 2, alpha(new Color(255, 255, 255, hover ? 60 : 28), a));
        hit(x - 2, y - 1, w + 4, 13, b -> {
            ColorPicker picker = pickers.computeIfAbsent(p, k -> new ColorPicker());
            if (openColors.remove(p)) return;
            picker.setColor(p.getValue().intValue());
            openColors.add(p);
        });
        if (open > 0.002f && pickers.containsKey(p)) {
            ColorPicker picker = pickers.get(p);
            int px = Math.round(x), py = Math.round(y + 13);
            pushClip(x, y + 11, w, (ColorPicker.TOTAL_HEIGHT + 4) * easeOut(open));
            picker.render(px, py, mouseX, mouseY);
            GlStateManager.enableTexture2D();
            popClip();
            hit(px, py, ColorPicker.WIDTH, ColorPicker.TOTAL_HEIGHT, b -> {
                if (picker.mouseClicked(px, py, mouseX, mouseY, b)) {
                    dragPicker = picker;
                    dragPickerProperty = p;
                    dragPickerX = px;
                    dragPickerY = py;
                }
                setColor(p, picker.getColor());
            });
        }
    }

    private static void setColor(Property<?> p, int argb) {
        if (p instanceof NumberProperty) ((NumberProperty) p).setValue((double) argb);
    }

    private void drawConfigPane(float x, float y, float w, float a, Color accent) {
        if (configs == null) configs = LocalConfigFetcher.fetchConfigList();
        String name = selectedConfig;
        if (name == null || !configs.contains(name)) name = configs.isEmpty() ? null : configs.get(0);
        final String cfg = name;

        stringMid(bold, cfg == null ? "configs" : lower(cfg), x + 10, y + 12, 14, TEXT, a);
        if (!configStatus.isEmpty() && System.currentTimeMillis() - configStatusAt < 3000) {
            float sa = clamp((3000 - (System.currentTimeMillis() - configStatusAt)) / 400f);
            String s = lower(configStatus);
            stringMid(small, s, x + w - 10 - small.getStringWidth(s), y + 12, 14, accent.getRGB() & 0xFFFFFF, a * sa);
        }

        float bx = x + 8, bw = w - 16, cy = y + TOP;
        float half = (bw - BOX_GAP) / 2f;

        float fileH = 16 + 13 + 6 + 16 + BOX_PAD - 2;
        fill(bx, cy, half, fileH, alpha(BOX, boxA(a, 0)));
        frame(bx, cy, half, fileH, alpha(BOX_EDGE, boxA(a, 0)));
        float fa = boxA(a, 0);
        stringMid(small, "file", bx + (half - small.getStringWidth("file")) / 2f, cy + 2, 12, TEXT_3, fa);
        if (cfg == null) {
            stringMid(body, "no configs saved yet", bx + BOX_PAD, cy + 16, 13, TEXT_3, fa);
        } else {
            stringMid(body, "name", bx + BOX_PAD, cy + 16, 13, TEXT_2, fa);
            String shown = trim(body, lower(cfg), half - 50);
            stringMid(body, shown, bx + half - BOX_PAD - body.getStringWidth(shown), cy + 16, 13, TEXT, fa);
            float btnY = cy + 16 + 13 + 6, btnW = (half - BOX_PAD * 2 - 8) / 3f;
            float b0 = bx + BOX_PAD;
            button("load", "load", b0, btnY, btnW, 16, fa, () -> {
                boolean ok = LocalConfigFetcher.loadConfig(cfg);
                status(ok ? "loaded " + cfg : "couldn't load " + cfg);
            });
            button("save", "save", b0 + btnW + 4, btnY, btnW, 16, fa, () -> {
                boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(cfg);
                status(ok ? "saved " + cfg : "couldn't save " + cfg);
            });
            boolean confirming = cfg.equals(confirmDelete);
            button("delete", confirming ? "sure?" : "delete", b0 + (btnW + 4) * 2, btnY, btnW, 16, fa, () -> {
                if (cfg.equals(armedDelete)) {
                    boolean ok = Tempest.INSTANCE.getConfigManager().deleteConfig(cfg);
                    status(ok ? "deleted " + cfg : "couldn't delete " + cfg);
                    configs = null;
                    selectedConfig = null;
                } else confirmDelete = cfg;
            });
        }

        float ma = boxA(a, 1), mx = bx + half + BOX_GAP, menuH = 16 + 13 + BOX_PAD - 2;
        fill(mx, cy, half, menuH, alpha(BOX, ma));
        frame(mx, cy, half, menuH, alpha(BOX_EDGE, ma));
        stringMid(small, "menu", mx + (half - small.getStringWidth("menu")) / 2f, cy + 2, 12, TEXT_3, ma);
        stringMid(body, "toggle menu key", mx + BOX_PAD, cy + 16, 13, TEXT_2, ma);
        String keyName = Keyboard.getKeyName(ClientSettingsModule.menuKey.getValue().intValue());
        String key = bindingMenuKey ? "[...]" : "[" + (keyName == null ? "RSHIFT" : keyName) + "]";
        float kx = mx + half - BOX_PAD - small.getStringWidth(key);
        boolean kh = over(kx - 2, cy + 14, small.getStringWidth(key) + 4, 16);
        stringMid(small, key, kx, cy + 16, 13, bindingMenuKey ? accent.getRGB() & 0xFFFFFF : kh ? TEXT : TEXT_3, bindingMenuKey ? ma * (0.55f + 0.45f * pulse()) : ma);
        hit(kx - 2, cy + 14, small.getStringWidth(key) + 4, 16, b -> {
            bindingMenuKey = !bindingMenuKey;
            binding = null;
        });

        cy += Math.max(fileH, menuH) + BOX_GAP;
        float na = boxA(a, 2), newH = 16 + 14 + BOX_PAD - 2;
        fill(bx, cy, bw, newH, alpha(BOX, na));
        frame(bx, cy, bw, newH, alpha(BOX_EDGE, na));
        stringMid(small, "new config", bx + (bw - small.getStringWidth("new config")) / 2f, cy + 2, 12, TEXT_3, na);
        float fx = bx + BOX_PAD, fy = cy + 16, fw = bw - BOX_PAD * 2 - 58, fh = 14;
        boolean fHover = over(fx, fy, fw, fh);
        fill(fx, fy, fw, fh, alpha(fHover || configFieldFocused ? FIELD_HOVER : new Color(20, 20, 24), na));
        frame(fx, fy, fw, fh, alpha(configFieldFocused ? new Color(74, 74, 82) : FIELD_EDGE, na));
        if (configName.isEmpty() && !configFieldFocused) stringMid(small, "config name", fx + 5, fy, fh, TEXT_4, na);
        else {
            String shown = trim(small, configName, fw - 10);
            stringMid(small, shown, fx + 5, fy, fh, TEXT, na);
            if (configFieldFocused && caretOn()) fill(fx + 6 + small.getStringWidth(shown), fy + 3, 1, fh - 6, alpha(new Color(TEXT), na));
        }
        hit(fx, fy, fw, fh, b -> focusOnly("config"));
        button("create", "create", fx + fw + 6, fy, 52, fh, na, this::createConfig);
    }

    private void createConfig() {
        String n = configName.trim();
        if (n.isEmpty()) return;
        boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(n);
        status(ok ? "saved " + n : "couldn't save " + n);
        configName = "";
        configs = null;
        selectedConfig = n;
        anims.put("pane", 0f);
    }

    private void status(String text) {
        configStatus = text;
        configStatusAt = System.currentTimeMillis();
    }

    private void button(String key, String label, float x, float y, float w, float h, float a, Runnable action) {
        boolean hover = over(x, y, w, h);
        float hv = anim("btn:" + key, hover ? 1f : 0f, FAST);
        fill(x, y, w, h, alpha(mix(FIELD, new Color(38, 38, 44), hv), a));
        stringMid(small, label, x + (w - small.getStringWidth(label)) / 2f, y, h, mixRgb(TEXT_2, TEXT, hv), a);
        hit(x, y, w, h, b -> action.run());
    }

    private void drawTooltip(float a) {
        if (hoverText == null) {
            hoverKey = null;
            anim("tip", 0f, FAST);
            return;
        }
        float t = anim("tip", System.currentTimeMillis() - hoverSince > 450 ? 1f : 0f, MEDIUM);
        if (t < 0.02f) return;
        String s = lower(hoverText);
        float tw = small.getStringWidth(s) + 10, th = 13;
        float tx = Math.min(mouseX + 10, width - tw - 4), ty = mouseY + 12 + (1f - t) * 3f;
        fill(tx, ty, tw, th, alpha(new Color(8, 8, 10, 240), t * a));
        frame(tx, ty, tw, th, alpha(BOX_EDGE, t * a));
        stringMid(small, s, tx + 5, ty, th, TEXT_2, t * a);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (closing) return;
        mouseX = mx;
        mouseY = my;
        armedDelete = confirmDelete;
        confirmDelete = null;

        Hit target = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                target = h;
                break;
            }
        }
        boolean wasBinding = binding != null || bindingMenuKey;
        searchFocused = false;
        configFieldFocused = false;
        if (editing != null) commitEdit();
        if (wasBinding && target != null) {
            binding = null;
            bindingMenuKey = false;
        }
        if (target != null) target.action.accept(button);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragSlider = null;
        dragWindow = false;
        if (dragPicker != null) {
            dragPicker.mouseReleased();
            dragPicker = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || closing) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        String key;
        float content, view;
        if (mx >= listX && mx <= listX + LIST_W && my >= listY && my <= listY + listH) {
            key = listKey();
            content = listContent;
            view = listH;
        } else if (mx >= paneX && my >= paneY && my <= paneY + paneH && mx <= winX + WIN_W) {
            if (showingConfigs()) return;
            if (showingThemes()) {
                key = THEMES_PANE;
            } else {
                Module m = selected(shownModules());
                if (m == null) return;
                key = paneKey(m);
            }
            content = paneContent;
            view = paneH;
        } else return;
        float target = scrollTarget.getOrDefault(key, 0f) - Math.signum(wheel) * 38f;
        scrollTarget.put(key, Math.max(0, Math.min(Math.max(0, content - view), target)));
    }

    private void commitEdit() {
        if (editing != null) editing.setValueObj(editBuffer);
        editing = null;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean press = keyCode > 0;

        if (binding != null || bindingMenuKey) {
            if (!press) return;
            boolean clear = keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK;
            if (bindingMenuKey) {
                if (keyCode != Keyboard.KEY_ESCAPE) ClientSettingsModule.menuKey.setValue((double) (clear ? Keyboard.KEY_RSHIFT : keyCode));
                bindingMenuKey = false;
            } else {
                binding.setKey(clear ? 0 : keyCode);
                binding = null;
            }
            return;
        }

        if (searchFocused || configFieldFocused || editing != null) {
            if (press) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                    if (editing != null) {
                        if (keyCode == Keyboard.KEY_RETURN) commitEdit();
                        else editing = null;
                    } else if (configFieldFocused && keyCode == Keyboard.KEY_RETURN) {
                        createConfig();
                    }
                    searchFocused = false;
                    configFieldFocused = false;
                } else if (keyCode == Keyboard.KEY_BACK) {
                    edit(s -> s.isEmpty() ? s : s.substring(0, s.length() - 1));
                } else if (isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
                    String clip = GuiScreen.getClipboardString();
                    if (clip != null) {
                        StringBuilder sb = new StringBuilder();
                        for (char ch : clip.toCharArray()) if (ChatAllowedCharacters.isAllowedCharacter(ch)) sb.append(ch);
                        edit(s -> s + sb);
                    }
                }
                return;
            }
            if (typedChar != 0 && ChatAllowedCharacters.isAllowedCharacter(typedChar) && !isCtrlKeyDown()) edit(s -> s + typedChar);
            return;
        }

        if (!press) return;
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) {
            searchFocused = true;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClientSettingsModule.menuKey.getValue().intValue()) closing = true;
    }

    private void edit(java.util.function.UnaryOperator<String> change) {
        if (searchFocused) {
            String before = search;
            search = change.apply(search);
            if (!search.equals(before)) {
                scrollTarget.put("list:search", 0f);
                if (before.isEmpty() || search.isEmpty()) {
                    anims.put("list", 0f);
                    anims.put("pane", 0f);
                }
            }
        } else if (configFieldFocused) {
            configName = change.apply(configName);
        } else if (editing != null) {
            editBuffer = change.apply(editBuffer);
        }
    }
}
