package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

public final class StepTabGui implements HudTabGui {
    private static final float ROW = 12.5f, TOP = 4f, CATEGORY_W = 55f;

    private final TabNav nav = new TabNav();
    private final float[] selector = new float[3];
    private float reveal, opacity = 160;
    private long lastKey = System.currentTimeMillis();

    private static CustomFontRenderer font() {
        return FontProcess.getStyledFont("sfdisplaybold", 16, Font.PLAIN);
    }

    public float attachedWidth;

    public float opacity() {
        return opacity;
    }

    public float naturalWidth() {
        CustomFontRenderer font = font();
        float w = CATEGORY_W;
        for (ModuleCategory c : TabNav.categories()) w = Math.max(w, font.getStringWidth(TabDraw.capitalise(c.getName())) + 12f);
        return w - 2;
    }

    private float categoryWidth() {
        return Math.max(naturalWidth(), attachedWidth) + 2;
    }

    private static float columnWidth(List<String> items) {
        CustomFontRenderer font = font();
        float w = 0;
        for (String s : items) w = Math.max(w, font.getStringWidth(s));
        return w + 7f;
    }

    private void column(int index, float left, float right, List<String> items, int selected, List<Integer> colors, int hud, int dank, int bg) {
        CustomFontRenderer font = font();
        boolean first = index == 0;
        float bgTop = first ? 0 : 2;
        float bgBottom = TOP + items.size() * ROW + (first ? 2 : 0.5f);
        TabDraw.rect(left, bgTop, right, bgBottom, bg);
        if (first) {
            float pixel = 1f / new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
            TabDraw.rect(left + 4, 1 - pixel / 2f, right - 4, 1 + pixel / 2f, 0xFFFFFFFF);
        }

        selector[index] = nav.ease(selector[index], TOP + selected * ROW, 14f);
        float sx0 = left + (first ? 2 : 1.5f), sx1 = right - (first ? 2 : 1.5f);
        Glyphs.gradient(sx0, selector[index], sx1 - sx0, ROW, new Color(dank, true), new Color(hud, true));

        for (int i = 0; i < items.size(); i++) {
            float rowTop = TOP + i * ROW;
            TabDraw.text(font, items.get(i), left + 3, rowTop + (ROW - font.getHeight()) / 2f, colors.get(i), false);
        }
    }

    @Override
    public void render() {
        nav.frame();
        List<ModuleCategory> cats = TabNav.categories();
        if (cats.isEmpty()) return;

        opacity = nav.ease(opacity, System.currentTimeMillis() - lastKey > 4500 ? 80 : 160, 6f);
        int bg = TabDraw.withAlpha(0, Math.round(opacity));
        int bright = Math.min(255, Math.round(opacity) + 150);
        int white = 0xFF000000 | bright << 16 | bright << 8 | bright;
        int dim = 0xFF000000 | (bright - 90) << 16 | (bright - 90) << 8 | (bright - 90);

        Color accent = ColorProcess.getColor();
        int hud = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200).getRGB();
        int dank = new Color(Math.max(0, accent.getRed() - 150), Math.max(0, accent.getGreen() - 150), Math.max(0, accent.getBlue() - 150), 200).getRGB();

        List<String> names = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (ModuleCategory c : cats) {
            names.add(TabDraw.capitalise(c.getName()));
            colors.add(white);
        }
        float catW = categoryWidth();

        List<Module> mods = nav.currentModules();
        List<String> modNames = new ArrayList<>();
        List<Integer> modColors = new ArrayList<>();
        for (Module m : mods) {
            modNames.add(m.getLabel());
            modColors.add(m.isEnabled() ? white : dim);
        }
        float modW = columnWidth(modNames);

        List<Property<?>> sets = nav.depth >= 1 ? nav.currentSettings() : List.of();
        List<String> setNames = new ArrayList<>();
        List<Integer> setColors = new ArrayList<>();
        for (Property<?> p : sets) {
            setNames.add(p.getLabel() + " " + TabNav.value(p));
            setColors.add(white);
        }
        float setW = columnWidth(setNames);

        float modLeft = catW, setLeft = catW + modW;
        float target = nav.depth == 0 ? catW : nav.depth == 1 ? setLeft : setLeft + setW + (nav.editing ? 10 : 0);
        reveal = nav.ease(reveal, target, 10f);

        TabDraw.scissor(0, 0, reveal, 1000);
        column(0, 0, catW - 2, names, nav.category, colors, hud, dank, bg);
        if (!mods.isEmpty()) column(1, modLeft, modLeft + modW - 2, modNames, nav.module, modColors, hud, dank, bg);
        if (!sets.isEmpty()) column(2, setLeft, setLeft + setW - 2, setNames, nav.setting, setColors, hud, dank, bg);
        if (nav.editing && !sets.isEmpty()) {
            float x = setLeft + setW + 1, cy = TOP + nav.setting * ROW + ROW / 2f;
            for (int k = 0; k < 4; k++) TabDraw.rect(x + k, cy - k - 0.5f, x + k + 1, cy + k + 0.5f, hud);
        }
        TabDraw.endScissor();
    }

    @Override
    public float width() {
        return categoryWidth() - 2;
    }

    @Override
    public float height() {
        return TOP + TabNav.categories().size() * ROW + 2;
    }

    @Override
    public void keyPress(int key) {
        if (nav.key(key)) lastKey = System.currentTimeMillis();
    }
}
