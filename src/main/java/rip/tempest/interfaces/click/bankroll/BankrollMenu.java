package rip.tempest.interfaces.click.bankroll;

import rip.tempest.Tempest;
import rip.tempest.api.config.ConfigManager;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.MultiModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.processes.Themes;
import rip.tempest.utils.client.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BankrollMenu {
    private static final String[] TAB_NAMES = {"combat", "movement", "player", "visuals", "client", "themes", "config"};
    private static final ModuleCategory[] TAB_CATEGORIES = {
            ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.PLAYER, ModuleCategory.VISUALS, ModuleCategory.CLIENT, null, null
    };
    private static final int THEMES_TAB = 5, CONFIG_TAB = 6;

    private static final int COLUMNS = 3;
    private static final float MARGIN = 10, COL_W = 215, GAP = 10, TOP = 34;
    static final float WINDOW_W = MARGIN * 2 + COL_W * COLUMNS + GAP * (COLUMNS - 1), WINDOW_H = 680;
    private static final float BOTTOM = WINDOW_H - 33;

    private static final float CLIP_TOP = 26, CLIP_BOTTOM = WINDOW_H - 24;

    private static final float SCROLL_STEP = 60;

    private static final float TRACK_X0 = WINDOW_W - 8, TRACK_X1 = WINDOW_W - 4;

    private static final float SEARCH_W = 150;

    private static final int SEARCH_PAGE = TAB_NAMES.length;

    private int tab;
    private String query = "";
    private int shownPage = -1;
    private boolean justOpened = true;

    private final float[] scroll = new float[TAB_NAMES.length + 1];
    private float scrollGrab;

    private final Map<Module, Integer> columns = new HashMap<>();

    private Module listening;
    private boolean listeningMenuKey;

    private String[] configs = new String[0];
    private int configSelected;
    private String configName = "";

    private final Map<Property<?>, float[]> colorCache = new HashMap<>();
    private final BankrollThemesPage themesPage = new BankrollThemesPage();

    void onOpen() {
        columns.clear();
        stopListening();
        refreshConfigs();
        themesPage.onOpen();

        justOpened = true;
    }

    boolean listening() {
        return listening != null || listeningMenuKey;
    }

    void stopListening() {
        listening = null;
        listeningMenuKey = false;
    }

    void bind(int key) {
        boolean clear = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE;
        if (listening != null) {
            listening.setKey(clear ? Keyboard.KEY_NONE : key);
        } else if (listeningMenuKey) {
            ClientSettingsModule.menuKey.setValue((double) (clear ? Keyboard.KEY_RSHIFT : key));
        }
        stopListening();
    }

    private static float columnX(int column) {
        return MARGIN + column * (COL_W + GAP);
    }

    void draw(BankrollUi ui) {
        boolean themed = Themes.active() && ClientSettingsModule.themeMenu.getValue();
        ui.setMenuColor((themed ? Themes.color() : ClientSettingsModule.bankrollAccent.getValue().intValue()) | 0xFF000000);
        if (ui.clicked()) stopListening();
        if (justOpened) {
            justOpened = false;
            ui.stopTyping(false);
            ui.resetAnimation("menu#open", 0);
            ui.resetAnimation("menu#page", 0);
        }

        float open = ui.animate("menu#open", 1, 14);
        ui.setAlpha(open);

        ui.setWindowSize(WINDOW_W, WINDOW_H);
        ui.placeWindowOnce((float) Math.floor((ui.displayWidth() - WINDOW_W) / 2), (float) Math.floor((ui.displayHeight() - WINDOW_H) / 2));
        ui.beginWindow();
        float wx = ui.windowX(), wy = ui.windowY();
        ui.drawWindowFrame(wx, wy, Tempest.NAME.toLowerCase(Locale.ROOT));

        float searchX0 = wx + WINDOW_W - 8 - SEARCH_W;
        String typed = ui.searchField("##search", query, "search", searchX0, wy + 4, wx + WINDOW_W - 8, wy + 19, 40);
        if (!typed.equals(query)) {
            query = typed;
            columns.clear();
        }

        if (!query.isEmpty() && ui.clearButton("##searchclear", searchX0 - 17, wy + 4, searchX0 - 2, wy + 19)) {
            if (ui.typingIn("##search")) ui.clearTyped();
            query = "";
            columns.clear();
        }
        boolean searching = !query.trim().isEmpty();
        int page = searching ? SEARCH_PAGE : tab;
        if (page != shownPage) {
            shownPage = page;
            ui.resetAnimation("menu#page", 0);
        }

        float shown = ui.animate("menu#page", 1, 12);
        ui.setAlpha(open * shown);
        float rise = Math.round((1 - shown) * 10);
        ModuleCategory category = TAB_CATEGORIES[tab];
        if (searching) {
            drawModules(ui, wx, wy, matches(query), page, rise);
        } else if (tab == THEMES_TAB) {
            ui.pushClip(wx + 2, wy + CLIP_TOP, wx + WINDOW_W - 2, wy + CLIP_BOTTOM);
            themesPage.draw(ui, wx + MARGIN, wy + TOP + rise, this);
            ui.popClip();
        } else if (category == null) {
            drawConfig(ui, wx, wy, rise);
        } else {
            drawModules(ui, wx, wy, modulesIn(category), page, rise);
        }
        ui.setAlpha(open);

        int active = searching ? -1 : tab;
        int picked = ui.bottomTabs(wx, wy, TAB_NAMES, active);
        if (picked != active) {
            tab = picked;
            query = "";
            columns.clear();
            if (tab == CONFIG_TAB) refreshConfigs();
        }
        ui.endWindow();
    }

    private static List<Module> modulesIn(ModuleCategory category) {
        List<Module> modules = new ArrayList<>();
        for (Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (module.getCategory() == category) modules.add(module);
        }
        return modules;
    }

    private static List<Module> matches(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT), compact = q.replace(" ", "");
        List<Module> modules = new ArrayList<>();
        for (Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
            String name = module.getLabel().toLowerCase(Locale.ROOT);
            if (name.contains(q) || name.replace(" ", "").contains(compact)) modules.add(module);
        }
        return modules;
    }

    private void drawModules(BankrollUi ui, float wx, float wy, List<Module> modules, int page, float rise) {
        float[] heights = new float[modules.size()];
        float[] bottoms = new float[COLUMNS];
        for (int i = 0; i < modules.size(); i++) {
            heights[i] = boxHeight(modules.get(i));

            int column = columns.computeIfAbsent(modules.get(i), m -> shortest(bottoms));
            bottoms[column] += heights[i] + GAP;
        }
        float content = 0;
        for (float bottom : bottoms) content = Math.max(content, bottom);
        content -= GAP;
        float view = BOTTOM - TOP;
        float maxScroll = Math.max(0, content - view);
        scroll[page] = Math.max(0, Math.min(maxScroll, scroll[page]));
        String scrollKey = "menu#scroll" + page;

        float scrolled = (float) Math.floor(Math.max(0, Math.min(maxScroll, ui.animate(scrollKey, scroll[page], 16))));

        ui.pushClip(wx + 2, wy + CLIP_TOP, wx + WINDOW_W - 2, wy + CLIP_BOTTOM);
        if (modules.isEmpty()) {
            String none = "no modules found";
            ui.textAtBaseline((float) Math.floor(wx + (WINDOW_W - ui.textWidth(none)) / 2), wy + TOP + 20 + rise, BankrollUi.TEXT, none);
        }
        float[] y = new float[COLUMNS];
        java.util.Arrays.fill(y, TOP - scrolled + rise);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            int column = columns.get(module);
            float bx = wx + columnX(column), by = wy + y[column];
            y[column] += heights[i] + GAP;
            if (by + heights[i] <= wy + CLIP_TOP || by >= wy + CLIP_BOTTOM) continue;
            drawModule(ui, module, bx, by, heights[i]);
        }

        if (maxScroll > 0) {
            float tx0 = wx + TRACK_X0, ty0 = wy + TOP, tx1 = wx + TRACK_X1, ty1 = wy + BOTTOM;
            float trackH = ty1 - ty0;
            float len = Math.max(16, (float) Math.floor(trackH * view / content));
            float off = (float) Math.floor((trackH - len) * scrolled / maxScroll);
            if (ui.clicked() && ui.mouseIn(tx0 - 1, ty0, tx1 + 1, ty1)) {
                float my = ui.mouseY();
                scrollGrab = my >= ty0 + off && my < ty0 + off + len ? my - (ty0 + off) : len / 2;
            }
            if (ui.dragArea("##tab_scroll", tx0 - 1, ty0, tx1 + 1, ty1)) {
                float t = (ui.mouseY() - ty0 - scrollGrab) / (trackH - len);
                scroll[page] = (float) Math.floor(Math.max(0, Math.min(1, t)) * maxScroll);
                ui.resetAnimation(scrollKey, scroll[page]);
                off = (float) Math.floor((trackH - len) * scroll[page] / maxScroll);
            }
            ui.fillRect(tx0, ty0, tx1, ty1, BankrollUi.rgb(23, 23, 23));
            ui.fillRect(tx0, ty0 + off, tx1, ty0 + off + len, BankrollUi.rgb(36, 36, 36));
        }
        ui.popClip();

        float wheel = ui.takeWheel();
        if (wheel != 0 && !ui.popupOpen() && ui.mouseIn(wx, wy, wx + WINDOW_W, wy + WINDOW_H)) {
            scroll[page] = Math.max(0, Math.min(maxScroll, scroll[page] - wheel * SCROLL_STEP));
        }
    }

    private static int shortest(float[] bottoms) {
        int best = 0;
        for (int i = 1; i < bottoms.length; i++) {
            if (bottoms[i] < bottoms[best]) best = i;
        }
        return best;
    }

    private float boxHeight(Module module) {
        float h = 25 + 20;
        for (Property<?> property : module.getElements()) {
            if (property.isAvailable()) h += rowHeight(property);
        }
        return h + 1;
    }

    private static float rowHeight(Property<?> property) {
        if (property instanceof NumberProperty number) return isColor(number) ? 20 : 35;
        if (property instanceof ModeProperty || property instanceof MultiModeProperty || property instanceof SelectProperty) return 14 + 34;
        if (property.getValue() instanceof Boolean) return 20;
        if (property.getValue() instanceof String) return 14 + 30;
        return 0;
    }

    private void drawModule(BankrollUi ui, Module module, float x, float y, float h) {
        ui.beginGroupBox(lower(module.getLabel()), x, y, COL_W, h, false);
        boolean enabled = ui.checkbox("enabled", module.isEnabled(), 0);
        if (enabled != module.isEnabled()) module.toggle();
        if (ui.keybindLabel("##key", listening == module ? "[...]" : "[" + keyName(module.getKey()) + "]")) {
            listening = module;
            listeningMenuKey = false;
        }
        int index = 0;
        for (Property<?> property : module.getElements()) {
            if (!property.isAvailable()) continue;
            ui.pushId(index++);
            drawProperty(ui, property);
            ui.popId();
        }
        ui.endGroupBox();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drawProperty(BankrollUi ui, Property<?> property) {
        String label = lower(property.getLabel());
        if (property instanceof NumberProperty number) {
            if (isColor(number)) {
                ui.rowLabel(label);
                float[] rgba = colorOf(number);
                if (ui.colorSwatch("##color", rgba, 0)) number.setValue((double) BankrollUi.toColor(rgba, false));
            } else {
                double value = number.getValue();
                double picked = ui.slider(label, value, number.getMin(), number.getMax(), number.getIncrement(),
                        v -> snap(number, v), v -> format(number, v));
                if (picked != value) number.setValue(picked);
            }
        } else if (property instanceof ModeProperty mode) {
            ui.label(label);
            Enum<?>[] values = (Enum<?>[]) mode.getValues();
            String[] names = new String[values.length];
            for (int i = 0; i < values.length; i++) names[i] = display(values[i].toString());
            int current = ((Enum<?>) mode.getValue()).ordinal();
            int picked = ui.combo("##mode", current, names);
            if (picked != current) mode.setValue(picked);
        } else if (property instanceof MultiModeProperty multi) {
            ui.label(label);
            Enum<?>[] values = (Enum<?>[]) multi.getValues();
            String[] names = new String[values.length];
            boolean[] selected = new boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                names[i] = display(values[i].toString());
                selected[i] = multi.isSelected((Enum) values[i]);
            }
            int toggled = ui.multiCombo("##multi", selected, names);
            if (toggled >= 0) multi.setValue(toggled);
        } else if (property instanceof SelectProperty select) {
            ui.label(label);
            List<String> options = select.getOptions();
            String[] names = new String[options.size()];
            for (int i = 0; i < names.length; i++) names[i] = lower(options.get(i));
            int current = options.indexOf(select.getValue());
            ui.onNextComboOpen(select::onOpen);
            int picked = ui.combo("##select", current, names);
            if (picked != current && picked >= 0 && picked < options.size()) select.setValue(options.get(picked));
        } else if (property.getValue() instanceof Boolean on) {
            boolean picked = ui.checkbox(label, on, 0);
            if (picked != on) ((Property<Boolean>) property).setValue(picked);
        } else if (property.getValue() instanceof String text) {
            ui.label(label);
            String typed = ui.textField("##text", text, "...", 256);
            if (!typed.equals(text)) ((Property<String>) property).setValue(typed);
        }
    }

    private static boolean isColor(NumberProperty property) {
        return property.getMin() == Integer.MIN_VALUE && property.getMax() == Integer.MAX_VALUE;
    }

    float[] colorOf(NumberProperty property) {
        int argb = (int) property.getValue().longValue();
        float[] rgba = colorCache.get(property);
        if (rgba == null || BankrollUi.toColor(rgba, false) != argb) {
            rgba = new float[]{(argb >> 16 & 255) / 255.0f, (argb >> 8 & 255) / 255.0f, (argb & 255) / 255.0f, (argb >>> 24) / 255.0f};
            colorCache.put(property, rgba);
        }
        return rgba;
    }

    static double snap(NumberProperty property, double value) {
        double increment = property.getIncrement();
        if (increment > 0) {
            value = Math.round(value / increment) * increment;
            value = BigDecimal.valueOf(value).setScale(decimals(increment), RoundingMode.HALF_UP).doubleValue();
        }
        return Math.max(property.getMin(), Math.min(property.getMax(), value));
    }

    private static int decimals(double increment) {
        if (increment <= 0) return 2;
        return Math.max(0, Math.min(4, BigDecimal.valueOf(increment).stripTrailingZeros().scale()));
    }

    static String format(NumberProperty property, double value) {
        FormatCache cache = FORMAT_CACHE.get(property);
        if (cache != null && cache.value == value) return cache.text;
        String text = java.math.BigDecimal.valueOf(value).setScale(decimals(property.getIncrement()), java.math.RoundingMode.HALF_UP).toPlainString();
        String label = property.getLabel().toLowerCase(Locale.ROOT);
        if (label.contains("fov") || label.contains("field of view")) text += "°";
        if (cache == null) FORMAT_CACHE.put(property, cache = new FormatCache());
        cache.value = value;
        cache.text = text;
        return text;
    }

    private static final class FormatCache {
        double value;
        String text;
    }

    private static final java.util.Map<NumberProperty, FormatCache> FORMAT_CACHE = new java.util.IdentityHashMap<>();

    private void drawConfig(BankrollUi ui, float wx, float wy, float rise) {
        ui.pushClip(wx + 2, wy + CLIP_TOP, wx + WINDOW_W - 2, wy + CLIP_BOTTOM);
        ui.beginGroupBox("menu", wx + columnX(0), wy + TOP + rise, COL_W, BOTTOM - TOP, false);
        ui.rowLabel("toggle menu");
        String key = listeningMenuKey ? "[...]" : "[" + keyName(ClientSettingsModule.menuKey.getValue().intValue()) + "]";
        if (ui.keybindLabel("##menu_key", key)) {
            listeningMenuKey = true;
            listening = null;
        }

        if (!Themes.active() || !ClientSettingsModule.themeMenu.getValue()) {
            ui.rowLabel("menu color");
            float[] rgba = colorOf(ClientSettingsModule.bankrollAccent);
            if (ui.colorSwatch("##menu_color", rgba, 0)) ClientSettingsModule.bankrollAccent.setValue((double) BankrollUi.toColor(rgba, false));
        }
        ui.endGroupBox();

        ui.beginGroupBox("configs", wx + columnX(1), wy + TOP + rise, COL_W, BOTTOM - TOP, false);

        configSelected = ui.listBox("##configs", configSelected, configs, 20, false);
        configName = ui.textField("##config_name", configName, "...", 31);
        ConfigManager manager = Tempest.INSTANCE.getConfigManager();
        if (ui.button("create")) {
            String name = configName.trim();
            if (!name.isEmpty() && manager.saveConfig(name)) {
                Logger.chatPrint("Config '" + name + "' has been created.");
                refreshConfigs();
                configSelected = Arrays.asList(configs).indexOf(name);
            }
        }
        if (ui.button("save") && selectedConfig() != null) {
            String name = selectedConfig();
            if (manager.saveConfig(name)) Logger.chatPrint("Config '" + name + "' has been saved.");
            refreshConfigs();
        }
        if (ui.button("load") && selectedConfig() != null) {
            String name = selectedConfig();
            if (manager.loadConfig(name)) Logger.chatPrint("Config '" + name + "' has been loaded.");
        }
        if (ui.button("open directory")) {
            try {
                if (!ConfigManager.CONFIGS_DIR.exists()) ConfigManager.CONFIGS_DIR.mkdirs();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(ConfigManager.CONFIGS_DIR);
            } catch (Exception e) {
                Logger.chatPrint("Couldn't open the configs folder: " + e.getMessage());
            }
        }
        ui.endGroupBox();
        ui.popClip();
    }

    private String selectedConfig() {
        return configSelected >= 0 && configSelected < configs.length ? configs[configSelected] : null;
    }

    private void refreshConfigs() {
        String selected = selectedConfig();
        File[] files = ConfigManager.CONFIGS_DIR.listFiles();
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.toLowerCase(Locale.ROOT).endsWith(ConfigManager.EXTENSION)) {
                    names.add(name.substring(0, name.length() - ConfigManager.EXTENSION.length()));
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        configs = names.toArray(new String[0]);
        int index = selected != null ? names.indexOf(selected) : -1;
        configSelected = index >= 0 ? index : (configs.length > 0 ? 0 : -1);
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    static String display(String name) {
        if (name == null) return "";

        String cached = DISPLAY_CACHE.get(name);
        if (cached != null) return cached;
        String shown = name;
        if (shown.indexOf(' ') < 0) shown = CAMEL_SPLIT.matcher(shown).replaceAll(" ");
        shown = shown.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (DISPLAY_CACHE.size() > 4096) DISPLAY_CACHE.clear();
        DISPLAY_CACHE.put(name, shown);
        return shown;
    }

    private static final java.util.regex.Pattern CAMEL_SPLIT = java.util.regex.Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final java.util.Map<String, String> DISPLAY_CACHE = new java.util.HashMap<>();

    static String keyName(int key) {
        if (key <= 0 || key >= Keyboard.KEYBOARD_SIZE) return "none";
        String name = Keyboard.getKeyName(key);
        if (name == null || name.isEmpty()) return "none";
        return switch (name) {
            case "INSERT" -> "INS";
            case "DELETE" -> "DEL";
            case "ESCAPE" -> "ESC";
            case "RETURN" -> "ENTER";
            case "BACK" -> "BKSP";
            case "PRIOR" -> "PGUP";
            case "NEXT" -> "PGDN";
            case "LCONTROL" -> "LCTRL";
            case "RCONTROL" -> "RCTRL";
            case "LMENU" -> "LALT";
            case "RMENU" -> "RALT";
            case "CAPITAL" -> "CAPS";
            default -> name;
        };
    }
}
