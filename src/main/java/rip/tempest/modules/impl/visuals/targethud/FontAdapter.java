package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.api.font.CustomFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws with a TTF font, or with the vanilla font scaled to the same height. Adapted from
 * Yuri's FontAdapter (AGPL-3.0), with Yuri's FontUtils cache folded in; fonts load from
 * tempest/fonts.
 */
public final class FontAdapter {
    private static final Map<String, CustomFontRenderer> FONTS = new HashMap<>();

    private final CustomFontRenderer custom;
    private final boolean vanilla;
    private final float scale;

    private FontAdapter(CustomFontRenderer custom, boolean vanilla, int size) {
        this.custom = custom;
        this.vanilla = vanilla;
        if (vanilla && !(size >= 18 && size <= 21)) {
            this.scale = custom.getHeight() / (float) Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        } else {
            this.scale = 1f;
        }
    }

    public static FontAdapter of(String name, int size, boolean useCustom) {
        CustomFontRenderer font = font(name, size);
        if (font == null) return null;
        return new FontAdapter(font, !useCustom, size);
    }

    public static CustomFontRenderer font(String name, int size) {
        return FONTS.computeIfAbsent(name + "|" + size, key -> new CustomFontRenderer(name, size, Font.PLAIN, true, false));
    }

    public float width(String text) {
        if (vanilla) return Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) * scale;
        return custom.getStringWidth(text);
    }

    public float height() {
        if (vanilla) return Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * scale;
        return custom.getHeight();
    }

    public void draw(String text, float x, float y, int color) {
        if (!vanilla) {
            custom.drawStringWithShadow(text, x, y, color);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, 0f, 0f, color);
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
