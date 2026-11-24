package rip.tempest.interfaces.click.bankroll;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;

public final class BankrollUi {
    public static final int WINDOW_BG = rgb(9, 9, 9);
    public static final int BORDER_DARK = rgb(7, 7, 7);
    public static final int BORDER_LIGHT = rgb(24, 24, 24);
    public static final int TITLE_TOP = rgb(24, 24, 24);
    public static final int TITLE_BOTTOM = rgb(10, 10, 10);
    public static final int TABBAR_BG = rgb(16, 16, 16);
    public static final int GROUP_LIGHT = rgb(23, 23, 23);
    public static final int GROUP_DARK = rgb(0, 0, 0);
    public static final int FRAME_BORDER = rgb(0, 0, 0);
    public static final int FRAME_TOP = rgb(24, 24, 24);
    public static final int FRAME_BOTTOM = rgb(16, 16, 16);
    public static final int SLIDER_TOP = rgb(24, 24, 24);
    public static final int SLIDER_BOTTOM = rgb(14, 14, 14);
    public static final int BOX_OFF_TOP = rgb(24, 24, 24);
    public static final int BOX_OFF_BOTTOM = rgb(13, 13, 13);
    public static final int BUTTON_TOP = rgb(24, 24, 24);
    public static final int BUTTON_BOTTOM = rgb(15, 15, 15);
    public static final int FIELD_BG = rgb(16, 16, 16);
    public static final int POPUP_TOP = rgb(24, 24, 24);
    public static final int POPUP_BOTTOM = rgb(15, 15, 15);
    public static final int PICKER_BORDER = rgb(24, 24, 24);
    public static final int PICKER_BG = rgb(17, 17, 17);
    public static final int TEXT = rgb(148, 148, 148);
    public static final int TEXT_ACTIVE = rgb(245, 245, 245);
    public static final int TEXT_TITLE = rgb(255, 255, 255);
    public static final int TEXT_WARNING = rgb(196, 160, 128);
    public static final int SEPARATOR = rgb(67, 67, 67);

    public static final int DEFAULT_MENU_COLOR = rgb(236, 184, 239);

    private int boxOnTop, boxOnBottom, textAccent, accent;
    private int glow22, glow24, glow25, glow396, glow398, glow399;

    public static final float ORIGINAL_W = 460.0f, ORIGINAL_H = 420.0f;
    private float windowW = ORIGINAL_W, windowH = ORIGINAL_H;

    private static final float COMBO_W = 139.0f, COMBO_H = 23.0f;
    private static final float ROW_STEP = 20.0f, COMBO_STEP = 34.0f, LABEL_STEP = 14.0f, SLIDER_STEP = 35.0f, BUTTON_STEP = 30.0f;
    private static final float ITEM_H = 15.0f;
    private static final int DROPDOWN_ROWS = 10;

    private static final float PICKER_W = 179.0f, PICKER_H = 193.0f;
    private static final float SV_SIZE = 147.0f, HUE_W = 14.0f, PICKER_PAD = 6.0f, PICKER_GAP = 7.0f, ALPHA_H = 8.0f;

    private static final int[][] CHEVRON = {
            {0, 143, 0, 0, 0, 143, 0},
            {255, 255, 153, 0, 145, 255, 255},
            {82, 255, 250, 148, 255, 250, 74},
            {0, 77, 255, 255, 255, 77, 0},
            {0, 0, 74, 252, 74, 0, 0},
            {0, 0, 0, 69, 0, 0, 0},
    };
    private static final int[][] PLUS = {
            {0, 0, 150, 71, 0, 0},
            {0, 0, 150, 64, 0, 0},
            {163, 227, 245, 237, 224, 89},
            {0, 0, 153, 66, 0, 0},
            {0, 0, 133, 59, 0, 0},
    };
    private static final int[][] MINUS = {{51, 230, 237, 163}};

    private final BankrollFont font;

    private final DrawList mainList = new DrawList();
    private final List<DrawList> childLists = new ArrayList<>();
    private int childCount;
    private final DrawList popupList = new DrawList();
    private DrawList dl = mainList;

    private float mouseX = -1e6f, mouseY = -1e6f;
    private boolean mouseDown, mouseClicked, mouseReleased;
    private float wheel;

    private static final int DROPDOWN_ROWS_PER_NOTCH = 3;
    private int displayW = 1, displayH = 1;
    private long frameTime;

    private String activeId;
    private boolean activeSeen;
    private boolean inPopup;
    private String openPopup, openedThisFrame;
    private boolean popupSeen, closePopupAtEnd;

    private boolean popupRectValid;
    private float popX0, popY0, popX1, popY1;
    private float curPopX0, curPopY0, curPopX1, curPopY1;
    private boolean moving;
    private float moveOffX, moveOffY;
    private float windowX = 100, windowY = 100;
    private boolean windowPlaced;

    private long lastNanos;
    private float dt;
    private boolean animations = true;
    private final Map<String, float[]> anims = new HashMap<>();
    private float alpha = 1;
    private float popupOpenT = 1;

    private final ArrayDeque<String> idStack = new ArrayDeque<>();
    private String idPrefix = "";

    private float curX, curY, lineStartX;
    private float groupX0, groupY0, groupX1, groupY1;
    private float rowY;
    private boolean hasRow, groupScrollable;

    private final Map<String, Integer> listScroll = new HashMap<>();
    private final Map<String, float[]> pickerAnchor = new HashMap<>();
    private final Map<String, float[]> lastRects = new HashMap<>();
    private int popupFirst;
    private float savedHue = -1, savedSat = -1;

    private String textId, pendingFocus;
    private final StringBuilder textBuffer = new StringBuilder();
    private String textInitial = "";
    private int textCaret, textMax;
    private float textScroll;
    private long textBlinkReset;

    public BankrollUi(BankrollFont font) {
        this.font = font;
        setMenuColor(DEFAULT_MENU_COLOR);
    }

    public BankrollFont font() {
        return font;
    }

    public int displayWidth() {
        return displayW;
    }

    public int displayHeight() {
        return displayH;
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int alphaOf(int c) {
        return c >>> 24;
    }

    private static int withAlpha(int c, float a) {
        return c & 0x00FFFFFF | Math.round(a * 255.0f) << 24;
    }

    private static int scale(int c, float k) {
        int r = toByte((c >> 16 & 255) / 255.0f * k), g = toByte((c >> 8 & 255) / 255.0f * k), b = toByte((c & 255) / 255.0f * k);
        return c & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int toByte(float v) {
        return (int) (Math.max(0.0f, Math.min(1.0f, v)) * 255.0f + 0.5f);
    }

    public static int toColor(float[] rgba, boolean opaque) {
        return (opaque ? 255 : toByte(rgba[3])) << 24 | toByte(rgba[0]) << 16 | toByte(rgba[1]) << 8 | toByte(rgba[2]);
    }

    public void setMenuColor(int color) {
        float r = (color >> 16 & 255) / 236.0f, g = (color >> 8 & 255) / 184.0f, b = (color & 255) / 239.0f;
        boxOnTop = tint(236, 184, 239, r, g, b);
        boxOnBottom = tint(146, 108, 148, r, g, b);
        textAccent = tint(204, 156, 212, r, g, b);
        accent = tint(220, 190, 222, r, g, b);
        glow22 = tint(17, 6, 18, r, g, b);
        glow24 = tint(31, 1, 33, r, g, b);
        glow25 = tint(17, 6, 17, r, g, b);
        glow396 = tint(17, 6, 17, r, g, b);
        glow398 = tint(38, 8, 40, r, g, b);
        glow399 = tint(22, 13, 23, r, g, b);
    }

    private static int tint(int red, int green, int blue, float r, float g, float b) {
        return rgb(Math.min(255, Math.round(red * r)), Math.min(255, Math.round(green * g)), Math.min(255, Math.round(blue * b)));
    }

    static int lerpColor(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    public void setAnimations(boolean on) {
        animations = on;
    }

    public float animate(String key, float target, float speed) {
        float[] v = anims.get(key);
        if (v == null) {
            anims.put(key, new float[]{target});
            return target;
        }
        if (!animations) {
            v[0] = target;
            return target;
        }
        v[0] += (target - v[0]) * (1 - (float) Math.exp(-speed * dt));
        if (Math.abs(target - v[0]) < 0.001f) v[0] = target;
        return v[0];
    }

    public void resetAnimation(String key, float value) {
        anims.computeIfAbsent(key, k -> new float[1])[0] = value;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0, Math.min(1, alpha));
        mainList.alpha = this.alpha;
        popupList.alpha = this.alpha;
        for (DrawList list : childLists) list.alpha = this.alpha;
    }

    public float alpha() {
        return alpha;
    }

    public void newFrame(int displayWidth, int displayHeight, float mx, float my, boolean down, boolean clicked, boolean released, float wheelNotches) {
        displayW = Math.max(1, displayWidth);
        displayH = Math.max(1, displayHeight);
        mouseX = mx;
        mouseY = my;
        mouseDown = down;
        mouseClicked = clicked;
        mouseReleased = released;
        wheel = wheelNotches;
        frameTime = System.currentTimeMillis();
        long now = System.nanoTime();
        dt = lastNanos == 0 ? 0 : Math.max(0, Math.min(0.1f, (now - lastNanos) / 1e9f));
        lastNanos = now;

        mainList.clear();
        for (DrawList list : childLists) list.clear();
        childCount = 0;
        popupList.clear();
        setAlpha(1);
        dl = mainList;
        mainList.resetClip(0, 0, displayW, displayH);
        popupList.resetClip(0, 0, displayW, displayH);

        activeSeen = false;
        popupSeen = false;
        closePopupAtEnd = false;
        openedThisFrame = null;
        inPopup = false;

        if (moving) {
            if (mouseDown) {
                windowX = mouseX - moveOffX;
                windowY = mouseY - moveOffY;
            } else {
                moving = false;
            }
        }
        clampWindow();

        if (mouseClicked && openPopup != null && !(popupRectValid && contains(popX0, popY0, popX1, popY1))) {
            closePopupAtEnd = true;
        }

        if (mouseClicked && textId != null) {
            float[] r = lastRects.get(textId);
            if (r == null || !contains(r[0], r[1], r[2], r[3])) textId = null;
        }
    }

    private void clampWindow() {
        windowX = Math.max(19 - windowW, Math.min(displayW - 19, windowX));
        windowY = Math.max(19 - windowH, Math.min(displayH - 19, windowY));
    }

    public void setWindowSize(float width, float height) {
        windowW = width;
        windowH = height;
    }

    public float windowWidth() {
        return windowW;
    }

    public float windowHeight() {
        return windowH;
    }

    public void placeWindowOnce(float x, float y) {
        if (!windowPlaced) {
            windowX = x;
            windowY = y;
            windowPlaced = true;
            clampWindow();
        }
    }

    public float windowX() {
        return (float) Math.floor(windowX);
    }

    public float windowY() {
        return (float) Math.floor(windowY);
    }

    public void beginWindow() {
        float x = windowX(), y = windowY();
        dl = mainList;
        mainList.resetClip(0, 0, displayW, displayH);
        mainList.pushClip(x, y, x + windowW, y + windowH, true);
    }

    public void endWindow() {
        mainList.popClip();
    }

    public void endFrame() {
        if (activeId != null && !activeSeen) activeId = null;
        if (mouseReleased) activeId = null;

        boolean overPopup = popupRectValid && contains(popX0, popY0, popX1, popY1);

        if (mouseClicked && activeId == null && !overPopup
                && contains(windowX(), windowY(), windowX() + windowW, windowY() + windowH)) {
            moving = true;
            moveOffX = mouseX - windowX;
            moveOffY = mouseY - windowY;
        }

        if (closePopupAtEnd && (openedThisFrame == null || !openedThisFrame.equals(openPopup))) openPopup = null;
        if (openPopup != null && !popupSeen) openPopup = null;
        if (openPopup != null) {
            popupRectValid = true;
            popX0 = curPopX0;
            popY0 = curPopY0;
            popX1 = curPopX1;
            popY1 = curPopY1;
        } else {
            popupRectValid = false;
            savedHue = savedSat = -1;
        }
        dl = mainList;
    }

    public void render(BankrollCanvas canvas) {
        mainList.replay(canvas, font);
        for (int i = 0; i < childCount; i++) childLists.get(i).replay(canvas, font);
        popupList.replay(canvas, font);
    }

    public boolean popupOpen() {
        return openPopup != null;
    }

    public void closePopup() {
        openPopup = null;
        popupRectValid = false;
    }

    public boolean isMoving() {
        return moving;
    }

    public float takeWheel() {
        float w = wheel;
        wheel = 0;
        return w;
    }

    public boolean mouseIn(float x0, float y0, float x1, float y1) {
        return contains(x0, y0, x1, y1);
    }

    public float mouseX() {
        return mouseX;
    }

    public float mouseY() {
        return mouseY;
    }

    public boolean clicked() {
        return mouseClicked;
    }

    public boolean area(String name, float x0, float y0, float x1, float y1) {
        return itemButton(name, x0, y0, x1, y1);
    }

    public boolean areaHovered() {
        return hovered;
    }

    public boolean dragArea(String name, float x0, float y0, float x1, float y1) {
        buttonBehavior(id(name), x0, y0, x1, y1);
        return held;
    }

    public float[] itemRect(String id) {
        return lastRects.get(id);
    }

    private void record(String id, float x0, float y0, float x1, float y1) {
        float[] r = lastRects.get(id);
        if (r == null) lastRects.put(id, r = new float[4]);
        r[0] = x0;
        r[1] = y0;
        r[2] = x1;
        r[3] = y1;
    }

    private boolean contains(float x0, float y0, float x1, float y1) {
        return mouseX >= x0 && mouseY >= y0 && mouseX < x1 && mouseY < y1;
    }

    public void pushId(String id) {
        idStack.push(idPrefix);
        idPrefix = idPrefix + "/" + id;
    }

    public void pushId(int id) {
        pushId(Integer.toString(id));
    }

    public void popId() {
        idPrefix = idStack.pop();
    }

    private String id(String name) {
        return idPrefix + "/" + name;
    }

    private boolean clipped(float x0, float y0, float x1, float y1) {
        float[] c = dl.clip;
        return x1 <= c[0] || y1 <= c[1] || x0 >= c[2] || y0 >= c[3];
    }

    private boolean hoverable(String id, float x0, float y0, float x1, float y1) {
        if (moving) return false;
        if (activeId != null && !activeId.equals(id)) return false;
        float[] c = dl.clip;
        if (!contains(Math.max(x0, c[0]), Math.max(y0, c[1]), Math.min(x1, c[2]), Math.min(y1, c[3]))) return false;
        if (inPopup) return contains(curPopX0, curPopY0, curPopX1, curPopY1);

        return openPopup == null || !popupRectValid;
    }

    private boolean hovered, held;

    private boolean buttonBehavior(String id, float x0, float y0, float x1, float y1) {
        record(id, x0, y0, x1, y1);
        hovered = held = false;
        if (clipped(x0, y0, x1, y1)) return false;
        hovered = hoverable(id, x0, y0, x1, y1);
        if (hovered && mouseClicked && !closePopupAtEnd) activeId = id;
        boolean pressed = false;
        if (id.equals(activeId)) {
            activeSeen = true;
            held = mouseDown;
            if (mouseReleased) {
                pressed = hovered;
                activeId = null;
            }
        }
        return pressed;
    }

    private boolean itemButton(String name, float x0, float y0, float x1, float y1) {
        return buttonBehavior(id(name), x0, y0, x1, y1);
    }

    private boolean isActive(String name) {
        return id(name).equals(activeId);
    }

    private void openPopup(String id) {
        openPopup = id;
        openedThisFrame = id;
        resetAnimation(id + "#open", 0);
        popupFirst = 0;
        savedHue = savedSat = -1;
    }

    private boolean beginPopup(String id, float x, float y, float w, float h) {
        if (!id.equals(openPopup)) return false;

        x = Math.max(0, Math.min(displayW - w, x));
        y = Math.max(0, Math.min(displayH - h, y));
        popupSeen = true;
        inPopup = true;
        curPopX0 = x;
        curPopY0 = y;
        curPopX1 = x + w;
        curPopY1 = y + h;
        dl = popupList;
        popupList.resetClip(0, 0, displayW, displayH);
        popupList.pushClip(x, y, x + w, y + h, true);

        popupOpenT = animate(id + "#open", 1, 18);
        popupList.alpha = alpha * popupOpenT;
        return true;
    }

    private void endPopup(DrawList back) {
        popupList.popClip();
        inPopup = false;
        dl = back;
    }

    private void fill(float x0, float y0, float x1, float y1, int c) {
        dl.quad(x0, y0, x1, y1, c, c, c, c);
    }

    public void fillRect(float x0, float y0, float x1, float y1, int color) {
        fill(x0, y0, x1, y1, color);
    }

    public void horizontalGradient(float x0, float y0, float x1, float y1, int left, int right) {
        dl.quad(x0, y0, x1, y1, left, right, right, left);
    }

    private void vGradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        dl.quad(x0, y0, x1, y1, top, top, bottom, bottom);
    }

    private void accentRow(float x0, float x1, float y, int center) {
        float mid = (float) Math.floor((x0 + x1) * 0.5f);
        int clear = withAlpha(center, 0.0f);
        dl.quad(x0, y, mid, y + 1, clear, center, center, clear);
        dl.quad(mid, y, x1, y + 1, center, clear, clear, center);
    }

    private void drawBitmap(float x, float y, int[][] bitmap, int color) {
        float a = alphaOf(color) / 255.0f;
        for (int r = 0; r < bitmap.length; r++) {
            for (int c = 0; c < bitmap[r].length; c++) {
                if (bitmap[r][c] != 0) fill(x + c, y + r, x + c + 1, y + r + 1, withAlpha(color, bitmap[r][c] / 255.0f * a));
            }
        }
    }

    private void drawChevron(float x, float y, int color) {
        drawBitmap(x, y, CHEVRON, color);
    }

    private void drawFrame(float x0, float y0, float x1, float y1, int top, int bottom) {
        fill(x0, y0, x1, y1, FRAME_BORDER);
        vGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, top, bottom);
    }

    public float textWidth(String text) {
        return font.width(text);
    }

    private float textWidth(String text, int start, int end) {
        return font.width(text, start, end);
    }

    public void textAtBaseline(float x, float baselineRow, int color, String text) {
        dl.text(font, x, baselineRow, color, text);
    }

    private void advance(float x, float y, float h) {
        curX = lineStartX;
        curY = y + h;
    }

    private float floorX() {
        return (float) Math.floor(curX);
    }

    private float floorY() {
        return (float) Math.floor(curY);
    }

    public float cursorY() {
        return curY;
    }

    public void drawWindowFrame(float x0, float y0, String title) {
        float x1 = x0 + windowW, y1 = y0 + windowH;

        float tabs = y1 - 22;

        vGradient(x0, y0, x1, y0 + 23, TITLE_TOP, TITLE_BOTTOM);
        fill(x0, y0 + 23, x1, tabs, WINDOW_BG);
        fill(x0, tabs, x1, y1, TABBAR_BG);

        fill(x0, y0 + 23, x0 + 1, tabs - 1, BORDER_DARK);
        fill(x0 + 1, y0 + 23, x0 + 2, tabs - 1, BORDER_LIGHT);
        fill(x1 - 2, y0 + 23, x1 - 1, tabs - 1, BORDER_LIGHT);
        fill(x1 - 1, y0 + 23, x1, tabs - 1, BORDER_DARK);

        accentRow(x0, x1, y0 + 22, glow22);
        accentRow(x0, x1, y0 + 23, accent);
        accentRow(x0, x1, y0 + 24, glow24);
        accentRow(x0, x1, y0 + 25, glow25);
        accentRow(x0, x1, tabs - 2, glow396);
        accentRow(x0, x1, tabs - 1, accent);
        accentRow(x0, x1, tabs, glow398);
        accentRow(x0, x1, tabs + 1, glow399);

        float tw = textWidth(title);
        textAtBaseline((float) Math.floor(x0 + (windowW - tw) * 0.5f), y0 + 14, TEXT_TITLE, title);
    }

    public int bottomTabs(float wx, float wy, String[] names, int active) {
        float y0 = wy + windowH - 22, tabW = windowW / names.length;
        for (int i = 0; i < names.length; i++) {
            float x0 = wx + Math.round(i * tabW), x1 = wx + Math.round((i + 1) * tabW);
            pushId(i);
            if (itemButton("##tab", x0, y0, x1, y0 + 22) && active != i) active = i;
            popId();
            if (i > 0) fill(x0, y0 + 5, x0 + 1, y0 + 17, SEPARATOR);
            float tw = textWidth(names[i]);
            float center = wx + (i + 0.5f) * tabW;
            float lit = animate(id("##tab" + i) + "#lit", i == active ? 1 : 0, 14);
            textAtBaseline((float) Math.floor(center - tw * 0.5f), y0 + 14, lerpColor(TEXT, TEXT_TITLE, lit), names[i]);
        }
        return active;
    }

    public int subTabs(float px, float py, String[] names, int active) {
        float[] offsets = {0.0f, 46.0f, 97.0f, 138.0f};
        float x = px;
        for (int i = 0; i < names.length; i++) {
            if (names.length == 4) x = px + offsets[i];
            float tw = textWidth(names[i]);
            pushId(i);
            if (itemButton("##subtab", x, py, x + tw, py + 12) && active != i) active = i;
            popId();
            textAtBaseline(x, py + 8, i == active ? TEXT_TITLE : TEXT, names[i]);
            x += Math.round(tw) + 11;
        }
        return active;
    }

    public void pushClip(float x0, float y0, float x1, float y1) {
        dl.pushClip(x0, y0, x1, y1, true);
    }

    public void popClip() {
        dl.popClip();
    }

    public void beginGroupBox(String title, float x, float y, float w, float h, boolean scrollable) {
        float px = (float) Math.floor(x), py = (float) Math.floor(y);
        float x1 = px + w, y1 = py + h;
        fill(px, py, x1, y1, GROUP_LIGHT);
        fill(px + 1, py + 1, x1 - 1, y1 - 1, GROUP_DARK);
        fill(px + 2, py + 2, x1 - 2, y1 - 2, WINDOW_BG);
        float tw = textWidth(title);
        textAtBaseline((float) Math.floor(px + (w - tw) * 0.5f), py + 3, TEXT, title);

        groupX0 = px;
        groupY0 = py;
        groupX1 = x1;
        groupY1 = y1;
        hasRow = false;
        groupScrollable = scrollable;

        float[] parentClip = mainList.clip;
        if (childCount == childLists.size()) childLists.add(new DrawList());
        DrawList child = childLists.get(childCount++);
        child.clear();
        child.alpha = alpha;
        child.resetClip(parentClip[0], parentClip[1], parentClip[2], parentClip[3]);
        child.pushClip(px + 2, py + 2, px + w - 2, py + h - 2, true);
        dl = child;
        pushId(title);

        lineStartX = px + 12;
        curX = px + 12;
        curY = py + 25;
    }

    public void endGroupBox() {
        popId();
        dl = mainList;
        if (groupScrollable) {
            fill(groupX1 - 7, groupY0 + 6, groupX1 - 3, groupY1 - 2, rgb(24, 24, 24));
            drawChevron(groupX1 - 20, groupY1 - 13, TEXT);
        }
        hasRow = false;
    }

    public boolean checkbox(String label, boolean value, int labelColor) {
        float cx = floorX(), cy = floorY();
        float tw = textWidth(label);
        if (itemButton(label, cx, cy, cx + 19 + tw, cy + 9)) value = !value;
        float on = animate(id(label) + "#on", value ? 1 : 0, 16);

        fill(cx, cy, cx + 9, cy + 9, FRAME_BORDER);
        vGradient(cx + 1, cy + 1, cx + 8, cy + 8, lerpColor(BOX_OFF_TOP, boxOnTop, on), lerpColor(BOX_OFF_BOTTOM, boxOnBottom, on));
        textAtBaseline(cx + 19, cy + 8, labelColor != 0 ? labelColor : lerpColor(TEXT, TEXT_ACTIVE, on), label);

        rowY = cy;
        hasRow = true;
        advance(cx, cy, ROW_STEP);
        return value;
    }

    public void label(String text) {
        float cx = floorX(), cy = floorY();
        textAtBaseline(cx + 19, cy + 7, TEXT_ACTIVE, text);
        advance(cx, cy, LABEL_STEP);
    }

    public void rowLabel(String text) {
        float cx = floorX(), cy = floorY();
        textAtBaseline(cx + 19, cy + 8, TEXT, text);
        rowY = cy;
        hasRow = true;
        advance(cx, cy, ROW_STEP);
    }

    public void swatchRow() {
        float cx = floorX(), cy = floorY();
        rowY = cy;
        hasRow = true;
        advance(cx, cy, ROW_STEP);
    }

    public void spacing(float px) {
        advance(curX, curY, px);
    }

    public int combo(String id, int current, String[] items) {
        int[] cur = {current};
        comboImpl(id, current >= 0 && current < items.length ? items[current] : "", items, cur, null);
        return cur[0];
    }

    public int multiCombo(String id, boolean[] selected, String[] items) {
        String preview = multiPreview(selected, items, 112.0f);
        int[] toggled = {-1};
        comboImpl(id, preview, items, toggled, selected);
        return toggled[0];
    }

    private String multiPreview(boolean[] selected, String[] items, float avail) {
        StringBuilder full = new StringBuilder();
        int n = 0;
        for (int i = 0; i < items.length; i++) {
            if (selected[i]) {
                if (n++ > 0) full.append(", ");
                full.append(items[i]);
            }
        }
        if (n == 0) return "-";
        if (textWidth(full.toString()) <= avail) return full.toString();
        String out = "";
        for (int i = 0, k = 0; i < items.length; i++) {
            if (selected[i]) {
                String next = out + (k > 0 ? ", " : "") + items[i];
                if (textWidth(next + "...") > avail) break;
                out = next;
                k++;
            }
        }
        return out + "...";
    }

    private void comboImpl(String name, String preview, String[] items, int[] current, boolean[] selected) {
        float cx = floorX(), cy = floorY();
        float x0 = cx + 20, y0 = cy, x1 = cx + 20 + COMBO_W, y1 = cy + COMBO_H;
        pushId(name);
        String popupId = id("##dropdown");
        if (itemButton("##frame", x0, y0, x1, y1)) {
            if (comboOpenHook != null) comboOpenHook.run();
            openPopup(popupId);
        }
        comboOpenHook = null;
        record(idPrefix, x0, y0, x1, y1);
        drawFrame(x0, y0, x1, y1, FRAME_TOP, FRAME_BOTTOM);
        dl.pushClip(x0, y0, x0 + 111, y1, true);
        textAtBaseline(x0 + 8, y0 + 15, TEXT, preview);
        dl.popClip();
        drawChevron(x0 + 123, y0 + 9, TEXT);
        dropdownList(popupId, x0, y0, x1, y1, items, current, selected);
        popId();
        advance(cx, cy, COMBO_STEP);
    }

    private Runnable comboOpenHook;

    public void onNextComboOpen(Runnable hook) {
        comboOpenHook = hook;
    }

    private void dropdownList(String popupId, float fx0, float fy0, float fx1, float fy1, String[] items, int[] current, boolean[] selected) {
        int count = items.length;
        int rows = Math.min(count, DROPDOWN_ROWS);
        float w = fx1 - fx0, h = 5.0f + rows * ITEM_H;
        float py0 = fy1;

        if (py0 + h > displayH && fy0 - h >= 0) py0 = fy0 - h;
        DrawList back = dl;
        if (!beginPopup(popupId, fx0, py0, w, h)) return;
        float px = (float) Math.floor(curPopX0), py = (float) Math.floor(curPopY0);

        float shown = (float) Math.ceil(h * popupOpenT);
        boolean upwards = py < fy0;
        dl.pushClip(px, upwards ? py + h - shown : py, px + w, upwards ? py + h : py + shown, true);
        fill(px, py, px + w, py + h, FRAME_BORDER);
        vGradient(px + 1, py, px + w - 1, py + h - 1, POPUP_TOP, POPUP_BOTTOM);

        int maxFirst = Math.max(0, count - rows);

        if (popupId.equals(openedThisFrame) && selected == null && current[0] >= rows) popupFirst = current[0] - (rows - 1) / 2;
        if (wheel != 0 && contains(px, py, px + w, py + h) && maxFirst > 0) {
            int rowsMoved = Math.round(wheel * DROPDOWN_ROWS_PER_NOTCH);
            if (rowsMoved == 0) rowsMoved = (int) Math.signum(wheel);
            popupFirst = Math.max(0, Math.min(maxFirst, popupFirst - rowsMoved));
            wheel = 0;
        }
        popupFirst = Math.max(0, Math.min(maxFirst, popupFirst));
        boolean scrolls = count > rows;

        for (int r = 0; r < rows; r++) {
            int i = popupFirst + r;
            float y = py + 3 + r * ITEM_H;
            pushId(i);
            if (itemButton("##item", px + 1, y, px + w - (scrolls ? 8 : 1), y + ITEM_H)) {
                if (selected != null) selected[i] = !selected[i];
                current[0] = i;
            }
            popId();
            boolean sel = selected != null ? selected[i] : current[0] == i;
            textAtBaseline(px + 8, y + 11, sel ? textAccent : TEXT, items[i]);
        }
        if (scrolls) {
            float tx0 = px + w - 5, ty0 = py + 1, tx1 = px + w, ty1 = py + h - 1;
            fill(tx0, ty0, tx1, ty1, rgb(23, 23, 23));
            float len = (float) Math.floor((ty1 - ty0 - 6) * rows / count);
            float off = (float) Math.floor((ty1 - ty0 - 6 - len) * popupFirst / maxFirst);
            fill(tx0, ty0 + 6 + off, tx1, ty0 + 6 + off + len, rgb(36, 36, 36));
        }
        dl.popClip();
        endPopup(back);
    }

    public double slider(String label, double value, double min, double max, double step,
                         DoubleUnaryOperator snap, DoubleFunction<String> format) {
        float cx = floorX(), cy = floorY();
        pushId(label);

        float x0 = cx + 20, y0 = cy + 14, x1 = cx + 20 + COMBO_W, y1 = cy + 24;
        buttonBehavior(id("##slider"), x0, y0, x1, y1);
        boolean dragging = held;
        if (dragging) {
            float t = Math.max(0.0f, Math.min(1.0f, (mouseX - x0 - 1) / (x1 - x0 - 2)));
            value = snap.applyAsDouble(min + t * (max - min));
        }

        if (itemButton("##minus", cx + 10, y0, cx + 18, y1)) value = Math.max(min, snap.applyAsDouble(value - step));
        if (itemButton("##plus", x1 + 4, y0, x1 + 13, y1)) value = Math.min(max, snap.applyAsDouble(value + step));

        drawFrame(x0, y0, x1, y1, SLIDER_TOP, SLIDER_BOTTOM);
        float t = max > min ? (float) Math.max(0.0, Math.min(1.0, (value - min) / (max - min))) : 0.0f;

        float shown = animate(id("##slider") + "#t", t, dragging ? 40 : 16);
        float fill = (float) Math.floor((x1 - x0 - 2) * shown);
        if (fill > 0) vGradient(x0 + 1, y0 + 1, x0 + 1 + fill, y1 - 1, boxOnTop, boxOnBottom);
        drawBitmap(x0 - 8, y0 + 5, MINUS, TEXT);
        drawBitmap(x1 + 5, y0 + 3, PLUS, TEXT);

        String text = format.apply(value);
        float valueX = x1 - textWidth(text);
        dl.pushClip(cx, cy - 4, valueX - 5, cy + 13, true);
        textAtBaseline(cx + 19, cy + 7, TEXT, label);
        dl.popClip();
        textAtBaseline(valueX, cy + 7, TEXT, text);

        popId();
        advance(cx, cy, SLIDER_STEP);
        return value;
    }

    public boolean colorSwatch(String name, float[] rgba, int slot) {
        if (!hasRow) return false;
        float x0 = groupX1 - 31 - 27.0f * slot, y0 = rowY;
        float bx1 = x0 + 17, by1 = y0 + 9;
        int c = toColor(rgba, true);

        pushId(name);
        String key = id("##anchor");
        if (itemButton("##swatch", x0, y0, bx1, by1)) {
            pickerAnchor.put(key, new float[]{(float) Math.floor(mouseX), (float) Math.floor(mouseY)});
            openPopup(id("##picker"));
        }
        record(idPrefix, x0, y0, bx1, by1);

        fill(x0 - 1, y0 - 1, bx1 + 1, by1 + 1, withAlpha(c, 0.06f));
        fill(x0, y0, bx1, by1, FRAME_BORDER);
        fill(x0 + 1, y0 + 1, bx1 - 1, y0 + 2, c);
        vGradient(x0 + 1, y0 + 2, bx1 - 1, by1 - 1, scale(c, 0.90f), scale(c, 0.68f));

        float[] anchor = pickerAnchor.get(key);
        boolean changed = colorPickerPopup(id("##picker"), rgba, anchor != null ? anchor[0] : bx1, anchor != null ? anchor[1] : y0);
        popId();
        return changed;
    }

    private boolean colorPickerPopup(String popupId, float[] rgba, float ax, float ay) {
        DrawList back = dl;
        if (!beginPopup(popupId, ax, ay, PICKER_W, PICKER_H)) return false;
        boolean changed = false;
        float px = curPopX0, py = curPopY0;
        fill(px, py, px + PICKER_W, py + PICKER_H, PICKER_BORDER);
        fill(px + 1, py + 1, px + PICKER_W - 1, py + PICKER_H - 1, PICKER_BG);

        float[] hsv = rgbToHsv(rgba[0], rgba[1], rgba[2]);
        float h = hsv[0], s = hsv[1], v = hsv[2];

        if ((s == 0 || v == 0) && savedHue >= 0) h = savedHue;
        if (v == 0 && savedSat >= 0) s = savedSat;

        float sx0 = px + PICKER_PAD, sy0 = py + PICKER_PAD, sx1 = sx0 + SV_SIZE, sy1 = sy0 + SV_SIZE;
        float[] hue = hsvToRgb(h, 1.0f, 1.0f);
        int hueCol = toColor(new float[]{hue[0], hue[1], hue[2], 1.0f}, true);
        int white = 0xFFFFFFFF, black = 0xFF000000, clear = 0x00000000;
        dl.quad(sx0, sy0, sx1, sy1, white, hueCol, hueCol, white);
        dl.quad(sx0, sy0, sx1, sy1, clear, clear, black, black);
        if (itemButton("##sv", sx0, sy0, sx1, sy1) || (isActive("##sv") && mouseDown)) {
            s = Math.max(0.0f, Math.min(1.0f, (mouseX - sx0) / SV_SIZE));
            v = 1.0f - Math.max(0.0f, Math.min(1.0f, (mouseY - sy0) / SV_SIZE));
            changed = true;
        }
        float kx = (float) Math.floor(sx0 + s * (SV_SIZE - 1)), ky = (float) Math.floor(sy0 + (1.0f - v) * (SV_SIZE - 1));
        fill(kx - 2, ky - 2, kx + 2, ky + 2, rgb(0, 0, 0));
        fill(kx - 1, ky - 1, kx + 1, ky + 1, rgb(200, 200, 200));

        float hx0 = sx1 + PICKER_GAP, hy0 = sy0, hx1 = sx1 + PICKER_GAP + HUE_W, hy1 = sy1;
        int[] hues = {rgb(255, 0, 0), rgb(255, 255, 0), rgb(0, 255, 0), rgb(0, 255, 255), rgb(0, 0, 255), rgb(255, 0, 255), rgb(255, 0, 0)};
        float hh = hy1 - hy0;
        for (int i = 0; i < 6; i++) {
            float y0 = hy0 + hh * i / 6.0f, y1 = hy0 + hh * (i + 1) / 6.0f;
            dl.quad(hx0, y0, hx1, y1, hues[i], hues[i], hues[i + 1], hues[i + 1]);
        }
        if (itemButton("##hue", hx0, hy0, hx1, hy1) || (isActive("##hue") && mouseDown)) {
            h = Math.max(0.0f, Math.min(0.999f, (mouseY - hy0) / hh));
            changed = true;
        }
        float hy = (float) Math.floor(hy0 + h * (hh - 1));
        fill(hx0 - 1, hy - 1, hx1 + 1, hy + 2, rgb(0, 0, 0));
        fill(hx0, hy, hx1, hy + 1, rgb(220, 220, 220));

        float ax0 = sx0, ay0 = sy1 + PICKER_GAP, ax1 = sx1, ay1 = sy1 + PICKER_GAP + ALPHA_H;
        int solid = toColor(rgba, true);
        fill(ax0, ay0, ax1, ay1, solid);
        if (itemButton("##alpha", ax0, ay0, ax1, ay1) || (isActive("##alpha") && mouseDown)) {
            rgba[3] = Math.max(0.0f, Math.min(1.0f, (mouseX - ax0) / (ax1 - ax0)));
            changed = true;
        }
        float axm = (float) Math.floor(ax0 + rgba[3] * (ax1 - ax0 - 1));
        fill(axm - 1, ay0 - 1, axm + 2, ay1 + 1, rgb(0, 0, 0));
        fill(axm, ay0, axm + 1, ay1, rgb(220, 220, 220));
        fill(hx0, ay0, hx1, ay1, solid);

        float by0 = ay1 + 6, by1 = by0 + 14;
        if (letterButton("##copy", "C", hx1 - 33, by0, hx1 - 17, by1)) {
            rip.tempest.interfaces.click.ColorClipboard.copy(toColor(rgba, false));
        }
        if (letterButton("##paste", "P", hx1 - 16, by0, hx1, by1)) {
            Integer pasted = rip.tempest.interfaces.click.ColorClipboard.paste();
            if (pasted != null) {
                float[] from = rgbToHsv((pasted >> 16 & 255) / 255.0f, (pasted >> 8 & 255) / 255.0f, (pasted & 255) / 255.0f);
                h = from[0];
                s = from[1];
                v = from[2];
                rgba[3] = (pasted >>> 24) / 255.0f;
                changed = true;
            }
        }

        if (changed) {
            float[] out = hsvToRgb(h, s, v);
            rgba[0] = out[0];
            rgba[1] = out[1];
            rgba[2] = out[2];
            savedHue = h;
            savedSat = s;
        }
        endPopup(back);
        return changed;
    }

    static float[] rgbToHsv(float r, float g, float b) {
        float k = 0.0f;
        if (g < b) {
            float t = g;
            g = b;
            b = t;
            k = -1.0f;
        }
        if (r < g) {
            float t = r;
            r = g;
            g = t;
            k = -2.0f / 6.0f - k;
        }
        float chroma = r - (g < b ? g : b);
        return new float[]{Math.abs(k + (g - b) / (6.0f * chroma + 1e-20f)), chroma / (r + 1e-20f), r};
    }

    static float[] hsvToRgb(float h, float s, float v) {
        if (s == 0.0f) return new float[]{v, v, v};
        h = (h % 1.0f) / (60.0f / 360.0f);
        int i = (int) h;
        float f = h - i;
        float p = v * (1.0f - s), q = v * (1.0f - s * f), t = v * (1.0f - s * (1.0f - f));
        switch (i) {
            case 0: return new float[]{v, t, p};
            case 1: return new float[]{q, v, p};
            case 2: return new float[]{p, v, t};
            case 3: return new float[]{p, q, v};
            case 4: return new float[]{t, p, v};
            default: return new float[]{v, p, q};
        }
    }

    public boolean keybindLabel(String name, String text) {
        if (!hasRow) return false;
        float tw = textWidth(text);
        float x = groupX1 - 13 - tw;
        boolean pressed = itemButton(name, x, rowY - 1, x + tw, rowY + 9);
        textAtBaseline(x, rowY + 6, TEXT, text);
        return pressed;
    }

    public int listBox(String name, int current, String[] items, int visibleRows, boolean markers) {
        float cx = floorX(), cy = floorY();
        float x0 = cx + 19, y0 = cy - 1, x1 = cx + 19 + COMBO_W, y1 = cy + 6 + visibleRows * ITEM_H;
        pushId(name);

        String key = id("##scroll");
        int count = items.length;
        int first = listScroll.getOrDefault(key, 0);
        int maxFirst = Math.max(0, count - visibleRows);
        float[] c = dl.clip;
        if (wheel != 0 && contains(Math.max(x0, c[0]), Math.max(y0, c[1]), Math.min(x1, c[2]), Math.min(y1, c[3]))) {
            first -= (int) Math.signum(wheel);
            wheel = 0;
        }
        first = Math.max(0, Math.min(maxFirst, first));
        listScroll.put(key, first);

        fill(x0, y0, x1, y1, FRAME_BORDER);
        fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, rgb(16, 16, 16));

        for (int r = 0; r < visibleRows && first + r < count; r++) {
            int i = first + r;
            float y = y0 + 6 + r * ITEM_H;
            pushId(i);
            if (itemButton("##entry", x0 + 1, y, x1 - 8, y + ITEM_H)) current = i;
            popId();
            if (markers) fill(x0 + 6, y + 4, x0 + 8, y + 10, rgb(200, 64, 64));
            textAtBaseline(x0 + (markers ? 11 : 7), y + 10, i == current ? textAccent : TEXT, items[i]);
        }

        if (count > visibleRows) {
            float tx0 = x1 - 5, ty0 = y0 + 1, tx1 = x1, ty1 = y1 - 1;
            fill(tx0, ty0, tx1, ty1, rgb(23, 23, 23));
            float len = (float) Math.floor((ty1 - ty0 - 6) * visibleRows / count);
            float off = (float) Math.floor((ty1 - ty0 - 6 - len) * first / maxFirst);
            fill(tx0, ty0 + 6 + off, tx1, ty0 + 6 + off + len, rgb(36, 36, 36));
        }

        popId();
        advance(cx, cy, y1 + 10 - cy);
        return current;
    }

    public String textField(String name, String text, String hint, int maxLength) {
        float cx = floorX(), cy = floorY();
        float x0 = cx + 19, y0 = cy, x1 = cx + 19 + COMBO_W, y1 = cy + COMBO_H;
        fill(x0, y0, x1, y1, FRAME_BORDER);
        fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FIELD_BG);
        text = editText(id(name), x0, y0, x1, y1, 8, y0 + 15, text, hint, maxLength);
        advance(cx, cy, BUTTON_STEP);
        return text;
    }

    public String searchField(String name, String text, String hint, float x0, float y0, float x1, float y1, int maxLength) {
        fill(x0, y0, x1, y1, FRAME_BORDER);
        fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FIELD_BG);
        float baseline = (float) Math.floor(y0 + (y1 - y0) / 2.0f) + 3;
        return editText(id(name), x0, y0, x1, y1, 5, baseline, text, hint, maxLength);
    }

    public boolean clearButton(String name, float x0, float y0, float x1, float y1) {
        return letterButton(name, "x", x0, y0, x1, y1);
    }

    private boolean letterButton(String name, String letter, float x0, float y0, float x1, float y1) {
        boolean pressed = itemButton(name, x0, y0, x1, y1);
        fill(x0, y0, x1, y1, FRAME_BORDER);
        fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, hovered ? FRAME_TOP : FIELD_BG);
        float baseline = (float) Math.floor(y0 + (y1 - y0) / 2.0f) + 3;
        textAtBaseline((float) Math.floor(x0 + (x1 - x0 - textWidth(letter)) * 0.5f), baseline, hovered ? TEXT_ACTIVE : TEXT, letter);
        return pressed;
    }

    private String editText(String fid, float x0, float y0, float x1, float y1, float padX, float baseline,
                            String text, String hint, int maxLength) {
        if (fid.equals(revertPending)) {
            revertPending = null;
            text = textInitial;
        }
        record(fid, x0, y0, x1, y1);

        if (!clipped(x0, y0, x1, y1) && mouseClicked && !closePopupAtEnd && hoverable(fid, x0, y0, x1, y1)) {
            if (!fid.equals(textId)) {
                textId = fid;
                textBuffer.setLength(0);
                textBuffer.append(text);
                textInitial = text;
                textScroll = 0;
            }
            textMax = maxLength;
            textCaret = caretAt(mouseX - (x0 + padX) + textScroll);
            textBlinkReset = frameTime;
            activeId = fid;
            activeSeen = true;
        } else if (fid.equals(activeId)) {
            activeSeen = true;
        } else if (fid.equals(pendingFocus)) {
            textId = fid;
            textBuffer.setLength(0);
            textBuffer.append(text);
            textInitial = text;
            textScroll = 0;
            textMax = maxLength;
            textCaret = text.length();
            textBlinkReset = frameTime;
        }
        if (fid.equals(pendingFocus)) pendingFocus = null;

        boolean active = fid.equals(textId);
        if (active) {
            textMax = maxLength;
            text = textBuffer.toString();
            textCaret = Math.max(0, Math.min(text.length(), textCaret));

            float inner = x1 - x0 - padX * 2, caretX = textWidth(text, 0, textCaret), step = inner * 0.25f;
            if (caretX < textScroll) {
                textScroll = (float) Math.floor(Math.max(0.0f, caretX - step));
            } else if (caretX - inner >= textScroll) {
                textScroll = (float) Math.floor(caretX - inner + step);
            }
        }

        dl.pushClip(x0, y0, x1, y1, true);
        float drawX = x0 + padX - (active ? textScroll : 0);
        if (!text.isEmpty()) dl.text(font, drawX, baseline, TEXT, text);
        if (active) {
            float t = (frameTime - textBlinkReset) / 1000.0f - 0.30f;
            if (t <= 0.0f || t % 1.20f <= 0.80f) {
                float caretX = (float) Math.floor(drawX + textWidth(text, 0, textCaret));
                fill(caretX, baseline - 8, caretX + 1, baseline + 2, TEXT);
            }
        }
        dl.popClip();
        if (!active && text.isEmpty()) textAtBaseline(x0 + padX, baseline, TEXT, hint);
        return text;
    }

    private int caretAt(float x) {
        String text = textBuffer.toString();
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            float advance = font.glyph(text.charAt(i)).advance;
            if (x < width + advance * 0.5f) return i;
            width += advance;
        }
        return text.length();
    }

    public boolean typing() {
        return textId != null;
    }

    public boolean typingIn(String name) {
        return id(name).equals(textId);
    }

    public void focusField(String name) {
        pendingFocus = id(name);
    }

    public void clearTyped() {
        if (textId == null) return;
        textBuffer.setLength(0);
        textCaret = 0;
        textScroll = 0;
        textBlinkReset = System.currentTimeMillis();
    }

    public String typedText() {
        return textBuffer.toString();
    }

    public void stopTyping(boolean revert) {
        if (textId != null && revert) {
            textBuffer.setLength(0);
            textBuffer.append(textInitial);
            revertPending = textId;
        }
        textId = null;
    }

    private String revertPending;

    public void typeChar(char c) {
        if (textId == null || c < 32 || c == 127) return;
        if (textBuffer.length() >= textMax) return;
        textBuffer.insert(textCaret, c);
        textCaret++;
        textBlinkReset = System.currentTimeMillis();
    }

    public void paste(String clip) {
        if (clip == null) return;
        for (int i = 0; i < clip.length(); i++) typeChar(clip.charAt(i));
    }

    public void backspace() {
        if (textId == null || textCaret <= 0) return;
        textBuffer.deleteCharAt(textCaret - 1);
        textCaret--;
        textBlinkReset = System.currentTimeMillis();
    }

    public void delete() {
        if (textId == null || textCaret >= textBuffer.length()) return;
        textBuffer.deleteCharAt(textCaret);
        textBlinkReset = System.currentTimeMillis();
    }

    public void moveCaret(int delta, boolean toEnd) {
        if (textId == null) return;
        if (toEnd) {
            textCaret = delta < 0 ? 0 : textBuffer.length();
        } else {
            textCaret = Math.max(0, Math.min(textBuffer.length(), textCaret + delta));
        }
        textBlinkReset = System.currentTimeMillis();
    }

    public boolean button(String label) {
        float cx = floorX(), cy = floorY();
        float x0 = cx + 19, y0 = cy, x1 = cx + 19 + COMBO_W, y1 = cy + COMBO_H;
        boolean pressed = itemButton(label, x0, y0, x1, y1);
        drawFrame(x0, y0, x1, y1, BUTTON_TOP, BUTTON_BOTTOM);
        float tw = textWidth(label);
        textAtBaseline((float) Math.floor(x0 + (x1 - x0 - tw) * 0.5f), y0 + 15, TEXT, label);
        advance(cx, cy, BUTTON_STEP);
        return pressed;
    }

    private static final class DrawList {
        private static final int QUAD = 0, GLYPH = 1;

        float[] clip = new float[4];
        private final ArrayDeque<float[]> clipStack = new ArrayDeque<>();
        float alpha = 1;

        private int faded(int c) {
            return alpha >= 1 ? c : c & 0x00FFFFFF | Math.round((c >>> 24) * alpha) << 24;
        }

        private int count;
        private int[] kind = new int[1024];
        private float[] pos = new float[1024 * 4];
        private int[] colors = new int[1024 * 4];
        private int[] clips = new int[1024 * 4];
        private int[] tex = new int[1024 * 2];

        void clear() {
            count = 0;
            clipStack.clear();
        }

        void resetClip(float x0, float y0, float x1, float y1) {
            clipStack.clear();
            clip = new float[]{x0, y0, x1, y1};
        }

        void pushClip(float x0, float y0, float x1, float y1, boolean intersect) {
            clipStack.push(clip);
            if (intersect) {
                x0 = Math.max(x0, clip[0]);
                y0 = Math.max(y0, clip[1]);
                x1 = Math.min(x1, clip[2]);
                y1 = Math.min(y1, clip[3]);
            }
            clip = new float[]{x0, y0, Math.max(x0, x1), Math.max(y0, y1)};
        }

        void popClip() {
            clip = clipStack.pop();
        }

        private int add(int type) {
            if (count == kind.length) {
                int n = count * 2;
                kind = java.util.Arrays.copyOf(kind, n);
                pos = java.util.Arrays.copyOf(pos, n * 4);
                colors = java.util.Arrays.copyOf(colors, n * 4);
                clips = java.util.Arrays.copyOf(clips, n * 4);
                tex = java.util.Arrays.copyOf(tex, n * 2);
            }
            int i = count++;
            kind[i] = type;

            clips[i * 4] = (int) clip[0];
            clips[i * 4 + 1] = (int) clip[1];
            clips[i * 4 + 2] = (int) clip[2];
            clips[i * 4 + 3] = (int) clip[3];
            return i;
        }

        void quad(float x0, float y0, float x1, float y1, int tl, int tr, int br, int bl) {
            if (x1 <= x0 || y1 <= y0) return;
            if (x1 <= clip[0] || y1 <= clip[1] || x0 >= clip[2] || y0 >= clip[3]) return;
            if ((tl | tr | br | bl) >>> 24 == 0 || alpha <= 0) return;
            int i = add(QUAD);
            pos[i * 4] = x0;
            pos[i * 4 + 1] = y0;
            pos[i * 4 + 2] = x1;
            pos[i * 4 + 3] = y1;
            colors[i * 4] = faded(tl);
            colors[i * 4 + 1] = faded(tr);
            colors[i * 4 + 2] = faded(br);
            colors[i * 4 + 3] = faded(bl);
        }

        void text(BankrollFont font, float x, float baselineRow, int color, String text) {
            float tx = (float) Math.floor(x);
            float base = (float) Math.floor(baselineRow + 1);
            float cx0 = clip[0], cy0 = clip[1], cx1 = clip[2], cy1 = clip[3];
            for (int c = 0; c < text.length(); c++) {
                BankrollFont.Glyph g = font.glyph(text.charAt(c));
                if (g.width > 0 && g.height > 0) {
                    float gx = tx + g.left, gy = base - g.top;
                    if (!(gx + g.width <= cx0 || gy + g.height <= cy0 || gx >= cx1 || gy >= cy1)) {
                        int i = add(GLYPH);
                        pos[i * 4] = gx;
                        pos[i * 4 + 1] = gy;
                        pos[i * 4 + 2] = g.width;
                        pos[i * 4 + 3] = g.height;
                        colors[i * 4] = faded(color);
                        tex[i * 2] = g.x;
                        tex[i * 2 + 1] = g.y;
                    }
                }
                tx += g.advance;
                if (tx >= cx1) break;
            }
        }

        void replay(BankrollCanvas canvas, BankrollFont font) {
            for (int i = 0; i < count; i++) {
                int c0 = clips[i * 4], c1 = clips[i * 4 + 1], c2 = clips[i * 4 + 2], c3 = clips[i * 4 + 3];
                if (c2 <= c0 || c3 <= c1) continue;
                if (kind[i] == QUAD) {
                    canvas.quad(pos[i * 4], pos[i * 4 + 1], pos[i * 4 + 2], pos[i * 4 + 3],
                            colors[i * 4], colors[i * 4 + 1], colors[i * 4 + 2], colors[i * 4 + 3], c0, c1, c2, c3);
                } else {
                    canvas.glyph(font, (int) pos[i * 4], (int) pos[i * 4 + 1], (int) pos[i * 4 + 2], (int) pos[i * 4 + 3],
                            tex[i * 2], tex[i * 2 + 1], colors[i * 4], c0, c1, c2, c3);
                }
            }
        }
    }
}
