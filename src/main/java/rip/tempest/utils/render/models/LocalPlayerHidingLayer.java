package rip.tempest.utils.render.models;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;

import static rip.tempest.utils.Util.mc;

public class LocalPlayerHidingLayer implements LayerRenderer<AbstractClientPlayer> {
    private final LayerRenderer<AbstractClientPlayer> delegate;

    public LocalPlayerHidingLayer(LayerRenderer<AbstractClientPlayer> delegate) {
        this.delegate = delegate;
    }

    public LayerRenderer<AbstractClientPlayer> getDelegate() {
        return delegate;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (entity == mc.thePlayer) return;
        delegate.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
    }

    @Override
    public boolean shouldCombineTextures() {
        return delegate.shouldCombineTextures();
    }
}
