package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import rip.tempest.modules.impl.visuals.CosmeticsModule.Tint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import static rip.tempest.utils.Util.mc;

final class DragonWings {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/enderdragon/dragon.png");
    private static final WingModel MODEL = new WingModel();

    private static final float BASE_SCALE = 0.1f;

    private static float phase;
    private static float flapRate = 0.5f;
    private static float fold;
    private static long lastFrame;

    private DragonWings() {
    }

    static void render(ModelPlayer model, AbstractClientPlayer player) {
        animate(player);
        float amplitude = 1f - fold * 0.8f;

        GlStateManager.pushMatrix();
        model.bipedBody.postRender(0.0625F);

        GlStateManager.translate(0F, 0.16F, 0.16F);
        float scale = BASE_SCALE * CosmeticsModule.wingScale.getValue().floatValue();
        GlStateManager.scale(scale, scale, scale);

        Tint tint = CosmeticsModule.wingColor.getValue();
        if (tint == Tint.Natural) {
            mc.getTextureManager().bindTexture(TEXTURE);
            GlStateManager.color(1f, 1f, 1f, 1f);
        } else {
            Cosmetics.beginSolid();
            Cosmetics.color(Cosmetics.main(tint, Cosmetics.WHITE));
        }

        GlStateManager.enableCull();
        for (int side = 0; side < 2; side++) {
            MODEL.pose(phase, fold, amplitude);
            MODEL.wing.render(0.0625F);
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            if (side == 0) GlStateManager.cullFace(GL11.GL_FRONT);
        }
        GlStateManager.cullFace(GL11.GL_BACK);
        GlStateManager.disableCull();

        if (tint != Tint.Natural) Cosmetics.endSolid();
        GlStateManager.popMatrix();
    }

    private static void animate(AbstractClientPlayer player) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min((now - lastFrame) / 1.0E9f, 0.1f);
        lastFrame = now;

        double dx = player.posX - player.prevPosX, dz = player.posZ - player.prevPosZ;
        float moving = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 4.0);
        boolean crouching = player.isSneaking();
        float speed = CosmeticsModule.flapSpeed.getValue().floatValue();

        float target = crouching ? 0.25f : 0.45f + moving * 0.9f + (player.onGround ? 0f : 0.9f);
        flapRate += (target * speed - flapRate) * Math.min(1f, dt * 3f);
        phase = (float) ((phase + dt * Math.PI * 2 * flapRate) % (Math.PI * 2));
        fold += ((crouching ? 1f : 0f) - fold) * Math.min(1f, dt * 6f);
    }

    private static final class WingModel extends ModelBase {
        final ModelRenderer wing;
        final ModelRenderer wingTip;

        WingModel() {
            textureWidth = 256;
            textureHeight = 256;
            setTextureOffset("wing.skin", -56, 88);
            setTextureOffset("wingtip.skin", -56, 144);
            setTextureOffset("wing.bone", 112, 88);
            setTextureOffset("wingtip.bone", 112, 136);

            wing = new ModelRenderer(this, "wing");
            wing.setRotationPoint(-12.0F, 0.0F, 0.0F);
            wing.addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8);
            wing.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);
            wingTip = new ModelRenderer(this, "wingtip");
            wingTip.setRotationPoint(-56.0F, 0.0F, 0.0F);
            wingTip.addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4);
            wingTip.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);
            wing.addChild(wingTip);
        }

        void pose(float phase, float fold, float amplitude) {
            wing.rotateAngleX = -0.95F + 0.2F * fold - (float) Math.cos(phase) * 0.12F * amplitude;
            wing.rotateAngleY = 0.35F + 0.7F * fold;
            wing.rotateAngleZ = (0.3F + (float) Math.sin(phase) * 0.45F * amplitude) * (1F - 0.7F * fold);
            wingTip.rotateAngleZ = -((float) Math.sin(phase + 2.0F) + 0.5F) * 0.6F * amplitude - 0.4F * fold;
        }
    }
}
