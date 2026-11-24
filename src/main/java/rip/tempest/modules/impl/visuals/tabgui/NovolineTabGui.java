package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.FontProcess;

import java.util.List;

public final class NovolineTabGui implements HudTabGui {
    private static final int BG = 0xAA141414, GREY = 0xFFA3A3A3;
    private static final float CAT_W = 65f, ROW = 12f;

    private final TabNav nav = new TabNav();
    private float[] slide = new float[16];
    private float modSlide, setSlide;

    private static CustomFontRenderer font() {
        return FontProcess.getFont("sf");
    }

    @Override
    public void render() {
        nav.frame();
        CustomFontRenderer font = font();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        int accent = TabDraw.accent();
        if (slide.length < cats.size()) slide = new float[cats.size()];

        for (int i = 0; i < cats.size(); i++) {
            float y = i * ROW;
            TabDraw.rect(0, y, CAT_W, y + ROW, BG);
            boolean sel = i == nav.category;
            slide[i] = nav.ease(slide[i], sel ? 3f : 0f, 18f);
            if (sel) TabDraw.rect(1, y + 1, 2, y + ROW - 1, accent);
            TabDraw.text(font, TabDraw.capitalise(cats.get(i).getName()), 3f + slide[i], y + 3f, -1, true);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float catY = nav.category * ROW;
        float longest = 0;
        for (Module m : mods) longest = Math.max(longest, font.getStringWidth(m.getLabel()));
        float modX = CAT_W + 2f, modW = longest + 14f;
        modSlide = nav.ease(modSlide, 3f, 18f);
        for (int j = 0; j < mods.size(); j++) {
            Module m = mods.get(j);
            float y = catY + j * ROW;
            boolean sel = j == nav.module;
            TabDraw.rect(modX, y, modX + modW, y + ROW, sel ? accent : BG);
            TabDraw.text(font, m.getLabel(), modX + 5f + (sel ? modSlide : 0f), y + 3f, m.isEnabled() || sel ? -1 : GREY, true);
        }
        if (nav.depth < 2) return;

        List<Property<?>> sets = nav.currentSettings();
        float longestSet = 0;
        for (Property<?> p : sets) longestSet = Math.max(longestSet, font.getStringWidth(p.getLabel() + ": " + TabNav.value(p)));
        float setX = modX + modW + 2f, setY = catY + nav.module * ROW;
        setSlide = nav.ease(setSlide, 3f, 18f);
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            float y = setY + k * ROW;
            boolean sel = k == nav.setting;
            TabDraw.rect(setX, y, setX + longestSet + 20f, y + ROW, sel ? accent : BG);
            TabDraw.text(font, p.getLabel() + ": " + TabNav.value(p), setX + 5f + (sel ? setSlide : 0f), y + 3f,
                    sel ? (nav.editing ? 0xFFFFFF55 : -1) : GREY, true);
        }
    }

    @Override
    public float width() {
        return CAT_W;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * ROW;
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
