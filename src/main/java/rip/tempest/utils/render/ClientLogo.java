package rip.tempest.utils.render;

import rip.tempest.modules.impl.visuals.InfoModule;
import rip.tempest.processes.ColorProcess;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static rip.tempest.utils.Util.mc;

public final class ClientLogo {
    private static final ResourceLocation SOURCE = new ResourceLocation("tempest/images/tempest_light.png");

    private static final int STRIPS = 24;

    private static DynamicTexture mask;
    private static boolean failed;

    private ClientLogo() {
    }

    public static void draw(float x, float y, float width, float height) {
        DynamicTexture texture = mask();
        if (texture == null) {
            RenderUtils.drawImage(SOURCE, x, y, width, height);
            return;
        }

        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        double phase = System.currentTimeMillis() / InfoModule.GRADIENT_WAVE_SPEED;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < STRIPS; i++) {
            float u0 = i / (float) STRIPS, u1 = (i + 1) / (float) STRIPS;
            float x0 = x + width * u0, x1 = x + width * u1;
            Color c0 = Glyphs.waveColor(first, second, u0, phase), c1 = Glyphs.waveColor(first, second, u1, phase);
            buffer.pos(x0, y + height, 0).tex(u0, 1).color(c0.getRed(), c0.getGreen(), c0.getBlue(), c0.getAlpha()).endVertex();
            buffer.pos(x1, y + height, 0).tex(u1, 1).color(c1.getRed(), c1.getGreen(), c1.getBlue(), c1.getAlpha()).endVertex();
            buffer.pos(x1, y, 0).tex(u1, 0).color(c1.getRed(), c1.getGreen(), c1.getBlue(), c1.getAlpha()).endVertex();
            buffer.pos(x0, y, 0).tex(u0, 0).color(c0.getRed(), c0.getGreen(), c0.getBlue(), c0.getAlpha()).endVertex();
        }
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);

        GlStateManager.resetColor();
    }

    private static DynamicTexture mask() {
        if (mask != null || failed) return mask;
        try (InputStream input = mc.getResourceManager().getResource(SOURCE).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            BufferedImage white = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < image.getHeight(); py++) {
                for (int px = 0; px < image.getWidth(); px++) {
                    int alpha = image.getRGB(px, py) >>> 24;
                    white.setRGB(px, py, alpha << 24 | 0xFFFFFF);
                }
            }
            mask = new DynamicTexture(white);
        } catch (Exception e) {
            failed = true;
        }
        return mask;
    }
}
