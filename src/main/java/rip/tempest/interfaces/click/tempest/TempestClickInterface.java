package rip.tempest.interfaces.click.tempest;

import rip.tempest.Tempest;
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
import rip.tempest.processes.Themes;
import rip.tempest.utils.misc.LocalConfigFetcher;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
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

public final class TempestClickInterface extends GuiScreen {
    private static final float WIN_W = 600, WIN_H = 380, SIDEBAR_W = 136, HEADER_H = 44, RADIUS = 9;
    private static final float PAD = 12, COL_GAP = 8, CARD_HEADER = 28, CARD_RADIUS = 6;
    private static final float NAV_H = 24, NAV_TOP = 64;

    private static final Color WINDOW = new Color(15, 15, 19, 248);
    private static final Color SIDEBAR = new Color(11, 11, 14, 255);
    private static final Color CARD = new Color(22, 22, 28, 255);
    private static final Color CARD_HOVER = new Color(27, 27, 34, 255);
    private static final Color FIELD = new Color(31, 32, 40, 255);
    private static final Color FIELD_HOVER = new Color(38, 39, 49, 255);
    private static final Color TRACK = new Color(48, 49, 60, 255);
    private static final Color EDGE = new Color(255, 255, 255, 16);
    private static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final int TEXT = 0xF2F3F6, TEXT_2 = 0xA3A6B3, TEXT_3 = 0x666978;

    private static final float FAST = 22f, MEDIUM = 14f, SLOW = 9f;

    private enum Page {
        COMBAT("Combat", ModuleCategory.COMBAT), MOVEMENT("Movement", ModuleCategory.MOVEMENT),
        PLAYER("Player", ModuleCategory.PLAYER), VISUALS("Visuals", ModuleCategory.VISUALS),
        CLIENT("Client", ModuleCategory.CLIENT), THEMES("Themes", null), CONFIGS("Configs", null);

        final String title;
        final ModuleCategory category;

        Page(String title, ModuleCategory category) {
            this.title = title;
            this.category = category;
        }
    }

    private static Page page = Page.COMBAT;
    private static String search = "";
    private static float winX = Float.NaN, winY;
    private static final Set<String> expanded = new HashSet<>();
    private static final Map<String, Float> scrollTarget = new HashMap<>();

    private final Map<Object, Float> anims = new HashMap<>();
    private final Map<Property<?>, ColorPicker> pickers = new HashMap<>();
    private final Set<Property<?>> openLists = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<float[]> clips = new ArrayDeque<>();
    private long lastFrame;
    private float delta;
    private long openedAt;
    private boolean closing;
    private int mouseX, mouseY;

    private boolean searchFocused;
    private Module binding;
    private Property<?> editing;
    private String editBuffer = "";
    private String configName = "";
    private boolean configFieldFocused;
    private String confirmDelete;

    private String armedDelete;
    private List<String> configs;
    private String configStatus = "";
    private long configStatusAt;

    private NumberProperty dragSlider;
    private float dragSliderX, dragSliderW;
    private ColorPicker dragPicker;
    private Property<?> dragPickerProperty;
    private int dragPickerX, dragPickerY;
    private boolean dragWindow, dragScrollbar;
    private float dragOffX, dragOffY, dragScrollStart, dragScrollMouse, dragScrollRatio;

    private String hoverText;
    private Object hoverKey;
    private long hoverSince;

    private float bodyX, bodyY, bodyW, bodyH, contentH;

    private CustomFontRenderer body, bold, small, title;

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

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        openedAt = System.currentTimeMillis();
        closing = false;
        lastFrame = 0L;
        configs = null;
        body = font("inter2", null, 18);
        bold = font("interbold", null, 18);
        small = font(null, "inter2", 15);
        title = font(null, "interbold", 24);
    }

    private static CustomFontRenderer font(String registered, String file, float size) {
        CustomFontRenderer f = null;
        try {
            f = registered != null ? FontProcess.getFont(registered) : FontProcess.getStyledFont(file, size, java.awt.Font.PLAIN);
        } catch (Exception ignored) {
        }
        return f != null ? f : FontProcess.getFont("tempest");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        binding = null;
        editing = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private float anim(Object key, float target, float speed) {
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

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOut(float t) {
        float u = 1f - clamp(t);
        return 1f - u * u * u;
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * clamp(a)));
    }

    private static Color mix(Color a, Color b, float t) {
        t = clamp(t);
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static int text(int rgb, float a) {
        return (Math.max(5, Math.round(255 * clamp(a))) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mixRgb(int a, int b, float t) {
        Color c = mix(new Color(a), new Color(b), t);
        return c.getRGB() & 0xFFFFFF;
    }

    private Color accent() {
        Color c = ColorProcess.getColor();
        return c == null ? new Color(110, 140, 255) : c;
    }

    private void pushClip(float x, float y, float w, float h) {
        float[] r = {x, y, x + w, y + h};
        if (!clips.isEmpty()) {
            float[] p = clips.peek();
            r = new float[]{Math.max(r[0], p[0]), Math.max(r[1], p[1]), Math.min(r[2], p[2]), Math.min(r[3], p[3])};
        }
        clips.push(r);
        applyClip(r);
    }

    private void popClip() {
        clips.pop();
        if (clips.isEmpty()) RenderUtils.endScissor();
        else applyClip(clips.peek());
    }

    private static void applyClip(float[] r) {
        RenderUtils.startScissor(r[0], r[1], Math.max(0, r[2] - r[0]), Math.max(0, r[3] - r[1]));
    }

    private boolean over(float x, float y, float w, float h) {
        if (closing || mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) return false;
        if (clips.isEmpty()) return true;
        float[] c = clips.peek();
        return mouseX >= c[0] && mouseX <= c[2] && mouseY >= c[1] && mouseY <= c[3];
    }

    private void hit(float x, float y, float w, float h, IntConsumer action) {
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

    private void tooltip(Object key, String text) {
        if (text == null || text.isEmpty()) return;
        if (key != hoverKey) {
            hoverKey = key;
            hoverSince = System.currentTimeMillis();
        }
        hoverText = text;
    }

    private static void round(float x, float y, float w, float h, float r, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, h, c);
    }

    private static void frame(float x, float y, float w, float h, Color c) {
        if (c.getAlpha() <= 0 || w <= 0 || h <= 0) return;
        Glyphs.bar(x, y, w, 1, c);
        Glyphs.bar(x, y + h - 1, w, 1, c);
        Glyphs.bar(x, y + 1, 1, h - 2, c);
        Glyphs.bar(x + w - 1, y + 1, 1, h - 2, c);
    }

    private static void checkbox(float x, float y, float s, float on, float a, Color accent, boolean hover) {
        round(x, y, s, s, 0, alpha(hover ? FIELD_HOVER : FIELD, a));
        frame(x, y, s, s, alpha(new Color(255, 255, 255, hover ? 46 : 26), a));
        float t = easeOut(on);
        if (t > 0.01f) {
            float inset = 2f + (s / 2f - 2f) * (1f - t);
            round(x + inset, y + inset, s - inset * 2, s - inset * 2, 0, alpha(accent, a * t));
        }
    }

    private void string(CustomFontRenderer f, String s, float x, float y, int rgb, float a) {
        if (a < 0.03f || s == null || s.isEmpty()) return;
        f.drawString(s, x, y, text(rgb, a));
    }

    private void stringMid(CustomFontRenderer f, String s, float x, float y, float h, int rgb, float a) {
        string(f, s, x, y + (h - f.getHeight()) / 2f, rgb, a);
    }

    private static String trim(CustomFontRenderer f, String s, float width) {
        if (f.getStringWidth(s) <= width) return s;
        String t = s;
        while (t.length() > 1 && f.getStringWidth(t + "...") > width) t = t.substring(0, t.length() - 1);
        return t + "...";
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

        if (Float.isNaN(winX)) {
            winX = (width - WIN_W) / 2f;
            winY = (height - WIN_H) / 2f;
        }
        updateDrags();

        float open = anim("open", closing ? 0f : 1f, closing ? 18f : 11f);
        if (closing && open < 0.02f) {
            mc.displayGuiScreen(mc.theWorld == null ? new rip.tempest.interfaces.menu.main.CustomMainMenu() : null);
            return;
        }
        float a = easeOut(open);

        if (mc.theWorld == null) drawDefaultBackground();
        Glyphs.bar(0, 0, width, height, new Color(0, 0, 0, Math.round(90 * a)));

        float x = Math.round(winX), y = Math.round(winY + (1f - a) * 14f);
        Color accent = accent();

        round(x - 1, y - 1, WIN_W + 2, WIN_H + 2, 0, new Color(0, 0, 0, Math.round(80 * a)));
        round(x, y, WIN_W, WIN_H, RADIUS, alpha(WINDOW, a));
        round(x, y, SIDEBAR_W, WIN_H, RADIUS, alpha(SIDEBAR, a));
        Glyphs.bar(x + SIDEBAR_W, y, 1, WIN_H, alpha(EDGE, a));
        frame(x, y, WIN_W, WIN_H, alpha(EDGE, a));

        drawSidebar(x, y, a, accent);
        drawHeader(x + SIDEBAR_W, y, a, accent);
        drawBody(x + SIDEBAR_W, y + HEADER_H, WIN_W - SIDEBAR_W, WIN_H - HEADER_H, a, accent);
        drawTooltip(a);

        if (!dragWindow) hit(x, y, SIDEBAR_W, 48, b -> startWindowDrag());

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void startWindowDrag() {
        dragWindow = true;
        dragOffX = mouseX - winX;
        dragOffY = mouseY - winY;
    }

    private void updateDrags() {
        boolean down = Mouse.isButtonDown(0);
        if (!down) {
            dragSlider = null;
            dragWindow = false;
            dragScrollbar = false;
            if (dragPicker != null) {
                dragPicker.mouseReleased();
                dragPicker = null;
            }
            return;
        }
        if (dragWindow) {
            winX = Math.max(-WIN_W + 60, Math.min(width - 60, mouseX - dragOffX));
            winY = Math.max(0, Math.min(height - 30, mouseY - dragOffY));
        }
        if (dragSlider != null) setSlider(dragSlider, (mouseX - dragSliderX) / dragSliderW);
        if (dragPicker != null) {
            dragPicker.mouseDragged(dragPickerX, dragPickerY, mouseX, mouseY);
            setColor(dragPickerProperty, dragPicker.getColor());
        }
        if (dragScrollbar) {
            float target = dragScrollStart + (mouseY - dragScrollMouse) * dragScrollRatio;
            scrollTarget.put(scrollKey(), Math.max(0, Math.min(maxScroll(), target)));
        }
    }

    private void drawSidebar(float x, float y, float a, Color accent) {
        string(title, "Tempest", x + 14, y + 25 - title.getHeight() / 2f, TEXT, a);
        string(small, "v" + Tempest.VERSION, x + 14, y + 37, TEXT_3, a);

        boolean searching = !search.isEmpty();
        float navY = y + NAV_TOP;
        float selectedY = -1;
        int index = 0;
        for (Page p : Page.values()) {
            if (p == Page.THEMES) {
                navY += 10;
                string(small, "CUSTOMISE", x + 14, navY, TEXT_3, a);
                navY += 12;
            }
            float rowY = navY;
            if (p == page && !searching) selectedY = rowY;
            boolean hover = over(x + 8, rowY, SIDEBAR_W - 16, NAV_H - 2);
            float h = anim("navh:" + p, hover ? 1f : 0f, FAST);
            float sel = anim("nav:" + p, p == page && !searching ? 1f : 0f, MEDIUM);
            Glyphs.bar(x + 1, rowY, SIDEBAR_W - 2, NAV_H - 2, alpha(new Color(255, 255, 255), 0.035f * h * a));

            int textRgb = mixRgb(mixRgb(TEXT_2, TEXT, h * 0.6f), TEXT, sel);
            ResourceLocation icon = icon(p);
            float textX = x + 14 + (icon != null ? 18 : 0) + 2 * h;
            if (icon != null) drawIcon(icon, x + 14 + 2 * h, rowY + (NAV_H - 2 - 11) / 2f, 11, mix(new Color(TEXT_2), accent, sel), a);

            stringMid(body, p.title, textX, rowY, NAV_H - 2, mixRgb(textRgb, accent.getRGB() & 0xFFFFFF, sel), a);

            if (p.category != null) {
                int count = Tempest.INSTANCE.getModuleManager().getModulesForCategory(p.category).size();
                String n = String.valueOf(count);
                stringMid(small, n, x + SIDEBAR_W - 16 - small.getStringWidth(n), rowY, NAV_H - 2, TEXT_3, a);
            }
            final Page target = p;
            hit(x + 8, rowY, SIDEBAR_W - 16, NAV_H - 2, b -> openPage(target));
            navY += NAV_H;
            index++;
        }

        String keyName = Keyboard.getKeyName(ClientSettingsModule.menuKey.getValue().intValue());
        string(small, (keyName == null ? "RSHIFT" : keyName) + " to close", x + 14, y + WIN_H - 18, TEXT_3, a);
    }

    private void openPage(Page p) {
        if (p == page && search.isEmpty()) return;
        page = p;
        anims.put("page", 0f);
        configs = null;
    }

    private final Map<Page, ResourceLocation> icons = new HashMap<>();
    private final Set<Page> missingIcons = new HashSet<>();

    private ResourceLocation icon(Page p) {
        if (missingIcons.contains(p)) return null;
        ResourceLocation r = icons.get(p);
        if (r != null) return r;
        String name = p == Page.THEMES ? "visuals" : p.name().toLowerCase();
        r = new ResourceLocation("tempest/images/categories/" + name + ".png");
        try {
            mc.getResourceManager().getResource(r);
            icons.put(p, r);
            return r;
        } catch (Exception e) {
            missingIcons.add(p);
            return null;
        }
    }

    private void drawIcon(ResourceLocation r, float x, float y, float size, Color tint, float a) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, a);
        mc.getTextureManager().bindTexture(r);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawHeader(float x, float y, float a, Color accent) {
        hit(x, y, WIN_W - SIDEBAR_W, HEADER_H - 1, b -> startWindowDrag());
        String heading = search.isEmpty() ? page.title : "Search";
        string(title, heading, x + PAD + 4, y + (HEADER_H - title.getHeight()) / 2f, TEXT, a);
        if (!search.isEmpty()) {
            int found = searchResults().size();
            string(small, found + (found == 1 ? " result" : " results"), x + PAD + 8 + title.getStringWidth(heading),
                    y + (HEADER_H - small.getHeight()) / 2f + 1, TEXT_3, a);
        }

        float sw = 168, sh = 22, sx = x + (WIN_W - SIDEBAR_W) - PAD - sw, sy = y + (HEADER_H - sh) / 2f;
        boolean hover = over(sx, sy, sw, sh);
        float focus = anim("searchF", searchFocused ? 1f : 0f, FAST);
        float hv = anim("searchH", hover ? 1f : 0f, FAST);
        round(sx, sy, sw, sh, sh / 2f, alpha(mix(FIELD, FIELD_HOVER, Math.max(hv, focus)), a));
        frame(sx, sy, sw, sh, alpha(mix(EDGE, accent, focus), a));

        Glyphs.ring(sx + 12, sy + sh / 2f - 0.5f, 3.6f, 1.2f, 0f, 360f, alpha(new Color(TEXT_2), a));
        Glyphs.stroke(sx + 14.6f, sy + sh / 2f + 2.1f, sx + 16.6f, sy + sh / 2f + 4.1f, 1.2f, alpha(new Color(TEXT_2), a));

        float textX = sx + 22;
        if (search.isEmpty() && !searchFocused) {
            stringMid(small, "Search modules", textX, sy, sh, TEXT_3, a);
        } else {
            String shown = trim(small, search, sw - 44);
            stringMid(small, shown, textX, sy, sh, TEXT, a);
            if (searchFocused && caretOn()) {
                float cx = textX + small.getStringWidth(shown) + 1;
                Glyphs.bar(cx, sy + 6, 1, sh - 12, alpha(accent, a));
            }
        }
        hit(sx, sy, sw, sh, b -> {
            searchFocused = true;
            configFieldFocused = false;
            editing = null;
        });

        if (!search.isEmpty()) {
            float cx = sx + sw - 11, cy = sy + sh / 2f;
            boolean ch = over(cx - 6, cy - 6, 12, 12);
            Color c = alpha(new Color(ch ? TEXT : TEXT_2), a);
            Glyphs.stroke(cx - 3, cy - 3, cx + 3, cy + 3, 1.2f, c);
            Glyphs.stroke(cx - 3, cy + 3, cx + 3, cy - 3, 1.2f, c);
            hit(cx - 6, cy - 6, 12, 12, b -> {
                search = "";
                scrollTarget.put(scrollKey(), 0f);
                anims.put("page", 0f);
            });
        }
        Glyphs.bar(x, y + HEADER_H - 1, WIN_W - SIDEBAR_W, 1, alpha(EDGE, a));
    }

    private boolean caretOn() {
        return System.currentTimeMillis() % 1000 < 520;
    }

    private String scrollKey() {
        return search.isEmpty() ? page.name() : "search";
    }

    private float maxScroll() {
        return Math.max(0, contentH - bodyH);
    }

    private void drawBody(float x, float y, float w, float h, float a, Color accent) {
        bodyX = x;
        bodyY = y;
        bodyW = w;
        bodyH = h;

        float pageIn = easeOut(anim("page", 1f, SLOW));
        float pa = a * pageIn;
        String key = scrollKey();
        float target = Math.max(0, Math.min(maxScroll(), scrollTarget.getOrDefault(key, 0f)));
        scrollTarget.put(key, target);
        float scroll = anim("scroll:" + key, target, MEDIUM);
        float top = y + PAD - scroll + (1f - pageIn) * 8f;

        pushClip(x, y, w - 1, h - 1);
        float end;
        if (!search.isEmpty()) end = drawModuleColumns(searchResults(), x, top, w, pa, accent);
        else if (page == Page.THEMES) end = drawThemes(x, top, w, pa, accent);
        else if (page == Page.CONFIGS) end = drawConfigs(x, top, w, pa, accent);
        else end = drawModuleColumns(modules(page.category), x, top, w, pa, accent);
        popClip();
        contentH = end - top + PAD;

        drawScrollbar(x, y, w, h, scroll, a, accent);
    }

    private void drawScrollbar(float x, float y, float w, float h, float scroll, float a, Color accent) {
        float max = maxScroll();
        float barX = x + w - 7, trackY = y + 6, trackH = h - 12;
        boolean inBody = over(x, y, w, h);
        boolean onBar = over(barX - 3, trackY, 9, trackH);
        float recent = anim("barRecent", 0f, 2.5f);
        float show = anim("barShow", max <= 0 ? 0f : (inBody || dragScrollbar ? 1f : Math.max(0.35f, recent)), MEDIUM);
        if (show <= 0.01f || max <= 0) return;
        float wide = anim("barWide", onBar || dragScrollbar ? 1f : 0f, FAST);
        float thumbH = Math.max(24, trackH * bodyH / contentH);
        float thumbY = trackY + (trackH - thumbH) * (scroll / max);
        float bw = 3f + 1.5f * wide;
        round(barX + 1.5f - bw / 2f, trackY, bw, trackH, bw / 2f, alpha(new Color(255, 255, 255), 0.04f * show * a));
        round(barX + 1.5f - bw / 2f, thumbY, bw, thumbH, bw / 2f, alpha(mix(new Color(120, 122, 135), accent, wide), (0.7f + 0.3f * wide) * show * a));
        hit(barX - 3, trackY, 9, trackH, b -> {
            if (mouseY < thumbY || mouseY > thumbY + thumbH) {
                float ratio = (mouseY - trackY - thumbH / 2f) / Math.max(1f, trackH - thumbH);
                scrollTarget.put(scrollKey(), Math.max(0, Math.min(max, ratio * max)));
            }
            dragScrollbar = true;
            dragScrollStart = scrollTarget.getOrDefault(scrollKey(), 0f);
            dragScrollMouse = mouseY;
            dragScrollRatio = max / Math.max(1f, trackH - thumbH);
        });
    }

    private List<Module> modules(ModuleCategory category) {
        List<Module> list = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModulesForCategory(category));
        list.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return list;
    }

    private List<Module> searchResults() {
        String q = search.toLowerCase().replace(" ", "");
        List<Module> out = new ArrayList<>();
        for (Module m : Tempest.INSTANCE.getModuleManager().getModules()) {
            if (m.getLabel().toLowerCase().replace(" ", "").contains(q)) out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.getLabel().toLowerCase()));
        return out;
    }

    private float drawModuleColumns(List<Module> list, float x, float top, float w, float a, Color accent) {
        float colW = (w - PAD * 2 - COL_GAP) / 2f;
        float[] colY = {top, top};
        if (list.isEmpty()) {
            string(body, search.isEmpty() ? "Nothing here yet" : "No modules match \"" + search + "\"", x + PAD + 4, top + 6, TEXT_3, a);
            return top + 30;
        }
        for (int i = 0; i < list.size(); i++) {
            int col = i % 2;
            float cx = x + PAD + col * (colW + COL_GAP);
            colY[col] += drawCard(list.get(i), cx, colY[col], colW, a, accent, i) + COL_GAP;
        }
        return Math.max(colY[0], colY[1]) - COL_GAP;
    }

    private List<Property<?>> settings(Module m) {
        List<Property<?>> out = new ArrayList<>();
        for (Property<?> p : m.getElements()) if (p.isAvailable()) out.add(p);
        return out;
    }

    private float drawCard(Module m, float x, float y, float w, float a, Color accent, int index) {
        String id = m.getLabel();

        float pageT = anims.getOrDefault("page", 1f);
        a *= easeOut(clamp(pageT * 1.8f - Math.min(index, 10) * 0.07f));

        List<Property<?>> props = settings(m);
        boolean isOpen = expanded.contains(id) && !props.isEmpty();
        float openT = anim("exp:" + id, isOpen ? 1f : 0f, MEDIUM);
        float settingsH = 0;
        for (Property<?> p : props) settingsH += rowHeight(p);
        float cardH = CARD_HEADER + (settingsH + 8) * easeOut(openT);

        boolean hoverCard = over(x, y, w, cardH);
        boolean hoverHeader = over(x, y, w, CARD_HEADER);
        float hv = anim("cardH:" + id, hoverHeader ? 1f : 0f, FAST);
        round(x, y, w, cardH, 0, alpha(mix(CARD, CARD_HOVER, hv), a));
        frame(x, y, w, cardH, alpha(EDGE, a));
        float on = anim("on:" + id, m.isEnabled() ? 1f : 0f, MEDIUM);

        if (on > 0.01f) round(x, y, 2, cardH * easeOut(on), 0, alpha(accent, on * a));

        int nameRgb = mixRgb(TEXT_2, TEXT, Math.max(on, hv * 0.5f));
        float nameX = x + 11;
        stringMid(body, trim(body, id, w - 90), nameX, y, CARD_HEADER, nameRgb, a);
        if (!props.isEmpty()) {
            float chevX = nameX + Math.min(body.getStringWidth(id), w - 90) + 7;
            Glyphs.chevron(chevX, y + CARD_HEADER / 2f, 2.4f, 1.1f, openT > 0.5f, alpha(new Color(TEXT_3), a * (0.6f + 0.4f * hv)));
        }
        if (hoverHeader) tooltip(m, m.getDescription());

        hit(x, y, w, CARD_HEADER, b -> {
            if (b == 0) m.toggle();
            else if (b == 2) binding = m;
            else if (b == 1 && !props.isEmpty()) {
                if (!expanded.remove(id)) expanded.add(id);
            }
        });

        float cb = 10, cbX = x + w - 10 - cb, cbY = y + (CARD_HEADER - cb) / 2f;
        checkbox(cbX, cbY, cb, on, a, accent, over(cbX - 2, cbY - 2, cb + 4, cb + 4));
        hit(cbX - 3, cbY - 3, cb + 6, cb + 6, b -> m.toggle());

        String key = binding == m ? "[...]" : m.getKey() > 0 ? "[" + Keyboard.getKeyName(m.getKey()) + "]" : (hoverHeader ? "[-]" : null);
        if (key != null) {
            float kw = small.getStringWidth(key), kh = 13, kx = cbX - 8 - kw, ky = y + (CARD_HEADER - kh) / 2f;
            boolean kHover = over(kx - 2, ky, kw + 4, kh);
            int kRgb = binding == m ? accent.getRGB() & 0xFFFFFF : kHover ? TEXT : (m.getKey() > 0 ? TEXT_2 : TEXT_3);
            stringMid(small, key, kx, ky, kh, kRgb, a);
            hit(kx - 2, ky, kw + 4, kh, b -> {
                binding = binding == m ? null : m;
                searchFocused = false;
            });
        }

        if (openT > 0.002f) {
            float sy = y + CARD_HEADER;
            pushClip(x, sy, w, cardH - CARD_HEADER);
            Glyphs.bar(x + 1, sy, w - 2, 1, alpha(EDGE, a * openT));
            float rowY = sy + 4;
            float sa = a * clamp(openT * 1.6f - 0.2f);
            for (Property<?> p : props) rowY += drawSetting(p, x + 10, rowY, w - 20, sa, accent);
            popClip();
        }
        return cardH;
    }

    private static boolean isColor(Property<?> p) {
        return p instanceof NumberProperty && ((NumberProperty) p).getMin() <= Integer.MIN_VALUE && ((NumberProperty) p).getMax() >= Integer.MAX_VALUE;
    }

    private static boolean isList(Property<?> p) {
        return p instanceof ModeProperty || p instanceof MultiModeProperty || p instanceof SelectProperty;
    }

    private float rowHeight(Property<?> p) {
        if (isColor(p)) return 18 + (ColorPicker.TOTAL_HEIGHT + 6) * easeOut(anims.getOrDefault("pick:" + System.identityHashCode(p), 0f));
        if (p instanceof NumberProperty) return 25;
        if (isList(p)) {
            float open = easeOut(anims.getOrDefault("list:" + System.identityHashCode(p), 0f));
            return 19 + (options(p).size() * 14 + 4) * open;
        }
        return 18;
    }

    private float drawSetting(Property<?> p, float x, float y, float w, float a, Color accent) {
        float h = rowHeight(p);
        if (isColor(p)) drawColorRow((NumberProperty) p, x, y, w, a, accent);
        else if (p instanceof NumberProperty) drawSlider((NumberProperty) p, x, y, w, a, accent);
        else if (isList(p)) drawList(p, x, y, w, a, accent);
        else if (p.getValue() instanceof Boolean) drawBool(p, x, y, w, a, accent);
        else if (p.getValue() instanceof String) drawText(p, x, y, w, a, accent);
        return h;
    }

    @SuppressWarnings("unchecked")
    private void drawBool(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean value = (Boolean) p.getValue();
        boolean hover = over(x - 4, y, w + 8, 18);
        float on = anim(p, value ? 1f : 0f, MEDIUM);
        stringMid(small, p.getLabel(), x, y, 18, mixRgb(TEXT_2, TEXT, Math.max(on * 0.7f, hover ? 0.5f : 0f)), a);
        checkbox(x + w - 9, y + 4.5f, 9, on, a, accent, hover);
        hit(x - 4, y, w + 8, 18, b -> ((Property<Boolean>) p).setValue(!value));
    }

    private void drawSlider(NumberProperty p, float x, float y, float w, float a, Color accent) {
        double min = p.getMin(), max = p.getMax(), value = p.getValue();
        float pct = max > min ? (float) ((value - min) / (max - min)) : 0f;
        float shown = anim(p, pct, FAST);
        boolean dragging = dragSlider == p;
        boolean hover = over(x - 2, y + 11, w + 4, 14) || dragging;
        float hv = anim("sh:" + System.identityHashCode(p), hover ? 1f : 0f, FAST);

        string(small, trim(small, p.getLabel(), w - 50), x, y + 3, mixRgb(TEXT_2, TEXT, hv * 0.6f), a);
        String v = format(value, p.getIncrement());
        string(small, v, x + w - small.getStringWidth(v), y + 3, TEXT, a);

        float ty = y + 16, th = 5;
        round(x, ty, w, th, 0, alpha(mix(TRACK, new Color(60, 61, 74), hv), a));
        if (shown > 0.001f) round(x, ty, w * shown, th, 0, alpha(accent, a));
        float kx = Math.max(x, Math.min(x + w - 2, x + w * shown - 1));
        round(kx, ty - 1, 2, th + 2, 0, alpha(Color.WHITE, (0.55f + 0.45f * hv) * a));
        hit(x - 4, y + 10, w + 8, 16, b -> {
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

    private List<String> options(Property<?> p) {
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

    private String listValue(Property<?> p) {
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

    private void drawList(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean isOpen = openLists.contains(p);
        float open = anim("list:" + System.identityHashCode(p), isOpen ? 1f : 0f, MEDIUM);
        String value = listValue(p);
        float boxW = Math.min(w * 0.58f, Math.max(60, small.getStringWidth(value) + 22)), boxH = 15, bx = x + w - boxW, by = y + 2;
        boolean hover = over(bx, by, boxW, boxH) || over(x, y, w - boxW, 19);
        float hv = anim("lh:" + System.identityHashCode(p), hover || isOpen ? 1f : 0f, FAST);

        stringMid(small, trim(small, p.getLabel(), w - boxW - 6), x, y, 19, mixRgb(TEXT_2, TEXT, hv * 0.6f), a);
        round(bx, by, boxW, boxH, 0, alpha(mix(FIELD, FIELD_HOVER, hv), a));
        frame(bx, by, boxW, boxH, alpha(EDGE, a));
        stringMid(small, trim(small, value, boxW - 20), bx + 6, by, boxH, TEXT, a);
        Glyphs.chevron(bx + boxW - 8, by + boxH / 2f, 2.2f, 1.1f, open > 0.5f, alpha(new Color(TEXT_2), a));
        hit(x, y, w, 19, b -> {
            if (!openLists.remove(p)) {
                openLists.add(p);
                if (p instanceof SelectProperty) ((SelectProperty) p).onOpen();
            }
        });

        if (open > 0.002f) {
            List<String> opts = options(p);
            float listH = (opts.size() * 14 + 4) * easeOut(open);
            float ly = y + 19;
            pushClip(x, ly, w, listH);
            round(x, ly, w, opts.size() * 14 + 2, 0, alpha(new Color(17, 17, 22), a));
            frame(x, ly, w, opts.size() * 14 + 2, alpha(EDGE, a));
            float oy = ly + 1;
            float oa = a * clamp(open * 1.5f - 0.3f);
            for (String o : opts) {
                boolean sel = selected(p, o);
                boolean oh = over(x, oy, w, 14);
                float ohv = anim("oh:" + System.identityHashCode(p) + o, oh ? 1f : 0f, FAST);
                if (ohv > 0.01f) round(x + 2, oy, w - 4, 14, 4, alpha(new Color(255, 255, 255), 0.05f * ohv * oa));
                stringMid(small, trim(small, o, w - 26), x + 8 + 2 * ohv, oy, 14, sel ? accent.getRGB() & 0xFFFFFF : mixRgb(TEXT_2, TEXT, ohv), oa);
                if (sel) Glyphs.check(x + w - 10, oy + 7, 3.4f, 1.2f, 1f, alpha(accent, oa));
                final String option = o;

                hit(x, oy, w, 14, b -> pick(p, option));
                oy += 14;
            }
            popClip();
        }
    }

    private void drawText(Property<?> p, float x, float y, float w, float a, Color accent) {
        boolean isEditing = editing == p;
        float fw = Math.min(w * 0.6f, 110), fh = 15, fx = x + w - fw, fy = y + 1.5f;
        boolean hover = over(fx, fy, fw, fh);
        float focus = anim("tf:" + System.identityHashCode(p), isEditing ? 1f : 0f, FAST);
        stringMid(small, trim(small, p.getLabel(), w - fw - 6), x, y, 18, TEXT_2, a);
        round(fx, fy, fw, fh, 0, alpha(hover || isEditing ? FIELD_HOVER : FIELD, a));
        frame(fx, fy, fw, fh, alpha(EDGE, a));
        if (focus > 0.01f) frame(fx, fy, fw, fh, alpha(accent, focus * a));
        String shown = isEditing ? editBuffer : String.valueOf(p.getValue());
        shown = trim(small, shown, fw - 12);
        stringMid(small, shown, fx + 5, fy, fh, TEXT, a);
        if (isEditing && caretOn()) Glyphs.bar(fx + 6 + small.getStringWidth(shown), fy + 3.5f, 1, fh - 7, alpha(accent, a));
        hit(fx, fy, fw, fh, b -> {
            editing = p;
            editBuffer = String.valueOf(p.getValue());
            searchFocused = false;
            configFieldFocused = false;
        });
    }

    private void drawColorRow(NumberProperty p, float x, float y, float w, float a, Color accent) {
        String key = "pick:" + System.identityHashCode(p);
        boolean isOpen = pickers.containsKey(p) && Boolean.TRUE.equals(openColor.get(p));
        float open = anim(key, isOpen ? 1f : 0f, MEDIUM);
        int argb = p.getValue().intValue();
        stringMid(small, p.getLabel(), x, y, 18, TEXT_2, a);
        float sw = 26, sh = 11, sx = x + w - sw, sy = y + 3.5f;
        boolean hover = over(sx - 2, sy - 2, sw + 4, sh + 4);
        round(sx, sy, sw, sh, 0, alpha(new Color(argb & 0xFFFFFF), a));
        frame(sx, sy, sw, sh, alpha(new Color(255, 255, 255, hover ? 70 : 34), a));
        hit(x, y, w, 18, b -> {
            ColorPicker picker = pickers.computeIfAbsent(p, k -> new ColorPicker());
            boolean nowOpen = !Boolean.TRUE.equals(openColor.get(p));
            if (nowOpen) picker.setColor(p.getValue().intValue());
            openColor.put(p, nowOpen);
        });
        if (open > 0.002f && pickers.containsKey(p)) {
            ColorPicker picker = pickers.get(p);
            int px = Math.round(x), py = Math.round(y + 20);
            pushClip(x, y + 18, w, (ColorPicker.TOTAL_HEIGHT + 6) * easeOut(open));
            picker.render(px, py, mouseX, mouseY);
            GlStateManager.enableTexture2D();
            popClip();
            hit(px, py, ColorPicker.WIDTH, ColorPicker.TOTAL_HEIGHT, b -> {
                if (picker.mouseClicked(px, py, mouseX, mouseY, b)) {
                    dragPicker = picker;
                    dragPickerProperty = p;
                    dragPickerX = px;
                    dragPickerY = py;
                }
                setColor(p, picker.getColor());
            });
        }
    }

    private final Map<Property<?>, Boolean> openColor = new HashMap<>();

    private static void setColor(Property<?> p, int argb) {
        if (p instanceof NumberProperty) ((NumberProperty) p).setValue((double) argb);
    }

    private float drawThemes(float x, float top, float w, float a, Color accent) {
        float cardX = x + PAD, cardW = w - PAD * 2;
        float y = top;

        List<Property<?>> props = new ArrayList<>();
        props.add(ClientSettingsModule.themeSpeed);
        props.add(ClientSettingsModule.themeLength);
        props.add(ClientSettingsModule.themeSaturation);
        props.add(ClientSettingsModule.themeDirection);
        if (Themes.current() == Themes.Preset.Custom) {
            props.add(ClientSettingsModule.pickerColor1);
            props.add(ClientSettingsModule.pickerColor2);
        }
        float settingsH = 0;
        for (Property<?> p : props) settingsH += rowHeight(p);
        float boxH = 26 + settingsH + 6;
        round(cardX, y, cardW, boxH, CARD_RADIUS, alpha(CARD, a));
        stringMid(bold, "Theme Settings", cardX + 11, y, 26, TEXT, a);
        boolean active = Themes.active();
        String state = active ? "In use" : "Pick a theme to use it";
        stringMid(small, state, cardX + cardW - 11 - small.getStringWidth(state), y, 26, active ? accent.getRGB() & 0xFFFFFF : TEXT_3, a);
        float rowY = y + 26;
        for (Property<?> p : props) rowY += drawSetting(p, cardX + 11, rowY, cardW - 22, a, accent);
        y += boxH + 12;

        string(small, "PRESETS", cardX + 2, y, TEXT_3, a);
        y += 12;
        int columns = 4;
        float gap = 8, tw = (cardW - gap * (columns - 1)) / columns, th = 44;
        Themes.Preset[] presets = Themes.Preset.values();
        for (int i = 0; i < presets.length; i++) {
            Themes.Preset preset = presets[i];
            float px = cardX + (i % columns) * (tw + gap), py = y + (i / columns) * (th + gap);
            if (py > bodyY + bodyH + 10 || py + th < bodyY - 10) continue;
            boolean hover = over(px, py, tw, th);
            boolean chosen = active && Themes.current() == preset;
            float hv = anim("th:" + preset, hover ? 1f : 0f, FAST);
            float lift = 1.5f * hv;
            round(px, py - lift, tw, th, CARD_RADIUS, alpha(mix(CARD, CARD_HOVER, hv), a));

            float stripX = px + 5, stripY = py - lift + 5, stripW = tw - 10, stripH = 22;
            int steps = 12;
            for (int s = 0; s < steps; s++) {
                Color c0 = new Color(Themes.sample(preset, s / (double) steps * 0.5));
                Color c1 = new Color(Themes.sample(preset, (s + 1) / (double) steps * 0.5));
                Glyphs.gradient(stripX + stripW * s / steps, stripY, stripW / steps + 0.5f, stripH, alpha(c0, a), alpha(c1, a));
            }
            stringMid(small, trim(small, preset.toString(), tw - 22), px + 6, py - lift + 28, 15, chosen ? TEXT : TEXT_2, a);
            if (chosen) {
                Glyphs.check(px + tw - 10, py - lift + 35.5f, 3.4f, 1.2f, 1f, alpha(accent, a));
                frame(px, py - lift, tw, th, alpha(accent, 0.8f * a));
            }
            hit(px, py - lift, tw, th, b -> Themes.select(preset));
        }
        int rows = (presets.length + columns - 1) / columns;
        y += rows * (th + gap) - gap + 14;

        rip.tempest.modules.impl.visuals.ArrayListModule list = Tempest.INSTANCE.getModuleManager().getModule(rip.tempest.modules.impl.visuals.ArrayListModule.class);
        if (list == null) return y - 14;
        string(small, "ARRAY LIST PATTERNS", cardX + 2, y, TEXT_3, a);
        y += 12;
        rip.tempest.utils.render.ArrayPattern[] patterns = rip.tempest.utils.render.ArrayPattern.values();
        double t = list.patternTime();
        for (int i = 0; i < patterns.length; i++) {
            rip.tempest.utils.render.ArrayPattern pattern = patterns[i];
            float px = cardX + (i % columns) * (tw + gap), py = y + (i / columns) * (th + gap);
            if (py > bodyY + bodyH + 10 || py + th < bodyY - 10) continue;
            boolean hover = over(px, py, tw, th);
            boolean chosen = list.pattern.getValue() == pattern;
            float hv = anim("pt:" + pattern, hover ? 1f : 0f, FAST);
            float lift = 1.5f * hv;
            round(px, py - lift, tw, th, CARD_RADIUS, alpha(mix(CARD, CARD_HOVER, hv), a));

            float stripX = px + 5, stripY = py - lift + 5, stripW = tw - 10, stripH = 22;
            if (pattern != rip.tempest.utils.render.ArrayPattern.Off) {
                float block = 3;
                for (float bx = 0; bx < stripW; bx += block) {
                    for (float by = 0; by < stripH; by += block) {
                        int c = pattern.color(bx + block / 2, by + block / 2, stripW, stripH, t, list.motion());
                        Glyphs.bar(stripX + bx, stripY + by, Math.min(block, stripW - bx), Math.min(block, stripH - by), alpha(new Color(c & 0xFFFFFF), a));
                    }
                }
            } else {
                Glyphs.bar(stripX, stripY, stripW, stripH, alpha(new Color(40, 40, 46), a));
            }
            String label = pattern == rip.tempest.utils.render.ArrayPattern.Off ? "None" : pattern.toString();
            stringMid(small, trim(small, label, tw - 22), px + 6, py - lift + 28, 15, chosen ? TEXT : TEXT_2, a);
            if (chosen) {
                Glyphs.check(px + tw - 10, py - lift + 35.5f, 3.4f, 1.2f, 1f, alpha(accent, a));
                frame(px, py - lift, tw, th, alpha(accent, 0.8f * a));
            }
            hit(px, py - lift, tw, th, b -> list.pattern.setValue(pattern));
        }
        int patternRows = (patterns.length + columns - 1) / columns;
        return y + patternRows * (th + gap) - gap;
    }

    private float drawConfigs(float x, float top, float w, float a, Color accent) {
        if (configs == null) configs = LocalConfigFetcher.fetchConfigList();
        float cardX = x + PAD, cardW = w - PAD * 2;
        float y = top;

        float boxH = 40;
        round(cardX, y, cardW, boxH, CARD_RADIUS, alpha(CARD, a));
        float fw = cardW - 22 - 70, fh = 20, fx = cardX + 11, fy = y + 10;
        boolean fHover = over(fx, fy, fw, fh);
        float focus = anim("cfgF", configFieldFocused ? 1f : 0f, FAST);
        round(fx, fy, fw, fh, 0, alpha(fHover || configFieldFocused ? FIELD_HOVER : FIELD, a));
        frame(fx, fy, fw, fh, alpha(EDGE, a));
        if (focus > 0.01f) frame(fx, fy, fw, fh, alpha(accent, focus * a));
        if (configName.isEmpty() && !configFieldFocused) stringMid(small, "Config name", fx + 7, fy, fh, TEXT_3, a);
        else {
            String shown = trim(small, configName, fw - 14);
            stringMid(small, shown, fx + 7, fy, fh, TEXT, a);
            if (configFieldFocused && caretOn()) Glyphs.bar(fx + 8 + small.getStringWidth(shown), fy + 5, 1, fh - 10, alpha(accent, a));
        }
        hit(fx, fy, fw, fh, b -> {
            configFieldFocused = true;
            searchFocused = false;
            editing = null;
        });
        button("new", "Save", fx + fw + 8, fy, 62, fh, a, accent, true, () -> {
            String name = configName.trim();
            if (name.isEmpty()) return;
            boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(name);
            status(ok ? "Saved " + name : "Couldn't save " + name);
            configName = "";
            configs = null;
        });
        y += boxH + 12;

        string(small, "SAVED CONFIGS", cardX + 2, y, TEXT_3, a);
        if (!configStatus.isEmpty() && System.currentTimeMillis() - configStatusAt < 3000) {
            float sa = clamp((3000 - (System.currentTimeMillis() - configStatusAt)) / 400f);
            string(small, configStatus, cardX + cardW - small.getStringWidth(configStatus) - 2, y, accent.getRGB() & 0xFFFFFF, a * sa);
        }
        y += 12;

        if (configs.isEmpty()) {
            string(body, "No configs saved yet", cardX + 4, y + 4, TEXT_3, a);
            return y + 24;
        }
        float rowH = 28;
        for (String name : configs) {
            boolean hover = over(cardX, y, cardW, rowH);
            float hv = anim("cfg:" + name, hover ? 1f : 0f, FAST);
            round(cardX, y, cardW, rowH, CARD_RADIUS, alpha(mix(CARD, CARD_HOVER, hv), a));
            stringMid(body, trim(body, name, cardW - 200), cardX + 11 + 2 * hv, y, rowH, mixRgb(TEXT_2, TEXT, hv), a);
            float bw = 54, bh = 18, by = y + (rowH - bh) / 2f;
            float bx = cardX + cardW - 9 - bw;
            boolean confirming = name.equals(confirmDelete);
            button("del:" + name, confirming ? "Sure?" : "Delete", bx, by, bw, bh, a * (0.6f + 0.4f * hv), new Color(235, 90, 90), confirming, () -> {
                if (name.equals(armedDelete)) {
                    boolean ok = Tempest.INSTANCE.getConfigManager().deleteConfig(name);
                    status(ok ? "Deleted " + name : "Couldn't delete " + name);
                    confirmDelete = null;
                    configs = null;
                } else confirmDelete = name;
            });
            bx -= bw + 6;
            button("save:" + name, "Save", bx, by, bw, bh, a * (0.6f + 0.4f * hv), accent, false, () -> {
                boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(name);
                status(ok ? "Saved " + name : "Couldn't save " + name);
            });
            bx -= bw + 6;
            button("load:" + name, "Load", bx, by, bw, bh, a * (0.6f + 0.4f * hv), accent, true, () -> {
                boolean ok = LocalConfigFetcher.loadConfig(name);
                status(ok ? "Loaded " + name : "Couldn't load " + name);
            });
            y += rowH + 6;
        }
        return y - 6;
    }

    private void status(String text) {
        configStatus = text;
        configStatusAt = System.currentTimeMillis();
    }

    private void button(String key, String label, float x, float y, float w, float h, float a, Color accent, boolean filled, Runnable action) {
        boolean hover = over(x, y, w, h);
        float hv = anim("btn:" + key, hover ? 1f : 0f, FAST);
        if (filled) round(x, y, w, h, 5, alpha(accent, (0.75f + 0.25f * hv) * a));
        else {
            round(x, y, w, h, 5, alpha(mix(FIELD, FIELD_HOVER, hv), a));
            frame(x, y, w, h, alpha(accent, (0.4f + 0.5f * hv) * a));
        }
        int rgb = filled ? (luminance(accent) > 0.62f ? 0x111216 : 0xFFFFFF) : TEXT;
        string(small, label, x + (w - small.getStringWidth(label)) / 2f, y + (h - small.getHeight()) / 2f, rgb, a);
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
        float show = System.currentTimeMillis() - hoverSince > 450 ? 1f : 0f;
        float t = anim("tip", show, MEDIUM);
        if (t < 0.02f) return;
        float tw = small.getStringWidth(hoverText) + 12, th = 16;
        float tx = Math.min(mouseX + 10, width - tw - 4), ty = mouseY + 12 + (1f - t) * 3f;
        round(tx, ty, tw, th, 5, alpha(new Color(8, 8, 11, 240), t * a));
        frame(tx, ty, tw, th, alpha(EDGE, t * a));
        stringMid(small, hoverText, tx + 6, ty, th, TEXT_2, t * a);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (closing) return;
        mouseX = mx;
        mouseY = my;
        boolean wasBinding = binding != null;

        armedDelete = confirmDelete;
        confirmDelete = null;

        Hit target = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx <= h.x + h.w && my >= h.y && my <= h.y + h.h) {
                target = h;
                break;
            }
        }

        searchFocused = false;
        configFieldFocused = false;
        if (editing != null) commitEdit();
        if (wasBinding && target != null) binding = null;

        if (target != null) target.action.accept(button);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        dragSlider = null;
        dragWindow = false;
        dragScrollbar = false;
        if (dragPicker != null) {
            dragPicker.mouseReleased();
            dragPicker = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || closing) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (mx < bodyX || mx > bodyX + bodyW || my < bodyY || my > bodyY + bodyH) return;
        String key = scrollKey();
        float target = scrollTarget.getOrDefault(key, 0f) - Math.signum(wheel) * 46f;
        scrollTarget.put(key, Math.max(0, Math.min(maxScroll(), target)));
        anims.put("barRecent", 1f);
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

        if (searchFocused || configFieldFocused || editing != null) {
            if (press) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                    if (editing != null) {
                        if (keyCode == Keyboard.KEY_RETURN) commitEdit();
                        else editing = null;
                    } else if (configFieldFocused && keyCode == Keyboard.KEY_RETURN && !configName.trim().isEmpty()) {
                        boolean ok = Tempest.INSTANCE.getConfigManager().saveConfig(configName.trim());
                        status(ok ? "Saved " + configName.trim() : "Couldn't save " + configName.trim());
                        configName = "";
                        configs = null;
                    }
                    searchFocused = false;
                    configFieldFocused = false;
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
            if (typedChar != 0 && ChatAllowedCharacters.isAllowedCharacter(typedChar) && !isCtrlKeyDown()) {
                edit(s -> s + typedChar);
            }
            return;
        }

        if (!press) return;
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_F) {
            searchFocused = true;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == ClientSettingsModule.menuKey.getValue().intValue()) {
            closing = true;
        }
    }

    private void edit(java.util.function.UnaryOperator<String> change) {
        if (searchFocused) {
            String before = search;
            search = change.apply(search);
            if (!search.equals(before)) {
                scrollTarget.put("search", 0f);
                if (before.isEmpty() || search.isEmpty()) anims.put("page", 0f);
            }
        } else if (configFieldFocused) {
            configName = change.apply(configName);
        } else if (editing != null) {
            editBuffer = change.apply(editBuffer);
        }
    }
}
