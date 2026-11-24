package rip.tempest.processes;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.modules.impl.visuals.ArrayListModule;
import rip.tempest.modules.impl.visuals.InfoModule;
import rip.tempest.utils.misc.Pair;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import lombok.Getter;

import java.awt.*;

public class ColorProcess {
    @Getter
    private static Color color = new Color(255, 255, 255);
    @Getter
    public static Pair<Color, Color> colors = Pair.of(new Color(255, 255, 255), new Color(255, 255, 255));

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> update();

    public static Color fluxTwoColor(float y) {
        double t = System.nanoTime() / 1_000_000_000.0;
        float dt = (float) Math.abs(((t + y * 0.003) % 2.0) - 1.0);
        return RenderUtils.interpolateColorC(new Color(ClientSettingsModule.getPickerColor1(), true),
                new Color(ClientSettingsModule.getPickerColor2(), true), dt);
    }

    public static Color fluxRainbow(float y) {
        double t = System.nanoTime() / 1_000_000_000.0 * 0.333;
        float hue = 1.0f - (float) ((t + y * 0.0015) % 1.0);
        return Color.getHSBColor(hue, 0.6f, 1.0f);
    }

    public static Color fluxCategory(ModuleCategory category) {
        return switch (category) {
            case COMBAT -> new Color(255, 99, 99);
            case MOVEMENT -> new Color(153, 173, 255);
            case PLAYER -> new Color(116, 204, 184);
            case VISUALS -> new Color(216, 173, 255);
            default -> new Color(173, 255, 175);
        };
    }

    public static Color fluxModule(Module module) {
        float hue = new java.util.Random(module.getLabel().hashCode()).nextFloat();
        return Color.getHSBColor(hue, 0.4f, 0.9f);
    }

    public static Color getColor() {
        return color;
    }

    public static ClientSettingsModule.Color mode() {
        ClientSettingsModule.Color theme = InfoModule.themeColor();
        return theme != null ? theme : ClientSettingsModule.color.getValue();
    }

    public static Color classicRainbow(int row) {
        float hue = ((System.currentTimeMillis() % 2048L) / 2048f + row * 10f / 255f) % 1f;
        return Color.getHSBColor(hue, 0.6f, 1f);
    }

    private static float exhibitionHue;
    private static long exhibitionHueAt = System.currentTimeMillis();

    private static void updateExhibitionHue() {
        long now = System.currentTimeMillis();
        long delta = now - exhibitionHueAt;
        if (delta <= 0) return;
        exhibitionHueAt = now;

        double step = Math.max(ClientSettingsModule.exhibitionRainbowSpeed.getValue() * delta / (1000D / 60D), 0.1D);
        exhibitionHue += (float) Math.max(-step, Math.min(step, 255 - exhibitionHue));
        if ((int) exhibitionHue >= 255) exhibitionHue = 0;
    }

    public static Color exhibitionRainbow(int row) {
        int hue = ((int) exhibitionHue + 9 * row) % 255;
        return new Color(net.minecraft.util.MathHelper.hsvToRGB(hue / 255.0f, 0.55f, 0.9f));
    }

    public static Color softRainbow(int row) {
        float hue = ((System.currentTimeMillis() - row * 165L) % 4500L) / 4500f;
        return Color.getHSBColor(hue, 0.5f, 1.0f);
    }

    private static final Color INDIGO_FROM = new Color(255, 163, 194), INDIGO_TO = new Color(204, 51, 255);

    private static Color indigo() {
        long tick = System.currentTimeMillis() / 50L;
        int phase = (int) (tick % 40L);
        double ratio = (phase <= 20 ? phase : 40 - phase) / 20.0;

        int r = INDIGO_FROM.getRed() + (int) ((INDIGO_TO.getRed() - INDIGO_FROM.getRed()) * ratio);
        int g = INDIGO_FROM.getGreen() + (int) ((INDIGO_TO.getGreen() - INDIGO_FROM.getGreen()) * ratio);
        int b = INDIGO_FROM.getBlue() + (int) ((INDIGO_TO.getBlue() - INDIGO_FROM.getBlue()) * ratio);
        return new Color(r, g, b);
    }

    public static void update() {
        Color first = new Color(255, 255, 255);
        Color second = new Color(255, 255, 255);
        switch (mode()) {
            case FluxTwoColor, FluxScroll -> {
                colors = Pair.of(new Color(ClientSettingsModule.getPickerColor1(), true), new Color(ClientSettingsModule.getPickerColor2(), true));
                color = fluxTwoColor(0);
            }
            case FluxRainbow -> {
                color = fluxRainbow(0);
                colors = Pair.of(color, fluxRainbow(200));
            }
            case FluxCategory, FluxModule -> {
                color = java.awt.Color.WHITE;
                colors = Pair.of(color, color);
            }
            case Rainbow -> {
                colors = Pair.of(RenderUtils.rainbowColors(15, 75), RenderUtils.rainbowColors(15, 75));
                color = RenderUtils.rainbowColors(15, 75);
            }
            case SoftRainbow -> {
                color = softRainbow(0);
                colors = Pair.of(color, softRainbow(4));
            }
            case ClassicRainbow -> {
                color = classicRainbow(0);
                colors = Pair.of(color, classicRainbow(4));
            }
            case Astolfo -> {
                colors = Pair.of(RenderUtils.astolfoColors(35, 75), RenderUtils.astolfoColors(15, 75));
                color = RenderUtils.astolfoColors(15, 75);
            }
            case Exhibition -> {
                float hue = (System.currentTimeMillis() % 3000) / 3000f;
                color = Color.getHSBColor(hue, 0.55f, 0.9f);
                colors = Pair.of(color, color);
            }
            case ExhibitionRainbow -> {
                updateExhibitionHue();
                color = exhibitionRainbow(0);
                colors = Pair.of(color, color);
            }
            case Tempest -> {
                first = new Color(54, 59, 181);
                second = new Color(98, 102, 217);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case White -> {
                first = new Color(255, 255, 255);
                second = new Color(155, 155, 155);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Red -> {
                first = new Color(255, 0, 0);
                second = new Color(100, 0, 0);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Purple -> {
                first = new Color(100, 0, 180);
                second = new Color(50, 0, 130);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Pink -> {
                first = new Color(255, 90, 255);
                second = new Color(255, 205, 255);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Vaporwave -> {
                first = new Color(180, 0, 180);
                second = new Color(0, 200, 255);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Category -> {
                color = java.awt.Color.WHITE;
                colors = Pair.of(color, color);
            }
            case Sunset -> {
                first = new Color(161, 82, 230);
                second = new Color(255, 104, 69);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 20, colors.getFirst(), colors.getSecond(), false);
            }
            case Mint -> {
                first = new Color(62, 210, 150);
                second = new Color(24, 175, 97);
                colors = Pair.of(first, second);
                color = first;
            }
            case Tenacity -> {
                first = new Color(236, 133, 209);
                second = new Color(28, 167, 222);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case FDP -> {
                first = new Color(29, 116, 148);
                second = new Color(38, 180, 113);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }
            case Horizontal -> {
                color = RenderUtils.horizontalRainbowColor(0, 1920);
                colors = Pair.of(color, color);
            }
            case Rise -> {
                first = new Color(71, 148, 253);
                second = new Color(71, 253, 160);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }

            case Vertical, Diagonal, Radial, Spiral -> {
                color = RenderUtils.rainbowColors(15, 75);
                colors = Pair.of(color, color);
            }
            case Picker -> {
                first = new Color(ClientSettingsModule.getPickerColor1(), true);
                second = new Color(ClientSettingsModule.getPickerColor2(), true);
                colors = Pair.of(first, second);
                color = RenderUtils.interpolateColorsBackAndForth(15, 75, colors.getFirst(), colors.getSecond(), false);
            }

            case VirtuePlain -> {
                color = new Color(255, 255, 255);
                colors = Pair.of(color, color);
            }
            case NewVirtue, Crest -> {
                color = new Color(203, 206, 209);
                colors = Pair.of(color, color);
            }
            case Trendy -> {
                color = new Color(103, 123, 137);
                colors = Pair.of(color, color);
            }
            case Indigo -> {
                color = indigo();
                colors = Pair.of(INDIGO_FROM, INDIGO_TO);
            }
            case Theme -> {
                color = new Color(Themes.color());
                colors = Pair.of(new Color(Themes.first()), new Color(Themes.last()));
            }
        }
    }
}
