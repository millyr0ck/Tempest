package rip.tempest.modules.impl.combat;

import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.ItemSlowdownEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Fast Bow", category = ModuleCategory.COMBAT, description = "Rapid bow with aimbot and prediction for NCP 3.15.2")
public final class FastBowModule extends Module {
    private final NumberProperty minMs    = new NumberProperty("Min MS", 690, 670, 900, 10);
    private final NumberProperty minTicks = new NumberProperty("Min Ticks", 14, 14, 20, 1);

    private final NumberProperty fov     = new NumberProperty("FOV", 90, 10, 180, 5);
    private final NumberProperty smooth  = new NumberProperty("Smooth", 3, 0, 10, 1);
    private final Property<Boolean> predict = new Property<>("Predict", true);

    private final Property<Boolean> showLine = new Property<>("Prediction Line", true);
    private final Property<Boolean> showESP  = new Property<>("Target ESP", true);

    private long pullStart;
    private float aimYaw, aimPitch;
    private boolean aimInit;
    private EntityPlayer lockedTarget;

    @EventLink
    public final Listener<ItemSlowdownEvent> slowdownListener = e -> {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() == Items.bow) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionListener = e -> {
        if (!e.isPre()) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        boolean isBow    = held != null && held.getItem() == Items.bow;
        boolean hasArrow = mc.thePlayer.inventory.hasItem(Items.arrow)
                        || mc.thePlayer.capabilities.isCreativeMode;
        boolean holdRMB  = mc.gameSettings.keyBindUseItem.isKeyDown();

        if (!isBow || !hasArrow || !holdRMB) {
            reset();
            return;
        }

        mc.timer.timerSpeed = 1.0F;

        lockedTarget = findTarget();
        if (lockedTarget != null) {
            float[] angles = getAimAngles(lockedTarget);
            if (angles != null) {
                if (!aimInit) {
                    aimYaw   = mc.thePlayer.rotationYaw;
                    aimPitch = mc.thePlayer.rotationPitch;
                    aimInit  = true;
                }
                int s = smooth.getValue().intValue();
                if (s == 0) {
                    aimYaw   = angles[0];
                    aimPitch = MathHelper.clamp_float(angles[1], -90f, 90f);
                } else {
                    double rate = 1.0 / (1.0 + s);
                    aimYaw   += (float) (wrapDiff(angles[0] - aimYaw)   * rate);
                    aimPitch += (float) ((MathHelper.clamp_float(angles[1], -90f, 90f) - aimPitch) * rate);
                }
                mc.thePlayer.rotationYaw   = aimYaw;
                mc.thePlayer.rotationPitch = aimPitch;
            }
        } else {
            aimInit = false;
        }

        if (!mc.thePlayer.isUsingItem()) {
            pullStart = 0L;
            return;
        }
        if (pullStart == 0L) pullStart = System.currentTimeMillis();

        int  used    = mc.thePlayer.getItemInUseDuration();
        long elapsed = System.currentTimeMillis() - pullStart;

        if (elapsed < minMs.getValue().longValue() || used < minTicks.getValue().intValue()) return;

        int need = Math.max(0, 20 - used);
        for (int i = 0; i < need; i++) {
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer(mc.thePlayer.onGround));
        }
        mc.getNetHandler().addToSendQueue(
                new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        new BlockPos(-1, -1, -1), EnumFacing.DOWN));
        mc.playerController.onStoppedUsingItem(mc.thePlayer);
        pullStart = 0L;
    };

    @EventLink
    public final Listener<PacketReceiveEvent> receiveListener = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) reset();
    };

    @EventLink
    public final Listener<Render3DEvent> renderListener = e -> {
        if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.hideGUI) return;
        if (!mc.thePlayer.isUsingItem() || !aimInit) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != Items.bow) return;

        float partial = mc.timer.renderPartialTicks;
        RenderManager rm = mc.getRenderManager();
        double viewX = rm.getRenderPosX(), viewY = rm.getRenderPosY(), viewZ = rm.getRenderPosZ();

        double arrowSpd = currentArrowSpeed();

        java.awt.Color col = ColorProcess.getColor();
        float r = col.getRed() / 255f, g = col.getGreen() / 255f, b = col.getBlue() / 255f;

        GlStateManager.pushMatrix();
        boolean hadLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        Tessellator tess   = Tessellator.getInstance();
        WorldRenderer buf  = tess.getWorldRenderer();

        if (showLine.getValue() && arrowSpd > 0.1) {
            double pitchRad = Math.toRadians(aimPitch);
            double yawRad   = Math.toRadians(aimYaw);
            double mX = -Math.sin(yawRad) * Math.cos(pitchRad) * arrowSpd;
            double mY = -Math.sin(pitchRad) * arrowSpd;
            double mZ =  Math.cos(yawRad) * Math.cos(pitchRad) * arrowSpd;

            double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partial - viewX;
            double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partial
                      + mc.thePlayer.getEyeHeight() - viewY;
            double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partial - viewZ;

            GL11.glLineWidth(1.5f);
            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            buf.pos(px, py, pz).color(r, g, b, 0.9f).endVertex();

            for (int t = 0; t < 100; t++) {
                px += mX; py += mY; pz += mZ;
                mX *= 0.99; mY *= 0.99; mZ *= 0.99;
                mY -= 0.05;

                float alpha = Math.max(0.1f, 0.9f - t * 0.009f);
                buf.pos(px, py, pz).color(r, g, b, alpha).endVertex();
                if (py + viewY < 0) break;
            }
            tess.draw();
        }

        if (showESP.getValue() && lockedTarget != null && !lockedTarget.isDead) {
            EntityPlayer t = lockedTarget;
            double tx = t.lastTickPosX + (t.posX - t.lastTickPosX) * partial - viewX;
            double ty = t.lastTickPosY + (t.posY - t.lastTickPosY) * partial - viewY;
            double tz = t.lastTickPosZ + (t.posZ - t.lastTickPosZ) * partial - viewZ;

            double x0 = tx - 0.3, x1 = tx + 0.3;
            double y0 = ty,        y1 = ty + t.height;
            double z0 = tz - 0.3, z1 = tz + 0.3;

            GL11.glLineWidth(1.5f);
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            boxEdge(buf, x0,y0,z0, x1,y0,z0, r,g,b,0.9f);
            boxEdge(buf, x1,y0,z0, x1,y0,z1, r,g,b,0.9f);
            boxEdge(buf, x1,y0,z1, x0,y0,z1, r,g,b,0.9f);
            boxEdge(buf, x0,y0,z1, x0,y0,z0, r,g,b,0.9f);

            boxEdge(buf, x0,y1,z0, x1,y1,z0, r,g,b,0.9f);
            boxEdge(buf, x1,y1,z0, x1,y1,z1, r,g,b,0.9f);
            boxEdge(buf, x1,y1,z1, x0,y1,z1, r,g,b,0.9f);
            boxEdge(buf, x0,y1,z1, x0,y1,z0, r,g,b,0.9f);

            boxEdge(buf, x0,y0,z0, x0,y1,z0, r,g,b,0.9f);
            boxEdge(buf, x1,y0,z0, x1,y1,z0, r,g,b,0.9f);
            boxEdge(buf, x1,y0,z1, x1,y1,z1, r,g,b,0.9f);
            boxEdge(buf, x0,y0,z1, x0,y1,z1, r,g,b,0.9f);
            tess.draw();
        }

        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        if (hadLighting) GlStateManager.enableLighting();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    };

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    private EntityPlayer findTarget() {
        Vec3   src  = getEyes();
        Vec3   look = mc.thePlayer.getLookVec();
        double half = fov.getValue() / 2.0;

        EntityPlayer best      = null;
        double       bestAngle = half;

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer || p.isDead || p.isInvisible()) continue;

            double dx = p.posX - src.xCoord;
            double dy = p.posY + p.getEyeHeight() * 0.5 - src.yCoord;
            double dz = p.posZ - src.zCoord;
            double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (len < 0.01 || len > 100) continue;

            double dot   = look.dotProduct(new Vec3(dx/len, dy/len, dz/len));
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp_double(dot, -1.0, 1.0)));
            if (angle < bestAngle) { bestAngle = angle; best = p; }
        }
        return best;
    }

    private float[] getAimAngles(EntityPlayer target) {
        double arrowSpd = currentArrowSpeed();
        if (arrowSpd < 0.1) return null;

        Vec3 src = getEyes();

        double tx = target.posX            - src.xCoord;
        double ty = target.posY + target.getEyeHeight() * 0.5 - src.yCoord;
        double tz = target.posZ            - src.zCoord;

        double tvx = target.posX - target.prevPosX;
        double tvy = target.posY - target.prevPosY;
        double tvz = target.posZ - target.prevPosZ;

        int iterations = predict.getValue() ? 4 : 1;
        for (int iter = 0; iter < iterations; iter++) {
            double horizDist = Math.sqrt(tx*tx + tz*tz);
            if (horizDist < 0.001) return null;

            float pitch  = findPitch(horizDist, ty, arrowSpd);
            int   flight = simFlightTime(horizDist, pitch, arrowSpd);

            if (!predict.getValue()) break;

            tx = target.posX + tvx * flight - src.xCoord;
            tz = target.posZ + tvz * flight - src.zCoord;

            double predY = target.posY;
            double predTvy = tvy;
            for (int t = 0; t < flight; t++) {
                predTvy -= 0.08;
                predTvy *= 0.98;
                predY += predTvy;
            }
            ty = predY + target.getEyeHeight() * 0.5 - src.yCoord;
        }

        double horizDist = Math.sqrt(tx*tx + tz*tz);
        if (horizDist < 0.001) return null;

        float yaw   = (float) Math.toDegrees(Math.atan2(-tx, tz));
        float pitch = findPitch(horizDist, ty, arrowSpd);
        return new float[]{yaw, pitch};
    }

    private static double currentArrowSpeed() {
        int i = mc.thePlayer.getItemInUseDuration();
        float f = (float) i / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f * 2.0;
    }

    private static float findPitch(double horizDist, double dy, double speed) {
        float lo = -89f, hi = 89f;
        for (int i = 0; i < 64; i++) {
            float  mid = (lo + hi) / 2f;
            double hit = simulateArrowY(horizDist, mid, speed);
            if (Double.isNaN(hit) || hit < dy) hi = mid;
            else                               lo = mid;
        }
        return (lo + hi) / 2f;
    }

    private static double simulateArrowY(double horizDist, float pitchDeg, double speed) {
        double pitchRad = Math.toRadians(pitchDeg);
        double vH = Math.cos(pitchRad) * speed;
        double vY = -Math.sin(pitchRad) * speed;
        double posH = 0, posY = 0;
        for (int t = 0; t < 200; t++) {
            posH += vH; posY += vY;
            vH *= 0.99; vY *= 0.99; vY -= 0.05;
            if (posH >= horizDist) return posY;
        }
        return Double.NaN;
    }

    private static int simFlightTime(double horizDist, float pitchDeg, double speed) {
        double vH = Math.cos(Math.toRadians(pitchDeg)) * speed;
        double posH = 0;
        for (int t = 0; t < 100; t++) {
            posH += vH; vH *= 0.99;
            if (posH >= horizDist) return t + 1;
        }
        return 100;
    }

    private static Vec3 getEyes() {
        return new Vec3(mc.thePlayer.posX,
                        mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                        mc.thePlayer.posZ);
    }

    private static float wrapDiff(float d) {
        while (d >  180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    private void reset() {
        pullStart    = 0L;
        aimInit      = false;
        lockedTarget = null;
        mc.timer.timerSpeed = 1.0F;
    }

    private static void boxEdge(WorldRenderer buf,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 float r, float g, float b, float a) {
        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
    }
}
