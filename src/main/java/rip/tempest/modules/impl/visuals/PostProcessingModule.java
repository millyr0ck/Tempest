package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.Bloom;
import rip.tempest.utils.render.shaders.Blur;
import rip.tempest.utils.render.shaders.Shadow;
import net.minecraft.client.shader.Framebuffer;

import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import org.lwjgl.opengl.GL11;

@ModuleInfo(label = "Post Processing", category = ModuleCategory.VISUALS)
public final class PostProcessingModule extends Module {
    private static final float BLOOM_RADIUS = 1.5f;
    private static final int BLOOM_ITERATIONS = 3;
    private static final float BLUR_RADIUS = 12f;
    private static final float BLUR_COMPRESSION = 3.5f;

    public final Property<Boolean> blur   = new Property<>("Blur", true);
    public final Property<Boolean> shadow = new Property<>("Shadow", true);
    public final Property<Boolean> bloom  = new Property<>("Bloom", true);

    public final NumberProperty bloomStrength = new NumberProperty("Bloom Strength", 4.0, bloom::getValue, 0.1, 10, 0.1);

    public static Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);
    public static Framebuffer bloomFramebuffer   = new Framebuffer(1, 1, false);

    public void renderShaders() {
        GL11.glGetError();
        if (!this.isEnabled()) return;

        if (blur.getValue()) {
            Blur.startBlur();
            blurQuery.begin();
            Tempest.INSTANCE.getEventBus().post(new ShaderEvent(ShaderEvent.ShaderType.BLUR));
            blurQuery.end();
            if (blurQuery.drewLastFrame()) Blur.endBlur(BLUR_RADIUS, BLUR_COMPRESSION);
            else Blur.endBlur(0, 0);
            RenderUtils.resetColor();
        }

        if (shadow.getValue()) {
            stencilFramebuffer = RenderUtils.createFrameBuffer(stencilFramebuffer, true);
            stencilFramebuffer.framebufferClear();
            stencilFramebuffer.bindFramebuffer(true);
            RenderUtils.resetColor();
            shadowQuery.begin();
            Tempest.INSTANCE.getEventBus().post(new ShaderEvent(ShaderEvent.ShaderType.SHADOW));
            shadowQuery.end();
            stencilFramebuffer.unbindFramebuffer();
            RenderUtils.resetColor();

            if (stencilFramebuffer.framebufferTexture > 0 && shadowQuery.drewLastFrame()) {
                Shadow.renderShadow(stencilFramebuffer.framebufferTexture, 50, 1);
            }
        }

        if (bloom.getValue()) {
            bloomFramebuffer = RenderUtils.createFrameBuffer(bloomFramebuffer);
            bloomFramebuffer.framebufferClear();
            bloomFramebuffer.bindFramebuffer(false);

            bloomQuery.begin();
            Tempest.INSTANCE.getEventBus().post(new ShaderEvent(ShaderEvent.ShaderType.BLOOM));
            bloomQuery.end();

            bloomFramebuffer.unbindFramebuffer();

            if (bloomQuery.drewLastFrame()) {
                Bloom.renderBloom(
                        bloomFramebuffer.framebufferTexture,
                        BLOOM_ITERATIONS,
                        BLOOM_RADIUS,
                        bloomStrength.getValue().floatValue()
                );
            }
        }
    }

    private final PassQuery blurQuery = new PassQuery(), shadowQuery = new PassQuery(), bloomQuery = new PassQuery();

    private static final class PassQuery {
        private int id = -1;
        private boolean pending, drew = true, broken;

        void begin() {
            if (broken) return;
            try {
                if (id < 0) id = org.lwjgl.opengl.GL15.glGenQueries();
                if (pending) {
                    if (org.lwjgl.opengl.GL15.glGetQueryObjecti(id, org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
                    drew = org.lwjgl.opengl.GL15.glGetQueryObjecti(id, org.lwjgl.opengl.GL15.GL_QUERY_RESULT) > 0;
                    pending = false;
                }
                org.lwjgl.opengl.GL15.glBeginQuery(org.lwjgl.opengl.GL15.GL_SAMPLES_PASSED, id);
                active = true;
            } catch (Throwable t) {
                broken = true;
                drew = true;
            }
        }

        private boolean active;

        void end() {
            if (!active) return;
            active = false;
            try {
                org.lwjgl.opengl.GL15.glEndQuery(org.lwjgl.opengl.GL15.GL_SAMPLES_PASSED);
                pending = true;
            } catch (Throwable t) {
                broken = true;
                drew = true;
            }
        }

        boolean drewLastFrame() {
            return broken || drew;
        }
    }
}
