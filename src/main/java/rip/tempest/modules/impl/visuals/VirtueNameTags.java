package rip.tempest.modules.impl.visuals;

import rip.tempest.utils.render.VirtueFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VirtueNameTags {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int TAG_2D_BOX = 0xB9000000;
    private static final int ENCHANT = 0xFFFFFAFA;

    private final Map<EntityPlayer, double[]> positions = new LinkedHashMap<>();
    private static final FloatBuffer PROJECT_MODEL = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECT_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer PROJECT_VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer PROJECT_SCREEN = BufferUtils.createFloatBuffer(3);
    private double gradualFovModifier;

    void update2D(List<EntityPlayer> players) {
        positions.clear();
        float partialTicks = mc.timer.renderPartialTicks;

        FloatBuffer modelView = PROJECT_MODEL, projection = PROJECT_PROJECTION;
        IntBuffer viewport = PROJECT_VIEWPORT;
        modelView.clear();
        projection.clear();
        viewport.clear();
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GlStateManager.getFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        FloatBuffer screen = PROJECT_SCREEN;
        for (EntityPlayer player : players) {
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().renderPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().renderPosY;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().renderPosZ;
            y += player.height + 0.2;
            screen.clear();
            if (!GLU.gluProject((float) x, (float) y, (float) z, modelView, projection, viewport, screen)) continue;
            double depth = screen.get(2);
            if (depth < 0.0 || depth >= 1.0) continue;
            positions.put(player, new double[]{screen.get(0), mc.displayHeight - screen.get(1), depth});
        }
    }

    void draw2D(double scaleSetting, boolean armor) {
        if (positions.isEmpty()) return;
        FontRenderer font = VirtueFont.get();
        GlStateManager.pushMatrix();

        double unit = 1.0 / new ScaledResolution(mc).getScaleFactor();
        GlStateManager.scale(unit, unit, unit);
        for (Map.Entry<EntityPlayer, double[]> entry : positions.entrySet()) {
            EntityPlayer player = entry.getKey();
            double[] at = entry.getValue();
            double health = round2(player.getHealth() / 2.0f);
            String color = health >= 6.0 ? "§2" : health >= 3.5 ? "§6" : "§4";
            String text = player.getDisplayName().getFormattedText() + " " + color + health + " ❤";

            GlStateManager.pushMatrix();
            GlStateManager.translate(at[0], at[1], 0.0);
            applyScale2D(scaleSetting);
            GlStateManager.translate(0.0, -2.5, 0.0);
            int width = font.getStringWidth(text);
            rect(-width / 2 - 3, -12.0, width / 2 + 3, 1.0, TAG_2D_BOX);
            font.drawString(text, -width / 2, -9, -1, false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            if (armor) {
                List<ItemStack> items = new ArrayList<>();
                for (int slot = 0; slot < 5; slot++) {
                    ItemStack stack = player.getEquipmentInSlot(slot);
                    if (stack != null) items.add(stack);
                }
                int x = -(items.size() * 9);
                for (ItemStack stack : items) {
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemIntoGUI(stack, x, -30);
                    mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, x, -30);
                    x += 2;
                    RenderHelper.disableStandardItemLighting();
                    enchantTags(stack, x, 21);
                    x += 16;
                }
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void applyScale2D(double scaleSetting) {
        float scale = (float) scaleSetting;
        float target = scale * (mc.gameSettings.fovSetting / (mc.gameSettings.fovSetting * mc.thePlayer.getFovModifier()));
        if (gradualFovModifier == 0.0 || Double.isNaN(gradualFovModifier) || Double.isInfinite(gradualFovModifier)) gradualFovModifier = target;
        gradualFovModifier += (target - gradualFovModifier) / (Math.max(1, Minecraft.getDebugFPS()) * 0.7);
        scale *= (float) gradualFovModifier;
        if (mc.currentScreen == null && mc.gameSettings.ofKeyBindZoom.isKeyDown()) scale *= 3;
        GlStateManager.scale(scale, scale, scale);
    }

    void draw3D(EntityPlayer player, double scaleSetting, boolean armor) {
        float dist = mc.thePlayer.getDistanceToEntity(player);
        float factor = (float) (dist <= 8.0f ? 8.0 * scaleSetting : dist * scaleSetting);
        beginTag(player, 0.02672f * factor);
        if (armor) {
            List<ItemStack> items = new ArrayList<>();
            ItemStack hand = player.getEquipmentInSlot(0);
            if (hand != null) items.add(hand);
            for (int slot = 4; slot > 0; slot--) {
                ItemStack stack = player.getEquipmentInSlot(slot);
                if (stack != null) items.add(stack);
            }
            int x = -(items.size() * 8);
            RenderItem renderItem = mc.getRenderItem();
            for (ItemStack stack : items) {
                GlStateManager.disableDepth();
                RenderHelper.enableGUIStandardItemLighting();
                renderItem.zLevel = -100.0f;
                renderItem.renderItemIntoGUI(stack, x, -18);
                renderItem.renderItemOverlays(mc.fontRendererObj, stack, x, -18);
                renderItem.zLevel = 0.0f;
                RenderHelper.disableStandardItemLighting();
                GlStateManager.enableDepth();
                GlStateManager.disableLighting();
                enchantTags(stack, x, 0);
                x += 16;
            }
        }
        endTag();
    }

    void drawTags(EntityPlayer player, double scaleSetting, double distanceSetting, boolean armor) {
        float dist = mc.thePlayer.getDistanceToEntity(player);
        float factor = (float) (dist <= distanceSetting ? distanceSetting * scaleSetting : dist * scaleSetting);
        beginTag(player, 0.02672f * factor);
        if (armor && mc.thePlayer.getDistanceSqToEntity(player) <= 900.0) {
            ItemStack[] worn = player.inventory.armorInventory;
            int x = 0;
            for (ItemStack stack : worn) {
                if (stack != null) x -= 8;
            }
            if (player.getHeldItem() != null) {
                x -= 8;
                renderItemStack(single(player.getHeldItem()), x, -20);
                x += 16;
            }
            for (ItemStack stack : worn) {
                if (stack != null) {
                    renderItemStack(single(stack), x, -20);
                    x += 16;
                }
            }
        }
        endTag();
    }

    private void beginTag(EntityPlayer player, float scale) {
        float partialTicks = mc.timer.renderPartialTicks;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().renderPosY + player.height + 0.5;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().renderPosZ;
        double health = round2(player.getHealth());
        String color = health >= 12.0 ? "§2" : health >= 4.0 ? "§6" : "§4";
        String text = player.getDisplayName().getFormattedText() + " | " + color + health;
        FontRenderer font = VirtueFont.get();

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.translate(x, y, z);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        int half = font.getStringWidth(text) / 2;
        GlStateManager.disableTexture2D();

        GL11.glLineWidth(1.0f);
        GlStateManager.color(0.0f, 0.0f, 0.0f, 1.0f);
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        renderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        renderer.pos(-half - 2, -0.8, 0.0).endVertex();
        renderer.pos(-half - 2, 8.8, 0.0).endVertex();
        renderer.pos(half + 2, 8.8, 0.0).endVertex();
        renderer.pos(half + 2, -0.8, 0.0).endVertex();
        renderer.pos(-half - 2, -0.8, 0.0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(-half - 2, -0.8, 0.0).color(0, 0, 0, 100).endVertex();
        renderer.pos(-half - 2, 8.8, 0.0).color(0, 0, 0, 100).endVertex();
        renderer.pos(half + 2, 8.8, 0.0).color(0, 0, 0, 100).endVertex();
        renderer.pos(half + 2, -0.8, 0.0).color(0, 0, 0, 100).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        font.drawString(text, -font.getStringWidth(text) / 2, 0, -1, false);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
    }

    private void endTag() {
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (copy.hasEffect() && (copy.getItem() instanceof ItemTool || copy.getItem() instanceof ItemArmor)) copy.stackSize = 1;
        return copy;
    }

    private void renderItemStack(ItemStack stack, int x, int y) {
        RenderItem renderItem = mc.getRenderItem();
        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        renderItem.zLevel = -150.0f;
        renderItem.renderItemAndEffectIntoGUI(stack, x, y);
        renderItem.renderItemOverlays(mc.fontRendererObj, stack, x, y);
        renderItem.zLevel = 0.0f;
        GlStateManager.disableBlend();
        GlStateManager.scale(0.5, 0.5, 0.5);
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        enchantCodes(stack, x, y);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        GlStateManager.enableAlpha();
        GlStateManager.popMatrix();
    }

    private static void enchantCodes(ItemStack stack, int x, int y) {
        FontRenderer font = VirtueFont.get();
        int encY = y - 24;
        List<String> codes = new ArrayList<>();
        if (stack.getItem() instanceof ItemArmor) {
            add(codes, "p", Enchantment.protection, stack);
            add(codes, "t", Enchantment.thorns, stack);
            add(codes, "u", Enchantment.unbreaking, stack);
        }
        if (stack.getItem() instanceof ItemBow) {
            add(codes, "d", Enchantment.power, stack);
            add(codes, "k", Enchantment.punch, stack);
            add(codes, "f", Enchantment.flame, stack);
            add(codes, "u", Enchantment.unbreaking, stack);
        }
        if (stack.getItem() instanceof ItemSword) {
            add(codes, "s", Enchantment.sharpness, stack);
            add(codes, "k", Enchantment.knockback, stack);
            add(codes, "f", Enchantment.fireAspect, stack);
            add(codes, "u", Enchantment.unbreaking, stack);
        }
        for (String code : codes) {
            font.drawStringWithShadow("§e" + code, x * 2, encY, 0xFFFFFFFF);
            encY += 7;
        }
    }

    private static void add(List<String> codes, String letter, Enchantment enchantment, ItemStack stack) {
        int level = EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, stack);
        if (level > 0) codes.add(letter + level);
    }

    private static void enchantTags(ItemStack stack, int x, int y) {
        int sharpness = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
        int fire = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
        int knockback = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
        if (sharpness > 0) {
            enchantTag("Sh" + sharpness, x, y);
            y -= 9;
        }
        if (fire > 0) {
            enchantTag("Fir" + fire, x, y);
            y -= 9;
        }
        if (knockback > 0) {
            enchantTag("Kb" + knockback, x, y);
        } else if (stack.getItem() instanceof ItemArmor) {
            int protection = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
            int thorns = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack);
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
            if (protection > 0) {
                enchantTag("P" + protection, x, y);
                y -= 9;
            }
            if (thorns > 0) {
                enchantTag("Th" + thorns, x, y);
                y -= 9;
            }
            if (unbreaking > 0) enchantTag("Unb" + unbreaking, x, y);
        } else if (stack.getItem() instanceof ItemBow) {
            int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
            int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
            int flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);
            if (power > 0) {
                enchantTag("Pow" + power, x, y);
                y -= 9;
            }
            if (punch > 0) {
                enchantTag("Pun" + punch, x, y);
                y -= 9;
            }
            if (flame > 0) enchantTag("Fir" + flame, x, y);
        } else if (stack.getRarity() == EnumRarity.EPIC) {
            enchantTag("§lGod", x, y);
        }
    }

    private static void enchantTag(String text, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        y -= 4;
        GlStateManager.scale(0.57f, 0.57f, 0.57f);
        VirtueFont.get().drawStringWithShadow(text, x, -36 - y, ENCHANT);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static void rect(double left, double top, double right, double bottom, int color) {
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(left, bottom, 0).color(r, g, b, a).endVertex();
        renderer.pos(right, bottom, 0).color(r, g, b, a).endVertex();
        renderer.pos(right, top, 0).color(r, g, b, a).endVertex();
        renderer.pos(left, top, 0).color(r, g, b, a).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
