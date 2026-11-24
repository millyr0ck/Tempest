package rip.tempest.utils.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

public final class VirtueFont {
    private static FontRenderer font;

    private VirtueFont() {
    }

    public static FontRenderer get() {
        if (font == null) {
            Minecraft mc = Minecraft.getMinecraft();
            font = new FontRenderer(mc.gameSettings, new ResourceLocation("tempest/fonts/virtue/ascii.png"), mc.getTextureManager(), false);

            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(font);
        }
        return font;
    }
}
