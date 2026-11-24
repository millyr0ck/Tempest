package rip.tempest.processes;

import rip.tempest.api.font.CustomFontRenderer;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class FontProcess {
    private static final Map<String, CustomFontRenderer> fontRegistry = new HashMap<>();
    private static final Map<String, CustomFontRenderer> scaledFontCache = new HashMap<>();
    private static final Map<String, CustomFontRenderer> styledFontCache = new HashMap<>();
    private static String currentFont = "tempest";

    public final static String
            BUG = "a",
            LIST = "b",
            BOMB = "c",
            EYE = "d",
            PERSON = "e",
            WHEELCHAIR = "f",
            SCRIPT = "g",
            SKIP_LEFT = "h",
            PAUSE = "i",
            PLAY = "j",
            SKIP_RIGHT = "k",
            SHUFFLE = "l",
            INFO = "m",
            SETTINGS = "n",
            CHECKMARK = "o",
            XMARK = "p",
            TRASH = "q",
            WARNING = "r",
            FOLDER = "s",
            LOAD = "t",
            SAVE = "u",
            UPVOTE_OUTLINE = "v",
            UPVOTE = "w",
            DOWNVOTE_OUTLINE = "x",
            DOWNVOTE = "y",
            DROPDOWN_ARROW = "z",
            PIN = "s",
            EDIT = "A",
            SEARCH = "B",
            UPLOAD = "C",
            REFRESH = "D",
            ADD_FILE = "E",
            STAR_OUTLINE = "F",
            STAR = "G";

    static {
        registerFont("small", createFont("tempest", 10));
        registerFont("tempest", createFont("tempest", 18));
        registerFont("bold", createFont("tempest-bold", 18));
        registerFont("semi-big", createFont("tempest", 32));
        registerFont("big", createFont("tempest-bold", 48));
        registerFont("inter2", createFont("inter2", 18));
        registerFont("moon", createFont("moon", 18));
        registerFont("astolfo", createFont("astolfo", 18));
        registerFont("sans", createFont("sans", 18));
        registerFont("convection", createFont("convection", 18));
        registerFont("icon", createFont("icon", 40));
        registerFont("verdana", createFont("verdana", 18));
        registerFont("sfui", createFont("sfui", 18));
        registerFont("tahoma", createFont("tahoma", 18));

        registerFont("opensans", createFont("opensans", 18));
        registerFont("opensansbold", createFont("opensansbold", 18));

        registerFont("comfortaa", createFont("comfortaa", 18));

        registerFont("sf", createFont("sf", 18));
        registerFont("sfbold", createFont("sfbold", 18));
        registerFont("mc", createFont("mc", 18));

        registerFont("smallestpixel", new CustomFontRenderer("smallestpixel", 16, Font.PLAIN, false, false));

        registerFont("radium", createFont("radium", 18));

        registerFont("virtue", CustomFontRenderer.bitmap("virtue", rip.tempest.utils.render.VirtueFont::get));

        registerFont("exhibition", CustomFontRenderer.exhibition("exhibition", "calibri-regular", 20));
        registerFont("blocky", CustomFontRenderer.bitmap("blocky", rip.tempest.utils.render.BlockyFont::get));

        registerFont("arial", createFont("arial", 18));
        registerFont("arialbold", createFont("arialbold", 18));
        registerFont("verminvibes", createFont("verminvibes", 18));

        registerFont("geist", createFont("geist", 18));
        registerFont("interbold", createFont("interbold", 18));
        registerFont("manrope", createFont("manrope", 18));

        registerFont("manropebold", createFont("manropebold", 18));
        registerFont("manropeextrabold", createFont("manropeextrabold", 18));
        registerFont("rubik", createFont("rubik", 18));
        registerFont("sourcesans3medium", createFont("sourcesans3medium", 18));

        registerFont("productsansbold", createFont("productsansbold", 18));
        registerFont("jelloregular", createFont("jelloregular", 18));
        registerFont("jellolight", createFont("jellolight", 18));
        registerFont("roboto", createFont("roboto", 18));
        registerFont("robotomedium", createFont("robotomedium", 18));
        registerFont("urwgothic", createFont("urwgothic", 18));
        registerFont("proggytiny", new CustomFontRenderer("proggytiny", 20, Font.PLAIN, false, false));
        registerFont("esp", createFont("esp", 18));

        registerFont("productsans", createFont("productsans", 18));
        registerFont("cobblersans", createFont("cobblersans", 18));

        registerFont("sfdisplaybold", createFont("sfdisplaybold", 18));
        registerFont("sfdisplaymedium", createFont("sfdisplaymedium", 18));
        registerFont("sfdisplaylight", createFont("sfdisplaylight", 18));
    }

    public static void registerFont(String name, CustomFontRenderer font) {
        fontRegistry.put(name, font);
    }

    public static CustomFontRenderer getFont(String name) {
        return fontRegistry.get(name);
    }

    public static void setCurrentFont(String name) {
        if (fontRegistry.containsKey(name)) {
            currentFont = name;
        }
    }

    public static CustomFontRenderer getCurrentFont() {
        return getFont(currentFont);
    }

    public static void swapFonts(String name1, String name2) {
        CustomFontRenderer font1 = getFont(name1);
        CustomFontRenderer font2 = getFont(name2);

        if (font1 != null && font2 != null) {
            fontRegistry.put(name1, font2);
            fontRegistry.put(name2, font1);

            if (currentFont.equals(name1)) currentFont = name2;
            else if (currentFont.equals(name2)) currentFont = name1;
        }
    }

    public static void aliasFont(String aliasName, String sourceName) {
        CustomFontRenderer source = getFont(sourceName);
        if (source != null) {
            registerFont(aliasName, source);
        }
    }

    public static void rebindFont(String name, CustomFontRenderer newFont) {
        if (fontRegistry.containsKey(name)) {
            fontRegistry.put(name, newFont);
        }
    }

    public static CustomFontRenderer getScaledFont(String name, float scale) {
        String cacheKey = name + "|" + scale;
        if (scaledFontCache.containsKey(cacheKey)) {
            return scaledFontCache.get(cacheKey);
        }

        CustomFontRenderer original = getFont(name);
        if (original == null) return null;

        String fontName = original.getNameFontTTF();
        float originalSize = original.getFont().getSize();
        float newSize = originalSize * scale;

        CustomFontRenderer scaledFont = new CustomFontRenderer(fontName, newSize, Font.PLAIN, true, false);
        scaledFontCache.put(cacheKey, scaledFont);

        return scaledFont;
    }

    public static void clearScaledFontCache() {
        scaledFontCache.clear();
    }

    public static CustomFontRenderer getStyledFont(String file, float size, int style) {
        Map<Long, CustomFontRenderer> bySize = styledFonts.get(file);
        if (bySize == null) styledFonts.put(file, bySize = new HashMap<>());
        long key = ((long) Float.floatToIntBits(size) << 8) | (style & 0xFF);
        CustomFontRenderer font = bySize.get(key);
        if (font == null) {
            String legacy = file + "|" + size + "|" + style;
            font = styledFontCache.get(legacy);
            if (font == null) {
                Font base = CustomFontRenderer.getFontFromTTF(new ResourceLocation("tempest/fonts/" + file + ".ttf"), size, Font.TRUETYPE_FONT);
                font = new CustomFontRenderer(base.deriveFont(style), true, false);
                styledFontCache.put(legacy, font);
            }
            bySize.put(key, font);
        }
        return font;
    }

    private static final Map<String, Map<Long, CustomFontRenderer>> styledFonts = new HashMap<>();

    private static CustomFontRenderer createFont(String name, int size) {
        return new CustomFontRenderer(name, size, Font.PLAIN, true, false);
    }
}
