package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.utils.render.VirtueFont;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

final class VirtueTabGui {
    private static final int INSIDE = 0xA0000000;
    private static final int TEXT = 0xFFFFFFFF, TEXT_OFF = 0xFFA3A3A3, SELECTED_OFF = 0xFFD1D1D1, UNSELECTED_ON = 0xFFDEDEDE;
    private static final int NEW_SELECTED = 0xFF1C1C1C, NEW_BOX = 0xFF333333, NEW_BORDER = 0xFF000000;
    static final int MAX_MOD_ROWS = 15;

    private final boolean lucid;

    private final int step;

    private boolean inMods;
    private int categoryTab, modTab;
    private int categoryPosition = 15, categoryTarget = 15, modPosition = 15, modTarget = 15;
    private boolean transitionQuickly;
    private long lastUpdate;
    private int modWidth;
    private int modScroll;

    private int scrollTo(int count) {
        int shown = Math.min(count, MAX_MOD_ROWS);
        if (modTab < modScroll) modScroll = modTab;
        if (modTab >= modScroll + shown) modScroll = modTab - shown + 1;
        modScroll = Math.max(0, Math.min(count - shown, modScroll));
        return shown;
    }

    VirtueTabGui(boolean lucid) {
        this.lucid = lucid;
        this.step = lucid ? 14 : 18;
    }

    private static FontRenderer font() {
        return VirtueFont.get();
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
        list.sort((a, b) -> a.getLabel().compareTo(b.getLabel()));
        return list;
    }

    private static String capitalize(String name) {
        String lower = name.toLowerCase();
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int categoryWidth(List<ModuleCategory> categories) {
        int widest = 0;
        for (ModuleCategory category : categories) widest = Math.max(widest, font().getStringWidth(capitalize(category.name())));
        return widest + (lucid ? 6 : 5);
    }

    float width() {
        List<ModuleCategory> categories = categories();
        return categoryWidth(categories) + 2 + (inMods ? modWidth + 4 : 0);
    }

    float height() {
        int rows = categories().size();
        if (inMods && categoryTab < categories().size()) rows = Math.max(rows, Math.min(MAX_MOD_ROWS, modules(categories().get(categoryTab)).size()));
        return 14 + rows * (lucid ? 14 : 19) + 2;
    }

    void render() {
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;
        categoryTab = Math.max(0, Math.min(categories.size() - 1, categoryTab));
        updateBars();
        if (lucid) {
            renderLucid(categories);
        } else {
            renderNew(categories);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderLucid(List<ModuleCategory> categories) {
        FontRenderer font = font();
        int categoryWidth = categoryWidth(categories), categoryHeight = categories.size() * 14 + 2;
        rect(2, 14, 2 + categoryWidth, 14 + categoryHeight, INSIDE);
        rect(3, categoryPosition, 2 + categoryWidth - 1, categoryPosition + 14, INSIDE);
        int y = 15;
        for (int i = 0; i < categories.size(); i++) {
            String name = capitalize(categories.get(i).name());
            font.drawStringWithShadow(name, categoryWidth / 2 - font.getStringWidth(name) / 2 + 3, y + 3, categoryTab == i ? TEXT : TEXT_OFF);
            y += 14;
        }
        if (inMods) {
            List<Module> mods = modules(categories.get(categoryTab));
            modTab = Math.max(0, Math.min(mods.size() - 1, modTab));
            int shown = scrollTo(mods.size());
            rect(categoryWidth + 4, categoryPosition - 1, categoryWidth + 2 + modWidth, categoryPosition + shown * 14 + 1, INSIDE);
            int bar = modPosition - modScroll * 14;
            rect(categoryWidth + 5, bar, categoryWidth + modWidth + 1, bar + 14, INSIDE);
            y = categoryPosition;
            for (int i = modScroll; i < modScroll + shown; i++) {
                Module mod = mods.get(i);
                String name = mod.getLabel();
                font.drawStringWithShadow(name, categoryWidth + modWidth / 2 - font.getStringWidth(name) / 2 + 3, y + 3, modColor(mod, modTab == i));
                y += 14;
            }
        }
    }

    private void renderNew(List<ModuleCategory> categories) {
        FontRenderer font = font();
        int categoryWidth = categoryWidth(categories);
        double y = 14;
        for (int i = 0; i < categories.size(); i++) {
            bordered(2, y, 2 + categoryWidth, y + 18, 0.6, categoryTab == i ? NEW_SELECTED : NEW_BOX, NEW_BORDER);
            y += 19;
        }
        double textY = 15;
        for (int i = 0; i < categories.size(); i++) {
            String name = capitalize(categories.get(i).name());
            font.drawStringWithShadow(name, categoryWidth / 2 - font.getStringWidth(name) / 2 + 2, (float) (textY + 5), categoryTab == i ? TEXT : TEXT_OFF);
            textY += 19;
        }
        if (inMods) {
            List<Module> mods = modules(categories.get(categoryTab));
            modTab = Math.max(0, Math.min(mods.size() - 1, modTab));
            int shown = scrollTo(mods.size());
            y = 14;
            for (int i = modScroll; i < modScroll + shown; i++) {
                bordered(categoryWidth + 4, y, 2 + categoryWidth + modWidth + 2, y + 18, 0.6, modTab == i ? NEW_SELECTED : NEW_BOX, NEW_BORDER);
                y += 19;
            }
            textY = 14;
            for (int i = modScroll; i < modScroll + shown; i++) {
                Module mod = mods.get(i);
                String name = mod.getLabel();
                font.drawStringWithShadow(name, categoryWidth + modWidth / 2 - font.getStringWidth(name) / 2 + 4, (float) (textY + 6), modColor(mod, modTab == i));
                textY += 19;
            }
        }
    }

    private static int modColor(Module mod, boolean selected) {
        if (selected) return mod.isEnabled() ? TEXT : SELECTED_OFF;
        return mod.isEnabled() ? UNSELECTED_ON : TEXT_OFF;
    }

    void keyPress(int key) {
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;
        categoryTab = Math.max(0, Math.min(categories.size() - 1, categoryTab));
        int last = categories.size() - 1;
        if (!inMods) {
            if (key == Keyboard.KEY_RIGHT) {
                List<Module> mods = modules(categories.get(categoryTab));
                if (mods.isEmpty()) return;
                int widest = 0;

                for (Module mod : mods) widest = Math.max(widest, font().getStringWidth(capitalize(mod.getLabel())));
                modWidth = widest + 6;
                modTarget = modPosition = categoryTarget;
                modTab = 0;
                modScroll = 0;
                inMods = true;
            } else if (key == Keyboard.KEY_DOWN) {
                categoryTab++;
                categoryTarget += step;
                if (categoryTab > last) {
                    transitionQuickly = true;
                    categoryTarget = 15;
                    categoryTab = 0;
                }
            } else if (key == Keyboard.KEY_UP) {
                categoryTab--;
                categoryTarget -= step;
                if (categoryTab < 0) {
                    transitionQuickly = true;
                    categoryTarget = 15 + last * step;
                    categoryTab = last;
                }
            }
        } else {
            List<Module> mods = modules(categories.get(categoryTab));
            if (mods.isEmpty()) {
                inMods = false;
                return;
            }
            int lastMod = mods.size() - 1;
            if (key == Keyboard.KEY_LEFT) {
                inMods = false;
            } else if (key == Keyboard.KEY_RIGHT) {
                mods.get(Math.max(0, Math.min(lastMod, modTab))).toggle();
            } else if (key == Keyboard.KEY_DOWN) {
                modTab++;
                modTarget += step;
                if (modTab > lastMod) {
                    transitionQuickly = true;
                    modTarget = categoryTarget;
                    modTab = 0;
                }
            } else if (key == Keyboard.KEY_UP) {
                modTab--;
                modTarget -= step;
                if (modTab < 0) {
                    transitionQuickly = true;
                    modTarget = categoryTarget + lastMod * step;
                    modTab = lastMod;
                }
            }
        }
    }

    private void updateBars() {
        long now = System.currentTimeMillis();
        long elapsed = lastUpdate == 0 ? 0 : now - lastUpdate;
        lastUpdate = now;
        int increment = Math.max(1, Math.round((transitionQuickly ? 100 : 20) * elapsed / 100f));
        if (categoryPosition < categoryTarget) {
            categoryPosition += increment;
            if (categoryPosition >= categoryTarget) {
                categoryPosition = categoryTarget;
                transitionQuickly = false;
            }
        } else if (categoryPosition > categoryTarget) {
            categoryPosition -= increment;
            if (categoryPosition <= categoryTarget) {
                categoryPosition = categoryTarget;
                transitionQuickly = false;
            }
        }
        if (modPosition < modTarget) {
            modPosition += increment;
            if (modPosition >= modTarget) {
                modPosition = modTarget;
                transitionQuickly = false;
            }
        } else if (modPosition > modTarget) {
            modPosition -= increment;
            if (modPosition <= modTarget) {
                modPosition = modTarget;
                transitionQuickly = false;
            }
        }
    }

    private static void rect(double left, double top, double right, double bottom, int color) {
        if (left > right) {
            double swap = left;
            left = right;
            right = swap;
        }
        if (top > bottom) {
            double swap = top;
            top = bottom;
            bottom = swap;
        }
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(left, bottom, 0).color(r, g, b, a).endVertex();
        renderer.pos(right, bottom, 0).color(r, g, b, a).endVertex();
        renderer.pos(right, top, 0).color(r, g, b, a).endVertex();
        renderer.pos(left, top, 0).color(r, g, b, a).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void bordered(double x, double y, double x1, double y1, double width, int inside, int border) {
        rect(x + width, y + width, x1 - width, y1 - width, inside);
        rect(x + width, y, x1 - width, y + width, border);
        rect(x, y, x + width, y1, border);
        rect(x1 - width, y, x1, y1, border);
        rect(x + width, y1 - width, x1 - width, y1, border);
    }
}
