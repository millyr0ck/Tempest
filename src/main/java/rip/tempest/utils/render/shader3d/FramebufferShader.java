package rip.tempest.utils.render.shader3d;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glEnable;

/**
 * Draws entities into an offscreen framebuffer from inside the 2D pass, then runs a shader
 * over that framebuffer onto the screen. {@link #startDraw} sets the world camera up again and
 * binds the framebuffer, the caller renders entities, and {@link #stopDraw} composites the
 * result in the given colour.
 *
 * <p>Adapted from Yuri (ddlc.yuri.utils.render.shader3d.FramebufferShader, AGPL-3.0), which
 * carries LiquidBounce's (GPL-3.0) FramebufferShader. Changed from Yuri's: the framebuffer is
 * twice the screen's resolution and sampled smoothly, so the shaders see how much of each screen
 * pixel an entity covers and can anti-alias their edges; and faint edge pixels aren't cut off by
 * the alpha test.
 */
public abstract class FramebufferShader extends Shader {
    private static final int SUPERSAMPLE = 2;

    private static Framebuffer framebuffer;

    protected float red, green, blue, alpha = 1F;
    protected float radius = 2F;
    protected float quality = 1F;

    private boolean entityShadows;

    public FramebufferShader(final String fragmentShader) {
        super(fragmentShader);
    }

    public void startDraw(final float partialTicks) {
        GlStateManager.enableAlpha();

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();

        framebuffer = setupFrameBuffer(framebuffer);
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);
        entityShadows = mc.gameSettings.entityShadows;
        mc.gameSettings.entityShadows = false;
        mc.entityRenderer.setupCameraTransform(partialTicks, 0);
    }

    public void stopDraw(final Color color, final float radius, final float quality) {
        mc.gameSettings.entityShadows = entityShadows;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        mc.getFramebuffer().bindFramebuffer(true);

        red = color.getRed() / 255F;
        green = color.getGreen() / 255F;
        blue = color.getBlue() / 255F;
        alpha = color.getAlpha() / 255F;
        this.radius = radius;
        this.quality = quality;

        mc.entityRenderer.disableLightmap();
        RenderHelper.disableStandardItemLighting();

        startShader();
        mc.entityRenderer.setupOverlayRendering();
        drawFramebuffer(framebuffer);
        stopShader();

        mc.entityRenderer.disableLightmap();

        GlStateManager.popMatrix();
        GlStateManager.popAttrib();
    }

    public Framebuffer setupFrameBuffer(Framebuffer frameBuffer) {
        int width = mc.displayWidth * SUPERSAMPLE, height = mc.displayHeight * SUPERSAMPLE;
        if (frameBuffer != null && frameBuffer.framebufferWidth == width && frameBuffer.framebufferHeight == height) {
            return frameBuffer;
        }

        if (frameBuffer != null) {
            frameBuffer.deleteFramebuffer();
        }

        Framebuffer created = new Framebuffer(width, height, false);
        created.setFramebufferFilter(GL11.GL_LINEAR);
        return created;
    }

    public void drawFramebuffer(final Framebuffer framebuffer) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);

        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);

        framebuffer.bindFramebufferTexture();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        worldrenderer.pos(0, scaledResolution.getScaledHeight(), 0).tex(0, 0).endVertex();
        worldrenderer.pos(scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), 0).tex(1, 0).endVertex();
        worldrenderer.pos(scaledResolution.getScaledWidth(), 0, 0).tex(1, 1).endVertex();
        worldrenderer.pos(0, 0, 0).tex(0, 1).endVertex();

        tessellator.draw();

        framebuffer.unbindFramebufferTexture();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }
}
