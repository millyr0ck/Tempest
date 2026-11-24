package rip.tempest.interfaces.click.exhibition.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.io.InputStream;

public final class ExhibitionFonts {
    public static TTFFontRenderer f;

    public static TTFFontRenderer fs;

    public static TTFFontRenderer fss;

    public static TTFFontRenderer fsmallbold;

    public static TTFFontRenderer test2, test3;

    public static TTFFontRenderer badCache;

    public static TTFFontRenderer calibri18, calibri10;

    private static boolean loaded;

    private ExhibitionFonts() {
    }

    public static void load() {
        if (loaded) return;

        f = new TTFFontRenderer(system("Impact", 24, false), true);
        fs = new TTFFontRenderer(tahoma(true, 11), true);
        test2 = new TTFFontRenderer(tahoma(true, 10), true);
        fss = new TTFFontRenderer(tahoma(false, 10), false);
        test3 = new TTFFontRenderer(tahoma(false, 10), true);
        fsmallbold = new TTFFontRenderer(tahoma(true, 10), true);
        badCache = new TTFFontRenderer(resource("tempest/exhibition/icons.ttf", 36, new Font("Impact", Font.PLAIN, 36)), true);
        calibri18 = new TTFFontRenderer(calibriBold(18), true);
        calibri10 = new TTFFontRenderer(calibriBold(11).deriveFont(10.5F), false);

        loaded = true;
    }

    private static Font tahoma(boolean bold, int size) {
        Font installed = new Font(bold ? "Tahoma Bold" : "Tahoma", Font.PLAIN, size);
        if (isInstalled(installed)) return installed;

        Font bundled = resource("tempest/fonts/tahoma.ttf", size, null);
        if (bundled != null) return bold ? bundled.deriveFont(Font.BOLD) : bundled;
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }

    private static Font calibriBold(int size) {
        Font installed = new Font("Calibri Bold", Font.PLAIN, size);
        return isInstalled(installed) ? installed : tahoma(true, size);
    }

    private static Font system(String name, int size, boolean bold) {
        Font font = new Font(name, bold ? Font.BOLD : Font.PLAIN, size);
        return isInstalled(font) ? font : tahoma(bold, size);
    }

    private static boolean isInstalled(Font font) {
        return !"Dialog".equalsIgnoreCase(font.getFamily());
    }

    private static Font resource(String path, int size, Font fallback) {
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(path)).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, (float) size);
        } catch (Exception e) {
            return fallback;
        }
    }
}
