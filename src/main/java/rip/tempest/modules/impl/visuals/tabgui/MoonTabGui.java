package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.FontProcess;

import java.util.List;

public final class MoonTabGui implements HudTabGui {
    private static final int BG = 0xBB212121, OFF = 0xFF808080;
    private static final float CAT_W = 60f, TAB_W = 70f, CAT_ROW = 12f;

    private final TabNav nav = new TabNav();
    private float catBar, catShift[] = new float[16], modBar, modShift, setBar;

    private static CustomFontRenderer font() {
        return FontProcess.getFont("moon");
    }

    private static float row() {
        return font().getHeight() + 3f;
    }

    @Override
    public void render() {
        nav.frame();
        CustomFontRenderer font = font();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        int accent = TabDraw.accent();

        TabDraw.box(0, 0, CAT_W, cats.size() * CAT_ROW + 1, BG);
        catBar = nav.ease(catBar, nav.category * CAT_ROW, 14f);
        TabDraw.box(0, catBar, CAT_W, 13, accent);
        if (catShift.length < cats.size()) catShift = new float[cats.size()];
        for (int i = 0; i < cats.size(); i++) {
            catShift[i] = nav.ease(catShift[i], i == nav.category ? 5f : 2f, 14f);
            TabDraw.text(font, TabDraw.capitalise(cats.get(i).getName()), catShift[i], i * CAT_ROW + 7f - font.getHeight() / 2f, -1, true);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float catY = nav.category * CAT_ROW;
        float longest = 0;
        for (Module m : mods) longest = Math.max(longest, font.getStringWidth(m.getLabel()));
        float modX = TAB_W + 2f - 10f, modW = longest + 12f, r = row();
        TabDraw.box(modX, catY, modW, r * mods.size() + 2f, BG);
        modBar = nav.ease(modBar, catY + nav.module * r, 14f);
        TabDraw.box(modX, modBar, modW, r + 2f, accent);
        for (int j = 0; j < mods.size(); j++) {
            Module m = mods.get(j);
            float shift = j == nav.module ? (modShift = nav.ease(modShift, 5f, 14f)) : 2f;
            TabDraw.text(font, m.getLabel(), modX + shift, catY + j * r + r / 2f - font.getHeight() / 2f + 1.5f, m.isEnabled() ? -1 : OFF, true);
        }
        if (nav.depth < 2) return;

        List<Property<?>> sets = nav.currentSettings();
        float longestSet = 0;
        for (Property<?> p : sets) longestSet = Math.max(longestSet, font.getStringWidth(p.getLabel() + ": " + TabNav.value(p)));
        float setX = TAB_W + 2f + longest + 12f - 8f, setW = longestSet + 15f;
        TabDraw.box(setX, catY, setW, r * sets.size(), BG);
        setBar = nav.ease(setBar, catY + nav.setting * r, 14f);
        TabDraw.box(setX, setBar, setW, r, accent);
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            boolean sel = k == nav.setting;
            String value = TabNav.value(p);
            float ty = catY + k * r + r / 2f - font.getHeight() / 2f + 1.5f;
            TabDraw.text(font, p.getLabel() + ": ", setX + (sel ? 5f : 2f), ty, -1, true);
            TabDraw.text(font, value, setX + (sel ? 5f : 2f) + font.getStringWidth(p.getLabel() + ": "), ty, sel && nav.editing ? 0xFFFFFF55 : 0xFFDDDDDD, true);
        }
    }

    @Override
    public float width() {
        return CAT_W;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * CAT_ROW + 1;
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
