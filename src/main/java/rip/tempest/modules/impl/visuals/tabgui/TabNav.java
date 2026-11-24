package rip.tempest.modules.impl.visuals.tabgui;

import rip.tempest.Tempest;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TabNav {
    public int depth, category, module, setting;
    public boolean editing;
    private long lastFrame;

    public float delta;

    public void frame() {
        long now = System.currentTimeMillis();
        delta = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        clamp();
    }

    public float ease(float current, float target, float speed) {
        if (delta <= 0f) return target;
        return current + (target - current) * (1f - (float) Math.exp(-delta * speed));
    }

    public static List<ModuleCategory> categories() {
        List<ModuleCategory> list = new ArrayList<>();
        for (ModuleCategory c : ModuleCategory.values()) {
            if (!Tempest.INSTANCE.getModuleManager().getModulesForCategory(c).isEmpty()) list.add(c);
        }
        return list;
    }

    public static List<Module> modules(ModuleCategory category) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(category));
        list.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return list;
    }

    public static List<Property<?>> settings(Module module) {
        List<Property<?>> list = new ArrayList<>();
        for (Property<?> p : module.getElements()) {
            if (!p.isAvailable()) continue;
            boolean number = p instanceof NumberProperty n && !(n.getMin() <= Integer.MIN_VALUE && n.getMax() >= Integer.MAX_VALUE);
            if (p instanceof ModeProperty || number || p.getValue() instanceof Boolean) list.add(p);
        }
        return list;
    }

    public ModuleCategory selectedCategory() {
        List<ModuleCategory> cats = categories();
        return cats.isEmpty() ? null : cats.get(Math.floorMod(category, cats.size()));
    }

    public List<Module> currentModules() {
        ModuleCategory c = selectedCategory();
        return c == null ? List.of() : modules(c);
    }

    public Module selectedModule() {
        List<Module> mods = currentModules();
        return mods.isEmpty() ? null : mods.get(Math.floorMod(module, mods.size()));
    }

    public List<Property<?>> currentSettings() {
        Module m = selectedModule();
        return m == null ? List.of() : settings(m);
    }

    public Property<?> selectedSetting() {
        List<Property<?>> s = currentSettings();
        return s.isEmpty() ? null : s.get(Math.floorMod(setting, s.size()));
    }

    public static String value(Property<?> p) {
        if (p.getValue() instanceof Boolean b) return b ? "On" : "Off";
        if (p instanceof NumberProperty n) {
            double v = n.getValue();
            return n.getIncrement() >= 1 ? Long.toString(Math.round(v)) : String.valueOf(Math.round(v * 100.0) / 100.0);
        }
        return String.valueOf(p.getValue());
    }

    private void clamp() {
        List<ModuleCategory> cats = categories();
        if (cats.isEmpty()) {
            depth = 0;
            return;
        }
        category = Math.floorMod(category, cats.size());
        if (depth >= 1) {
            int n = currentModules().size();
            if (n == 0) depth = 0;
            else module = Math.floorMod(module, n);
        }
        if (depth >= 2) {
            int n = currentSettings().size();
            if (n == 0) {
                depth = 1;
                editing = false;
            } else {
                setting = Math.floorMod(setting, n);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public boolean key(int key) {
        clamp();
        boolean up = key == Keyboard.KEY_UP, down = key == Keyboard.KEY_DOWN, left = key == Keyboard.KEY_LEFT,
                right = key == Keyboard.KEY_RIGHT, enter = key == Keyboard.KEY_RETURN;
        if (!(up || down || left || right || enter)) return false;

        if (editing) {
            Property<?> p = selectedSetting();
            if (left || right) step(p, right ? 1 : -1);
            else editing = false;
            return true;
        }

        switch (depth) {
            case 0 -> {
                if (up) category--;
                else if (down) category++;
                else if ((right || enter) && !currentModules().isEmpty()) {
                    depth = 1;
                    module = 0;
                }
            }
            case 1 -> {
                if (up) module--;
                else if (down) module++;
                else if (left) depth = 0;
                else if (enter) {
                    Module m = selectedModule();
                    if (m != null) m.toggle();
                } else if (right && !currentSettings().isEmpty()) {
                    depth = 2;
                    setting = 0;
                }
            }
            default -> {
                if (up) setting--;
                else if (down) setting++;
                else if (left) depth = 1;
                else {
                    Property<?> p = selectedSetting();
                    if (p != null && p.getValue() instanceof Boolean b) ((Property<Boolean>) p).setValue(!b);
                    else if (p != null) editing = true;
                }
            }
        }
        clamp();
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void step(Property<?> p, int direction) {
        if (p instanceof NumberProperty n) {
            double next = n.getValue() + n.getIncrement() * direction;
            n.setValue(Math.max(n.getMin(), Math.min(n.getMax(), next)));
        } else if (p instanceof ModeProperty<?> mode) {
            Object[] values = mode.getValues();
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == mode.getValue()) index = i;
            mode.setValue(Math.floorMod(index + direction, values.length));
        } else if (p != null && p.getValue() instanceof Boolean b) {
            ((Property<Boolean>) p).setValue(!b);
        }
    }
}
