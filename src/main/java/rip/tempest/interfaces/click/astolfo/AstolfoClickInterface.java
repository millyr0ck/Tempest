package rip.tempest.interfaces.click.astolfo;

import rip.tempest.Tempest;
import rip.tempest.api.config.ConfigManager;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.MultiModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.interfaces.click.ColorPicker;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.misc.LocalConfigFetcher;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AstolfoClickInterface extends GuiScreen {
    private static final int PANEL_W = 107, HEADER_H = 17, MODULE_W = 103, MODULE_H = 15, ROW_W = 99, ROW_H = 10, SUB_W = 93;
    private static final int PANEL_STEP = 125;

    private static final int PANEL_BG = 0xFF1A1A1A;
    private static final int INNER_BG = 0xFF252525;
    private static final int OPEN_MODULE_BG = new Color(37, 37, 37).darker().getRGB();
    private static final int HOVER_OFF = 0xFF343434;
    private static final int TEXT_OFF = 0xFFCCCCCC;
    private static final int MAP_BG = 0xFF121212;
    private static final int BACKGROUND = new Color(1, 1, 1, 140).getRGB();
    private static final int CONFIG_BORDER = 0xFF808080;

    private static final Map<ModuleCategory, Integer> CATEGORY_COLOR = new EnumMap<>(ModuleCategory.class);
    private static final Map<ModuleCategory, String> CATEGORY_ICON = new EnumMap<>(ModuleCategory.class);

    static {
        CATEGORY_COLOR.put(ModuleCategory.COMBAT, -1618884);
        CATEGORY_COLOR.put(ModuleCategory.MOVEMENT, -13710223);
        CATEGORY_COLOR.put(ModuleCategory.PLAYER, -7453523);
        CATEGORY_COLOR.put(ModuleCategory.VISUALS, -13172530);
        CATEGORY_COLOR.put(ModuleCategory.CLIENT, -812014);
        CATEGORY_ICON.put(ModuleCategory.COMBAT, "combat");
        CATEGORY_ICON.put(ModuleCategory.MOVEMENT, "movement");
        CATEGORY_ICON.put(ModuleCategory.PLAYER, "player");
        CATEGORY_ICON.put(ModuleCategory.VISUALS, "visual");
        CATEGORY_ICON.put(ModuleCategory.CLIENT, "other");
    }

    private static final ResourceLocation EYE_OPEN = icon("eye_open"), EYE_CLOSED = icon("eye_close"),
            OPTIONS = icon("options"), FOLDER = icon("folder");

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("tempest/astolfo/" + name + ".png");
    }

    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");
    private static final SimpleDateFormat DATE = new SimpleDateFormat("MM/dd/yyyy");

    private static final Map<ModuleCategory, float[]> panelPos = new EnumMap<>(ModuleCategory.class);
    private static final Set<ModuleCategory> openPanels = new HashSet<>();
    private static final Map<ModuleCategory, Float> panelScroll = new EnumMap<>(ModuleCategory.class);
    private static final Set<String> openModules = new HashSet<>();
    private static final Set<Property<?>> openMulti = new HashSet<>();
    private static boolean configsOpen;
    private static boolean settingsOpen, themesOpen;
    private static float settingsScroll, themesScroll;
    private static String loadedConfig = "";
    private static boolean initialised;

    private CustomFontRenderer font;
    private ModuleCategory dragging;
    private float dragX, dragY;
    private NumberProperty slider;
    private float sliderX, sliderW;
    private Module binding;
    private Property<?> typing;
    private final Map<Property<?>, ColorPicker> pickers = new HashMap<>();
    private final Set<Property<?>> openPickers = new HashSet<>();
    private ColorPicker dragPicker;
    private Property<?> dragPickerProperty;
    private int dragPickerX, dragPickerY;
    private List<String> configs;
    private long folderClickAt;
    private String hoverDescription;

    private final List<Row> rows = new ArrayList<>();

    private static final class Row {
        final float x, y, w, h;
        final Click click;

        Row(float x, float y, float w, float h, Click click) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.click = click;
        }
    }

    private interface Click {
        void on(int button);
    }

    private static List<ModuleCategory> categories() {
        List<ModuleCategory> list = new ArrayList<>();
        for (ModuleCategory c : ModuleCategory.values()) if (CATEGORY_COLOR.containsKey(c)) list.add(c);
        return list;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        font = FontProcess.getStyledFont("lato-bold", 15, java.awt.Font.PLAIN);
        if (font == null) font = FontProcess.getFont("tempest");
        configs = null;
        if (!initialised) {
            int x = 3;
            for (ModuleCategory c : categories()) {
                panelPos.put(c, new float[]{x, 3});
                openPanels.add(c);
                x += PANEL_STEP;
            }
            initialised = true;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        binding = null;
        typing = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int color(ModuleCategory c) {
        return ClientSettingsModule.astolfoCategoryColors.getValue() ? CATEGORY_COLOR.get(c) : ColorProcess.getColor().getRGB();
    }

    private static int darker(int rgb) {
        return new Color(rgb).darker().getRGB();
    }

    private static void rect(double x1, double y1, double x2, double y2, int color) {
        if (x2 - x1 < 0.01 || y2 - y1 < 0.01) return;
        rectF((float) x1, (float) y1, (float) x2, (float) y2, color);
    }

    private float mid(float y, float h) {
        return y + (h - font.getHeight()) / 2f;
    }

    private static void rectF(float x1, float y1, float x2, float y2, int color) {
        net.minecraft.client.renderer.Tessellator t = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer w = t.getWorldRenderer();
        float a = (color >> 24 & 255) / 255f, r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        w.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        w.pos(x1, y2, 0).endVertex();
        w.pos(x2, y2, 0).endVertex();
        w.pos(x2, y1, 0).endVertex();
        w.pos(x1, y1, 0).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void icon(ResourceLocation r, float x, float y, int size, int tint) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
        mc.getTextureManager().bindTexture(r);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, size, size, size, size);
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        rows.clear();
        hoverDescription = null;
        updateDrags(mouseX, mouseY);

        rect(0, 0, width, height, BACKGROUND);

        for (ModuleCategory c : categories()) drawPanel(c, mouseX, mouseY);
        drawConfigs(mouseX, mouseY);
        drawSettingsDock(mouseX, mouseY);
        drawThemesDock(mouseX, mouseY);

        if (hoverDescription != null && !hoverDescription.isEmpty()) {
            font.drawString(hoverDescription, 3, height - 12, -1);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void updateDrags(int mouseX, int mouseY) {
        boolean down = Mouse.isButtonDown(0);
        if (dragging != null) {
            if (down) panelPos.put(dragging, new float[]{mouseX - dragX, mouseY - dragY});
            else dragging = null;
        }
        if (slider != null) {
            if (down) setSlider(slider, mouseX);
            else slider = null;
        }
        if (dragPicker != null) {
            if (down) {
                dragPicker.mouseDragged(dragPickerX, dragPickerY, mouseX, mouseY);
                setColor(dragPickerProperty, dragPicker.getColor());
            } else {
                dragPicker.mouseReleased();
                dragPicker = null;
            }
        }
    }

    private List<Module> modules(ModuleCategory c) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(c));
        list.removeIf(m -> m instanceof ClientSettingsModule);
        list.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return list;
    }

    private List<Property<?>> settings(Module m) {
        List<Property<?>> out = new ArrayList<>();
        for (Property<?> p : m.getElements()) if (p.isAvailable()) out.add(p);
        return out;
    }

    private void drawPanel(ModuleCategory c, int mouseX, int mouseY) {
        float[] pos = panelPos.get(c);
        float x = pos[0], y = pos[1];
        int color = color(c);
        boolean open = openPanels.contains(c);

        List<Module> modules = modules(c);
        int modulesH = 0;
        if (open) for (Module m : modules) modulesH += moduleHeight(m);
        int bodyH = open ? modulesH + 2 : 2;

        float maxShown = Math.max(60, height - y - 6 - HEADER_H);
        float scroll = Math.max(0, Math.min(Math.max(0, bodyH - maxShown), panelScroll.getOrDefault(c, 0f)));
        panelScroll.put(c, scroll);
        float shownH = Math.min(bodyH, maxShown);

        rectF(x - 0.5f, y - 0.5f, x + PANEL_W + 0.5f, y + HEADER_H + shownH + 0.5f, color);
        rect(x, y, x + PANEL_W, y + HEADER_H + shownH, PANEL_BG);
        if (shownH > 2) rect(x + 2, y + 17, x + PANEL_W - 2, y + HEADER_H + shownH - 2, INNER_BG);

        icon(open ? EYE_OPEN : EYE_CLOSED, x + PANEL_W - 21, y + 4, 8, open ? color : new Color(PANEL_BG).brighter().brighter().getRGB());
        icon(icon(CATEGORY_ICON.get(c)), x + PANEL_W - 11, y + 4, 8, color);
        font.drawStringWithShadow(c.getName().toLowerCase(), x + 4, mid(y, HEADER_H), -1);

        rows.add(new Row(x, y, PANEL_W, HEADER_H, b -> {
            if (b == 0) {
                dragging = c;
                dragX = mouseX - x;
                dragY = mouseY - y;
            } else if (b == 1) {
                if (!openPanels.remove(c)) openPanels.add(c);
            }
        }));

        if (!open) return;
        int scissorTop = (int) (y + HEADER_H), scissorH = (int) Math.max(0, shownH - 2);
        startScissor(x, scissorTop, PANEL_W, scissorH);
        float my = y + HEADER_H - scroll;
        for (Module m : modules) {
            my += drawModule(m, c, x + 2, my, color, mouseX, mouseY, scissorTop, scissorTop + scissorH);
        }
        endScissor();
    }

    private int moduleHeight(Module m) {
        if (!openModules.contains(m.getLabel())) return MODULE_H;
        int h = 18;
        for (Property<?> p : settings(m)) h += rowHeight(p);
        return h;
    }

    private int rowHeight(Property<?> p) {
        if (isColor(p)) return ROW_H + (openPickers.contains(p) ? ColorPicker.TOTAL_HEIGHT + 4 : 0);
        if (p instanceof MultiModeProperty && openMulti.contains(p)) return ROW_H + ((MultiModeProperty<?>) p).getValues().length * ROW_H + 3;
        if (p.getValue() instanceof String && !(p instanceof SelectProperty)) return ROW_H + 3;
        return ROW_H;
    }

    private static boolean isColor(Property<?> p) {
        return p instanceof NumberProperty && ((NumberProperty) p).getMin() <= Integer.MIN_VALUE && ((NumberProperty) p).getMax() >= Integer.MAX_VALUE;
    }

    private void row(float x, float y, float w, float h, float clipTop, float clipBottom, Click click) {
        float top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom > top) rows.add(new Row(x, top, w, bottom - top, click));
    }

    private boolean hovered(int mx, int my, float x, float y, float w, float h, float clipTop, float clipBottom) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h && my >= clipTop && my <= clipBottom;
    }

    private int drawModule(Module m, ModuleCategory c, float x, float y, int color, int mouseX, int mouseY, float clipTop, float clipBottom) {
        boolean open = openModules.contains(m.getLabel());
        int h = moduleHeight(m);
        if (y > clipBottom || y + h < clipTop) return h;
        boolean hover = hovered(mouseX, mouseY, x, y, MODULE_W, MODULE_H, clipTop, clipBottom);
        if (hover) hoverDescription = m.getDescription();

        if (m.isEnabled()) rect(x, y, x + MODULE_W, y + MODULE_H, hover ? darker(color) : color);
        else if (hovered(mouseX, mouseY + 1, x, y, MODULE_W, MODULE_H, clipTop, clipBottom)) rect(x, y, x + MODULE_W, y + MODULE_H, HOVER_OFF);
        if (open) rect(x, y, x + MODULE_W, y + h, OPEN_MODULE_BG);

        if (m.getKey() > 0 || binding == m) {
            String key = "[" + (binding == m ? "..." : Keyboard.getKeyName(m.getKey())) + "]";
            font.drawString(key, x + MODULE_W - font.getStringWidth(key) - 2, mid(y, MODULE_H), Color.GRAY.getRGB());
        }

        font.drawString(m.getLabel().toLowerCase(), x + 3, mid(y, MODULE_H), open && m.isEnabled() ? color : TEXT_OFF);

        List<Property<?>> props = settings(m);
        row(x, y, MODULE_W, MODULE_H, clipTop, clipBottom, b -> {
            if (b == 0) m.toggle();
            else if (b == 2 || b == 1 && isShiftKeyDown()) binding = binding == m ? null : m;
            else if (b == 1 && !props.isEmpty()) {
                if (!openModules.remove(m.getLabel())) openModules.add(m.getLabel());
            }
        });

        if (open) {
            float ry = y + MODULE_H;
            for (Property<?> p : props) {
                drawSetting(p, c, x + 2, ry, ROW_W, color, mouseX, mouseY, clipTop, clipBottom);
                ry += rowHeight(p);
            }
        }
        return h;
    }

    @SuppressWarnings("unchecked")
    private void drawSetting(Property<?> p, ModuleCategory c, float x, float y, int w, int color, int mouseX, int mouseY, float clipTop, float clipBottom) {
        if (y > clipBottom || y + rowHeight(p) < clipTop) return;
        String label = p.getLabel();

        if (isColor(p)) {
            NumberProperty np = (NumberProperty) p;
            font.drawString(label, x + 3, mid(y, ROW_H), -1);
            int argb = np.getValue().intValue() | 0xFF000000;
            rect(x + w - 16, y + 1, x + w - 2, y + ROW_H - 1, argb);
            row(x, y, w, ROW_H, clipTop, clipBottom, b -> {
                if (!openPickers.remove(p)) {
                    openPickers.add(p);
                    pickers.computeIfAbsent(p, k -> new ColorPicker()).setColor(np.getValue().intValue());
                }
            });
            if (openPickers.contains(p)) {
                ColorPicker picker = pickers.computeIfAbsent(p, k -> new ColorPicker());
                int px = (int) (x + (w - ColorPicker.WIDTH) / 2f), py = (int) (y + ROW_H + 2);
                picker.render(px, py, mouseX, mouseY);
                GlStateManager.enableTexture2D();
                row(px, py, ColorPicker.WIDTH, ColorPicker.TOTAL_HEIGHT, clipTop, clipBottom, b -> {
                    if (picker.mouseClicked(px, py, mouseX, mouseY, b)) {
                        dragPicker = picker;
                        dragPickerProperty = p;
                        dragPickerX = px;
                        dragPickerY = py;
                    }
                    setColor(p, picker.getColor());
                });
            }
        } else if (p instanceof NumberProperty) {
            NumberProperty np = (NumberProperty) p;
            double min = np.getMin(), max = np.getMax();
            int fill = (int) (max > min ? (np.getValue() - min) / (max - min) * w : 0);
            rect(x, y, x + fill, y + ROW_H, color);
            rect(x + Math.max(fill - 3, 0), y, x + fill, y + ROW_H, darker(color));
            font.drawString(label, x + 3, mid(y, ROW_H), -1);
            String value = NUMBER.format(np.getValue());
            font.drawString(value, x + w - font.getStringWidth(value), mid(y, ROW_H), TEXT_OFF);
            row(x, y, w, ROW_H, clipTop, clipBottom, b -> {
                if (b == 0) {
                    slider = np;
                    sliderX = x;
                    sliderW = w;
                    setSlider(np, mouseX);
                }
            });
        } else if (p instanceof MultiModeProperty) {
            MultiModeProperty<?> mp = (MultiModeProperty<?>) p;
            boolean open = openMulti.contains(p);
            if (open) rect(x, y, x + w, y + rowHeight(p), MAP_BG);
            String title = label + "...";
            font.drawString(title, x + w / 2f - font.getStringWidth(title) / 2f + 3, mid(y, ROW_H), -1);
            row(x, y, w, ROW_H, clipTop, clipBottom, b -> {
                if (!openMulti.remove(p)) openMulti.add(p);
            });
            if (open) {
                Object[] values = mp.getValues();
                for (int i = 0; i < values.length; i++) {
                    float oy = y + ROW_H + i * ROW_H;
                    final int index = i;
                    boolean on = mp.getValue().contains(values[i]);
                    if (on) rect(x + 3, oy, x + 3 + SUB_W, oy + ROW_H, color);
                    font.drawString(values[i].toString(), x + 6, mid(oy, ROW_H), -1);
                    row(x + 3, oy, SUB_W, ROW_H, clipTop, clipBottom, b -> mp.setValue(index));
                }
            }
        } else if (p instanceof ModeProperty || p instanceof SelectProperty) {
            String value = String.valueOf(p.getValue());
            font.drawString(label.replace("-", " "), x + 3, mid(y, ROW_H), -1);
            font.drawString(value, x + w - font.getStringWidth(value), mid(y, ROW_H), TEXT_OFF);
            row(x, y, w, ROW_H, clipTop, clipBottom, b -> step(p, b == 1 ? -1 : 1));
        } else if (p.getValue() instanceof Boolean) {
            if ((Boolean) p.getValue()) rect(x, y, x + w, y + ROW_H, color);
            font.drawString(label, x + 3, mid(y, ROW_H), -1);
            row(x, y, w, ROW_H, clipTop, clipBottom, b -> {
                if (b == 0) ((Property<Boolean>) p).setValue(!(Boolean) p.getValue());
            });
        } else if (p.getValue() instanceof String) {
            int sw = SUB_W;
            if (typing == p) {
                rect(x + 1, y, x + sw, y + 1, color);
                rect(x + 1, y + ROW_H + 2, x + sw, y + ROW_H + 3, color);
                rect(x + 1, y, x + 2, y + ROW_H + 3, color);
                rect(x + sw - 1, y, x + sw, y + ROW_H + 3, color);
            }
            rect(x + 3, y + ROW_H + 1, x + sw - 3, y + ROW_H + 2, color);
            font.drawString(p.getValue() + "...", x + 4, mid(y, ROW_H), -1);
            row(x, y, sw, ROW_H + 3, clipTop, clipBottom, b -> {
                if (b == 0) typing = typing == p ? null : p;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void step(Property<?> p, int direction) {
        if (p instanceof ModeProperty) {
            Object[] values = ((ModeProperty<?>) p).getValues();
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == p.getValue()) index = i;

            index += direction;
            if (index >= values.length || index < 0) index = 0;
            ((Property<Object>) p).setValueObj(values[index]);
        } else if (p instanceof SelectProperty) {
            SelectProperty sp = (SelectProperty) p;
            sp.onOpen();
            List<String> options = sp.getOptions();
            if (options.isEmpty()) return;
            int index = Math.max(0, options.indexOf(sp.getValue())) + direction;
            if (index >= options.size() || index < 0) index = 0;
            sp.setValue(options.get(index));
        }
    }

    private void setSlider(NumberProperty p, int mouseX) {
        double pct = Math.max(0, Math.min(1, (mouseX - sliderX) / (double) sliderW));
        double raw = p.getMin() + (p.getMax() - p.getMin()) * pct;
        double inc = p.getIncrement();
        if (inc > 0) raw = Math.round(raw / inc) * inc;
        p.setValue(Math.max(p.getMin(), Math.min(p.getMax(), raw)));
    }

    private static void setColor(Property<?> p, int argb) {
        if (p instanceof NumberProperty) ((NumberProperty) p).setValue((double) argb);
    }

    private void drawConfigs(int mouseX, int mouseY) {
        if (configs == null) {
            configs = LocalConfigFetcher.fetchConfigList();
            java.util.Collections.reverse(configs);
        }
        float x = width - PANEL_W - 0.5f, y = height - HEADER_H - 1;
        int listH = configsOpen ? configs.size() * MODULE_H : 0;

        rectF(x - 0.5f, y - listH - 0.5f, x + PANEL_W + 0.5f, y + HEADER_H + 0.5f, CONFIG_BORDER);
        rect(x, y - listH, x + PANEL_W, y + HEADER_H, PANEL_BG);
        if (listH > 0) rect(x + 2, y - listH + 2, x + PANEL_W - 2, y + HEADER_H - 17, INNER_BG);

        icon(OPTIONS, x + PANEL_W - 15, y + 2, 12, -1);
        float fx = x + PANEL_W - 11 - 14, fy = y + 4;
        boolean folderHover = mouseX >= fx && mouseX <= fx + 8 && mouseY >= fy && mouseY <= fy + 8;
        icon(FOLDER, fx, fy, 8, folderHover ? 0xFF7F7F7F : -1);
        font.drawStringWithShadow("configs", x + 4, mid(y, HEADER_H), -1);

        rows.add(new Row(x, y, PANEL_W, HEADER_H, b -> {
            if (b == 0 && mouseX >= fx && mouseX <= fx + 8 && mouseY >= fy && mouseY <= fy + 8) openFolder();
            else if (b == 1) {
                configsOpen = !configsOpen;
                configs = null;
            }
        }));

        if (!configsOpen) return;

        float ey = y - 30 + 17;
        for (String name : configs) {
            float entryY = ey;
            boolean current = name.equalsIgnoreCase(loadedConfig);
            if (current) rect(x + 2, entryY, x + 2 + MODULE_W, entryY + MODULE_H, PANEL_BG);
            font.drawString(name, x + 4, mid(entryY, MODULE_H), -1);
            File file = new File(ConfigManager.CONFIGS_DIR, name + ConfigManager.EXTENSION);
            String date = DATE.format(file.lastModified());
            float dw = font.getStringWidth(date);
            rect(x + 2 + MODULE_W - dw - 3, entryY + 3, x + 2 + MODULE_W, entryY + 12, current ? PANEL_BG : INNER_BG);
            font.drawString(date, x + 2 + MODULE_W - dw - 3, mid(entryY, MODULE_H), 0xFF7F7F7F);
            rows.add(new Row(x + 2, entryY, MODULE_W, MODULE_H, b -> {
                if (b == 0 && LocalConfigFetcher.loadConfig(name)) loadedConfig = name;
            }));
            ey -= MODULE_H;
        }
    }

    private float dockX(int slot) {
        return width - PANEL_W - 0.5f - slot * (PANEL_W + 4);
    }

    private float dockMaxBody() {
        return Math.max(40, height - HEADER_H - 1 - 24);
    }

    private float dockBody(float x, float y, String title, boolean open, float bodyH, int mouseX, int mouseY, Runnable toggle) {
        float shown = open ? Math.min(bodyH, dockMaxBody()) : 0;
        rectF(x - 0.5f, y - shown - 0.5f, x + PANEL_W + 0.5f, y + HEADER_H + 0.5f, CONFIG_BORDER);
        rect(x, y - shown, x + PANEL_W, y + HEADER_H, PANEL_BG);
        if (shown > 0) rect(x + 2, y - shown + 2, x + PANEL_W - 2, y, INNER_BG);
        icon(OPTIONS, x + PANEL_W - 15, y + 2, 12, -1);
        font.drawStringWithShadow(title, x + 4, mid(y, HEADER_H), -1);
        rows.add(new Row(x, y, PANEL_W, HEADER_H, b -> {
            if (b == 0 || b == 1) toggle.run();
        }));
        return shown;
    }

    private static List<Property<?>> clientSettings() {
        List<Property<?>> out = new ArrayList<>();
        Module m = Tempest.INSTANCE.getModuleManager().getModule(ClientSettingsModule.class);
        for (Property<?> p : m.getElements()) {
            if (p.isAvailable() && !themeProperty(p)) out.add(p);
        }
        return out;
    }

    private static boolean themeProperty(Property<?> p) {
        return p == ClientSettingsModule.theme || p == ClientSettingsModule.themeSpeed || p == ClientSettingsModule.themeLength
                || p == ClientSettingsModule.themeSaturation || p == ClientSettingsModule.themeDirection;
    }

    private static List<Property<?>> themeSettings() {
        List<Property<?>> out = new ArrayList<>();
        out.add(ClientSettingsModule.themeSpeed);
        out.add(ClientSettingsModule.themeLength);
        out.add(ClientSettingsModule.themeSaturation);
        out.add(ClientSettingsModule.themeDirection);
        if (rip.tempest.processes.Themes.current() == rip.tempest.processes.Themes.Preset.Custom) {
            out.add(ClientSettingsModule.pickerColor1);
            out.add(ClientSettingsModule.pickerColor2);
        }
        return out;
    }

    private void drawSettingsDock(int mouseX, int mouseY) {
        float x = dockX(1), y = height - HEADER_H - 1;
        List<Property<?>> props = clientSettings();
        float bodyH = 6;
        for (Property<?> p : props) bodyH += rowHeight(p);
        float shown = dockBody(x, y, "settings", settingsOpen, bodyH, mouseX, mouseY, () -> settingsOpen = !settingsOpen);
        if (shown <= 0) return;

        settingsScroll = Math.max(0, Math.min(bodyH - shown, settingsScroll));
        int color = color(ModuleCategory.CLIENT);
        float top = y - shown + 2, bottom = y;
        startScissor(x, top, PANEL_W, bottom - top);
        float ry = top + 2 - settingsScroll;
        for (Property<?> p : props) {
            drawSetting(p, ModuleCategory.CLIENT, x + 4, ry, ROW_W, color, mouseX, mouseY, top, bottom);
            ry += rowHeight(p);
        }
        endScissor();
    }

    private void drawThemesDock(int mouseX, int mouseY) {
        float x = dockX(2), y = height - HEADER_H - 1;
        rip.tempest.processes.Themes.Preset[] presets = rip.tempest.processes.Themes.Preset.values();
        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        rip.tempest.modules.impl.visuals.ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
        List<Property<?>> props = themeSettings();
        float bodyH = 4 + presets.length * MODULE_H + 4 + MODULE_H + patterns.length * MODULE_H + 4;
        for (Property<?> p : props) bodyH += rowHeight(p);
        float shown = dockBody(x, y, "themes", themesOpen, bodyH, mouseX, mouseY, () -> themesOpen = !themesOpen);
        if (shown <= 0) return;

        themesScroll = Math.max(0, Math.min(bodyH - shown, themesScroll));
        int color = color(ModuleCategory.CLIENT);
        float top = y - shown + 2, bottom = y;
        startScissor(x, top, PANEL_W, bottom - top);
        float ry = top - themesScroll;
        for (Property<?> p : props) {
            drawSetting(p, ModuleCategory.CLIENT, x + 4, ry, ROW_W, color, mouseX, mouseY, top, bottom);
            ry += rowHeight(p);
        }
        ry += 4;
        for (rip.tempest.processes.Themes.Preset preset : presets) {
            float py = ry;
            ry += MODULE_H;
            if (py > bottom || py + MODULE_H < top) continue;
            boolean inUse = rip.tempest.processes.Themes.active() && rip.tempest.processes.Themes.current() == preset;
            boolean hover = hovered(mouseX, mouseY, x + 2, py, MODULE_W, MODULE_H, top, bottom);
            if (inUse) rect(x + 2, py, x + 2 + MODULE_W, py + MODULE_H, PANEL_BG);
            else if (hover) rect(x + 2, py, x + 2 + MODULE_W, py + MODULE_H, HOVER_OFF);
            float sx = x + 2 + MODULE_W - 30, sy = py + 5, sw = 27, sh = 5;
            int steps = 9;
            for (int i = 0; i < steps; i++) {
                int c = rip.tempest.processes.Themes.sample(preset, i / (double) steps * 0.5);
                rectF(sx + sw * i / steps, sy, sx + sw * (i + 1) / steps + 0.3f, sy + sh, c | 0xFF000000);
            }
            font.drawString(preset.toString().toLowerCase(), x + 5, mid(py, MODULE_H), inUse ? rip.tempest.processes.Themes.sample(preset, 0) | 0xFF000000 : TEXT_OFF);
            row(x + 2, py, MODULE_W, MODULE_H, top, bottom, b -> {
                if (b == 0) rip.tempest.processes.Themes.select(preset);
            });
        }
        if (list != null) {
            ry += 4;
            if (ry <= bottom && ry + MODULE_H >= top) font.drawString("patterns", x + 5, mid(ry, MODULE_H), 0xFF7F7F7F);
            ry += MODULE_H;
            double t = list.patternTime();
            for (rip.tempest.utils.render.ArrayPattern pattern : patterns) {
                float py = ry;
                ry += MODULE_H;
                if (py > bottom || py + MODULE_H < top) continue;
                boolean chosen = list.pattern.getValue() == pattern;
                boolean hover = hovered(mouseX, mouseY, x + 2, py, MODULE_W, MODULE_H, top, bottom);
                if (chosen) rect(x + 2, py, x + 2 + MODULE_W, py + MODULE_H, PANEL_BG);
                else if (hover) rect(x + 2, py, x + 2 + MODULE_W, py + MODULE_H, HOVER_OFF);
                if (pattern != rip.tempest.utils.render.ArrayPattern.Off) {
                    float sx = x + 2 + MODULE_W - 30, sy = py + 4, sw = 27, sh = 7;
                    for (float bx = 0; bx < sw; bx += 3) {
                        for (float by = 0; by < sh; by += 3) {
                            int c = pattern.color(bx + 1.5, by + 1.5, sw, sh, t, list.motion());
                            rectF(sx + bx, sy + by, sx + Math.min(sw, bx + 3), sy + Math.min(sh, by + 3), c | 0xFF000000);
                        }
                    }
                }
                String label = pattern == rip.tempest.utils.render.ArrayPattern.Off ? "none" : pattern.toString().toLowerCase();
                font.drawString(label, x + 5, mid(py, MODULE_H), chosen ? color : TEXT_OFF);
                row(x + 2, py, MODULE_W, MODULE_H, top, bottom, b -> {
                    if (b == 0) list.pattern.setValue(pattern);
                });
            }
        }
        endScissor();
    }

    private boolean scrollDock(int mx, int my, int wheel) {
        float y = height - HEADER_H - 1;
        float step = -Math.signum(wheel) * MODULE_H * 2;
        if (settingsOpen && mx >= dockX(1) && mx <= dockX(1) + PANEL_W && my < y) {
            settingsScroll = Math.max(0, settingsScroll + step);
            return true;
        }
        if (themesOpen && mx >= dockX(2) && mx <= dockX(2) + PANEL_W && my < y) {
            themesScroll = Math.max(0, themesScroll + step);
            return true;
        }
        return false;
    }

    private void openFolder() {
        if (System.currentTimeMillis() - folderClickAt < 1500) return;
        folderClickAt = System.currentTimeMillis();
        File dir = ConfigManager.CONFIGS_DIR.getAbsoluteFile();
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
            } catch (Exception ignored) {
            }
        }, "open-configs").start();
    }

    private void startScissor(float x, float y, float w, float h) {
        int scale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glScissor((int) (x * scale), (int) (mc.displayHeight - (y + h) * scale), (int) (w * scale), (int) (h * scale));
    }

    private void endScissor() {
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        typing = null;
        Row target = null;
        for (Row r : rows) {
            if (mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h) target = r;
        }

        if (target != null) {
            if (binding != null && target.click != null) binding = null;
            target.click.on(button);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = null;
        slider = null;
        if (dragPicker != null) {
            dragPicker.mouseReleased();
            dragPicker = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (scrollDock(mx, my, wheel)) return;
        for (ModuleCategory c : categories()) {
            float[] pos = panelPos.get(c);
            if (mx >= pos[0] && mx <= pos[0] + PANEL_W && my >= pos[1]) {
                panelScroll.put(c, Math.max(0, panelScroll.getOrDefault(c, 0f) - Math.signum(wheel) * MODULE_H * 2));
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean press = keyCode > 0;
        if (binding != null) {
            if (!press) return;
            binding.setKey(keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK ? 0 : keyCode);
            binding = null;
            return;
        }
        if (typing != null) {
            String value = String.valueOf(typing.getValue());
            if (press) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) typing = null;
                else if (keyCode == Keyboard.KEY_BACK && !value.isEmpty()) typing.setValueObj(value.substring(0, value.length() - 1));
                else if (isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
                    String clip = GuiScreen.getClipboardString();
                    if (clip != null) typing.setValueObj(value + clip);
                }
            } else if (typedChar != 0 && ChatAllowedCharacters.isAllowedCharacter(typedChar) && !isCtrlKeyDown()) {
                typing.setValueObj(value + typedChar);
            }
            return;
        }
        if (!press) return;
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClientSettingsModule.menuKey.getValue().intValue()) {
            mc.displayGuiScreen(mc.theWorld == null ? new rip.tempest.interfaces.menu.main.CustomMainMenu() : null);
        }
    }
}
