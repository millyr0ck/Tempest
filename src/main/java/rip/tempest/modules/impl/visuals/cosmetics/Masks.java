package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;

final class Masks {
    private static final Color BANDANA = new Color(192, 32, 42);
    private static final Color MASK = new Color(30, 30, 34);

    private Masks() {
    }

    static void render(ModelPlayer model, AbstractClientPlayer player) {
        Tint tint = CosmeticsModule.maskColor.getValue();
        boolean bandana = CosmeticsModule.mask.getValue() == CosmeticsModule.Mask.Bandana;

        double s = 4.65 + Cosmetics.helmetOffset(player);

        GlStateManager.pushMatrix();
        model.bipedHead.postRender(0.0625F);
        Cosmetics.beginSolid();
        Cosmetics.color(Cosmetics.main(tint, bandana ? BANDANA : MASK));
        Shapes.begin();
        if (bandana) bandana(s);
        else mask(s);
        Shapes.end();
        Cosmetics.endSolid();
        GlStateManager.popMatrix();
    }

    private static void bandana(double s) {
        double top = -6.9, bottom = -5.3, t = 0.35;
        band(s, t, top, bottom);
        Shapes.box(-0.8, top - 0.1, s, 0.8, bottom + 0.1, s + 0.6);

        double flutter = Math.sin(System.currentTimeMillis() / 260.0) * 0.5;
        for (int side = -1; side <= 1; side += 2) {
            double[] from = {side * 0.4, -5.9, s + 0.5};
            double[] to = {side * (1.7 + flutter * side * 0.4), -2.4, s + 1.3 + flutter};
            Shapes.beam(from, to, 0.45, 0.3);
        }
    }

    private static void mask(double s) {
        double top = -3.9, bottom = -0.2, t = 0.35;
        Shapes.box(-s, top, -s - t, s, bottom, -s);
        Shapes.box(-s - t, top, -s, -s, bottom, -1.0);
        Shapes.box(s, top, -s, s + t, bottom, -1.0);
        double strapTop = top - 0.5, strapBottom = top + 0.3;
        Shapes.box(-s - t, strapTop, -1.0, -s, strapBottom, s);
        Shapes.box(s, strapTop, -1.0, s + t, strapBottom, s);
        Shapes.box(-s - t, strapTop, s, s + t, strapBottom, s + t);
    }

    private static void band(double s, double t, double top, double bottom) {
        Shapes.box(-s - t, top, -s - t, s + t, bottom, -s);
        Shapes.box(-s - t, top, s, s + t, bottom, s + t);
        Shapes.box(-s - t, top, -s, -s, bottom, s);
        Shapes.box(s, top, -s, s + t, bottom, s);
    }
}
