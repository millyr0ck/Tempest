package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;

import static rip.tempest.utils.Util.mc;

final class MoonWatermark {
    private MoonWatermark() {
    }

    private static CustomFontRenderer font(String file, int size) {
        return FontProcess.getStyledFont(file, size, Font.PLAIN);
    }

    private static int rainbow(int speed, int offset, float saturation) {
        float hue = ((System.currentTimeMillis() + offset) % speed) / (float) speed;
        return Color.getHSBColor(hue, saturation, 1F).getRGB();
    }

    private static String version() {
        return Tempest.VERSION.split(" ")[0];
    }

    private static String novolineText(String name) {
        return name + " §7(§f" + new SimpleDateFormat("HH:mm").format(new Date()) + "§7)";
    }

    private static String customText(String format, String name, String ping) {
        String coords = mc.thePlayer == null ? "" : "§7X§7: §f" + Math.round(mc.thePlayer.posX)
                + " §7Y: §f" + Math.round(mc.thePlayer.posY) + " §7Z: §f" + Math.round(mc.thePlayer.posZ);
        return format.replace("%fps%", String.valueOf(Minecraft.getDebugFPS()))
                .replace("%version%", version())
                .replace("%cn%", name)
                .replace("%ping%", ping)
                .replace("%time%", new SimpleDateFormat("HH:mm").format(new Date()))
                .replace("%coords%", coords)
                .replace("&", "§");
    }

    private static String nostalgiaName(String name) {
        String brand = name.isEmpty() ? "" : name.charAt(0) + "§7" + name.substring(1);
        return brand + " §8-";
    }

    private static float nostalgiaFpsX(String name) {
        return (4 + mc.fontRendererObj.getStringWidth(nostalgiaName(name))) * 1.2F + 4;
    }

    private static String virtueLine(String ping) {
        return "§7FPS: " + Minecraft.getDebugFPS() + " - MS: " + ping;
    }

    static void draw(InfoModule.Type type, String name, String format, String ping, boolean shaderPass) {
        int color = ColorProcess.getColor().getRGB();
        FontRenderer vanilla = mc.fontRendererObj;
        switch (type) {
            case MoonMoon -> font("sans", 18).drawStringWithShadow(name, 2, 4, color);
            case MoonJello -> {
                font("jellolight", 47).drawString(name, 9, 8, -1428300323);
                font("jelloregular", 20).drawString(version(), 9.5F, 32, -1428300323);
            }
            case MoonAstolfo -> font("astolfo", 18).drawStringWithShadow(name, 2, 4, color);
            case MoonRemix -> font("roboto", 19).drawStringWithShadow(name, 4, 6, color);
            case MoonNovoline -> font("astolfo", 19).drawStringWithShadow(novolineText(name), 2, 3, color);
            case MoonXave -> {
                CustomFontRenderer esp = font("esp", 42);
                RenderUtils.drawRect(0, 0, esp.getStringWidth(name) + 2, esp.getHeight() + 7, new Color(0x67000000, true));

                if (!shaderPass) esp.drawStringWithShadow(name, 2, 4, -1);
            }
            case MoonFlux -> font("urwgothic", 22).drawStringWithShadow(name, 3, 3, rainbow(3000, 10, 0.5F));
            case MoonCustom -> FontProcess.getCurrentFont().drawStringWithShadow(customText(format, name, ping), 2, 2, -1);
            case MoonNostalgia -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(1.2F, 1.2F, 1);
                vanilla.drawStringWithShadow(nostalgiaName(name), 4, 4, rainbow(4500, -30, 1F));
                GlStateManager.popMatrix();
                vanilla.drawStringWithShadow(" §7FPS §8[§7" + Minecraft.getDebugFPS() + "§8]", nostalgiaFpsX(name), 7, -1);
            }
            case MoonVirtue -> {
                vanilla.drawStringWithShadow(name + " §7(" + new SimpleDateFormat("HH:mm a").format(new Date()) + ")", 2, 2, -1);
                vanilla.drawStringWithShadow(virtueLine(ping), 2, 3 + vanilla.FONT_HEIGHT, -1);
            }
            default -> {
            }
        }
    }

    static float[] size(InfoModule.Type type, String name, String format, String ping) {
        FontRenderer vanilla = mc.fontRendererObj;
        return switch (type) {
            case MoonMoon -> new float[]{font("sans", 18).getStringWidth(name) + 4, 16};
            case MoonJello -> new float[]{font("jellolight", 47).getStringWidth(name) + 12, 44};
            case MoonAstolfo -> new float[]{font("astolfo", 18).getStringWidth(name) + 4, 16};
            case MoonRemix -> new float[]{font("roboto", 19).getStringWidth(name) + 8, 18};
            case MoonNovoline -> new float[]{font("astolfo", 19).getStringWidth(novolineText(name)) + 4, 15};
            case MoonXave -> new float[]{font("esp", 42).getStringWidth(name) + 2, font("esp", 42).getHeight() + 7};
            case MoonFlux -> new float[]{font("urwgothic", 22).getStringWidth(name) + 6, 16};
            case MoonCustom -> new float[]{FontProcess.getCurrentFont().getStringWidth(customText(format, name, ping)) + 4, 14};
            case MoonNostalgia -> new float[]{nostalgiaFpsX(name) + vanilla.getStringWidth(" FPS [000]"), 17};
            case MoonVirtue -> new float[]{Math.max(vanilla.getStringWidth(name + " (00:00 AM)"), vanilla.getStringWidth(virtueLine(ping))) + 4, 24};
            default -> null;
        };
    }
}
