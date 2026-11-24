package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import rip.tempest.processes.ColorProcess;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

final class Aura {
    private static final double SPREAD = 2.39996;

    private Aura() {
    }

    static void render() {
        int count = CosmeticsModule.auraParticles.getValue().intValue();
        Tint tint = CosmeticsModule.auraColor.getValue();
        long now = System.currentTimeMillis();

        float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);

        for (int i = 0; i < count; i++) {
            int ring = i % 3;
            double radius = 11 + ring * 1.6;
            double speed = (0.0011 + ring * 0.00035) * (ring == 1 ? -1 : 1);
            double angle = i * SPREAD + now * speed;
            double height = -6 + (i * 37) % 29 + Math.sin(now * 0.0015 + i) * 2.5;
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;

            Color color = tint == Tint.White ? Cosmetics.WHITE
                    : Cosmetics.blend(ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), count > 1 ? i / (float) (count - 1) : 0f);
            float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
            float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.004 + i * 1.3);

            GlStateManager.color(r, g, b, 0.9f * pulse);
            Shapes.begin();
            Shapes.octa(x, height, z, 0.55);
            Shapes.end();
            GlStateManager.color(r, g, b, 0.16f * pulse);
            Shapes.begin();
            Shapes.octa(x, height, z, 1.5);
            Shapes.end();
        }

        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
    }
}
