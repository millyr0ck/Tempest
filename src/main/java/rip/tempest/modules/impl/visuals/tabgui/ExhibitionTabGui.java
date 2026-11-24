package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.FontProcess;

import java.util.List;

public final class ExhibitionTabGui implements HudTabGui {
    private static final float ROW = 12f;
    private static final long IDLE_MS = 3000;

    private final TabNav nav = new TabNav();
    private float opacity = 45f;
    private long lastUsed;
    private float catSel, modSel, setSel;

    private static CustomFontRenderer font() {
        return FontProcess.getFont("exhibition");
    }

    private float catWidth(CustomFontRenderer font) {
        float w = 0;
        for (ModuleCategory c : TabNav.categories()) w = Math.max(w, font.getStringWidth(c.name()));
        return w + 10f;
    }

    @Override
    public void render() {
        nav.frame();
        CustomFontRenderer font = font();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        boolean active = System.currentTimeMillis() - lastUsed < IDLE_MS;
        opacity = nav.ease(opacity, active ? 200f : 30f, 6f);
        int box = TabDraw.withAlpha(0, Math.round(opacity));
        int sel = TabDraw.withAlpha(TabDraw.accent(), Math.round(opacity) + 64);
        int text = TabDraw.withAlpha(0xAFAFAF, Math.round(opacity) + 64);

        float catW = catWidth(font);
        TabDraw.rect(0, 0, catW, cats.size() * ROW + 1, box);
        catSel = nav.ease(catSel, nav.category * ROW, 16f);
        TabDraw.rect(0.3f, catSel + 0.3f, catW - 0.3f, catSel + ROW - 0.3f, sel);
        for (int i = 0; i < cats.size(); i++) {
            boolean s = i == nav.category;
            TabDraw.text(font, cats.get(i).name(), s ? 4 : 2, i * ROW + 1.5f, s ? -1 : text, true);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float modX = catW + 1, longest = 0, catY = nav.category * ROW;
        for (Module m : mods) longest = Math.max(longest, font.getStringWidth(m.getLabel()));
        float modW = longest + 10f;
        TabDraw.rect(modX, catY, modX + modW, catY + mods.size() * ROW, box);
        modSel = nav.ease(modSel, catY + nav.module * ROW, 16f);
        TabDraw.rect(modX + 0.3f, modSel + 0.3f, modX + modW - 0.3f, modSel + ROW - 0.3f, sel);
        for (int j = 0; j < mods.size(); j++) {
            Module m = mods.get(j);
            boolean s = j == nav.module;
            TabDraw.text(font, m.getLabel(), modX + (s ? 4 : 2), catY + j * ROW + 1.5f, s || m.isEnabled() ? -1 : text, true);
        }
        if (nav.depth < 2) return;

        List<Property<?>> sets = nav.currentSettings();
        float setX = modX + modW + 1, longestSet = 0, setY = catY + nav.module * ROW;
        for (Property<?> p : sets) longestSet = Math.max(longestSet, font.getStringWidth(p.getLabel() + ": " + TabNav.value(p)));
        float setW = longestSet + 12f;
        TabDraw.rect(setX, setY, setX + setW, setY + sets.size() * ROW, box);
        setSel = nav.ease(setSel, setY + nav.setting * ROW, 16f);
        TabDraw.rect(setX + 0.3f, setSel + 0.3f, setX + setW - 0.3f, setSel + ROW - 0.3f, sel);
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            boolean s = k == nav.setting;
            String line = p.getLabel() + ": §l" + TabNav.value(p);
            TabDraw.text(font, line, setX + (s ? 4 : 2), setY + k * ROW + 1.5f, s ? (nav.editing ? 0xFFFFFF55 : -1) : text, true);
        }
    }

    @Override
    public float width() {
        return catWidth(font());
    }

    @Override
    public float height() {
        return TabNav.categories().size() * ROW + 1;
    }

    @Override
    public void keyPress(int key) {
        if (nav.key(key)) lastUsed = System.currentTimeMillis();
    }
}
