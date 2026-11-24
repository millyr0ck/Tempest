package net.minecraft.client.renderer;

import rip.tempest.processes.SlotProcess;
import rip.tempest.Tempest;
import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.modules.impl.visuals.AnimationsModule;
import rip.tempest.utils.render.SwordTrail;
import rip.tempest.utils.mc.InventoryUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.src.Config;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.MapData;
import net.optifine.DynamicLights;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.GL11;

public class ItemRenderer {
    private static final ResourceLocation RES_MAP_BACKGROUND = new ResourceLocation("textures/map/map_background.png");
    private static final ResourceLocation RES_UNDERWATER_OVERLAY = new ResourceLocation("textures/misc/underwater.png");
    private final Minecraft mc;
    private ItemStack itemToRender;
    private float equippedProgress;
    private float prevEquippedProgress;
    /** Plays the re-equip dip once more for the item already held (Animations' Hit Bob). */
    private boolean replayEquip;
    private final RenderManager renderManager;
    private final RenderItem itemRenderer;
    private int equippedItemSlot = -1;


    public ItemRenderer(Minecraft mcIn) {
        this.mc = mcIn;
        this.renderManager = mcIn.getRenderManager();
        this.itemRenderer = mcIn.getRenderItem();
    }

    public void renderItem(EntityLivingBase entityIn, ItemStack heldStack, ItemCameraTransforms.TransformType transform) {
        if (heldStack != null) {
            Item item = heldStack.getItem();
            Block block = Block.getBlockFromItem(item);
            GlStateManager.pushMatrix();

            if (this.itemRenderer.shouldRenderItemIn3D(heldStack)) {
                GlStateManager.scale(2.0F, 2.0F, 2.0F);

                if (this.isBlockTranslucent(block) && (!Config.isShaders() || !Shaders.renderItemKeepDepthMask)) {
                    GlStateManager.depthMask(false);
                }
            }

            this.itemRenderer.renderItemModelForEntity(heldStack, entityIn, transform);

            if (this.isBlockTranslucent(block)) {
                GlStateManager.depthMask(true);
            }

            GlStateManager.popMatrix();
        }
    }

    private boolean isBlockTranslucent(Block blockIn) {
        return blockIn != null && blockIn.getBlockLayer() == EnumWorldBlockLayer.TRANSLUCENT;
    }

    private void rotateArroundXAndY(float angle, float angleY) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(angle, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(angleY, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private void setLightMapFromPlayer(AbstractClientPlayer clientPlayer) {
        int i = this.mc.theWorld.getCombinedLight(new BlockPos(clientPlayer.posX, clientPlayer.posY + (double) clientPlayer.getEyeHeight(), clientPlayer.posZ), 0);

        if (Config.isDynamicLights()) {
            i = DynamicLights.getCombinedLight(this.mc.getRenderViewEntity(), i);
        }

        float f = (float) (i & 65535);
        float f1 = (float) (i >> 16);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, f, f1);
    }

    private void rotateWithPlayerRotations(EntityPlayerSP entityplayerspIn, float partialTicks) {
        float f = entityplayerspIn.prevRenderArmPitch + (entityplayerspIn.renderArmPitch - entityplayerspIn.prevRenderArmPitch) * partialTicks;
        float f1 = entityplayerspIn.prevRenderArmYaw + (entityplayerspIn.renderArmYaw - entityplayerspIn.prevRenderArmYaw) * partialTicks;
        GlStateManager.rotate((entityplayerspIn.rotationPitch - f) * 0.1F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate((entityplayerspIn.rotationYaw - f1) * 0.1F, 0.0F, 1.0F, 0.0F);
    }

    private float getMapAngleFromPitch(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = MathHelper.clamp_float(f, 0.0F, 1.0F);
        f = -MathHelper.cos(f * (float) Math.PI) * 0.5F + 0.5F;
        return f;
    }

    private void renderRightArm(RenderPlayer renderPlayerIn) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(54.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(64.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-62.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(0.25F, -0.85F, 0.75F);
        renderPlayerIn.renderRightArm(this.mc.thePlayer);
        GlStateManager.popMatrix();
    }

    private void renderLeftArm(RenderPlayer renderPlayerIn) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(92.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(45.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(41.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-0.3F, -1.1F, 0.45F);
        renderPlayerIn.renderLeftArm(this.mc.thePlayer);
        GlStateManager.popMatrix();
    }

    private void renderPlayerArms(AbstractClientPlayer clientPlayer) {
        this.mc.getTextureManager().bindTexture(clientPlayer.getLocationSkin());
        Render<AbstractClientPlayer> render = this.renderManager.getEntityRenderObject(this.mc.thePlayer);
        RenderPlayer renderplayer = (RenderPlayer) render;

        if (!clientPlayer.isInvisible()) {
            GlStateManager.disableCull();
            this.renderRightArm(renderplayer);
            this.renderLeftArm(renderplayer);
            GlStateManager.enableCull();
        }
    }

    private void renderItemMap(AbstractClientPlayer clientPlayer, float pitch, float equipmentProgress, float swingProgress) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
        float f3 = this.getMapAngleFromPitch(pitch);
        GlStateManager.translate(0.0F, 0.04F, -0.72F);
        GlStateManager.translate(0.0F, equipmentProgress * -1.2F, 0.0F);
        GlStateManager.translate(0.0F, f3 * -0.5F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * -85.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(0.0F, 1.0F, 0.0F, 0.0F);
        this.renderPlayerArms(clientPlayer);
        float f4 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f5 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f4 * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f5 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f5 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.38F, 0.38F, 0.38F);
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(0.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(-1.0F, -1.0F, 0.0F);
        GlStateManager.scale(0.015625F, 0.015625F, 0.015625F);
        this.mc.getTextureManager().bindTexture(RES_MAP_BACKGROUND);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GL11.glNormal3f(0.0F, 0.0F, -1.0F);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(-7.0D, 135.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        worldrenderer.pos(135.0D, 135.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        worldrenderer.pos(135.0D, -7.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        worldrenderer.pos(-7.0D, -7.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
        MapData mapdata = Items.filled_map.getMapData(this.itemToRender, this.mc.theWorld);

        if (mapdata != null) {
            this.mc.entityRenderer.getMapItemRenderer().renderMap(mapdata, false);
        }
    }

    public void swing() {
        if (mc.thePlayer.getItemInUseCount() > 0) {
            if (mc.objectMouseOver != null) {
                mc.thePlayer.swingItemClient();
            }
        }
    }

    private void renderPlayerArm(AbstractClientPlayer clientPlayer, float equipProgress, float swingProgress) {
        float f = -0.3F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float f1 = 0.4F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.4F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
        GlStateManager.translate(0.64000005F, -0.6F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f3 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f4 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f4 * 70.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * -20.0F, 0.0F, 0.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(clientPlayer.getLocationSkin());
        GlStateManager.translate(-1.0F, 3.6F, 3.5F);
        GlStateManager.rotate(120.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(200.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(1.0F, 1.0F, 1.0F);
        GlStateManager.translate(5.6F, 0.0F, 0.0F);
        Render<AbstractClientPlayer> render = this.renderManager.getEntityRenderObject(this.mc.thePlayer);
        GlStateManager.disableCull();
        RenderPlayer renderplayer = (RenderPlayer) render;
        renderplayer.renderRightArm(this.mc.thePlayer);
        GlStateManager.enableCull();
    }

    private void doItemUsedTransformations(float swingProgress) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
    }

    private void performDrinking(AbstractClientPlayer clientPlayer, float partialTicks) {
        float f = (float) clientPlayer.getItemInUseCount() - partialTicks + 1.0F;
        float f1 = f / (float) this.itemToRender.getMaxItemUseDuration();
        float f2 = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);

        if (f1 >= 0.8F) {
            f2 = 0.0F;
        }

        GlStateManager.translate(0.0F, f2, 0.0F);
        float f3 = 1.0F - (float) Math.pow(f1, 27.0D);
        GlStateManager.translate(f3 * 0.6F, f3 * -0.5F, f3 * 0.0F);
        GlStateManager.rotate(f3 * 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * 10.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f3 * 30.0F, 0.0F, 0.0F, 1.0F);
    }

    private void sigmaBase(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.0F, 0.1F, 0.0F);
        this.transformFirstPersonItem(equipProgress, swingProgress);
    }

    private void transformFirstPersonItem(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    private void doBowTransformations(float partialTicks, AbstractClientPlayer clientPlayer) {
        GlStateManager.rotate(-18.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-12.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-8.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(-0.9F, 0.2F, 0.0F);
        float f = (float) this.itemToRender.getMaxItemUseDuration() - ((float) clientPlayer.getItemInUseCount() - partialTicks + 1.0F);
        float f1 = f / 20.0F;
        f1 = (f1 * f1 + f1 * 2.0F) / 3.0F;

        if (f1 > 1.0F) {
            f1 = 1.0F;
        }

        if (f1 > 0.1F) {
            float f2 = MathHelper.sin((f - 0.1F) * 1.3F);
            float f3 = f1 - 0.1F;
            float f4 = f2 * f3;
            GlStateManager.translate(f4 * 0.0F, f4 * 0.01F, f4 * 0.0F);
        }

        GlStateManager.translate(f1 * 0.0F, f1 * 0.0F, f1 * 0.1F);
        GlStateManager.scale(1.0F, 1.0F, 1.0F + f1 * 0.2F);
    }

    /** The display transform the held item is drawn with this frame; see riseBlockTransform. */
    private ItemCameraTransforms.TransformType heldItemTransform = ItemCameraTransforms.TransformType.FIRST_PERSON;

    /**
     * Rise 6's block animations. {@code f} is the equip progress and {@code f1} the swing. Its
     * leaked build swings them with sin(sqrt(swing) * 2 pi), a stand-in for the pi its login server
     * sends; here it is pi, as in every other block animation.
     */
    private void riseBlockTransform(AnimationsModule.AnimationMode mode, float f, float f1) {
        float swing = MathHelper.sin(MathHelper.sqrt_float(f1) * (float) Math.PI);
        float scale = AnimationsModule.scale.getValue().floatValue();

        switch (mode) {
            case SevenAccurate -> {
                this.transformFirstPersonItem(f, f1);
                GlStateManager.scale(scale, scale, scale);
                this.doBlockTransformations();
                this.heldItemTransform = this.classicItemPose(this.itemToRender)
                        ? ItemCameraTransforms.TransformType.FIRST_PERSON : ItemCameraTransforms.TransformType.NONE;
            }
            case Lucid -> {
                this.transformFirstPersonItem(f - 0.1F, f1);
                GlStateManager.scale(scale, scale, scale);
                this.doBlockTransformations();
            }
            case Astro -> {
                GlStateManager.translate(0.0F, 0.03F, -0.05F);
                this.transformFirstPersonItem(f / 2.0F, f1);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(swing * 30.0F / 2.0F, -swing, -0.0F, 9.0F);
                GlStateManager.rotate(swing * 40.0F, 1.0F, -swing / 2.0F, -0.0F);
                this.doBlockTransformations();
            }
            case ExhibitionNew -> {
                GlStateManager.translate(0.0F, -0.04F, -0.01F);
                this.transformFirstPersonItem(f / 2.0F, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.translate(0.0F, 0.3F, -0.0F);
                GlStateManager.rotate(-swing * 30.0F, 1.0F, 0.0F, 2.0F);
                GlStateManager.rotate(-swing * 44.0F, 1.5F, swing / 1.2F, 0.0F);
                this.doBlockTransformations();
            }
            case Swong -> {
                GlStateManager.translate(0.0F, 0.1F, -0.05F);
                this.transformFirstPersonItem(f / 2.0F, f1);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(swing * 30.0F, -swing, -0.0F, 9.0F);
                GlStateManager.rotate(swing * 40.0F, 1.0F, -swing, -0.0F);
                this.doBlockTransformations();
            }
            case Question -> {
                this.transformFirstPersonItem(f, f1);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.translate(-0.35F, 0.1F, 0.0F);
                GlStateManager.translate(-0.05F, -0.1F, 0.1F);
                this.doBlockTransformations();
            }
            case Dortware -> {
                float late = MathHelper.sin((float) (f1 * f1 * Math.PI - 3.0));
                this.transformFirstPersonItem(f, 1.0F);
                GlStateManager.rotate(-swing * 10.0F, 0.0F, 15.0F, 200.0F);
                GlStateManager.rotate(-swing * 10.0F, 300.0F, swing / 2.0F, 1.0F);
                this.doBlockTransformations();
                GlStateManager.translate(2.4F, 0.3F, 0.5F);
                GlStateManager.translate(-2.1F, -0.2F, 0.1F);
                GlStateManager.rotate(late * 13.0F, -10.0F, -1.4F, -10.0F);
            }
            default -> {
            }
        }
    }

    /**
     * Radium's block animations: 1.8's block pose with its own tilts on the swing, and the equip
     * dip halved, as Radium had it by default.
     */
    /**
     * OpenAstolfo's block-hit animations (its ItemRenderer, BLOCK case), call for call. {@code f} is
     * the equip progress and {@code f1} the swing progress, as there. Tempest's Scale goes on right
     * after the item's base transform, like the other ported styles; Astolfo's X/Y/Z offset is
     * Tempest's own X/Y/Z, which is already applied.
     */
    private void astolfoBlockTransform(AnimationsModule.AnimationMode mode, float f, float f1) {
        float scale = AnimationsModule.scale.getValue().floatValue();
        float f4 = MathHelper.sin((float) (MathHelper.sqrt_float(f1) * Math.PI));
        switch (mode) {
            case AstolfoDefault -> {
                this.transformFirstPersonItem(f, f1);
                GlStateManager.scale(scale, scale, scale);
                this.doBlockTransformations();
            }
            case AstolfoOld -> {
                GlStateManager.translate(0.08F, -0.027F, -0.33F);
                this.transformFirstPersonItem(f, f1);
                GlStateManager.scale(scale, scale, scale);
                this.doBlockTransformations();
            }
            case AstolfoSpin -> {
                GlStateManager.rotate(astolfoSpinAngle(), 0.0F, 1.0F, 5.0F);
                this.transformFirstPersonItem(f, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                this.doBlockTransformations();
            }
            case Spinny -> {
                this.transformFirstPersonItem(f, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.translate(0.5F, 0.1F, 0.0F);
                GlStateManager.rotate(10.0F, -2.0F, 1.0F, 0.0F);
                GlStateManager.rotate(astolfoSpinAngle(), 0.0F, 1.0F, 0.0F);
            }
            case Dash -> {
                this.transformFirstPersonItem(f / 2.0F, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(-f4 * 30.0F, 9.0F, -0.2F, 9.0F);
                GlStateManager.rotate(-f4 * 45.0F / 2.0F, f4, -0.0F, 9.0F);
                GlStateManager.rotate(-f4 * 30.0F, f4, f4 / 2.0F, -0.0F);
                this.doBlockTransformations();
            }
            default -> {
            }
        }
    }

    /** Astolfo's spin angle: Astolfo Spin degrees a frame at 60 fps, wrapped at 360. */
    private static float astolfoSpinAngle() {
        double perSecond = AnimationsModule.astolfoSpin.getValue() * 60.0D;
        return (float) ((System.currentTimeMillis() / 1000.0D * perSecond) % 360.0D);
    }

    private void radiumBlockTransform(AnimationsModule.AnimationMode mode, float f, float f1) {
        float swing = MathHelper.sin(MathHelper.sqrt_float(f1) * (float) Math.PI);
        float equip = f / 2.0F;
        float scale = AnimationsModule.scale.getValue().floatValue();

        switch (mode) {
            case Lol -> {
                this.transformFirstPersonItem(equip, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(-swing * 30.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(-swing * 10.0F, 0.0F, 1.0F, 0.0F);
            }
            case Lul -> {
                this.transformFirstPersonItem(equip, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(-swing * 30.0F, 1.0F, 0.0F, 0.0F);
            }
            case Lel -> {
                this.transformFirstPersonItem(equip, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(-swing * 10.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(-swing * 30.0F, 1.0F, 0.0F, 0.0F);
            }
            case Lil -> {
                this.transformFirstPersonItem(equip, 0.0F);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(-swing * 35.0F, 1.0F, 0.0F, 0.0F);
            }
            case Exhibibi -> {
                this.transformFirstPersonItem(equip, f1);
                GlStateManager.scale(scale, scale, scale);
                GlStateManager.rotate(swing * 30.0F / 2.0F, -swing, -0.0F, 9.0F);
                GlStateManager.rotate(swing * 40.0F, 1.0F, -swing / 2.0F, -0.0F);
            }
            default -> {
            }
        }
        this.doBlockTransformations();
    }

    /**
     * Rise's "1.7 Accurate" finish: the item placed the way 1.7 drew held items. Returns whether
     * to draw it with its first-person transform (block-like items) or with none (flat items,
     * such as swords), as Rise did.
     */
    private boolean classicItemPose(ItemStack stack) {
        if (stack.getItem().shouldRotateAroundWhenRendering()) {
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        }
        GlStateManager.translate(0.58800083F, 0.36999986F, -0.77000016F);
        GlStateManager.translate(0.0F, -0.3F, 0.0F);
        GlStateManager.scale(1.5F, 1.5F, 1.5F);
        GlStateManager.rotate(50.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(335.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-0.9375F, -0.0625F, 0.0F);
        GlStateManager.scale(-2.0F, 2.0F, -2.0F);

        if (this.itemRenderer.shouldRenderItemIn3D(stack)) {
            GlStateManager.scale(0.58823526F, 0.58823526F, 0.58823526F);
            GlStateManager.rotate(-25.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(0.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0F, -0.25F, -0.125F);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            return true;
        }

        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        return false;
    }

    private void doBlockTransformations() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    // Block transform with a parameterized Y translate — used by the imported Swing/UpNDown
    // animation modes (this is the reference client's func_178103_d(float)).
    private void func_178103_d(float qq) {
        GlStateManager.translate(-0.5F, qq, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    /**
     * OpenAstralis's block animations. It was made for modern Minecraft, where a held sword sits
     * differently: the arm transform has no 45 degree turn or 0.4 scale, and swords have another
     * first-person display transform. So this rebuilds that pose (modern's arm transform, the
     * animation, modern's sword display transform), then undoes this version's display transform,
     * which renderItem applies next. That reproduces OpenAstralis's poses exactly.
     */
    private void modernBlockTransform(AnimationsModule.AnimationMode mode, float equipProgress, float swingProgress) {
        float converted = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);

        // Modern's applyItemArmTransform, right arm.
        GlStateManager.translate(0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        switch (mode) {
            case Slide3Alt -> {
                GlStateManager.rotate(-275.5F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(-51.635F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(120.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(converted * 30.0F, 1.0F, 0.0F, 0.0F);
            }
            default -> {
            }
        }

        float scale = AnimationsModule.scale.getValue().floatValue();
        GlStateManager.scale(scale, scale, scale);

        // Modern's first-person display transform for swords (models/item/handheld.json).
        GlStateManager.translate(1.13F / 16F, 3.2F / 16F, 1.13F / 16F);
        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(25.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(0.68F, 0.68F, 0.68F);

        // This version's (translate, then Y, X, Z turns, then scale), undone in reverse. Read from
        // the model so a resource pack's sword pose is cancelled too.
        ItemTransformVec3f own = this.itemRenderer.getItemModelMesher().getItemModel(this.itemToRender)
                .getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.FIRST_PERSON);
        if (own.scale.x != 0.0F && own.scale.y != 0.0F && own.scale.z != 0.0F) {
            GlStateManager.scale(1.0F / own.scale.x, 1.0F / own.scale.y, 1.0F / own.scale.z);
            GlStateManager.rotate(-own.rotation.z, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(-own.rotation.x, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-own.rotation.y, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-own.translation.x, -own.translation.y, -own.translation.z);
        }
    }

    public void renderItemInFirstPerson(float partialTicks)
    {
        if (!Config.isShaders() || !Shaders.isSkipRenderHand())
        {
            float f = 1.0F - (this.prevEquippedProgress + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
            AbstractClientPlayer abstractclientplayer = this.mc.thePlayer;
            float f1 = abstractclientplayer.getSwingProgress(partialTicks);
            float f2 = abstractclientplayer.prevRotationPitch + (abstractclientplayer.rotationPitch - abstractclientplayer.prevRotationPitch) * partialTicks;
            float f3 = abstractclientplayer.prevRotationYaw + (abstractclientplayer.rotationYaw - abstractclientplayer.prevRotationYaw) * partialTicks;
            float var16 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927F);
            final float swingProgress = abstractclientplayer.swingProgress;
            final float convertedProgress = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI * 2);
            this.rotateArroundXAndY(f2, f3);
            this.setLightMapFromPlayer(abstractclientplayer);
            this.rotateWithPlayerRotations((EntityPlayerSP)abstractclientplayer, partialTicks);

            if (rip.tempest.modules.impl.visuals.InspectModule.renderFirstPerson(partialTicks))
            {
                RenderHelper.disableStandardItemLighting();
                return;
            }

            GlStateManager.enableRescaleNormal();
            GlStateManager.translate(AnimationsModule.x.getValue().floatValue(), AnimationsModule.y.getValue().floatValue(), AnimationsModule.z.getValue().floatValue());
            GlStateManager.pushMatrix();

            if (this.itemToRender != null)
            {
                this.heldItemTransform = ItemCameraTransforms.TransformType.FIRST_PERSON;

                if (this.itemToRender.getItem() instanceof ItemMap)
                {
                    this.renderItemMap(abstractclientplayer, f2, f, f1);
                    SwordTrail.capture(null, f1, false, null);
                }

                else
                {
                    // The swing trail replays this pose at in-between swing progress values.
                    SwordTrail.capture(this.itemToRender, f1, abstractclientplayer.isSwingInProgress,
                            progress -> this.transformHeldItem(abstractclientplayer, partialTicks, f, progress, f2, false));
                    this.transformHeldItem(abstractclientplayer, partialTicks, f, f1, f2, true);
                }

                this.renderItem(abstractclientplayer, this.itemToRender, this.heldItemTransform);
            }
            else if (!abstractclientplayer.isInvisible())
            {
                SwordTrail.capture(null, f1, abstractclientplayer.isSwingInProgress, null);
                this.renderPlayerArm(abstractclientplayer, f, f1);
            }

            SwordTrail.render();
            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        }
    }

    /**
     * Poses the held item for the current swing, block-hit or use animation. Split out of
     * renderItemInFirstPerson so the swing trail can replay the same pose at in-between swing
     * progress values; {@code live} is false for those replays, which skips side effects.
     */
    private void transformHeldItem(AbstractClientPlayer abstractclientplayer, float partialTicks, float f, float f1, float f2, boolean live) {
        float var16 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927F);
        boolean toolBlock = AnimationsModule.toolBlocking(abstractclientplayer, this.itemToRender);
        if (abstractclientplayer.getItemInUseCount() > 0 || toolBlock || AuraModule.rendersBlock())
        {
            EnumAction enumaction = this.itemToRender.getItemUseAction();
            // Block With Tools: drawn as a sword block would be, though nothing is really in use.
            if (toolBlock) enumaction = EnumAction.BLOCK;

            // Aura (OpenMyau's KillAura) draws its block whether or not it's really up
            if (AuraModule.rendersBlock()) enumaction = EnumAction.BLOCK;

            switch (enumaction)
            {
                case NONE:
                    this.transformFirstPersonItem(f, 0.0F);
                    GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                    break;
                case EAT:
                case DRINK:
                    if (AnimationsModule.swingEating.getValue() && Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled()) {
                        if (live) this.swing();
                    }
                    this.performDrinking(abstractclientplayer, partialTicks);
                    this.transformFirstPersonItem(f, 0.0F);
                    GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                    break;

                case BLOCK:
                    if (Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled()) {
                        switch (AnimationsModule.mode.getValue()) {
                            case Exhibition:
                                this.transformFirstPersonItem(f / 2, 0);
                                GlStateManager.rotate(-var16 * 40.0F / 2.0F, var16 / 2.0F, -0.0F, 9.0F);
                                GlStateManager.rotate(-var16 * 30.0F, 1.0F, var16 / 2.0F, -0.0F);
                                this.doBlockTransformations();
                                GL11.glTranslatef(-0.05F, this.mc.thePlayer.isSneaking() ? -0F : 0.0F, 0.1F);
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            case Novoline:
                                this.transformFirstPersonItem(f / 1.5F, 0.0F);
                                GlStateManager.translate(-0.5F, 0.2F, 0.0F);
                                GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
                                GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.translate(-0.05F, 0.3F, 0.3F);
                                GlStateManager.rotate(-var16 * 140.0F, 8.0F, 0.0F, 8.0F);
                                GlStateManager.rotate(var16 * 90.0F, 8.0F, 0.0F, 8.0F);
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            case Sigma: {
                                float sq = MathHelper.sin(f1 * f1 * (float) Math.PI);
                                this.sigmaBase(f * 0.5f, 0);
                                GlStateManager.rotate(-sq * 55 / 2.0F, -8.0F, -0.0F, 9.0F);
                                GlStateManager.rotate(-sq * 45, 1.0F, sq / 2, -0.0F);
                                doBlockTransformations();
                                GL11.glTranslated(1.2, 0.3, 0.5);
                                GL11.glTranslatef(-1, mc.thePlayer.isSneaking() ? -0.1F : -0.2F, 0.2F);
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            }
                            case SigmaSlide: {
                                float smooth = f1 * 0.8f - f1 * f1 * 0.8f;
                                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                                GlStateManager.translate(0.0F, f * 0.3f * -0.6F, 0.0F);
                                GlStateManager.rotate(45.0F, 0.0F, 2 + smooth * 0.5f, smooth * 3);
                                GlStateManager.scale(0.37F, 0.37F, 0.37F);
                                this.doBlockTransformations();
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            }
                            case SigmaVanilla:
                                this.sigmaBase(0, f1);
                                this.doBlockTransformations();
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            case SigmaAvatar: {
                                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                                float sq = MathHelper.sin(f1 * f1 * (float) Math.PI);
                                float rt = MathHelper.sin(MathHelper.sqrt_float(f1) * (float) Math.PI);
                                GlStateManager.rotate(sq * -20.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.rotate(rt * -20.0F, 0.0F, 0.0F, 1.0F);
                                GlStateManager.rotate(rt * -40.0F, 1.0F, 0.0F, 0.0F);
                                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                                this.doBlockTransformations();
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            }
                            case SigmaTap: {
                                float smooth = f1 * 0.8f - f1 * f1 * 0.8f;
                                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                                GlStateManager.translate(0.0F, f * -0.15F, 0.0F);
                                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.rotate(smooth * -90.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.scale(0.37F, 0.37F, 0.37F);
                                this.doBlockTransformations();
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            }
                            case SigmaTap2: {
                                float rt = MathHelper.sin(MathHelper.sqrt_float(f1) * (float) Math.PI);
                                GlStateManager.translate(0.56F, -0.42F, -0.71999997F);
                                GlStateManager.translate(0.0F, f * -0.15F, 0.0F);
                                GlStateManager.rotate(30, 0.0F, 1.0F, 0.0F);
                                GlStateManager.rotate(rt * -30.0F, 0.0F, 1.0F, 0.0F);
                                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                                this.doBlockTransformations();
                                GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                                break;
                            }
                            case Swang:
                                GlStateManager.translate(0, -0.003 * 25, 0);
                                this.transformFirstPersonItem(f / 1.8f, 0.0f);
                                GL11.glTranslatef(0.1F, 0.4F, -0.1F);
                                GL11.glRotated(-var16 * 22.0F * 1.2, var16 / 2, 0.0F, 9.0F);
                                GL11.glRotated(-var16 * 39.0F * 1.2, 0.8F, var16 / 2, 0F);
                                this.doBlockTransformations();
                                break;
                            case OnePointEight:
                                transformFirstPersonItem(f, 0.0F);
                                doBlockTransformations();
                                break;
                            case UpNDown: {
                                this.transformFirstPersonItem(f / 2.0F, f1);
                                float var152 = MathHelper.sin((float) (MathHelper.sqrt_float(f1) * Math.PI));
                                GlStateManager.rotate(var152 * 30.0F / 2.0F, -var152, -0.0F, 9.0F);
                                GlStateManager.rotate(var152 * 40.0F, 1.0F, -var152 / 2.0F, -0.0F);
                                this.func_178103_d(0.4F);
                                break;
                            }
                            // ── Imported block-hit animations ──────────────────────────
                            case Virtue: {
                                this.transformFirstPersonItem(f - 0.30F, f1);
                                this.doBlockTransformations();
                                break;
                            }
                            case SevenClassic: {
                                this.transformFirstPersonItem(f, f1);
                                this.doBlockTransformations();
                                break;
                            }
                            case Smooth2: {
                                this.transformFirstPersonItem(f / 1.5F, 0.0F);
                                this.doBlockTransformations();
                                GlStateManager.translate(-0.05F, 0.3F, 0.3F);
                                GlStateManager.rotate(-var16 * 140.0F, 8.0F, 0.0F, 8.0F);
                                GlStateManager.rotate(var16 * 90.0F, 8.0F, 0.0F, 8.0F);
                                break;
                            }
                            case Ethereal: {
                                this.transformFirstPersonItem(f, 0.0F);
                                this.doBlockTransformations();
                                GlStateManager.translate(-0.05F, 0.2F, 0.2F);
                                GlStateManager.rotate(-var16 * 70.0F / 2.0F, -8.0F, -0.0F, 9.0F);
                                GlStateManager.rotate(-var16 * 70.0F, 1.0F, -0.4F, -0.0F);
                                break;
                            }
                            // ── From Yuri (AGPL-3.0): the block animations Tempest didn't already have ──
                            case Spin: {
                                this.transformFirstPersonItem(f, 0.0F);
                                GlStateManager.translate(0, 0.2F, -1);
                                GlStateManager.rotate(-59, -1, 0, 3);
                                GlStateManager.rotate(-(float) ((System.currentTimeMillis() / 3 * AnimationsModule.spinSpeed.getValue()) % 360), 1.0F, 0.0F, 0.0F);
                                GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                                break;
                            }
                            case Smooth3: {
                                this.transformFirstPersonItem(f / 2.0F - 0.18F, 0.0F);
                                GlStateManager.rotate(-var16 * 80.0F / 5.0F, var16 / 3.0F, -0.0F, 9.0F);
                                GlStateManager.rotate(-var16 * 40.0F, 8.0F, var16 / 9.0F, -0.1F);
                                this.doBlockTransformations();
                                break;
                            }
                            // ── From OpenAstralis, drawn in modern Minecraft's hand pose ──
                            case Slide3Alt:
                                this.modernBlockTransform(AnimationsModule.mode.getValue(), f, f1);
                                break;
                            // ── From Rise 6 ──
                            case SevenAccurate:
                            case Lucid:
                            case Astro:
                            case ExhibitionNew:
                            case Swong:
                            case Question:
                            case Dortware:
                                this.riseBlockTransform(AnimationsModule.mode.getValue(), f, f1);
                                break;
                            // ── From Radium ──
                            case Lol:
                            case Lul:
                            case Lel:
                            case Lil:
                            case Exhibibi:
                                this.radiumBlockTransform(AnimationsModule.mode.getValue(), f, f1);
                                break;
                            // ── From OpenAstolfo ──
                            case AstolfoDefault:
                            case AstolfoOld:
                            case AstolfoSpin:
                            case Spinny:
                            case Dash:
                                this.astolfoBlockTransform(AnimationsModule.mode.getValue(), f, f1);
                                break;
                        }
                    } else {
                        this.transformFirstPersonItem(f, 0.0F);
                        this.doBlockTransformations();
                        GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
                    }
                    break;

                case BOW:
                    this.transformFirstPersonItem(f, 0.0F);
                    this.doBowTransformations(partialTicks, abstractclientplayer);
                    GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
            }
        }
        else
        {
            if (Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled() && AnimationsModule.smoothHit.getValue()) {
                this.sigmaBase(f * 0.5F, f1);
            } else if (Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled() && AnimationsModule.fluxSwing.getValue()) {
                this.transformFirstPersonItem(f, f1);
            } else {
                this.doItemUsedTransformations(f1);
                this.transformFirstPersonItem(f, f1);
            }
            GlStateManager.scale(AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue(), AnimationsModule.scale.getValue());
        }
    }

    public void renderOverlays(float partialTicks) {
        GlStateManager.disableAlpha();

        if (this.mc.thePlayer.isEntityInsideOpaqueBlock()) {
            IBlockState iblockstate = this.mc.theWorld.getBlockState(new BlockPos(this.mc.thePlayer));
            BlockPos blockpos = new BlockPos(this.mc.thePlayer);
            EntityPlayer entityplayer = this.mc.thePlayer;

            for (int i = 0; i < 8; ++i) {
                double d0 = entityplayer.posX + (double) (((float) ((i >> 0) % 2) - 0.5F) * entityplayer.width * 0.8F);
                double d1 = entityplayer.posY + (double) (((float) ((i >> 1) % 2) - 0.5F) * 0.1F);
                double d2 = entityplayer.posZ + (double) (((float) ((i >> 2) % 2) - 0.5F) * entityplayer.width * 0.8F);
                BlockPos blockpos1 = new BlockPos(d0, d1 + (double) entityplayer.getEyeHeight(), d2);
                IBlockState iblockstate1 = this.mc.theWorld.getBlockState(blockpos1);

                if (iblockstate1.getBlock().isVisuallyOpaque()) {
                    iblockstate = iblockstate1;
                    blockpos = blockpos1;
                }
            }

            if (iblockstate.getBlock().getRenderType() != -1) {
                this.renderBlockInHand(partialTicks, this.mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(iblockstate));
            }
        }

        if (!this.mc.thePlayer.isSpectator()) {
            if (this.mc.thePlayer.isInsideOfMaterial(Material.water)) {
                this.renderWaterOverlayTexture(partialTicks);
            }

            if (this.mc.thePlayer.isBurning()) {
                if (Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled() && AnimationsModule.noFireOverlay.getValue()) return;
                this.renderFireInFirstPerson(partialTicks);
            }
        }

        GlStateManager.enableAlpha();
    }

    private void renderBlockInHand(float partialTicks, TextureAtlasSprite atlas) {
        this.mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        float f = 0.1F;
        GlStateManager.color(0.1F, 0.1F, 0.1F, 0.5F);
        GlStateManager.pushMatrix();
        float f1 = -1.0F;
        float f2 = 1.0F;
        float f3 = -1.0F;
        float f4 = 1.0F;
        float f5 = -0.5F;
        float f6 = atlas.getMinU();
        float f7 = atlas.getMaxU();
        float f8 = atlas.getMinV();
        float f9 = atlas.getMaxV();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(-1.0D, -1.0D, -0.5D).tex(f7, f9).endVertex();
        worldrenderer.pos(1.0D, -1.0D, -0.5D).tex(f6, f9).endVertex();
        worldrenderer.pos(1.0D, 1.0D, -0.5D).tex(f6, f8).endVertex();
        worldrenderer.pos(-1.0D, 1.0D, -0.5D).tex(f7, f8).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderWaterOverlayTexture(float partialTicks) {
        if (!Config.isShaders() || Shaders.isUnderwaterOverlay()) {
            this.mc.getTextureManager().bindTexture(RES_UNDERWATER_OVERLAY);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer worldrenderer = tessellator.getWorldRenderer();
            float f = this.mc.thePlayer.getBrightness(partialTicks);
            GlStateManager.color(f, f, f, 0.5F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.pushMatrix();
            float f1 = 4.0F;
            float f2 = -1.0F;
            float f3 = 1.0F;
            float f4 = -1.0F;
            float f5 = 1.0F;
            float f6 = -0.5F;
            float f7 = -this.mc.thePlayer.rotationYaw / 64.0F;
            float f8 = this.mc.thePlayer.rotationPitch / 64.0F;
            worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
            worldrenderer.pos(-1.0D, -1.0D, -0.5D).tex(4.0F + f7, 4.0F + f8).endVertex();
            worldrenderer.pos(1.0D, -1.0D, -0.5D).tex(0.0F + f7, 4.0F + f8).endVertex();
            worldrenderer.pos(1.0D, 1.0D, -0.5D).tex(0.0F + f7, 0.0F + f8).endVertex();
            worldrenderer.pos(-1.0D, 1.0D, -0.5D).tex(4.0F + f7, 0.0F + f8).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
        }
    }

    private void renderFireInFirstPerson(float partialTicks) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.9F);
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float f = 1.0F;

        for (int i = 0; i < 2; ++i) {
            GlStateManager.pushMatrix();
            TextureAtlasSprite textureatlassprite = this.mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/fire_layer_1");
            this.mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            float f1 = textureatlassprite.getMinU();
            float f2 = textureatlassprite.getMaxU();
            float f3 = textureatlassprite.getMinV();
            float f4 = textureatlassprite.getMaxV();
            float f5 = (0.0F - f) / 2.0F;
            float f6 = f5 + f;
            float f7 = 0.0F - f / 2.0F;
            float f8 = f7 + f;
            float f9 = -0.5F;
            GlStateManager.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            GlStateManager.rotate((float) (i * 2 - 1) * 10.0F, 0.0F, 1.0F, 0.0F);
            worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
            worldrenderer.setSprite(textureatlassprite);
            worldrenderer.pos(f5, f7, f9).tex(f2, f4).endVertex();
            worldrenderer.pos(f6, f7, f9).tex(f1, f4).endVertex();
            worldrenderer.pos(f6, f8, f9).tex(f1, f3).endVertex();
            worldrenderer.pos(f5, f8, f9).tex(f2, f3).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
    }

    public void updateEquippedItem() {
        this.prevEquippedProgress = this.equippedProgress;
        EntityPlayer entityplayer = this.mc.thePlayer;
        // During a server-side slot swap the hand keeps showing what was held before (SlotProcess).
        ItemStack itemstack = SlotProcess.visibleStack(entityplayer);
        boolean flag = false;

        if (this.itemToRender != null && itemstack != null) {
            if (!this.itemToRender.getIsItemStackEqual(itemstack)) {
                // With Hit Bob on, Hit Bob alone shows a hit's wear, once per damaging hit, so
                // Unbreaking or a breakable sword can't add or take away bobs. The same item in
                // the same slot that has only lost durability is taken without a dip of its own.
                boolean onlyWorn = this.itemToRender.getItem() == itemstack.getItem()
                        && this.itemToRender.stackSize == itemstack.stackSize
                        && ItemStack.areItemStackTagsEqual(this.itemToRender, itemstack)
                        && this.equippedItemSlot == SlotProcess.visibleSlot(entityplayer);
                if (onlyWorn && AnimationsModule.hitBobActive()) this.itemToRender = itemstack;
                else flag = true;
            }
        } else flag = this.itemToRender != null || itemstack != null;
        if (this.replayEquip) flag = true;

        float f2 = 0.4F;
        float f = flag ? 0.0F : 1.0F;
        float f1 = MathHelper.clamp_float(f - this.equippedProgress, -f2, f2);
        this.equippedProgress += f1;

        if (this.equippedProgress < 0.1F) {
            this.itemToRender = itemstack;
            this.equippedItemSlot = SlotProcess.visibleSlot(entityplayer);
            this.replayEquip = false;

            if (Config.isShaders()) {
                Shaders.setItemToRenderMain(itemstack);
            }
        }
    }

    public void resetEquippedProgress() {
        this.equippedProgress = 0.0F;
    }

    /**
     * Dips the held item out of view and brings it back, as when it changes. Singleplayer shows
     * this on every damaging hit because the server sends back the sword with its new durability;
     * most servers don't (unbreakable swords, or the client already counted the wear).
     */
    public void replayEquip() {
        this.replayEquip = true;
    }
}
