package rip.tempest.modules.impl.client;

import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.Themes;

@ModuleInfo(label = "Client Settings", category = ModuleCategory.CLIENT)
public final class ClientSettingsModule extends Module {
    public static final ModeProperty<MenuStyle> menuStyle = new ModeProperty<>("Menu Style", MenuStyle.Astolfo);

    public enum MenuStyle {
        Astolfo, Tempest
    }

    public static final ModeProperty<ClickInterface> clickInterface = new ModeProperty<>("Click Interface", ClickInterface.Tempest);

    public static final Property<Boolean> astolfoCategoryColors = new Property<>("Astolfo Category Colors", true,
            () -> clickInterface.getValue() == ClickInterface.Astolfo);
    public static final ModeProperty<GuiAnimation> guiAnimation = new ModeProperty<>("GUI Animation", GuiAnimation.Pop);
    public static final ModeProperty<Font> font = new ModeProperty<>("Font", Font.MC);
    public static final ModeProperty<Color> color = new ModeProperty<>("Color", Color.Tempest);

    public static final NumberProperty pickerColor1 = new NumberProperty("Picker Color 1", -16776961, ClientSettingsModule::usesPickerColors, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    public static final NumberProperty pickerColor2 = new NumberProperty("Picker Color 2", -65536, ClientSettingsModule::usesPickerColors, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

    public static final ModeProperty<Themes.Preset> theme = new ModeProperty<>("Theme", Themes.Preset.Ocean, ClientSettingsModule::themeSettingsShown);
    public static final NumberProperty themeSpeed = new NumberProperty("Theme Speed", 1, ClientSettingsModule::themeSettingsShown, 0.1, 5, 0.1);
    public static final NumberProperty themeLength = new NumberProperty("Theme Wave Length", 1, ClientSettingsModule::themeSettingsShown, 0.5, 5, 0.1);

    public static final NumberProperty themeSaturation = new NumberProperty("Theme Saturation", 1, ClientSettingsModule::themeSettingsShown, 0, 2, 0.05);
    public static final ModeProperty<Themes.Direction> themeDirection = new ModeProperty<>("Theme Direction", Themes.Direction.Down, ClientSettingsModule::themeSettingsShown);

    public static final Property<Boolean> themeMenu = new Property<>("Theme Menu", true, () -> false);

    public static final NumberProperty exhibitionRainbowSpeed = new NumberProperty("Exhibition Rainbow Speed", 5,
            () -> color.getValue() == Color.ExhibitionRainbow, 1, 15, 1);

    public static final NumberProperty exhibitionAccent = new NumberProperty("Exhibition Accent", 0xFF00C0FF, () -> false, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

    public static final NumberProperty bankrollAccent = new NumberProperty("Bankroll Accent", 0xFFECB8EF, () -> false, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

    public static final NumberProperty menuKey = new NumberProperty("Menu Key", 54, () -> false, 1, 255, 1);

    public static final Property<Boolean> snapElements = new Property<>("Snap To Elements", true);
    public static final Property<Boolean> snapGrid = new Property<>("Snap To Grid", true);
    public static final NumberProperty gridSize = new NumberProperty("Grid Size", 8, snapGrid::getValue, 2, 32, 1);

    private static boolean usesPickerColors() {
        Color mode = color.getValue();
        return mode == Color.Picker || mode == Color.FluxTwoColor || mode == Color.FluxScroll
                || mode == Color.Theme && theme.getValue() == Themes.Preset.Custom;
    }

    private static boolean themeSettingsShown() {
        return color.getValue() == Color.Theme && clickInterface.getValue() != ClickInterface.Bankroll
                && clickInterface.getValue() != ClickInterface.Tempest && clickInterface.getValue() != ClickInterface.Tiles
                && clickInterface.getValue() != ClickInterface.Columns && clickInterface.getValue() != ClickInterface.Astolfo
                && clickInterface.getValue() != ClickInterface.Exhibition && clickInterface.getValue() != ClickInterface.Storm;
    }

    public static int getPickerColor1() { return pickerColor1.getValue().intValue(); }
    public static int getPickerColor2() { return pickerColor2.getValue().intValue(); }
    public static void setPickerColor1(int c) { pickerColor1.setValue((double) c); }
    public static void setPickerColor2(int c) { pickerColor2.setValue((double) c); }

    public static final Property<Boolean> blinkCancelsIncoming = new Property<>("Blink Cancels Incoming", true);

    public enum ClickInterface {
        Tempest,
        Exhibition,
        Bankroll,

        Astolfo,

        Storm,

        Tiles,
        Columns
    }

    public enum GuiAnimation {
        Pop,
        Cascade,

        Dropdown
    }

    public enum Font {
        Inter2,
        Sans,
        Convection,
        Tahoma,
        Moon,
        Astolfo,
        SFUI,
        Verdana,
        OpenSans,
        OpenSansBold,
        Comfortaa,
        SF,
        SFBold,
        MC,
        SmallestPixel,

        Radium,

        Virtue,

        Exhibition,

        Blocky,

        Arial,
        ArialBold,
        VerminVibes,

        Geist,
        InterBold,
        Manrope,
        ManropeBold,
        ManropeExtraBold,
        Rubik,
        SourceSans3Medium,

        ProductSansBold,
        JelloRegular,
        JelloLight,
        Roboto,
        RobotoMedium,
        URWGothic,
        ProggyTiny,
        ESP,

        ProductSans,
        CobblerSans,

        SFDisplayBold,
        SFDisplayMedium,
        SFDisplayLight
    }

    public enum Color {
        Rainbow,

        SoftRainbow {
            @Override
            public String toString() {
                return "Soft Rainbow";
            }
        },
        ClassicRainbow {
            @Override
            public String toString() {
                return "Classic Rainbow";
            }
        },
        Exhibition,

        ExhibitionRainbow {
            @Override
            public String toString() {
                return "Exhibition Rainbow";
            }
        },
        Astolfo,
        Tempest,
        Category,
        Tenacity,
        FDP,
        Rise,
        Mint,
        Horizontal,
        Vaporwave,
        Sunset,
        White,
        Red,
        Purple,
        Pink,

        Vertical,
        Diagonal,
        Radial,
        Spiral,
        Picker,

        FluxTwoColor {
            @Override
            public String toString() {
                return "Flux Two Color";
            }
        },
        FluxScroll {
            @Override
            public String toString() {
                return "Flux Scroll";
            }
        },
        FluxRainbow {
            @Override
            public String toString() {
                return "Flux Rainbow";
            }
        },
        FluxCategory {
            @Override
            public String toString() {
                return "Flux Category";
            }
        },
        FluxModule {
            @Override
            public String toString() {
                return "Flux Module";
            }
        },

        VirtuePlain {
            @Override
            public String toString() {
                return "Virtue Plain";
            }
        },
        NewVirtue {
            @Override
            public String toString() {
                return "New Virtue";
            }
        },
        Crest,
        Trendy,
        Indigo,

        Theme
    }

    public ClientSettingsModule() {
        toggle();
        setHidden(true);
    }
}
