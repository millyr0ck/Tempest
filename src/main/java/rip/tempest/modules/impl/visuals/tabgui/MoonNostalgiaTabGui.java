package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;

import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class MoonNostalgiaTabGui implements HudTabGui {
    private static final int BORDER = 0xFF000000, FILL = 0x60000000, OFF = 0xFF808080;
    private static final float WIDTH = 70f;

    private final TabNav nav = new TabNav();

    private static float catRow() {
        return mc.fontRendererObj.FONT_HEIGHT + 4;
    }

    private static float row() {
        return mc.fontRendererObj.FONT_HEIGHT + 2;
    }

    @Override
    public void render() {
        nav.frame();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        float cr = catRow(), r = row();
        TabDraw.bordered(0, 0, WIDTH, cr * cats.size(), 0.5f, BORDER, FILL);
        for (int i = 0; i < cats.size(); i++) {
            boolean sel = i == nav.category;
            TabDraw.mcText(TabDraw.capitalise(cats.get(i).getName()), 2f + (sel ? 7 : 0), i * cr + 2.5f, sel ? -1 : OFF);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float catY = nav.category * cr;
        int longest = 0;
        for (Module m : mods) longest = Math.max(longest, mc.fontRendererObj.getStringWidth(m.getLabel()));
        float modX = WIDTH + 2f, modW = longest + 22f;
        TabDraw.bordered(modX, catY, modW, r * mods.size() + 2, 0.5f, BORDER, FILL);
        for (int j = 0; j < mods.size(); j++) {
            Module m = mods.get(j);
            TabDraw.mcText(m.getLabel(), modX + 2f + (j == nav.module ? 8 : 0), catY + j * r + 2.5f, m.isEnabled() ? -1 : OFF);
        }
        if (nav.depth < 2) return;

        List<Property<?>> sets = nav.currentSettings();
        int longestSet = 0;
        for (Property<?> p : sets) longestSet = Math.max(longestSet, mc.fontRendererObj.getStringWidth(p.getLabel() + ": " + TabNav.value(p)));
        float setX = modX + modW + 2f, setY = catY + nav.module * r;
        TabDraw.bordered(setX, setY, longestSet + 15f, r * sets.size() + 2, 0.5f, BORDER, FILL);
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            boolean sel = k == nav.setting;
            String text = p.getLabel() + ": " + (sel && nav.editing ? "§e" : "§7") + TabNav.value(p);
            TabDraw.mcText(text, setX + 2f + (sel ? 8 : 0), setY + k * r + 2.5f, sel ? -1 : OFF);
        }
    }

    @Override
    public float width() {
        return WIDTH;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * catRow();
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
