package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.FontProcess;

import java.util.ArrayList;
import java.util.List;

public final class AstolfoTabGui implements HudTabGui {
    private static final int BG = 0xA0000000;
    private static final float GAP = 3f;

    private final TabNav nav = new TabNav();
    private final float[] selector = new float[3], reveal = new float[3];
    private final List<float[]> slides = new ArrayList<>();

    private static CustomFontRenderer font() {
        return FontProcess.getCurrentFont();
    }

    private static float row() {
        return font().getHeight() + 5f;
    }

    private float[] slides(int column, int size) {
        while (slides.size() <= column) slides.add(new float[0]);
        float[] s = slides.get(column);
        if (s.length < size) {
            float[] grown = new float[size];
            System.arraycopy(s, 0, grown, 0, s.length);
            for (int i = s.length; i < size; i++) grown[i] = 2f;
            slides.set(column, grown);
            s = grown;
        }
        return s;
    }

    private float panel(int column, float x, List<String> items, int selected, List<Integer> colors) {
        CustomFontRenderer font = font();
        float w = 0, r = row();
        for (String s : items) w = Math.max(w, font.getStringWidth(s) + 15f);
        float h = r * items.size();
        boolean open = column <= nav.depth;

        reveal[column] = nav.ease(reveal[column], open ? 1f : 0f, 9f);
        if (reveal[column] < 0.01f) return w;
        TabDraw.scissor(x, 0, w * reveal[column], h);
        TabDraw.box(x, 0, w, h, BG);
        selector[column] = nav.ease(selector[column], selected * r, 9f);
        TabDraw.box(x, selector[column], w, r, TabDraw.accent());
        float[] s = slides(column, items.size());
        for (int i = 0; i < items.size(); i++) {
            s[i] = nav.ease(s[i], i == selected ? 8f : 2f, 6f);
            TabDraw.text(font, items.get(i), x + s[i], i * r + (r - font.getHeight()) / 2f, colors.get(i), true);
        }
        TabDraw.endScissor();
        return w;
    }

    @Override
    public void render() {
        nav.frame();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        List<String> names = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (ModuleCategory c : cats) {
            names.add(c.getName());
            colors.add(-1);
        }
        float x = panel(0, 0, names, nav.category, colors) + GAP;

        List<Module> mods = nav.currentModules();
        names = new ArrayList<>();
        colors = new ArrayList<>();
        for (Module m : mods) {
            names.add(m.getLabel());
            colors.add(m.isEnabled() ? -1 : 0xFFAAAAAA);
        }
        if (!mods.isEmpty()) x += panel(1, x, names, nav.module, colors) + GAP;

        List<Property<?>> sets = nav.depth >= 1 ? nav.currentSettings() : List.of();
        names = new ArrayList<>();
        colors = new ArrayList<>();
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            names.add(p.getLabel() + ": " + TabNav.value(p));
            colors.add(k == nav.setting && nav.editing ? 0xFFFFFF55 : -1);
        }
        if (!sets.isEmpty()) panel(2, x, names, nav.setting, colors);
    }

    @Override
    public float width() {
        CustomFontRenderer font = font();
        float w = 0;
        for (ModuleCategory c : TabNav.categories()) w = Math.max(w, font.getStringWidth(c.getName()) + 15f);
        return w;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * row();
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
