package rip.tempest.interfaces.click.flat;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.MultiModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.interfaces.click.ColorClipboard;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

public abstract class FlatGui extends GuiScreen {
    protected static final Color PANEL = new Color(18, 19, 24, 250);
    protected static final Color DEEP = new Color(14, 15, 19, 255);
    protected static final Color RAISED = new Color(23, 24, 30, 255);
    protected static final Color FIELD = new Color(25, 26, 32, 255);
    protected static final Color EDGE = new Color(36, 38, 45, 255);
    protected static final Color EDGE_2 = new Color(42, 44, 52, 255);
    protected static final Color OFF_BOX = new Color(58, 60, 69, 255);
    protected static final Color TRACK = new Color(36, 38, 45, 255);
    protected static final int WHITE = 0xFFFFFF, TEXT = 0xE9EAEE, TEXT_2 = 0xC9CCD3, MUTED = 0x9DA1AB, FAINT = 0x8A8E99;

    protected static final float FAST = 22f, MEDIUM = 14f, SLOW = 9f;
    protected static final float CHECK = 5;

    protected final Map<Object, Float> anims = new HashMap<>();
    protected final Set<Property<?>> openColors = new HashSet<>();
    protected final Set<Property<?>> openLists = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<float[]> clips = new ArrayDeque<>();
    private long lastFrame;
    protected float delta;
    protected boolean closing;
    protected int mouseX, mouseY;

    protected boolean searchFocused;
    protected Module binding;
    protected Property<?> editing;
    protected String editBuffer = "";

    private NumberProperty dragSlider;
    private float dragSliderX, dragSliderW;

    private String hoverText;
    private Object hoverKey;
    private long hoverSince;

    private static final class Hit {
        final float x, y, w, h;
        final IntConsumer action;

        Hit(float x, float y, float w, float h, IntConsumer action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    protected abstract String search();

    protected abstract void setSearch(String text);

    protected abstract void drawGui(float a, Color accent);

    protected abstract void scrolled(int mx, int my, int direction);

    protected boolean editOwn(UnaryOperator<String> change) {
        return false;
    }

    protected void submitOwn() {
    }

    protected boolean ownFocused() {
        return false;
    }

    protected void unfocusOwn() {
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        closing = false;
        lastFrame = 0L;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        binding = null;
        if (editing != null) commitEdit();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        long now = System.currentTimeMillis();
        delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        mouseX = mx;
        mouseY = my;
        hits.clear();
        hoverText = null;
        updateDrags();

        float open = anim("open", closing ? 0f : 1f, closing ? 18f : 11f);
        if (closing && open < 0.02f) {
            mc.displayGuiScreen(mc.theWorld == null ? new rip.tempest.interfaces.menu.main.CustomMainMenu() : null);
            return;
        }
        float a = easeOut(open);
        if (mc.theWorld == null) drawDefaultBackground();
        Glyphs.bar(0, 0, width, height, new Color(0, 0, 0, Math.round(85 * a)));

        drawGui(a, accent());
        drawColorPopup(a);
        drawTooltip(a);

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    protected float anim(Object key, float target, float speed) {
        Float current = anims.get(key);
        if (current == null) {
            anims.put(key, target);
            return target;
        }
        float next = current + (target - current) * (1f - (float) Math.exp(-delta * speed));
        if (Math.abs(next - target) < 0.0005f) next = target;
        anims.put(key, next);
        return next;
    }

    protected static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    protected static float easeOut(float t) {
        float u = 1f - clamp(t);
        return 1f - u * u * u;
    }

    protected static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * clamp(a)));
    }

    protected static Color mix(Color a, Color b, float t) {
        t = clamp(t);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    protected static int mixRgb(int a, int b, float t) {
        return mix(new Color(a), new Color(b), t).getRGB() & 0xFFFFFF;
    }

    protected Color accent() {
        Color c = ColorProcess.getColor();
        return c == null ? new Color(236, 184, 239) : c;
    }

    protected static int rgb(Color c) {
        return c.getRGB() & 0xFFFFFF;
    }

    protected boolean caretOn() {
        return System.currentTimeMillis() % 1000 < 520;
    }

    protected static CustomFontRenderer heavy(float size) {
        return font("manropeextrabold", size);
    }

    protected static CustomFontRenderer bold(float size) {
        return font("manropebold", size);
    }

    protected static CustomFontRenderer regular(float size) {
        return font("manrope", size);
    }

    private static CustomFontRenderer font(String file, float size) {
        CustomFontRenderer f = null;
        try {
            f = FontProcess.getStyledFont(file, size, Font.PLAIN);
        } catch (Exception ignored) {
        }
        return f != null ? f : FontProcess.getFont("tempest");
    }

    protected void pushClip(float x, float y, float w, float h) {
        float[] r = {x, y, x + w, y + h};
        if (!clips.isEmpty()) {
            float[] p = clips.peek();
            r = new float[]{Math.max(r[0], p[0]), Math.max(r[1], p[1]), Math.min(r[2], p[2]), Math.min(r[3], p[3])};
        }
        clips.push(r);
        applyClip(r);
    }

    protected void popClip() {
        clips.pop();
        if (clips.isEmpty()) RenderUtils.endScissor();
        else applyClip(clips.peek());
    }

    private static void applyClip(float[] r) {
        RenderUtils.startScissor(r[0], r[1], Math.max(0, r[2] - r[0]), Math.max(0, r[3] - r[1]));
    }

    protected boolean over(float x, float y, float w, float h) {
        if (closing || mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) return false;
        if (clips.isEmpty()) return true;
        float[] c = clips.peek();
        return mouseX >= c[0] && mouseX <= c[2] && mouseY >= c[1] && mouseY <= c[3];
    }

    protected void hit(float x, float y, float w, float h, IntConsumer action) {
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (!clips.isEmpty()) {
            float[] c = clips.peek();
            x0 = Math.max(x0, c[0]);
            y0 = Math.max(y0, c[1]);
            x1 = Math.min(x1, c[2]);
            y1 = Math.min(y1, c[3]);
        }
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, action));
    }

    protected void tooltip(Object key, String text) {
        if (text == null || text.isEmpty()) return;
        if (key != hoverKey) {
            hoverKey = key;
            hoverSince = System.currentTimeMillis();
        }
        hoverText = text;
    }

    protected static void fill(float x, float y, float w, float h, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, h, c);
    }

    protected static void frame(float x, float y, float w, float h, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, 0.5f, c);
        Glyphs.bar(x, y + h - 0.5f, w, 0.5f, c);
        Glyphs.bar(x, y + 0.5f, 0.5f, h - 1, c);
        Glyphs.bar(x + w - 0.5f, y + 0.5f, 0.5f, h - 1, c);
    }

    protected static void checkbox(float x, float y, float on, float a, Color accent, boolean hover) {
        float t = easeOut(on);
        frame(x, y, CHECK, CHECK, alpha(hover ? new Color(90, 93, 104) : OFF_BOX, a * (1 - t)));
        if (t > 0.01f) {
            float inset = (CHECK / 2f) * (1 - t);
            fill(x + inset, y + inset, CHECK - inset * 2, CHECK - inset * 2, alpha(accent, a));
        }
    }

    protected void string(CustomFontRenderer f, String s, float x, float y, int rgb, float a) {
        if (a < 0.03f || s == null || s.isEmpty()) return;
        f.drawString(s, x, y, (Math.max(5, Math.round(255 * clamp(a))) << 24) | (rgb & 0xFFFFFF));
    }

    protected void stringMid(CustomFontRenderer f, String s, float x, float y, float h, int rgb, float a) {
        string(f, s, x, y + (h - f.getHeight()) / 2f, rgb, a);
    }

    protected static String trim(CustomFontRenderer f, String s, float width) {
        if (s == null) return "";
        if (f.getStringWidth(s) <= width) return s;
        String t = s;
        while (t.length() > 1 && f.getStringWidth(t + "...") > width) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    protected static String keyName(Module m) {
        return m.getKey() > 0 ? "[" + Keyboard.getKeyName(m.getKey()) + "]" : "";
    }

    protected void searchBox(float x, float y, float w, float h, float a, Color accent) {
        boolean hover = over(x, y, w, h);
        float focus = anim("searchF", searchFocused ? 1f : 0f, FAST);
        fill(x, y, w, h, alpha(DEEP, a));
        frame(x, y, w, h, alpha(mix(hover ? new Color(58, 60, 69) : EDGE_2, accent, focus), a));
        float my = y + h / 2f;
        Glyphs.ring(x + 6.5f, my - 0.5f, 2.4f, 0.9f, 0f, 360f, alpha(new Color(MUTED), a));
        Glyphs.stroke(x + 8.2f, my + 1.2f, x + 9.6f, my + 2.6f, 0.9f, alpha(new Color(MUTED), a));
        String text = search();
        float tx = x + 13;
        CustomFontRenderer f = bold(12);
        if (text.isEmpty() && !searchFocused) {
            stringMid(f, "search", tx, y, h, FAINT, a);
            String hint = "Ctrl F";
            stringMid(f, hint, x + w - 5 - f.getStringWidth(hint), y, h, FAINT, a * 0.8f);
        } else {
            String shown = trim(f, text, w - 26);
            stringMid(f, shown, tx, y, h, TEXT, a);
            if (searchFocused && caretOn()) fill(tx + f.getStringWidth(shown) + 0.5f, y + 3.5f, 0.5f, h - 7, alpha(new Color(TEXT), a));
        }
        hit(x, y, w, h, b -> searchFocused = true);
        if (!text.isEmpty()) {
            float cx = x + w - 6, cy = my;
            Color c = alpha(new Color(over(cx - 4, cy - 4, 8, 8) ? TEXT : MUTED), a);
            Glyphs.stroke(cx - 1.8f, cy - 1.8f, cx + 1.8f, cy + 1.8f, 0.9f, c);
            Glyphs.stroke(cx - 1.8f, cy + 1.8f, cx + 1.8f, cy - 1.8f, 0.9f, c);
            hit(cx - 4, cy - 4, 8, 8, b -> setSearch(""));
        }
    }

    protected static List<Module> modules(ModuleCategory category) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(category));
        list.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return list;
    }

    protected static boolean matches(Module m, String query) {
        String q = query.toLowerCase().replace(" ", "");
        return m.getLabel().toLowerCase().replace(" ", "").contains(q);
    }

    protected static List<Property<?>> settings(Module m) {
        List<Property<?>> out = new ArrayList<>();
        for (Property<?> p : m.getElements()) if (p.isAvailable()) out.add(p);
        return out;
    }

    protected void startBinding(Module m) {
        binding = binding == m ? null : m;
        searchFocused = false;
    }

    protected static List<Property<?>> themeSettings() {
        List<Property<?>> props = new ArrayList<>();
        props.add(ClientSettingsModule.themeSpeed);
        props.add(ClientSettingsModule.themeLength);
        props.add(ClientSettingsModule.themeSaturation);
        props.add(ClientSettingsModule.themeDirection);
        if (rip.tempest.processes.Themes.current() == rip.tempest.processes.Themes.Preset.Custom) {
            props.add(ClientSettingsModule.pickerColor1);
            props.add(ClientSettingsModule.pickerColor2);
        }
        return props;
    }

    protected static boolean themeInUse(rip.tempest.processes.Themes.Preset preset) {
        return rip.tempest.processes.Themes.active() && rip.tempest.processes.Themes.current() == preset;
    }

    protected static void themeStrip(rip.tempest.processes.Themes.Preset preset, float x, float y, float w, float h, float a) {
        int steps = 10;
        for (int s = 0; s < steps; s++) {
            Color c0 = new Color(rip.tempest.processes.Themes.sample(preset, s / (double) steps * 0.5));
            Color c1 = new Color(rip.tempest.processes.Themes.sample(preset, (s + 1) / (double) steps * 0.5));
            Glyphs.gradient(x + w * s / steps, y, w / steps + 0.3f, h, alpha(c0, a), alpha(c1, a));
        }
    }

    protected static boolean isColor(Property<?> p) {
        return p instanceof NumberProperty && ((NumberProperty) p).getMin() <= Integer.MIN_VALUE && ((NumberProperty) p).getMax() >= Integer.MAX_VALUE;
    }

    protected static boolean isList(Property<?> p) {
        return p instanceof ModeProperty || p instanceof MultiModeProperty || p instanceof SelectProperty;
    }

    protected float rowHeight(Property<?> p) {
        if (isColor(p)) return 11;
        if (p instanceof NumberProperty) return 17;
        if (isList(p)) {
            float open = easeOut(anims.getOrDefault("list:" + System.identityHashCode(p), 0f));
            return 23 + (options(p).size() * 11 + 3) * open;
        }
        if (p.getValue() instanceof String) return 23;
        return 11;
    }

    protected float drawSetting(Property<?> p, float x, float y, float w, float a, Color accent) {
        float h = rowHeight(p);
        if (isColor(p)) drawColor((NumberProperty) p, x, y, w, a);
        else if (p instanceof NumberProperty) drawSlider((NumberProperty) p, x, y, w, a, accent);
        else if (isList(p)) drawCombo(p, x, y, w, a, accent);
        else if (p.getValue() instanceof Boolean) drawBool(p, x, y, w, a, accent);
        else if (p.getValue() instanceof String) drawText(p, x, y, w, a, accent);
        return h;
    }

    @SuppressWarnings("unchecked")
    private void drawBool(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean value = (Boolean) p.getValue();
        boolean hover = over(x - 2, y - 1, w + 4, 13);
        float on = anim(p, value ? 1f : 0f, MEDIUM);
        CustomFontRenderer f = bold(12.5f);
        stringMid(f, trim(f, p.getLabel(), w - 12), x, y, 11, mixRgb(MUTED, TEXT, Math.max(on, hover ? 0.6f : 0f)), a);
        checkbox(x + w - CHECK, y + (11 - CHECK) / 2f, on, a, accent, hover);
        hit(x - 2, y - 1, w + 4, 13, b -> ((Property<Boolean>) p).setValue(!value));
    }

    private void drawSlider(NumberProperty p, float x, float y, float w, float a, Color accent) {
        double min = p.getMin(), max = p.getMax(), value = p.getValue();
        float pct = max > min ? (float) ((value - min) / (max - min)) : 0f;
        boolean dragging = dragSlider == p;
        float shown = dragging ? pct : anim(p, pct, FAST);
        if (dragging) anims.put(p, pct);
        boolean hover = over(x - 2, y + 9, w + 4, 8) || dragging;

        CustomFontRenderer f = bold(12.5f);
        String v = format(value, p.getIncrement());
        stringMid(f, trim(f, p.getLabel(), w - f.getStringWidth(v) - 6), x, y, 9, hover ? TEXT : TEXT_2, a);
        stringMid(f, v, x + w - f.getStringWidth(v), y, 9, dragging ? rgb(accent) : TEXT, a);

        float by = y + 12, bh = 2;
        fill(x, by, w, bh, alpha(TRACK, a));
        if (shown > 0.001f) fill(x, by, w * shown, bh, alpha(accent, a));
        float kx = Math.max(x, Math.min(x + w - 1.5f, x + w * shown - 0.75f));
        fill(kx, by - (hover ? 1.5f : 1f), 1.5f, bh + (hover ? 3f : 2f), alpha(new Color(TEXT), a));
        hit(x - 2, y + 9, w + 4, 8, b -> {
            dragSlider = p;
            dragSliderX = x;
            dragSliderW = w;
            setSlider(p, (mouseX - x) / w);
        });
    }

    private void setSlider(NumberProperty p, float pct) {
        double raw = p.getMin() + (p.getMax() - p.getMin()) * clamp(pct);
        double inc = p.getIncrement();
        if (inc > 0) raw = Math.round(raw / inc) * inc;
        p.setValue(Math.max(p.getMin(), Math.min(p.getMax(), raw)));
    }

    private static String format(double value, double increment) {
        int places = 0;
        if (increment > 0 && increment < 1) places = Math.min(3, BigDecimal.valueOf(increment).stripTrailingZeros().scale());
        return new BigDecimal(value).setScale(Math.max(0, places), RoundingMode.HALF_UP).toPlainString();
    }

    protected List<String> options(Property<?> p) {
        List<String> out = new ArrayList<>();
        if (p instanceof ModeProperty) for (Object v : ((ModeProperty<?>) p).getValues()) out.add(v.toString());
        else if (p instanceof MultiModeProperty) for (Object v : ((MultiModeProperty<?>) p).getValues()) out.add(v.toString());
        else if (p instanceof SelectProperty) {
            out.addAll(((SelectProperty) p).getOptions());
            if (out.isEmpty()) out.add("(none)");
        }
        return out;
    }

    private boolean selected(Property<?> p, String option) {
        if (p instanceof MultiModeProperty) {
            MultiModeProperty<?> mp = (MultiModeProperty<?>) p;
            for (Object v : mp.getValues()) if (v.toString().equals(option)) return mp.getValue().contains(v);
            return false;
        }
        return option.equals(String.valueOf(p.getValue()));
    }

    private String comboValue(Property<?> p) {
        if (p instanceof MultiModeProperty) {
            List<String> chosen = new ArrayList<>();
            for (String o : options(p)) if (selected(p, o)) chosen.add(o);
            return chosen.isEmpty() ? "None" : String.join(", ", chosen);
        }
        String v = String.valueOf(p.getValue());
        return v.isEmpty() || "null".equals(v) ? "Select" : v;
    }

    @SuppressWarnings("unchecked")
    private void pick(Property<?> p, String option) {
        if (p instanceof MultiModeProperty) {
            MultiModeProperty<?> mp = (MultiModeProperty<?>) p;
            Object[] values = mp.getValues();
            for (int i = 0; i < values.length; i++) if (values[i].toString().equals(option)) mp.setValue(i);
        } else if (p instanceof ModeProperty) {
            for (Object v : ((ModeProperty<?>) p).getValues()) if (v.toString().equals(option)) ((Property<Object>) p).setValueObj(v);
        } else if (p instanceof SelectProperty && !"(none)".equals(option)) {
            ((SelectProperty) p).setValue(option);
        }
    }

    private void drawCombo(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean isOpen = openLists.contains(p);
        float open = anim("list:" + System.identityHashCode(p), isOpen ? 1f : 0f, MEDIUM);
        CustomFontRenderer label = bold(11.5f), f = bold(12.5f);
        stringMid(label, trim(label, p.getLabel(), w), x, y, 8, MUTED, a);

        float cy = y + 10, ch = 13;
        boolean hover = over(x, cy, w, ch);
        float hv = anim("comboH:" + System.identityHashCode(p), hover || isOpen ? 1f : 0f, FAST);
        fill(x, cy, w, ch, alpha(FIELD, a));
        frame(x, cy, w, ch, alpha(mix(EDGE_2, new Color(66, 68, 78), hv), a));
        stringMid(f, trim(f, comboValue(p), w - 14), x + 4.5f, cy, ch, TEXT, a);
        Glyphs.chevron(x + w - 5.5f, cy + ch / 2f, 1.8f, 0.9f, open > 0.5f, alpha(new Color(MUTED), a));
        hit(x, cy, w, ch, b -> {
            if (!openLists.remove(p)) {
                openLists.add(p);
                if (p instanceof SelectProperty) ((SelectProperty) p).onOpen();
            }
        });

        if (open > 0.002f) {
            List<String> opts = options(p);
            float full = opts.size() * 11 + 2, ly = cy + ch + 1;
            pushClip(x, ly, w, (full + 1) * easeOut(open));
            float oa = a * clamp(open * 1.5f - 0.3f);
            fill(x, ly, w, full, alpha(RAISED, a));
            frame(x, ly, w, full, alpha(EDGE_2, a));
            float oy = ly + 1;
            for (String o : opts) {
                boolean sel = selected(p, o);
                boolean oh = over(x + 1, oy, w - 2, 11);
                if (oh || sel) fill(x + 1, oy, w - 2, 11, alpha(new Color(30, 31, 38), oa));
                stringMid(f, trim(f, o, w - 14), x + 4, oy, 11, sel ? rgb(accent) : mixRgb(TEXT_2, TEXT, oh ? 1 : 0), oa);
                if (sel) Glyphs.check(x + w - 5.5f, oy + 5.5f, 2.2f, 0.9f, 1f, alpha(accent, oa));
                final String option = o;

                hit(x + 1, oy, w - 2, 11, b -> pick(p, option));
                oy += 11;
            }
            popClip();
        }
    }

    private void drawText(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean isEditing = editing == p;
        CustomFontRenderer label = bold(11.5f), f = bold(12.5f);
        stringMid(label, trim(label, p.getLabel(), w), x, y, 8, MUTED, a);
        float fy = y + 10, fh = 13;
        boolean hover = over(x, fy, w, fh);
        fill(x, fy, w, fh, alpha(isEditing || hover ? FIELD : DEEP, a));
        frame(x, fy, w, fh, alpha(isEditing ? accent : EDGE_2, a));
        String shown = trim(f, isEditing ? editBuffer : String.valueOf(p.getValue()), w - 9);
        stringMid(f, shown, x + 4.5f, fy, fh, TEXT, a);
        if (isEditing && caretOn()) fill(x + 5 + f.getStringWidth(shown), fy + 3, 0.5f, fh - 6, alpha(new Color(TEXT), a));
        hit(x, fy, w, fh, b -> {
            editing = p;
            editBuffer = String.valueOf(p.getValue());
            searchFocused = false;
        });
    }

    private void drawColor(NumberProperty p, float x, float y, float w, float a) {
        boolean isOpen = popColor == p && openColors.contains(p);
        int argb = p.getValue().intValue();
        boolean hover = over(x - 2, y - 1, w + 4, 13);
        CustomFontRenderer f = bold(12.5f);
        stringMid(f, trim(f, p.getLabel(), w - 22), x, y, 11, hover || isOpen ? TEXT : TEXT_2, a);
        float sw = 16, sh = 8, sx = x + w - sw, sy = y + (11 - sh) / 2f;
        fill(sx, sy, sw, sh, alpha(new Color(argb & 0xFFFFFF), a));
        frame(sx - 0.5f, sy - 0.5f, sw + 1, sh + 1, alpha(new Color(255, 255, 255, isOpen ? 150 : hover ? 80 : 40), a));
        if (isOpen) {
            popAnchorX = x + w + 10;
            popAnchorY = y - 4;
            popRowLeft = x - 10;
        }
        final float ax = x + w + 10, ay = y - 4, rl = x - 10;
        hit(x - 2, y - 1, w + 4, 13, b -> {
            if (popColor == p && openColors.contains(p)) {
                closePopup();
                return;
            }
            openColors.clear();
            openColors.add(p);
            popColor = p;
            popAnchorX = ax;
            popAnchorY = ay;
            popRowLeft = rl;
            int c = p.getValue().intValue();
            float[] hsb = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
            popHue = hsb[0];
            popSat = hsb[1];
            popVal = hsb[2];
            popAlpha = (c >>> 24) == 0 ? 1f : (c >>> 24) / 255f;
            anims.put("colorpop", 0f);
        });
    }

    private static final float PICK_PAD = 6, PICK_SV = 147, PICK_GAP = 7, PICK_HUE = 14, PICK_ALPHA = 8, PICK_ROW = 14;
    private static final float PICK_W = PICK_PAD + PICK_SV + PICK_GAP + PICK_HUE + PICK_PAD;
    private static final float PICK_H = PICK_PAD + PICK_SV + PICK_GAP + PICK_ALPHA + 6 + PICK_ROW + PICK_PAD;

    private NumberProperty popColor;
    private float popAnchorX, popAnchorY, popRowLeft;
    private float popHue, popSat = 1, popVal = 1, popAlpha = 1;
    private float popX, popY;
    private int popDrag;

    private void closePopup() {
        if (popColor != null) openColors.remove(popColor);
        popColor = null;
        popDrag = 0;
    }

    private int popArgb() {
        return Math.round(clamp(popAlpha) * 255) << 24 | (Color.HSBtoRGB(popHue, popSat, popVal) & 0xFFFFFF);
    }

    private void applyPopup() {
        if (popColor != null) setColor(popColor, popArgb());
    }

    private void dragPopup() {
        float sx = popX + PICK_PAD, sy = popY + PICK_PAD;
        if (popDrag == 1) {
            popSat = clamp((mouseX - sx) / PICK_SV);
            popVal = 1f - clamp((mouseY - sy) / PICK_SV);
        } else if (popDrag == 2) {
            popHue = Math.min(0.999f, clamp((mouseY - sy) / PICK_SV));
        } else if (popDrag == 3) {
            popAlpha = clamp((mouseX - sx) / PICK_SV);
        }
        if (popDrag != 0) applyPopup();
    }

    private static void gradQuad(float x0, float y0, float x1, float y1, int tl, int tr, int br, int bl, float a) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        vertex(x0, y0, tl, a);
        vertex(x1, y0, tr, a);
        vertex(x1, y1, br, a);
        vertex(x0, y1, bl, a);
        GL11.glEnd();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.color(0f, 0f, 0f, 0f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void vertex(float x, float y, int argb, float a) {
        GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f * a);
        GL11.glVertex2f(x, y);
    }

    private void drawColorPopup(float a) {
        if (popColor != null && !openColors.contains(popColor)) closePopup();
        if (popColor == null) return;
        float t = easeOut(anim("colorpop", 1f, FAST)) * a;

        float px = popAnchorX, py = popAnchorY;
        if (px + PICK_W > width - 4) px = popRowLeft - PICK_W;
        px = Math.max(4, Math.min(px, width - PICK_W - 4));
        py = Math.max(4, Math.min(py, height - PICK_H - 4));
        popX = px;
        popY = py;

        hit(0, 0, width, height, b -> closePopup());
        hit(px, py, PICK_W, PICK_H, b -> {
        });

        fill(px - 1, py - 1, PICK_W + 2, PICK_H + 2, alpha(new Color(0, 0, 0, 90), t));
        fill(px, py, PICK_W, PICK_H, alpha(new Color(17, 17, 20, 255), t));
        frame(px, py, PICK_W, PICK_H, alpha(EDGE_2, t));

        float sx0 = px + PICK_PAD, sy0 = py + PICK_PAD, sx1 = sx0 + PICK_SV, sy1 = sy0 + PICK_SV;
        int hueCol = Color.HSBtoRGB(popHue, 1f, 1f) | 0xFF000000;
        gradQuad(sx0, sy0, sx1, sy1, 0xFFFFFFFF, hueCol, hueCol, 0xFFFFFFFF, t);
        gradQuad(sx0, sy0, sx1, sy1, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000, t);
        frame(sx0 - 0.5f, sy0 - 0.5f, PICK_SV + 1, PICK_SV + 1, alpha(EDGE, t));
        float kx = sx0 + popSat * PICK_SV, ky = sy0 + (1f - popVal) * PICK_SV;
        fill(kx - 3, ky - 3, 6, 6, alpha(new Color(0, 0, 0, 220), t));
        fill(kx - 2, ky - 2, 4, 4, alpha(new Color(235, 235, 235), t));
        fill(kx - 1, ky - 1, 2, 2, alpha(new Color(popArgb() & 0xFFFFFF), t));
        hit(sx0, sy0, PICK_SV, PICK_SV, b -> {
            popDrag = 1;
            dragPopup();
        });

        float hx0 = sx1 + PICK_GAP, hx1 = hx0 + PICK_HUE;
        int[] hues = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        for (int i = 0; i < 6; i++) {
            float y0 = sy0 + PICK_SV * i / 6f, y1 = sy0 + PICK_SV * (i + 1) / 6f;
            gradQuad(hx0, y0, hx1, y1, hues[i], hues[i], hues[i + 1], hues[i + 1], t);
        }
        float hy = sy0 + popHue * PICK_SV;
        fill(hx0 - 1.5f, hy - 1.5f, PICK_HUE + 3, 3, alpha(new Color(0, 0, 0, 220), t));
        fill(hx0 - 0.5f, hy - 0.5f, PICK_HUE + 1, 1, alpha(new Color(235, 235, 235), t));
        hit(hx0 - 2, sy0, PICK_HUE + 4, PICK_SV, b -> {
            popDrag = 2;
            dragPopup();
        });

        float ay0 = sy1 + PICK_GAP, ay1 = ay0 + PICK_ALPHA;
        int rgb = popArgb() & 0xFFFFFF;
        for (int i = 0; i < PICK_SV / 4; i++) {
            for (int j = 0; j < 2; j++) {
                fill(sx0 + i * 4, ay0 + j * 4, 4, 4, alpha((i + j) % 2 == 0 ? new Color(60, 60, 66) : new Color(36, 36, 40), t));
            }
        }
        gradQuad(sx0, ay0, sx1, ay1, rgb, 0xFF000000 | rgb, 0xFF000000 | rgb, rgb, t);
        float am = sx0 + popAlpha * PICK_SV;
        fill(am - 1.5f, ay0 - 1, 3, PICK_ALPHA + 2, alpha(new Color(0, 0, 0, 220), t));
        fill(am - 0.5f, ay0, 1, PICK_ALPHA, alpha(new Color(235, 235, 235), t));
        hit(sx0, ay0 - 2, PICK_SV, PICK_ALPHA + 4, b -> {
            popDrag = 3;
            dragPopup();
        });
        fill(hx0, ay0, PICK_HUE, PICK_ALPHA, alpha(new Color(rgb), t));

        float by0 = ay1 + 6;
        fill(sx0, by0, 26, PICK_ROW, alpha(new Color(rgb), t));
        frame(sx0 - 0.5f, by0 - 0.5f, 27, PICK_ROW + 1, alpha(new Color(255, 255, 255, 50), t));
        CustomFontRenderer f = bold(12.5f);
        String hex = String.format("#%06X", rgb) + (popAlpha < 0.995f ? "  " + Math.round(popAlpha * 100) + "%" : "");
        stringMid(f, hex, sx0 + 32, by0, PICK_ROW, TEXT, t);

        Color accent = accent();
        button("pick-copy", "C", hx1 - 33, by0, 16, PICK_ROW, t, accent, false, () -> ColorClipboard.copy(popArgb()));
        button("pick-paste", "P", hx1 - 16, by0, 16, PICK_ROW, t, accent, false, () -> {
            Integer pasted = ColorClipboard.paste();
            if (pasted == null) return;
            float[] hsb = Color.RGBtoHSB(pasted >> 16 & 255, pasted >> 8 & 255, pasted & 255, null);
            popHue = hsb[0];
            popSat = hsb[1];
            popVal = hsb[2];
            popAlpha = (pasted >>> 24) / 255f;
            applyPopup();
        });
    }

    private static void setColor(Property<?> p, int argb) {
        if (p instanceof NumberProperty) ((NumberProperty) p).setValue((double) argb);
    }

    protected void button(Object key, String label, float x, float y, float w, float h, float a, Color accent, boolean strong, Runnable action) {
        boolean hover = over(x, y, w, h);
        float hv = anim("btn:" + key, hover ? 1f : 0f, FAST);
        CustomFontRenderer f = bold(12.5f);
        if (strong) {
            fill(x, y, w, h, alpha(accent, (0.85f + 0.15f * hv) * a));
            stringMid(f, label, x + (w - f.getStringWidth(label)) / 2f, y, h, luminance(accent) > 0.6f ? 0x121318 : WHITE, a);
        } else {
            fill(x, y, w, h, alpha(mix(FIELD, new Color(34, 35, 43), hv), a));
            frame(x, y, w, h, alpha(EDGE_2, a));
            stringMid(f, label, x + (w - f.getStringWidth(label)) / 2f, y, h, mixRgb(TEXT_2, TEXT, hv), a);
        }
        hit(x, y, w, h, b -> action.run());
    }

    private static float luminance(Color c) {
        return (0.2126f * c.getRed() + 0.7152f * c.getGreen() + 0.0722f * c.getBlue()) / 255f;
    }

    private void drawTooltip(float a) {
        if (hoverText == null) {
            hoverKey = null;
            anim("tip", 0f, FAST);
            return;
        }
        float t = anim("tip", System.currentTimeMillis() - hoverSince > 450 ? 1f : 0f, MEDIUM);
        if (t < 0.02f) return;
        CustomFontRenderer f = bold(12);
        float tw = f.getStringWidth(hoverText) + 10, th = 13;
        float tx = Math.min(mouseX + 9, width - tw - 4), ty = mouseY + 11 + (1f - t) * 3f;
        fill(tx, ty, tw, th, alpha(new Color(10, 11, 14, 245), t * a));
        frame(tx, ty, tw, th, alpha(EDGE_2, t * a));
        stringMid(f, hoverText, tx + 5, ty, th, TEXT_2, t * a);
    }

    private void updateDrags() {
        if (!Mouse.isButtonDown(0)) {
            dragSlider = null;
            popDrag = 0;
            return;
        }
        if (dragSlider != null) setSlider(dragSlider, (mouseX - dragSliderX) / dragSliderW);
        dragPopup();
        dragging();
    }

    protected void dragging() {
    }

    protected void released() {
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (closing) return;
        mouseX = mx;
        mouseY = my;
        Hit target = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                target = h;
                break;
            }
        }
        boolean wasBinding = binding != null;
        searchFocused = false;
        unfocusOwn();
        if (editing != null) commitEdit();
        if (wasBinding && target != null) binding = null;
        if (target != null) target.action.accept(button);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragSlider = null;
        popDrag = 0;
        released();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || closing) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        scrolled(mx, my, wheel > 0 ? 1 : -1);
    }

    private void commitEdit() {
        if (editing != null) editing.setValueObj(editBuffer);
        editing = null;
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

        if (searchFocused || editing != null || ownFocused()) {
            if (press) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                    if (editing != null) {
                        if (keyCode == Keyboard.KEY_RETURN) commitEdit();
                        else editing = null;
                    } else if (ownFocused() && keyCode == Keyboard.KEY_RETURN) {
                        submitOwn();
                    }
                    searchFocused = false;
                    unfocusOwn();
                } else if (keyCode == Keyboard.KEY_BACK) {
                    edit(s -> s.isEmpty() ? s : s.substring(0, s.length() - 1));
                } else if (isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
                    String clip = GuiScreen.getClipboardString();
                    if (clip != null) {
                        StringBuilder sb = new StringBuilder();
                        for (char ch : clip.toCharArray()) if (ChatAllowedCharacters.isAllowedCharacter(ch)) sb.append(ch);
                        edit(s -> s + sb);
                    }
                }
                return;
            }
            if (typedChar != 0 && ChatAllowedCharacters.isAllowedCharacter(typedChar) && !isCtrlKeyDown()) edit(s -> s + typedChar);
            return;
        }

        if (!press) return;
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) {
            searchFocused = true;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClientSettingsModule.menuKey.getValue().intValue()) closing = true;
    }

    private void edit(UnaryOperator<String> change) {
        if (searchFocused) setSearch(change.apply(search()));
        else if (editing != null) editBuffer = change.apply(editBuffer);
        else editOwn(change);
    }
}
