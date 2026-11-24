package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.util.Random;

final class Ears {
    private static final Color CAT = new Color(58, 56, 62), CAT_INNER = new Color(232, 158, 178);
    private static final Color FOX = new Color(228, 124, 44), FOX_INNER = new Color(248, 238, 222);
    private static final Color BUNNY = new Color(244, 244, 244), BUNNY_INNER = new Color(240, 170, 190);

    private static final double HEAD_TOP = -8.4;
    private static final long TWITCH_WINDOW = 3600;
    private static final long TWITCH_LENGTH = 260;

    private static float roll, rollVelocity, pitch, pitchVelocity;
    private static float shownRoll, shownRollPrev, shownPitch, shownPitchPrev;
    private static float lastYaw, lastPitch;
    private static double lastVertical;
    private static int lastTick = Integer.MIN_VALUE;

    private Ears() {
    }

    static void tick(EntityPlayer player) {
        int tick = player.ticksExisted;
        if (tick == lastTick) return;
        boolean fresh = lastTick == Integer.MIN_VALUE || tick < lastTick || tick - lastTick > 10;
        lastTick = tick;

        float yaw = player.rotationYawHead, headPitch = player.rotationPitch;
        double vertical = player.posY - player.prevPosY;
        if (fresh) {
            roll = rollVelocity = pitch = pitchVelocity = 0f;
            shownRoll = shownRollPrev = shownPitch = shownPitchPrev = 0f;
            lastYaw = yaw;
            lastPitch = headPitch;
            lastVertical = vertical;
            return;
        }

        boolean floppy = CosmeticsModule.ears.getValue() == CosmeticsModule.Ears.Bunny;
        float stiffness = floppy ? 0.12f : 0.35f, damping = floppy ? 0.82f : 0.6f;
        float turnPull = floppy ? 0.35f : 0.12f, limit = floppy ? 35f : 14f;

        float yawRate = MathHelper.wrapAngleTo180_float(yaw - lastYaw);
        float pitchRate = headPitch - lastPitch;

        float lift = (float) (vertical - lastVertical);
        if (Math.abs(lift) < 0.12f) lift = 0f;
        double speed = Math.hypot(player.posX - player.prevPosX, player.posZ - player.prevPosZ);
        lastYaw = yaw;
        lastPitch = headPitch;
        lastVertical = vertical;

        float restPitch = floppy ? (float) -Math.min(30.0, speed * 110.0) : 0f;

        rollVelocity = rollVelocity * damping - roll * stiffness + yawRate * turnPull;

        pitchVelocity = pitchVelocity * damping + (restPitch - pitch) * stiffness
                - pitchRate * turnPull - lift * (floppy ? 60f : 18f);
        roll = MathHelper.clamp_float(roll + rollVelocity, -limit, limit);
        pitch = MathHelper.clamp_float(pitch + pitchVelocity, -limit, limit);

        shownRollPrev = shownRoll;
        shownPitchPrev = shownPitch;
        shownRoll = roll;
        shownPitch = pitch;
    }

    static void render(ModelPlayer model, AbstractClientPlayer player, float partialTicks) {
        CosmeticsModule.Ears type = CosmeticsModule.ears.getValue();
        Tint tint = CosmeticsModule.earColor.getValue();
        Color natural = type == CosmeticsModule.Ears.Cat ? CAT : type == CosmeticsModule.Ears.Fox ? FOX : BUNNY;
        Color naturalInner = type == CosmeticsModule.Ears.Cat ? CAT_INNER : type == CosmeticsModule.Ears.Fox ? FOX_INNER : BUNNY_INNER;
        Color main = Cosmetics.main(tint, natural);
        Color inner = Cosmetics.accent(tint, naturalInner, main);
        double base = HEAD_TOP - Cosmetics.helmetOffset(player);

        float swayRoll = shownRollPrev + (shownRoll - shownRollPrev) * partialTicks;
        float swayPitch = shownPitchPrev + (shownPitch - shownPitchPrev) * partialTicks;

        GlStateManager.pushMatrix();
        model.bipedHead.postRender(0.0625F);
        Cosmetics.beginSolid();
        for (int side = -1; side <= 1; side += 2) {
            double pivotX = side * (type == CosmeticsModule.Ears.Bunny ? 1.9 : 2.6);

            float lean = side * ((type == CosmeticsModule.Ears.Bunny ? 10f : 8f) + twitch(side)) + swayRoll;
            GlStateManager.pushMatrix();
            GlStateManager.translate(pivotX / 16.0, base / 16.0, 0);
            GlStateManager.rotate(lean, 0, 0, 1);
            GlStateManager.rotate(swayPitch, 1, 0, 0);
            GlStateManager.translate(-pivotX / 16.0, -base / 16.0, 0);
            drawEar(type, side, base, main, inner);
            GlStateManager.popMatrix();
        }
        Cosmetics.endSolid();
        GlStateManager.popMatrix();
    }

    private static void drawEar(CosmeticsModule.Ears type, int side, double base, Color main, Color inner) {
        switch (type) {
            case Cat:
                triangleEar(side, base, 1.0, 4.0, 3.4, main);
                innerTriangle(side, base, 1.6, 3.4, 2.4, inner);
                break;
            case Fox:
                triangleEar(side, base, 0.6, 4.3, 4.4, main);
                innerTriangle(side, base, 1.3, 3.6, 3.2, inner);
                break;
            default:
                Cosmetics.color(main);
                Shapes.begin();
                Shapes.box(side * 1.0, base - 6.8, -0.4, side * 2.8, base, 0.4);
                Shapes.end();
                Cosmetics.color(inner);
                Shapes.begin();
                Shapes.box(side * 1.4, base - 6.0, -0.52, side * 2.4, base - 0.6, -0.4);
                Shapes.end();
                break;
        }
    }

    private static void triangleEar(int side, double base, double from, double to, double height, Color color) {
        Cosmetics.color(color);
        Shapes.begin();
        Shapes.prism(new double[]{side * from, base}, new double[]{side * to, base},
                new double[]{side * (from + to) * 0.55, base - height}, -0.6, 0.6);
        Shapes.end();
    }

    private static void innerTriangle(int side, double base, double from, double to, double height, Color color) {
        Cosmetics.color(color);
        Shapes.begin();
        Shapes.prism(new double[]{side * from, base - 0.2}, new double[]{side * to, base - 0.2},
                new double[]{side * (from + to) * 0.55, base - height}, -0.72, -0.6);
        Shapes.end();
    }

    private static float twitch(int side) {
        long now = System.currentTimeMillis();
        long window = now / TWITCH_WINDOW;
        Random random = new Random(window * 31L + side * 17L);
        if (random.nextFloat() > 0.55f) return 0f;
        long start = (long) (random.nextFloat() * (TWITCH_WINDOW - TWITCH_LENGTH));
        long t = now % TWITCH_WINDOW - start;
        if (t < 0 || t > TWITCH_LENGTH) return 0f;
        return (float) Math.sin(t / (double) TWITCH_LENGTH * Math.PI) * 22f;
    }
}
