package rip.tempest.interfaces.minigame.cases;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

final class CaseOpening {
    private enum Phase {INVENTORY, CASE, OPENING, REVEAL, PREVIEW}

    private static final float H = 1080;

    private static final int REEL_TILES = 38, REEL_WINNER = 3;
    private static final double REEL_SHOWN_AT = 2.0, REEL_STARTS_AT = 2.1, REEL_SECONDS = 6.0;
    private static final double[] TICKS = {0.000, 0.063, 0.125, 0.188, 0.250, 0.313, 0.375, 0.438, 0.500, 0.563, 0.625, 0.688, 0.750, 0.813, 0.875, 0.938, 1.000, 1.063, 1.125, 1.188, 1.250, 1.313, 1.351, 1.375, 1.483, 1.620, 1.701, 1.786, 1.872, 2.003, 2.154, 2.313, 2.466, 2.615, 2.773, 2.941, 3.104, 3.339, 3.630, 3.953, 4.385, 5.004};

    private static final float REEL_WIDTH_OF_HEIGHT = 1.33f;
    private static final float OUTER_W = 320, OUTER_H = OUTER_W * .70f, MAGNIFIED_W = 360, MAGNIFIED_H = MAGNIFIED_W * .75f, TILE_MARGIN = 10;
    private static final float BAR_W = 4, BAR_H = 268, RARITY_H = 12;
    private static final int WARNING = 0xFFB1AF2D, POSITIVE = 0xFF0F9D58, UNUSUAL = 0xFFFFD700, KILL_EATER = 0xFFCF6A32;
    private static final float LOOT_PADDING_X = 64, LOOT_PADDING_BOTTOM = 80;
    private static final double LOOT_IN = 1.0, LOOT_OUT = .5;

    private static final float LENS_RADIUS = 362, OUTER_FADE = 250;
    private static final float TILE_PITCH_X = 177.6f, TILE_PITCH_Y = 170, TILE_W = 151.6f, TILE_H = 110;
    private static final float CASE_W = 580, CASE_Y = 414;
    private static final double CASE_FALL = .95, REVEAL_FADE = .35;

    private static final float BUTTON_ROW_W = 968, BUTTON_ROW_H = 80;

    private static final float INV_TILE_W = 192, INV_TILE_H = 184, INV_IMAGE_H = 121, INV_PADDING = 13, INV_TOP = 150;

    private final List<CaseData.Container> containers;
    private final File root;
    private final CaseGraphics g;
    private final Consumer<String> sound;
    private final Runnable stopSounds;
    private final Random random = new Random();

    private Phase phase = Phase.INVENTORY;
    private double now, phaseStart;
    private float width = 1920, scale = 1;
    private int pixelW, pixelH;
    private float mouseX, mouseY;

    private float inventoryScroll, inventoryScrollTarget, inventoryMax;

    private CaseData.Container container;
    private double lootSince;
    private List<CaseData.Item> lootItems;

    private CaseData.Roll won;
    private List<CaseData.Item> reel;
    private double slack;
    private int nextTick;
    private boolean revealSoundDone;

    private int previewIndex;

    private final List<float[]> hitBoxes = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();

    CaseOpening(final File root, final List<CaseData.Container> containers, final CaseGraphics graphics, final Consumer<String> sound, final Runnable stopSounds) {
        this.root = root;
        this.containers = containers;
        this.g = graphics;
        this.sound = sound;
        this.stopSounds = stopSounds;
    }

    private void setPhase(final Phase next) {
        phase = next;
        phaseStart = now;
    }

    void openCase(final CaseData.Container chosen) {
        container = chosen;
        lootItems = new ArrayList<>(chosen.items);
        lootItems.sort((a, b) -> a.rarity - b.rarity);
        lootSince = now;
        setPhase(Phase.CASE);
        sound.accept("container_weapon_fall");
    }

    void unlock() {
        unlockWith(CaseData.roll(container, random));
    }

    void unlockWith(final CaseData.Roll roll) {
        won = roll;
        reel = CaseData.fillReel(container, REEL_TILES, random);
        slack = (10 + random.nextInt(81)) / 100.0;
        nextTick = 0;
        revealSoundDone = false;
        icon(won.item);
        setPhase(Phase.OPENING);
        sound.accept("container_weapon_open");
        sound.accept("container_weapon_ticker");
    }

    void skip() {
        if (phase != Phase.OPENING) return;
        stopSounds.run();
        nextTick = TICKS.length;
        finishOpening();
    }

    private void finishOpening() {
        setPhase(Phase.REVEAL);

        sound.accept("reveal_" + Math.min(won.item.rarity, CaseData.COVERT));
    }

    private void showPreview(final int index) {
        previewIndex = index;
        setPhase(Phase.PREVIEW);
    }

    private void backToInventory() {
        container = null;
        setPhase(Phase.INVENTORY);
    }

    boolean escape() {
        switch (phase) {
            case INVENTORY:
                return false;
            case PREVIEW:
                setPhase(Phase.CASE);
                lootSince = now - LOOT_IN;
                return true;
            default:
                backToInventory();
                return true;
        }
    }

    void click(final int pixelX, final int pixelY) {
        final float x = pixelX / scale, y = pixelY / scale;
        for (int i = hitBoxes.size() - 1; i >= 0; i--) {
            final float[] box = hitBoxes.get(i);
            if (x >= box[0] && y >= box[1] && x < box[0] + box[2] && y < box[1] + box[3]) {
                sound.accept("button_press");
                hitActions.get(i).run();
                return;
            }
        }
    }

    void scroll(final int amount) {
        if (phase == Phase.INVENTORY) inventoryScrollTarget = clamp(inventoryScrollTarget - Math.signum(amount) * INV_TILE_H, 0, inventoryMax);
    }

    private void hit(final float x, final float y, final float w, final float h, final Runnable action) {
        hitBoxes.add(new float[]{x, y, w, h});
        hitActions.add(action);
    }

    private boolean hovered(final float x, final float y, final float w, final float h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    void render(final int pixelWidth, final int pixelHeight, final double time, final int pixelMouseX, final int pixelMouseY) {
        now = time;
        pixelW = pixelWidth;
        pixelH = pixelHeight;
        scale = pixelHeight / H;
        width = pixelWidth / scale;
        mouseX = pixelMouseX / scale;
        mouseY = pixelMouseY / scale;
        hitBoxes.clear();
        hitActions.clear();
        if (phase == Phase.OPENING) updateOpening();

        g.begin(pixelW, pixelH, width);
        try {
            final File custom = customBackground();
            if (custom != null) drawCover(g.texture(custom));
            switch (phase) {
                case INVENTORY:
                    drawInventory();
                    break;
                case CASE:
                    drawCaseScreen();
                    break;
                case OPENING:
                    drawOpening();
                    break;
                case REVEAL:
                    drawInspect(won.item, won, true);
                    break;
                case PREVIEW:
                    drawPreview();
                    break;
            }
        } finally {
            g.end();
        }
    }

    private void updateOpening() {
        final double t = now - phaseStart;
        while (nextTick < TICKS.length && t >= REEL_STARTS_AT + TICKS[nextTick]) {
            sound.accept("container_weapon_ticker");
            nextTick++;
        }
        if (t >= REEL_STARTS_AT + REEL_SECONDS) finishOpening();
    }

    private File customBackground() {
        for (final String name : new String[]{"background.png", "background.jpg"}) {
            final File file = new File(root, name);
            if (file.isFile()) return file;
        }
        return null;
    }

    private void drawCover(final int texture) {
        g.image(texture, 0, 0, width, H, 0xFFFFFFFF);
    }

    private void backdrop(final float lod, final int tint) {
        g.blurBackdrop(pixelW, pixelH, width, lod, tint);
    }

    private void drawInventory() {
        backdrop(4.5f, 0xFF4A4C50);
        inventoryScroll += (inventoryScrollTarget - inventoryScroll) * .25f;

        final int columns = Math.max(1, (int) ((width - 2 * LOOT_PADDING_X) / INV_TILE_W));
        final float left = (width - columns * INV_TILE_W) / 2;
        final int rows = (containers.size() + columns - 1) / columns;
        inventoryMax = Math.max(0, INV_TOP + rows * INV_TILE_H + 40 - H);
        inventoryScrollTarget = clamp(inventoryScrollTarget, 0, inventoryMax);

        for (int i = 0; i < containers.size(); i++) {
            final CaseData.Container entry = containers.get(i);
            final float x = left + i % columns * INV_TILE_W, y = INV_TOP + i / columns * INV_TILE_H - inventoryScroll;
            if (y > H || y + INV_TILE_H < INV_TOP - 40) continue;
            final float tileX = x + INV_PADDING, tileW = INV_TILE_W - 2 * INV_PADDING;
            final boolean over = hovered(tileX, y, tileW, INV_IMAGE_H) && mouseY >= INV_TOP;
            itemBackground(tileX, y, tileW, INV_IMAGE_H, over);
            fitImage(g.texture(new File(root, "icons/" + entry.icon)), tileX, y, tileW, INV_IMAGE_H);
            g.rect(tileX, y + INV_IMAGE_H - 4, tileW, 4, 0xFFB0C3D9);
            final List<String> lines = g.wrap(entry.name, tileW, 14, scale);
            for (int line = 0; line < lines.size() && line < 3; line++) g.text(lines.get(line), tileX, y + INV_IMAGE_H + 6 + line * 17, 14, false, 0xFFFFFFFF, scale);
            if (y + INV_IMAGE_H > INV_TOP) hit(tileX, y, tileW, INV_IMAGE_H, () -> openCase(entry));
        }
        g.gradient(0, 0, width, INV_TOP, 0xF0141518, 0xF0141518);
        g.gradient(0, INV_TOP, width, 24, 0x80000000, 0x00000000);
        g.text("Inventory", left + INV_PADDING, 48, 50, true, 0xFFFFFFFF, scale);
        final String count = containers.size() + " Containers";
        g.text(count, left + INV_PADDING, 108, 20, false, 0xFFB5B5B5, scale);
    }

    private void drawHeader() {
        final float cx = width / 2;
        g.gradient(0, 0, width, 200, 0x73000000, 0x00000000);
        centered("Unlock Container", cx, 48, 50, true, 0xFFFFFFFF);
        final String unlock = "Unlock ", name = container.name;
        final float w = g.textWidth(unlock, 24, false, scale) + g.textWidth(name, 24, true, scale);
        final float x = cx - w / 2;
        final float after = g.text(unlock, x, 104, 24, false, 0xFFEDEDED, scale);
        g.text(name, x + after, 104, 24, true, 0xFFFFFFFF, scale);
        final String warning = "This Container can only be opened once";
        final float warningW = g.textWidth(warning, 20, false, scale);
        final float warningX = cx - warningW / 2 + 12;
        drawInfoIcon(warningX - 26, 142);
        g.text(warning, warningX, 136, 20, false, 0xFFEDEDED, scale);
    }

    private void drawInfoIcon(final float x, final float y) {
        centered("i", x + 10, y - 3, 17, true, 0xFFEDEDED);
        circleOutline(x + 10, y + 10, 9.5f, 0xFFEDEDED);
    }

    private void circleOutline(final float x, final float y, final float radius, final int color) {
        final float[] rgba = {(color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f};
        g.rect(0, 0, 0, 0, color);
        GL11.glLineWidth(Math.max(1f, 1.5f * scale));
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(rgba[0], rgba[1], rgba[2], rgba[3]);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 48; i++) {
            final double a = i / 48.0 * Math.PI * 2;
            GL11.glVertex2f(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawCaseScreen() {
        backdrop(1.6f, 0xFFFFFFFF);
        final double t = now - lootSince;
        drawCaseModel(t, 1f, 1f, 0);
        drawLootList(easeInOut((float) Math.min(1, t / LOOT_IN)));
        drawHeader();
        drawButtonRow(false);
    }

    private void drawCaseModel(final double t, final float zoom, final float bright, final float blur) {
        final int texture = g.texture(new File(root, "icons/" + container.icon));
        float y = CASE_Y, roll = 0;
        if (t < CASE_FALL) {
            final double land = .42;
            if (t < land) {
                final double u = t / land;
                y = CASE_Y - (float) ((1 - u * u) * 760);
                roll = (float) (26 * (1 - u * u));
            } else {
                final double u = (t - land) / (CASE_FALL - land);
                final double bounce = Math.sin(u * Math.PI * 2.5) * Math.pow(1 - u, 2);
                y = CASE_Y - (float) (Math.abs(bounce) * 34);
                roll = (float) (bounce * 5);
            }
        }
        final float w = CASE_W * zoom, h = w * .75f;
        final float settled = zoom > 1 ? (zoom - 1) / .36f * 184 : 0;
        g.brightness(bright);
        if (blur > 0) g.blur(blur, (float) (Math.pow(2, blur) * .75f / 512));
        g.imageTurned(texture, width / 2, y + settled, w, h, 0, roll, 0xFFFFFFFF);
        g.blur(0, 0);
        g.brightness(1);
    }

    private void drawLootList(final float shown) {
        final float innerW = width - 2 * LOOT_PADDING_X + 2 * 9;
        final int columns = Math.max(1, (int) (innerW / TILE_PITCH_X));
        final int tiles = lootItems.size() + (container.rare.isEmpty() ? 0 : 1);
        final int rows = (tiles + columns - 1) / columns;
        final float height = 46 + rows * TILE_PITCH_Y + LOOT_PADDING_BOTTOM;
        final float top = H - height * shown;
        g.rect(0, top, width, height, 0x66000000);
        final float left = LOOT_PADDING_X - 9;

        centered("Open and receive one of the following", width / 2, top + 2, 20, false, 0xFFFFFFFF);
        final String inspect = "INSPECT ITEMS";
        final float inspectW = g.textWidth(inspect, 16, true, scale);
        final float inspectX = width - LOOT_PADDING_X - inspectW;
        if (hovered(inspectX - 10, top, inspectW + 20, 30)) g.rect(inspectX - 10, top, inspectW + 20, 30, 0x70000000);
        g.text(inspect, inspectX, top + 5, 16, true, 0xFFFFFFFF, scale);
        hit(inspectX - 10, top, inspectW + 20, 30, () -> showPreview(0));
        g.rect(LOOT_PADDING_X, top + 32, width - 2 * LOOT_PADDING_X, 1, 0x40FFFFFF);

        for (int i = 0; i < tiles; i++) {
            final float x = left + i % columns * TILE_PITCH_X, y = top + 46 + i / columns * TILE_PITCH_Y;
            final boolean over = hovered(x, y, TILE_W, TILE_H);
            final int index = i;
            hit(x, y, TILE_W, TILE_H, () -> showPreview(index));
            if (i < lootItems.size()) {
                final CaseData.Item item = lootItems.get(i);
                itemBackground(x, y, TILE_W, TILE_H, over);
                fitImage(icon(item), x, y, TILE_W, TILE_H);
                g.rect(x, y + TILE_H - 4, TILE_W, 4, 0xFF000000 | Integer.parseInt(item.color.substring(1), 16));
                g.text(item.weapon, x, y + TILE_H + 4, 14, false, 0xFFFFFFFF, scale);
                g.text(item.paint, x, y + TILE_H + 22, 14, false, 0xFFCFCFCF, scale);
            } else {
                g.gradient(x, y, TILE_W, TILE_H, 0xFFD9B100, 0xFF7A6200);
                if (over) g.rect(x, y, TILE_W, TILE_H, 0x18FFFFFF);
                fitImage(g.texture(new File(root, "rare/" + container.rareIcon)), x, y, TILE_W, TILE_H);
                g.rect(x, y + TILE_H - 4, TILE_W, 4, UNUSUAL);
                g.text(container.rareName, x, y + TILE_H + 4, 14, false, 0xFFFFFFFF, scale);
            }
        }
    }

    private void itemBackground(final float x, final float y, final float w, final float h, final boolean over) {
        g.gradient(x, y, w, h, over ? 0xCC5F5F5F : 0xBF4B4B4B, over ? 0xCCC0C0C0 : 0xBFAFAFAF);
    }

    private void fitImage(final int texture, final float x, final float y, final float w, final float h) {
        final float imageH = Math.min(h, w * .75f), imageW = imageH / .75f;
        g.image(texture, x + (w - imageW) / 2, y + (h - imageH) / 2, imageW, imageH, 0xFFFFFFFF);
    }

    private int icon(final CaseData.Item item) {
        return g.texture(new File(root, "icons/" + item.icon));
    }

    private void drawButtonRow(final boolean opening) {
        final float w = Math.min(BUTTON_ROW_W, width), x = (width - w) / 2, y = H - BUTTON_ROW_H;
        g.rect(x, y, w, 1, 0x80919191);
        g.image(g.texture(new File(root, "keys/" + container.keyIcon)), x, y + 8, 64 / .75f, 64, 0xFFFFFFFF);
        final float textX = x + 64 / .75f + 8, textY = y + 27;
        final float used = g.text("Use ", textX, textY, 20, false, 0xFFD5D5D5, scale);
        g.text(container.keyName, textX + used, textY, 20, true, 0xFFFFFFFF, scale);

        float right = x + w;
        right = button("CLOSE", right, y, 0, this::backToInventory);
        if (opening) {
            right = button("SKIP", right, y, 0, this::skip);
            spinner(right - 22, y + 40);
        } else {
            button("UNLOCK CONTAINER", right, y, POSITIVE, this::unlock);
        }
    }

    private float button(final String label, final float right, final float rowY, final int fill, final Runnable action) {
        final float textW = g.textWidth(label, 24, true, scale), w = textW + 24, h = 46;
        final float x = right - 4 - w, y = rowY + (BUTTON_ROW_H - h) / 2;
        final boolean over = hovered(x, y, w, h);
        if (fill != 0) g.rect(x, y, w, h, over ? brighter(fill) : fill);
        else if (over) g.rect(x, y, w, h, 0x0DFFFFFF);
        g.text(label, x + 12, y + 8, 24, true, 0xFFFFFFFF, scale);
        hit(x, y, w, h, action);
        return x - 4;
    }

    private static int brighter(final int color) {
        final int r = Math.min(255, (color >> 16 & 255) + 26), green = Math.min(255, (color >> 8 & 255) + 26), b = Math.min(255, (color & 255) + 26);
        return 0xFF000000 | r << 16 | green << 8 | b;
    }

    private void spinner(final float x, final float y) {
        g.rect(0, 0, 0, 0, 0xFFFFFFFF);
        GL11.glLineWidth(Math.max(1f, 2.5f * scale));
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(1, 1, 1, 1);
        final double start = now * 5;
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= 30; i++) {
            final double a = start + i / 30.0 * Math.PI * 1.45;
            GL11.glVertex2f(x + (float) Math.cos(a) * 13, y + (float) Math.sin(a) * 13);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawOpening() {
        final double t = now - phaseStart;
        backdrop(1.6f, 0xFFFFFFFF);

        final float zoom = 1 + .36f * easeInOut((float) Math.min(1, t / 1.5));
        final boolean reelUp = t >= REEL_SHOWN_AT;
        final float reelIn = reelUp ? (float) Math.min(1, (t - REEL_SHOWN_AT) / .25) : 0;
        drawCaseModel(CASE_FALL, zoom, 1 - .6f * reelIn, 2.5f * reelIn);

        if (t < LOOT_OUT) drawLootList(1 - easeInOut((float) (t / LOOT_OUT)));
        if (reelUp) drawReel(reelIn, (float) cubicBezier(Math.max(0, Math.min(1, (t - REEL_STARTS_AT) / REEL_SECONDS)), .075, .82, .165, 1));
        drawHeader();
        drawButtonRow(true);
    }

    private void drawReel(final float open, final float progress) {
        final float cx = width / 2, cy = H / 2;
        final float boxW = H * REEL_WIDTH_OF_HEIGHT, boxLeft = cx - boxW / 2;
        final float eased = open * open;

        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0);
        GL11.glScalef(1, Math.max(.01f, eased), 1);
        GL11.glTranslatef(-cx, -cy, 0);

        final float circleX = cx * scale, circleY = pixelH - cy * scale, radius = LENS_RADIUS * scale * Math.max(.02f, eased);

        g.fade(boxLeft * scale, (boxLeft + boxW) * scale, OUTER_FADE * scale);
        g.circle(circleX, circleY, radius, 2);
        drawReelList(boxLeft, boxW, cy, OUTER_W, OUTER_H, progress, false);
        g.noFade();

        g.circle(circleX, circleY, radius, 1);
        g.rect(boxLeft, 0, boxW, H, 0x80000000);
        drawReelList(boxLeft, boxW, cy, MAGNIFIED_W, MAGNIFIED_H, progress, true);
        g.circle(0, 0, 0, 0);

        g.rect(cx - BAR_W / 2, cy - BAR_H / 2, BAR_W, BAR_H, WARNING);
        GL11.glPopMatrix();

        if (open < 1) {
            final int alpha = (int) ((1 - open) * 255);
            g.image(g.radial(), cx - boxW / 2, cy - 260 * Math.max(.2f, eased), boxW, 520 * Math.max(.2f, eased), alpha << 24 | 0xFFFFFF);
        }
    }

    private void drawReelList(final float boxLeft, final float boxW, final float cy, final float tileW, final float tileH, final float progress, final boolean magnified) {
        final float pitch = tileW + 2 * TILE_MARGIN;
        final float end = (REEL_TILES - 1 - REEL_WINNER) * pitch + TILE_MARGIN + tileW * (float) slack;
        final float start = boxW / 2;
        final float at = start + (end - start) * progress;
        final float zero = boxLeft + boxW / 2 - at;
        for (int i = 0; i < REEL_TILES; i++) {
            final float x = zero + (REEL_TILES - 1 - i) * pitch + TILE_MARGIN;
            if (x + tileW < boxLeft || x > boxLeft + boxW) continue;
            final boolean winner = i == REEL_WINNER;
            final boolean gold = winner && won.special;
            final CaseData.Item item = winner ? won.item : reel.get(i);
            drawReelTile(x, cy - tileH / 2, tileW, tileH, gold ? UNUSUAL : 0xFF000000 | Integer.parseInt(item.color.substring(1), 16),
                    gold ? g.texture(new File(root, "rare/" + container.rareIcon)) : icon(item), magnified);
        }
    }

    private void drawReelTile(final float x, final float y, final float w, final float h, final int rarity, final int texture, final boolean magnified) {
        if (!magnified) {
            g.brightness(.5f);
        }

        g.rect(x, y, w, h, 0xBF64646E);
        g.image(g.radial(), x, y, w, h, 0x402D2D23);
        final int r = rarity >> 16 & 255, green = rarity >> 8 & 255, b = rarity & 255;
        final int tint = (r * 145 / 255) << 16 | (green * 145 / 255) << 8 | b * 145 / 255;
        g.gradient(x, y + h * .6f, w, h * .4f, tint, 0xFF000000 | tint);
        if (!magnified) g.blur(1.2f, 2.5f / 512);
        g.image(texture, x, y, w, h, 0xFFFFFFFF);
        g.blur(0, 0);
        g.rect(x, y + h - RARITY_H, w, RARITY_H, 0xFF000000 | Math.min(255, r * 230 / 255) << 16 | green * 230 / 255 << 8 | b * 230 / 255);
        if (magnified) {
            g.rect(x, y, w, 1, 0xFF444444);
            g.rect(x, y + h - 1, w, 1, 0xFF444444);
            g.rect(x, y, 1, h, 0xFF444444);
            g.rect(x + w - 1, y, 1, h, 0xFF444444);
        } else {
            g.brightness(1);
        }
    }

    private void drawPreview() {
        final List<CaseData.Item> all = new ArrayList<>(lootItems);
        all.addAll(container.rare);
        previewIndex = Math.floorMod(previewIndex, all.size());
        final CaseData.Item item = all.get(previewIndex);
        drawInspect(item, null, false);

        final CaseData.Item before = all.get(Math.floorMod(previewIndex - 1, all.size())), after = all.get(Math.floorMod(previewIndex + 1, all.size()));
        arrow(true, before, () -> previewIndex--);
        arrow(false, after, () -> previewIndex++);
    }

    private void arrow(final boolean left, final CaseData.Item next, final Runnable action) {
        final float w = 320, h = 90, x = left ? 40 : width - 40 - w, y = H / 2 - h / 2 - 60;
        final boolean over = hovered(x, y, w, h);
        final int color = over ? 0xFFFFFFFF : 0xB0FFFFFF;
        final String name = next.name;
        final float nameW = g.textWidth(name, 18, false, scale);
        if (left) {
            g.text("<", x, y + 16, 44, true, color, scale);
            g.text(name, x + 40, y + 34, 18, false, color, scale);
        } else {
            g.text(">", x + w - 26, y + 16, 44, true, color, scale);
            g.text(name, x + w - 40 - nameW, y + 34, 18, false, color, scale);
        }
        hit(x, y, w, h, action);
    }

    private void drawInspect(final CaseData.Item item, final CaseData.Roll roll, final boolean reveal) {
        final double t = now - phaseStart;
        final float appear = reveal ? (float) Math.min(1, t / REVEAL_FADE) : 1;
        backdrop(reveal ? 1.6f * (1 - appear) : 0, 0xFFFFFFFF);

        final float yaw = (float) (Math.sin(now * .7) * .42);
        final float itemW = item.knife ? 560 : item.gloves ? 620 : 800;
        final float itemY = item.knife ? 510 : 470;
        final int alpha = (int) (appear * 255) << 24 | 0xFFFFFF;
        final float grow = .94f + .06f * easeInOut(appear);
        g.imageTurned(icon(item), width / 2, itemY, itemW * grow, itemW * .75f * grow, yaw, item.knife ? 55 : 0, alpha);

        final int textAlpha = (int) (appear * 255) << 24;
        g.gradient(0, 0, width, 200, (int) (appear * 115) << 24, 0);
        g.gradient(0, H - 340, width, 340, 0, (int) (appear * 150) << 24);
        final String name = roll != null ? roll.displayName() : item.name;
        final float nameW = Math.min(width - 80, g.textWidth(name, 50, true, scale));
        centered(name, width / 2, 48, 50, true, textAlpha | 0xFFFFFF);
        final int rarity = Integer.parseInt(item.color.substring(1), 16);
        g.rect(width / 2 - nameW / 2 - 16, 114, nameW + 32, 6, textAlpha | rarity);

        drawDescription(item, roll, textAlpha);

        final float w = Math.min(BUTTON_ROW_W, width), x = (width - w) / 2, y = H - BUTTON_ROW_H;
        g.rect(x, y, w, 1, 0x80919191);
        final Runnable close = phase == Phase.PREVIEW ? () -> {
            setPhase(Phase.CASE);
            lootSince = now - LOOT_IN;
        } : this::backToInventory;
        button("CLOSE", x + w, y, 0, close);

        if (roll != null && roll.wear >= 0) {
            final float boxW = itemW * .7f, boxH = itemW * .55f;
            if (hovered(width / 2 - boxW / 2, itemY - boxH / 2, boxW, boxH)) drawTooltip(roll);
        }
    }

    private void drawDescription(final CaseData.Item item, final CaseData.Roll roll, final int textAlpha) {
        final float size = 18, line = 24, maxW = Math.min(900 - 56, width - 80);
        final List<String[]> lines = new ArrayList<>();
        final String white = Integer.toHexString(textAlpha | 0xE6E6E6), orange = Integer.toHexString(textAlpha | KILL_EATER & 0xFFFFFF), grey = Integer.toHexString(textAlpha | 0xC2C2C2);
        if (roll != null && roll.stattrak) {
            for (final String part : g.wrap("This item features StatTrak™ technology, which tracks certain statistics when equipped by its owner.", maxW, size, scale)) lines.add(new String[]{part, white});
            lines.add(new String[]{"This item tracks Confirmed Kills.", orange});
            lines.add(new String[]{"", white});
        }
        for (final String part : g.wrap(item.description, maxW, size, scale)) lines.add(new String[]{part, white});
        if (!item.flavour.isEmpty()) {
            lines.add(new String[]{"", white});
            for (final String part : g.wrap(item.flavour, maxW, size, scale)) lines.add(new String[]{part, grey});
        }
        final int shown = Math.min(lines.size(), 8);
        float y = H - BUTTON_ROW_H - 12 - shown * line;
        final float x = width / 2 - maxW / 2;
        for (int i = 0; i < shown; i++) {
            g.text(lines.get(i)[0], x, y, size, false, (int) Long.parseLong(lines.get(i)[1], 16), scale);
            y += line;
        }
    }

    private void drawTooltip(final CaseData.Roll roll) {
        final String[] lines = {"Exterior: " + roll.exterior(), String.format("Wear Rating: %.9f", roll.wear), "Pattern Template: " + roll.pattern};
        float w = 0;
        for (final String line : lines) w = Math.max(w, g.textWidth(line, 18, false, scale));
        final float boxW = w + 28, boxH = lines.length * 24 + 20;
        final float x = Math.min(mouseX + 18, width - boxW - 8), y = Math.min(mouseY + 18, H - boxH - 8);
        g.rect(x, y, boxW, boxH, 0xEE1B1C1F);
        g.rect(x, y, boxW, 1, 0xFF4A4B50);
        for (int i = 0; i < lines.length; i++) g.text(lines[i], x + 14, y + 10 + i * 24, 18, false, 0xFFFFFFFF, scale);
    }

    private void centered(final String text, final float cx, final float y, final float size, final boolean strong, final int color) {
        g.text(text, cx - g.textWidth(text, size, strong, scale) / 2, y, size, strong, color, scale);
    }

    private static float clamp(final float value, final float low, final float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static float easeInOut(final float t) {
        return t * t * (3 - 2 * t);
    }

    static double cubicBezier(final double x, final double x1, final double y1, final double x2, final double y2) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double t = x;
        for (int i = 0; i < 12; i++) {
            final double u = 1 - t;
            final double value = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t - x;
            final double slope = 3 * u * u * x1 + 6 * u * t * (x2 - x1) + 3 * t * t * (1 - x2);
            if (Math.abs(slope) < 1e-6) break;
            t = Math.max(0, Math.min(1, t - value / slope));
        }
        final double u = 1 - t;
        return 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t;
    }

    List<CaseData.Container> containers() {
        return containers;
    }

    int ticksPlayed() {
        return nextTick;
    }

    String phaseName() {
        return phase.name();
    }

    static double[] ticks() {
        return Arrays.copyOf(TICKS, TICKS.length);
    }
}
