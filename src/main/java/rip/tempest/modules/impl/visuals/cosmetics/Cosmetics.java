package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import rip.tempest.processes.ColorProcess;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

import java.awt.Color;

public final class Cosmetics {
    static final Color WHITE = new Color(236, 236, 236);

    private Cosmetics() {
    }

    public static void render(ModelPlayer model, AbstractClientPlayer player, float partialTicks) {
        if (CosmeticsModule.tail.getValue() != CosmeticsModule.Tail.Off) Tails.render(player, partialTicks);

        GlStateManager.pushMatrix();

        if (player.isSneaking()) GlStateManager.translate(0.0F, 0.2F, 0.0F);
        if (CosmeticsModule.wings.getValue()) DragonWings.render(model, player);
        if (CosmeticsModule.ears.getValue() != CosmeticsModule.Ears.Off) Ears.render(model, player, partialTicks);
        if (CosmeticsModule.hat.getValue() != CosmeticsModule.Hat.Off) Hats.render(model, player);
        if (CosmeticsModule.mask.getValue() != CosmeticsModule.Mask.Off) Masks.render(model, player);
        if (CosmeticsModule.backItem.getValue() != CosmeticsModule.BackItem.Off) BackItems.render(model, player);
        GlStateManager.popMatrix();

        if (CosmeticsModule.aura.getValue()) Aura.render();
    }

    public static void tick(EntityPlayer player) {
        if (CosmeticsModule.tail.getValue() != CosmeticsModule.Tail.Off) Tails.tick(player);
        if (CosmeticsModule.ears.getValue() != CosmeticsModule.Ears.Off) Ears.tick(player);
    }

    static Color main(Tint tint, Color natural) {
        switch (tint) {
            case White:
                return WHITE;
            case Client:
                return ColorProcess.getColor();
            default:
                return natural;
        }
    }

    static Color accent(Tint tint, Color natural, Color main) {
        return tint == Tint.Natural ? natural : blend(main, Color.WHITE, 0.45f);
    }

    static Color blend(Color from, Color to, float amount) {
        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    static void color(Color color) {
        GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f);
    }

    static void beginSolid() {
        GlStateManager.disableTexture2D();
    }

    static void endSolid() {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    static double helmetOffset(AbstractClientPlayer player) {
        return player.getCurrentArmor(3) != null ? 1.0 : 0.0;
    }
}
