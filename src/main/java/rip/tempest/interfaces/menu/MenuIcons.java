package rip.tempest.interfaces.menu;

import rip.tempest.utils.render.Glyphs;

import java.awt.Color;

public final class MenuIcons {
    @FunctionalInterface
    public interface Icon {
        void draw(float cx, float cy, float size, Color color);
    }

    private MenuIcons() {
    }

    private static float line(float size) {
        return Math.max(0.9f, size * 0.2f);
    }

    private static void path(float cx, float cy, float size, Color color, float... unit) {
        float[] points = new float[unit.length];
        for (int i = 0; i < unit.length; i += 2) {
            points[i] = cx + unit[i] * size;
            points[i + 1] = cy + unit[i + 1] * size;
        }
        Glyphs.path(line(size), 1f, color, points);
    }

    public static final Icon HOME = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.9f, -0.02f, 0f, -0.85f, 0.9f, -0.02f);
        path(cx, cy, s, c, -0.62f, -0.28f, -0.62f, 0.82f, 0.62f, 0.82f, 0.62f, -0.28f);
        path(cx, cy, s, c, -0.18f, 0.82f, -0.18f, 0.32f, 0.18f, 0.32f, 0.18f, 0.82f);
    };

    public static final Icon PERSON = (cx, cy, s, c) -> {
        Glyphs.ring(cx, cy - s * 0.42f, s * 0.36f, line(s), 0f, 360f, c);
        Glyphs.ring(cx, cy + s * 0.95f, s * 0.72f, line(s), 200f, 140f, c);
    };

    public static final Icon PEOPLE = (cx, cy, s, c) -> {
        float lineWidth = line(s);

        Color back = MenuTheme.alpha(c, 0.7f);
        Glyphs.ring(cx + s * 0.46f, cy - s * 0.46f, s * 0.24f, lineWidth, 0f, 360f, back);
        Glyphs.ring(cx + s * 0.52f, cy + s * 0.6f, s * 0.44f, lineWidth, 262f, 70f, back);
        Glyphs.ring(cx - s * 0.26f, cy - s * 0.36f, s * 0.31f, lineWidth, 0f, 360f, c);
        Glyphs.ring(cx - s * 0.26f, cy + s * 0.94f, s * 0.62f, lineWidth, 200f, 140f, c);
    };

    public static final Icon GLOBE = (cx, cy, s, c) -> {
        float r = s * 0.82f;
        Glyphs.ring(cx, cy, r, line(s), 0f, 360f, c);
        Glyphs.stroke(cx - r, cy, cx + r, cy, line(s), c);

        int steps = 24;
        float[] points = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            points[i * 2] = cx + (float) Math.cos(a) * r * 0.42f;
            points[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        Glyphs.path(line(s), 1f, c, points);
    };

    public static final Icon GRID = (cx, cy, s, c) -> {
        float g = 0.16f, e = 0.82f;
        for (int i = 0; i < 4; i++) {
            float x0 = i % 2 == 0 ? -e : g, y0 = i < 2 ? -e : g;
            float x1 = x0 + (e - g), y1 = y0 + (e - g);
            path(cx, cy, s, c, x0, y0, x1, y0, x1, y1, x0, y1, x0, y0);
        }
    };

    public static final Icon SLIDERS = (cx, cy, s, c) -> {
        float lineWidth = line(s);
        float[] rows = {-0.6f, 0f, 0.6f}, knobs = {0.3f, -0.35f, 0.15f};
        for (int i = 0; i < 3; i++) {
            float y = cy + rows[i] * s, knob = cx + knobs[i] * s, r = s * 0.2f;
            Glyphs.stroke(cx - s * 0.85f, y, knob - r - lineWidth * 0.5f, y, lineWidth, c);
            Glyphs.stroke(knob + r + lineWidth * 0.5f, y, cx + s * 0.85f, y, lineWidth, c);
            Glyphs.ring(knob, y, r, lineWidth, 0f, 360f, c);
        }
    };

    public static final Icon POWER = (cx, cy, s, c) -> {
        Glyphs.ring(cx, cy + s * 0.08f, s * 0.7f, line(s), -55f, 290f, c);
        path(cx, cy, s, c, 0f, -0.92f, 0f, -0.12f);
    };

    public static final Icon PLUS = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.75f, 0f, 0.75f, 0f);
        path(cx, cy, s, c, 0f, -0.75f, 0f, 0.75f);
    };

    public static final Icon REFRESH = (cx, cy, s, c) -> {
        float r = s * 0.66f, start = -60f, sweep = 290f;
        Glyphs.ring(cx, cy, r, line(s), start, sweep, c);

        double end = Math.toRadians(start + sweep);
        float tipX = cx + (float) Math.cos(end) * r, tipY = cy + (float) Math.sin(end) * r;
        double travel = Math.atan2(Math.cos(end), -Math.sin(end));
        float wing = s * 0.42f;
        double left = travel + Math.PI - 0.75, right = travel + Math.PI + 0.75;
        Glyphs.path(line(s), 1f, c,
                tipX + (float) Math.cos(left) * wing, tipY + (float) Math.sin(left) * wing,
                tipX, tipY,
                tipX + (float) Math.cos(right) * wing, tipY + (float) Math.sin(right) * wing);
    };

    public static final Icon BOLT = (cx, cy, s, c) ->
            path(cx, cy, s, c, 0.18f, -0.9f, -0.5f, 0.12f, 0.02f, 0.12f, -0.18f, 0.9f, 0.5f, -0.12f, -0.02f, -0.12f, 0.18f, -0.9f);

    public static final Icon TRASH = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.8f, -0.55f, 0.8f, -0.55f);
        path(cx, cy, s, c, -0.25f, -0.55f, -0.25f, -0.85f, 0.25f, -0.85f, 0.25f, -0.55f);
        path(cx, cy, s, c, -0.58f, -0.4f, -0.46f, 0.85f, 0.46f, 0.85f, 0.58f, -0.4f);
        path(cx, cy, s, c, -0.16f, -0.18f, -0.16f, 0.55f);
        path(cx, cy, s, c, 0.16f, -0.18f, 0.16f, 0.55f);
    };

    public static final Icon PENCIL = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.72f, 0.72f, -0.62f, 0.32f, 0.42f, -0.72f, 0.72f, -0.42f, -0.32f, 0.62f, -0.72f, 0.72f);
        path(cx, cy, s, c, 0.18f, -0.48f, 0.48f, -0.18f);
    };

    public static final Icon PLAY = (cx, cy, s, c) -> Glyphs.play(cx + s * 0.05f, cy, s * 0.8f, c);

    public static final Icon MINUS = (cx, cy, s, c) -> path(cx, cy, s, c, -0.75f, 0f, 0.75f, 0f);

    public static final Icon PAUSE = (cx, cy, s, c) -> {
        Glyphs.bar(cx - s * 0.55f, cy - s * 0.65f, s * 0.36f, s * 1.3f, c);
        Glyphs.bar(cx + s * 0.19f, cy - s * 0.65f, s * 0.36f, s * 1.3f, c);
    };

    public static final Icon FILM = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.9f, -0.66f, 0.9f, -0.66f, 0.9f, 0.66f, -0.9f, 0.66f, -0.9f, -0.66f);
        Glyphs.play(cx + s * 0.06f, cy, s * 0.4f, c);
    };

    public static final Icon FOLDER = (cx, cy, s, c) ->
            path(cx, cy, s, c, -0.9f, -0.66f, -0.3f, -0.66f, -0.12f, -0.42f, 0.9f, -0.42f, 0.9f, 0.7f, -0.9f, 0.7f, -0.9f, -0.66f);

    public static final Icon SEARCH = (cx, cy, s, c) -> {
        Glyphs.ring(cx - s * 0.15f, cy - s * 0.15f, s * 0.52f, line(s), 0f, 360f, c);
        path(cx, cy, s, c, 0.25f, 0.25f, 0.8f, 0.8f);
    };

    public static final Icon LOGIN = (cx, cy, s, c) -> {
        path(cx, cy, s, c, 0.05f, -0.8f, 0.72f, -0.8f, 0.72f, 0.8f, 0.05f, 0.8f);
        path(cx, cy, s, c, -0.82f, 0f, 0.3f, 0f);
        path(cx, cy, s, c, -0.05f, -0.34f, 0.3f, 0f, -0.05f, 0.34f);
    };

    public static final Icon DICE = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.75f, -0.75f, 0.75f, -0.75f, 0.75f, 0.75f, -0.75f, 0.75f, -0.75f, -0.75f);
        float d = line(s) * 0.85f;
        Glyphs.disc(cx - s * 0.33f, cy - s * 0.33f, d, c);
        Glyphs.disc(cx, cy, d, c);
        Glyphs.disc(cx + s * 0.33f, cy + s * 0.33f, d, c);
    };

    public static final Icon CUBE = (cx, cy, s, c) -> {
        path(cx, cy, s, c, 0f, -0.9f, 0.8f, -0.46f, 0.8f, 0.46f, 0f, 0.9f, -0.8f, 0.46f, -0.8f, -0.46f, 0f, -0.9f);
        path(cx, cy, s, c, -0.8f, -0.46f, 0f, 0f, 0.8f, -0.46f);
        path(cx, cy, s, c, 0f, 0f, 0f, 0.9f);
    };

    public static final Icon COPY = (cx, cy, s, c) -> {
        path(cx, cy, s, c, -0.35f, -0.35f, 0.8f, -0.35f, 0.8f, 0.8f, -0.35f, 0.8f, -0.35f, -0.35f);
        path(cx, cy, s, c, -0.8f, 0.35f, -0.8f, -0.8f, 0.35f, -0.8f);
    };

    public static final Icon CHECK = (cx, cy, s, c) -> Glyphs.check(cx, cy, s, line(s), 1f, c);

    public static final Icon CROSS = (cx, cy, s, c) -> Glyphs.cross(cx, cy, s * 1.1f, line(s), 1f, c);

    public static final Icon UP = (cx, cy, s, c) -> Glyphs.chevron(cx, cy, s * 0.6f, line(s), true, c);

    public static final Icon DOWN = (cx, cy, s, c) -> Glyphs.chevron(cx, cy, s * 0.6f, line(s), false, c);
}
