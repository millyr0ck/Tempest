package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
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
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import rip.tempest.utils.misc.PathFinder;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Teleport", category = ModuleCategory.COMBAT)
public final class SnapModule extends Module {
    private static final double HOP_Y1 = 0.16, HOP_Y2 = 0.07;
    private static final float CORE_WIDTH = 1.5f, CORE_ALPHA = 1f, GLOW_WIDTH = 4f, GLOW_ALPHA = 0.14f;

    private final NumberProperty reach = new NumberProperty("Reach", 100, 3, 200, 1);
    private final NumberProperty delay = new NumberProperty("Delay (ms)", 3000, 500, 10000, 100);
    private final Property<Boolean> renderTrail = new Property<>("Render Trail", true);

    private final Timer timer = new Timer();
    private volatile boolean hopping;
    private long hoppingTimestamp;

    private Entity target;
    private List<Vec3> path;

    @Override
    public void onEnable() {
        hopping = false;
        hoppingTimestamp = 0L;
        target = null;
        path = null;
    }

    @Override
    public void onDisable() {
        hopping = false;
        path = null;
        super.onDisable();
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (hopping) {
            e.setX(0.0D);
            e.setY(0.0D);
            e.setZ(0.0D);
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || mc.thePlayer == null || mc.theWorld == null) return;

        if (hopping) {
            if (System.currentTimeMillis() - hoppingTimestamp > 8000L) {
                hopping = false;
                setSuffix("Timeout");
            } else {
                mc.thePlayer.motionX = 0.0D;
                mc.thePlayer.motionY = 0.0D;
                mc.thePlayer.motionZ = 0.0D;
                mc.thePlayer.noClip = true;
                mc.thePlayer.onGround = false;
                return;
            }
        }

        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;
        if (!mc.thePlayer.onGround) return;

        final List<Entity> targets = TargetSelectionProcess.targetsWithin(reach.getValue().floatValue());
        if (targets.isEmpty()) {
            setSuffix("No Target");
            return;
        }

        target = targets.getFirst();

        final double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + HOP_Y1, z, false));
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + HOP_Y2, z, false));

        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
        mc.thePlayer.noClip = true;
        mc.thePlayer.onGround = false;
        mc.thePlayer.isAirBorne = true;

        hopping = true;
        hoppingTimestamp = System.currentTimeMillis();
        setSuffix("→ " + target.getName());
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (hopping && e.getPacket() instanceof C03PacketPlayer) e.setCancelled();
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!hopping || !(e.getPacket() instanceof S08PacketPlayerPosLook)) return;
        hopping = false;
        timer.reset();

        if (target == null) {
            setSuffix("No Target");
            return;
        }

        final S08PacketPlayerPosLook s08 = (S08PacketPlayerPosLook) e.getPacket();
        final Vec3 from = new Vec3(s08.getX(), s08.getY(), s08.getZ());

        mc.addScheduledTask(() -> {
            final Vec3 to = new Vec3(target.posX, target.posY, target.posZ);
            final PathFinder pf = new PathFinder(from, to);
            pf.compute(5000, 8);
            final ArrayList<Vec3> raw = pf.getPath();
            if (raw == null || raw.isEmpty()) {
                setSuffix("No Path");
                return;
            }

            final List<Vec3> computed = new ArrayList<>(raw.size());
            for (final Vec3 v : raw) computed.add(v.addVector(0.5, 0, 0.5));

            for (final Vec3 v : computed) {
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                        v.xCoord, v.yCoord, v.zCoord, false));
            }

            final Vec3 end = computed.get(computed.size() - 1);

            mc.thePlayer.setPosition(end.xCoord, end.yCoord, end.zCoord);
            mc.thePlayer.noClip = false;

            path = TPAuraModule.withStops(computed);
            setSuffix("Snapped to " + target.getName());
            if (isEnabled()) toggle();
        });
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (!renderTrail.getValue() || path == null) return;

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
        final double camX = mc.getRenderManager().getRenderPosX(),
                camY = mc.getRenderManager().getRenderPosY(),
                camZ = mc.getRenderManager().getRenderPosZ();
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        final int last = points.size() - 1;
        final int a = Math.round(alpha * 255);
        for (int i = 0; i <= last; i++) {
            final Vec3 p = points.get(i);
            final int color = pathColor(i / (float) last);
            buffer.pos(p.xCoord - camX, p.yCoord + 0.01 - camY, p.zCoord - camZ)
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, a).endVertex();
        }
        tessellator.draw();
    }

    private static int pathColor(final float along) {
        if (Themes.active()) return Themes.strip(Themes.current(), along, true);
        return RenderUtils.interpolateColorC(ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), along).getRGB();
    }
}
