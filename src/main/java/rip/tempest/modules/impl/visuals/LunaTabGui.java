package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

final class LunaTabGui {
    private static final String FONT = "comfortaa";

    private static final int BACKGROUND = 0xD2120F1C;
    private static final int GOLD = 0xFFE8D05A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DISABLED = 0xFFA0A0A8;

    private static final int BORDER = 1;
    private static final int PAD_X = 8;
    private static final int HEADER_HEIGHT = 16;
    private static final int ROW = 14;
    private static final int LIST_PAD = 2;
    private static final int GAP = 3;

    private static final float SHIFT = 7f;
    private static final int MAX_MOD_ROWS = 12;

    private enum Section { CATEGORY, MODS }

    private static CustomFontRenderer font;
    private static CustomFontRenderer titleFont;

    private Section section = Section.CATEGORY;
    private int categoryTab;
    private int modTab;
    private int modScroll;
    private float[] categoryShift = new float[0];
    private float[] modShift = new float[0];
    private long lastFrame;

    private int panelWidth;
    private int panelHeight;
    private int modPanelWidth;
    private int modPanelHeight;

    static CustomFontRenderer font() {
        if (font == null) font = new CustomFontRenderer(FONT, 18, Font.PLAIN, true, false);
        return font;
    }

    private static CustomFontRenderer titleFont() {
        if (titleFont == null) titleFont = new CustomFontRenderer(FONT, 30, Font.PLAIN, true, false);
        return titleFont;
    }

    void render(String title) {
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;
        categoryTab = Math.max(0, Math.min(categoryTab, categories.size() - 1));

        CustomFontRenderer f = font();
        CustomFontRenderer t = titleFont();
        float ease = ease();

        int widest = 0;
        for (ModuleCategory category : categories) {
            widest = Math.max(widest, f.getStringWidth(label(category)));
        }
        panelWidth = Math.max(PAD_X + widest + (int) SHIFT + PAD_X, t.getStringWidth(title) + PAD_X * 2);
        int listTop = HEADER_HEIGHT + BORDER + LIST_PAD;
        panelHeight = listTop + categories.size() * ROW + LIST_PAD + BORDER;

        drawBox(0, 0, panelWidth, panelHeight);
        Gui.drawRect(BORDER, HEADER_HEIGHT, panelWidth - BORDER, HEADER_HEIGHT + BORDER, GOLD);
        t.drawStringWithShadow(title, (panelWidth - t.getStringWidth(title)) / 2f,
                (HEADER_HEIGHT - t.getHeight()) / 2f + 1, TEXT);

        categoryShift = resize(categoryShift, categories.size());
        for (int i = 0; i < categories.size(); i++) {
            boolean selected = i == categoryTab;
            categoryShift[i] += ((selected ? SHIFT : 0f) - categoryShift[i]) * ease;
            drawEntry(f, label(categories.get(i)), 0, listTop + i * ROW, categoryShift[i], selected, TEXT);
        }

        modPanelWidth = 0;
        modPanelHeight = 0;
        if (section != Section.MODS) return;

        List<Module> mods = modules(categories.get(categoryTab));
        if (mods.isEmpty()) {
            section = Section.CATEGORY;
            return;
        }

        int visible = Math.min(mods.size(), MAX_MOD_ROWS);
        int widestMod = 0;
        for (Module module : mods) {
            widestMod = Math.max(widestMod, f.getStringWidth(module.getLabel()));
        }
        int x = panelWidth + GAP;
        int top = listTop + categoryTab * ROW - LIST_PAD - BORDER;
        modPanelWidth = PAD_X + widestMod + (int) SHIFT + PAD_X;
        modPanelHeight = BORDER + LIST_PAD + visible * ROW + LIST_PAD + BORDER;

        drawBox(x, top, modPanelWidth, modPanelHeight);

        modShift = resize(modShift, mods.size());
        for (int i = modScroll; i < modScroll + visible && i < mods.size(); i++) {
            Module module = mods.get(i);
            boolean selected = i == modTab;
            modShift[i] += ((selected ? SHIFT : 0f) - modShift[i]) * ease;
            int rowTop = top + BORDER + LIST_PAD + (i - modScroll) * ROW;
            drawEntry(f, module.getLabel(), x, rowTop, modShift[i], selected, module.isEnabled() ? TEXT : DISABLED);
        }
    }

    void keyPress(int key) {
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;
        int count = categories.size();
        categoryTab = Math.max(0, Math.min(categoryTab, count - 1));

        if (section == Section.CATEGORY) {
            if (key == Keyboard.KEY_RIGHT) {
                if (modules(categories.get(categoryTab)).isEmpty()) return;
                modTab = 0;
                modScroll = 0;
                modShift = new float[0];
                section = Section.MODS;
            } else if (key == Keyboard.KEY_DOWN) {
                categoryTab = (categoryTab + 1) % count;
            } else if (key == Keyboard.KEY_UP) {
                categoryTab = (categoryTab - 1 + count) % count;
            }
        } else {
            List<Module> mods = modules(categories.get(categoryTab));
            if (mods.isEmpty()) {
                section = Section.CATEGORY;
                return;
            }
            if (key == Keyboard.KEY_LEFT) {
                section = Section.CATEGORY;
            } else if (key == Keyboard.KEY_RIGHT) {
                if (modTab < mods.size()) mods.get(modTab).toggle();
            } else if (key == Keyboard.KEY_DOWN) {
                modTab = (modTab + 1) % mods.size();
                scrollToSelection(mods.size());
            } else if (key == Keyboard.KEY_UP) {
                modTab = (modTab - 1 + mods.size()) % mods.size();
                scrollToSelection(mods.size());
            }
        }
    }

    int width() {
        return modPanelWidth > 0 ? panelWidth + GAP + modPanelWidth : Math.max(panelWidth, 1);
    }

    int height() {
        return Math.max(panelHeight, 1);
    }

    private void drawEntry(CustomFontRenderer f, String text, int x, int rowTop, float shift, boolean selected, int color) {
        float textY = rowTop + (ROW - f.getHeight()) / 2f;
        if (selected) {
            f.drawStringWithShadow(">", x + PAD_X, textY, TEXT);
        }
        f.drawStringWithShadow(text, x + PAD_X + shift, textY, color);
    }

    private void drawBox(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, BACKGROUND);
        Gui.drawRect(x, y, x + width, y + BORDER, GOLD);
        Gui.drawRect(x, y + height - BORDER, x + width, y + height, GOLD);
        Gui.drawRect(x, y + BORDER, x + BORDER, y + height - BORDER, GOLD);
        Gui.drawRect(x + width - BORDER, y + BORDER, x + width, y + height - BORDER, GOLD);
    }

    private void scrollToSelection(int size) {
        int visible = Math.min(size, MAX_MOD_ROWS);
        if (modTab < modScroll) modScroll = modTab;
        if (modTab >= modScroll + visible) modScroll = modTab - visible + 1;
    }

    private float ease() {
        long now = System.currentTimeMillis();
        long elapsed = lastFrame == 0 ? 0 : Math.min(now - lastFrame, 100);
        lastFrame = now;
        return 1f - (float) Math.exp(-elapsed / 45.0);
    }

    private static float[] resize(float[] values, int size) {
        if (values.length == size) return values;
        float[] resized = new float[size];
        System.arraycopy(values, 0, resized, 0, Math.min(values.length, size));
        return resized;
    }

    private static List<ModuleCategory> categories() {
        List<ModuleCategory> list = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            if (!Tempest.INSTANCE.getModuleManager().getModulesForCategory(category).isEmpty()) list.add(category);
        }
        return list;
    }

    private static List<Module> modules(ModuleCategory category) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(category));
        CustomFontRenderer f = font();
        list.sort((a, b) -> {
            int cmp = f.getStringWidth(b.getLabel()) - f.getStringWidth(a.getLabel());
            return cmp != 0 ? cmp : b.getLabel().compareTo(a.getLabel());
        });
        return list;
    }

    private static String label(ModuleCategory category) {
        String name = category.name().toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
