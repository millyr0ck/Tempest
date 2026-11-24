package rip.tempest.modules.impl.visuals;
import rip.tempest.api.events.impl.game.LivingUpdateEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "DMGParticle", category = ModuleCategory.VISUALS)
public final class DamageParticles extends Module {
    private final Map<EntityLivingBase, Float> healthMap = new WeakHashMap<>();

    private final List<Particles> particles = new CopyOnWriteArrayList<>();

    private final ModeProperty<ColorMode> colorMode = new ModeProperty<>("Color Mode", ColorMode.Health);

    private enum ColorMode { Health, Client }

    @EventLink
    public final Listener<LivingUpdateEvent> livingUpdateEventListener = e -> {
        if (e.getEntity() == null || !(e.getEntity() instanceof EntityLivingBase)) {
            return;
        }
        if (mc.thePlayer == null) return;

        if (!e.getEntity().worldObj.isRemote) return;
        EntityLivingBase entity = (EntityLivingBase) e.getEntity();
        if (entity == mc.thePlayer) return;
        if (!this.healthMap.containsKey(entity)) this.healthMap.put(entity, entity.getHealth());
        float floatValue = this.healthMap.get(entity);
        float health = entity.getHealth();
        if (floatValue != health) {
            boolean heal = health > floatValue;
            boolean crit = entity.hurtResistantTime < 18 || mc.thePlayer.motionY < 0 && !mc.thePlayer.onGround;
            String color = heal ? "\247a" : crit ? "\247c" : "\247e";
            String text = floatValue - health < 0.0f ? color + roundToPlace((floatValue - health) * -1.0f, 1) : color + roundToPlace(floatValue - health, 1);
            Location location = new Location(entity);
            location.setY(entity.getEntityBoundingBox().minY + (entity.getEntityBoundingBox().maxY - entity.getEntityBoundingBox().minY) / 2.0);
            location.setX(location.getX() - 0.5 + new Random(System.currentTimeMillis()).nextInt(5) * 0.1);
            location.setZ(location.getZ() - 0.5 + new Random(System.currentTimeMillis() + 1).nextInt(5) * 0.1);
            this.particles.add(new Particles(location, text));
            this.healthMap.remove(entity);
            this.healthMap.put(entity, entity.getHealth());
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        this.healthMap.clear();
        this.particles.clear();
    };

    @Override
    public void onDisable() {
        super.onDisable();
        this.healthMap.clear();
        this.particles.clear();
    }

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = event -> {
        for (Particles update : this.particles) {
            ++update.ticks;
            if (update.ticks <= 10)
                update.location.setY(update.location.getY() + update.ticks * 0.005);
        }
        this.particles.removeIf(update -> update.ticks > 20);
    };

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = event -> {
        for (Particles p : this.particles) {
            double x = p.location.getX();
            double n = x - mc.getRenderManager().renderPosX;
            double y = p.location.getY();
            double n2 = y - mc.getRenderManager().renderPosY;
            double z = p.location.getZ();
            double n3 = z - mc.getRenderManager().renderPosZ;
            GlStateManager.pushMatrix();
            GlStateManager.enablePolygonOffset();
            GlStateManager.doPolygonOffset(1.0f, -1500000.0f);
            GlStateManager.translate((float) n, (float) n2, (float) n3);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
            float textY = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, textY, 0.0f, 0.0f);
            final double size = 0.03;
            GlStateManager.scale(-size, -size, size);
            GL11.glDepthMask(false);

            String display = p.text;
            int rgb = 0;
            if (colorMode.getValue() == ColorMode.Client) {
                display = p.text.replaceAll("\247.", "");
                rgb = ColorProcess.getColor().getRGB();
            }
            float drawX = (float) -(mc.fontRendererObj.getStringWidth(display) / 2);
            float drawY = (float) -(mc.fontRendererObj.FONT_HEIGHT - 1);
            mc.fontRendererObj.drawStringWithShadow(display, drawX, drawY, rgb);
            mc.fontRendererObj.drawStringWithShadow(display, drawX, drawY, rgb);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glDepthMask(true);
            GlStateManager.doPolygonOffset(1.0f, 1500000.0f);
            GlStateManager.disablePolygonOffset();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    };

    public static double roundToPlace(double p_roundToPlace_0_, int p_roundToPlace_2_) {
        if (p_roundToPlace_2_ < 0) throw new IllegalArgumentException();
        return new BigDecimal(p_roundToPlace_0_).setScale(p_roundToPlace_2_, RoundingMode.HALF_UP).doubleValue();
    }

    public static class Particles {
        public int ticks;
        public Location location;
        public String text;

        public Particles(final Location location, final String text) {
            this.location = location;
            this.text = text;
            this.ticks = 0;
        }
    }

    @Getter
    @Setter
    public static class Location {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public Location(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location(EntityLivingBase entity) {
            this.x = entity.posX;
            this.y = entity.posY;
            this.z = entity.posZ;
            this.yaw = 0.0f;
            this.pitch = 0.0f;
        }
    }
}
