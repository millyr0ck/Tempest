package rip.tempest.interfaces.click.flat;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.Themes;
import rip.tempest.utils.misc.LocalConfigFetcher;
import rip.tempest.utils.render.Glyphs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class TilesClickInterface extends FlatGui {
    private static final float WIN_W = 460, WIN_H = 264, BAR_H = 23, DRAWER_H = 100, PAD = 9, TILE_H = 35, GAP = 5, COLS = 4;

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
    private static final Map<Tab, String> selected = new EnumMap<>(Tab.class);
    private static String selectedSearch, selectedConfig;
    private static final Map<String, Float> scrollTarget = new HashMap<>();

    private boolean dragWindow;
    private float dragOffX, dragOffY;
    private float gridX, gridY, gridW, gridH, gridContent, drawerX, drawerY, drawerW, drawerH, drawerContent;

    private List<String> configs;
    private String configName = "", status = "", confirmDelete, armedDelete;
    private boolean configFocused;
    private long statusAt;

    @Override
    public void initGui() {
        super.initGui();
        configs = null;
        anims.put("page", 0f);
    }

    @Override
    protected String search() {
        return search;
    }

    @Override
    protected void setSearch(String text) {
        boolean wasEmpty = search.isEmpty();
        search = text;
        scrollTarget.put("grid:search", 0f);
        if (wasEmpty != search.isEmpty()) anims.put("page", 0f);
    }

    private boolean searching() {
        return !search.isEmpty();
    }

    private boolean configTab() {
        return !searching() && tab == Tab.CONFIG;
    }

    private boolean themesTab() {
        return !searching() && tab == Tab.THEMES;
    }

    @Override
    protected void drawGui(float a, Color accent) {
        if (Float.isNaN(winX)) {
            winX = (width - WIN_W) / 2f;
            winY = (height - WIN_H) / 2f;
        }
        float x = Math.round(winX), y = Math.round(winY + (1 - a) * 8);

        fill(x - 1, y - 1, WIN_W + 2, WIN_H + 2, new Color(0, 0, 0, Math.round(70 * a)));
        fill(x, y, WIN_W, WIN_H, alpha(PANEL, a));
        drawTabBar(x, y, a, accent);

        float drawerTop = y + WIN_H - DRAWER_H;
        drawGrid(x, y + BAR_H, WIN_W, drawerTop - (y + BAR_H), a, accent);
        drawDrawer(x, drawerTop, WIN_W, DRAWER_H, a, accent);
        frame(x, y, WIN_W, WIN_H, alpha(EDGE_2, a));
    }

    private void drawTabBar(float x, float y, float a, Color accent) {
        CustomFontRenderer logo = heavy(15), f = bold(13.5f), heavyTab = heavy(13.5f);
        hit(x, y, 70, BAR_H, b -> {
            dragWindow = true;
            dragOffX = mouseX - winX;
            dragOffY = mouseY - winY;
        });
        stringMid(logo, "tempest", x + PAD, y, BAR_H, WHITE, a);

        float tx = x + PAD + logo.getStringWidth("tempest") + 9;
        float selX = -1, selW = 0;
        for (Tab t : Tab.values()) {
            boolean cur = !searching() && t == tab;
            float tw = Math.max(f.getStringWidth(t.label), heavyTab.getStringWidth(t.label)) + 14;
            boolean hover = over(tx, y, tw, BAR_H);
            float sel = anim("tab:" + t, cur ? 1f : 0f, FAST);
            stringMid(cur ? heavyTab : f, t.label, tx + 7, y, BAR_H, mixRgb(mixRgb(MUTED, TEXT, hover ? 0.7f : 0f), WHITE, sel), a);
            if (cur) {
                selX = tx + 7;
                selW = tw - 14;
            }
            final Tab target = t;
            hit(tx, y, tw, BAR_H, b -> openTab(target));
            tx += tw;
        }

        float ux = anim("tabX", selX < 0 ? anims.getOrDefault("tabX", x) : selX, MEDIUM);
        float uw = anim("tabW", selX < 0 ? anims.getOrDefault("tabW", 0f) : selW, MEDIUM);
        float ua = anim("tabA", selX < 0 ? 0f : 1f, FAST);
        fill(ux, y + BAR_H - 1, uw, 1, alpha(accent, a * ua));

        searchBox(x + WIN_W - 100 - 4, y + (BAR_H - 15) / 2f, 100, 15, a, accent);
        fill(x, y + BAR_H - 0.5f, WIN_W, 0.5f, alpha(EDGE, a));
    }

    private void openTab(Tab t) {
        if (t == tab && !searching()) return;
        tab = t;

        search = "";
        searchFocused = false;
        configs = null;
        anims.put("page", 0f);
    }

    private List<Module> shownModules() {
        if (!searching()) return modules(tab.category);
        List<Module> out = new ArrayList<>();
        for (Tab t : Tab.values()) {
            if (t.category == null) continue;
            for (Module m : modules(t.category)) if (matches(m, search)) out.add(m);
        }
        return out;
    }

    private Module selectedModule(List<Module> list) {
        String id = searching() ? selectedSearch : selected.get(tab);
        for (Module m : list) if (m.getLabel().equals(id)) return m;
        return list.isEmpty() ? null : list.get(0);
    }

    private void select(Module m) {
        String before = searching() ? selectedSearch : selected.get(tab);
        if (searching()) selectedSearch = m.getLabel();
        else selected.put(tab, m.getLabel());
        if (!m.getLabel().equals(before)) {
            anims.put("drawer", 0f);
            openLists.clear();
            openColors.clear();
        }
    }

    private String gridKey() {
        return searching() ? "grid:search" : "grid:" + tab;
    }

    private void drawGrid(float x, float y, float w, float h, float a, Color accent) {
        float tileW = (w - PAD * 2 - GAP * (COLS - 1)) / COLS;
        int count;
        List<Module> mods = null;
        Themes.Preset[] presets = Themes.Preset.values();
        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        if (themesTab()) {
            count = presets.length + patterns.length;
        } else if (configTab()) {
            if (configs == null) configs = LocalConfigFetcher.fetchConfigList();
            count = configs.size();
        } else {
            mods = shownModules();
            count = mods.size();
        }
        int rows = (int) Math.ceil(count / COLS);
        gridX = x;
        gridY = y;
        gridW = w;
        gridH = h;
        gridContent = PAD * 2 + rows * TILE_H + Math.max(0, rows - 1) * GAP;
        float max = Math.max(0, gridContent - h);
        String key = gridKey();
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(key, 0f)));
        scrollTarget.put(key, target);
        float scroll = anim("scroll:" + key, target, MEDIUM);

        float page = anim("page", 1f, SLOW);
        pushClip(x, y, w, h);
        if (count == 0) {
            String text = configTab() ? "No configs saved yet" : searching() ? "No modules match" : "Nothing here yet";
            stringMid(bold(13), text, x + PAD + 2, y + PAD, 12, MUTED, a);
        }
        Module sel = mods == null ? null : selectedModule(mods);
        String selCfg = configTab() ? currentConfig() : null;
        for (int i = 0; i < count; i++) {
            int row = (int) (i / COLS), col = (int) (i % COLS);
            float tx = x + PAD + col * (tileW + GAP), ty = y + PAD + row * (TILE_H + GAP) - scroll;
            if (ty > y + h || ty + TILE_H < y) continue;

            float t = easeOut(clamp(page * 1.8f - row * 0.18f - col * 0.04f));
            float ta = a * t, rise = (1 - t) * 5;
            if (themesTab() && i < presets.length) drawThemeTile(presets[i], tx, ty + rise, tileW, ta, accent);
            else if (themesTab()) drawPatternTile(patterns[i - presets.length], tx, ty + rise, tileW, ta, accent);
            else if (mods != null) drawTile(mods.get(i), tx, ty + rise, tileW, mods.get(i) == sel, ta, accent);
            else drawConfigTile(configs.get(i), tx, ty + rise, tileW, configs.get(i).equals(selCfg), ta, accent);
        }
        popClip();
        if (max > 0) {
            float thumbH = Math.max(12, h * h / gridContent);
            fill(x + w - 3, y + (h - thumbH) * (scroll / max), 1, thumbH, alpha(new Color(70, 72, 82), a));
        }
    }

    private void drawTile(Module m, float x, float y, float w, boolean current, float a, Color accent) {
        String id = m.getLabel();
        float on = anim("on:" + id, m.isEnabled() ? 1f : 0f, MEDIUM);
        boolean hover = over(x, y, w, TILE_H);
        float hv = anim("tileH:" + id, hover ? 1f : 0f, FAST);
        float cur = anim("cur:" + id, current ? 1f : 0f, FAST);

        fill(x, y, w, TILE_H, alpha(mix(new Color(23, 24, 30), new Color(28, 29, 36), hv), a));
        frame(x, y, w, TILE_H, alpha(mix(EDGE, accent, cur), a));

        float line = w * easeOut(on);
        if (line > 0.3f) fill(x, y + TILE_H - 1, line, 1, alpha(accent, a));

        CustomFontRenderer name = heavy(14), desc = regular(11.5f), small = bold(10.5f);
        stringMid(name, trim(name, id, w - 18), x + 6, y + 6, 9, mixRgb(TEXT_2, WHITE, Math.max(on, hv * 0.5f)), a);
        checkbox(x + w - 6 - CHECK, y + 8, on, a, accent, hover);
        String key = binding == m ? "[...]" : keyName(m);
        float kw = key.isEmpty() ? 0 : small.getStringWidth(key) + 4;
        String about = searching() ? category(m) : m.getDescription();
        stringMid(desc, trim(desc, about == null ? "" : about, w - 12 - kw), x + 6, y + 18, 9, MUTED, a);
        if (!key.isEmpty()) stringMid(small, key, x + w - 6 - small.getStringWidth(key), y + 18, 9, binding == m ? rgb(accent) : FAINT, a);

        hit(x, y, w, TILE_H, b -> {
            if (b == 0) m.toggle();
            else if (b == 1) select(m);
            else if (b == 2) startBinding(m);
        });
    }

    private void drawThemeTile(Themes.Preset preset, float x, float y, float w, float a, Color accent) {
        boolean hover = over(x, y, w, TILE_H);
        float hv = anim("thH:" + preset, hover ? 1f : 0f, FAST);
        float cur = anim("thC:" + preset, themeInUse(preset) ? 1f : 0f, FAST);
        fill(x, y, w, TILE_H, alpha(mix(new Color(23, 24, 30), new Color(28, 29, 36), hv), a));
        frame(x, y, w, TILE_H, alpha(mix(EDGE, accent, cur), a));
        CustomFontRenderer f = heavy(14);
        stringMid(f, trim(f, preset.toString(), w - 18), x + 6, y + 6, 9, mixRgb(TEXT_2, WHITE, Math.max(cur, hv * 0.5f)), a);
        if (cur > 0.01f) Glyphs.check(x + w - 8.5f, y + 10.5f, 2.4f, 0.9f, 1f, alpha(accent, a * cur));
        themeStrip(preset, x + 6, y + 20, w - 12, 5, a);
        hit(x, y, w, TILE_H, b -> Themes.select(preset));
    }

    private void drawPatternTile(rip.tempest.utils.render.ArrayPattern pattern, float x, float y, float w, float a, Color accent) {
        rip.tempest.modules.impl.visuals.ArrayListModule list = rip.tempest.Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
        if (list == null) return;
        boolean hover = over(x, y, w, TILE_H);
        float hv = anim("ptH:" + pattern, hover ? 1f : 0f, FAST);
        float cur = anim("ptC:" + pattern, list.pattern.getValue() == pattern ? 1f : 0f, FAST);
        fill(x, y, w, TILE_H, alpha(mix(new Color(23, 24, 30), new Color(28, 29, 36), hv), a));
        frame(x, y, w, TILE_H, alpha(mix(EDGE, accent, cur), a));
        CustomFontRenderer f = heavy(14), desc = regular(11.5f);
        String label = pattern == rip.tempest.utils.render.ArrayPattern.Off ? "No pattern" : pattern.toString();
        stringMid(f, trim(f, label, w - 18), x + 6, y + 6, 9, mixRgb(TEXT_2, WHITE, Math.max(cur, hv * 0.5f)), a);
        if (cur > 0.01f) Glyphs.check(x + w - 8.5f, y + 10.5f, 2.4f, 0.9f, 1f, alpha(accent, a * cur));
        float sx = x + 6, sy = y + 19, sw = w - 12, sh = 6;
        if (pattern == rip.tempest.utils.render.ArrayPattern.Off) {
            stringMid(desc, "array list pattern", sx, y + 18, 9, MUTED, a);
        } else {
            double t = list.patternTime();
            for (float bx = 0; bx < sw; bx += 2) {
                for (float by = 0; by < sh; by += 2) {
                    int c = pattern.color(bx + 1, by + 1, sw, sh, t, list.motion());
                    fill(sx + bx, sy + by, Math.min(2, sw - bx), 2, alpha(new Color(c & 0xFFFFFF), a));
                }
            }
        }
        hit(x, y, w, TILE_H, b -> list.pattern.setValue(pattern));
    }

    private static String category(Module m) {
        String n = m.getCategory().name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private String currentConfig() {
        if (configs == null || configs.isEmpty()) return null;
        return selectedConfig != null && configs.contains(selectedConfig) ? selectedConfig : configs.get(0);
    }

    private void drawConfigTile(String name, float x, float y, float w, boolean current, float a, Color accent) {
        boolean hover = over(x, y, w, TILE_H);
        float hv = anim("cfgH:" + name, hover ? 1f : 0f, FAST);
        float cur = anim("cfgC:" + name, current ? 1f : 0f, FAST);
        fill(x, y, w, TILE_H, alpha(mix(new Color(23, 24, 30), new Color(28, 29, 36), hv), a));
        frame(x, y, w, TILE_H, alpha(mix(EDGE, accent, cur), a));
        CustomFontRenderer f = heavy(14), desc = regular(11.5f);
        stringMid(f, trim(f, name, w - 12), x + 6, y + 6, 9, mixRgb(TEXT_2, WHITE, Math.max(cur, hv * 0.5f)), a);
        stringMid(desc, "saved config", x + 6, y + 18, 9, MUTED, a);
        hit(x, y, w, TILE_H, b -> {
            if (!name.equals(selectedConfig)) anims.put("drawer", 0f);
            selectedConfig = name;
        });
    }

    private void drawDrawer(float x, float y, float w, float h, float a, Color accent) {
        fill(x, y, w, h, alpha(DEEP, a));
        fill(x, y, w, 0.5f, alpha(EDGE_2, a));
        float da = a * easeOut(anim("drawer", 1f, SLOW));
        if (configTab()) {
            drawConfigDrawer(x, y, w, h, da, accent);
            return;
        }
        if (themesTab()) {
            drawThemesDrawer(x, y, w, h, da, accent);
            return;
        }
        List<Module> mods = shownModules();
        Module m = selectedModule(mods);
        if (m == null) return;

        CustomFontRenderer title = heavy(14), small = bold(11.5f);
        float hy = y, hh = 20;
        stringMid(title, m.getLabel(), x + PAD, hy, hh, WHITE, da);
        String desc = m.getDescription() == null ? "" : m.getDescription();
        String key = keyName(m);
        String line = desc + (key.isEmpty() ? "" : "  ·  " + key);
        float lx = x + PAD + title.getStringWidth(m.getLabel()) + 8;
        String hint = "right-click a tile for its settings";
        float hintW = small.getStringWidth(hint);
        stringMid(small, trim(small, line, x + w - PAD - hintW - 12 - lx), lx, hy, hh, MUTED, da);
        stringMid(small, hint, x + w - PAD - hintW, hy, hh, FAINT, da * 0.85f);
        fill(x, y + hh, w, 0.5f, alpha(EDGE, a));

        float cy = y + hh + 7, ch = h - hh - 7;
        drawerX = x;
        drawerY = cy;
        drawerW = w;
        drawerH = ch;
        List<Property<?>> props = settings(m);
        float colGap = 14, colW = (w - PAD * 2 - colGap * 2) / 3f;
        String key2 = "drawer:" + m.getLabel();
        float max = Math.max(0, drawerContent - ch);
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(key2, 0f)));
        scrollTarget.put(key2, target);
        float scroll = anim("scroll:" + key2, target, MEDIUM);

        pushClip(x, cy - 3, w, ch + 3);
        if (props.isEmpty()) stringMid(bold(12.5f), "No settings", x + PAD, cy, 11, MUTED, da);
        float[] colY = {cy - scroll, cy - scroll, cy - scroll};
        for (Property<?> p : props) {
            int c = colY[0] <= colY[1] && colY[0] <= colY[2] ? 0 : colY[1] <= colY[2] ? 1 : 2;
            colY[c] += drawSetting(p, x + PAD + c * (colW + colGap), colY[c], colW, da, accent) + 5;
        }
        popClip();
        drawerContent = Math.max(colY[0], Math.max(colY[1], colY[2])) - (cy - scroll) + 2;
        if (max > 0) {
            float thumbH = Math.max(10, ch * ch / drawerContent);
            fill(x + w - 3, cy + (ch - thumbH) * (scroll / max), 1, thumbH, alpha(new Color(70, 72, 82), a));
        }
    }

    private void drawThemesDrawer(float x, float y, float w, float h, float a, Color accent) {
        CustomFontRenderer title = heavy(14), small = bold(11.5f);
        float hh = 20;
        stringMid(title, "Themes", x + PAD, y, hh, WHITE, a);
        String line = Themes.active() ? "In use: " + Themes.current() : "Pick a preset to colour the client with it";
        stringMid(small, line, x + PAD + title.getStringWidth("Themes") + 8, y, hh, Themes.active() ? rgb(accent) : MUTED, a);
        fill(x, y + hh, w, 0.5f, alpha(EDGE, a));

        float cy = y + hh + 7, ch = h - hh - 7;
        drawerX = x;
        drawerY = cy;
        drawerW = w;
        drawerH = ch;
        float colGap = 14, colW = (w - PAD * 2 - colGap * 2) / 3f;
        String key = "drawer:themes";
        float max = Math.max(0, drawerContent - ch);
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(key, 0f)));
        scrollTarget.put(key, target);
        float scroll = anim("scroll:" + key, target, MEDIUM);
        pushClip(x, cy - 3, w, ch + 3);
        float[] colY = {cy - scroll, cy - scroll, cy - scroll};
        for (Property<?> p : themeSettings()) {
            int c = colY[0] <= colY[1] && colY[0] <= colY[2] ? 0 : colY[1] <= colY[2] ? 1 : 2;
            colY[c] += drawSetting(p, x + PAD + c * (colW + colGap), colY[c], colW, a, accent) + 5;
        }
        popClip();
        drawerContent = Math.max(colY[0], Math.max(colY[1], colY[2])) - (cy - scroll) + 2;
    }

    private void drawConfigDrawer(float x, float y, float w, float h, float a, Color accent) {
        CustomFontRenderer title = heavy(14), small = bold(11.5f), f = bold(12.5f);
        String cfg = currentConfig();
        float hh = 20;
        stringMid(title, cfg == null ? "Configs" : cfg, x + PAD, y, hh, WHITE, a);
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 3000) {
            float sa = clamp((3000 - (System.currentTimeMillis() - statusAt)) / 400f);
            stringMid(small, status, x + w - PAD - small.getStringWidth(status), y, hh, rgb(accent), a * sa);
        }
        fill(x, y + hh, w, 0.5f, alpha(EDGE, a));

        float by = y + hh + 10, bw = 60, bh = 15;
        if (cfg != null) {
            button("load", "Load", x + PAD, by, bw, bh, a, accent, true, () -> {
                boolean ok = LocalConfigFetcher.loadConfig(cfg);
                status(ok ? "Loaded " + cfg : "Couldn't load " + cfg);
            });
            button("save", "Save", x + PAD + bw + 5, by, bw, bh, a, accent, false, () -> {
                boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(cfg);
                status(ok ? "Saved " + cfg : "Couldn't save " + cfg);
            });
            boolean sure = cfg.equals(confirmDelete);
            button("delete", sure ? "Sure?" : "Delete", x + PAD + (bw + 5) * 2, by, bw, bh, a, accent, false, () -> {
                if (cfg.equals(armedDelete)) {
                    boolean ok = Tempest.INSTANCE.getConfigManager().deleteConfig(cfg);
                    status(ok ? "Deleted " + cfg : "Couldn't delete " + cfg);
                    confirmDelete = null;
                    configs = null;
                    selectedConfig = null;
                } else confirmDelete = cfg;
            });
        }

        float ny = by + bh + 12;
        stringMid(small, "New config", x + PAD, ny, 8, MUTED, a);
        float fx = x + PAD, fy = ny + 10, fw = 220, fh = 15;
        boolean hover = over(fx, fy, fw, fh);
        fill(fx, fy, fw, fh, alpha(configFocused || hover ? FIELD : DEEP.brighter(), a));
        frame(fx, fy, fw, fh, alpha(configFocused ? accent : EDGE_2, a));
        if (configName.isEmpty() && !configFocused) stringMid(f, "name", fx + 5, fy, fh, FAINT, a);
        else {
            String shown = trim(f, configName, fw - 10);
            stringMid(f, shown, fx + 5, fy, fh, TEXT, a);
            if (configFocused && caretOn()) fill(fx + 5.5f + f.getStringWidth(shown), fy + 3.5f, 0.5f, fh - 7, alpha(new Color(TEXT), a));
        }
        hit(fx, fy, fw, fh, b -> configFocused = true);
        button("create", "Create", fx + fw + 5, fy, bw, fh, a, accent, false, this::submitOwn);
    }

    private void status(String text) {
        status = text;
        statusAt = System.currentTimeMillis();
    }

    @Override
    protected boolean ownFocused() {
        return configFocused;
    }

    @Override
    protected void unfocusOwn() {
        configFocused = false;

        armedDelete = confirmDelete;
        confirmDelete = null;
    }

    @Override
    protected boolean editOwn(UnaryOperator<String> change) {
        if (!configFocused) return false;
        configName = change.apply(configName);
        return true;
    }

    @Override
    protected void submitOwn() {
        String n = configName.trim();
        if (n.isEmpty()) return;
        boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(n);
        status(ok ? "Saved " + n : "Couldn't save " + n);
        configName = "";
        configs = null;
        selectedConfig = n;
    }

    @Override
    protected void dragging() {
        if (!dragWindow) return;
        winX = Math.max(-WIN_W + 60, Math.min(width - 60, mouseX - dragOffX));
        winY = Math.max(0, Math.min(height - 30, mouseY - dragOffY));
    }

    @Override
    protected void released() {
        dragWindow = false;
    }

    @Override
    protected void scrolled(int mx, int my, int direction) {
        if (mx >= gridX && mx <= gridX + gridW && my >= gridY && my <= gridY + gridH) {
            String key = gridKey();
            float max = Math.max(0, gridContent - gridH);
            scrollTarget.put(key, Math.max(0, Math.min(max, scrollTarget.getOrDefault(key, 0f) - direction * 30f)));
        } else if (themesTab() && mx >= drawerX && mx <= drawerX + drawerW && my >= drawerY && my <= drawerY + drawerH) {
            float max = Math.max(0, drawerContent - drawerH);
            scrollTarget.put("drawer:themes", Math.max(0, Math.min(max, scrollTarget.getOrDefault("drawer:themes", 0f) - direction * 24f)));
        } else if (!configTab() && mx >= drawerX && mx <= drawerX + drawerW && my >= drawerY && my <= drawerY + drawerH) {
            Module m = selectedModule(shownModules());
            if (m == null) return;
            String key = "drawer:" + m.getLabel();
            float max = Math.max(0, drawerContent - drawerH);
            scrollTarget.put(key, Math.max(0, Math.min(max, scrollTarget.getOrDefault(key, 0f) - direction * 24f)));
        }
    }
}
