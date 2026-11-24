package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.misc.Pair;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Ambience", category = ModuleCategory.VISUALS)
public final class AmbienceModule extends Module {
    private final ModeProperty<TimeEnum> timeProperty = new ModeProperty<>("Time", TimeEnum.Night);
    private final Property<Boolean> rainProperty = new Property<>("Rain", false);

    private final ModeProperty<SkyLook> sky = new ModeProperty<>("Sky", SkyLook.Vanilla);
    private final NumberProperty skyStrength = new NumberProperty("Sky Strength", 80, () -> sky.getValue() != SkyLook.Vanilla, 10, 100, 1);

    private final Property<Boolean> overlay = new Property<>("Overlay", false);
    private final NumberProperty grain = new NumberProperty("Film Grain", 25, overlay::getValue, 0, 100, 1);
    private final NumberProperty grainSize = new NumberProperty("Grain Size", 1.5, overlay::getValue, 1, 4, 0.5);
    private final NumberProperty vignette = new NumberProperty("Vignette", 35, overlay::getValue, 0, 100, 1);
    private final ModeProperty<Grade> grade = new ModeProperty<>("Color Grade", Grade.Film, overlay::getValue);
    private final NumberProperty gradeStrength = new NumberProperty("Grade Strength", 60, () -> overlay.getValue() && grade.getValue() != Grade.None, 0, 100, 1);
    private final NumberProperty aberration = new NumberProperty("Chromatic Aberration", 0, overlay::getValue, 0, 100, 1);
    private final NumberProperty lightLeak = new NumberProperty("Light Leak", 0, overlay::getValue, 0, 100, 1);
    private final ModeProperty<Bars> letterbox = new ModeProperty<>("Letterbox", Bars.Off, overlay::getValue);

    public enum TimeEnum {
        Day,
        Night,

        Server,

        Sunrise,
        Sunset,
    }

    public enum SkyLook {
        Vanilla(null, null, null, null, null),
        GoldenHour("Golden Hour", new float[]{0.36f, 0.50f, 0.78f}, new float[]{1.00f, 0.70f, 0.44f}, new float[]{1.00f, 0.55f, 0.25f}, new float[]{1.00f, 0.86f, 0.72f}),
        Dusk("Dusk", new float[]{0.15f, 0.16f, 0.36f}, new float[]{0.90f, 0.48f, 0.46f}, new float[]{0.95f, 0.36f, 0.45f}, new float[]{0.78f, 0.58f, 0.68f}),
        BlueHour("Blue Hour", new float[]{0.08f, 0.13f, 0.32f}, new float[]{0.36f, 0.50f, 0.72f}, new float[]{0.50f, 0.58f, 0.88f}, new float[]{0.48f, 0.54f, 0.70f}),
        Clear("Clear", new float[]{0.20f, 0.44f, 0.86f}, new float[]{0.74f, 0.85f, 0.97f}, new float[]{1.00f, 0.80f, 0.60f}, new float[]{1.00f, 1.00f, 1.00f}),
        Overcast("Overcast", new float[]{0.48f, 0.52f, 0.56f}, new float[]{0.70f, 0.71f, 0.72f}, new float[]{0.80f, 0.76f, 0.70f}, new float[]{0.82f, 0.84f, 0.87f}),
        Night("Night", new float[]{0.02f, 0.03f, 0.08f}, new float[]{0.08f, 0.10f, 0.20f}, new float[]{0.30f, 0.30f, 0.62f}, new float[]{0.20f, 0.22f, 0.30f});

        private final String display;
        private final float[] zenith, horizon, sun, clouds;

        SkyLook(String display, float[] zenith, float[] horizon, float[] sun, float[] clouds) {
            this.display = display;
            this.zenith = zenith;
            this.horizon = horizon;
            this.sun = sun;
            this.clouds = clouds;
        }

        @Override
        public String toString() {
            return display == null ? name() : display;
        }
    }

    public enum Grade {
        None, Film, Warm, Cool, TealOrange, Faded, Mono;

        @Override
        public String toString() {
            return this == TealOrange ? "Teal & Orange" : name();
        }
    }

    public enum Bars {
        Off("Off", 0f), Cinema("2.39:1", 2.39f), Wide("2:1", 2.0f), Film("1.85:1", 1.85f);

        private final String display;
        private final float ratio;

        Bars(String display, float ratio) {
            this.display = display;
            this.ratio = ratio;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(timeProperty.getValue().toString());
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.theWorld == null)
            return;
        switch (timeProperty.getValue()) {
            case Day:
                mc.theWorld.setWorldTime(1000);
                break;
            case Night:
                mc.theWorld.setWorldTime(18000);
                break;
            case Sunrise:
                mc.theWorld.setWorldTime(23400);
                break;
            case Sunset:
                mc.theWorld.setWorldTime(12600);
                break;
            default:
                break;
        }
        if (rainProperty.getValue()) {
            mc.theWorld.setRainStrength(1);
            mc.theWorld.setThunderStrength(1);
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        if (event.getPacket() instanceof S03PacketTimeUpdate && timeProperty.getValue() != TimeEnum.Server) {
            event.setCancelled(true);
        }
    };

    public static float[] tintFog(float r, float g, float b) {
        AmbienceModule module = skyLook();
        return module == null ? null : module.toward(r, g, b, module.sky.getValue().horizon, 1f);
    }

    public static Vec3 tintSky(Vec3 sky) {
        AmbienceModule module = skyLook();
        if (module == null) return sky;
        float[] looked = module.toward((float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord, module.sky.getValue().zenith, 1f);
        return new Vec3(looked[0], looked[1], looked[2]);
    }

    public static float[] sunGlow(float[] colors) {
        AmbienceModule module = skyLook();
        if (module == null || colors == null) return colors;
        float[] c = module.toward(colors[0], colors[1], colors[2], module.sky.getValue().sun, 1f);
        float k = module.skyStrength.getValue().floatValue() / 100f;
        return new float[]{c[0], c[1], c[2], Math.min(1f, colors[3] * (1f + 0.35f * k))};
    }

    public static Vec3 tintClouds(Vec3 clouds) {
        AmbienceModule module = skyLook();
        if (module == null) return clouds;
        float[] tint = module.sky.getValue().clouds;
        float k = module.skyStrength.getValue().floatValue() / 100f;
        return new Vec3(clouds.xCoord * (1 - k + tint[0] * k), clouds.yCoord * (1 - k + tint[1] * k), clouds.zCoord * (1 - k + tint[2] * k));
    }

    private static AmbienceModule skyLook() {
        if (Tempest.INSTANCE == null || Tempest.INSTANCE.getModuleManager() == null) return null;
        AmbienceModule module = Tempest.INSTANCE.getModuleManager().getModule(AmbienceModule.class);
        if (module == null || !module.isEnabled() || module.sky.getValue() == SkyLook.Vanilla) return null;
        net.minecraft.entity.Entity view = mc.getRenderViewEntity();
        if (view != null && (view.isInsideOfMaterial(net.minecraft.block.material.Material.water)
                || view.isInsideOfMaterial(net.minecraft.block.material.Material.lava))) return null;
        return module;
    }

    private float[] toward(float r, float g, float b, float[] target, float scale) {
        float k = skyStrength.getValue().floatValue() / 100f * scale;
        return new float[]{r + (target[0] - r) * k, g + (target[1] - g) * k, b + (target[2] - b) * k};
    }

    private static rip.tempest.utils.render.shaders.ShaderUtils overlayShader;
    private static boolean overlayFailed;
    private static net.minecraft.client.shader.Framebuffer overlayCopy;

    public static void renderOverlay() {
        if (overlayFailed || mc.theWorld == null || Tempest.INSTANCE == null || Tempest.INSTANCE.getModuleManager() == null) return;
        AmbienceModule m = Tempest.INSTANCE.getModuleManager().getModule(AmbienceModule.class);
        if (m == null || !m.isEnabled() || !m.overlay.getValue()) return;

        float grainAmount = m.grain.getValue().floatValue() / 100f;
        float vignetteAmount = m.vignette.getValue().floatValue() / 100f;
        float gradeAmount = m.grade.getValue() == Grade.None ? 0f : m.gradeStrength.getValue().floatValue() / 100f;
        float aberrationAmount = m.aberration.getValue().floatValue() / 100f;
        float leakAmount = m.lightLeak.getValue().floatValue() / 100f;
        int w = mc.displayWidth, h = mc.displayHeight;
        float bars = 0f;
        float ratio = m.letterbox.getValue().ratio;
        if (ratio > 0f && (float) w / h < ratio) bars = (1f - ((float) w / h) / ratio) / 2f;
        if (grainAmount <= 0 && vignetteAmount <= 0 && gradeAmount <= 0 && aberrationAmount <= 0 && leakAmount <= 0 && bars <= 0) return;

        if (overlayShader == null) {
            try {
                overlayShader = new rip.tempest.utils.render.shaders.ShaderUtils("tempest/shaders/overlay.fsh");
            } catch (Throwable t) {
                overlayFailed = true;
                t.printStackTrace();
                return;
            }
        }

        overlayCopy = rip.tempest.utils.render.RenderUtils.createFrameBuffer(overlayCopy);
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(overlayCopy.framebufferTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);

        mc.entityRenderer.setupOverlayRendering();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        GlStateManager.color(1f, 1f, 1f, 1f);

        overlayShader.init();
        overlayShader.setUniformi("scene", 0);
        overlayShader.setUniformf("resolution", w, h);
        overlayShader.setUniformf("texSize", overlayCopy.framebufferTextureWidth, overlayCopy.framebufferTextureHeight);
        overlayShader.setUniformf("time", (System.currentTimeMillis() % 3_600_000L) / 1000f);
        overlayShader.setUniformi("grade", m.grade.getValue().ordinal());
        overlayShader.setUniformf("gradeAmount", gradeAmount);
        overlayShader.setUniformf("aberration", aberrationAmount);
        overlayShader.setUniformf("leak", leakAmount);
        overlayShader.setUniformf("vignette", vignetteAmount);
        overlayShader.setUniformf("grain", grainAmount);
        overlayShader.setUniformf("grainSize", m.grainSize.getValue().floatValue());
        overlayShader.setUniformf("letterbox", bars);
        rip.tempest.utils.render.shaders.ShaderUtils.drawQuads();
        overlayShader.unload();

        GlStateManager.bindTexture(0);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    }
}
