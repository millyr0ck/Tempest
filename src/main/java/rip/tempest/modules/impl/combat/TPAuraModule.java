package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.TargetSelectionProcess;
import rip.tempest.processes.Themes;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PathFinderUtils;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "TP Aura", category = ModuleCategory.COMBAT)
public final class TPAuraModule extends Module {
    public static NumberProperty reach   = new NumberProperty("Reach", 100, 3, 200, 1);
    public static NumberProperty delay   = new NumberProperty("Delay (ms)", 2000, 200, 5000, 100);
    private final Property<Boolean> renderTrail = new Property<>("Render Trail", true);

    private final Property<Boolean> mountBypass = new Property<>("Mount Bypass", false);

    private final Property<Boolean> criticalsOnly = new Property<>("Criticals Only", false, () -> !mountBypass.getValue());

    private final Timer attackTimer = new Timer();
    public Entity target;

    private List<Vec3> path;

    private static final double PATH_SPACING = 1.0;
    private static final float CORE_WIDTH = 1.5f, CORE_ALPHA = 1f, GLOW_WIDTH = 4f, GLOW_ALPHA = 0.14f;

    private boolean isSneaking = false;
    private boolean isReady = false;

    @Override
    public void onEnable() {
        isSneaking = false;
        isReady = false;
        path = null;
        target = null;
    }

    @Override
    public void onDisable() {
        if (isSneaking && mc.thePlayer != null) {
            PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
            isSneaking = false;
        }
        path = null;
        target = null;
        super.onDisable();
    }

    @EventLink
    public final Listener<MotionEvent> onMotion = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (!event.isPre()) return;

        final boolean mounted = mountBypass.getValue();
        if (mounted) {
            if (!isReady) {
                setSuffix("Mount First");
                if (mc.thePlayer.isRiding()) {
                    PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                    isSneaking = true;
                    isReady = true;
                    attackTimer.reset();
                }
                return;
            }

            if (isSneaking) {
                PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
                isSneaking = false;
            } else {
                PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                isSneaking = true;
            }
        } else if (isSneaking) {
            PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
            isSneaking = false;
            isReady = false;
        }

        if (!attackTimer.hasTimeElapsed(delay.getValue().longValue())) return;

        final List<Entity> targets = TargetSelectionProcess.targetsWithin(reach.getValue().floatValue());
        if (targets.isEmpty()) {
            target = null;
            setSuffix("No Target");
            return;
        }
        final boolean crit = !mounted && criticalsOnly.getValue();
        target = crit ? firstCritable(targets) : targets.getFirst();
        if (target == null) {
            setSuffix("No Crit");
            return;
        }

        final List<Vec3> computed = tryAttack(target, mounted, crit);
        if (computed != null && !computed.isEmpty()) {
            path = withStops(computed);
            setSuffix("Hit");
            if (mounted) {
                event.setCancelled(true);
                mc.thePlayer.posY += 0.5;
            }
        }

        attackTimer.reset();
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (!renderTrail.getValue() || path == null || target == null) return;

        final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        final boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        final int shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        GL11.glLineWidth(GLOW_WIDTH);
        drawPath(path, GLOW_ALPHA);
        GL11.glLineWidth(CORE_WIDTH);
        drawPath(path, CORE_ALPHA);

        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.shadeModel(shade);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        if (fog) GlStateManager.enableFog();
        if (lighting) GlStateManager.enableLighting();
        GlStateManager.resetColor();
    };

    private static void drawPath(final List<Vec3> points, final float alpha) {
        final double camX = mc.getRenderManager().getRenderPosX(), camY = mc.getRenderManager().getRenderPosY(), camZ = mc.getRenderManager().getRenderPosZ();
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        final int last = points.size() - 1;
        final int a = Math.round(alpha * 255);
        for (int i = 0; i <= last; i++) {
            final Vec3 point = points.get(i);
            final int color = pathColor(i / (float) last);
            buffer.pos(point.xCoord - camX, point.yCoord + 0.01 - camY, point.zCoord - camZ)
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, a).endVertex();
        }
        tessellator.draw();
    }

    private static int pathColor(final float along) {
        if (Themes.active()) return Themes.strip(Themes.current(), along, true);
        return RenderUtils.interpolateColorC(ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), along).getRGB();
    }

    static List<Vec3> withStops(final List<Vec3> route) {
        final List<Vec3> points = new ArrayList<>();
        points.add(route.get(0));
        for (int i = 0; i < route.size() - 1; i++) {
            final Vec3 a = route.get(i), b = route.get(i + 1);
            final int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / PATH_SPACING));
            for (int step = 1; step <= steps; step++) {
                final double t = step / (double) steps;
                points.add(new Vec3(a.xCoord + (b.xCoord - a.xCoord) * t, a.yCoord + (b.yCoord - a.yCoord) * t, a.zCoord + (b.zCoord - a.zCoord) * t));
            }
        }
        return points;
    }

    private static Entity firstCritable(final List<Entity> targets) {
        if (mc.thePlayer.isPotionActive(Potion.blindness) || mc.thePlayer.isRiding()) return null;
        for (final Entity entity : targets) {
            if (!entity.isInWater() && !((EntityLivingBase) entity).isOnLadder()) return entity;
        }
        return null;
    }

    private List<Vec3> tryAttack(final Entity t, final boolean mounted, final boolean crit) {
        final List<Vec3> computed = PathFinderUtils.computePath(
                new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                new Vec3(t.posX, t.posY, t.posZ),
                true);
        if (computed == null || computed.isEmpty()) return null;

        final EntityLivingBase previous = TargetSelectionProcess.getTarget();
        TargetSelectionProcess.setTarget((EntityLivingBase) t);
        final AttackEvent attackEvent = new AttackEvent(t);
        Tempest.INSTANCE.getEventBus().post(attackEvent);
        TargetSelectionProcess.setTarget(previous);
        if (attackEvent.isCancelled()) return null;

        mc.thePlayer.swingItem();

        for (final Vec3 v : computed) {
            PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(v.xCoord, v.yCoord, v.zCoord, false));
        }

        if (crit) {
            final Vec3 end = computed.get(computed.size() - 1);
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(end.xCoord, end.yCoord + 0.125, end.zCoord, false));
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(end.xCoord, end.yCoord + 0.046599998474120774, end.zCoord, false));
            PacketUtils.sendPacketNoEvent(new C02PacketUseEntity(t, C02PacketUseEntity.Action.ATTACK));
        } else {
            PacketUtils.sendSilentPacket(new C02PacketUseEntity(t, C02PacketUseEntity.Action.ATTACK));
        }

        final List<Vec3> reversed = new ArrayList<>(computed);
        Collections.reverse(reversed);
        for (final Vec3 v : reversed) {
            PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(v.xCoord, v.yCoord, v.zCoord, false));
        }

        if (!mounted) {
            PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.onGround));
        }

        return computed;
    }
}
