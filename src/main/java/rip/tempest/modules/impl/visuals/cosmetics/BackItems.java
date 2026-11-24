package rip.tempest.modules.impl.visuals.cosmetics;

import rip.tempest.modules.impl.visuals.CosmeticsModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import static rip.tempest.utils.Util.mc;

final class BackItems {
    private static ItemStack sword;
    private static ItemStack bow;

    private BackItems() {
    }

    static void render(ModelPlayer model, AbstractClientPlayer player) {
        boolean isSword = CosmeticsModule.backItem.getValue() == CosmeticsModule.BackItem.Sword;
        ItemStack held = player.getHeldItem();
        if (held != null && (isSword ? held.getItem() instanceof ItemSword : held.getItem() instanceof ItemBow)) return;

        if (sword == null) {
            sword = new ItemStack(Items.diamond_sword);
            bow = new ItemStack(Items.bow);
        }

        GlStateManager.pushMatrix();
        model.bipedBody.postRender(0.0625F);

        GlStateManager.translate(0F, 0.32F, 0.17F);

        GlStateManager.rotate(180F, 0F, 0F, 1F);
        GlStateManager.scale(0.9F, 0.9F, 0.9F);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getRenderItem().renderItem(isSword ? sword : bow, ItemCameraTransforms.TransformType.NONE);
        GlStateManager.popMatrix();
    }
}
