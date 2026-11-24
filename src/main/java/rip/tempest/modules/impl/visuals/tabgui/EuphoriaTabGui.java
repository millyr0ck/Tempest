package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.FontProcess;

import java.util.List;

public final class EuphoriaTabGui implements HudTabGui {
    private static final float ROW_H = 13f, PAD_X = 6f, PAD_Y = 4f, CAT_W = 72f, MOD_W = 82f, SET_W = 105f, GAP = 4f;
    private static final int BG = 0xA0000000, TINT = 0x1EFFFFFF, GREY = 0xFF8C8C8C, LABEL = 0xFFDCDCDC, VALUE = 0xFFA0A0A0;

    private final TabNav nav = new TabNav();
    private float catSel, modSel, setSel;

    private static CustomFontRenderer font() {
        return FontProcess.getFont("sf");
    }

    private float select(float current, float target) {
        return nav.ease(current, target, 12f);
    }

    @Override
    public void render() {
        nav.frame();
        CustomFontRenderer fr = font();
        int accent = TabDraw.accent();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;

        float h = cats.size() * ROW_H + PAD_Y;
        TabDraw.rect(0, 0, CAT_W, h, BG);
        catSel = select(catSel, PAD_Y / 2f + nav.category * ROW_H);
        TabDraw.rect(0, catSel, 2, catSel + ROW_H, accent);
        TabDraw.rect(0, catSel, CAT_W, catSel + ROW_H, TINT);
        for (int i = 0; i < cats.size(); i++) {
            TabDraw.text(fr, cats.get(i).getName(), PAD_X, PAD_Y / 2f + i * ROW_H + 1.5f, i == nav.category ? -1 : GREY, true);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float x = CAT_W + GAP;
        TabDraw.rect(x, 0, x + MOD_W, mods.size() * ROW_H + PAD_Y, BG);
        modSel = select(modSel, PAD_Y / 2f + nav.module * ROW_H);
        TabDraw.rect(x, modSel, x + 2, modSel + ROW_H, accent);
        TabDraw.rect(x, modSel, x + MOD_W, modSel + ROW_H, TINT);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            boolean sel = i == nav.module;
            int color = sel ? -1 : m.isEnabled() ? accent : GREY;
            float y = PAD_Y / 2f + i * ROW_H + 1.5f;
            String label = trim(fr, m.getLabel(), MOD_W - PAD_X * 2 - 6);
            TabDraw.text(fr, label, x + PAD_X, y, color, true);
            if (!TabNav.settings(m).isEmpty()) TabDraw.text(fr, ">", x + MOD_W - 8, y, 0xFF646464, true);
        }
        if (nav.depth < 2) return;

        List<Property<?>> props = nav.currentSettings();
        x += MOD_W + GAP;
        TabDraw.rect(x, 0, x + SET_W, props.size() * ROW_H + PAD_Y, BG);
        setSel = select(setSel, PAD_Y / 2f + nav.setting * ROW_H);
        TabDraw.rect(x, setSel, x + 2, setSel + ROW_H, accent);
        TabDraw.rect(x, setSel, x + SET_W, setSel + ROW_H, TINT);
        for (int i = 0; i < props.size(); i++) {
            Property<?> p = props.get(i);
            float rowY = PAD_Y / 2f + i * ROW_H + 1.5f;
            boolean editingThis = i == nav.setting && nav.editing;
            if (p instanceof NumberProperty n) {
                double pct = (n.getValue() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
                TabDraw.rect(x + PAD_X, rowY - 1, x + SET_W - PAD_X, rowY + ROW_H - 3, 0x3C000000);
                TabDraw.rect(x + PAD_X, rowY - 1, x + PAD_X + (float) ((SET_W - PAD_X * 2) * pct), rowY + ROW_H - 3, TabDraw.withAlpha(accent, editingThis ? 170 : 100));
                TabDraw.text(fr, p.getLabel() + "  " + TabNav.value(p), x + PAD_X + 2, rowY, -1, true);
            } else if (p.getValue() instanceof Boolean on) {
                String val = on ? "On" : "Off";
                TabDraw.text(fr, p.getLabel(), x + PAD_X, rowY, LABEL, true);
                TabDraw.text(fr, val, x + SET_W - PAD_X - fr.getStringWidth(val), rowY, on ? accent : GREY, true);
            } else {
                String val = TabNav.value(p);
                TabDraw.text(fr, p.getLabel(), x + PAD_X, rowY, LABEL, true);
                TabDraw.text(fr, val, x + SET_W - PAD_X - fr.getStringWidth(val), rowY, editingThis ? accent : VALUE, true);
            }
        }
    }

    private static String trim(CustomFontRenderer fr, String s, float w) {
        if (fr.getStringWidth(s) <= w) return s;
        while (s.length() > 1 && fr.getStringWidth(s + "..") > w) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    @Override
    public float width() {
        return CAT_W;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * ROW_H + PAD_Y;
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
