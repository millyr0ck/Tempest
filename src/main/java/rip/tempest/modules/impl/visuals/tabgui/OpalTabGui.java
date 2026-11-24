package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.RoundedEffects;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

public final class OpalTabGui implements HudTabGui {
    private static final int PANEL = 0x80090909, MUTED = 0xFF9C9CA6;
    private static final float WIDTH = 75f, TAB = 16f, BORDER = 2f, GAP = 7f;

    private final TabNav nav = new TabNav();

    private static CustomFontRenderer font() {
        return FontProcess.getStyledFont("productsansbold", 16.5f, Font.PLAIN);
    }

    private void panel(float x, float y, float w, int count) {
        float h = TAB * count;
        if (RoundedEffects.prepareBlur(8f)) RoundedEffects.drawBlur(x - BORDER, y - BORDER, w + BORDER * 2, h + BORDER * 2, 5.5f, 1f);
        RenderUtils.drawRoundedRect(x - BORDER, y - BORDER, w + BORDER * 2, h + BORDER * 2, 5.5f, new Color(PANEL, true));
    }

    private void tab(float x, float y, float w, boolean selected, String left, String right, int textColor) {
        if (selected) {
            Color a = ColorProcess.colors.getFirst(), b = ColorProcess.colors.getSecond();
            Glyphs.gradient(x, y, w, TAB, new Color(a.getRed(), a.getGreen(), a.getBlue(), 102), new Color(b.getRed(), b.getGreen(), b.getBlue(), 102));
        }
        CustomFontRenderer font = font();
        float ty = y + (TAB - font.getHeight()) / 2f;
        TabDraw.text(font, left, x + 5f, ty, textColor, false);
        if (right != null) TabDraw.text(font, right, x + 5f + font.getStringWidth(left), ty, textColor, false);
    }

    @Override
    public void render() {
        nav.frame();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;
        float x = BORDER, y = BORDER;
        panel(x, y, WIDTH, cats.size());
        for (int i = 0; i < cats.size(); i++) {
            boolean sel = i == nav.category;
            tab(x, y + i * TAB, WIDTH, sel, cats.get(i).getName(), null, sel ? -1 : MUTED);
        }
        if (nav.depth < 1) return;

        List<Module> mods = nav.currentModules();
        float modX = x + WIDTH + GAP;
        panel(modX, y, WIDTH, mods.size());
        for (int j = 0; j < mods.size(); j++) {
            boolean sel = j == nav.module;
            tab(modX, y + j * TAB, WIDTH, sel, mods.get(j).getLabel(), null, sel ? -1 : mods.get(j).isEnabled() ? 0xFFE6E6EA : MUTED);
        }
        if (nav.depth < 2) return;

        List<Property<?>> sets = nav.currentSettings();
        CustomFontRenderer font = font();
        float max = 0;
        for (Property<?> p : sets) max = Math.max(max, font.getStringWidth(p.getLabel() + ": " + TabNav.value(p)));
        float setX = modX + WIDTH + GAP, setW = max + 12.5f;
        panel(setX, y, setW, sets.size());
        for (int k = 0; k < sets.size(); k++) {
            Property<?> p = sets.get(k);
            boolean sel = k == nav.setting;
            tab(setX, y + k * TAB, setW, sel, p.getLabel() + ": ", TabNav.value(p), sel ? (nav.editing ? 0xFFFFFF80 : -1) : MUTED);
        }
    }

    @Override
    public float width() {
        return WIDTH + BORDER * 2;
    }

    @Override
    public float height() {
        return TabNav.categories().size() * TAB + BORDER * 2;
    }

    @Override
    public void keyPress(int key) {
        nav.key(key);
    }
}
