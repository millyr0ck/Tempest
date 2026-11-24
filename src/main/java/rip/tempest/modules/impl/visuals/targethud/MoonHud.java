package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.mc.ServerUtils;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import java.awt.Color;
import java.awt.Font;

import static rip.tempest.utils.Util.mc;

public final class MoonHud {
    public enum Style {
        Moon, Astolfo, AstolfoO, AstolfoOld, Novoline, NovolineO, Remix, Custom, Nostalgia
    }

    private static final int ARMOR_BLUE = -15374912;

    private EntityLivingBase current;

    private double barA, barB;

    private double armorBar;

    public void update(Style style, EntityLivingBase target) {
        current = target;
        if (target == null || Float.isNaN(target.getHealth())) return;

        double fps = Math.max(Minecraft.getDebugFPS(), 1);
        float fraction = fraction(target);
        switch (style) {
            case Moon -> {
                float inner = moonWidth(target) - 2;
                barA = ease(barA, Math.max(inner * fraction, 0.5D), 15 / fps);
                armorBar = ease(armorBar, Math.max(inner / 20 * Math.min(target.getTotalArmorValue(), 20), 0.5D), 15 / fps);
            }
            case AstolfoO -> barA = linear(barA, Math.max(194 * fraction, 0.5D), 200 / fps);
            case Astolfo -> barA = linear(barA, Math.max(111 * fraction, 0.5D), 300 / fps);
            case AstolfoOld -> barA = ease(barA, 94 * fraction, 25 / fps);
            case Novoline -> {
                barA = ease(barA, 86 * fraction, 25 / fps);
                barB = ease(barB, 86 * fraction, 6 / fps);
            }
            case NovolineO -> barA = linear(barA, Math.max(novolineOWidth(target) * fraction, 0.5D), 150 / fps);
            case Custom -> barA = ease(barA, (customWidth(target) - 7) * fraction, 15 / fps);
            default -> {
            }
        }
    }

    public float[] size(Style style) {
        EntityLivingBase target = current;
        return switch (style) {
            case Moon -> new float[]{target == null ? 100 : moonWidth(target), 36.5F};
            case Astolfo, Remix -> new float[]{140, 49};
            case AstolfoO -> new float[]{200, 70};
            case AstolfoOld -> new float[]{127, 45};
            case Novoline -> new float[]{130, 40};
            case NovolineO -> new float[]{target == null ? 90 : novolineOWidth(target), 43};
            case Custom -> new float[]{target == null ? 118 : customWidth(target), 43};
            case Nostalgia -> new float[]{target == null ? 110 : nostalgiaWidth(target), 41};
        };
    }

    public void render(Style style, float x, float y, boolean shapeOnly) {
        EntityLivingBase target = current;
        if (target == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (shapeOnly) {
            float[] size = size(style);
            if (style == Style.Moon) rect(0, 0, size[0], size[1], new Color(21, 21, 21, 95).getRGB());
            if (style == Style.Custom) rect(0, 0, size[0], size[1], new Color(0, 0, 0, 120).getRGB());
        } else {
            GlStateManager.enableBlend();
            switch (style) {
                case Moon -> moon(target);
                case Astolfo -> astolfo(target);
                case AstolfoO -> astolfoO(target);
                case AstolfoOld -> astolfoOld(target);
                case Novoline -> novoline(target);
                case NovolineO -> novolineO(target);
                case Remix -> remix(target);
                case Custom -> custom(target);
                case Nostalgia -> nostalgia(target);
            }
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
        GlStateManager.popMatrix();
    }

    private void moon(EntityLivingBase target) {
        float width = moonWidth(target), inner = width - 2;
        CustomFontRenderer small = FontProcess.getStyledFont("sans", 18, Font.PLAIN);
        CustomFontRenderer big = FontProcess.getStyledFont("sans", 25, Font.PLAIN);
        int barBorder = new Color(25, 25, 25).getRGB(), barBack = new Color(11, 11, 11, 150).getRGB();

        rect(0, 0, width, 36.5F, new Color(21, 21, 21, 95).getRGB());
        bordered(1, 33, inner, 2, 0.5F, barBorder, barBack);
        bordered(1, 33, (float) armorBar, 2, 0.5F, barBorder, ARMOR_BLUE);
        face(target, 2, 3, 25);
        bordered(1, 29, inner, 2, 0.5F, barBorder, barBack);
        bordered(1, 29, (float) barA, 2, 0.5F, barBorder, healthColor(target, 1));
        small.drawStringWithShadow(moonHealth(target), 29, 17.5F, -1);
        big.drawStringWithShadow(target.getName(), 29, 3.5F, -1);
    }

    private void astolfo(EntityLivingBase target) {
        int color = ColorProcess.getColor().getRGB();
        Color base = new Color(color);

        rect(0, 0, 140, 49, new Color(11, 11, 11, 159).getRGB());
        rect(26, 40.5F, 111, 6, base.darker().darker().darker().getRGB());
        rect(26, 40.5F, (float) Math.max(barA - 5, 0), 6, color);
        rect((float) Math.max(26 + barA - 5, 16), 40.5F, 5, 6, base.darker().getRGB());
        scaledText(hearts(target), 26, 18.5F, 2, color);
        mc.fontRendererObj.drawStringWithShadow(target.getName(), 28, 5, -1);
        entity(12, 43, 20, 310, 0, target);
    }

    private void astolfoO(EntityLivingBase target) {
        int color = healthColor(target, 1);
        int dark = new Color(color).darker().darker().getRGB();

        rect(0, 0, 200, 70, new Color(11, 11, 11, 159).getRGB());
        rect(3, 61.5F, 194, 6, dark);
        rect(3, 61.5F, (float) barA, 6, color);
        scaledText(hearts(target), 46, 19, 2, color);
        mc.fontRendererObj.drawStringWithShadow(target.getName(), 48, 5, -1);

        if (!ServerUtils.isOnHypixel()) {
            int ping = ping(target);
            if (ping > 0) scaledText(ping + "ms", 46, 39, 1.5F, new Color(150, 150, 150).getRGB());
        }

        int[] rows = {46, 31, 15, 1};
        for (int i = 1; i < 5; i++) {
            ItemStack stack = target.getEquipmentInSlot(i);
            if (stack == null) continue;
            item(stack, 182, rows[i - 1], true);
            String tag = protectionTag(stack);
            if (tag != null) {
                scaledText(tag, 178 - mc.fontRendererObj.getStringWidth(tag) / 2F, rows[i - 1] + 6, 0.5F, -8070169);
            }
        }
        entity(18, 57, 27, 310, 0, target);
    }

    private void astolfoOld(EntityLivingBase target) {
        int color = healthColor(target, 1);

        rect(0, 0, 127, 45, 0xAA000000);
        mc.fontRendererObj.drawStringWithShadow(target.getName(), 28, 4, -1);
        rect(28, 14, (float) barA, 12, color);
        mc.fontRendererObj.drawStringWithShadow(String.valueOf(round(target.getHealth() / 2F, 2)), 65, 14, color);
        entity(14, 41, 18, target.rotationYaw, -target.rotationPitch, target);
    }

    private void novoline(EntityLivingBase target) {
        int color = healthColor(target, 1);

        bordered(0, 0, 130, 40, 1, new Color(45, 45, 45).getRGB(), new Color(66, 66, 66).getRGB());
        rect(1, 1, 32, 38, new Color(58, 58, 62).getRGB());
        entity(16, 35, 15, target.rotationYaw, -target.rotationPitch, target);
        mc.fontRendererObj.drawString(target.getName(), 37, 5, -1, false);
        mc.fontRendererObj.drawString(round(target.getHealth(), 1) + " §c❤", 37, 28.5F, -1, false);
        bordered(37, 15, 88, 10, 1, new Color(50, 50, 50).getRGB(), new Color(56, 56, 56).getRGB());
        rect(38, 16, (float) barB, 8, new Color(color).darker().getRGB() | 0xFF000000);
        rect(38, 16, (float) barA, 8, color);
    }

    private void novolineO(EntityLivingBase target) {
        rect(0, 0, novolineOWidth(target), 43, 0x67000000);
        entity(12, 35, 16, target.rotationYaw, -target.rotationPitch, target);
        mc.fontRendererObj.drawStringWithShadow(target.getName(), 25, 5.5F, -1);
        rect(0, 41, (float) barA, 2, healthColor(target, 1));

        int x = 3;
        for (int i = 0; i < 5; i++) {
            ItemStack stack = target.getEquipmentInSlot(4 - i);
            if (stack != null) {
                x += 19;
                item(stack, x, 18, false);
            }
        }
    }

    private void remix(EntityLivingBase target) {
        int color = healthColor(target, 1);
        float end = 130 * fraction(target);

        bordered(0, 0, 140, 49, 2, -15592942, -14935012);
        rect(5, 40, 130, 5, -9892341);
        rect(5, 40, end, 5, color);
        if (end <= 129) rect(5 + end, 40, 1, 5, new Color(color).darker().getRGB());
        mc.fontRendererObj.drawStringWithShadow(target.getName(), 40, 5, -1);

        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(target.getUniqueID());
        if (info != null) {
            int ping = info.getResponseTime();
            String text = ping + "ms";
            scaledText(text, 135 - mc.fontRendererObj.getStringWidth(text) / 4F, 28, 0.5F, -1);

            int row = ping < 0 ? 5 : ping < 150 ? 0 : ping < 300 ? 1 : ping < 600 ? 2 : ping < 1000 ? 3 : 4;
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(Gui.icons);
            Gui.drawModalRectWithCustomSizedTexture(125, 17, 0, 176 + row * 8, 10, 8, 256, 256);
        }

        rect(5, 4.5F, 32, 32, -7500403);
        face(target, 6, 6, 30);

        float slotX = 40;
        for (int i = 0; i < 4; i++) {
            bordered(slotX, 15, 18, 18, 1, -15856114, -11776948);
            slotX += 19;
        }
        rect(41, 35, 94, 2, -16777092);
        rect(41, 35, 94F * target.getTotalArmorValue() / 20F, 2, -16776961);

        for (int i = 1; i < 5; i++) {
            ItemStack stack = target.getEquipmentInSlot(5 - i);
            if (stack != null) item(stack, 41 + 19 * (i - 1), 16, true);
        }
    }

    private void custom(EntityLivingBase target) {
        float width = customWidth(target);
        rect(0, 0, width, 43, new Color(0, 0, 0, 120).getRGB());
        mc.fontRendererObj.drawStringWithShadow("Name: " + target.getName(), 32, 4, Color.gray.getRGB());

        for (int i = 1; i < 5; i++) {
            ItemStack stack = target.getEquipmentInSlot(i);
            if (stack != null) item(stack, 32 + 18 * (i - 1), 14, true);
        }

        entity(15, 32, 15, target.rotationYaw, -target.rotationPitch, target);
        bordered(3, 34, width - 6, 6, 0.5F, new Color(0).getRGB(), new Color(35, 35, 35).getRGB());
        rect(3.5F, 34.5F, (float) barA, 5, healthColor(target, 0.75F));
    }

    private void nostalgia(EntityLivingBase target) {
        float width = nostalgiaWidth(target);
        FontRenderer font = mc.fontRendererObj;

        bordered(0, 0, width, 41, 0.5F, -5592406, -2013265920);
        font.drawStringWithShadow(nostalgiaName(target), 3, 3, -1);
        font.drawStringWithShadow(nostalgiaPing(target), 3, 12.5F, -1);
        font.drawStringWithShadow(nostalgiaDirection(target), 3, 22, -1);
        rect(3, 32.5F, width - 20, 4.5F, Color.RED.brighter().brighter().getRGB());
        rect(3, 32.5F, (width - 20) * fraction(target), 4.5F, Color.GREEN.brighter().brighter().getRGB());

        double half = round(target.getHealth() / 2F, 1);
        String health = half == Math.round(half) ? String.valueOf(Math.round(half)) : String.valueOf(half);
        font.drawStringWithShadow(health.replace(".", ","), width - 15, 32, healthColor(target, 1));
    }

    private static String nostalgiaName(EntityLivingBase target) {
        return "Name: §b" + target.getName();
    }

    private static String nostalgiaPing(EntityLivingBase target) {
        return "Ping: §6" + Math.max(ping(target), 1);
    }

    private static String nostalgiaDirection(EntityLivingBase target) {
        return "Direction: §e" + target.getHorizontalFacing();
    }

    private static float customWidth(EntityLivingBase target) {
        return Math.max(110, 32 + mc.fontRendererObj.getStringWidth("Name: " + target.getName()) + 4);
    }

    private static String moonHealth(EntityLivingBase target) {
        return "Health: " + round(target.getHealth(), 1);
    }

    private static float moonWidth(EntityLivingBase target) {
        return 45 + Math.max(FontProcess.getStyledFont("sans", 25, Font.PLAIN).getStringWidth(target.getName()),
                FontProcess.getStyledFont("sans", 18, Font.PLAIN).getStringWidth(moonHealth(target)));
    }

    private static float novolineOWidth(EntityLivingBase target) {
        float addition = 0;
        for (int i = 0; i < 5; i++) {
            if (target.getEquipmentInSlot(4 - i) != null) addition += 15;
        }
        return Math.max(50 + addition, 40 + mc.fontRendererObj.getStringWidth(target.getName()));
    }

    private static float nostalgiaWidth(EntityLivingBase target) {
        FontRenderer font = mc.fontRendererObj;
        int text = Math.max(font.getStringWidth(nostalgiaName(target)),
                Math.max(font.getStringWidth(nostalgiaPing(target)), font.getStringWidth(nostalgiaDirection(target))));
        return Math.max(100, text + 8);
    }

    private static float fraction(EntityLivingBase target) {
        float health = target.getHealth();
        if (Float.isNaN(health)) return 0;
        return Math.max(0, Math.min(health, target.getMaxHealth()) / target.getMaxHealth());
    }

    private static int healthColor(EntityLivingBase target, float brightness) {
        return Color.HSBtoRGB(fraction(target) / 3F, 1F, brightness) | 0xFF000000;
    }

    private static String hearts(EntityLivingBase target) {
        return Float.isNaN(target.getHealth()) ? "NaN ❤" : round(target.getHealth() / 2F, 1) + " ❤";
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private static int ping(EntityLivingBase target) {
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(target.getUniqueID());
        return info == null ? 0 : info.getResponseTime();
    }

    private static String protectionTag(ItemStack stack) {
        NBTTagList enchants = stack.getEnchantmentTagList();
        if (enchants == null) return null;
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        for (int i = 0; i < enchants.tagCount(); i++) {
            int id = enchants.getCompoundTagAt(i).getShort("id");
            int level = enchants.getCompoundTagAt(i).getShort("lvl");
            if (Enchantment.getEnchantmentById(id) == Enchantment.protection) {
                return "protection " + (level >= 1 && level <= 10 ? numerals[level - 1] : String.valueOf(level));
            }
        }
        return null;
    }

    private static double ease(double from, double to, double speed) {
        return from + (to - from) * Math.max(0, Math.min(speed, 1));
    }

    private static double linear(double from, double to, double speed) {
        if (Math.abs(to - from) <= speed) return to;
        return from + (from < to ? speed : -speed);
    }

    private static void rect(float x, float y, float width, float height, int color) {
        if (width > 0 && height > 0) RenderUtils.drawRect(x, y, width, height, new Color(color, true));
    }

    private static void bordered(float x, float y, float width, float height, float line, int border, int fill) {
        rect(x, y, width, height, fill);
        rect(x, y, width, line, border);
        rect(x, y, line, height, border);
        rect(x + width - line, y, line, height, border);
        rect(x, y + height - line, width, line, border);
    }

    private static void scaledText(String text, float x, float y, float scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        mc.fontRendererObj.drawStringWithShadow(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private static void item(ItemStack stack, int x, int y, boolean overlay) {
        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(stack, x, y);
        if (overlay) mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private static void face(EntityLivingBase target, int x, int y, int size) {
        if (!(target instanceof AbstractClientPlayer player)) return;
        mc.getTextureManager().bindTexture(player.getLocationSkin());
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
    }

    private static void entity(int x, int y, int scale, float yaw, float pitch, EntityLivingBase target) {
        GlStateManager.enableDepth();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.translate(x, y, 50);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180, 0, 0, 1);

        float offset = target.renderYawOffset, rotationYaw = target.rotationYaw, rotationPitch = target.rotationPitch,
                prevHead = target.prevRotationYawHead, head = target.rotationYawHead;

        GlStateManager.rotate(135, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135, 0, 1, 0);
        GlStateManager.rotate(-((float) Math.atan(pitch / 40F)) * 20, 1, 0, 0);

        target.renderYawOffset = yaw;
        target.rotationYaw = yaw;
        target.rotationPitch = -pitch;
        target.rotationYawHead = yaw;
        target.prevRotationYawHead = yaw;

        RenderManager manager = mc.getRenderManager();
        manager.setPlayerViewY(180);
        manager.setRenderShadow(false);
        manager.renderEntityWithPosYaw(target, 0, 0, 0, 0, 1);
        manager.setRenderShadow(true);

        target.renderYawOffset = offset;
        target.rotationYaw = rotationYaw;
        target.rotationPitch = rotationPitch;
        target.prevRotationYawHead = prevHead;
        target.rotationYawHead = head;

        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.disableDepth();
    }
}
