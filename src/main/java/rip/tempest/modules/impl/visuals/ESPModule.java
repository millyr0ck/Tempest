package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.ESPUtils;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "ESP", category = ModuleCategory.VISUALS)
public final class ESPModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Corners);
    public static Property<Boolean> players = new Property<>("Only Players", true);

    private final Property<Boolean> healthBar = new Property<>("Health Bar", false);
    private final Property<Boolean> redOnDamage = new Property<>("Red On Damage", true);
    private final Property<Boolean> rainbow = new Property<>("Rainbow", false);
    private final Property<Boolean> showInvis = new Property<>("Show Invis", true);
    private final Property<Boolean> renderSelf = new Property<>("Render Self", false);
    private final NumberProperty maxDistance = new NumberProperty("Max Distance", 128, 16, 512, 8);

    public enum Mode {
        GameSense,
        Full,
        Box,
        Outline,
        Corners,
        Flat,
        Ring,
        Skeleton,
        HealthBar
    }

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        final Mode m = mode.getValue();
        final boolean boxy = m == Mode.Full || m == Mode.Box || m == Mode.Outline
                || m == Mode.Corners || m == Mode.Flat || m == Mode.Ring;

        final boolean drawHealth = healthBar.getValue() || m == Mode.HealthBar;

        if (boxy || drawHealth) {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2848);
            GL11.glDisable(2929);
            GL11.glDisable(3553);
            GlStateManager.disableCull();
            GL11.glDepthMask(false);
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!shouldRender(entity)) continue;
                final float[] c = colorFor(entity);
                if (boxy) drawShape(m, entity, c[0], c[1], c[2]);
                if (drawHealth && entity instanceof EntityLivingBase) {
                    ESPUtils.drawHealthBarESP((EntityLivingBase) entity);
                }
            }
            GL11.glDepthMask(true);
            GlStateManager.enableCull();
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDisable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(2848);
        }

        if (m == Mode.Skeleton) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!(entity instanceof EntityPlayer) || !shouldRender(entity)) continue;
                final EntityPlayer player = (EntityPlayer) entity;
                final Render<?> render = mc.getRenderManager().getEntityRenderObject(player);
                if (render instanceof RenderPlayer) {
                    ESPUtils.drawSkeletonESP(player, (ModelBiped) ((RenderPlayer) render).getMainModel(), colorIntFor(player));
                }
            }
        }
    };

    @EventLink
    public final Listener<Render2DEvent> render2DEventListener = event -> {
        if (mode.getValue() != Mode.GameSense || mc.theWorld == null || mc.thePlayer == null) return;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!shouldRender(entity)) continue;
            final float[] c = colorFor(entity);
            ESPUtils.render2DESP(entity.getEntityBoundingBox()
                            .offset(-entity.posX, -entity.posY, -entity.posZ)
                            .offset(ESPUtils.interpolate(entity.lastTickPosX, entity.posX),
                                    ESPUtils.interpolate(entity.lastTickPosY, entity.posY),
                                    ESPUtils.interpolate(entity.lastTickPosZ, entity.posZ))
                            .offset(-mc.getRenderManager().viewerPosX,
                                    -mc.getRenderManager().viewerPosY,
                                    -mc.getRenderManager().viewerPosZ),
                    new java.awt.Color(c[0], c[1], c[2]), 0.25f);
        }
    };

    private void drawShape(final Mode m, final Entity entity, final float r, final float g, final float b) {
        switch (m) {
            case Full -> ESPUtils.drawEntityESP(entity, r, g, b, 0.5f, 1.0f, 6.0f);
            case Box -> ESPUtils.drawEntityESP(entity, r, g, b, 0.5f, 1.0f, 0.0f);
            case Outline -> ESPUtils.drawOutlineEntityESP(entity, r, g, b, 0.5f, 1.0f, 6.0f);
            case Corners -> ESPUtils.drawCornerESP(entity, r, g, b);
            case Flat -> ESPUtils.drawFake2DESP(entity, r, g, b);
            case Ring -> ESPUtils.drawRingESP(entity, r, g, b);
            default -> {
            }
        }
    }

    private boolean shouldRender(final Entity entity) {
        if (entity == null) return false;
        if (players.getValue() && !(entity instanceof EntityPlayer)) return false;
        if (entity == mc.thePlayer) return renderSelf.getValue();
        if (AntiBotModule.botList.contains(entity)) return false;
        if (!showInvis.getValue() && entity.isInvisible()) return false;
        return mc.thePlayer.getDistanceToEntity(entity) <= maxDistance.getValue();
    }

    private float[] colorFor(final Entity entity) {
        final int rgb = colorIntFor(entity);
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private int colorIntFor(final Entity entity) {
        if (redOnDamage.getValue() && entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0) {
            return 0xFFFF0000;
        }
        if (rainbow.getValue()) {
            return RenderUtils.getRainbowFromEntity(System.currentTimeMillis(), 4000, entity.getEntityId(), false, 1.0f);
        }
        return ColorProcess.getColor().getRGB();
    }
}
