package rip.tempest.utils.render.shaders;

import rip.tempest.utils.Util;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glUniform1fv;

public class Blur extends Util {
    private static final ShaderUtils GAUSSIAN_BLUR_SHADER = new ShaderUtils("gaussianBlur");
    private static Framebuffer framebuffer = new Framebuffer(1, 1, false);

    private static final FloatBuffer WEIGHTS = BufferUtils.createFloatBuffer(256);
    private static float weightsRadius = Float.NaN;

    private static FloatBuffer buildWeights(float radius) {
        if (radius != weightsRadius) {
            weightsRadius = radius;
            WEIGHTS.clear();
            float sigma = radius / 2f;
            int last = Math.min(255, (int) radius);
            for (int i = 0; i < 256; i++) {
                WEIGHTS.put(i <= last ? MathUtils.calculateGaussianValue(i, sigma) : 0f);
            }
        }
        WEIGHTS.rewind();
        return WEIGHTS;
    }

    private static void setupUniforms(float dirX, float dirY, float radius, FloatBuffer weights) {
        GAUSSIAN_BLUR_SHADER.setUniformi("textureIn", 0);
        GAUSSIAN_BLUR_SHADER.setUniformf("texelSize", 1.0f / mc.displayWidth, 1.0f / mc.displayHeight);
        GAUSSIAN_BLUR_SHADER.setUniformf("direction", dirX, dirY);
        GAUSSIAN_BLUR_SHADER.setUniformf("radius", radius);
        glUniform1fv(GAUSSIAN_BLUR_SHADER.getUniform("weights"), weights);
    }

    public static void startBlur() {
        StencilUtils.initStencilToWrite();
    }

    public static void endBlur(float radius, float compression) {
        if (radius <= 0.0f || compression <= 0.0f) {
            StencilUtils.uninitStencilBuffer();
            return;
        }

        StencilUtils.readStencilBuffer(1);

        FloatBuffer weights = buildWeights(radius);

        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

        framebuffer = RenderUtils.createFrameBuffer(framebuffer);
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);

        GAUSSIAN_BLUR_SHADER.init();
        setupUniforms(compression, 0, radius, weights);
        GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
        ShaderUtils.drawQuads();
        framebuffer.unbindFramebuffer();
        GAUSSIAN_BLUR_SHADER.unload();

        mc.getFramebuffer().bindFramebuffer(true);
        GAUSSIAN_BLUR_SHADER.init();
        setupUniforms(0, compression, radius, weights);
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        ShaderUtils.drawQuads();
        GAUSSIAN_BLUR_SHADER.unload();

        StencilUtils.uninitStencilBuffer();
        RenderUtils.resetColor();
        GlStateManager.bindTexture(0);
    }
}
