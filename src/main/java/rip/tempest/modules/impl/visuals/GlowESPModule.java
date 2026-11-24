package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shader3d.FramebufferShader;
import rip.tempest.utils.render.shader3d.impl.GlowShader;
import rip.tempest.utils.render.shader3d.impl.OutlineShader;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Glow ESP", category = ModuleCategory.VISUALS)
public final class GlowESPModule extends Module {
    private static final float COLOR_GLIDE_MS = 1500F;

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Outline);
    private final NumberProperty outlineWidth = new NumberProperty("Outline Width", 2, () -> mode.getValue() == Mode.Outline, 1, 4, 0.25);
    private final ModeProperty<ColorMode> colorMode = new ModeProperty<>("Color", ColorMode.Animated);

    private final NumberProperty fadeTime = new NumberProperty("Fade Time", 60, () -> colorMode.getValue() == ColorMode.Animated, 5, 300, 5);
    private final Property<Boolean> players = new Property<>("Players", true);
    private final Property<Boolean> animals = new Property<>("Animals", true);
    private final Property<Boolean> mobs = new Property<>("Mobs", true);

    private Frustum frustum;
    private final List<Entity> entities = new ArrayList<>();
    private final float[] shownColor = new float[3];
    private boolean colorPrimed;
    private long lastColorUpdate;

    @Override
    public void onEnable() {
        super.onEnable();
        colorPrimed = false;
    }

    @EventLink
    public final Listener<Render2DEvent> render2DEventListener = e -> {
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;

        final boolean glow = mode.getValue() == Mode.Glow;
        final FramebufferShader shader = glow ? GlowShader.GLOW_SHADER : OutlineShader.OUTLINE_SHADER;
        float partialTicks = mc.timer.renderPartialTicks;

        collectEntities(partialTicks);
        if (entities.isEmpty()) return;

        shader.startDraw(e.getPartialTicks());
        RendererLivingEntity.hideNameTags = true;
        try {
            for (Entity entity : entities) {
                renderModel(entity, partialTicks);
            }
        } finally {
            RendererLivingEntity.hideNameTags = false;
        }

        if (glow) shader.stopDraw(color(), 5f, 2.4f);
        else shader.stopDraw(color(), outlineWidth.getValue().floatValue(), 1f);
    };

    private void collectEntities(float partialTicks) {
        entities.clear();

        Entity view = mc.getRenderViewEntity();
        if (frustum == null) frustum = new Frustum();
        frustum.setPosition(
                view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks,
                view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks,
                view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks);

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity.isDead) continue;
            if (entity == view && mc.gameSettings.thirdPersonView == 0) continue;
            if (!frustum.isBoundingBoxInFrustum(entity.getEntityBoundingBox())) continue;

            boolean wanted = entity instanceof EntityPlayer ? players.getValue()
                    : entity instanceof EntityAnimal ? animals.getValue()
                    : entity instanceof IMob && mobs.getValue();
            if (wanted) entities.add(entity);
        }
    }

    private void renderModel(Entity entity, float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        Render<Entity> render = renderManager.getEntityRenderObject(entity);
        if (render == null) return;

        int brightness = entity.isBurning() ? 15728880 : entity.getBrightnessForRender(partialTicks);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightness % 65536, brightness / 65536);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderManager.renderPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderManager.renderPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderManager.renderPosZ;
        float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
        render.doRender(entity, x, y, z, yaw, partialTicks);
    }

    private Color color() {
        Color target = targetColor();
        long now = System.currentTimeMillis();
        if (!colorPrimed) {
            shownColor[0] = target.getRed();
            shownColor[1] = target.getGreen();
            shownColor[2] = target.getBlue();
            colorPrimed = true;
        } else {
            float step = 1F - (float) Math.exp(-Math.min(now - lastColorUpdate, 1000L) / COLOR_GLIDE_MS);
            shownColor[0] += (target.getRed() - shownColor[0]) * step;
            shownColor[1] += (target.getGreen() - shownColor[1]) * step;
            shownColor[2] += (target.getBlue() - shownColor[2]) * step;
        }
        lastColorUpdate = now;
        return new Color(Math.round(shownColor[0]), Math.round(shownColor[1]), Math.round(shownColor[2]));
    }

    private Color targetColor() {
        Color first = ColorProcess.colors.getFirst();
        if (colorMode.getValue() == ColorMode.Static) return first;

        long period = (long) (fadeTime.getValue() * 2000);
        double phase = (System.currentTimeMillis() % period) / (double) period;
        float blend = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2));
        return RenderUtils.interpolateColorC(first, ColorProcess.colors.getSecond(), blend);
    }

    public enum Mode {
        Outline,
        Glow
    }

    public enum ColorMode {
        Static,
        Animated
    }
}
