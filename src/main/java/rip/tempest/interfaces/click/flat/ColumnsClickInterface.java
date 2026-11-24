package rip.tempest.interfaces.click.flat;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.Tempest;
import rip.tempest.modules.impl.visuals.ArrayListModule;
import rip.tempest.processes.Themes;
import rip.tempest.utils.misc.LocalConfigFetcher;
import rip.tempest.utils.render.ArrayPattern;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColumnsClickInterface extends FlatGui {
    private static final float W = 118, HEADER = 20, ROW = 15, GAP = 6, PAD = 7, SEARCH_W = 120, SEARCH_H = 15;
    private static final int MAX_ROWS = 20;
    private static final String THEMES = "Themes", PATTERNS = "Patterns", CONFIGS = "Configs";

    private static String search = "";
    private static final Map<String, float[]> positions = new HashMap<>();
    private static final Set<String> folded = new HashSet<>();

    private static String expanded;
    private static final Map<String, Float> scrollTarget = new HashMap<>();

    private static final List<String> order = new ArrayList<>();

    private long openedAt;
    private String dragPanel;
    private float dragOffX, dragOffY;

    private final Map<String, float[]> bodies = new HashMap<>();

    @Override
    public void initGui() {
        super.initGui();
        openedAt = System.currentTimeMillis();
        int i = 0;
        for (ModuleCategory c : ModuleCategory.values()) {
            if (modules(c).isEmpty()) continue;
            String title = name(c);
            positions.putIfAbsent(title, new float[]{8 + i * (W + GAP), 8});
            if (!order.contains(title)) order.add(title);
            i++;
        }

        order.remove(PATTERNS);
        positions.remove(PATTERNS);
        for (String extra : new String[]{THEMES, CONFIGS}) {
            positions.putIfAbsent(extra, new float[]{8 + i * (W + GAP), 8});
            if (!order.contains(extra)) order.add(extra);
            i++;
        }
        configs = null;
    }

    @Override
    protected String search() {
        return search;
    }

    @Override
    protected void setSearch(String text) {
        search = text;
    }

    @Override
    protected void drawGui(float a, Color accent) {
        bodies.clear();
        int index = 0;
        for (String title : new ArrayList<>(order)) {
            float[] pos = positions.get(title);
            if (pos == null) continue;

            float t = clamp((System.currentTimeMillis() - openedAt - index * 45) / 300f);
            index++;
            float pa = a * easeOut(t), py = pos[1] - (1 - easeOut(t)) * 8;
            if (THEMES.equals(title)) drawThemesPanel(pos[0], py, pa, accent);
            else if (CONFIGS.equals(title)) drawConfigsPanel(pos[0], py, pa, accent);
            else {
                ModuleCategory c = category(title);
                if (c != null) drawModulePanel(c, title, pos[0], py, pa, accent);
            }
        }
        searchBox(width - SEARCH_W - 8, 8, SEARCH_W, SEARCH_H, a, accent);
    }

    private float[] panel(String title, String right, float x, float y, float contentH, float a, Color accent) {
        fill(x, y, W, HEADER, alpha(PANEL, a));
        CustomFontRenderer head = heavy(14), small = bold(11);
        stringMid(head, title, x + 6, y, HEADER, WHITE, a);
        stringMid(small, right, x + W - 6 - small.getStringWidth(right), y, HEADER, MUTED, a);
        fill(x, y + HEADER - 1, W, 1, alpha(accent, a));
        hit(x, y, W, HEADER, b -> {
            toFront(title);
            if (b == 0) {
                dragPanel = title;
                dragOffX = mouseX - positions.get(title)[0];
                dragOffY = mouseY - positions.get(title)[1];
            } else if (b == 1 && !folded.remove(title)) folded.add(title);
        });

        float foldT = easeOut(anim("fold:" + title, folded.contains(title) ? 0f : 1f, MEDIUM));
        float room = room(y);
        float bodyH = Math.min(contentH, room) * foldT;
        if (bodyH < 0.5f) return null;
        float by = y + HEADER;
        fill(x, by, W, bodyH, alpha(PANEL, a));
        frame(x, y, W, HEADER + bodyH, alpha(EDGE, a));
        bodies.put(title, new float[]{x, by, W, bodyH, contentH});

        float max = Math.max(0, contentH - room);
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(title, 0f)));
        scrollTarget.put(title, target);
        float scroll = anim("scroll:" + title, target, MEDIUM);
        if (max > 0) {
            float thumbH = Math.max(12, bodyH * bodyH / contentH);
            fill(x + W - 2, by + (bodyH - thumbH) * (scroll / max), 1, thumbH, alpha(new Color(70, 72, 82), a));
        }
        return new float[]{x, by, bodyH, scroll};
    }

    private float room(float panelY) {
        return Math.max(40, Math.min(MAX_ROWS * ROW, height - (panelY + HEADER) - 8));
    }

    private void drawModulePanel(ModuleCategory c, String title, float x, float y, float a, Color accent) {
        List<Module> mods = new ArrayList<>();
        for (Module m : modules(c)) if (search.isEmpty() || matches(m, search)) mods.add(m);
        if (mods.isEmpty()) return;
        int on = 0;
        for (Module m : mods) if (m.isEnabled()) on++;

        float contentH = 0;
        for (Module m : mods) contentH += ROW + settingsHeight(m);
        float[] body = panel(title, on + "/" + mods.size(), x, y, contentH, a, accent);
        if (body == null) return;

        pushClip(x, body[1], W, body[2]);
        float ry = body[1] - body[3];
        for (Module m : mods) ry += drawRow(m, x, ry, a, accent);
        popClip();
    }

    private float settingsHeight(Module m) {
        float t = easeOut(anims.getOrDefault("exp:" + m.getLabel(), 0f));
        if (t <= 0.002f) return 0;
        float h = PAD * 2 - 4;
        for (Property<?> p : settings(m)) h += rowHeight(p) + 4;
        return h * t;
    }

    private float drawRow(Module m, float x, float y, float a, Color accent) {
        String id = m.getLabel();
        List<Property<?>> props = settings(m);
        anim("exp:" + id, id.equals(expanded) && !props.isEmpty() ? 1f : 0f, MEDIUM);
        float sh = settingsHeight(m);

        float on = anim("on:" + id, m.isEnabled() ? 1f : 0f, MEDIUM);
        boolean hover = over(x, y, W, ROW);
        float hv = anim("rowH:" + id, hover ? 1f : 0f, FAST);
        if (on > 0.01f) fill(x, y, W, ROW, alpha(accent, 0.1f * on * a));
        if (hv > 0.01f) fill(x, y, W, ROW, alpha(new Color(255, 255, 255), 0.03f * hv * a));

        float stripe = ROW * easeOut(on);
        if (stripe > 0.2f) fill(x, y + (ROW - stripe) / 2f, 1, stripe, alpha(accent, a));

        CustomFontRenderer f = bold(13), small = bold(11);
        String key = binding == m ? "[...]" : keyName(m);
        float kw = key.isEmpty() ? 0 : small.getStringWidth(key) + 4;
        stringMid(f, trim(f, id, W - 12 - kw), x + 6, y, ROW, mixRgb(MUTED, WHITE, Math.max(on, hv * 0.6f)), a);
        if (!key.isEmpty()) stringMid(small, key, x + W - 6 - small.getStringWidth(key), y, ROW, binding == m ? rgb(accent) : FAINT, a);
        if (hover) tooltip(m, m.getDescription());

        hit(x, y, W, ROW, b -> {
            if (b == 0) m.toggle();
            else if (b == 1 && !props.isEmpty()) {
                expanded = id.equals(expanded) ? null : id;
                openLists.clear();
                openColors.clear();
            } else if (b == 2) startBinding(m);
        });

        if (sh > 0.5f) {
            float sy = y + ROW;
            fill(x + 0.5f, sy, W - 1, sh, alpha(DEEP, a));
            fill(x + 0.5f, sy, W - 1, 0.5f, alpha(EDGE, a));
            pushClip(x, sy, W, sh);
            float py = sy + PAD;
            float sa = a * clamp(anims.getOrDefault("exp:" + id, 0f) * 1.6f - 0.2f);
            for (Property<?> p : props) py += drawSetting(p, x + PAD, py, W - PAD * 2, sa, accent) + 4;
            popClip();
        }
        return ROW + sh;
    }

    private void drawThemesPanel(float x, float y, float a, Color accent) {
        if (!search.isEmpty()) return;
        List<Property<?>> props = themeSettings();
        float settingsH = PAD * 2 - 4;
        for (Property<?> p : props) settingsH += rowHeight(p) + 4;
        Themes.Preset[] presets = Themes.Preset.values();

        float contentH = settingsH + ROW + presets.length * ROW;

        String right = Themes.active() ? Themes.current().toString() : "off";
        float[] body = panel(THEMES, trim(bold(11), right, 50), x, y, contentH, a, accent);
        if (body == null) return;

        pushClip(x, body[1], W, body[2]);
        float top = body[1] - body[3];

        fill(x + 0.5f, top, W - 1, settingsH, alpha(DEEP, a));
        float py = top + PAD;
        for (Property<?> p : props) py += drawSetting(p, x + PAD, py, W - PAD * 2, a, accent) + 4;
        fill(x + 0.5f, top + settingsH - 0.5f, W - 1, 0.5f, alpha(EDGE, a));

        float ry = top + settingsH;
        CustomFontRenderer f = bold(13);
        {
            boolean hover = over(x, ry, W, ROW);
            float hv = anim("patRowH", hover ? 1f : 0f, FAST);
            float open = anims.getOrDefault("patSide", 0f);
            if (hv > 0.01f || open > 0.01f) fill(x, ry, W, ROW, alpha(new Color(255, 255, 255), (0.03f * hv + 0.04f * open) * a));
            stringMid(f, "Flags & patterns", x + 6, ry, ROW, mixRgb(TEXT_2, WHITE, Math.max(hv, open)), a);
            rip.tempest.utils.render.Glyphs.stroke(x + W - 9, ry + ROW / 2f - 2.5f, x + W - 6.5f, ry + ROW / 2f, 1f, alpha(new Color(MUTED), a));
            rip.tempest.utils.render.Glyphs.stroke(x + W - 6.5f, ry + ROW / 2f, x + W - 9, ry + ROW / 2f + 2.5f, 1f, alpha(new Color(MUTED), a));
            hit(x, ry, W, ROW, b -> patternsOpen = !patternsOpen);
            ry += ROW;
            fill(x + 0.5f, ry - 0.5f, W - 1, 0.5f, alpha(EDGE, a));
        }
        for (Themes.Preset preset : presets) {
            if (ry + ROW >= body[1] && ry <= body[1] + body[2]) {
                boolean inUse = themeInUse(preset);
                float on = anim("theme:" + preset, inUse ? 1f : 0f, MEDIUM);
                boolean hover = over(x, ry, W, ROW);
                float hv = anim("themeH:" + preset, hover ? 1f : 0f, FAST);
                if (on > 0.01f) fill(x, ry, W, ROW, alpha(accent, 0.1f * on * a));
                if (hv > 0.01f) fill(x, ry, W, ROW, alpha(new Color(255, 255, 255), 0.03f * hv * a));
                float stripe = ROW * easeOut(on);
                if (stripe > 0.2f) fill(x, ry + (ROW - stripe) / 2f, 1, stripe, alpha(accent, a));
                float sw = 26;
                stringMid(f, trim(f, preset.toString(), W - 18 - sw), x + 6, ry, ROW, mixRgb(MUTED, WHITE, Math.max(on, hv * 0.6f)), a);
                themeStrip(preset, x + W - 6 - sw, ry + (ROW - 4) / 2f, sw, 4, a);
                hit(x, ry, W, ROW, b -> Themes.select(preset));
            }
            ry += ROW;
        }
        popClip();

        float open = easeOut(anim("patSide", patternsOpen ? 1f : 0f, MEDIUM));
        if (open > 0.01f) drawPatternsPanel(x + W + 3 - (1 - open) * 10, y, a * open, accent);
    }

    private static boolean patternsOpen;

    private void drawPatternsPanel(float x, float y, float a, Color accent) {
        if (!search.isEmpty()) return;
        ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(ArrayListModule.class);
        if (list == null) return;
        ArrayPattern[] patterns = ArrayPattern.values();
        ArrayPattern current = list.pattern.getValue();
        String right = current == ArrayPattern.Off ? "off" : current.toString();

        fill(x, y, W, HEADER, alpha(PANEL, a));
        CustomFontRenderer head = heavy(14), small = bold(11);
        stringMid(head, "Patterns", x + 6, y, HEADER, WHITE, a);
        String shownRight = trim(small, right, 44);
        stringMid(small, shownRight, x + W - 16 - small.getStringWidth(shownRight), y, HEADER, MUTED, a);
        float cx = x + W - 7, cy = y + HEADER / 2f;
        Color cross = alpha(new Color(over(cx - 4, cy - 4, 8, 8) ? TEXT : MUTED), a);
        rip.tempest.utils.render.Glyphs.stroke(cx - 2, cy - 2, cx + 2, cy + 2, 1f, cross);
        rip.tempest.utils.render.Glyphs.stroke(cx - 2, cy + 2, cx + 2, cy - 2, 1f, cross);
        hit(x, y, W, HEADER, b -> {
        });
        hit(cx - 5, cy - 5, 10, 10, b -> patternsOpen = false);
        fill(x, y + HEADER - 1, W, 1, alpha(accent, a));
        float contentH = patterns.length * ROW;
        float bodyH = Math.min(contentH, room(y));
        float max = Math.max(0, contentH - bodyH);
        float target = Math.max(0, Math.min(max, scrollTarget.getOrDefault(PATTERNS, 0f)));
        scrollTarget.put(PATTERNS, target);
        float scrolled = anim("scroll:" + PATTERNS, target, MEDIUM);
        fill(x, y + HEADER, W, bodyH, alpha(PANEL, a));
        frame(x, y, W, HEADER + bodyH, alpha(EDGE, a));
        bodies.put(PATTERNS, new float[]{x, y + HEADER, W, bodyH, contentH});
        if (max > 0) {
            float thumbH = Math.max(12, bodyH * bodyH / contentH);
            fill(x + W - 2, y + HEADER + (bodyH - thumbH) * (scrolled / max), 1, thumbH, alpha(new Color(70, 72, 82), a));
        }
        float[] body = {x, y + HEADER, bodyH, scrolled};

        pushClip(x, body[1], W, body[2]);
        float ry = body[1] - body[3];
        CustomFontRenderer f = bold(13);
        double t = System.nanoTime() / 1.0e9;
        for (ArrayPattern pattern : patterns) {
            if (ry + ROW >= body[1] && ry <= body[1] + body[2]) {
                boolean selected = current == pattern;
                float on = anim("pat:" + pattern, selected ? 1f : 0f, MEDIUM);
                boolean hover = over(x, ry, W, ROW);
                float hv = anim("patH:" + pattern, hover ? 1f : 0f, FAST);
                if (on > 0.01f) fill(x, ry, W, ROW, alpha(accent, 0.1f * on * a));
                if (hv > 0.01f) fill(x, ry, W, ROW, alpha(new Color(255, 255, 255), 0.03f * hv * a));
                float stripe = ROW * easeOut(on);
                if (stripe > 0.2f) fill(x, ry + (ROW - stripe) / 2f, 1, stripe, alpha(accent, a));
                float sw = 26, sh = 6, sx = x + W - 6 - sw, sy = ry + (ROW - sh) / 2f;
                String label = pattern == ArrayPattern.Off ? "None" : pattern.toString();
                stringMid(f, trim(f, label, W - 18 - sw), x + 6, ry, ROW, mixRgb(MUTED, WHITE, Math.max(on, hv * 0.6f)), a);
                if (pattern != ArrayPattern.Off) {
                    for (float bx = 0; bx < sw; bx += 2) {
                        for (float by = 0; by < sh; by += 2) {
                            int c = pattern.color(bx + 1, by + 1, sw, sh, t, list.motion());
                            fill(sx + bx, sy + by, 2, 2, alpha(new Color(c & 0xFFFFFF), a));
                        }
                    }
                }
                hit(x, ry, W, ROW, b -> list.pattern.setValue(pattern));
            }
            ry += ROW;
        }
        popClip();
    }

    private List<String> configs;
    private String configName = "", status = "", confirmDelete, armedDelete;
    private boolean configFocused;
    private long statusAt;

    private void drawConfigsPanel(float x, float y, float a, Color accent) {
        if (!search.isEmpty()) return;
        if (configs == null) configs = LocalConfigFetcher.fetchConfigList();
        float topH = PAD + 14 + PAD;
        float contentH = topH + Math.max(1, configs.size()) * ROW;
        boolean showStatus = !status.isEmpty() && System.currentTimeMillis() - statusAt < 3000;
        float[] body = panel(CONFIGS, showStatus ? trim(bold(11), status, 70) : String.valueOf(configs.size()), x, y, contentH, a, accent);
        if (body == null) return;

        pushClip(x, body[1], W, body[2]);
        float top = body[1] - body[3];

        fill(x + 0.5f, top, W - 1, topH, alpha(DEEP, a));
        CustomFontRenderer f = bold(12.5f);
        float fx = x + PAD, fy = top + PAD, fw = W - PAD * 2 - 34, fh = 14;
        boolean fHover = over(fx, fy, fw, fh);
        fill(fx, fy, fw, fh, alpha(configFocused || fHover ? FIELD : PANEL, a));
        frame(fx, fy, fw, fh, alpha(configFocused ? accent : EDGE_2, a));
        if (configName.isEmpty() && !configFocused) stringMid(f, "new config", fx + 4, fy, fh, FAINT, a);
        else {
            String shown = trim(f, configName, fw - 8);
            stringMid(f, shown, fx + 4, fy, fh, TEXT, a);
            if (configFocused && caretOn()) fill(fx + 4.5f + f.getStringWidth(shown), fy + 3.5f, 0.5f, fh - 7, alpha(new Color(TEXT), a));
        }
        hit(fx, fy, fw, fh, b -> configFocused = true);
        button("cfg:save", "Save", fx + fw + 4, fy, 30, fh, a, accent, true, this::submitOwn);
        fill(x + 0.5f, top + topH - 0.5f, W - 1, 0.5f, alpha(EDGE, a));

        float ry = top + topH;
        CustomFontRenderer name = bold(13), small = bold(11);
        if (configs.isEmpty()) stringMid(small, "none saved yet", x + 6, ry, ROW, MUTED, a);
        for (String cfg : configs) {
            if (ry + ROW >= body[1] && ry <= body[1] + body[2]) {
                boolean hover = over(x, ry, W, ROW);
                float hv = anim("cfgH:" + cfg, hover ? 1f : 0f, FAST);
                if (hv > 0.01f) fill(x, ry, W, ROW, alpha(new Color(255, 255, 255), 0.03f * hv * a));
                hit(x, ry, W, ROW, b -> {
                    boolean ok = LocalConfigFetcher.loadConfig(cfg);
                    status(ok ? "loaded " + cfg : "load failed");
                });
                float bx = x + W - 6;
                if (hv > 0.05f) {
                    boolean sure = cfg.equals(confirmDelete);
                    String del = sure ? "sure?" : "delete";
                    float dw = small.getStringWidth(del);
                    bx -= dw;
                    boolean dh = over(bx - 2, ry, dw + 4, ROW);
                    stringMid(small, del, bx, ry, ROW, sure ? 0xFF7A70 : dh ? TEXT : MUTED, a * hv);
                    hit(bx - 2, ry, dw + 4, ROW, b -> {
                        if (cfg.equals(armedDelete)) {
                            boolean ok = Tempest.INSTANCE.getConfigManager().deleteConfig(cfg);
                            status(ok ? "deleted " + cfg : "delete failed");
                            configs = null;
                            confirmDelete = null;
                        } else confirmDelete = cfg;
                    });
                    String sv = "save";
                    float svw = small.getStringWidth(sv);
                    bx -= svw + 6;
                    boolean svh = over(bx - 2, ry, svw + 4, ROW);
                    stringMid(small, sv, bx, ry, ROW, svh ? TEXT : MUTED, a * hv);
                    hit(bx - 2, ry, svw + 4, ROW, b -> {
                        boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(cfg);
                        status(ok ? "saved " + cfg : "save failed");
                    });
                }
                stringMid(name, trim(name, cfg, bx - x - 12), x + 6, ry, ROW, mixRgb(TEXT_2, WHITE, hv), a);
            }
            ry += ROW;
        }
        popClip();
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
    protected boolean editOwn(java.util.function.UnaryOperator<String> change) {
        if (!configFocused) return false;
        configName = change.apply(configName);
        return true;
    }

    @Override
    protected void submitOwn() {
        String n = configName.trim();
        if (n.isEmpty()) return;
        boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(n);
        status(ok ? "saved " + n : "save failed");
        configName = "";
        configs = null;
    }

    private static String name(ModuleCategory c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private static ModuleCategory category(String title) {
        for (ModuleCategory c : ModuleCategory.values()) if (name(c).equals(title)) return c;
        return null;
    }

    private void toFront(String title) {
        order.remove(title);
        order.add(title);
    }

    @Override
    protected void dragging() {
        if (dragPanel == null) return;
        float[] p = positions.get(dragPanel);
        p[0] = Math.max(-W + 30, Math.min(width - 30, mouseX - dragOffX));
        p[1] = Math.max(0, Math.min(height - HEADER, mouseY - dragOffY));
    }

    @Override
    protected void released() {
        dragPanel = null;
    }

    @Override
    protected void scrolled(int mx, int my, int direction) {
        float[] side = bodies.get(PATTERNS);
        if (side != null && mx >= side[0] && mx <= side[0] + side[2] && my >= side[1] && my <= side[1] + side[3]) {
            float max = Math.max(0, side[4] - side[3]);
            scrollTarget.put(PATTERNS, Math.max(0, Math.min(max, scrollTarget.getOrDefault(PATTERNS, 0f) - direction * 30f)));
            return;
        }

        for (int i = order.size() - 1; i >= 0; i--) {
            String title = order.get(i);
            float[] b = bodies.get(title);
            if (b == null || mx < b[0] || mx > b[0] + b[2] || my < b[1] || my > b[1] + b[3]) continue;
            float room = room(b[1] - HEADER);
            float max = Math.max(0, b[4] - room);
            scrollTarget.put(title, Math.max(0, Math.min(max, scrollTarget.getOrDefault(title, 0f) - direction * 30f)));
            return;
        }
    }
}
