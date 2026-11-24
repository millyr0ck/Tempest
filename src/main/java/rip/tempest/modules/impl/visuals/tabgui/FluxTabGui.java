package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class FluxTabGui implements HudTabGui {
    private static final int ELEMENT_HEIGHT = 24, PADDING_XY = 2, MODULE_PANEL_WIDTH = 200, SETTINGS_PANEL_WIDTH = 240;
    private static final int PANEL = 0xdc121214, GREY = 0xff808080, LOCKED = 0xff80ff80;
    private static final ResourceLocation GEARS = new ResourceLocation("tempest/images/flux_gears.png");

    private final boolean newStyle;

    public FluxTabGui(boolean newStyle) {
        this.newStyle = newStyle;
    }

    private final int[] cursorItems = new int[3];
    private int cursorDepth;
    private boolean lockedInSlider;

    private boolean fluxStyle() {
        return !newStyle;
    }

    private static List<ModuleCategory> categories() {
        List<ModuleCategory> list = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            if (!Tempest.INSTANCE.getModuleManager().getModulesForCategory(category).isEmpty()) list.add(category);
        }
        return list;
    }

    private static List<Module> modules(ModuleCategory category) {
        return Tempest.INSTANCE.getModuleManager().getModulesForCategory(category);
    }

    private static List<Property<?>> settings(Module module) {
        List<Property<?>> list = new ArrayList<>();
        for (Property<?> property : module.getElements()) if (property.isAvailable()) list.add(property);
        return list;
    }

    @Override
    public void render() {
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;
        float factor = new ScaledResolution(mc).getScaleFactor();
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;
        wrapCursor(categories);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1 / factor, 1 / factor, 1);
        render(categories, categoryPanelWidth());
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    private int categoryPanelWidth() {
        return fluxStyle() ? 120 : 140;
    }

    @Override
    public float width() {
        return (PADDING_XY + categoryPanelWidth()) / (float) new ScaledResolution(mc).getScaleFactor();
    }

    @Override
    public float height() {
        return categories().size() * ELEMENT_HEIGHT / (float) new ScaledResolution(mc).getScaleFactor();
    }

    private void render(List<ModuleCategory> categories, int categoryPanelWidth) {
        boolean flux = fluxStyle();
        int accent = ColorProcess.getColor().getRGB();
        rect(PADDING_XY, 0, PADDING_XY + categoryPanelWidth, categories.size() * ELEMENT_HEIGHT, PANEL);

        int selCat = cursorItems[0];
        float textIndent = flux ? 4 : 0, xOffset = flux ? 2 : 0, x2Offset = 6, yOff = flux ? 2 : 0;

        for (int i = 0; i < categories.size(); i++) {
            boolean selected = i == selCat;
            if (selected) {
                if (flux) {
                    rect(PADDING_XY + 1 + xOffset, i * ELEMENT_HEIGHT + 1 + yOff, PADDING_XY + x2Offset, (i + 1) * ELEMENT_HEIGHT - 1 - yOff, accent);
                } else {
                    gradient(PADDING_XY + 1, i * ELEMENT_HEIGHT + 1, PADDING_XY + categoryPanelWidth - 1, (i + 1) * ELEMENT_HEIGHT - 1, accent, darker(accent, 0.7F));
                }
            }
            text(categories.get(i).getName(), PADDING_XY * (selected ? 4 : 2) + textIndent, i * ELEMENT_HEIGHT + PADDING_XY / 2F, selected ? -1 : GREY);
        }

        if (cursorDepth < 1) return;
        List<Module> modules = modules(categories.get(selCat));
        float modulesX = PADDING_XY + categoryPanelWidth + PADDING_XY;
        rect(modulesX, selCat * ELEMENT_HEIGHT, modulesX + MODULE_PANEL_WIDTH, (selCat + modules.size()) * ELEMENT_HEIGHT, PANEL);
        int selMod = cursorItems[1];
        for (int j = 0; j < modules.size(); j++) {
            Module module = modules.get(j);
            boolean selected = j == selMod;
            float top = (selCat + j) * ELEMENT_HEIGHT;
            if (selected) {
                rect(modulesX + 1 + xOffset, top + 1 + yOff, modulesX + (flux ? 6 : MODULE_PANEL_WIDTH - 1), top + ELEMENT_HEIGHT - 1 - yOff, accent);
            }
            text(module.getLabel(), PADDING_XY + categoryPanelWidth + PADDING_XY * (selected ? 4 : 2) + textIndent, top + PADDING_XY / 2F,
                    module.isEnabled() ? -1 : GREY);
            if (!settings(module).isEmpty()) gears(PADDING_XY + categoryPanelWidth + MODULE_PANEL_WIDTH - 24, top + PADDING_XY / 2F + 10, GREY);
        }

        if (cursorDepth < 2) return;
        List<Property<?>> settings = settings(modules.get(selMod));
        float settingsX = modulesX + MODULE_PANEL_WIDTH + PADDING_XY;
        rect(settingsX, (selCat + selMod) * ELEMENT_HEIGHT, settingsX + SETTINGS_PANEL_WIDTH, (selMod + selCat + settings.size()) * ELEMENT_HEIGHT, PANEL);
        int selSett = cursorItems[2];
        for (int k = 0; k < settings.size(); k++) {
            Property<?> setting = settings.get(k);
            boolean selected = k == selSett;
            float top = (selCat + selMod + k) * ELEMENT_HEIGHT;
            if (selected) {
                rect(settingsX + 1 + xOffset, top + 1 + yOff, settingsX + (flux ? x2Offset : SETTINGS_PANEL_WIDTH - 1), top + ELEMENT_HEIGHT - 1 - yOff, accent);
            }
            text(setting.getLabel() + ": " + display(setting), PADDING_XY + categoryPanelWidth + PADDING_XY + MODULE_PANEL_WIDTH + PADDING_XY * (selected ? 4 : 2) + textIndent,
                    top + PADDING_XY / 2F, selected && lockedInSlider ? LOCKED : selected ? -1 : GREY);
        }
    }

    private static String display(Property<?> setting) {
        Object value = setting.getValue();
        if (value instanceof Boolean b) return b ? "on" : "off";
        if (setting instanceof NumberProperty number) {
            double v = number.getValue();
            return number.getIncrement() >= 1 ? Long.toString(Math.round(v)) : String.valueOf(Math.round(v * 100.0) / 100.0);
        }
        return String.valueOf(value);
    }

    @Override
    public void keyPress(int key) {
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) return;
        List<ModuleCategory> categories = categories();
        if (categories.isEmpty()) return;

        if (key == Keyboard.KEY_RIGHT) {
            cursorDepth++;
            clampDepth(categories);
            wrapCursor(categories);
        } else if (key == Keyboard.KEY_LEFT) {
            lockedInSlider = false;
            cursorDepth--;
            clampDepth(categories);
        } else if (key == Keyboard.KEY_DOWN || key == Keyboard.KEY_UP) {
            int direction = key == Keyboard.KEY_UP ? 1 : -1;
            if (lockedInSlider) {
                nudge(categories, direction);
                return;
            }
            cursorItems[cursorDepth] -= direction;
            wrapCursor(categories);
        } else if (key == Keyboard.KEY_RETURN) {
            enter(categories);
        }
    }

    @SuppressWarnings("unchecked")
    private void enter(List<ModuleCategory> categories) {
        if (cursorDepth == 1) {
            modules(categories.get(cursorItems[0])).get(cursorItems[1]).toggle();
        } else if (cursorDepth == 2) {
            Property<?> setting = settings(modules(categories.get(cursorItems[0])).get(cursorItems[1])).get(cursorItems[2]);
            if (setting instanceof NumberProperty) {
                lockedInSlider = !lockedInSlider;
            } else if (setting instanceof ModeProperty<?> mode) {
                Object[] values = mode.getValues();
                int index = 0;
                for (int i = 0; i < values.length; i++) if (values[i] == mode.getValue()) index = i;
                mode.setValue((index + 1) % values.length);
            } else if (setting.getValue() instanceof Boolean b) {
                ((Property<Boolean>) setting).setValue(!b);
            }
        }
    }

    private void nudge(List<ModuleCategory> categories, int direction) {
        Property<?> setting = settings(modules(categories.get(cursorItems[0])).get(cursorItems[1])).get(cursorItems[2]);
        if (setting instanceof NumberProperty number) {
            double next = number.getValue() + number.getIncrement() * direction;
            number.setValue(Math.max(number.getMin(), Math.min(number.getMax(), next)));
        }
    }

    private void wrapCursor(List<ModuleCategory> categories) {
        if (cursorItems[0] >= categories.size()) cursorItems[0] = 0;
        else if (cursorItems[0] < 0) cursorItems[0] = categories.size() - 1;
        if (cursorDepth >= 1) {
            int count = modules(categories.get(cursorItems[0])).size();
            if (cursorItems[1] >= count) cursorItems[1] = 0;
            else if (cursorItems[1] < 0) cursorItems[1] = count - 1;
        }
        if (cursorDepth >= 2) {
            int count = settings(modules(categories.get(cursorItems[0])).get(cursorItems[1])).size();
            if (count == 0) {
                cursorDepth = 1;
                lockedInSlider = false;
            } else if (cursorItems[2] >= count) {
                cursorItems[2] = 0;
            } else if (cursorItems[2] < 0) {
                cursorItems[2] = count - 1;
            }
        }
    }

    private void clampDepth(List<ModuleCategory> categories) {
        if (cursorDepth < 0) {
            cursorDepth = 0;
            return;
        }
        if (cursorDepth > 2) cursorDepth = 2;
        if (cursorDepth == 2 && settings(modules(categories.get(cursorItems[0])).get(cursorItems[1])).isEmpty()) cursorDepth = 1;
    }

    private void text(String text, float x, float lineTop, int color) {
        CustomFontRenderer font = FontProcess.getStyledFont(fluxStyle() ? "arialbold" : "arial", 38, Font.PLAIN);
        GlStateManager.enableBlend();

        int shadow = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
        font.drawString(text, x + 1, lineTop + 1 + 2, shadow);
        font.drawString(text, x, lineTop + 2, color);
    }

    private static void rect(float x1, float y1, float x2, float y2, int color) {
        Glyphs.bar(x1, y1, x2 - x1, y2 - y1, new Color(color, true));
    }

    private static void gradient(float x1, float y1, float x2, float y2, int left, int right) {
        Glyphs.gradient(x1, y1, x2 - x1, y2 - y1, new Color(left, true), new Color(right, true));
    }

    private static int darker(int color, float factor) {
        float f = 1 - factor;
        int r = (int) ((color >> 16 & 0xFF) * f), g = (int) ((color >> 8 & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void gears(float x, float centreY, int color) {
        float s = 18F / 128F, top = centreY - 9;
        float x1 = x - 4 * s, y1 = top - 2 * s, x2 = x1 + 168 * s, y2 = y1 + 134 * s;
        mc.getTextureManager().bindTexture(GEARS);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x1, y2, 0).tex(0, 1).endVertex();
        wr.pos(x2, y2, 0).tex(1, 1).endVertex();
        wr.pos(x2, y1, 0).tex(1, 0).endVertex();
        wr.pos(x1, y1, 0).tex(0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }
}
