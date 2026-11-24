package rip.tempest.utils.render;

import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.Themes;

import java.awt.Color;

public enum ArrayPattern {
    Off("Off", Kind.OFF),

    IsraelFlag("Israel Flag", Kind.FLAG),
    UsaFlag("USA Flag", Kind.FLAG),
    UkFlag("UK Flag", Kind.FLAG),
    JapanFlag("Japan Flag", Kind.FLAG),
    FranceFlag("France Flag", Kind.FLAG),
    UkraineFlag("Ukraine Flag", Kind.FLAG),
    BrazilFlag("Brazil Flag", Kind.FLAG),
    SwedenFlag("Sweden Flag", Kind.FLAG),
    GermanyFlag("Germany Flag", Kind.FLAG),
    ItalyFlag("Italy Flag", Kind.FLAG),
    CanadaFlag("Canada Flag", Kind.FLAG),
    IrelandFlag("Ireland Flag", Kind.FLAG),
    NetherlandsFlag("Netherlands Flag", Kind.FLAG),
    SpainFlag("Spain Flag", Kind.FLAG),
    GreeceFlag("Greece Flag", Kind.FLAG),
    SwitzerlandFlag("Switzerland Flag", Kind.FLAG),
    NorwayFlag("Norway Flag", Kind.FLAG),
    FinlandFlag("Finland Flag", Kind.FLAG),
    DenmarkFlag("Denmark Flag", Kind.FLAG),
    PolandFlag("Poland Flag", Kind.FLAG),
    SouthKoreaFlag("South Korea Flag", Kind.FLAG),
    IndiaFlag("India Flag", Kind.FLAG),
    JamaicaFlag("Jamaica Flag", Kind.FLAG),
    ScotlandFlag("Scotland Flag", Kind.FLAG),
    ArgentinaFlag("Argentina Flag", Kind.FLAG),

    PrideFlag("Pride Flag", Kind.FLAG),
    ProgressPrideFlag("Progress Pride Flag", Kind.FLAG),
    TransFlag("Trans Flag", Kind.FLAG),
    BisexualFlag("Bisexual Flag", Kind.FLAG),
    PansexualFlag("Pansexual Flag", Kind.FLAG),
    NonbinaryFlag("Nonbinary Flag", Kind.FLAG),
    LesbianFlag("Lesbian Flag", Kind.FLAG),

    MatrixRain("Matrix Rain", Kind.SCENE),
    Fire("Fire", Kind.SCENE),
    Ocean("Ocean", Kind.SCENE),
    Aurora("Aurora", Kind.SCENE),
    Galaxy("Galaxy", Kind.SCENE),
    Plasma("Plasma", Kind.SCENE),
    Synthwave("Synthwave", Kind.SCENE),
    CandyStripes("Candy Stripes", Kind.SCENE),
    LavaLamp("Lava Lamp", Kind.SCENE),
    Snowfall("Snowfall", Kind.SCENE),
    CherryBlossom("Cherry Blossom", Kind.SCENE),
    Thunderstorm("Thunderstorm", Kind.SCENE),
    Slime("Slime", Kind.SCENE),
    Fireflies("Fireflies", Kind.SCENE),
    Confetti("Confetti", Kind.SCENE),
    Hologram("Hologram", Kind.SCENE),
    Glitch("Glitch", Kind.SCENE),
    Disco("Disco", Kind.SCENE),
    Camo("Camo", Kind.SCENE),
    Marble("Marble", Kind.SCENE),
    Kaleidoscope("Kaleidoscope", Kind.SCENE),
    DeepSea("Deep Sea", Kind.SCENE),

    Star("Star", Kind.SHAPE),
    Heart("Heart", Kind.SHAPE),
    Smiley("Smiley", Kind.SHAPE),
    Diamond("Diamond", Kind.SHAPE),
    Moon("Moon", Kind.SHAPE),
    Bolt("Lightning Bolt", Kind.SHAPE),
    Spiral("Spiral", Kind.SHAPE);

    private enum Kind { OFF, FLAG, SCENE, SHAPE }

    private static final double TAU = Math.PI * 2;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int INK = 0xFF5A5A5A;

    private final String label;
    private final Kind kind;

    ArrayPattern(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    @Override
    public String toString() {
        return label;
    }

    public boolean flag() {
        return kind == Kind.FLAG;
    }

    public boolean shape() {
        return kind == Kind.SHAPE;
    }

    public boolean animated(boolean motion) {
        if (this == Off) return false;
        return !flag() || motion;
    }

    public int color(double x, double y, double w, double h, double t, boolean motion) {
        return color(x, y, w, h, w / 2, w, t, motion);
    }

    public int color(double x, double y, double w, double h, double cx, double span, double t, boolean motion) {
        if (this == Off) return WHITE;
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (flag()) {
            if (!motion) return flagColor(x, y, w, h, cx, span);

            double phase = TAU * (x / w * 1.3 + y / h * 0.25) - t * 2.6;
            double dx = Math.cos(phase * 0.7) * w * 0.01, dy = Math.sin(phase) * h * 0.012;
            return scale(flagColor(x + dx, y + dy, w, h, cx, span), (float) (0.86 + 0.14 * Math.cos(phase)));
        }
        if (shape()) return shapeColor(x, y, w, h, cx, span, t, motion);
        return switch (this) {
            case MatrixRain -> matrix(x, y, h, t);
            case Fire -> fire(x, y, h, t);
            case Ocean -> ocean(x, y, h, t);
            case Aurora -> aurora(x, y, h, t);
            case Galaxy -> galaxy(x, y, t);
            case Plasma -> plasma(x, y, h, cx, t);
            case Synthwave -> synthwave(x, y, h, cx, span, t);
            case CandyStripes -> candy(x, y, t);
            case LavaLamp -> lava(x, y, w, h, t);
            case Snowfall -> snow(x, y, h, t);
            case CherryBlossom -> blossom(x, y, h, t);
            case Thunderstorm -> storm(x, y, h, cx, span, t);
            case Slime -> slime(x, y, h, t);
            case Fireflies -> fireflies(x, y, h, t);
            case Confetti -> confetti(x, y, h, t);
            case Hologram -> hologram(x, y, h, t);
            case Glitch -> glitch(x, y, t);
            case Disco -> disco(x, y, h, cx, span, t);
            case Camo -> camo(x, y, t);
            case Marble -> marble(x, y, t);
            case Kaleidoscope -> kaleidoscope(x, y, h, cx, t);
            default -> deepSea(x, y, h, t);
        };
    }

    private int flagColor(double x, double y, double w, double h, double cx, double span) {
        return switch (this) {
            case IsraelFlag -> israel(x, y, h, cx, span);
            case UsaFlag -> usa(x, y, w, h);
            case UkFlag -> uk(x, y, w, h, cx);
            case JapanFlag -> japan(x, y, h, cx, span);
            case FranceFlag -> columns(x, w, 0xFF2F5FD0, WHITE, 0xFFEF4135);
            case UkraineFlag -> stripes(y, h, 0xFF2A7DE1, 0xFFFFD500);
            case BrazilFlag -> brazil(x, y, h, cx, span);
            case SwedenFlag -> nordic(x, y, w, h, cx, span, 0xFF2E7FD1, 0xFFFECC00, 0xFFFECC00);
            case GermanyFlag -> stripes(y, h, INK, 0xFFFF3B3B, 0xFFFFCE00);
            case ItalyFlag -> columns(x, w, 0xFF1FB25A, WHITE, 0xFFEF3B47);
            case CanadaFlag -> canada(x, y, h, cx, span);
            case IrelandFlag -> columns(x, w, 0xFF1FBF6E, WHITE, 0xFFFF883E);
            case NetherlandsFlag -> stripes(y, h, 0xFFE0303C, WHITE, 0xFF3A6AD0);
            case SpainFlag -> stripes(y, h, 0xFFE0262E, 0xFFF1BF00, 0xFFF1BF00, 0xFFE0262E);
            case GreeceFlag -> greece(x, y, w, h);
            case SwitzerlandFlag -> swiss(x, y, h, cx, span);
            case NorwayFlag -> nordic(x, y, w, h, cx, span, 0xFFE0303F, WHITE, 0xFF2F55B8);
            case FinlandFlag -> nordic(x, y, w, h, cx, span, WHITE, 0xFF2F62D0, 0xFF2F62D0);
            case DenmarkFlag -> nordic(x, y, w, h, cx, span, 0xFFE8303F, WHITE, WHITE);
            case PolandFlag -> stripes(y, h, WHITE, 0xFFE8304F);
            case SouthKoreaFlag -> korea(x, y, h, cx, span);
            case IndiaFlag -> india(x, y, h, cx, span);
            case JamaicaFlag -> jamaica(x, y, w, h);
            case ScotlandFlag -> diagonalDistance(x, y, w, h) < Math.min(w, h) * 0.11 ? WHITE : 0xFF2F7FE0;
            case ArgentinaFlag -> argentina(x, y, h, cx, span);
            case PrideFlag -> stripes(y, h, PRIDE);
            case ProgressPrideFlag -> progress(x, y, w, h);
            case TransFlag -> stripes(y, h, 0xFF5BCEFA, 0xFFF5A9B8, WHITE, 0xFFF5A9B8, 0xFF5BCEFA);
            case BisexualFlag -> stripes(y, h, 0xFFE0307F, 0xFFE0307F, 0xFFA45BB0, 0xFF3A66E0, 0xFF3A66E0);
            case PansexualFlag -> stripes(y, h, 0xFFFF218C, 0xFFFFD800, 0xFF21B1FF);
            case NonbinaryFlag -> stripes(y, h, 0xFFFCF434, WHITE, 0xFFA569DB, INK);
            default -> stripes(y, h, 0xFFE8501F, 0xFFFF9A56, WHITE, 0xFFD362A4, 0xFFC0307A);
        };
    }

    private static final int[] PRIDE = {0xFFF03A3A, 0xFFFF8C00, 0xFFFFED00, 0xFF24B04E, 0xFF3070FF, 0xFFA84ADB};

    private static int stripes(double y, double h, int... colors) {
        int i = (int) Math.floor(y / h * colors.length);
        return colors[Math.max(0, Math.min(colors.length - 1, i))];
    }

    private static int columns(double x, double w, int... colors) {
        int i = (int) Math.floor(x / w * colors.length);
        return colors[Math.max(0, Math.min(colors.length - 1, i))];
    }

    private static int israel(double x, double y, double h, double cx, double span) {
        int blue = 0xFF1E5EFF;
        double v = y / h;
        if (v >= 15 / 160.0 && v < 40 / 160.0 || v >= 120 / 160.0 && v < 145 / 160.0) return blue;
        double radius = Math.min(span * 0.48, h * 0.22);
        double band = Math.max(1.5, radius * 0.22);
        return starOfDavid(x - cx, y - h / 2, radius, band) ? blue : WHITE;
    }

    private static boolean starOfDavid(double dx, double dy, double radius, double band) {
        double inradius = radius / 2;

        double down = dy, upLeft = -0.8660254 * dx - 0.5 * dy, upRight = 0.8660254 * dx - 0.5 * dy;
        double up = Math.max(down, Math.max(upLeft, upRight));
        double flipped = Math.max(-down, Math.max(-upLeft, -upRight));
        return up <= inradius && up >= inradius - band || flipped <= inradius && flipped >= inradius - band;
    }

    private static int usa(double x, double y, double w, double h) {
        int stripe = (int) Math.min(12, Math.max(0, Math.floor(y / h * 13)));
        double cantonW = w * 0.42, cantonH = h * 7 / 13;
        if (x < cantonW && y < cantonH) {
            double cellW = cantonW / 6, cellH = cantonH / 5;
            int row = (int) Math.floor(y / cellH);
            double shift = row % 2 == 0 ? 0 : cellW / 2;
            double fx = ((x - shift) % cellW + cellW) % cellW - cellW / 2, fy = y - (row + 0.5) * cellH;
            return Math.hypot(fx, fy) < Math.min(cellW, cellH) * 0.28 ? WHITE : 0xFF3D4DB7;
        }
        return stripe % 2 == 0 ? 0xFFD62839 : WHITE;
    }

    private static double diagonalDistance(double x, double y, double w, double h) {
        return Math.min(Math.abs(x * h - y * w), Math.abs((w - x) * h - y * w)) / Math.hypot(w, h);
    }

    private static int uk(double x, double y, double w, double h, double cx) {
        int red = 0xFFE0303A;
        double m = Math.min(w, h);
        double crossX = Math.abs(x - cx), crossY = Math.abs(y - h / 2);
        if (crossX < m * 0.10 || crossY < m * 0.10) return red;
        if (crossX < m * 0.17 || crossY < m * 0.17) return WHITE;
        double d = diagonalDistance(x, y, w, h);
        if (d < m * 0.05) return red;
        if (d < m * 0.12) return WHITE;
        return 0xFF1F3FA0;
    }

    private static int japan(double x, double y, double h, double cx, double span) {
        double radius = Math.min(span * 0.45, h * 0.3);
        return Math.hypot(x - cx, y - h / 2) <= radius ? 0xFFE0243C : WHITE;
    }

    private static int brazil(double x, double y, double h, double cx, double span) {
        double dx = x - cx, dy = y - h / 2;
        double globe = Math.min(span * 0.32, h * 0.16);
        if (Math.hypot(dx, dy) <= globe) {
            return Math.abs(dy + dx * 0.25 + globe * 0.05) < globe * 0.13 ? WHITE : 0xFF2348A8;
        }
        if (Math.abs(dx) / (span * 0.5) + Math.abs(dy) / (h * 0.36) <= 1) return 0xFFFFDF00;
        return 0xFF1FA54A;
    }

    private static int nordic(double x, double y, double w, double h, double cx, double span, int field, int outer, int inner) {
        double m = Math.min(w, h);
        double ux = Math.abs(x - (cx - span * 0.15)), uy = Math.abs(y - h / 2);
        if (ux < m * 0.055 || uy < m * 0.055) return inner;
        if (ux < m * 0.10 || uy < m * 0.10) return outer;
        return field;
    }

    private static int canada(double x, double y, double h, double cx, double span) {
        int red = 0xFFE8343A;
        if (Math.abs(x - cx) > span * 0.5) return red;
        double radius = Math.min(span * 0.44, h * 0.2);
        return maple(x - cx, y - h / 2, radius) ? red : WHITE;
    }

    private static final double[][] MAPLE = {{0.00, -1.00}, {0.14, -0.74}, {0.28, -0.80}, {0.22, -0.34}, {0.44, -0.56},
            {0.50, -0.44}, {0.78, -0.50}, {0.68, -0.24}, {0.78, -0.18}, {0.44, 0.08}, {0.50, 0.22}, {0.06, 0.16}, {0.06, 0.64},
            {0.00, 0.64}};

    private static boolean maple(double dx, double dy, double radius) {
        return inPolygon(MAPLE, Math.abs(dx / radius), dy / radius - 0.15);
    }

    private static boolean inPolygon(double[][] points, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            double xi = points[i][0], yi = points[i][1], xj = points[j][0], yj = points[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    private static int greece(double x, double y, double w, double h) {
        int blue = 0xFF2E7DE0;
        double cantonW = w * 0.42, cantonH = h * 5 / 9;
        if (x < cantonW && y < cantonH) {
            return Math.abs(x - cantonW / 2) < cantonW * 0.1 || Math.abs(y - cantonH / 2) < cantonH * 0.1 ? WHITE : blue;
        }
        return (int) Math.floor(y / h * 9) % 2 == 0 ? blue : WHITE;
    }

    private static int swiss(double x, double y, double h, double cx, double span) {
        double arm = Math.min(span, h) * 0.38, thick = arm * 0.32;
        double dx = Math.abs(x - cx), dy = Math.abs(y - h / 2);
        boolean cross = dx < thick && dy < arm || dy < thick && dx < arm;
        return cross ? WHITE : 0xFFE8343A;
    }

    private static int korea(double x, double y, double h, double cx, double span) {
        double radius = Math.min(span * 0.3, h * 0.13);
        double dx = x - cx, dy = y - h / 2;
        double d = Math.hypot(dx, dy);
        if (d <= radius) {
            double c = Math.cos(0.59), s = Math.sin(0.59);
            double rx = dx * c + dy * s, ry = -dx * s + dy * c;
            boolean red = ry < 0;
            if (Math.hypot(rx + radius / 2, ry) < radius / 2) red = true;
            if (Math.hypot(rx - radius / 2, ry) < radius / 2) red = false;
            return red ? 0xFFE0303F : 0xFF2F62D0;
        }

        int[][] broken = {{0, 0, 0}, {1, 0, 1}, {0, 1, 0}, {1, 1, 1}};
        double[][] dirs = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            double ux = dirs[i][0] / Math.sqrt(2), uy = dirs[i][1] / Math.sqrt(2);
            double along = dx * ux + dy * uy - radius * 1.55, across = -dx * uy + dy * ux;
            if (Math.abs(across) > radius * 0.42 || Math.abs(along) > radius * 0.33) continue;
            double slot = (along + radius * 0.33) / (radius * 0.22);
            int bar = Math.min(2, (int) Math.floor(slot));
            if (slot - Math.floor(slot) > 0.62) continue;
            if (broken[i][bar] == 1 && Math.abs(across) < radius * 0.06) continue;
            return INK;
        }
        return WHITE;
    }

    private static int india(double x, double y, double h, double cx, double span) {
        int band = stripes(y, h, 0xFFFF9933, WHITE, 0xFF1FAF4A);
        double radius = Math.min(span * 0.4, h * 0.14);
        double dx = x - cx, dy = y - h / 2;
        double d = Math.hypot(dx, dy);
        if (d > radius) return band;
        int navy = 0xFF3A4AD0;
        if (d > radius * 0.86 || d < radius * 0.16) return navy;
        double spoke = TAU / 24;
        double a = ((Math.atan2(dy, dx) % spoke) + spoke) % spoke;
        return Math.min(a, spoke - a) * d < radius * 0.05 ? navy : WHITE;
    }

    private static int jamaica(double x, double y, double w, double h) {
        if (diagonalDistance(x, y, w, h) < Math.min(w, h) * 0.09) return 0xFFFED100;
        return Math.abs(y - h / 2) / h > Math.abs(x - w / 2) / w ? 0xFF1FBF5A : INK;
    }

    private static int argentina(double x, double y, double h, double cx, double span) {
        int band = stripes(y, h, 0xFF74ACDF, WHITE, 0xFF74ACDF);
        double radius = Math.min(span * 0.42, h * 0.13);
        double dx = x - cx, dy = y - h / 2;
        double d = Math.hypot(dx, dy);
        if (d <= radius * 0.42) return 0xFFF6B40E;
        if (d > radius) return band;
        double f = Math.atan2(dy, dx) / TAU * 16;
        f -= Math.floor(f);
        return d < radius * (0.42 + 0.58 * (1 - 2 * Math.abs(f - 0.5))) ? 0xFFF6B40E : band;
    }

    private static int progress(double x, double y, double w, double h) {
        double depth = w * 0.55;
        double d = x + Math.abs(y - h / 2) * (depth / (h / 2));
        int band = (int) Math.floor(d / (depth / 5));
        return switch (band) {
            case 0 -> WHITE;
            case 1 -> 0xFFF5A9B8;
            case 2 -> 0xFF5BCEFA;
            case 3 -> 0xFFA0673A;
            case 4 -> INK;
            default -> stripes(y, h, PRIDE);
        };
    }

    private static int matrix(double x, double y, double h, double t) {
        int column = (int) Math.floor(x / 3.0);
        double lit = 0;
        for (int drop = 0; drop < 2; drop++) {
            int k = drop * 7;
            double speed = 35 + hash(column, 1 + k) * 70;
            double trail = 25 + hash(column, 2 + k) * 55;
            double period = h + trail + 20 + hash(column, 3 + k) * 60;
            double head = (t * speed + hash(column, 4 + k) * period) % period;
            double behind = head - y;
            if (behind >= 0 && behind < 2.5) return 0xFFDCFFDC;
            if (behind >= 0 && behind < trail) lit = Math.max(lit, 1 - behind / trail);
        }
        int cell = (int) Math.floor(y / 4.0);
        double flicker = hash(column * 131 + cell, (int) Math.floor(t * 8));
        double g = (0.42 + 0.58 * lit) * (0.82 + 0.18 * flicker);
        return rgb(g * 60, g * 255, g * 85);
    }

    private static int fire(double x, double y, double h, double t) {
        double v = y / h;
        double n = fbm(x * 0.09, y * 0.07 + t * 1.6);
        double heat = clamp01(v * 1.15 - 0.1 + (n - 0.5) * 0.75);
        return ramp(heat, 0xFFB3121B, 0xFFFF4D00, 0xFFFFA200, 0xFFFFE066, 0xFFFFFFE0);
    }

    private static int ocean(double x, double y, double h, double t) {
        double c = Math.sin(x * 0.16 + t * 1.1) + Math.sin(y * 0.13 - t * 1.4) + Math.sin((x + y) * 0.08 + t * 0.6);
        double foam = Math.pow(Math.max(0, 1 - Math.abs(c) * 1.6), 3);
        int water = Themes.mix(0xFF2B9BFF, 0xFF0D5AA8, (float) clamp01(y / h));
        return Themes.mix(water, 0xFFE6FFFF, (float) (foam * 0.85));
    }

    private static int aurora(double x, double y, double h, double t) {
        double v = clamp01(y / h);
        int night = Themes.mix(0xFF5677B8, 0xFF3E5294, (float) v);
        double s = x * 0.06 + Math.sin(y * 0.025 + t * 0.7) * 1.8 + t * 0.35;
        double b1 = 0.5 + 0.5 * Math.sin(s * Math.PI), b2 = 0.5 + 0.5 * Math.sin(s * 1.7 + 1.3);
        b1 *= b1;
        b2 *= b2;
        b2 *= b2;
        double band = b1 * b1, second = b2 * b2 * 0.6;
        double glow = Math.min(1, (band + second) * (0.6 + 0.4 * fbm(x * 0.05, y * 0.02 - t * 0.3)));
        int curtain = Themes.mix(0xFF39FF9F, 0xFFC77DFF, (float) clamp01(v * 1.2));
        return Themes.mix(night, curtain, (float) (0.25 + glow * 0.75));
    }

    private static int galaxy(double x, double y, double t) {
        double n = fbm(x * 0.05 + t * 0.05, y * 0.05);
        int nebula = ramp(n, 0xFF3B2A86, 0xFF5A3BB0, 0xFF9A48C2, 0xFFE07BD8, 0xFFFFC4F0);
        int cellX = (int) Math.floor(x / 4), cellY = (int) Math.floor(y / 4);
        double seed = hash(cellX * 7, cellY * 13);
        if (seed > 0.86) {
            double fx = x / 4 - cellX - 0.5, fy = y / 4 - cellY - 0.5;
            if (Math.hypot(fx, fy) < 0.28) {
                double twinkle = 0.5 + 0.5 * Math.sin(t * (2 + seed * 4) + seed * 40);
                return Themes.mix(nebula, WHITE, (float) twinkle);
            }
        }
        return nebula;
    }

    private static int plasma(double x, double y, double h, double cx, double t) {
        double v = Math.sin(x * 0.10 + t) + Math.sin(y * 0.08 - t * 1.2) + Math.sin((x + y) * 0.06 + t * 0.7)
                + Math.sin(Math.hypot(x - cx, y - h / 2) * 0.10 - t * 1.5);
        double hue = v / 8 + 0.5 + t * 0.05;
        return Color.HSBtoRGB((float) (hue - Math.floor(hue)), 0.6f, 1f);
    }

    private static int synthwave(double x, double y, double h, double cx, double span, double t) {
        double v = y / h, horizon = 0.55;
        if (v < horizon) {
            double u = v / horizon;
            int sky = u < 0.5 ? Themes.mix(0xFF9B4DFF, 0xFFFF2E97, (float) (u * 2)) : Themes.mix(0xFFFF2E97, 0xFFFFB86B, (float) (u * 2 - 1));
            double radius = Math.min(span * 0.45, h * 0.18);
            double sunY = horizon * h - radius * 0.25;
            double dy = y - sunY;
            if (Math.hypot(x - cx, dy) <= radius) {
                boolean slit = dy > 0 && ((dy / (radius * 0.2) - t * 0.6) % 1 + 1) % 1 < 0.28;
                if (!slit) return Themes.mix(0xFFFFE066, 0xFFFF4FA3, (float) clamp01((dy + radius) / (radius * 2)));
            }
            return sky;
        }
        double depth = (v - horizon) * h;
        int ground = 0xFF7D45C9;
        double z = 40 / (depth + 2);
        double across = (x - cx) / (depth + 2) * 3;
        double rows = (z * 0.25 + t * 0.8) % 1;
        double columns = Math.abs(across - Math.rint(across));
        float fade = (float) clamp01(depth / (h * 0.15));
        if (rows < 0.12) return Themes.mix(ground, 0xFFFF4FD8, fade);
        if (columns < 0.06) return Themes.mix(ground, 0xFF2DE2E6, fade);
        return ground;
    }

    private static int candy(double x, double y, double t) {
        int stripe = Math.floorMod((int) Math.floor((x + y + t * 18) / 7), 3);
        return stripe == 0 ? 0xFFFF7EB6 : stripe == 1 ? WHITE : 0xFF7EE8FA;
    }

    private static int lava(double x, double y, double w, double h, double t) {
        double field = 0;
        for (int i = 0; i < 5; i++) {
            double bx = w * (0.5 + 0.35 * Math.sin(t * (0.3 + 0.07 * i) + i * 1.7));
            double by = h * (0.5 + 0.42 * Math.sin(t * (0.22 + 0.05 * i) + i * 2.3));
            double r = Math.min(w, h) * (0.16 + 0.04 * (i % 3));
            double dx = x - bx, dy = y - by;
            field += r * r / (dx * dx + dy * dy + 1);
        }
        if (field > 1) return Themes.mix(0xFFFF7A00, 0xFFFFE14D, (float) clamp01((field - 1) * 0.6));
        int back = Themes.mix(0xFFA052D6, 0xFFD0457F, (float) clamp01(y / h));
        return Themes.mix(back, 0xFFFF5E3A, (float) (field * field * field * 0.6));
    }

    private static int snow(double x, double y, double h, double t) {
        int sky = Themes.mix(0xFF7398DC, 0xFF4F6CB8, (float) clamp01(y / h));
        double flake = 0;
        for (int layer = 0; layer < 3; layer++) {
            double size = 5 + layer * 3;
            double fy = y - t * (7 + layer * 7);
            double fx = x + Math.sin(t * 0.9 + fy * 0.05 + layer * 2) * (1 + layer);
            int gx = (int) Math.floor(fx / size), gy = (int) Math.floor(fy / size);
            if (hash(gx * 31 + layer * 977, gy * 17 + layer) < 0.5) continue;
            double px = (gx + 0.2 + 0.6 * hash(gx, gy + 5 + layer)) * size;
            double py = (gy + 0.2 + 0.6 * hash(gx + 9, gy + layer)) * size;
            double d = Math.hypot(fx - px, fy - py);
            double r = 0.55 + layer * 0.35;
            flake = Math.max(flake, clamp01(1 - (d - r) / 0.8));
        }
        return Themes.mix(sky, WHITE, (float) flake);
    }

    private static int blossom(double x, double y, double h, double t) {
        int sky = Themes.mix(0xFFFFB8D6, 0xFFC9A2FF, (float) clamp01(y / h));
        for (int layer = 0; layer < 2; layer++) {
            double size = 8 + layer * 4;
            double speed = 10 + layer * 8;
            double fy = y - t * speed;
            double fx = x + t * speed * 0.45 + Math.sin(t * 1.2 + fy * 0.04 + layer) * 2.5;
            int gx = (int) Math.floor(fx / size), gy = (int) Math.floor(fy / size);
            double seed = hash(gx * 13 + layer * 501, gy * 7 + layer);
            if (seed < 0.45) continue;
            double px = (gx + 0.25 + 0.5 * hash(gx, gy + 3)) * size, py = (gy + 0.25 + 0.5 * hash(gx + 4, gy)) * size;
            double a = t * (1 + seed * 2) + seed * 12;
            double dx = fx - px, dy = fy - py;
            double ex = (dx * Math.cos(a) + dy * Math.sin(a)) / (1.6 + layer * 0.6);
            double ey = (-dx * Math.sin(a) + dy * Math.cos(a)) / (0.9 + layer * 0.35) * (0.6 + 0.4 * Math.abs(Math.cos(a * 0.7)));
            double e = ex * ex + ey * ey;
            if (e < 1) return Themes.mix(0xFFFF4F93, WHITE, (float) (seed > 0.75 ? 0.75 : e * 0.5));
        }
        return sky;
    }

    private static int storm(double x, double y, double h, double cx, double span, double t) {
        double n = fbm(x * 0.06 + t * 0.25, y * 0.05);
        int c = ramp(n, 0xFF46508A, 0xFF5E69AA, 0xFF8890CC, 0xFFB6BCE6);
        double slant = x + y * 0.3;
        int lane = (int) Math.floor(slant / 3);
        if (hash(lane, 5) > 0.55 && ((y + t * 90 + hash(lane, 9) * 60) % 20) < 4) c = Themes.mix(c, 0xFFDDE4FF, 0.4f);

        double period = 3.1;
        int strike = (int) Math.floor(t / period);
        double into = t - strike * period;
        double flash = into < 0.08 ? 1 : into < 0.14 ? 0.3 : into < 0.2 ? 0.9 : Math.max(0, (0.5 - into) / 0.3) * 0.6;
        if (into < 0.3) {
            double bx = cx + (hash(strike, 1) - 0.5) * span * 0.6
                    + (noise(y * 0.09, strike * 5.0) - 0.5) * span * 0.6
                    + (noise(y * 0.45, strike * 3.0 + 50) - 0.5) * span * 0.14;
            double d = Math.abs(x - bx);
            if (d < 0.9) return 0xFFF4F7FF;
            if (d < 3.5) c = Themes.mix(c, 0xFFC9D4FF, (float) (1 - d / 3.5));
        }
        return Themes.mix(c, 0xFFE8ECFF, (float) (flash * 0.55));
    }

    private static int slime(double x, double y, double h, double t) {
        int back = Themes.mix(0xFFAA80EA, 0xFF8460CC, (float) clamp01(y / h));
        int green = 0xFF8CFF3F, light = 0xFFE4FFB0;
        double top = h * 0.06 + Math.sin(x * 0.35 + t * 1.5) * 1.2;
        if (y < top) return y > top - 1 ? light : green;
        double col = 6;
        int c = (int) Math.floor(x / col);
        if (hash(c, 13) < 0.3) return back;
        double centre = (c + 0.5) * col;
        double cycle = (t * (0.06 + hash(c, 3) * 0.1) + hash(c, 7)) % 1;
        double end = top + cycle * h * 1.15;
        double r = col * (0.22 + 0.1 * hash(c, 11));
        double dx = x - centre;
        if (y < end) {
            double along = (y - top) / Math.max(1, end - top);
            double width = r * (0.45 + 0.4 * along);
            if (Math.abs(dx) < width) return dx < -width * 0.2 && dx > -width * 0.65 ? light : green;
        }
        double d = Math.hypot(dx, (y - end) * 0.85);
        if (d < r * 1.35) return Math.hypot(dx + r * 0.45, y - end + r * 0.4) < r * 0.35 ? light : green;
        return back;
    }

    private static int fireflies(double x, double y, double h, double t) {
        int forest = Themes.mix(0xFF3E9A66, 0xFF2A6E4A, (float) clamp01(y / h));
        forest = Themes.mix(forest, 0xFF2E7D50, (float) (fbm(x * 0.07, y * 0.07) * 0.6));
        double cell = 12, glow = 0;
        int gx = (int) Math.floor(x / cell), gy = (int) Math.floor(y / cell);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int ax = gx + i, ay = gy + j;
                double seed = hash(ax * 17, ay * 23);
                if (seed < 0.3) continue;
                double px = (ax + 0.5) * cell + Math.sin(t * (0.5 + seed) + seed * 20) * cell * 0.45;
                double py = (ay + 0.5) * cell + Math.cos(t * (0.4 + seed * 0.7) + seed * 13) * cell * 0.45;
                double d2 = (x - px) * (x - px) + (y - py) * (y - py);
                if (d2 > 30) continue;
                double pulse = Math.pow(0.5 + 0.5 * Math.sin(t * (1.5 + seed * 2) + seed * 30), 2);
                glow = Math.max(glow, pulse * Math.exp(-d2 / 5));
            }
        }
        return Themes.mix(forest, 0xFFFFF27A, (float) Math.min(1, glow * 1.3));
    }

    private static final int[] CONFETTI = {0xFFFF4F79, 0xFFFFD23F, 0xFF3BCEAC, 0xFF4CC9F0, 0xFFF72585, 0xFFB5FF3D, WHITE};

    private static int confetti(double x, double y, double h, double t) {
        int back = Themes.mix(0xFF8F8AE6, 0xFF6F6AC9, (float) clamp01(y / h));
        for (int layer = 0; layer < 2; layer++) {
            double size = 6 + layer * 3;
            double fy = y - t * (14 + layer * 10);
            int gx = (int) Math.floor(x / size);
            double fx = x + Math.sin(t * 1.5 + gx + layer) * 1.2;
            gx = (int) Math.floor(fx / size);
            int gy = (int) Math.floor(fy / size);
            double seed = hash(gx * 29 + layer * 313, gy * 11 + layer);
            if (seed < 0.4) continue;
            double px = (gx + 0.5) * size, py = (gy + 0.5) * size;
            double a = t * (2 + seed * 3) + seed * 20;
            double dx = fx - px, dy = fy - py;
            double u = dx * Math.cos(a) + dy * Math.sin(a), v = -dx * Math.sin(a) + dy * Math.cos(a);
            double flip = 0.25 + 0.75 * Math.abs(Math.cos(t * (3 + seed * 4) + seed * 9));
            if (Math.abs(u) < 1.7 + layer * 0.4 && Math.abs(v) < (0.8 + layer * 0.2) * flip) {
                return CONFETTI[(int) (hash(gx, gy + 77) * CONFETTI.length) % CONFETTI.length];
            }
        }
        return back;
    }

    private static int hologram(double x, double y, double h, double t) {
        int frame = (int) Math.floor(t * 6);
        int band = (int) Math.floor(y / 6);
        if (hash(band, frame) > 0.92) x += (hash(band + 3, frame) - 0.5) * 20;
        double hue = 0.52 + 0.1 * Math.sin(y * 0.05 + t * 0.8) + 0.07 * Math.sin(x * 0.07 - t * 0.5);
        int c = Color.HSBtoRGB((float) hue, 0.5f, 1f);
        if ((int) Math.floor(y / 1.5) % 2 == 0) c = scale(c, 0.82f);
        double sweep = (t * 40) % (h + 40) - 20;
        double bar = Math.exp(-Math.pow((y - sweep) / 4, 2));
        c = Themes.mix(c, WHITE, (float) (bar * 0.6));
        return scale(c, (float) (0.93 + 0.07 * hash(frame, 1)));
    }

    private static int glitch(double x, double y, double t) {
        int frame = (int) Math.floor(t * 8);
        int band = (int) Math.floor(y / 5);
        double g = hash(band * 7, frame);
        double xs = x + (g > 0.8 ? (hash(band, frame + 99) - 0.5) * 30 : 0);
        int c = Themes.mix(0xFFFF4FD8, 0xFF2DE2E6, (float) (0.5 + 0.5 * Math.sin(xs * 0.05 + y * 0.03 + t * 0.6)));
        if (g > 0.93) c = c & 0xFF00FF00 | (c >> 16 & 255) | (c & 255) << 16;
        int bx = (int) Math.floor(xs / 4), by = (int) Math.floor(y / 3);
        double block = hash(bx * 13 + by * 7, frame + 5);
        if (block > 0.975) return WHITE;
        if (block > 0.955) return 0xFFB6FF2B;
        return c;
    }

    private static final int[] DISCO = {0xFFFF3CAC, 0xFF2DE2E6, 0xFFFFD23F, 0xFF9B5CFF, 0xFF3BFF8A, 0xFFFF7A3C};

    private static int disco(double x, double y, double h, double cx, double span, double t) {
        double beats = t / 0.5;
        int beat = (int) Math.floor(beats);
        double into = beats - beat;
        int tx = (int) Math.floor(x / 6), ty = (int) Math.floor(y / 6);
        int c = DISCO[(int) (hash(tx * 31 + ty, beat) * DISCO.length) % DISCO.length];
        c = scale(c, (float) (0.68 + 0.32 * (1 - into) * (1 - into)));
        double gx = x - tx * 6, gy = y - ty * 6;
        if (gx < 0.5 || gy < 0.5) c = scale(c, 0.75f);
        double sx = cx + Math.sin(t * 1.3) * span * 0.6, sy = h * (0.5 + 0.4 * Math.sin(t * 0.9));
        double spot = Math.exp(-((x - sx) * (x - sx) + (y - sy) * (y - sy)) / Math.max(1, span * span * 0.08));
        return Themes.mix(c, WHITE, (float) (spot * 0.5));
    }

    private static int camo(double x, double y, double t) {
        double a = fbm(x * 0.06 + t * 0.08, y * 0.05), b = fbm(x * 0.09 + 40, y * 0.08 - t * 0.06);
        if (b > 0.62) return 0xFF667A4C;
        if (a > 0.58) return 0xFF9C7852;
        if (a < 0.4) return 0xFFAAC07F;
        return 0xFF78985A;
    }

    private static int marble(double x, double y, double t) {
        double turb = fbm(x * 0.04, y * 0.04 + t * 0.05) * 7;
        double a = 1 - Math.abs(Math.sin(x * 0.05 + y * 0.09 + turb));
        double a2 = a * a, a4 = a2 * a2, vein = a4 * a4 * a2;
        double b = 1 - Math.abs(Math.sin(x * 0.03 - y * 0.05 + turb * 1.3 + 2));
        double b2 = b * b, b4 = b2 * b2, b8 = b4 * b4, gold = b8 * b8 * b8 * b4 * b2;
        int c = Themes.mix(0xFFF4F4F8, 0xFFC9CDD8, (float) fbm(x * 0.1, y * 0.1));
        c = Themes.mix(c, 0xFF8088A0, (float) (vein * 0.85));
        return Themes.mix(c, 0xFFE0B94A, (float) gold);
    }

    private static int kaleidoscope(double x, double y, double h, double cx, double t) {
        double dx = x - cx, dy = y - h / 2;
        double r = Math.hypot(dx, dy);
        double sector = TAU / 8;
        double a = ((Math.atan2(dy, dx) + t * 0.3) % sector + sector) % sector;
        if (a > sector / 2) a = sector - a;
        double px = r * Math.cos(a), py = r * Math.sin(a);
        double v = Math.sin(px * 0.18 - t * 1.2) + Math.sin(py * 0.25 + t) + Math.sin((px + py) * 0.12 + t * 0.5) + Math.sin(r * 0.15 - t * 1.6);
        double hue = v / 8 + 0.5 + t * 0.04;
        return Color.HSBtoRGB((float) (hue - Math.floor(hue)), 0.62f, 1f);
    }

    private static int deepSea(double x, double y, double h, double t) {
        double v = clamp01(y / h);
        int c = Themes.mix(0xFF3AD2D2, 0xFF2E62C8, (float) v);
        double ray = Math.pow(Math.max(0, Math.sin((x + y * 0.4) * 0.12 + Math.sin(t * 0.4) * 2)), 6) * (1 - v);
        c = Themes.mix(c, 0xFFD2FFFF, (float) (ray * 0.4));
        double size = 9;
        int col = (int) Math.floor(x / size);
        double fy = y + t * (10 + hash(col, 2) * 14);
        int row = (int) Math.floor(fy / size);
        double seed = hash(col * 5, row * 3);
        if (seed > 0.5) {
            double px = (col + 0.5) * size + Math.sin(row * 1.7 + t * 2) * 1.5, py = (row + 0.5) * size;
            double r = 1.2 + (seed - 0.5) * 3.6;
            double d = Math.hypot(x - px, fy - py);
            if (Math.abs(d - r) < 0.55) return Themes.mix(c, WHITE, 0.85f);
            if (Math.hypot(x - px + r * 0.35, fy - py + r * 0.35) < r * 0.28) return WHITE;
            if (d < r) return Themes.mix(c, WHITE, 0.2f);
        }
        return c;
    }

    private int shapeColor(double x, double y, double w, double h, double cx, double span, double t, boolean motion) {
        int base = base(y / h * 0.8 + x / w * 0.2, t);
        double radius = Math.min(span, h) * 0.46;
        double dx = x - cx, dy = y - h / 2;
        double light = switch (this) {
            case Star -> star(dx, dy, radius, motion ? t * 0.9 : 0) ? 0.7 : 0;
            case Heart -> heart(dx, dy, radius * (motion ? 1 + 0.08 * beat(t) : 1)) ? 0.7 : 0;
            case Smiley -> smiley(dx, dy, radius, t, motion) ? 0.7 : 0;
            case Diamond -> diamond(dx / radius, dy / radius, motion ? t : -1);
            case Moon -> moon(dx / radius, dy / radius, motion ? 0.5 + 0.3 * Math.sin(t * 0.7) : 0.5) ? 0.7 : 0;
            case Bolt -> bolt(dx / radius, dy / radius) ? (motion && hash((int) Math.floor(t * 12), 3) > 0.8 ? 1 : 0.7) : 0;
            default -> spiral(dx / radius, dy / radius, motion ? t : 0) ? 0.7 : 0;
        };
        return light > 0 ? Themes.mix(base, WHITE, (float) light) : scale(base, 0.68f);
    }

    private static boolean star(double dx, double dy, double radius, double spin) {
        double r = Math.hypot(dx, dy);
        if (r > radius) return false;
        double sector = TAU / 5;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 - spin;
        angle = ((angle % sector) + sector) % sector;
        if (angle > sector / 2) angle = sector - angle;

        double inner = radius * 0.382;
        double ox = radius, oy = 0;
        double ix = inner * Math.cos(sector / 2), iy = inner * Math.sin(sector / 2);
        double px = r * Math.cos(angle), py = r * Math.sin(angle);
        double edge = (ix - ox) * (py - oy) - (iy - oy) * (px - ox);
        double centre = (ix - ox) * (0 - oy) - (iy - oy) * (0 - ox);
        return Math.signum(edge) == Math.signum(centre) || edge == 0;
    }

    private static double beat(double t) {
        double p = t * TAU * 1.1;
        return Math.pow(Math.max(0, Math.sin(p)), 8) + 0.6 * Math.pow(Math.max(0, Math.sin(p - 0.9)), 8);
    }

    private static boolean heart(double dx, double dy, double radius) {
        double x = dx / radius * 1.25, y = -dy / radius * 1.25 + 0.2;
        double a = x * x + y * y - 1;
        return a * a * a - x * x * y * y * y <= 0;
    }

    private static boolean smiley(double dx, double dy, double radius, double t, boolean motion) {
        if (motion) dy -= Math.sin(t * 2) * radius * 0.06;
        double d = Math.hypot(dx, dy) / radius;
        if (d >= 0.84 && d <= 1.0) return true;
        double eye = radius * 0.13;
        boolean blink = motion && t % 3.2 < 0.15;
        for (int side = -1; side <= 1; side += 2) {
            double ex = dx - side * radius * 0.34, ey = dy + radius * 0.28;
            if (blink ? Math.abs(ey) < eye * 0.35 && Math.abs(ex) < eye * 1.1 : Math.hypot(ex, ey) <= eye) return true;
        }
        return dy > radius * 0.12 && d >= 0.46 && d <= 0.6;
    }

    private static double diamond(double nx, double ny, double t) {
        double ax = Math.abs(nx);
        boolean inside;
        double facet;
        if (ny < -0.55) {
            return 0;
        } else if (ny < -0.2) {
            inside = ax <= 0.5 + (ny + 0.55) / 0.35 * 0.4;
            facet = ax < 0.25 ? 0.85 : 0.65;
        } else {
            inside = ny <= 0.85 && ax <= 0.9 * (0.85 - ny) / 1.05;
            facet = nx < 0 ? 0.75 : 0.55;
        }
        if (!inside) return 0;
        if (t >= 0) {
            double sweep = (t * 0.6) % 2.6 - 1.3;
            if (Math.abs(nx + ny * 0.5 - sweep) < 0.12) return 1;
        }
        return facet;
    }

    private static boolean moon(double nx, double ny, double tilt) {
        if (Math.hypot(nx, ny) > 0.85) return false;
        double ox = Math.cos(tilt) * 0.42, oy = -Math.sin(tilt) * 0.42;
        return Math.hypot(nx - ox, ny - oy) > 0.72;
    }

    private static final double[][] BOLT = {{0.30, -1.0}, {-0.40, 0.12}, {-0.02, 0.12}, {-0.30, 1.0}, {0.42, -0.18}, {0.04, -0.18}};

    private static boolean bolt(double nx, double ny) {
        return inPolygon(BOLT, nx, ny);
    }

    private static boolean spiral(double nx, double ny, double t) {
        double r = Math.hypot(nx, ny);
        if (r > 1) return false;
        if (r < 0.08) return true;
        double f = (Math.atan2(ny, nx) / TAU + r * 1.6 - t * 0.25) * 2;
        return f - Math.floor(f) < 0.42;
    }

    private static final int BASE_STEPS = 128;
    private static final ThreadLocal<int[]> BASE_TABLE = new ThreadLocal<>();

    public static int[] baseTable(double t) {
        int[] table = new int[BASE_STEPS + 1];
        for (int i = 0; i <= BASE_STEPS; i++) table[i] = liveBase(i / (double) BASE_STEPS, t);
        return table;
    }

    static void useBaseTable(int[] table) {
        if (table == null) BASE_TABLE.remove();
        else BASE_TABLE.set(table);
    }

    private static int base(double u, double t) {
        int[] table = BASE_TABLE.get();
        if (table == null) return liveBase(u, t);
        double v = clamp01(u) * BASE_STEPS;
        int i = Math.min(BASE_STEPS - 1, (int) v);
        return Themes.mix(table[i], table[i + 1], (float) (v - i));
    }

    private static int liveBase(double u, double t) {
        if (Themes.active()) return Themes.strip(Themes.current(), u, true) | 0xFF000000;
        Color a = ColorProcess.colors.getFirst(), b = ColorProcess.colors.getSecond();
        double phase = u + t * 0.25;
        double ping = Math.abs(((phase % 2) + 2) % 2 - 1);
        return Themes.mix(a.getRGB() | 0xFF000000, b.getRGB() | 0xFF000000, (float) ping);
    }

    private static int ramp(double u, int... stops) {
        double v = clamp01(u) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(v));
        return Themes.mix(stops[i], stops[i + 1], (float) (v - i));
    }

    private static double noise(double x, double y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double top = hash(xi, yi) + (hash(xi + 1, yi) - hash(xi, yi)) * ux;
        double bottom = hash(xi, yi + 1) + (hash(xi + 1, yi + 1) - hash(xi, yi + 1)) * ux;
        return top + (bottom - top) * uy;
    }

    private static double fbm(double x, double y) {
        return noise(x, y) * 0.5 + noise(x * 2.03 + 17, y * 2.03 + 9) * 0.3 + noise(x * 4.1 + 31, y * 4.1 + 3) * 0.2;
    }

    private static int scale(int color, float factor) {
        return color & 0xFF000000 | clamp((color >> 16 & 255) * factor) << 16 | clamp((color >> 8 & 255) * factor) << 8 | clamp((color & 255) * factor);
    }

    private static int rgb(double r, double g, double b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (double) (1 << 24);
    }
}
