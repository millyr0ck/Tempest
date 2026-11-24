package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.GlintEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.src.Config;
import net.optifine.CustomItems;
import net.optifine.shaders.Shaders;
import net.optifine.shaders.ShadersRender;

import java.awt.*;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Glint", category = ModuleCategory.VISUALS)
public final class GlintModule extends Module {
    private final Property<Boolean> glintWeapons = new Property<>("Glint Weapons", true);
    private final rip.tempest.api.properties.impl.ModeProperty<ColorMode> colorMode = new rip.tempest.api.properties.impl.ModeProperty<>("Color Mode", ColorMode.Single);
    private final NumberProperty color = new NumberProperty("Glint Color", 0xFFA060FF,
            () -> colorMode.getValue() == ColorMode.Single || colorMode.getValue() == ColorMode.Gradient, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    private final NumberProperty secondColor = new NumberProperty("Second Color", 0xFF40C0FF,
            () -> colorMode.getValue() == ColorMode.Gradient, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    private final NumberProperty intensity = new NumberProperty("Intensity", 1, 0.1, 1, 0.05);
    private final NumberProperty speed = new NumberProperty("Speed", 1, 0.1, 4, 0.1);
    private final NumberProperty layers = new NumberProperty("Layers", 2, 1, 8, 1);

    public enum ColorMode {
        Single, Gradient, Client, Rainbow
    }

    private int layerColor(int layer, int count) {
        float t = count <= 1 ? 0 : (layer - 1) / (float) (count - 1);
        int rgb = switch (colorMode.getValue()) {
            case Single -> color.getValue().intValue();
            case Gradient -> mix(color.getValue().intValue(), secondColor.getValue().intValue(), t);
            case Client -> rip.tempest.processes.ColorProcess.getColor().getRGB();
            case Rainbow -> Color.HSBtoRGB((Minecraft.getSystemTime() % 4000L) / 4000F + t * 0.5F, 0.8F, 1F);
        };
        float k = intensity.getValue().floatValue();
        int r = Math.round((rgb >> 16 & 255) * k), g = Math.round((rgb >> 8 & 255) * k), b = Math.round((rgb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    @EventLink
    public final Listener<GlintEvent> onGlint = event -> {
        final ItemStack itemStack = event.getItemStack();
        final Item item = itemStack.getItem();

        if (this.glintWeapons.getValue() && (item instanceof ItemSword || item instanceof ItemAxe)) {
            event.setEnchanted(true);
        }

        event.setCancelled();

        if (event.isEnchanted() && event.isRender()) {
            this.renderEffect(event.getModel());
        }
    };

    public void renderEffect(final IBakedModel model) {
        if (!Config.isCustomItems() || CustomItems.isUseGlint()) {
            if (!Config.isShaders() || !Shaders.isShadowPass) {
                GlStateManager.depthMask(false);
                GlStateManager.depthFunc(514);
                GlStateManager.disableLighting();
                GlStateManager.blendFunc(768, 1);
                mc.getRenderItem().textureManager.bindTexture(RenderItem.RES_ITEM_GLINT);

                if (Config.isShaders() && !mc.getRenderItem().renderItemGui) {
                    ShadersRender.renderEnchantedGlintBegin();
                }

                GlStateManager.matrixMode(5890);
                GlStateManager.pushMatrix();

                GlStateManager.scale(8.0F, 8.0F, 8.0F);
                long period = Math.max(200L, Math.round(3000 / speed.getValue()));
                final float f = (float) (Minecraft.getSystemTime() % period) / period / 8.0F;
                GlStateManager.translate(f, 0.0F, 0.0F);

                int count = layers.getValue().intValue();
                for (int layer = 1; layer <= count; layer++) {
                    GlStateManager.rotate(-50.0F, 0.0F, 0.0F, 1.0F);
                    mc.getRenderItem().renderModel(model, layerColor(layer, count));
                }

                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5888);
                GlStateManager.blendFunc(770, 771);
                GlStateManager.enableLighting();
                GlStateManager.depthFunc(515);
                GlStateManager.depthMask(true);
                mc.getRenderItem().textureManager.bindTexture(TextureMap.locationBlocksTexture);

                if (Config.isShaders() && !mc.getRenderItem().renderItemGui) {
                    ShadersRender.renderEnchantedGlintEnd();
                }
            }
        }
    }
}
