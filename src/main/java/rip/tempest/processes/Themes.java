package rip.tempest.processes;

import rip.tempest.modules.impl.client.ClientSettingsModule;

import java.awt.Color;

public final class Themes {
    private Themes() {
    }

    public enum Direction {
        Left, Up, Down, Right;

        public boolean horizontal() {
            return this == Left || this == Right;
        }
    }

    public enum Preset {
        LiquidGlass("Liquid Glass", 0xFFFFFFFF, 0xFFD2E9FF, 0xFFEADFFF, 0xFFFFF4E6),

        Ocean("Ocean", 0xFF5096FC, 0xFF1575FF),
        Royal("Royal", 0xFF7DCCF1, 0xFF2F62D0),
        Sky("Sky", 0xFFA0E6E1, 0xFF0FBEDC),
        Glacier("Glacier", 0xFFE0F2FE, 0xFF7DD3FC, 0xFF38BDF8),
        Lagoon("Lagoon", 0xFF2DD4BF, 0xFF0EA5E9),
        Abyss("Abyss", 0xFF3460DC, 0xFF2F73F0, 0xFF06B6D4),
        Midnight("Midnight", 0xFF4F86E8, 0xFF4A52CC, 0xFF8046CC),
        Classic("Classic", 0xFF5359D8, 0xFF8488F2),
        Marine("Marine", 0xFF38BDF8, 0xFF0F7FB8),
        Horizon("Horizon", 0xFF7DD3FC, 0xFF818CF8),
        Periwinkle("Periwinkle", 0xFFA5B4FC, 0xFF6366F1),
        Aurora("Aurora", 0xFF00E5A8, 0xFF00B6E0, 0xFF8E7BFF),

        Lavender("Lavender", 0xFFDDD6FE, 0xFFA78BFA),
        Amethyst("Amethyst", 0xFFC084FC, 0xFF7C3AED),
        Flower("Flower", 0xFFD7A6E7, 0xFFD35AE8),
        Plum("Plum", 0xFFE08ED8, 0xFF9A4FB0),
        Iris("Iris", 0xFF818CF8, 0xFFC084FC),
        Mauve("Mauve", 0xFFD8B4C8, 0xFF9C6B98),
        Twilight("Twilight", 0xFF6366F1, 0xFFA855F7, 0xFFEC4899),
        Cyber("Cyber", 0xFF35C5E8, 0xFF7664F2, 0xFFA229AE),
        Tenacity("Tenacity", 0xFFEC85D1, 0xFF1CA7DE),
        CottonCandy("Cotton Candy", 0xFF63F9FF, 0xFFB9A7FF, 0xFFFF68CC),

        Sakura("Sakura", 0xFFFFA6B8, 0xFFFFD9EC, 0xFFD9A6FF),
        Cherry("Cherry", 0xFFFFC8C8, 0xFFF33A6A),
        Rose("Rose", 0xFFFDA4AF, 0xFFE11D48),
        Wine("Wine", 0xFFE0457A, 0xFFA8327E),
        Magenta("Magenta", 0xFFFF3CAC, 0xFFB5179E),
        Bubblegum("Bubblegum", 0xFFFF9CEE, 0xFFFF5CAA),
        Sorbet("Sorbet", 0xFFFF9A8B, 0xFFFF6A88, 0xFFFF99AC),
        RoseGold("Rose Gold", 0xFFF6C6B8, 0xFFC77F7F),
        Peach("Peach", 0xFFFFD6A5, 0xFFFF9AA2),
        Coral("Coral", 0xFFFF8A65, 0xFFFF5277),
        Dawn("Dawn", 0xFFFFC3A0, 0xFFFFAFBD, 0xFFC9A0FF),
        Lava("Lava", 0xFFFF512F, 0xFFE6308A),

        Sunset("Sunset", 0xFFFF5B3A, 0xFFFFC36B),
        Dusk("Dusk", 0xFFF97316, 0xFFDB2777, 0xFF7C3AED),
        Bloom("Bloom", 0xFFFEDA75, 0xFFFA7E1E, 0xFFD62976, 0xFF962FBF),
        Ember("Ember", 0xFFFFB347, 0xFFFF6B35, 0xFFD7263D),
        Red("Red", 0xFFFF6B6B, 0xFFC4203A),
        Autumn("Autumn", 0xFFE8A15B, 0xFFC2562E, 0xFFA83A3A),
        Copper("Copper", 0xFFF4B183, 0xFFB8622F),
        Tangerine("Tangerine", 0xFFFFB75E, 0xFFED8F03),
        Apricot("Apricot", 0xFFFFD8A8, 0xFFFF922B),
        Gold("Gold", 0xFFFFD700, 0xFFF09F00),
        Champagne("Champagne", 0xFFF5E6C8, 0xFFD4AF7A),

        Forest("Forest", 0xFF6CC47A, 0xFF3A9A63, 0xFF1F8F86),
        Vine("Vine", 0xFF11C02D, 0xFFC9EAC6),
        Jade("Jade", 0xFF43E886, 0xFF11804B),
        Mint("Mint", 0xFF3ED296, 0xFF18AF61),
        Seafoam("Seafoam", 0xFFA7F3D0, 0xFF2DD4BF),
        Teal("Teal", 0xFF2DD4BF, 0xFF0F9488),
        Sage("Sage", 0xFFB7C9A8, 0xFF6F8F72),

        Sand("Sand", 0xFFE7D3B0, 0xFFB08D57),
        Steel("Steel", 0xFFCBD5E1, 0xFF64748B),
        Graphite("Graphite", 0xFFB5B5B5, 0xFF6B6B6B),
        Mono("Mono", 0xFFFFFFFF, 0xFF8C8C8C),
        Holo("Holo", 0xFFA5F3FC, 0xFFC4B5FD, 0xFFFBCFE8),
        Opal("Opal", 0xFFA7F3D0, 0xFFA5F3FC, 0xFFDDD6FE),
        Neon("Neon", 0xFFFF2BD6, 0xFF19D3FF, 0xFFB6FF2B),

        Israel("Israel", 0xFF1E5EFF, 0xFFFFFFFF),
        Usa("USA", 0xFFE63946, 0xFFFFFFFF, 0xFF3A5BD9),
        Uk("UK", 0xFF3B5BDB, 0xFFFFFFFF, 0xFFE03131),
        Japan("Japan", 0xFFFFFFFF, 0xFFE0243C),
        France("France", 0xFF3A6BE0, 0xFFFFFFFF, 0xFFEF4135),
        Italy("Italy", 0xFF2BB673, 0xFFFFFFFF, 0xFFE63946),
        Ukraine("Ukraine", 0xFF2A7DE1, 0xFFFFD500),
        Brazil("Brazil", 0xFF22B14C, 0xFFFFDF00, 0xFF3D6EE0),
        Sweden("Sweden", 0xFF2E86DE, 0xFFFECC00),
        Germany("Germany", 0xFF5A5A5A, 0xFFFF3B3B, 0xFFFFCE00),
        Canada("Canada", 0xFFE8343A, 0xFFFFFFFF),
        Ireland("Ireland", 0xFF1FBF6E, 0xFFFFFFFF, 0xFFFF883E),
        Netherlands("Netherlands", 0xFFE0303C, 0xFFFFFFFF, 0xFF3A6AD0),
        Spain("Spain", 0xFFE0262E, 0xFFF1BF00),
        Greece("Greece", 0xFF2E7DE0, 0xFFFFFFFF),
        Switzerland("Switzerland", 0xFFE8343A, 0xFFFFFFFF),
        Norway("Norway", 0xFFE0303F, 0xFFFFFFFF, 0xFF2F55B8),
        Finland("Finland", 0xFFFFFFFF, 0xFF2F62D0),
        Denmark("Denmark", 0xFFE8303F, 0xFFFFFFFF),
        Poland("Poland", 0xFFFFFFFF, 0xFFE8304F),
        SouthKorea("South Korea", 0xFFE0303F, 0xFFFFFFFF, 0xFF2F62D0),
        India("India", 0xFFFF9933, 0xFFFFFFFF, 0xFF1FAF4A),
        Jamaica("Jamaica", 0xFF1FBF5A, 0xFFFED100, 0xFF5A5A5A),
        Scotland("Scotland", 0xFF2F7FE0, 0xFFFFFFFF),
        Argentina("Argentina", 0xFF74ACDF, 0xFFFFFFFF, 0xFFF6B40E),
        Mexico("Mexico", 0xFF1FAF5A, 0xFFFFFFFF, 0xFFE8343A),

        Pride("Pride", 0xFFF03A3A, 0xFFFF8C00, 0xFFFFED00, 0xFF24B04E, 0xFF3070FF, 0xFFA84ADB),
        Trans("Trans", 0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA),
        Bisexual("Bisexual", 0xFFE0307F, 0xFFA45BB0, 0xFF3A66E0),
        Pansexual("Pansexual", 0xFFFF218C, 0xFFFFD800, 0xFF21B1FF),
        Nonbinary("Nonbinary", 0xFFFCF434, 0xFFFFFFFF, 0xFFA569DB, 0xFF6A6A6A),
        Lesbian("Lesbian", 0xFFE8501F, 0xFFFF9A56, 0xFFFFFFFF, 0xFFD362A4, 0xFFC0307A),

        Synthwave("Synthwave", 0xFF9B4DFF, 0xFFFF2E97, 0xFFFFB86B),
        Vaporwave("Vaporwave", 0xFF7DF9FF, 0xFFFF71CE, 0xFFB967FF),
        Toxic("Toxic", 0xFFB6FF00, 0xFF00FF87),
        BloodMoon("Blood Moon", 0xFFFF3B3B, 0xFF8A0F2A),
        Matrix("Matrix", 0xFF00FF41, 0xFF0F8A2E),
        IceFire("Ice & Fire", 0xFF4CC9F0, 0xFFF72585),
        GoldRush("Gold Rush", 0xFFFFE259, 0xFFFFA751),
        Nebula("Nebula", 0xFF6A82FB, 0xFFC86DD7, 0xFFFC5C7D),
        Arctic("Arctic", 0xFFFFFFFF, 0xFFBDE0FE, 0xFF7FB3FF),
        Inferno("Inferno", 0xFFFFE066, 0xFFFF7A00, 0xFFE0242F),
        Watermelon("Watermelon", 0xFFFF5E78, 0xFFFFB3C1, 0xFF3DDC84),
        Peacock("Peacock", 0xFF1FD1C1, 0xFF2F7FE0, 0xFF8E5CF0),
        Mojito("Mojito", 0xFFD4FC79, 0xFF96E6A1),
        Miami("Miami", 0xFFFF6EC7, 0xFF3DF5E0),
        GrapeSoda("Grape Soda", 0xFFB06AFF, 0xFF6A4CFF),
        Lemonade("Lemonade", 0xFFFFF27A, 0xFFFF9ECF),
        Thunder("Thunder", 0xFFFFFFFF, 0xFF9DB4FF, 0xFF5A65D6),
        Frost("Frost", 0xFFE0F7FF, 0xFF9AD9FF, 0xFFB9A7FF),
        Hologram("Hologram", 0xFF39E6FF, 0xFF9E7BFF, 0xFFFF7BE5),
        Firefly("Firefly", 0xFFFFF27A, 0xFF3E9A66),
        DeepSea("Deep Sea", 0xFF3AD2D2, 0xFF2E62C8),
        Sunrise("Sunrise", 0xFFFF9A5A, 0xFFFFD36E, 0xFFFFF1C1),
        RoyalGold("Royal Gold", 0xFF8E5CF0, 0xFFFFD23F),
        MintChoc("Mint Choc", 0xFFA8F0D0, 0xFFB07A55),

        Reef("Reef", 0xFF2EC4B6, 0xFFF2E3B3, 0xFFFF8A65),
        Pine("Pine", 0xFFB8EBC8, 0xFF2E9E77),
        Haze("Haze", 0xFFB8B0F0, 0xFFF5B7C5),
        Ash("Ash", 0xFFF0EAE4, 0xFFA39286, 0xFF7E8AA0),
        Film("Film", 0xFFF0C9A0, 0xFFA8BDD0),

        TempestStorm("Tempest", 0xFF5EA8FF, 0xFFC8D4E8, 0xFF7B6CD9),
        DuskSea("Dusk Sea", 0xFF5B8CFF, 0xFFC99AD8, 0xFFFF8C7A),
        Fig("Fig", 0xFFC7849E, 0xFF6E5A9E),

        Rainbow("Rainbow"),

        Custom("Custom");

        private final String label;
        private final int[] stops;

        Preset(String label, int... stops) {
            this.label = label;
            this.stops = stops;
        }

        public int[] stops() {
            if (this != Custom) return stops;
            return new int[]{ClientSettingsModule.getPickerColor1() | 0xFF000000, ClientSettingsModule.getPickerColor2() | 0xFF000000};
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final double MS_PER_STOP = 7500.0, ROW_STEP = 12.0, PIXEL_STEP = 0.35, PHASE = 0.12 / (Math.PI * 2);

    private static double clock;
    private static long clockAt;

    private static double clock() {
        long now = System.currentTimeMillis();
        if (clockAt != 0) clock += (now - clockAt) * Math.max(0.1, ClientSettingsModule.themeSpeed.getValue());
        clockAt = now;
        return clock;
    }

    public static boolean active() {
        return ClientSettingsModule.color.getValue() == ClientSettingsModule.Color.Theme;
    }

    public static Preset current() {
        return ClientSettingsModule.theme.getValue();
    }

    public static void select(Preset preset) {
        ClientSettingsModule.theme.setValue(preset);
        ClientSettingsModule.color.setValue(ClientSettingsModule.Color.Theme);
    }

    public static Direction direction() {
        return ClientSettingsModule.themeDirection.getValue();
    }

    public static boolean horizontal() {
        return direction().horizontal();
    }

    private static int sign() {
        Direction direction = direction();
        return direction == Direction.Left || direction == Direction.Up ? 1 : -1;
    }

    private static double length() {
        return Math.max(0.5, ClientSettingsModule.themeLength.getValue());
    }

    private static int segments(Preset preset) {
        return Math.max(1, preset.stops().length - 1);
    }

    public static int sample(Preset preset, double u) {
        return saturate(rawSample(preset, u));
    }

    private static int rawSample(Preset preset, double u) {
        u -= Math.floor(u);
        int[] stops = preset.stops();
        if (stops.length == 0) return Color.HSBtoRGB((float) u, 1.0f, 1.0f);
        int n = stops.length - 1;
        if (n == 0) return stops[0];
        double v = u * n * 2.0;
        double w = v <= n ? v : n * 2.0 - v;
        int i = Math.min(n - 1, (int) Math.floor(w));
        return mix(stops[i], stops[i + 1], (float) (w - i));
    }

    public static boolean liquidGlass() {
        return active() && current() == Preset.LiquidGlass;
    }

    public static int saturate(int color) {
        double amount = ClientSettingsModule.themeSaturation.getValue();
        if (Math.abs(amount - 1) < 0.001) return color;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        double grey = r * 0.299 + g * 0.587 + b * 0.114;
        return color & 0xFF000000 | channel(grey + (r - grey) * amount) << 16 | channel(grey + (g - grey) * amount) << 8 | channel(grey + (b - grey) * amount);
    }

    private static int channel(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int at(Preset preset, double phase) {
        int n = segments(preset);
        return sample(preset, clock() / (MS_PER_STOP * n) + phase * PHASE / n);
    }

    public static int color() {
        return at(current(), 0);
    }

    public static int row(Preset preset, int row) {
        return at(preset, row * (ROW_STEP / length()) * sign());
    }

    public static int row(int row) {
        return row(current(), row);
    }

    public static int atX(Preset preset, double x) {
        return at(preset, x * (PIXEL_STEP / length()) * sign());
    }

    public static int atX(double x) {
        return atX(current(), x);
    }

    public static int strip(Preset preset, double along, boolean moving) {
        boolean wheel = preset.stops().length == 0;
        double u = along * (wheel ? 1.0 : 0.5);
        if (!moving) return sample(preset, u);
        return sample(preset, u + sign() * clock() / (MS_PER_STOP * segments(preset)));
    }

    public static int first() {
        Preset preset = current();
        return preset.stops().length == 0 ? color() : preset.stops()[0];
    }

    public static int last() {
        Preset preset = current();
        int[] stops = preset.stops();
        if (stops.length == 0) return sample(preset, clock() / MS_PER_STOP + 0.15);
        return stops[stops.length - 1];
    }

    public static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
