package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;

import java.awt.*;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Item ESP", category = ModuleCategory.VISUALS)
public final class ItemESPModule extends Module {
    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = e -> {
        for (final Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (!(entity instanceof EntityItem entityItem))
                continue;

            String enhancement = "";

            if (EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, entityItem.getEntityItem()) != 0) {
                enhancement = "§b Protection:§c" + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, entityItem.getEntityItem());
            }

            if (EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, entityItem.getEntityItem()) != 0) {
                enhancement = "§b Sharpness:§c" + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, entityItem.getEntityItem());
            }

            if (entityItem.getEntityItem().getItem() == Items.golden_apple) {
                if (entityItem.getEntityItem().getItem().hasEffect(entityItem.getEntityItem())) {
                    enhancement = "§c Enchanted";
                }
            }

            final String var3 = (entityItem.getEntityItem().stackSize > 1) ? ("§f x" + entityItem.getEntityItem().stackSize) : "";

            float partialTicks = mc.timer.renderPartialTicks;
            double interpolatedX = entityItem.lastTickPosX + (entityItem.posX - entityItem.lastTickPosX) * partialTicks;
            double interpolatedY = entityItem.lastTickPosY + (entityItem.posY - entityItem.lastTickPosY) * partialTicks;
            double interpolatedZ = entityItem.lastTickPosZ + (entityItem.posZ - entityItem.lastTickPosZ) * partialTicks;
            double diffX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks - interpolatedX;
            double diffY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks - interpolatedY;
            double diffZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks - interpolatedZ;

            double dist = MathHelper.sqrt_double(diffX * diffX + diffY * diffY + diffZ * diffZ);

            GlStateManager.pushMatrix();
            drawText(entityItem.getEntityItem().getDisplayName() + var3 + enhancement, -1, interpolatedX, interpolatedY, interpolatedZ, dist);
            GlStateManager.popMatrix();
        }
    };

    public static void drawText(String value, int textColor, double posX, double posY, double posZ, double dist) {
        posX -= mc.getRenderManager().viewerPosX;
        posY -= mc.getRenderManager().viewerPosY;
        posZ -= mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posX, (float) posY + 1, (float) posZ);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        float scale = Math.min(Math.max(0.02266667f, (float) (0.001500000013038516 * dist)), 0.07f);
        GlStateManager.scale(-scale, -scale, -scale);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        int textWidth = mc.fontRendererObj.getStringWidth(value);
        int x = -textWidth / 2 + (int) (scale * 3.5f);
        int y = (int) (-(123.805f * scale - 2.47494f));

        String outline = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        int black = Color.BLACK.getRGB();
        mc.fontRendererObj.drawString(outline, x - 1, y, black);
        mc.fontRendererObj.drawString(outline, x + 1, y, black);
        mc.fontRendererObj.drawString(outline, x, y - 1, black);
        mc.fontRendererObj.drawString(outline, x, y + 1, black);
        mc.fontRendererObj.drawString(value, x, y, textColor);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }
}
