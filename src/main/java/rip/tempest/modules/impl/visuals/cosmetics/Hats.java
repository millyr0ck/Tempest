package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;

final class Hats {
    private static final Color WITCH = new Color(46, 28, 62), WITCH_BAND = new Color(150, 90, 205), BUCKLE = new Color(235, 195, 70);
    private static final Color SANTA = new Color(200, 32, 42), FUR = new Color(246, 246, 246);
    private static final Color TOP_HAT = new Color(30, 30, 34), RIBBON = new Color(170, 30, 42);
    private static final Color GOLD = new Color(240, 196, 64);
    private static final Color[] GEMS = {new Color(220, 40, 60), new Color(60, 110, 230), new Color(60, 200, 110)};

    private static final double HEAD_TOP = -8.6;
    private static final int SEGMENTS = 24;

    private Hats() {
    }

    static void render(ModelPlayer model, AbstractClientPlayer player) {
        Tint tint = CosmeticsModule.hatColor.getValue();
        double base = HEAD_TOP - Cosmetics.helmetOffset(player);

        GlStateManager.pushMatrix();
        model.bipedHead.postRender(0.0625F);
        Cosmetics.beginSolid();
        switch (CosmeticsModule.hat.getValue()) {
            case Witch:
                witch(tint, base);
                break;
            case Santa:
                santa(tint, base);
                break;
            case TopHat:
                topHat(tint, base);
                break;
            case Crown:
                crown(tint, base);
                break;
            default:
                break;
        }
        Cosmetics.endSolid();
        GlStateManager.popMatrix();
    }

    private static void witch(Tint tint, double base) {
        Color main = Cosmetics.main(tint, WITCH);
        Cosmetics.color(main);
        Shapes.begin();
        Shapes.cylinder(0, base - 0.5, 0, 7.6, 0.5, SEGMENTS);
        Shapes.cone(0, base - 0.5, 0, 4.6, 10.5, 0, 2.8, SEGMENTS);
        Shapes.end();

        Cosmetics.color(Cosmetics.accent(tint, WITCH_BAND, main));
        Shapes.begin();
        Shapes.cylinder(0, base - 1.9, 0, 4.75, 1.4, SEGMENTS);
        Shapes.end();

        Cosmetics.color(tint == Tint.Natural ? BUCKLE : Cosmetics.WHITE);
        Shapes.begin();
        Shapes.box(-1.1, base - 2.0, -5.0, 1.1, base - 0.4, -4.7);
        Shapes.end();
    }

    private static void santa(Tint tint, double base) {
        Color main = Cosmetics.main(tint, SANTA);
        Color trim = tint == Tint.Natural ? FUR : Cosmetics.accent(tint, FUR, main);
        double tipX = 3.4, tipZ = 2.6, height = 8.5, coneBase = base - 2.0;

        Cosmetics.color(main);
        Shapes.begin();
        Shapes.cone(0, coneBase, 0, 4.5, height, tipX, tipZ, SEGMENTS);
        Shapes.end();

        Cosmetics.color(trim);
        Shapes.begin();
        Shapes.tube(0, base - 2.0, 0, 3.9, 5.1, 2.1, SEGMENTS);
        Shapes.octa(tipX, coneBase - height, tipZ, 1.5);
        Shapes.end();
    }

    private static void topHat(Tint tint, double base) {
        Color main = Cosmetics.main(tint, TOP_HAT);
        Cosmetics.color(main);
        Shapes.begin();
        Shapes.cylinder(0, base - 0.6, 0, 6.2, 0.6, SEGMENTS);
        Shapes.cylinder(0, base - 7.6, 0, 4.3, 7.0, SEGMENTS);
        Shapes.end();

        Cosmetics.color(tint == Tint.Natural ? RIBBON : Cosmetics.accent(tint, RIBBON, main));
        Shapes.begin();
        Shapes.cylinder(0, base - 2.2, 0, 4.42, 1.5, SEGMENTS);
        Shapes.end();
    }

    private static void crown(Tint tint, double base) {
        Color main = Cosmetics.main(tint, GOLD);
        double bandTop = base - 2.6;
        Cosmetics.color(main);
        Shapes.begin();
        Shapes.tube(0, bandTop, 0, 4.0, 4.7, 2.6, SEGMENTS);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * (i + 0.5) / 8;
            Shapes.cone(Math.cos(angle) * 4.35, bandTop, Math.sin(angle) * 4.35, 0.9, 2.3, 0, 0, 6);
        }
        Shapes.end();

        for (int i = 0; i < GEMS.length; i++) {
            double angle = Math.toRadians(-90 + (i - 1) * 35);
            Cosmetics.color(tint == Tint.Natural ? GEMS[i] : Cosmetics.accent(tint, GEMS[i], main));
            Shapes.begin();
            Shapes.octa(Math.cos(angle) * 4.85, bandTop + 1.3, Math.sin(angle) * 4.85, 0.75);
            Shapes.end();
        }
    }
}
