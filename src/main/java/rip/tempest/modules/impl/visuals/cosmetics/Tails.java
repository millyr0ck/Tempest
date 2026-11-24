package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

final class Tails {
    private static final Color CAT = new Color(58, 56, 62);
    private static final Color DRAGON = new Color(38, 36, 44), DRAGON_SPIKES = new Color(150, 70, 205);

    private static final TailPhysics CAT_TAIL = new TailPhysics(catProfile());
    private static final TailPhysics DRAGON_TAIL = new TailPhysics(dragonProfile());

    private Tails() {
    }

    private static TailPhysics.Profile catProfile() {
        TailPhysics.Profile p = new TailPhysics.Profile();
        p.points = 10;
        p.segmentPx = 1.5;
        p.rootStiffness = 0.5;
        p.tipStiffness = 0.14;
        p.drag = 0.8;
        p.gravity = 0.012;

        p.baseAngle = -30;
        p.bend = 15;
        p.crouchAngle = -55;
        p.crouchBend = 7;
        p.runDrop = 18;
        p.idleSway = 28;
        p.runSway = 9;
        p.swayPeriod = 3.2;
        p.flick = 7;
        return p;
    }

    private static TailPhysics.Profile dragonProfile() {
        TailPhysics.Profile p = new TailPhysics.Profile();
        p.points = 10;
        p.segmentPx = 2.4;
        p.rootStiffness = 0.38;
        p.tipStiffness = 0.05;
        p.drag = 0.78;
        p.gravity = 0.03;

        p.baseAngle = -38;
        p.bend = 4.5;
        p.crouchAngle = -50;
        p.crouchBend = 3;
        p.runDrop = 6;
        p.idleSway = 12;
        p.runSway = 6;
        p.swayPeriod = 4.5;
        p.flick = 3;
        return p;
    }

    static void tick(EntityPlayer player) {
        boolean dragon = CosmeticsModule.tail.getValue() == CosmeticsModule.Tail.Dragon;
        (dragon ? DRAGON_TAIL : CAT_TAIL).step(player);
        (dragon ? CAT_TAIL : DRAGON_TAIL).reset();
    }

    static void render(AbstractClientPlayer player, float partialTicks) {
        boolean dragon = CosmeticsModule.tail.getValue() == CosmeticsModule.Tail.Dragon;
        TailPhysics tail = dragon ? DRAGON_TAIL : CAT_TAIL;
        int count = tail.count();

        if (!tail.ready()) return;

        double[][] points = new double[count][];
        double[] radii = new double[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) (count - 1);
            points[i] = tail.point(i, partialTicks);
            radii[i] = dragon ? lerp(0.95, 0.2, t) : lerp(0.6, 0.38, t);
        }

        Tint tint = CosmeticsModule.tailColor.getValue();
        Color main = Cosmetics.main(tint, dragon ? DRAGON : CAT);

        Cosmetics.beginSolid();
        Cosmetics.color(main);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Shapes.begin();
        Shapes.tube(points, radii, 8);
        Shapes.end();
        GlStateManager.shadeModel(GL11.GL_FLAT);

        if (dragon) {
            Cosmetics.color(Cosmetics.accent(tint, DRAGON_SPIKES, main));
            Shapes.begin();
            for (int i = 1; i < count - 1; i += 2) {
                drawSpike(points[i], points[i + 1], lerp(1.5, 0.6, i / (double) (count - 1)));
            }
            Shapes.end();
        }
        Cosmetics.endSolid();
    }

    private static void drawSpike(double[] a, double[] b, double height) {
        double[] along = Shapes.sub(b, a);
        double[] side = Shapes.norm(Shapes.cross(along, new double[]{0, -1, 0}));
        double[] up = Shapes.norm(Shapes.cross(side, along));
        if (up[1] > 0) up = Shapes.scale(up, -1);
        double[] mid = Shapes.scale(Shapes.add(a, b), 0.5);
        double[] apex = Shapes.add(mid, Shapes.scale(up, height));
        double[] centre = Shapes.add(mid, side);
        Shapes.tri(a, b, apex, centre);
        Shapes.tri(a, b, apex, Shapes.sub(mid, side));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
