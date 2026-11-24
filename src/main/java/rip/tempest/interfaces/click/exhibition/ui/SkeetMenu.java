package rip.tempest.interfaces.click.exhibition.ui;

import rip.tempest.Tempest;
import rip.tempest.api.config.ConfigManager;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.MultiModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.interfaces.click.ArrayListColorPickerUI;
import rip.tempest.interfaces.click.exhibition.ExhibitionClickInterface;
import rip.tempest.interfaces.click.exhibition.components.Button;
import rip.tempest.interfaces.click.exhibition.components.Checkbox;
import rip.tempest.interfaces.click.exhibition.components.*;
import rip.tempest.interfaces.click.exhibition.util.*;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.utils.client.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SkeetMenu extends UI {
    public static Opacity opacity = new Opacity(0);
    private final Minecraft mc = Minecraft.getMinecraft();

    private final ResourceLocation tex = new ResourceLocation("tempest/exhibition/tex.png");
    private final ResourceLocation texture = new ResourceLocation("tempest/exhibition/skeetchainmail.png");

    private final Translate bar = new Translate(0, 0);

    private static final ColorObject ACCENT = new ColorObject(ClientSettingsModule.exhibitionAccent);

    private static final float CONTENT_LEFT = 43, CONTENT_RIGHT = 337, CONTENT_TOP = 15, CONTENT_BOTTOM = 337;
    private static final float SCROLL_STEP = 16;

    private static final int MAX_LIST_ROWS = 10;

    private static final float SEARCH_X = 247, SEARCH_Y = 6, SEARCH_W = 85, SEARCH_H = 7.5F;

    private static final int MIN_TEXT_ALPHA = 4;

    private static final int NOWHERE = -10000;

    private String hoverText;
    private int hoverColor;
    private long lastConfigRefresh;

    @Override
    public void mainPanelDraw(MainPanel panel, int p0, int p1) {
        hoverText = null;
        opacity.interp(panel.isOpen ? 255 : 0, 25);

        RenderingUtil.rectangleBordered(panel.x + panel.dragX - 0.3, panel.y + panel.dragY - 0.3, panel.x + 340 + panel.dragX + 0.5, panel.y + 340 + panel.dragY + 0.3, 0.5, Colors.getColor(0, 0), Colors.getColor(10, op()));
        RenderingUtil.rectangleBordered(panel.x + panel.dragX, panel.y + panel.dragY, panel.x + 340 + panel.dragX, panel.y + 340 + panel.dragY, 0.5, Colors.getColor(0, 0), Colors.getColor(60, op()));
        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2, panel.y + panel.dragY + 2, panel.x + 340 + panel.dragX - 2, panel.y + 340 + panel.dragY - 2, 0.5, Colors.getColor(0, 0), Colors.getColor(60, op()));
        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 0.6, panel.y + panel.dragY + 0.6, panel.x + 340 + panel.dragX - 0.5, panel.y + 340 + panel.dragY - 0.6, 1.3, Colors.getColor(0, 0), Colors.getColor(40, op()));

        Depth.pre();
        Depth.mask();

        float y = 15;
        for (int i = 0; i <= panel.typeButton.size(); i++) {
            if (i <= panel.typeButton.size() - 1 && panel.typeButton.get(i).categoryPanel.visible && i > 0) {
                y = 15 + ((i) * 40);
            }
        }
        bar.interpolate(0, y, 0.6F);
        y = bar.getY();

        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + 4F, panel.x + panel.dragX + 40, panel.y + panel.dragY + y, -1);
        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + y + 40, panel.x + panel.dragX + 40, panel.y + panel.dragY + 307.5 + 30, -1);

        Depth.render(GL11.GL_LESS);

        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2.5, panel.y + panel.dragY + 2.5, panel.x + 340 + panel.dragX - 2.5, panel.y + 340 + panel.dragY - 2.5, 0.5, Colors.getColor(22, op()), Colors.getColor(22, op()));

        Depth.post();

        RenderingUtil.drawGradientSideways(panel.x + panel.dragX + 3, panel.y + panel.dragY + 3, panel.x + 178 + panel.dragX - 3, panel.dragY + panel.y + 4, Colors.getColor(55, 177, 218, op()), Colors.getColor(204, 77, 198, op()));
        RenderingUtil.drawGradientSideways(panel.x + panel.dragX + 175, panel.y + panel.dragY + 3, panel.x + 340 + panel.dragX - 3, panel.dragY + panel.y + 4, Colors.getColor(204, 77, 198, op()), Colors.getColor(204, 227, 53, op()));

        int i11 = op() - 145;
        if (i11 < 0) {
            i11 = 0;
        }
        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + 3.3, panel.x + 340 + panel.dragX - 3, panel.dragY + panel.y + 4, Colors.getColor(0, i11));

        GlStateManager.pushMatrix();
        Depth.pre();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        Depth.mask();

        GlStateManager.color(1, 1, 1, opacity.getScale());
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.translate(panel.x + panel.dragX + 40, panel.dragY + panel.y + 3f, 0);
        drawIcon(0, 0, 0, .5F, 340 - 3 - 40, 310 - 6 + 30, 812 / 2F, 688 / 2F);
        drawIcon(-40 + 2.5, y - 3, .5F, .5F + y, 40 - 2.5F, 40, 812 / 2F, 688 / 2F);

        Depth.render(GL11.GL_EQUAL);

        mc.getTextureManager().bindTexture(tex);
        drawIcon(-40, 1, 0, 0, 340, 310 - 7 + 30, 812 / 2F, 688 / 2F);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        Depth.post();
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + 4F, panel.x + panel.dragX + 40, panel.y + panel.dragY + y + 1, -1);
        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + y + 40, panel.x + panel.dragX + 40, panel.y + panel.dragY + 307.5 + 30, -1);

        Depth.render();
        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2, panel.y + panel.dragY + 3, panel.x + panel.dragX + 39.5, panel.y + panel.dragY + y - 0.5, 0.5, Colors.getColor(0, 0), Colors.getColor(0, op()));
        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2, panel.y + panel.dragY + 3, panel.x + panel.dragX + 40, panel.y + panel.dragY + y, 0.5, Colors.getColor(0, 0), Colors.getColor(48, op()));

        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + 4, panel.x + panel.dragX + 39, panel.y + panel.dragY + y - 1, Colors.getColor(12, op()));

        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2, panel.y + panel.dragY + y + 40.5, panel.x + panel.dragX + 39.5, panel.y + panel.dragY + 308 + 30, 0.5, Colors.getColor(0, 0), Colors.getColor(0, op()));
        RenderingUtil.rectangleBordered(panel.x + panel.dragX + 2, panel.y + panel.dragY + y + 40, panel.x + panel.dragX + 40, panel.y + panel.dragY + 308 + 30, 0.5, Colors.getColor(0, 0), Colors.getColor(48, op()));

        RenderingUtil.rectangle(panel.x + panel.dragX + 3, panel.y + panel.dragY + y + 41, panel.x + panel.dragX + 39, panel.y + panel.dragY + 307.5 + 30, Colors.getColor(12, op()));
        Depth.post();
        GlStateManager.popMatrix();

        if (opacity.getOpacity() >= MIN_TEXT_ALPHA) {
            for (CategoryButton button : panel.typeButton) {
                button.draw(p0, p1);
            }
            drawSearchBar(panel, p0, p1);
            ScaledResolution rs = new ScaledResolution(mc);
            double twoDscale = ExhibitionClickInterface.twoDScale();
            if (panel.dragging) {
                panel.dragX = p0 - panel.lastDragX;
                panel.dragY = p1 - panel.lastDragY;
            }
            double xBorder = (rs.getScaledWidth() / twoDscale - 392);
            if (panel.dragX > xBorder) {
                panel.dragX = (float) xBorder;
            }
            if (panel.dragX < 2 - 50) {
                panel.dragX = 2 - 50;
            }
            double yBorder = (rs.getScaledHeight() / twoDscale - 392);

            if (panel.dragY > yBorder) {
                panel.dragY = (float) yBorder;
            }
            if (panel.dragY < 2 - 50) {
                panel.dragY = 2 - 50;
            }
        }

        if (hoverText != null && !hoverText.isEmpty()) {
            String text = ExhibitionFonts.fss.trimStringToWidth(hoverText, SEARCH_X - 4 - 57, false);
            ExhibitionFonts.fss.drawStringWithShadow(text, (panel.x + 2 + panel.dragX) + 55, (panel.y + 9 + panel.dragY), hoverColor);
        }
    }

    private void drawSearchBar(MainPanel panel, float mouseX, float mouseY) {
        float x = panel.x + panel.dragX + SEARCH_X;
        float y = panel.y + panel.dragY + SEARCH_Y;
        String query = panel.searchQuery;
        boolean hoveringBox = hovering(mouseX, mouseY, x, y, SEARCH_W, SEARCH_H);

        RenderingUtil.rectangle(x - 0.3, y - 0.3, x + SEARCH_W + 0.3, y + SEARCH_H + 0.3, Colors.getColor(10, op()));
        RenderingUtil.drawGradient(x, y, x + SEARCH_W, y + SEARCH_H, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (hoveringBox || panel.searchFocused) {
            RenderingUtil.rectangleBordered(x, y, x + SEARCH_W, y + SEARCH_H, 0.3, Colors.getColor(0, 0), panel.searchFocused ? Colors.getColor(130, op()) : Colors.getColor(90, op()));
        }

        boolean clearable = !query.isEmpty();
        float textX = x + 2.5F;
        float textWidth = SEARCH_W - 2.5F - (clearable ? 9 : 2);
        if (query.isEmpty() && !panel.searchFocused) {
            ExhibitionFonts.fss.drawString("Search modules...", textX, y + 2, Colors.getColor(90, op()));
        } else {
            String shown = query;
            while (!shown.isEmpty() && ExhibitionFonts.fss.getWidth(shown) > textWidth) {
                shown = shown.substring(1);
            }
            ExhibitionFonts.fss.drawString(shown, textX, y + 2, Colors.getColor(200, op()));
            if (panel.searchFocused && System.currentTimeMillis() % 1000 < 500) {
                float caretX = textX + ExhibitionFonts.fss.getWidth(shown) + 0.5F;
                RenderingUtil.rectangle(caretX, y + 1.5, caretX + 0.5, y + 6, Colors.getColor(220, op()));
            }
        }

        if (clearable) {
            boolean hoveringClear = hovering(mouseX, mouseY, x + SEARCH_W - 8, y, 8, SEARCH_H);
            ExhibitionFonts.fss.drawString("x", x + SEARCH_W - 6, y + 1.5F, Colors.getColor(hoveringClear ? 220 : 120, op()));
        }
    }

    private boolean overSearch(MainPanel panel, float mouseX, float mouseY) {
        return hovering(mouseX, mouseY, panel.x + panel.dragX + SEARCH_X, panel.y + panel.dragY + SEARCH_Y, SEARCH_W, SEARCH_H);
    }

    private void setSearch(MainPanel panel, String query) {
        panel.searchQuery = query;
        for (CategoryButton button : panel.typeButton) {
            button.categoryPanel.scroll = 0;
        }
        if (query.isEmpty()) return;

        for (CategoryButton button : panel.typeButton) {
            if (button.enabled && button.category != null) return;
        }
        CategoryButton target = firstModuleTab(panel);
        if (target == null) return;
        for (CategoryButton button : panel.typeButton) {
            button.enabled = button == target;
            button.categoryPanel.visible = button == target;
        }
    }

    private CategoryButton firstModuleTab(MainPanel panel) {
        for (CategoryButton button : panel.typeButton) {
            if (button.category != null) return button;
        }
        return null;
    }

    private void drawIcon(double x, double y, float u, float v, double width, double height, float textureWidth, float textureHeight) {
        float f = 1.0F / textureWidth;
        float f1 = 1.0F / textureHeight;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y + height, 0.0D).tex(u * f, (v + (float) height) * f1).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0D).tex((u + (float) width) * f, (v + (float) height) * f1).endVertex();
        worldrenderer.pos(x + width, y, 0.0D).tex((u + (float) width) * f, v * f1).endVertex();
        worldrenderer.pos(x, y, 0.0D).tex(u * f, v * f1).endVertex();
        tessellator.draw();
    }

    @Override
    public void mainPanelKeyPress(MainPanel panel, char typedChar, int key) {
        if (panel.searchFocused) {
            searchKeyPressed(panel, typedChar, key);
            return;
        }

        boolean bad = false;
        for (CategoryButton button : panel.typeButton) {
            for (TextBox textBox : button.categoryPanel.textBoxes) {
                if (textBox.isTyping || textBox.isFocused)
                    bad = true;
            }
            if (button.categoryPanel.configTextBox != null) {
                ConfigTextBox textBox = button.categoryPanel.configTextBox;
                if (button.categoryPanel.visible) {
                    if (textBox.isTyping || textBox.isFocused)
                        bad = true;
                }
            }
        }
        if (opacity.getOpacity() < 10)
            return;
        if (key == Keyboard.KEY_ESCAPE) {
            for (CategoryButton buttonb : panel.typeButton) {
                for (Button button : buttonb.categoryPanel.buttons) {
                    if (button.isBinding) {
                        bad = true;
                    }
                }
            }
        }

        if (!bad && (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_INSERT || key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RSHIFT)) {
            panel.typeButton.forEach(o -> o.categoryPanel.multiDropdownBoxes.forEach(b -> b.active = false));
            panel.typeButton.forEach(o -> o.categoryPanel.dropdownBoxes.forEach(b -> b.active = false));
            panel.typeButton.forEach(o -> o.categoryPanel.buttons.forEach(b -> b.isBinding = false));
            panel.typeButton.forEach(o -> o.categoryPanel.textBoxes.forEach(b -> b.isTyping = false));
            panel.typeButton.forEach(o -> o.categoryPanel.textBoxes.forEach(b -> b.isFocused = false));

            if (mc.currentScreen instanceof ExhibitionClickInterface)
                mc.displayGuiScreen(null);
            return;
        }
        panel.typeButton.forEach(o -> o.categoryPanel.buttons.forEach(b -> b.keyPressed(key)));
        panel.typeButton.forEach(o -> o.categoryPanel.textBoxes.forEach(t -> t.keyPressed(typedChar, key)));
        for (CategoryButton button : panel.typeButton) {
            if (button.categoryPanel.configTextBox != null && button.categoryPanel.visible) {
                button.categoryPanel.configTextBox.keyPressed(typedChar, key);
            }
        }
    }

    private void searchKeyPressed(MainPanel panel, char typedChar, int key) {
        String query = panel.searchQuery;
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
            panel.searchFocused = false;
        } else if (key == Keyboard.KEY_BACK) {
            if (!query.isEmpty()) setSearch(panel, query.substring(0, query.length() - 1));
        } else if (GuiScreen.isKeyComboCtrlV(key)) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) setSearch(panel, query + ChatAllowedCharacters.filterAllowedCharacters(clipboard));
        } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            setSearch(panel, query + typedChar);
        }
    }

    @Override
    public void panelConstructor(MainPanel mainPanel, float x, float y) {
        int y1 = 15;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (modulesIn(category).isEmpty()) continue;
            mainPanel.typeButton.add(new CategoryButton(mainPanel, category.getName(), category, x + 3, y + y1));
            y += 40;
        }
        mainPanel.typeButton.add(new CategoryButton(mainPanel, "Colors", null, x + 3, y + y1));
        y += 40;
        mainPanel.typeButton.add(new CategoryButton(mainPanel, "Themes", null, x + 3, y + y1));
        y += 40;
        mainPanel.typeButton.add(new CategoryButton(mainPanel, "Settings", null, x + 3, y + y1));
        mainPanel.typeButton.get(0).enabled = true;
        mainPanel.typeButton.get(0).categoryPanel.visible = true;
    }

    @Override
    public void panelMouseClicked(MainPanel mainPanel, int x, int y, int z) {
        if (opacity.getOpacity() < 220)
            return;
        if (overSearch(mainPanel, x, y)) {
            if (z == 0) {
                boolean onClear = hovering(x, y, mainPanel.x + mainPanel.dragX + SEARCH_X + SEARCH_W - 8, mainPanel.y + mainPanel.dragY + SEARCH_Y, 8, SEARCH_H);
                if (onClear && !mainPanel.searchQuery.isEmpty()) {
                    setSearch(mainPanel, "");
                }
                mainPanel.searchFocused = true;
                Keyboard.enableRepeatEvents(true);
            }
            return;
        }

        mainPanel.searchFocused = false;

        if (x >= mainPanel.x + mainPanel.dragX && y >= mainPanel.dragY + mainPanel.y && x <= mainPanel.dragX + mainPanel.x + 400 && y <= mainPanel.dragY + mainPanel.y + 12.0f && z == 0) {
            mainPanel.dragging = true;
            mainPanel.lastDragX = x - mainPanel.dragX;
            mainPanel.lastDragY = y - mainPanel.dragY;
        }
        mainPanel.typeButton.forEach(c -> {
            c.mouseClicked(x, y, z);
            c.categoryPanel.mouseClicked(x, y, z);
        });
    }

    @Override
    public void panelMouseMovedOrUp(MainPanel mainPanel, int x, int y, int z) {
        if (opacity.getOpacity() < 220)
            return;
        if (z == 0) {
            mainPanel.dragging = false;
        }
        for (CategoryButton button : mainPanel.typeButton) {
            button.mouseReleased(x, y, z);
        }
    }

    @Override
    public void handleMouseInput(MainPanel panel, int mouseX, int mouseY, int wheel) {
        if (wheel == 0) return;
        int direction = wheel > 0 ? -1 : 1;

        for (CategoryButton button : panel.typeButton) {
            CategoryPanel categoryPanel = button.categoryPanel;
            if (!categoryPanel.visible) continue;

            ConfigList configList = categoryPanel.configList;
            if (configList != null) {
                if (configList.configs.length <= 4 || !configList.hovering)
                    return;
                configList.amountScrolled += direction * (-8.0F) / 2;
                if (configList.amountScrolled > 0) {
                    configList.amountScrolled = 0;
                }
                if (configList.amountScrolled < ((configList.configs.length - 10) * -8.0F)) {
                    configList.amountScrolled = ((configList.configs.length - 10) * -8.0F);
                }
                return;
            }

            if (button.category != null || themesTab(button)) {
                for (DropdownBox db : categoryPanel.dropdownBoxes) {
                    if (db.active && overList(panel, db.x, db.y, db.width, db.visibleButtons().size(), mouseX, mouseY)) {
                        db.scrollRow = scrollRows(db.scrollRow + direction, db.buttons.size(), db.visibleRows);
                        return;
                    }
                }
                for (MultiDropdownBox db : categoryPanel.multiDropdownBoxes) {
                    if (db.active && overList(panel, db.x, db.y, db.width, db.visibleButtons().size(), mouseX, mouseY)) {
                        db.scrollRow = scrollRows(db.scrollRow + direction, db.buttons.size(), db.visibleRows);
                        return;
                    }
                }
            }

            if ((button.category != null || themesTab(button)) && inContent(panel, mouseX, mouseY)) {
                categoryPanel.scroll = clamp(categoryPanel.scroll + direction * SCROLL_STEP, 0, categoryPanel.maxScroll);
            }
        }
    }

    @Override
    public void categoryButtonConstructor(CategoryButton p0, MainPanel p1) {
        p0.categoryPanel = new CategoryPanel(p0.name, p0, 0, 0);
    }

    @Override
    public void categoryButtonMouseClicked(CategoryButton p0, MainPanel p1, int p2, int p3, int p4) {
        if (p2 >= p0.x + p1.dragX && p3 >= p1.dragY + p0.y && p2 <= p1.dragX + p0.x + 40 && p3 <= p1.dragY + p0.y + 40 && p4 == 0) {
            if (!p1.searchQuery.isEmpty()) {
                setSearch(p1, "");
            }
            for (CategoryButton button : p1.typeButton) {
                if (button == p0) {
                    p0.enabled = true;
                    p0.categoryPanel.visible = true;
                } else {
                    button.enabled = false;
                    button.categoryPanel.visible = false;
                }
            }
        }
    }

    @Override
    public void categoryButtonMouseReleased(CategoryButton categoryButton, int x, int y, int button) {
        categoryButton.categoryPanel.mouseReleased(x, y, button);
    }

    @Override
    public void categoryButtonDraw(CategoryButton p0, float p2, float p3) {
        int brightness = p0.enabled ? 210 : 91;
        boolean hovering = p2 >= p0.x + p0.panel.dragX && p3 >= p0.panel.dragY + p0.y && p2 <= p0.panel.dragX + p0.x + 40 && p3 < p0.panel.dragY + p0.y + 40;
        if (hovering && !p0.enabled) {
            brightness = 165;
        }

        if (hovering) {
            hover(p0.name, Colors.getColor(220, op()));
        }

        p0.fade.interp(brightness, 10);
        int color = Colors.getColor((int) p0.fade.getOpacity(), op());

        switch (p0.name) {
            case "Client":
                ExhibitionFonts.badCache.drawCenteredString("I", (p0.x + 19 + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            case "Combat":
                ExhibitionFonts.badCache.drawCenteredString("E", (p0.x + 19 + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            case "Player":
                ExhibitionFonts.badCache.drawCenteredString("F", (p0.x + 18 + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            case "Movement":
                ExhibitionFonts.badCache.drawCenteredString("J", (p0.x + 19 + p0.panel.dragX), (p0.y + 22 + p0.panel.dragY), color);
                break;
            case "Visuals":
                ExhibitionFonts.badCache.drawCenteredString("C", (p0.x + 18 + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            case "Colors":
                ExhibitionFonts.badCache.drawCenteredString("H", (p0.x + 18.5F + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            case "Themes": {
                float sx = p0.x + 11 + p0.panel.dragX, sy = p0.y + 17 + p0.panel.dragY;
                int alpha = (int) (p0.fade.getOpacity() * op() / 255F);
                rip.tempest.processes.Themes.Preset current = rip.tempest.processes.Themes.current();
                for (int i = 0; i < 8; i++) {
                    int c = rip.tempest.processes.Themes.sample(current, i / 8.0 * 0.5);
                    RenderingUtil.rectangle(sx + i * 2, sy, sx + i * 2 + 2.3, sy + 6, Colors.getColor(c >> 16 & 255, c >> 8 & 255, c & 255, alpha));
                }
                break;
            }
            case "Settings":
                ExhibitionFonts.f.drawCenteredString("[+]", (p0.x + 18.5F + p0.panel.dragX), (p0.y + 20 + p0.panel.dragY), color);
                break;
            default:
                ExhibitionFonts.f.drawStringWithShadow(p0.name.substring(0, 1), (p0.x + 12 + p0.panel.dragX), (p0.y + 13 + p0.panel.dragY), color);
                break;
        }

        if (p0.enabled) {
            p0.categoryPanel.draw(p2, p3);
        }
    }

    @Override
    public void categoryPanelConstructor(CategoryPanel categoryPanel, CategoryButton categoryButton, float x, float y) {
        float xOff = 50 + categoryButton.panel.x;
        float yOff = 15 + categoryButton.panel.y;

        if (categoryButton.name.equalsIgnoreCase("Settings")) {
            float xOffsetXD = xOff, yOffsetXD = yOff + y;
            categoryPanel.groupBoxes.add(new GroupBox("Configs", categoryPanel, xOffsetXD, yOffsetXD, 180, 85 - 1.5F, true));
            categoryPanel.configTextBox = new ConfigTextBox(xOffsetXD + 89, yOffsetXD + 67.5F, categoryPanel);
            categoryPanel.configList = new ConfigList(xOffsetXD + 1, yOffsetXD + 10, categoryPanel);
        }
        if (categoryButton.name.equalsIgnoreCase("Colors")) {
            categoryPanel.colorPreviews.add(new ColorPreview(ACCENT, "Menu Accent", xOff + 77.5F, y, categoryButton));
            categoryPanel.colorPreviews.add(new ColorPreview(new ColorObject(ClientSettingsModule.pickerColor1), "Picker Color 1", xOff + 177.5F, y, categoryButton));
            categoryPanel.colorPreviews.add(new ColorPreview(new ColorObject(ClientSettingsModule.pickerColor2), "Picker Color 2", xOff + 277.5F, y, categoryButton));
        }
    }

    @Override
    public void categoryPanelMouseClicked(CategoryPanel categoryPanel, int p1, int p2, int p3) {
        if (!categoryPanel.visible) return;
        if ((categoryPanel.categoryButton.category != null || themesTab(categoryPanel.categoryButton)) && !inContent(categoryPanel.categoryButton.panel, p1, p2)) {
            p1 = p2 = NOWHERE;
        }
        final int mouseX = p1, mouseY = p2;

        boolean active = false;
        for (TextBox tb : categoryPanel.textBoxes) {
            if (tb.isFocused || tb.isTyping) {
                tb.mouseClicked(mouseX, mouseY, p3);
                active = true;
                break;
            }
        }
        if (categoryPanel.configTextBox != null) {
            ConfigTextBox tb = categoryPanel.configTextBox;
            if (tb.isFocused || tb.isTyping) {
                tb.mouseClicked(mouseX, mouseY, p3);
                active = true;
            }
        }
        for (DropdownBox db : categoryPanel.dropdownBoxes) {
            if (db.active) {
                db.mouseClicked(mouseX, mouseY, p3);
                active = true;
                break;
            }
        }
        for (MultiDropdownBox db : categoryPanel.multiDropdownBoxes) {
            if (db.active) {
                db.mouseClicked(mouseX, mouseY, p3);
                active = true;
                break;
            }
        }
        if (!active) {
            if (categoryPanel.configTextBox != null) {
                categoryPanel.configTextBox.mouseClicked(mouseX, mouseY, p3);
                categoryPanel.configList.mouseClicked(mouseX, mouseY, p3);
            }
            categoryPanel.textBoxes.forEach(o -> o.mouseClicked(mouseX, mouseY, p3));
            categoryPanel.dropdownBoxes.forEach(o -> o.mouseClicked(mouseX, mouseY, p3));
            for (MultiDropdownBox db : categoryPanel.multiDropdownBoxes)
                db.mouseClicked(mouseX, mouseY, p3);
            for (Button button : categoryPanel.buttons) {
                button.mouseClicked(mouseX, mouseY, p3);
            }
            for (Checkbox checkbox : categoryPanel.checkboxes) {
                checkbox.mouseClicked(mouseX, mouseY, p3);
            }
            for (Slider slider : categoryPanel.sliders) {
                slider.mouseClicked(mouseX, mouseY, p3);
            }
            for (ColorPreview cp : categoryPanel.colorPreviews) {
                for (HSVColorPicker slider : cp.sliders) {
                    slider.mouseClicked(mouseX, mouseY, p3);
                }
            }
            if (p3 == 0 && themesTab(categoryPanel.categoryButton)) {
                MainPanel panel = categoryPanel.categoryButton.panel;
                for (int i = 0; i < themeRects.size(); i++) {
                    float[] r = themeRects.get(i);
                    if (hovering(mouseX, mouseY, r[0] + panel.dragX, r[1] + panel.dragY, r[2], r[3])) {
                        int presetCount = rip.tempest.processes.Themes.Preset.values().length;
                        if (i < presetCount) {
                            rip.tempest.processes.Themes.select(rip.tempest.processes.Themes.Preset.values()[i]);
                        } else {
                            rip.tempest.modules.impl.visuals.ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
                            if (list != null) list.pattern.setValue(rip.tempest.utils.render.ArrayPattern.values()[i - presetCount]);
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void categoryPanelDraw(CategoryPanel categoryPanel, float x, float y) {
        MainPanel panel = categoryPanel.categoryButton.panel;
        boolean themes = themesTab(categoryPanel.categoryButton);
        boolean moduleTab = categoryPanel.categoryButton.category != null || themes;
        if (moduleTab) {
            if (themes) layoutThemes(categoryPanel);
            else layoutModules(categoryPanel);
            boolean dragging = categoryPanel.sliders.stream().anyMatch(slider -> slider.dragging);
            if (!dragging && !inContent(panel, x, y)) {
                x = y = NOWHERE;
            }
            beginClip(panel);
        }

        for (ColorPreview cp : categoryPanel.colorPreviews) {
            cp.draw(x, y);
        }
        for (GroupBox groupBox : categoryPanel.groupBoxes) {
            groupBox.draw(x, y);
        }
        for (TextBox tb : categoryPanel.textBoxes) {
            if (categoryPanel.visible) {
                tb.draw(x, y);
            }
        }
        if (themes) drawThemePresets(panel, x, y);
        if (categoryPanel.configTextBox != null && categoryPanel.visible) {
            categoryPanel.configTextBox.draw(x, y);
            categoryPanel.configList.draw(x, y);
        }
        for (Button button : categoryPanel.buttons) {
            button.draw(x, y);
        }
        for (Checkbox checkbox : categoryPanel.checkboxes) {
            checkbox.draw(x, y);
        }
        for (Slider slider : categoryPanel.sliders) {
            slider.draw(x, y);
        }

        List<MultiDropdownBox> multiList = new ArrayList<>(categoryPanel.multiDropdownBoxes);
        Collections.reverse(multiList);
        for (MultiDropdownBox db : multiList) {
            db.draw(x, y);
        }
        List<DropdownBox> list = new ArrayList<>(categoryPanel.dropdownBoxes);
        Collections.reverse(list);
        for (DropdownBox db : list) {
            db.draw(x, y);
        }

        for (DropdownBox db : list) {
            if (db.active) {
                db.visibleRows = listRows(panel, db.y, db.buttons.size());
                db.scrollRow = scrollRows(db.scrollRow, db.buttons.size(), db.visibleRows);
                for (int i = 0; i < db.buttons.size(); i++) {
                    db.buttons.get(i).y = db.y + 10 + 9 * (i - db.scrollRow);
                }
                drawListBackground(panel, db.x, db.y, db.width, db.visibleRows, db.scrollRow, db.buttons.size());
                for (DropdownButton b : db.visibleButtons()) {
                    b.draw(x, y);
                }
            }
        }
        for (MultiDropdownBox db : multiList) {
            if (db.active) {
                db.visibleRows = listRows(panel, db.y, db.buttons.size());
                db.scrollRow = scrollRows(db.scrollRow, db.buttons.size(), db.visibleRows);
                for (int i = 0; i < db.buttons.size(); i++) {
                    db.buttons.get(i).y = db.y + 10 + 9 * (i - db.scrollRow);
                }
                drawListBackground(panel, db.x, db.y, db.width, db.visibleRows, db.scrollRow, db.buttons.size());
                for (MultiDropdownButton b : db.visibleButtons()) {
                    b.draw(x, y);
                }
            }
        }

        if (moduleTab) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            drawScrollBar(categoryPanel);

            if (categoryPanel.groupBoxes.isEmpty() && !panel.searchQuery.trim().isEmpty()) {
                String message = "No modules match \"" + panel.searchQuery.trim() + "\"";
                float centreX = panel.x + panel.dragX + (CONTENT_LEFT + CONTENT_RIGHT) / 2;
                ExhibitionFonts.fs.drawStringWithShadow(message, centreX - ExhibitionFonts.fs.getWidth(message) / 2, panel.y + panel.dragY + 40, Colors.getColor(150, op()));
            }
        }
    }

    @Override
    public void categoryPanelMouseMovedOrUp(CategoryPanel categoryPanel, int x, int y, int button) {
        for (Slider slider : categoryPanel.sliders) {
            slider.mouseReleased(x, y, button);
        }
        for (ColorPreview cp : categoryPanel.colorPreviews) {
            for (HSVColorPicker slider : cp.sliders) {
                slider.mouseReleased(x, y, button);
            }
        }
    }

    private final List<float[]> themeRects = new ArrayList<>();

    private static boolean themesTab(CategoryButton button) {
        return button.category == null && "Themes".equals(button.name);
    }

    private void layoutThemes(CategoryPanel categoryPanel) {
        MainPanel panel = categoryPanel.categoryButton.panel;
        categoryPanel.clearLayout();
        categoryPanel.scroll = clamp(categoryPanel.scroll, 0, categoryPanel.maxScroll);

        float xOff = 50 + panel.x;
        float yOff = 15 + panel.y - categoryPanel.scroll;

        float y = 12;
        NumberProperty[] numbers = {ClientSettingsModule.themeSpeed, ClientSettingsModule.themeLength, ClientSettingsModule.themeSaturation};
        for (NumberProperty number : numbers) {
            Slider slider = (Slider) categoryPanel.settingCache.computeIfAbsent(number, s -> new Slider(categoryPanel, 0, 0, (NumberProperty) s));
            slider.width = Slider.WIDE_WIDTH;
            slider.x = xOff + 1.5F;
            slider.y = yOff + y + 4;
            categoryPanel.sliders.add(slider);
            y += 12;
        }
        DropdownBox direction = (DropdownBox) categoryPanel.settingCache.computeIfAbsent(ClientSettingsModule.themeDirection, s -> new DropdownBox(s, 0, 0, categoryPanel));
        direction.width = DropdownBox.WIDE_WIDTH;
        direction.setPosition(xOff + 0.5F, yOff + y + 4);
        categoryPanel.dropdownBoxes.add(direction);
        y += 17;
        categoryPanel.groupBoxes.add(new GroupBox("Theme", categoryPanel, xOff, yOff, 90, y - 11, true));

        rip.tempest.processes.Themes.Preset[] presets = rip.tempest.processes.Themes.Preset.values();
        themeRects.clear();
        float listX = xOff + 95;
        for (int i = 0; i < presets.length; i++) {
            themeRects.add(new float[]{listX + 3 + (i % 2) * 91, yOff + 10 + (i / 2) * 9, 88, 8.5F});
        }
        int rows = (presets.length + 1) / 2;
        float listH = rows * 9 + 3;
        categoryPanel.groupBoxes.add(new GroupBox("Presets", categoryPanel, listX, yOff, 185, listH, true));

        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        float patternY = yOff + listH + 22;
        for (int i = 0; i < patterns.length; i++) {
            themeRects.add(new float[]{listX + 3 + (i % 2) * 91, patternY + 10 + (i / 2) * 9, 88, 8.5F});
        }
        int patternRows = (patterns.length + 1) / 2;
        float patternH = patternRows * 9 + 3;
        categoryPanel.groupBoxes.add(new GroupBox("Array List Patterns", categoryPanel, listX, patternY, 185, patternH, true));

        float bottom = patternY + categoryPanel.scroll + 10 + patternH;
        categoryPanel.maxScroll = Math.max(0, bottom - (panel.y + CONTENT_BOTTOM) + 4);
    }

    private void drawThemePresets(MainPanel panel, float mouseX, float mouseY) {
        rip.tempest.processes.Themes.Preset[] presets = rip.tempest.processes.Themes.Preset.values();
        TTFFontRenderer font = ExhibitionFonts.fss;
        float top = panel.y + panel.dragY + CONTENT_TOP, bottom = panel.y + panel.dragY + CONTENT_BOTTOM;
        for (int i = 0; i < presets.length && i < themeRects.size(); i++) {
            rip.tempest.processes.Themes.Preset preset = presets[i];
            float[] r = themeRects.get(i);
            float rx = r[0] + panel.dragX, ry = r[1] + panel.dragY;
            if (ry > bottom || ry + r[3] < top) continue;
            boolean inUse = rip.tempest.processes.Themes.active() && rip.tempest.processes.Themes.current() == preset;
            boolean hover = hovering(mouseX, mouseY, rx, ry, r[2], r[3]);

            RenderingUtil.rectangle(rx - 0.3, ry + 1.7, rx + 18.3, ry + 6.3, Colors.getColor(10, op()));
            for (int s = 0; s < 6; s++) {
                int c = rip.tempest.processes.Themes.sample(preset, s / 6.0 * 0.5);
                RenderingUtil.rectangle(rx + s * 3, ry + 2, rx + s * 3 + 3, ry + 6, Colors.getColor(c >> 16 & 255, c >> 8 & 255, c & 255, op()));
            }
            int text = inUse ? hud(op()) : hover ? Colors.getColor(220, op()) : Colors.getColor(150, op());
            font.drawStringWithShadow(preset.toString(), rx + 21, ry + 1.5F, text);
        }

        rip.tempest.modules.impl.visuals.ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
        if (list == null) return;
        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        double t = list.patternTime();
        for (int i = 0; i < patterns.length && presets.length + i < themeRects.size(); i++) {
            rip.tempest.utils.render.ArrayPattern pattern = patterns[i];
            float[] r = themeRects.get(presets.length + i);
            float rx = r[0] + panel.dragX, ry = r[1] + panel.dragY;
            if (ry > bottom || ry + r[3] < top) continue;
            boolean chosen = list.pattern.getValue() == pattern;
            boolean hover = hovering(mouseX, mouseY, rx, ry, r[2], r[3]);
            RenderingUtil.rectangle(rx - 0.3, ry + 1.7, rx + 18.3, ry + 6.3, Colors.getColor(10, op()));
            if (pattern != rip.tempest.utils.render.ArrayPattern.Off) {
                for (int bx = 0; bx < 9; bx++) {
                    for (int by = 0; by < 2; by++) {
                        int c = pattern.color(bx * 2 + 1, by * 2 + 1, 18, 4, t, list.motion());
                        RenderingUtil.rectangle(rx + bx * 2, ry + 2 + by * 2, rx + bx * 2 + 2, ry + 4 + by * 2, Colors.getColor(c >> 16 & 255, c >> 8 & 255, c & 255, op()));
                    }
                }
            } else {
                RenderingUtil.rectangle(rx, ry + 2, rx + 18, ry + 6, Colors.getColor(31, op()));
            }
            int text = chosen ? hud(op()) : hover ? Colors.getColor(220, op()) : Colors.getColor(150, op());
            font.drawStringWithShadow(pattern == rip.tempest.utils.render.ArrayPattern.Off ? "None" : pattern.toString(), rx + 21, ry + 1.5F, text);
        }
    }

    private void layoutModules(CategoryPanel categoryPanel) {
        MainPanel panel = categoryPanel.categoryButton.panel;
        categoryPanel.clearLayout();
        categoryPanel.scroll = clamp(categoryPanel.scroll, 0, categoryPanel.maxScroll);

        float baseX = 50 + panel.x;
        float startY = 15 + panel.y;
        float[] columns = {startY, startY, startY};
        List<Module> noSettings = new ArrayList<>();

        String query = panel.searchQuery.trim().toLowerCase();
        List<Module> modules = query.isEmpty() ? modulesIn(categoryPanel.categoryButton.category) : searchMatches(query);
        for (Module module : modules) {
            List<Property<?>> settings = visibleSettings(module);
            if (settings.isEmpty()) {
                noSettings.add(module);
                continue;
            }
            int column = shortest(columns);
            columns[column] += layoutModule(categoryPanel, module, settings, baseX + column * 95, columns[column] - categoryPanel.scroll);
        }
        if (!noSettings.isEmpty()) {
            int column = shortest(columns);
            columns[column] += layoutNoSettings(categoryPanel, noSettings, baseX + column * 95, columns[column] - categoryPanel.scroll);
        }

        float bottom = Math.max(columns[0], Math.max(columns[1], columns[2]));
        categoryPanel.maxScroll = Math.max(0, bottom - (panel.y + CONTENT_BOTTOM) + 4);
    }

    @SuppressWarnings("unchecked")
    private float layoutModule(CategoryPanel categoryPanel, Module module, List<Property<?>> settings, float xOff, float yOff) {
        float y = 20;
        Button button = categoryPanel.buttonCache.computeIfAbsent(module, m -> new Button(categoryPanel, m.getLabel(), 0, 0, m));
        button.x = xOff + 0.5F;
        button.y = yOff + 10;
        categoryPanel.buttons.add(button);

        float x1 = 0.5F;
        for (Property<?> setting : settings) {
            if (!isBoolean(setting)) continue;
            Checkbox checkbox = (Checkbox) categoryPanel.settingCache.computeIfAbsent(setting, s -> new Checkbox(categoryPanel, 0, 0, (Property<Boolean>) s));
            boolean wide = ExhibitionFonts.fss.getWidth(display(setting.getLabel())) > 35;
            if (wide && x1 == 44.5F) {
                x1 = 0.5F;
                y += 10;
            }
            checkbox.x = xOff + x1;
            checkbox.y = yOff + y;
            categoryPanel.checkboxes.add(checkbox);
            x1 += wide ? 88 : 44;
            if (x1 >= 88.5F) {
                x1 = 0.5F;
                y += 10;
            }
        }
        if (x1 == 44.5F) {
            y += 10;
        }

        x1 = 0.5F;
        float tY = 0;
        for (Property<?> setting : settings) {
            if (!(setting instanceof NumberProperty)) continue;
            Slider slider = (Slider) categoryPanel.settingCache.computeIfAbsent(setting, s -> new Slider(categoryPanel, 0, 0, (NumberProperty) s));
            boolean wide = sliderTooWide(slider);
            if (wide && x1 == 44.5F) {
                x1 = 0.5F;
                y += 12;
            }
            slider.width = wide ? Slider.WIDE_WIDTH : Slider.NARROW_WIDTH;
            slider.x = xOff + x1 + 1;
            slider.y = yOff + y + 4;
            categoryPanel.sliders.add(slider);
            x1 += wide ? 88 : 44;
            tY = 10;
            if (x1 >= 88.5F) {
                tY = 0;
                x1 = 0.5F;
                y += 12;
            }
        }

        if (settings.stream().anyMatch(this::isChoice)) {
            if (x1 == 44.5F) {
                y += 14;
            }
            x1 = 0.5F;
        }
        for (Property<?> setting : settings) {
            boolean wide;
            if (setting instanceof MultiModeProperty) {
                MultiDropdownBox box = (MultiDropdownBox) categoryPanel.settingCache.computeIfAbsent(setting, s -> new MultiDropdownBox((MultiModeProperty<?>) s, 0, 0, categoryPanel));
                wide = ExhibitionFonts.fss.getWidth(box.name) > MultiDropdownBox.NARROW_WIDTH;
                if (wide && x1 == 44.5F) {
                    y += 17;
                    x1 = 0.5F;
                }
                box.width = wide ? MultiDropdownBox.WIDE_WIDTH : MultiDropdownBox.NARROW_WIDTH;
                box.setPosition(xOff + x1, yOff + y + 4);
                categoryPanel.multiDropdownBoxes.add(box);
            } else if (setting instanceof ModeProperty || setting instanceof SelectProperty) {
                DropdownBox box = (DropdownBox) categoryPanel.settingCache.computeIfAbsent(setting, s -> new DropdownBox(s, 0, 0, categoryPanel));
                wide = dropdownTooWide(box);
                if (wide && x1 == 44.5F) {
                    y += 17;
                    x1 = 0.5F;
                }
                box.width = wide ? DropdownBox.WIDE_WIDTH : DropdownBox.NARROW_WIDTH;
                box.setPosition(xOff + x1, yOff + y + 4);
                categoryPanel.dropdownBoxes.add(box);
            } else {
                continue;
            }
            tY = 17;
            x1 += wide ? 88 : 44;
            if (x1 >= 88.5F) {
                y += 17;
                tY = 0;
                x1 = 0.5F;
            }
        }

        if (settings.stream().anyMatch(this::isText)) {
            if (x1 == 44.5F) {
                y += 11;
            }
            x1 = 0.5F;
        }
        for (Property<?> setting : settings) {
            if (!isText(setting)) continue;
            TextBox textBox = (TextBox) categoryPanel.settingCache.computeIfAbsent(setting, s -> new TextBox((Property<String>) s, 0, 0, categoryPanel));
            textBox.x = xOff + x1;
            textBox.y = yOff + y + 4;
            categoryPanel.textBoxes.add(textBox);
            tY = 16;
            x1 += 88;
            if (x1 >= 88.5F) {
                y += 15.5F;
                tY = 0;
                x1 = 0.5F;
            }
        }
        y += tY;

        float height = y == 34 ? 40 : y - 11;
        categoryPanel.groupBoxes.add(new GroupBox(module.getLabel(), categoryPanel, xOff, yOff, height));
        return height + 11;
    }

    private float layoutNoSettings(CategoryPanel categoryPanel, List<Module> modules, float xOff, float yOff) {
        float y = 20, x1 = 0.5F;
        for (Module module : modules) {
            Button button = categoryPanel.buttonCache.computeIfAbsent(module, m -> new Button(categoryPanel, m.getLabel(), 0, 0, m));
            boolean wide = ExhibitionFonts.fs.getWidth(module.getLabel()) > 41;
            if (wide && x1 == 44.5F) {
                x1 = 0.5F;
                y += 17;
            }
            button.x = xOff + x1;
            button.y = yOff + y;
            categoryPanel.buttons.add(button);
            x1 += wide ? 88 : 44;
            if (x1 >= 88.5F) {
                x1 = 0.5F;
                y += 17;
            }
        }
        if (x1 == 44.5F) {
            y += 17;
        }
        float height = y - 11;
        categoryPanel.groupBoxes.add(new GroupBox("No Settings", categoryPanel, xOff, yOff, 90, height, true));
        return height + 11;
    }

    private boolean sliderTooWide(Slider slider) {
        float label = ExhibitionFonts.fss.getWidth(display(slider.setting.getLabel()));
        float value = ExhibitionFonts.fs.getWidth(formatValue(slider.setting, slider.setting.getMax()));
        return label + value + 4 > Slider.NARROW_WIDTH + 4;
    }

    private boolean dropdownTooWide(DropdownBox box) {
        if (ExhibitionFonts.fss.getWidth(box.getName()) > DropdownBox.NARROW_WIDTH) return true;
        for (String option : box.getOptions()) {
            if (ExhibitionFonts.fss.getWidth(option) > DropdownBox.NARROW_WIDTH - 10) return true;
        }
        return false;
    }

    private void drawScrollBar(CategoryPanel categoryPanel) {
        if (categoryPanel.maxScroll <= 0) return;
        MainPanel panel = categoryPanel.categoryButton.panel;
        float top = panel.y + panel.dragY + CONTENT_TOP + 4;
        float height = CONTENT_BOTTOM - CONTENT_TOP - 8;
        float visible = height / (height + categoryPanel.maxScroll);
        float thumb = Math.max(12, height * visible);
        float thumbY = top + (height - thumb) * (categoryPanel.scroll / categoryPanel.maxScroll);
        float x = panel.x + panel.dragX + 333;
        RenderingUtil.rectangle(x, top, x + 2, top + height, Colors.getColor(17, op()));
        RenderingUtil.rectangleBordered(x - 0.5, thumbY, x + 2.5, thumbY + thumb, 0.5, Colors.getColor(50, op()), Colors.getColor(10, op()));
    }

    private int listRows(MainPanel panel, float boxY, int options) {
        int fit = (int) Math.floor((panel.y + CONTENT_BOTTOM - 2 - (boxY + 10)) / 9);
        return Math.max(Math.min(3, options), Math.min(options, Math.min(MAX_LIST_ROWS, fit)));
    }

    private static int scrollRows(int row, int options, int rows) {
        return Math.max(0, Math.min(row, options - rows));
    }

    private boolean overList(MainPanel panel, float boxX, float boxY, float width, int rows, int mouseX, int mouseY) {
        float left = boxX + panel.dragX, top = boxY + panel.dragY;
        return mouseX >= left && mouseX <= left + width && mouseY > top + 8 && mouseY <= top + 9 + 9 * rows;
    }

    private void drawListBackground(MainPanel panel, float boxX, float boxY, float width, int rows, int scrollRow, int options) {
        float left = boxX + panel.dragX, top = boxY + panel.dragY;
        float bottom = top + 9 + 9 * rows;
        RenderingUtil.rectangle(left - 0.3, top + 10 - 0.3, left + width + 0.3, bottom + 0.3, Colors.getColor(10, op()));
        RenderingUtil.drawGradient(left, top + 10, left + width, bottom, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (options <= rows) return;

        float trackTop = top + 11, trackHeight = bottom - 1 - trackTop;
        float thumb = Math.max(6, trackHeight * rows / options);
        float thumbY = trackTop + (trackHeight - thumb) * scrollRow / (options - rows);
        RenderingUtil.rectangle(left + width - 2.5, trackTop, left + width - 1, trackTop + trackHeight, Colors.getColor(20, op()));
        RenderingUtil.rectangle(left + width - 2.5, thumbY, left + width - 1, thumbY + thumb, Colors.getColor(90, op()));
    }

    private void beginClip(MainPanel panel) {
        double scale = ExhibitionClickInterface.pixelScale();
        int left = (int) Math.floor((panel.x + panel.dragX + CONTENT_LEFT) * scale);
        int right = (int) Math.ceil((panel.x + panel.dragX + CONTENT_RIGHT) * scale);
        int top = (int) Math.floor((panel.y + panel.dragY + CONTENT_TOP) * scale);
        int bottom = (int) Math.ceil((panel.y + panel.dragY + CONTENT_BOTTOM) * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, mc.displayHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private boolean inContent(MainPanel panel, float x, float y) {
        float left = panel.x + panel.dragX;
        float top = panel.y + panel.dragY;
        return x >= left + CONTENT_LEFT && x <= left + CONTENT_RIGHT && y >= top + CONTENT_TOP && y <= top + CONTENT_BOTTOM;
    }

    @Override
    public void groupBoxDraw(GroupBox groupBox, float x, float y) {
        float xOff = groupBox.x + groupBox.categoryPanel.categoryButton.panel.dragX - 2.5F;
        float yOff = groupBox.y + groupBox.categoryPanel.categoryButton.panel.dragY + 10;
        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(xOff + 4.5, yOff - 6, xOff + ExhibitionFonts.fs.getWidth(groupBox.label) + 6.5, yOff - 5.5, -1);
        RenderingUtil.rectangle(xOff + 5, yOff - 5.5, xOff + ExhibitionFonts.fs.getWidth(groupBox.label) + 6, yOff - 5, -1);
        Depth.render(GL11.GL_LESS);
        RenderingUtil.rectangleBordered(xOff, yOff - 6, xOff + groupBox.width, yOff + groupBox.height, 0.5, Colors.getColor(0, 0), Colors.getColor(10, op()));
        Depth.post();

        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(xOff + 4.5, yOff - 6, xOff + ExhibitionFonts.fs.getWidth(groupBox.label) + 6.5, yOff - 5.5, -1);
        RenderingUtil.rectangle(xOff + 5, yOff - 5.5, xOff + ExhibitionFonts.fs.getWidth(groupBox.label) + 6, yOff - 5, -1);

        Depth.render(GL11.GL_LESS);
        RenderingUtil.rectangleBordered(xOff + 0.5, yOff - 5.5, xOff + groupBox.width - 0.5, yOff + groupBox.height - 0.5, 0.5, Colors.getColor(17, op()), Colors.getColor(48, op()));
        Depth.post();

        if (groupBox.renderLabel) {
            ExhibitionFonts.fs.drawStringWithShadow(groupBox.label, xOff + 6, yOff - 6.5F, Colors.getColor(220, op()));
        }
    }

    @Override
    public void buttonMouseClicked(Button p0, int p2, int p3, int p4, CategoryPanel panel) {
        if (panel.categoryButton.enabled) {
            float xOff = panel.categoryButton.panel.dragX;
            float yOff = panel.categoryButton.panel.dragY;
            boolean hovering = p2 >= p0.x + xOff && p3 >= p0.y + yOff && p2 <= p0.x + 35 + xOff && p3 <= p0.y + 6 + yOff;
            if (hovering) {
                if (p4 == 0) {
                    if (!p0.isBinding) {
                        p0.module.toggle();
                        p0.enabled = p0.module.isEnabled();
                    } else {
                        p0.isBinding = false;
                    }
                } else if (p4 == 1) {
                    if (p0.isBinding) {
                        p0.module.setKey(Keyboard.KEY_NONE);
                        p0.isBinding = false;
                    } else {
                        p0.isBinding = true;
                    }
                }
            } else if (p0.isBinding) {
                p0.isBinding = false;
            }
        }
    }

    @Override
    public void buttonDraw(Button p0, float p2, float p3, CategoryPanel panel) {
        if (panel.categoryButton.enabled) {
            GlStateManager.pushMatrix();
            float xOff = panel.categoryButton.panel.dragX;
            float yOff = panel.categoryButton.panel.dragY;
            RenderingUtil.rectangleBordered(p0.x + xOff + 0.6, p0.y + yOff + 0.6, p0.x + 6 + xOff + -0.6, p0.y + 6 + yOff + -0.6, 0.5, Colors.getColor(0, 0), Colors.getColor(10, op()));
            RenderingUtil.drawGradient(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + 6 + xOff + -1, p0.y + 6 + yOff + -1, Colors.getColor(76, op()), Colors.getColor(51, op()));
            p0.enabled = p0.module.isEnabled();
            boolean hovering = p2 >= p0.x + xOff && p3 >= p0.y + yOff && p2 <= p0.x + 35 + xOff && p3 <= p0.y + 6 + yOff;
            GlStateManager.pushMatrix();
            ExhibitionFonts.fs.drawStringWithShadow(p0.module.getLabel(), (p0.x + xOff + 3), (p0.y + 0.5f + yOff - 7), Colors.getColor(220, op()));
            ExhibitionFonts.fss.drawStringWithShadow("Enable", (p0.x + 7.6f + xOff), (p0.y + 1 + yOff), Colors.getColor(185, op()));

            String meme = "[" + keyName(p0.module.getKey()) + "]";
            GlStateManager.pushMatrix();
            GlStateManager.translate((p0.x + xOff + 29), (p0.y + 1f + yOff), 0);
            GlStateManager.enableBlend();
            GlStateManager.scale(0.5, 0.5, 0.5);
            mc.fontRendererObj.drawStringWithShadow(meme, 0, 0, p0.isBinding ? Colors.getColor(216, 56, 56, op()) : Colors.getColor(75, op()));
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();

            GlStateManager.popMatrix();
            if (p0.enabled) {
                RenderingUtil.drawGradient(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + xOff + 5, p0.y + yOff + 5, hud(op()), hud((int) (120 * (opacity.getOpacity() / 255F))));
            }
            if (hovering && !p0.enabled) {
                RenderingUtil.rectangle(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + xOff + 5, p0.y + yOff + 5, Colors.getColor(255, 40));
            }

            if (hovering) {
                String description = p0.module.getDescription();
                hover(description != null && !description.isEmpty() ? description : p0.module.getLabel(), Colors.getColor(220, op()));
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void buttonKeyPressed(Button button, int key) {
        if (button.isBinding && key != 0) {
            int keyToBind = key;
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK) {
                keyToBind = Keyboard.KEY_NONE;
            }
            button.module.setKey(keyToBind);
            button.isBinding = false;
        }
    }

    @Override
    public void checkBoxMouseClicked(Checkbox p0, int p2, int p3, int p4, CategoryPanel panel) {
        if (panel.categoryButton.enabled) {
            float xOff = panel.categoryButton.panel.dragX;
            float yOff = panel.categoryButton.panel.dragY;
            boolean hovering = p2 >= p0.x + xOff && p3 >= p0.y + yOff && p2 <= p0.x + 35 + xOff && p3 <= p0.y + 6 + yOff;
            if (hovering && p4 == 0) {
                p0.setting.setValue(!p0.setting.getValue());
            }
        }
    }

    @Override
    public void checkBoxDraw(Checkbox p0, float p2, float p3, CategoryPanel panel) {
        if (panel.categoryButton.enabled) {
            GlStateManager.pushMatrix();
            float xOff = panel.categoryButton.panel.dragX;
            float yOff = panel.categoryButton.panel.dragY;
            GlStateManager.pushMatrix();
            String xd = display(p0.setting.getLabel());
            ExhibitionFonts.fss.drawStringWithShadow(xd, (p0.x + 7.5f + xOff), (p0.y + 1 + yOff), Colors.getColor(185, op()));
            GlStateManager.popMatrix();
            RenderingUtil.rectangleBordered(p0.x + xOff + 0.6, p0.y + yOff + 0.6, p0.x + 6 + xOff + -0.6, p0.y + 6 + yOff + -0.6, 0.5, Colors.getColor(0, 0), Colors.getColor(10, op()));
            RenderingUtil.drawGradient(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + 6 + xOff + -1, p0.y + 6 + yOff + -1, Colors.getColor(76, op()), Colors.getColor(51, op()));
            p0.enabled = p0.setting.getValue();
            boolean hovering = p2 >= p0.x + xOff && p3 >= p0.y + yOff && p2 <= p0.x + 35 + xOff && p3 <= p0.y + 6 + yOff;

            if (p0.enabled) {
                RenderingUtil.drawGradient(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + xOff + 5, p0.y + yOff + 5, hud(op()), hud((int) (120 * (opacity.getOpacity() / 255F))));
            }
            if (hovering && !p0.enabled) {
                RenderingUtil.rectangle(p0.x + xOff + 1, p0.y + yOff + 1, p0.x + xOff + 5, p0.y + yOff + 5, Colors.getColor(255, 40));
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void dropDownMouseClicked(DropdownBox dropDown, int mouseX, int mouseY, int mouse, CategoryPanel panel) {
        for (DropdownButton db : new ArrayList<>(dropDown.visibleButtons())) {
            if (dropDown.active && dropDown.panel.visible) {
                db.mouseClicked(mouseX, mouseY, mouse);
            }
        }
        MainPanel mainPanel = panel.categoryButton.panel;
        float boxX = mainPanel.dragX + dropDown.x;
        float boxY = mainPanel.dragY + dropDown.y;
        boolean onHeader = mouseX >= boxX && mouseY >= boxY && mouseX <= boxX + dropDown.width && mouseY <= boxY + 8;
        boolean onList = dropDown.active && overList(mainPanel, dropDown.x, dropDown.y, dropDown.width, dropDown.visibleButtons().size(), mouseX, mouseY);
        if (onHeader && mouse == 0 && dropDown.panel.visible) {
            if (!dropDown.active && dropDown.setting instanceof SelectProperty) {
                ((SelectProperty) dropDown.setting).onOpen();
                dropDown.rebuildButtons();
            }
            dropDown.active = !dropDown.active;
            if (dropDown.active) {
                int rows = listRows(mainPanel, dropDown.y, dropDown.buttons.size());
                int selected = dropDown.getOptions().indexOf(dropDown.getSelected());
                dropDown.visibleRows = rows;
                dropDown.scrollRow = scrollRows(selected - rows / 2, dropDown.buttons.size(), rows);
            }
        } else if (!onList) {
            dropDown.active = false;
        }
    }

    @Override
    public void dropDownDraw(DropdownBox p0, float p2, float p3, CategoryPanel panel) {
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        float w = p0.width;
        boolean hovering = (p2 >= panel.categoryButton.panel.dragX + p0.x) && (p3 >= panel.categoryButton.panel.dragY + p0.y) && (p2 <= panel.categoryButton.panel.dragX + p0.x + w) && (p3 <= panel.categoryButton.panel.dragY + p0.y + 9);

        RenderingUtil.rectangle(p0.x + xOff - 0.3, p0.y + yOff - 0.3, p0.x + xOff + w + 0.3, p0.y + yOff + 9 + 0.3, Colors.getColor(10, op()));

        RenderingUtil.drawGradient(p0.x + xOff, p0.y + yOff, p0.x + xOff + w, p0.y + yOff + 9, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (hovering) {
            RenderingUtil.rectangleBordered(p0.x + xOff, p0.y + yOff, p0.x + xOff + w, p0.y + yOff + 9, 0.3, Colors.getColor(0, 0), Colors.getColor(90, op()));
        }
        ExhibitionFonts.fss.drawStringWithShadow(p0.getName(), (p0.x + xOff + 1), (p0.y - 6 + yOff), Colors.getColor(185, op()));
        drawArrow(p0.x + xOff + w - 2 - (p0.active ? 2.5 : 0), p0.y + 4.5 + yOff, p0.active);

        GlStateManager.pushMatrix();
        Depth.pre();
        Depth.mask();
        ExhibitionFonts.fss.drawString(p0.getSelected(), (p0.x + 4 + xOff) - 1, (p0.y + 3f + yOff), Colors.getColor(151, op()));
        Depth.render();
        RenderingUtil.rectangle(p0.x + xOff, p0.y + yOff, p0.x + xOff + w - 10, p0.y + yOff + 9, Colors.getColor(151, op()));
        RenderingUtil.drawGradientSideways(p0.x + xOff + w - 10, p0.y + yOff, p0.x + xOff + w - 5, p0.y + yOff + 9, Colors.getColor(151, op()), Colors.getColor(151, 0));

        Depth.post();
        GlStateManager.popMatrix();
    }

    private void drawArrow(double x, double y, boolean open) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate(open ? 270 : 90, 0, 0, 90);

        RenderingUtil.rectangle(0 - 1, 0, 0.5 - 1, 2.5, Colors.getColor(0, op()));
        RenderingUtil.rectangle(0.5 - 1, 0, 0, 2.5, Colors.getColor(151, op()));
        RenderingUtil.rectangle(0, 0.5, 1.5 - 1, 2, Colors.getColor(151, op()));
        RenderingUtil.rectangle(1.5 - 1, 1, 2 - 1, 1.5, Colors.getColor(151, op()));

        GlStateManager.popMatrix();
    }

    @Override
    public void dropDownButtonMouseClicked(DropdownButton p0, DropdownBox p1, int x, int y, int mouse) {
        if ((x >= p1.panel.categoryButton.panel.dragX + p0.x) && (y >= p1.panel.categoryButton.panel.dragY + p0.y) && (x <= p1.panel.categoryButton.panel.dragX + p0.x + p1.width) && (y <= p1.panel.categoryButton.panel.dragY + p0.y + 8.5) && (mouse == 0)) {
            p1.setSelected(p0.name);
        }
    }

    @Override
    public void dropDownButtonDraw(DropdownButton p0, DropdownBox p1, float x, float y) {
        float xOff = p1.panel.categoryButton.panel.dragX;
        float yOff = p1.panel.categoryButton.panel.dragY;
        boolean hovering = (x >= xOff + p0.x) && (y >= yOff + p0.y) && (x <= xOff + p0.x + p1.width) && (y <= yOff + p0.y + 8.5);
        GlStateManager.pushMatrix();
        boolean active = p1.getSelected().equalsIgnoreCase(p0.name);
        TTFFontRenderer font = hovering ? ExhibitionFonts.test2 : ExhibitionFonts.test3;
        font.drawStringWithShadow((hovering || active ? "\247l" : "") + p0.name, (p0.x + 3 + xOff), (p0.y + 2f + yOff), active && !hovering ? hud(op()) : Colors.getColor(255, op()));
        GlStateManager.popMatrix();
    }

    @Override
    public void multiDropDownMouseClicked(MultiDropdownBox p0, int x, int u, int mouse, CategoryPanel panel) {
        for (MultiDropdownButton db : p0.visibleButtons()) {
            if (p0.active && p0.panel.visible) {
                db.mouseClicked(x, u, mouse);
            }
        }
        if (mouse == 0)
            if ((x >= panel.categoryButton.panel.dragX + p0.x) && (u >= panel.categoryButton.panel.dragY + p0.y) && (x <= panel.categoryButton.panel.dragX + p0.x + p0.width) && (u <= panel.categoryButton.panel.dragY + p0.y + 8) &&
                    p0.panel.visible) {
                p0.active = (!p0.active);
                if (p0.active) p0.scrollRow = 0;
            } else if (!((x >= panel.categoryButton.panel.dragX + p0.x) && (u >= panel.categoryButton.panel.dragY + p0.y + 8) && (x <= panel.categoryButton.panel.dragX + p0.x + p0.width) && (u <= panel.categoryButton.panel.dragY + p0.y + 8 + p0.visibleButtons().size() * 9))) {
                p0.active = false;
            }
    }

    @Override
    public void multiDropDownDraw(MultiDropdownBox p0, float x, float y, CategoryPanel panel) {
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        float w = p0.width;
        boolean hovering = (x >= panel.categoryButton.panel.dragX + p0.x) && (y >= panel.categoryButton.panel.dragY + p0.y) && (x <= panel.categoryButton.panel.dragX + p0.x + w) && (y <= panel.categoryButton.panel.dragY + p0.y + 9);

        RenderingUtil.rectangle(p0.x + xOff - 0.3, p0.y + yOff - 0.3, p0.x + xOff + w + 0.3, p0.y + yOff + 9 + 0.3, Colors.getColor(10, op()));

        RenderingUtil.drawGradient(p0.x + xOff, p0.y + yOff, p0.x + xOff + w, p0.y + yOff + 9, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (hovering) {
            RenderingUtil.rectangleBordered(p0.x + xOff, p0.y + yOff, p0.x + xOff + w, p0.y + yOff + 9, 0.3, Colors.getColor(0, 0), Colors.getColor(90, op()));
        }
        ExhibitionFonts.fss.drawStringWithShadow(p0.name, (p0.x + xOff + 1), (p0.y - 6 + yOff), Colors.getColor(185, op()));
        drawArrow(p0.x + xOff + w - 2 - (p0.active ? 2.5 : 0), p0.y + 4.5 + yOff, p0.active);

        List<String> enabled = new ArrayList<>();
        for (int i = 0; i < p0.buttons.size(); i++) {
            if (p0.isSelected(i))
                enabled.add(display(p0.buttons.get(i).name));
        }
        GlStateManager.pushMatrix();
        Depth.pre();
        Depth.mask();
        String str = enabled.isEmpty() ? "None" : enabled.toString().replace("[", "").replace("]", "");
        ExhibitionFonts.fss.drawString(str, (p0.x + 4 + xOff) - 1, (p0.y + 3f + yOff), -1);
        Depth.render();
        RenderingUtil.rectangle(p0.x + xOff, p0.y + yOff, p0.x + xOff + w - 10, p0.y + yOff + 9, Colors.getColor(151, op()));
        RenderingUtil.drawGradientSideways(p0.x + xOff + w - 10, p0.y + yOff, p0.x + xOff + w - 5, p0.y + yOff + 9, Colors.getColor(151, op()), Colors.getColor(151, 0));

        Depth.post();
        GlStateManager.popMatrix();
    }

    @Override
    public void multiDropDownButtonMouseClicked(MultiDropdownButton p0, MultiDropdownBox p1, int x, int y, int mouse) {
        if ((x >= p1.panel.categoryButton.panel.dragX + p0.x) && (y >= p1.panel.categoryButton.panel.dragY + p0.y) && (x <= p1.panel.categoryButton.panel.dragX + p0.x + p1.width) && (y <= p1.panel.categoryButton.panel.dragY + p0.y + 8.5) && (mouse == 0)) {
            p1.toggle(p0.index);
        }
    }

    @Override
    public void multiDropDownButtonDraw(MultiDropdownButton p0, MultiDropdownBox p1, float x, float y) {
        float xOff = p1.panel.categoryButton.panel.dragX;
        float yOff = p1.panel.categoryButton.panel.dragY;
        boolean hovering = (x >= xOff + p0.x) && (y >= yOff + p0.y) && (x <= xOff + p0.x + p1.width) && (y <= yOff + p0.y + 8.5);
        boolean active = p1.isSelected(p0.index);
        TTFFontRenderer font = hovering ? ExhibitionFonts.test2 : ExhibitionFonts.test3;
        font.drawStringWithShadow((hovering || active ? "\247l" : "") + display(p0.name), (p0.x + 3 + xOff), (p0.y + 2f + yOff), active && !hovering ? hud(op()) : Colors.getColor(255, op()));
    }

    @Override
    public void colorConstructor(ColorPreview colorPreview, float x, float y) {
        colorPreview.sliders.add(new HSVColorPicker(x + 10, y, colorPreview, colorPreview.colorObject));
    }

    @Override
    public void colorPrewviewDraw(ColorPreview colorPreview, float x, float y) {
        float xOff = colorPreview.x + colorPreview.categoryPanel.panel.dragX;
        float yOff = colorPreview.y + colorPreview.categoryPanel.panel.dragY + 75;
        RenderingUtil.rectangleBordered(xOff - 80, yOff - 6, xOff + 1, yOff + 46, 0.3, Colors.getColor(48, op()), Colors.getColor(10, op()));
        RenderingUtil.rectangle(xOff - 79, yOff - 5, xOff, yOff + 45, Colors.getColor(17, op()));
        RenderingUtil.rectangle(xOff - 74, yOff - 6, xOff - 73 + ExhibitionFonts.fs.getWidth(colorPreview.colorName) + 1, yOff - 4, Colors.getColor(17, op()));
        ExhibitionFonts.fs.drawStringWithShadow(colorPreview.colorName, xOff - 73, yOff - 8, Colors.getColor(255, op()));
        colorPreview.sliders.get(0).draw(x, y);
    }

    @Override
    public void colorPickerConstructor(HSVColorPicker picker, float x, float y) {
        Color color = new Color(picker.colorPreview.colorObject.getColorHex());
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        picker.opacity = (float) picker.colorPreview.colorObject.getAlpha() / 255;
        picker.hue = hsb[0];
        picker.saturation = hsb[1];
        picker.brightness = hsb[2];
    }

    @Override
    public void colorPickerDraw(HSVColorPicker cp, float x, float y) {
        float xOff = cp.x + cp.colorPreview.categoryPanel.panel.dragX - 85.5f;
        float yOff = cp.y + cp.colorPreview.categoryPanel.panel.dragY + 74;

        RenderingUtil.rectangle(xOff, yOff, xOff + 43, yOff + 43, Colors.getColor(32, op()));
        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(xOff + 0.5, yOff + 0.5, xOff + 42.5, yOff + 42.5, -1);
        Depth.render();
        RenderingUtil.drawGradientSideways(xOff + 0.5, yOff + 0.5, xOff + 46.5f, yOff + 42.5, Colors.getColor(255, op()), Colors.getColorOpacity(MathHelper.hsvToRGB(cp.hue, 1, 1), op()));
        RenderingUtil.drawGradient(xOff + 0.5, yOff - 4, xOff + 42.5, yOff + 42.5, Colors.getColor(0, 0), Colors.getColor(0, op()));
        Depth.post();
        RenderingUtil.rectangleBordered(xOff + (42.5 * cp.saturation) - 1, yOff + 42.5 - (42.5 * cp.brightness) - 1, xOff + (42.5 * cp.saturation) + 1, yOff + 42.5 - (42.5 * cp.brightness) + 1, 0.5, Colors.getColorOpacity(MathHelper.hsvToRGB(cp.hue, cp.saturation, cp.brightness), op()), Colors.getColor(0, op()));

        RenderingUtil.rectangle(xOff + 45, yOff, xOff + 48, yOff + 43, Colors.getColor(32, op()));

        float[] stops = {0, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 1f};
        float[] edges = {0.5f, 8, 13, 17, 22, 26, 30, 34, 42.5f};
        for (int i = 0; i < stops.length - 1; i++) {
            RenderingUtil.drawGradient(xOff + 45.5f, yOff + edges[i], xOff + 47.5f, yOff + edges[i + 1], Colors.getColorOpacity(MathHelper.hsvToRGB(stops[i], 1, 1), op()), Colors.getColorOpacity(MathHelper.hsvToRGB(stops[i + 1], 1, 1), op()));
        }

        RenderingUtil.rectangleBordered(xOff + 45, yOff + (42.5 * cp.hue) - 1.5f, xOff + 48, yOff + (42.5 * cp.hue) + 1.5f, 0.5f, Colors.getColor(0, op()), Colors.getColor(cp.selectingHue ? 255 : 200, op()));
        RenderingUtil.rectangleBordered(xOff + 50, yOff, xOff + 53, yOff + 43, 0.5f, Colors.getColor(cp.color.getRed(), cp.color.getGreen(), cp.color.getBlue(), op()), Colors.getColor(32, op()));
        RenderingUtil.rectangleBordered(xOff + 50, yOff + (42.5 * cp.opacity) - 1.5f, xOff + 53, yOff + (42.5 * cp.opacity) + 1.5f, 0.5f, Colors.getColor(0, op()), Colors.getColor(cp.selectingOpacity ? 255 : 200, op()));
        boolean shouldUpdate = (cp.selectingHue || cp.selectingColor || cp.selectingOpacity);
        if (shouldUpdate) {
            Color tcolor = Color.getHSBColor(cp.hue, cp.saturation, cp.brightness);
            cp.color.updateColors(tcolor.getRed(), tcolor.getGreen(), tcolor.getBlue(), (int) (255 * cp.opacity));
        } else {
            float[] bruh = new float[3];
            Color.RGBtoHSB(cp.color.getRed(), cp.color.getGreen(), cp.color.getBlue(), bruh);
            cp.opacity = cp.color.getAlpha() / 255F;
            cp.hue = bruh[0];
            cp.saturation = bruh[1];
            cp.brightness = bruh[2];
        }

        if (cp.selectingOpacity) {
            float tempY = y;
            if (tempY > yOff + 42) {
                tempY = yOff + 42;
            } else if (tempY < yOff) {
                tempY = yOff;
            }
            tempY -= yOff;
            cp.opacity = tempY / 42;
        }
        if (cp.selectingHue) {
            float tempY = y;
            if (tempY > yOff + 42) {
                tempY = yOff + 42;
            } else if (tempY < yOff) {
                tempY = yOff;
            }
            tempY -= yOff;
            cp.hue = tempY / 42;
        }
        if (cp.selectingColor) {
            float tempY = y;
            float tempX = x;
            if (tempY > yOff + 43) {
                tempY = yOff + 43;
            } else if (tempY < yOff) {
                tempY = yOff;
            }
            tempY -= yOff;
            if (tempX > xOff + 43) {
                tempX = xOff + 43;
            } else if (tempX < xOff) {
                tempX = xOff;
            }
            tempX -= xOff;
            cp.brightness = 1 - tempY / 43;
            cp.saturation = tempX / 43;
        }

        if (x > xOff + 57 && y > yOff && x < xOff + 72 && y < yOff + 30) {
            String hex = String.format("#%02X%02X%02X%02X", cp.color.getAlpha(), cp.color.getRed(), cp.color.getGreen(), cp.color.getBlue());
            hover(hex + String.format(" rgba(%d, %d, %d, %d)", cp.color.getRed(), cp.color.getGreen(), cp.color.getBlue(), cp.color.getAlpha()), Colors.getColor(255, op()));
        }

        RenderingUtil.rectangle(xOff + 57, yOff, xOff + 72, yOff + 30, Colors.getColor(255, op()));
        boolean offset = false;
        for (int yThing = 0; yThing < 30; yThing += 1) {
            for (int i = offset ? 0 : 1; i < 15; i += 2) {
                RenderingUtil.rectangle(xOff + 57 + i, yOff + yThing, xOff + 57 + i + 1, yOff + yThing + 1, Colors.getColor(190, op()));
            }
            offset = !offset;
        }

        float scale = opacity.getOpacity() / 255;
        int colorXD = Colors.getColor(cp.colorPreview.colorObject.getRed(), cp.colorPreview.colorObject.getGreen(), cp.colorPreview.colorObject.getBlue(), (int) (cp.colorPreview.colorObject.getAlpha() * scale));
        RenderingUtil.rectangleBordered(xOff + 59, yOff + 2, xOff + 70, yOff + 28, 0.5f, colorXD, Colors.getColor(0, op()));
        GlStateManager.pushMatrix();
        GlStateManager.translate(xOff + 65, yOff + 33, 0);
        GlStateManager.scale(0.5, 0.5, 0.5);
        GlStateManager.enableBlend();
        mc.fontRendererObj.drawStringWithShadow("Copy", 0 - mc.fontRendererObj.getStringWidth("Copy") / 2F, 0, Colors.getColor(255, op()));
        mc.fontRendererObj.drawStringWithShadow("Paste", 0 - mc.fontRendererObj.getStringWidth("Paste") / 2F, 12, Colors.getColor(255, op()));
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    public void colorPickerClick(HSVColorPicker cp, float x, float y, int mouse) {
        if (!cp.colorPreview.categoryPanel.enabled)
            return;
        float xOff = cp.x + cp.colorPreview.categoryPanel.panel.dragX - 85.5f;
        float yOff = cp.y + cp.colorPreview.categoryPanel.panel.dragY + 74;

        if (mouse == 0) {
            try {
                if (hovering(x, y, xOff + 59, yOff + 33, 12, 4)) {
                    String hex = String.format("#%02X%02X%02X%02X", cp.color.getAlpha(), cp.color.getRed(), cp.color.getGreen(), cp.color.getBlue());
                    GuiScreen.setClipboardString(hex);
                    Logger.chatPrint("Copied " + cp.colorPreview.colorName + " to clipboard: " + hex);
                }
                if (hovering(x, y, xOff + 59, yOff + 39, 12, 4)) {
                    String hex = GuiScreen.getClipboardString().trim();
                    if (!Objects.equals(hex, "")) {
                        String s = hex.replace("#", "").replace("0x", "").trim();
                        if (s.length() >= 6) {
                            int hexValue = (int) Long.parseLong(s, 16);
                            int alpha = (hexValue >> 24 & 0xFF);
                            if (s.length() < 8) {
                                alpha = 255;
                            }
                            int red = (hexValue >> 16 & 0xFF);
                            int green = (hexValue >> 8 & 0xFF);
                            int blue = (hexValue & 0xFF);
                            float[] hsb = Color.RGBtoHSB(red, green, blue, null);
                            cp.opacity = alpha / 255F;
                            cp.hue = hsb[0];
                            cp.saturation = hsb[1];
                            cp.brightness = hsb[2];
                            cp.color.updateColors(red, green, blue, alpha);
                        }
                    }
                }
            } catch (Exception e) {
                Logger.chatPrint("Invalid colour on the clipboard.");
            }
        }
        if (!cp.selectingHue && !cp.selectingColor && !cp.selectingOpacity && mouse == 0) {
            if (hovering(x, y, xOff + 50, yOff, 3, 43)) cp.selectingOpacity = true;
            if (hovering(x, y, xOff + 45, yOff, 3, 43)) cp.selectingHue = true;
            if (hovering(x, y, xOff, yOff, 43, 43)) cp.selectingColor = true;
        }
    }

    @Override
    public void colorPickerMovedOrUp(HSVColorPicker slider, float x, float y, int mouse) {
        if (mouse == 0) {
            slider.selectingOpacity = false;
            slider.selectingColor = false;
            slider.selectingHue = false;
        }
    }

    private boolean hovering(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    @Override
    public void configButtonDraw(ConfigButton configButton, float x, float y) {
        float xOff = configButton.configList.categoryPanel.categoryButton.panel.dragX;
        float yOff = configButton.configList.categoryPanel.categoryButton.panel.dragY;

        boolean hovering = (x >= xOff + configButton.x) && (y >= yOff + configButton.y) && (x <= xOff + configButton.x + 85) && (y <= yOff + configButton.y + 7.5);

        RenderingUtil.rectangle(configButton.x + xOff - 0.3, configButton.y + yOff - 0.3, configButton.x + xOff + 85 + 0.3, configButton.y + yOff + 7.5 + 0.3, Colors.getColor(10, op()));
        RenderingUtil.drawGradient(configButton.x + xOff, configButton.y + yOff, configButton.x + xOff + 85, configButton.y + yOff + 7.5, Colors.getColor(31, op()), Colors.getColor(36, op()));
        TTFFontRenderer font = ExhibitionFonts.fsmallbold;

        String name = configButton.buttonType.label();
        font.drawStringWithShadow(name, (configButton.x + 85 / 2F + xOff) - font.getWidth(name) / 2, (configButton.y + 2 + yOff), hovering ? Colors.getColor(255, op()) : Colors.getColor(150, op()));
        if (hovering) {
            RenderingUtil.rectangleBordered(configButton.x + xOff, configButton.y + yOff, configButton.x + xOff + 85, configButton.y + yOff + 7.5, 0.3, Colors.getColor(0, 0), Colors.getColor(90, op()));
        }
    }

    @Override
    public void configButtonMouseClicked(ConfigButton configButton, float x, float y, int button) {
        float xOff = configButton.configList.categoryPanel.categoryButton.panel.dragX;
        float yOff = configButton.configList.categoryPanel.categoryButton.panel.dragY;
        boolean hovering = (x >= xOff + configButton.x) && (y >= yOff + configButton.y) && (x <= xOff + configButton.x + 84) && (y <= yOff + configButton.y + 7.5);
        if (!hovering || button != 0) return;

        ConfigList list = configButton.configList;
        ConfigManager configs = Tempest.INSTANCE.getConfigManager();
        switch (configButton.buttonType) {
            case CREATE -> {
                String name = list.configTextBox.textString.trim();
                if (!name.isEmpty() && configs.saveConfig(name)) {
                    Logger.chatPrint("Config '" + name + "' has been created.");
                }
            }
            case OPEN_FOLDER -> {
                try {
                    if (!ConfigManager.CONFIGS_DIR.exists()) ConfigManager.CONFIGS_DIR.mkdirs();
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(ConfigManager.CONFIGS_DIR);
                    }
                } catch (Exception e) {
                    Logger.chatPrint("Couldn't open the configs folder: " + e.getMessage());
                }
            }
            default -> {
                if (list.configs.length == 0 || list.selectedConfigID < 0 || list.selectedConfigID >= list.configs.length) break;
                String name = list.configs[list.selectedConfigID];
                switch (configButton.buttonType) {
                    case LOAD -> {
                        if (configs.loadConfig(name)) Logger.chatPrint("Config '" + name + "' has been loaded.");
                    }
                    case SAVE -> {
                        if (configs.saveConfig(name)) Logger.chatPrint("Config '" + name + "' has been saved.");
                    }
                    case DELETE -> {
                        if (configs.deleteConfig(name)) Logger.chatPrint("Config '" + name + "' has been removed.");
                        list.selectedConfigID = -1;
                    }
                    default -> {
                    }
                }
            }
        }
        list.configs = ConfigList.listConfigs();
    }

    @Override
    public void configListDraw(ConfigList configList, float x, float y) {
        float xOff = configList.categoryPanel.categoryButton.panel.dragX;
        float yOff = configList.categoryPanel.categoryButton.panel.dragY;

        int height = 80;

        if (configList.categoryPanel.visible && System.currentTimeMillis() - lastConfigRefresh >= 1000) {
            configList.configs = ConfigList.listConfigs();
            lastConfigRefresh = System.currentTimeMillis();
        }

        boolean hovering = configList.hovering = (x >= xOff + configList.x) && (y >= yOff + configList.y) && (x <= xOff + configList.x + 84) && (y <= yOff + configList.y + height);

        RenderingUtil.rectangle(configList.x + xOff - 0.3, configList.y + yOff - 0.3, configList.x + xOff + 84 + 1.5, configList.y + yOff + height + 0.3, Colors.getColor(10, op()));
        RenderingUtil.drawGradient(configList.x + xOff, configList.y + yOff, configList.x + xOff + 85, configList.y + yOff + height, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (hovering) {
            RenderingUtil.rectangleBordered(configList.x + xOff, configList.y + yOff, configList.x + xOff + 85, configList.y + yOff + height, 0.3, Colors.getColor(0, 0), Colors.getColor(90, op()));
        }

        float offsetY = 2 + (configList.amountScrolled);
        float offsetValueThing = (configList.amountScrolled / -8.0F);
        float totalHeight = (configList.configs.length * 8.0F);
        if (totalHeight > height) {
            float relativeScale = (height - 1) * (height / totalHeight);
            float relativeOffset = (height - 1) * (offsetValueThing / configList.configs.length);
            float yPos = configList.y + yOff + relativeOffset;
            RenderingUtil.rectangle(configList.x + xOff + 82, yPos + 0.5, configList.x + xOff + 83, yPos + relativeScale + 0.5, Colors.getColor(10, op()));
            RenderingUtil.rectangleBordered(configList.x + xOff + 81.5, yPos + 1, configList.x + xOff + 83.5, yPos + relativeScale, 0.5, Colors.getColor(50, op()), Colors.getColor(10, op()));
            RenderingUtil.rectangle(configList.x + xOff + 82, yPos + 1, configList.x + xOff + 83, yPos + relativeScale, Colors.getColor(50, op()));
        }

        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(configList.x + xOff, configList.y + yOff + 0.5, configList.x + xOff + 84, configList.y + yOff + height - 0.5, -1);
        Depth.render();
        int i = 0;
        for (String cfgName : configList.configs) {
            boolean hoveringName = (x >= xOff + configList.x) && (y >= yOff + configList.y + offsetY) && (x <= xOff + configList.x + 84) && (y <= yOff + configList.y + offsetY + 8.0F);
            TTFFontRenderer font = ExhibitionFonts.fsmallbold;
            int color = configList.selectedConfigID == i ? hud(op()) : hoveringName ? Colors.getColor(255, op()) : Colors.getColor(150, op());
            font.drawStringWithShadow(cfgName, (configList.x + 1.5F + xOff), (configList.y + offsetY + yOff), color);
            offsetY += 8.0F;
            i++;
        }
        Depth.post();
    }

    @Override
    public void configListMouseClicked(ConfigList configList, float x, float y, int button) {
        float xOff = configList.categoryPanel.categoryButton.panel.dragX;
        float yOff = configList.categoryPanel.categoryButton.panel.dragY;
        if (!configList.categoryPanel.visible)
            return;

        int height = 80;

        boolean hovering = (x >= xOff + configList.x) && (y >= yOff + configList.y) && (x <= xOff + configList.x + 84) && (y <= yOff + configList.y + height);
        float offsetY = 2 + (configList.amountScrolled);
        int i = 0;
        for (String ignored : configList.configs) {
            boolean hoveringName = hovering && (x >= xOff + configList.x) && (y >= yOff + configList.y + offsetY) && (x <= xOff + configList.x + 84) && (y <= yOff + configList.y + offsetY + 8.0F);
            offsetY += 8.0F;
            if (hoveringName) {
                configList.selectedConfigID = i;
                break;
            } else if (hovering) {
                configList.selectedConfigID = -1;
            }
            i++;
        }
    }

    @Override
    public void SliderContructor(Slider p0, CategoryPanel panel) {
        NumberProperty setting = p0.setting;
        double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        p0.dragX = p0.width * percent;
    }

    @Override
    public void SliderMouseMovedOrUp(Slider slider, int mouseX, int mouseY, int mouse, CategoryPanel panel) {
        if (mouse == 0) {
            NumberProperty setting = slider.setting;
            if (slider.dragging) {
                setting.setValue(valueAt(slider));
            }
            clampSetting(setting);
            slider.dragging = false;
        }
    }

    @Override
    public void SliderDraw(Slider slider, float x, float y, CategoryPanel panel) {
        if (panel.visible) {
            GlStateManager.pushMatrix();
            float xOff = panel.categoryButton.panel.dragX;
            float yOff = panel.categoryButton.panel.dragY;
            NumberProperty setting = slider.setting;
            float w = slider.width;
            double min = setting.getMin(), max = setting.getMax();

            double value = valueAt(slider);

            float sliderX = (float) (((setting.getValue() - min) / (max - min)) * w);
            RenderingUtil.rectangle(slider.x + xOff - 0.3, slider.y + yOff - 0.3, slider.x + xOff + w + 0.3, slider.y + yOff + 2.5 + 0.3, Colors.getColor(10, op()));
            RenderingUtil.drawGradient(slider.x + xOff, slider.y + yOff, slider.x + xOff + w, slider.y + yOff + 2.5, Colors.getColor(46, op()), Colors.getColor(27, op()));

            int fillTop = hud(op()), fillBottom = hud((int) (120 * (opacity.getOpacity() / 255F)));
            if (min < 0 && max > 0 && max == -min) {
                float centre = w / 2;
                if (sliderX >= centre) {
                    RenderingUtil.drawGradient(slider.x + xOff + centre, slider.y + yOff, slider.x + xOff + sliderX, slider.y + yOff + 2.5, fillTop, fillBottom);
                } else {
                    RenderingUtil.drawGradient(slider.x + xOff + sliderX, slider.y + yOff, slider.x + xOff + centre, slider.y + yOff + 2.5, fillTop, fillBottom);
                }
            } else {
                RenderingUtil.drawGradient(slider.x + xOff, slider.y + yOff, slider.x + xOff + sliderX, slider.y + yOff + 2.5, fillTop, fillBottom);
            }
            boolean hoverMinus = x >= panel.x + xOff + slider.x - 3 && y >= yOff + panel.y + slider.y && x <= xOff + panel.x + slider.x - 0.5 && y <= yOff + panel.y + slider.y + 2;
            boolean hoverPlus = x >= panel.x + xOff + slider.x + w + 0.5 && y >= yOff + panel.y + slider.y && x <= xOff + panel.x + slider.x + w + 3 && y <= yOff + panel.y + slider.y + 2;
            RenderingUtil.rectangle(slider.x + xOff - 2.5, slider.y + yOff + 1, slider.x + xOff - 1, slider.y + yOff + 1.5, Colors.getColor(hoverMinus ? 220 : 120, op()));
            RenderingUtil.rectangle(slider.x + xOff + w + 1, slider.y + yOff + 1, slider.x + xOff + w + 2.5, slider.y + yOff + 1.5, Colors.getColor(hoverPlus ? 220 : 120, op()));
            RenderingUtil.rectangle(slider.x + xOff + w + 1.5, slider.y + yOff + 0.5, slider.x + xOff + w + 2, slider.y + yOff + 2, Colors.getColor(hoverPlus ? 220 : 120, op()));

            String labelText = formatValue(setting, setting.getValue());
            float strWidth = ExhibitionFonts.fs.getWidth(labelText);
            ExhibitionFonts.fsmallbold.drawBorderedString(labelText, (slider.x + xOff + w + 4) - strWidth, (slider.y - 6 + yOff), Colors.getColor(220, op()), Colors.getColor(0, op()));
            ExhibitionFonts.fss.drawStringWithShadow(display(setting.getLabel()), (slider.x + xOff), (slider.y - 6 + yOff), Colors.getColor(185, op()));

            if (slider.dragging) {
                float divide = Math.abs((y - (slider.y + yOff + 2))) / 4;
                if (divide < 1) {
                    divide = 1;
                }

                double mouseDiff = (x - slider.lastDragX);

                slider.dragX = slider.dragX + (mouseDiff / divide);
                slider.lastDragX = x;

                setting.setValue(value);
            }
            clampSetting(setting);

            if ((x >= xOff + slider.x && y >= yOff + slider.y - 6 && x <= xOff + slider.x + w && y <= yOff + slider.y + 3)) {
                hover("Min: " + formatNumber(min) + " Max: " + formatNumber(max), Colors.getColor(255, op()));
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void SliderMouseClicked(Slider slider, int mouseX, int mouseY, int mouse, CategoryPanel panel) {
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        float w = slider.width;
        NumberProperty setting = slider.setting;
        if (panel.visible && mouseX >= panel.x + xOff + slider.x && mouseY >= yOff + panel.y + slider.y - 6 && mouseX <= xOff + panel.x + slider.x + w && mouseY <= yOff + panel.y + slider.y + 3.5F && mouse == 0) {
            slider.dragging = true;
            slider.lastDragX = mouseX;
            slider.dragX = (mouseX - (slider.x + xOff));
        }

        if (panel.visible && mouseX >= panel.x + xOff + slider.x - 3 && mouseY >= yOff + panel.y + slider.y && mouseX <= xOff + panel.x + slider.x - 0.5 && mouseY <= yOff + panel.y + slider.y + 2 && mouse == 0) {
            double value = setting.getValue();
            if (value - setting.getIncrement() >= setting.getMin()) {
                setting.setValue(MathUtils.getIncremental(value - setting.getIncrement(), setting.getIncrement()));
            } else {
                setting.setValue(setting.getMin());
            }
        } else
            if (panel.visible && mouseX >= panel.x + xOff + slider.x + w + 0.5 && mouseY >= yOff + panel.y + slider.y && mouseX <= xOff + panel.x + slider.x + w + 3 && mouseY <= yOff + panel.y + slider.y + 2 && mouse == 0) {
                double value = setting.getValue();
                if (value + setting.getIncrement() <= setting.getMax()) {
                    setting.setValue(MathUtils.getIncremental(value + setting.getIncrement(), setting.getIncrement()));
                } else {
                    setting.setValue(setting.getMax());
                }
            }
    }

    private double valueAt(Slider slider) {
        NumberProperty setting = slider.setting;
        final double percent = MathHelper.clamp_double(slider.dragX / slider.width, 0, 1);
        if (percent <= 0) return setting.getMin();
        if (percent >= 1) return setting.getMax();
        return MathUtils.getIncremental((percent * (setting.getMax() - setting.getMin())) + setting.getMin(), setting.getIncrement());
    }

    private void clampSetting(NumberProperty setting) {
        double value = setting.getValue();
        if (value < setting.getMin()) {
            setting.setValue(setting.getMin());
        } else if (value > setting.getMax()) {
            setting.setValue(setting.getMax());
        }
    }

    private String formatValue(NumberProperty setting, double value) {
        String labelText = formatNumber(value);
        String a = setting.getLabel().toLowerCase();
        if (a.contains("fov")) {
            labelText = labelText + "°";
        } else if ((a.contains("delay") || a.contains("switch")) && setting.getIncrement() != 1) {
            labelText = labelText + "ms";
        } else if ((a.contains("delay") && setting.getIncrement() == 1) || a.equals("existed")) {
            labelText += " tick";
        } else if (a.equals("range")) {
            labelText += "m";
        } else if ((a.equals("horizontal") || a.equals("vertical")) && setting.getMin() == -100 && setting.getMax() == 100) {
            labelText += "%";
        } else if (a.equals("health")) {
            labelText += "hp";
        }
        return labelText;
    }

    private static String formatNumber(double value) {
        if (MathUtils.isInteger(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(MathUtils.roundToPlace(value, 2));
    }

    @Override
    public void textBoxDraw(TextBox textBox, float x, float y) {
        CategoryPanel panel = textBox.panel;
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        if (textBox.cursorPos > textBox.textString.length()) {
            textBox.cursorPos = textBox.textString.length();
        } else if (textBox.cursorPos < 0) {
            textBox.cursorPos = 0;
        }
        if (!textBox.isFocused && !textBox.isTyping && !textBox.textString.equals(textBox.setting.getValue())) {
            textBox.textString = textBox.setting.getValue();
        }
        int selectedChar = textBox.cursorPos;
        boolean hovering = (x >= xOff + textBox.x) && (y >= yOff + textBox.y)
                && (x <= xOff + textBox.x + 84) && (y <= yOff + textBox.y + 9);

        RenderingUtil.rectangle(textBox.x + xOff - 0.3, textBox.y + yOff - 0.3, textBox.x + xOff + 84 + 0.3, textBox.y + yOff + 7.5F + 0.3, Colors.getColor(10, op()));
        RenderingUtil.drawGradient(textBox.x + xOff, textBox.y + yOff, textBox.x + xOff + 84, textBox.y + yOff + 7.5F, Colors.getColor(31, op()), Colors.getColor(36, op()));
        if (hovering || textBox.isFocused) {
            RenderingUtil.rectangleBordered(textBox.x + xOff, textBox.y + yOff, textBox.x + xOff + 84, textBox.y + yOff + 7.5F, 0.3, Colors.getColor(0, 0), textBox.isFocused ? Colors.getColor(130, op()) : Colors.getColor(90, op()));
        }
        ExhibitionFonts.fss.drawStringWithShadow(display(textBox.setting.getLabel()), (textBox.x + xOff + 1), (textBox.y - 6 + yOff), Colors.getColor(185, op()));

        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(textBox.x + xOff + 2, textBox.y + yOff, textBox.x + xOff + 82, textBox.y + yOff + 7.5F, Colors.getColor(90, op()));
        Depth.render();
        ExhibitionFonts.fss.drawString(textBox.textString, (textBox.x + 1.5F + xOff) - textBox.offset, (textBox.y + 2 + yOff), Colors.getColor(151, op()));
        Depth.post();

        if (textBox.opacity.getOpacity() >= 270) {
            textBox.backwards = true;
        } else if (textBox.opacity.getOpacity() <= 40) {
            textBox.backwards = false;
        }
        textBox.opacity.interp(textBox.backwards ? 40 : 270, 7);

        if (textBox.isFocused) {
            float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, selectedChar));
            float posX = textBox.x + xOff + width - textBox.offset;
            RenderingUtil.rectangle(posX - 0.5, textBox.y + yOff + 1.5, posX, textBox.y + yOff + 6, Colors.getColor(220, (int) textBox.opacity.getOpacity()));
        } else {
            textBox.opacity.setOpacity(255);
        }
    }

    @Override
    public void textBoxMouseClicked(TextBox textBox, int x, int y, int mouseID) {
        CategoryPanel panel = textBox.panel;
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        boolean hovering = (x >= xOff + textBox.x) && (y >= yOff + textBox.y) && (x <= xOff + textBox.x + 84) && (y <= yOff + textBox.y + 9);

        if (hovering && mouseID == 0 && !textBox.isFocused) {
            float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, textBox.cursorPos));
            float barOffset = (width - textBox.offset);
            if (barOffset < 0) {
                textBox.offset += barOffset;
            }

            if (barOffset > 82) {
                textBox.offset += (barOffset - 82);
            }

            textBox.isFocused = true;
            Keyboard.enableRepeatEvents(true);

            String currentString = textBox.textString;
            float mouseOffsetAdjusted = x - (xOff + textBox.x) - 2;
            textBox.cursorPos = ExhibitionFonts.fss.trimStringToWidth(currentString, textBox.offset + (mouseOffsetAdjusted), false).length();
        } else {
            if (!hovering) {
                textBox.isFocused = false;
                textBox.isTyping = false;
            } else if (mouseID == 0) {
                String currentString = textBox.textString;
                float mouseOffsetAdjusted = x - (xOff + textBox.x) - 2.5F;
                textBox.cursorPos = ExhibitionFonts.fss.trimStringToWidth(currentString, textBox.offset + (mouseOffsetAdjusted), false).length();
            }
        }
    }

    @Override
    public void textBoxKeyPressed(TextBox textBox, char letter, int key) {
        if (letter == '\r' || key == Keyboard.KEY_RETURN) {
            textBox.isFocused = false;
            textBox.isTyping = false;
            textBox.setting.setValue(textBox.textString);
            return;
        }

        if (textBox.isFocused) {
            if (GuiScreen.isKeyComboCtrlC(key)) {
                GuiScreen.setClipboardString(textBox.textString);
                return;
            }
            if (GuiScreen.isKeyComboCtrlV(key)) {
                String oldString = textBox.textString;

                StringBuilder stringBuilder = new StringBuilder(oldString);
                String input = ChatAllowedCharacters.filterAllowedCharacters(GuiScreen.getClipboardString());
                stringBuilder.insert(textBox.cursorPos, input);

                textBox.textString = ChatAllowedCharacters.filterAllowedCharacters(stringBuilder.toString());

                textBox.cursorPos += input.length();

                textBox.setting.setValue(textBox.textString);
                return;
            } else {
                if (key == Keyboard.KEY_LEFT) {
                    if (textBox.cursorPos > 0) {
                        if (GuiScreen.isShiftKeyDown())
                            textBox.cursorPos = 0;
                        else
                            textBox.cursorPos--;
                    }
                    float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, textBox.cursorPos));
                    float barOffset = (width - textBox.offset);
                    barOffset -= 2;
                    if (barOffset < 0) {
                        textBox.offset += barOffset;
                    }
                } else if (key == Keyboard.KEY_RIGHT) {
                    if (textBox.cursorPos < textBox.textString.length()) {
                        if (GuiScreen.isShiftKeyDown())
                            textBox.cursorPos = textBox.textString.length();
                        else
                            textBox.cursorPos++;
                    }
                    float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, textBox.cursorPos));
                    float barOffset = (width - textBox.offset);
                    if (barOffset > 82) {
                        textBox.offset += (barOffset - 82);
                    }
                } else if (key == Keyboard.KEY_DOWN) {
                    textBox.cursorPos = textBox.textString.length();
                    float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, textBox.cursorPos));
                    float barOffset = (width - textBox.offset);
                    if (barOffset > 82) {
                        textBox.offset += (barOffset - 82);
                    }
                } else if (key == Keyboard.KEY_UP) {
                    textBox.cursorPos = 0;
                    textBox.offset = 0;
                } else if (key == Keyboard.KEY_BACK && textBox.textString.length() > 0 && textBox.cursorPos > 0) {
                    String oldString = textBox.textString;

                    StringBuilder stringBuilder = new StringBuilder(oldString);
                    stringBuilder.deleteCharAt(textBox.cursorPos - 1);
                    textBox.textString = ChatAllowedCharacters.filterAllowedCharacters(stringBuilder.toString());
                    textBox.cursorPos--;
                    if (ExhibitionFonts.fss.getWidth(oldString) > 82 && textBox.offset > 0) {
                        float newTextWidth = ExhibitionFonts.fss.getWidth(textBox.textString);
                        float oldTextWidth = ExhibitionFonts.fss.getWidth(oldString);
                        float charWidth = newTextWidth - oldTextWidth;
                        if (newTextWidth <= 82 && oldTextWidth - 82 > charWidth) {
                            charWidth = 82 - oldTextWidth;
                        }

                        textBox.offset += charWidth;
                    }
                    if (textBox.cursorPos > textBox.textString.length()) {
                        textBox.cursorPos = textBox.textString.length();
                    }
                    textBox.setting.setValue(textBox.textString);
                }
            }
        }

        if (textBox.isFocused && ChatAllowedCharacters.isAllowedCharacter(letter)) {
            if (!Keyboard.areRepeatEventsEnabled())
                Keyboard.enableRepeatEvents(true);

            if (!textBox.isTyping)
                textBox.isTyping = true;

            String oldString = textBox.textString;

            StringBuilder stringBuilder = new StringBuilder(oldString);
            stringBuilder.insert(textBox.cursorPos, letter);

            textBox.textString = ChatAllowedCharacters.filterAllowedCharacters(stringBuilder.toString());

            if (textBox.cursorPos > textBox.textString.length()) {
                textBox.cursorPos = textBox.textString.length();
            } else if (textBox.cursorPos == oldString.length() && textBox.textString.startsWith(oldString)) {
                textBox.cursorPos += textBox.textString.length() - oldString.length();
            } else {
                textBox.cursorPos++;
                float width = ExhibitionFonts.fss.getWidth(textBox.textString.substring(0, textBox.cursorPos));
                float barOffset = (width - textBox.offset);
                if (barOffset > 82) {
                    textBox.offset += (barOffset - 82);
                }
            }
            TTFFontRenderer f = ExhibitionFonts.fss;
            float newTextWidth = f.getWidth(textBox.textString);
            float oldTextWidth = f.getWidth(oldString);
            if (newTextWidth > 82) {
                if (oldTextWidth < 82) {
                    oldTextWidth = 82;
                }
                float charWidth = (newTextWidth - oldTextWidth);
                if (textBox.cursorPos == textBox.textString.length())
                    textBox.offset += charWidth;
            }

            textBox.setting.setValue(textBox.textString);
        }
    }

    private int op() {
        return (int) opacity.getOpacity();
    }

    private int hud(int alpha) {
        return Colors.getColor(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), alpha);
    }

    private static String keyName(int key) {
        if (key == Keyboard.KEY_NONE || key < 0 || key >= Keyboard.KEYBOARD_SIZE) return "-";
        String name = Keyboard.getKeyName(key);
        return name != null ? name : "-";
    }

    private void hover(String text, int color) {
        hoverText = text;
        hoverColor = color;
    }

    private static String display(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.charAt(0) + name.toLowerCase().substring(1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int shortest(float[] columns) {
        int best = 0;
        for (int i = 1; i < columns.length; i++) {
            if (columns[i] < columns[best]) best = i;
        }
        return best;
    }

    private static List<Module> modulesIn(ModuleCategory category) {
        List<Module> modules = new ArrayList<>();
        for (Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (module.getCategory() == category) modules.add(module);
        }
        return modules;
    }

    private static List<Module> searchMatches(String query) {
        List<Module> modules = new ArrayList<>();
        for (Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (module.getLabel().toLowerCase().contains(query)) modules.add(module);
        }
        return modules;
    }

    private List<Property<?>> visibleSettings(Module module) {
        List<Property<?>> settings = new ArrayList<>();
        for (Property<?> property : module.getElements()) {
            if (!property.isAvailable() || ArrayListColorPickerUI.isHiddenProperty(property)) continue;
            if (isBoolean(property) || property instanceof NumberProperty || isChoice(property) || isText(property)) {
                settings.add(property);
            }
        }
        return settings;
    }

    private boolean isBoolean(Property<?> property) {
        return property.getValue() instanceof Boolean;
    }

    private boolean isChoice(Property<?> property) {
        return property instanceof ModeProperty || property instanceof SelectProperty || property instanceof MultiModeProperty;
    }

    private boolean isText(Property<?> property) {
        return property.getValue() instanceof String && !(property instanceof SelectProperty);
    }
}
