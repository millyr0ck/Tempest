package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.processes.ColorProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Tracers", category = ModuleCategory.VISUALS)
public final class TracersModule extends Module {
    private enum Colour {
        Client, Distance
    }

    private final Property<Boolean> players = new Property<>("Players", true);
    private final Property<Boolean> mobs = new Property<>("Mobs", false);
    private final Property<Boolean> animals = new Property<>("Animals", false);
    private final Property<Boolean> invisibles = new Property<>("Invisibles", false);
    private final ModeProperty<Colour> colour = new ModeProperty<>("Color", Colour.Client);
    private final NumberProperty lineWidth = new NumberProperty("Line Width", 1.5, 0.5, 5, 0.1);
    private final NumberProperty opacity = new NumberProperty("Opacity", 0.6, 0.1, 1, 0.05);

    private static final float FAR = 50f;

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = event -> {
        if (mc.theWorld == null || mc.thePlayer == null || mc.gameSettings.hideGUI) return;

        float partialTicks = mc.timer.renderPartialTicks;
        RenderManager renderManager = mc.getRenderManager();
        double viewX = renderManager.getRenderPosX(), viewY = renderManager.getRenderPosY(), viewZ = renderManager.getRenderPosZ();
        float alpha = opacity.getValue().floatValue();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        float eye = mc.entityRenderer.applySteadyCamera(partialTicks);

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(lineWidth.getValue().floatValue());

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        boolean any = false;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!shows(entity)) continue;

            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - viewX;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + entity.getEyeHeight() - viewY;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - viewZ;
            Color color = colorFor(entity);
            float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;

            buffer.pos(0.0, eye, 0.0).color(r, g, b, alpha).endVertex();
            buffer.pos(x, y, z).color(r, g, b, alpha).endVertex();
            any = true;
        }

        if (any) tessellator.draw();
        else buffer.finishDrawing();

        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        if (lighting) GlStateManager.enableLighting();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    };

    private boolean shows(Entity entity) {
        if (entity == mc.thePlayer || entity == mc.getRenderViewEntity() || entity.isDead) return false;
        if (entity.isInvisible() && !invisibles.getValue()) return false;
        if (entity instanceof EntityPlayer) {
            return players.getValue() && !AntiBotModule.botList.contains(entity) && ((EntityPlayer) entity).deathTime == 0;
        }
        if (entity instanceof IMob) return mobs.getValue();
        if (entity instanceof EntityAnimal) return animals.getValue();
        return false;
    }

    private Color colorFor(Entity entity) {
        if (colour.getValue() == Colour.Distance) {
            float far = Math.min(1f, mc.thePlayer.getDistanceToEntity(entity) / FAR);

            return Color.getHSBColor(far / 3f, 0.85f, 1f);
        }
        return ColorProcess.getColor();
    }
}
