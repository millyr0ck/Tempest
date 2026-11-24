package rip.tempest.interfaces.click.bankroll;

import rip.tempest.Tempest;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.modules.impl.visuals.ArrayListModule;
import rip.tempest.utils.render.ArrayPattern;
import rip.tempest.processes.Themes;
import rip.tempest.processes.Themes.Direction;
import rip.tempest.processes.Themes.Preset;

import java.util.Locale;

final class BankrollThemesPage {
    private static final float PAGE_H = 613, GAP = 10;
    private static final float LEFT_W = 440, RIGHT_W = 215;
    private static final float WAVE_H = 226, CUSTOM_H = 100;
    private static final float PREVIEW_H = PAGE_H - WAVE_H - CUSTOM_H - GAP * 2;

    private static final int CARD_COLUMNS = 4;
    private static final float INSET = 12, BOX_TOP = 25, BOX_BOTTOM = 14, CARD_GAP = 6, CARD_H = 52, LABEL_H = 19;

    private static final float CLIP_TOP = 19, CLIP_BOTTOM = 8;

    private static final float ROW_STEP = CARD_H + CARD_GAP;

    private static final float TRACK_INSET_0 = 8, TRACK_INSET_1 = 4;

    private static final float STRIP_STEP = 6;

    private static final float PATTERN_HEADER = 22, PATTERN_BLOCK = 3;

    private static final int CARD_BORDER = BankrollUi.rgb(24, 24, 24), CARD_BORDER_HOT = BankrollUi.rgb(62, 62, 62),
            CARD_BG = BankrollUi.rgb(13, 13, 13), BLACK = BankrollUi.rgb(0, 0, 0),
            SCREEN_BG = BankrollUi.rgb(15, 15, 15), SEGMENT_ON = BankrollUi.rgb(34, 34, 34);

    private static final float PREVIEW_SCALE = 2;

    private static final String[] MOCK_MODULES = {"auto armor", "name tags", "kill aura", "velocity", "scaffold", "tracers", "stealer", "no slow", "sprint", "speed", "esp", "hud"};

    private Preset shownFrom = Preset.Ocean, shown = Preset.Ocean;
    private float blend = 1;

    private float scroll, scrollGrab;
    private boolean bringSelectedIntoView = true;

    private ArrayPattern hoveredPattern;

    void onOpen() {
        bringSelectedIntoView = true;
    }

    void draw(BankrollUi ui, float x, float y, BankrollMenu menu) {
        hoveredPattern = null;
        Preset hovered = drawPresets(ui, x, y);
        float rightX = x + LEFT_W + GAP;
        float waveY = y + PREVIEW_H + GAP, customY = waveY + WAVE_H + GAP;
        drawWave(ui, rightX, waveY);
        hovered = drawCustom(ui, rightX, customY, menu, hovered);

        ArrayPattern pattern = hoveredPattern != null ? hoveredPattern : hovered == null ? arrayList().pattern.getValue() : ArrayPattern.Off;
        drawPreview(ui, rightX, y, hovered != null ? hovered : Themes.current(), pattern);
    }

    private static ArrayListModule arrayList() {
        return Tempest.INSTANCE.getModuleManager().getModule(ArrayListModule.class);
    }

    private static double patternTime() {
        return System.nanoTime() / 1.0e9;
    }

    private Preset drawPresets(BankrollUi ui, float x, float y) {
        int cards = Preset.values().length - 1;
        int rows = (cards + CARD_COLUMNS - 1) / CARD_COLUMNS;
        ArrayPattern[] patterns = ArrayPattern.values();
        int patternRows = (patterns.length + CARD_COLUMNS - 1) / CARD_COLUMNS;
        float patternTop = rows * ROW_STEP + PATTERN_HEADER;
        float content = patternTop + patternRows * ROW_STEP - CARD_GAP;
        float view = PAGE_H - BOX_TOP - BOX_BOTTOM;
        float maxScroll = Math.max(0, content - view);

        if (bringSelectedIntoView) {
            bringSelectedIntoView = false;
            placeSelected(ui, view, maxScroll);
        }
        float wheel = ui.takeWheel();
        if (wheel != 0 && !ui.popupOpen() && ui.mouseIn(x, y, x + LEFT_W, y + PAGE_H)) {
            scroll -= wheel * ROW_STEP;
        }
        scroll = clamp(scroll, maxScroll);
        float scrolled = (float) Math.floor(clamp(ui.animate("themes#scroll", scroll, 16), maxScroll));

        ui.beginGroupBox("themes", x, y, LEFT_W, PAGE_H, false);
        float clipTop = y + CLIP_TOP, clipBottom = y + PAGE_H - CLIP_BOTTOM;
        ui.pushClip(x + 2, clipTop, x + LEFT_W - 2, clipBottom);
        Preset hovered = null;
        float inner = LEFT_W - INSET * 2;
        float cardW = (inner - CARD_GAP * (CARD_COLUMNS - 1)) / CARD_COLUMNS;
        int index = 0;
        for (Preset preset : Preset.values()) {
            if (preset == Preset.Custom) continue;
            int column = index % CARD_COLUMNS, row = index / CARD_COLUMNS;
            index++;
            float x0 = (float) Math.floor(x + INSET + column * (cardW + CARD_GAP));
            float y0 = y + BOX_TOP + row * ROW_STEP - scrolled;
            if (y0 + CARD_H <= clipTop || y0 >= clipBottom) continue;
            if (drawCard(ui, preset, x0, y0, (float) Math.floor(x0 + cardW), y0 + CARD_H, true)) hovered = preset;
        }

        float headingBaseline = y + BOX_TOP + rows * ROW_STEP - scrolled + 12;
        if (headingBaseline > clipTop && headingBaseline - 10 < clipBottom) {
            ui.textAtBaseline(x + INSET, headingBaseline, BankrollUi.TEXT_TITLE, "arraylist patterns");
        }
        for (int i = 0; i < patterns.length; i++) {
            int column = i % CARD_COLUMNS, row = i / CARD_COLUMNS;
            float x0 = (float) Math.floor(x + INSET + column * (cardW + CARD_GAP));
            float y0 = y + BOX_TOP + patternTop + row * ROW_STEP - scrolled;
            if (y0 + CARD_H <= clipTop || y0 >= clipBottom) continue;
            if (drawPatternCard(ui, patterns[i], x0, y0, (float) Math.floor(x0 + cardW), y0 + CARD_H)) hoveredPattern = patterns[i];
        }
        ui.popClip();
        if (maxScroll > 0) drawScrollBar(ui, x, y, content, view, maxScroll, scrolled);
        ui.endGroupBox();
        return hovered;
    }

    private static float clamp(float value, float maxScroll) {
        return Math.max(0, Math.min(maxScroll, value));
    }

    private void placeSelected(BankrollUi ui, float view, float maxScroll) {
        if (Themes.active() && Themes.current() != Preset.Custom) {
            float top = Themes.current().ordinal() / CARD_COLUMNS * ROW_STEP;
            if (top < scroll) {
                scroll = top;
            } else if (top + CARD_H > scroll + view) {
                scroll = top + CARD_H - view;
            }
        }
        scroll = clamp(scroll, maxScroll);
        ui.resetAnimation("themes#scroll", scroll);
    }

    private void drawScrollBar(BankrollUi ui, float x, float y, float content, float view, float maxScroll, float scrolled) {
        float x0 = x + LEFT_W - TRACK_INSET_0, x1 = x + LEFT_W - TRACK_INSET_1;
        float y0 = y + BOX_TOP, y1 = y + PAGE_H - BOX_BOTTOM;
        float trackH = y1 - y0;
        float length = Math.max(16, (float) Math.floor(trackH * view / content));
        float offset = (float) Math.floor((trackH - length) * scrolled / maxScroll);
        if (ui.clicked() && ui.mouseIn(x0 - 1, y0, x1 + 1, y1)) {
            float my = ui.mouseY();
            scrollGrab = my >= y0 + offset && my < y0 + offset + length ? my - (y0 + offset) : length / 2;
        }
        if (ui.dragArea("##scroll", x0 - 1, y0, x1 + 1, y1)) {
            float t = (ui.mouseY() - y0 - scrollGrab) / (trackH - length);
            scroll = (float) Math.floor(Math.max(0, Math.min(1, t)) * maxScroll);
            ui.resetAnimation("themes#scroll", scroll);
            offset = (float) Math.floor((trackH - length) * scroll / maxScroll);
        }
        ui.fillRect(x0, y0, x1, y1, BankrollUi.rgb(23, 23, 23));
        ui.fillRect(x0, y0 + offset, x1, y0 + offset + length, BankrollUi.rgb(36, 36, 36));
    }

    private boolean drawCard(BankrollUi ui, Preset preset, float x0, float y0, float x1, float y1, boolean labelled) {
        String id = "##card" + preset.name();
        if (ui.area(id, x0, y0, x1, y1)) Themes.select(preset);
        boolean hovered = ui.areaHovered();
        boolean selected = Themes.active() && Themes.current() == preset;
        float hot = ui.animate("themes#hot" + preset.name(), hovered ? 1 : 0, 18);
        float on = ui.animate("themes#on" + preset.name(), selected ? 1 : 0, 16);

        int now = Themes.strip(preset, 0.5, selected || hovered);
        ui.fillRect(x0, y0, x1, y1, BankrollUi.lerpColor(BankrollUi.lerpColor(CARD_BORDER, CARD_BORDER_HOT, hot), now | 0xFF000000, on));
        ui.fillRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, CARD_BG);

        float stripBottom = labelled ? y1 - LABEL_H : y1 - 1;
        drawStrip(ui, preset, x0 + 1, y0 + 1, x1 - 1, stripBottom, selected || hovered);
        if (labelled) {
            ui.fillRect(x0 + 1, stripBottom, x1 - 1, stripBottom + 1, BLACK);
            int text = BankrollUi.lerpColor(BankrollUi.TEXT, BankrollUi.TEXT_TITLE, Math.max(hot, on));
            float baseline = y1 - 6;
            ui.textAtBaseline(x0 + 7, baseline, text, preset.toString().toLowerCase(Locale.ROOT));
            if (on > 0.01f) drawTick(ui, x1 - 14, baseline - 6, BankrollUi.lerpColor(CARD_BG, BankrollUi.TEXT_TITLE, on));
        }
        return hovered;
    }

    private boolean drawPatternCard(BankrollUi ui, ArrayPattern pattern, float x0, float y0, float x1, float y1) {
        ArrayListModule list = arrayList();
        if (ui.area("##pattern" + pattern.name(), x0, y0, x1, y1)) list.pattern.setValue(pattern);
        boolean hovered = ui.areaHovered();
        boolean selected = list.pattern.getValue() == pattern;
        float hot = ui.animate("themes#phot" + pattern.name(), hovered ? 1 : 0, 18);
        float on = ui.animate("themes#pon" + pattern.name(), selected ? 1 : 0, 16);

        ui.fillRect(x0, y0, x1, y1, BankrollUi.lerpColor(BankrollUi.lerpColor(CARD_BORDER, CARD_BORDER_HOT, hot), BankrollUi.TEXT_TITLE, on));
        ui.fillRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, CARD_BG);

        float stripBottom = y1 - LABEL_H;
        if (pattern != ArrayPattern.Off) {
            float px0 = x0 + 1, py0 = y0 + 1, w = x1 - 1 - px0, h = stripBottom - py0;
            double t = patternTime();
            for (float by = py0; by < stripBottom; by += PATTERN_BLOCK) {
                for (float bx = px0; bx < x1 - 1; bx += PATTERN_BLOCK) {
                    int color = pattern.color(bx - px0 + PATTERN_BLOCK / 2, by - py0 + PATTERN_BLOCK / 2, w, h, t, list.motion());
                    ui.fillRect(bx, by, Math.min(x1 - 1, bx + PATTERN_BLOCK), Math.min(stripBottom, by + PATTERN_BLOCK), color | 0xFF000000);
                }
            }
        }

        ui.fillRect(x0 + 1, stripBottom, x1 - 1, stripBottom + 1, BLACK);
        int text = BankrollUi.lerpColor(BankrollUi.TEXT, BankrollUi.TEXT_TITLE, Math.max(hot, on));
        float baseline = y1 - 6;
        ui.textAtBaseline(x0 + 7, baseline, text, pattern == ArrayPattern.Off ? "none" : pattern.toString().toLowerCase(Locale.ROOT));
        if (on > 0.01f) drawTick(ui, x1 - 14, baseline - 6, BankrollUi.lerpColor(CARD_BG, BankrollUi.TEXT_TITLE, on));
        return hovered;
    }

    private static void drawStrip(BankrollUi ui, Preset preset, float x0, float y0, float x1, float y1, boolean moving) {
        float width = x1 - x0;
        float left = x0;
        int leftColor = Themes.strip(preset, 0, moving) | 0xFF000000;
        while (left < x1) {
            float right = Math.min(x1, left + STRIP_STEP);
            int rightColor = Themes.strip(preset, (right - x0) / width, moving) | 0xFF000000;
            ui.horizontalGradient(left, y0, right, y1, leftColor, rightColor);
            left = right;
            leftColor = rightColor;
        }
    }

    private static void drawTick(BankrollUi ui, float x, float y, int color) {
        int[][] rows = {{6, 7}, {5, 7}, {4, 6}, {0, 1, 3, 5}, {1, 4}, {2, 3}};
        for (int r = 0; r < rows.length; r++) {
            int[] row = rows[r];
            if (row.length == 4) {
                ui.fillRect(x + row[0], y + r, x + row[1] + 1, y + r + 1, color);
                ui.fillRect(x + row[2], y + r, x + row[3], y + r + 1, color);
            } else {
                ui.fillRect(x + row[0], y + r, x + row[1], y + r + 1, color);
            }
        }
    }

    private void drawPreview(BankrollUi ui, float x, float y, Preset preset, ArrayPattern pattern) {
        if (preset != shown) {
            shownFrom = shown;
            shown = preset;
            ui.resetAnimation("themes#blend", 0);
        }
        blend = ui.animate("themes#blend", 1, 14);

        ui.beginGroupBox("preview", x, y, RIGHT_W, PREVIEW_H, false);
        float left = x + INSET, right = x + RIGHT_W - INSET;

        boolean patterned = pattern != ArrayPattern.Off;
        String title = patterned ? pattern.toString() : shown.toString();
        ui.textAtBaseline(left, y + 31, BankrollUi.TEXT_TITLE, title.toLowerCase(Locale.ROOT));
        int[] stops = patterned ? new int[0] : shown.stops();
        for (int i = 0; i < stops.length; i++) {
            float chip = right - (stops.length - i) * 14 + 4;
            ui.fillRect(chip, y + 23, chip + 10, y + 33, BLACK);
            ui.fillRect(chip + 1, y + 24, chip + 9, y + 32, stops[i] | 0xFF000000);
        }

        float top = y + 40, bottom = y + PREVIEW_H - (Themes.active() ? 12 : 26);
        ui.fillRect(left, top, right, bottom, BLACK);
        ui.fillRect(left + 1, top + 1, right - 1, bottom - 1, SCREEN_BG);

        float unit = PREVIEW_SCALE;

        String brand = Tempest.NAME.toLowerCase(Locale.ROOT);
        drawWavy(ui, left + 7, top + 15, brand, 0, left, unit);

        float rowY = top + 6;
        float listW = 0;
        for (String name : MOCK_MODULES) listW = Math.max(listW, ui.textWidth(name));
        float listX0 = right - 8 - listW, listY0 = rowY, listH = MOCK_MODULES.length * 13f;

        float span = ui.textWidth(MOCK_MODULES[MOCK_MODULES.length / 2]), cx = listW - span / 2;
        boolean motion = arrayList().motion();
        double t = patternTime();
        for (int row = 0; row < MOCK_MODULES.length; row++) {
            String name = MOCK_MODULES[row];
            float textX = (float) Math.floor(right - 8 - ui.textWidth(name));
            if (patterned) {
                float letterX = textX;
                for (int i = 0; i < name.length(); i++) {
                    String letter = name.substring(i, i + 1);
                    float width = ui.textWidth(letter);
                    int color = pattern.color(letterX + width / 2 - listX0, rowY + 6 - listY0, listW, listH, cx, span, t, motion);
                    ui.textAtBaseline(letterX, rowY + 9, color | 0xFF000000, letter);
                    letterX += width;
                }
                int bar = pattern.color(listW - 1, rowY + 6.5 - listY0, listW, listH, cx, span, t, motion);
                ui.fillRect(right - 3, rowY, right - 1, rowY + 13, bar | 0xFF000000);
            } else {
                drawWavy(ui, textX, rowY + 9, name, row, left, unit);
                ui.fillRect(right - 3, rowY, right - 1, rowY + 13, previewColor(row, (right - 2 - left) / unit));
            }
            rowY += 13;
        }

        float barY = bottom - 16, barLeft = left + 7, barRight = right - 7;
        ui.textAtBaseline(barLeft, barY - 7, BankrollUi.TEXT_ACTIVE, "target");
        String health = "16.0";
        ui.textAtBaseline(barRight - ui.textWidth(health), barY - 7, BankrollUi.TEXT, health);
        ui.fillRect(barLeft, barY, barRight, barY + 6, BLACK);
        float fill = barLeft + 1 + (barRight - barLeft - 2) * 0.8f;
        float piece = barLeft + 1;
        int row = 0;

        while (piece < fill) {
            float next = Math.min(fill, piece + STRIP_STEP);
            ui.horizontalGradient(piece, barY + 1, next, barY + 5,
                    previewColor(row, (piece - left) / unit), previewColor(row, (next - left) / unit));
            piece = next;
        }

        if (!Themes.active()) {
            String mode = BankrollMenu.display(ClientSettingsModule.color.getValue().toString());
            ui.textAtBaseline(left, y + PREVIEW_H - 11, BankrollUi.TEXT, "in use: " + mode);
        }
        ui.endGroupBox();
    }

    private void drawWavy(BankrollUi ui, float x, float baseline, String text, int row, float screenLeft, float unit) {
        if (!Themes.horizontal()) {
            ui.textAtBaseline(x, baseline, previewColor(row, 0), text);
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            String letter = text.substring(i, i + 1);
            float width = ui.textWidth(letter);
            ui.textAtBaseline(x, baseline, previewColor(row, (x + width / 2 - screenLeft) / unit), letter);
            x += width;
        }
    }

    private int previewColor(int row, float x) {
        int to = waveColor(shown, row, x);
        return (blend >= 1 ? to : Themes.mix(waveColor(shownFrom, row, x), to, blend)) | 0xFF000000;
    }

    private static int waveColor(Preset preset, int row, float x) {
        return Themes.horizontal() ? Themes.atX(preset, x) : Themes.row(preset, row);
    }

    private void drawWave(BankrollUi ui, float x, float y) {
        ui.beginGroupBox("wave", x, y, RIGHT_W, WAVE_H, false);
        slider(ui, "speed", ClientSettingsModule.themeSpeed);
        slider(ui, "wave length", ClientSettingsModule.themeLength);
        slider(ui, "saturation", ClientSettingsModule.themeSaturation);

        slider(ui, "pattern speed", arrayList().patternSpeed);
        ui.label("direction");
        drawDirections(ui, x + INSET + 19, (float) Math.floor(ui.cursorY()));
        ui.spacing(26);
        boolean follow = ClientSettingsModule.themeMenu.getValue();
        boolean picked = ui.checkbox("menu follows theme", follow, 0);
        if (picked != follow) ClientSettingsModule.themeMenu.setValue(picked);
        ui.endGroupBox();
    }

    private static void slider(BankrollUi ui, String label, NumberProperty property) {
        double value = property.getValue();
        double picked = ui.slider(label, value, property.getMin(), property.getMax(), property.getIncrement(),
                v -> BankrollMenu.snap(property, v), v -> BankrollMenu.format(property, v));
        if (picked != value) property.setValue(picked);
    }

    private static void drawDirections(BankrollUi ui, float x, float y) {
        Direction[] directions = Direction.values();
        float width = 139f / directions.length;
        float height = 18;
        ui.fillRect(x, y, x + 139, y + height, BLACK);
        for (int i = 0; i < directions.length; i++) {
            Direction direction = directions[i];
            float x0 = (float) Math.floor(x + i * width), x1 = (float) Math.floor(x + (i + 1) * width);
            if (ui.area("##dir" + direction.name(), x0, y, x1, y + height)) {
                ClientSettingsModule.themeDirection.setValue(direction);
            }
            boolean on = Themes.direction() == direction;
            float hot = ui.animate("themes#dirhot" + i, ui.areaHovered() ? 1 : 0, 18);
            float lit = ui.animate("themes#dir" + i, on ? 1 : 0, 16);

            int fill = BankrollUi.lerpColor(BankrollUi.lerpColor(BankrollUi.BUTTON_BOTTOM, BankrollUi.BUTTON_TOP, hot), SEGMENT_ON, lit);
            ui.fillRect(x0 + (i == 0 ? 1 : 0), y + 1, x1 - 1, y + height - 1, fill);
            int arrow = BankrollUi.lerpColor(BankrollUi.lerpColor(BankrollUi.TEXT, BankrollUi.TEXT_ACTIVE, hot), BankrollUi.TEXT_TITLE, lit);
            drawArrow(ui, direction, (float) Math.floor((x0 + x1) / 2), y + 9, arrow);
        }
    }

    private static void drawArrow(BankrollUi ui, Direction direction, float x, float y, int color) {
        for (int i = 0; i < 4; i++) {
            switch (direction) {
                case Up -> ui.fillRect(x - i, y - 2 + i, x + i + 1, y - 1 + i, color);
                case Down -> ui.fillRect(x - i, y + 1 - i, x + i + 1, y + 2 - i, color);
                case Left -> ui.fillRect(x - 2 + i, y - i, x - 1 + i, y + i + 1, color);
                case Right -> ui.fillRect(x + 1 - i, y - i, x + 2 - i, y + i + 1, color);
            }
        }
    }

    private Preset drawCustom(BankrollUi ui, float x, float y, BankrollMenu menu, Preset hovered) {
        ui.beginGroupBox("custom", x, y, RIGHT_W, CUSTOM_H, false);
        float cardY = (float) Math.floor(ui.cursorY());
        if (drawCard(ui, Preset.Custom, x + INSET, cardY, x + RIGHT_W - INSET, cardY + 20, false)) hovered = Preset.Custom;
        ui.spacing(27);
        colorRow(ui, "color 1", ClientSettingsModule.pickerColor1, x, menu);
        colorRow(ui, "color 2", ClientSettingsModule.pickerColor2, x, menu);
        ui.endGroupBox();
        return hovered;
    }

    private static void colorRow(BankrollUi ui, String label, NumberProperty property, float boxX, BankrollMenu menu) {
        float rowY = (float) Math.floor(ui.cursorY());
        ui.rowLabel(label);

        float fieldRight = boxX + RIGHT_W - 31 - 7;
        String hex = hex(property.getValue().intValue());
        String typed = ui.searchField("##hex" + label, hex, "#rrggbb", fieldRight - 58, rowY - 3, fieldRight, rowY + 12, 9);
        if (!typed.equals(hex)) {
            Integer parsed = parseHex(typed);
            if (parsed != null && (parsed & 0xFFFFFF) != (property.getValue().intValue() & 0xFFFFFF)) {
                property.setValue((double) parsed.intValue());
                Themes.select(Preset.Custom);
            }
        }

        float[] rgba = menu.colorOf(property);
        if (ui.colorSwatch("##swatch" + label, rgba, 0)) {
            property.setValue((double) BankrollUi.toColor(rgba, false));
            Themes.select(Preset.Custom);
        }
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static Integer parseHex(String text) {
        String digits = text.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 6 && digits.length() != 8) return null;
        try {
            return (int) (Long.parseLong(digits, 16) & 0xFFFFFFL) | 0xFF000000;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
