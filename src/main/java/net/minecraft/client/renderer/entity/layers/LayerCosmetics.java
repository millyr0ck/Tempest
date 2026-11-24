package net.minecraft.client.renderer.entity.layers;

import rip.tempest.Tempest;
import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.cosmetics.Cosmetics;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;

import static rip.tempest.utils.Util.mc;

/** Tempest's cosmetics (wings, ears, tail, hat, face, back item, aura) on your own player. */
public class LayerCosmetics implements LayerRenderer<AbstractClientPlayer> {
    private final RenderPlayer playerRenderer;

    public LayerCosmetics(RenderPlayer playerRendererIn) {
        this.playerRenderer = playerRendererIn;
    }

    public void doRenderLayer(AbstractClientPlayer entitylivingbaseIn, float p_177141_2_, float p_177141_3_, float partialTicks, float p_177141_5_, float p_177141_6_, float p_177141_7_, float scale) {
        if (entitylivingbaseIn != mc.thePlayer || entitylivingbaseIn.isInvisible()) return;
        if (!Tempest.INSTANCE.getModuleManager().getModule(CosmeticsModule.class).isEnabled()) return;
        Cosmetics.render(this.playerRenderer.getMainModel(), entitylivingbaseIn, partialTicks);
    }

    public boolean shouldCombineTextures() {
        return false;
    }
}
