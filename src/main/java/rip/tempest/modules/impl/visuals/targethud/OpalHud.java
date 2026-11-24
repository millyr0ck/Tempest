package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.kit.render.RoundedUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

public final class OpalHud {
    private static final DecimalFormat HEALTH = new DecimalFormat("0.#");

    private static final float PADDING = 3, HEAD_OFFSET = 22.5F, EQUIPMENT_WIDTH = 55, HEIGHT = PADDING * 2 + 25.5F;

    private static final float ASCENT = 0.96F;
    private static final Color HEART_GOLD = new Color(0xFFC247), HEART_RED = new Color(0xFF5555);
    private static final Map<Integer, String> ENCHANTMENTS = new HashMap<>();

    static {
        String[][] names = {
                {"0", "Pr"}, {"1", "Fp"}, {"2", "Ff"}, {"3", "Bp"}, {"4", "Pp"}, {"5", "Re"}, {"6", "Aa"}, {"7", "Th"},
                {"8", "Ds"}, {"16", "Sh"}, {"17", "Sm"}, {"18", "BoA"}, {"19", "Kb"}, {"20", "Fa"}, {"21", "Lo"},
                {"32", "Ef"}, {"33", "St"}, {"34", "Un"}, {"35", "Fo"}, {"48", "Po"}, {"49", "Pu"}, {"50", "Fl"},
                {"51", "In"}, {"61", "Lu"}, {"62", "Lr"}};
        for (String[] n : names) ENCHANTMENTS.put(Integer.parseInt(n[0]), n[1]);
    }

    private EntityLivingBase current;

    private final Anim fade = new Anim(200), health = new Anim(1000);

    public OpalHud() {
        fade.set(1);
    }

    public void update(EntityLivingBase target) {
        if (target != null) {
            if (current != target) health.set(fraction(target));
            current = target;
            fade.set(1);
        } else if (current != null) {
            fade.to(0);
            if (fade.value() <= 0) current = null;
        }
        if (current != null) health.to(fraction(current));
    }

    public boolean showing() {
        return current != null;
    }

    public float[] size() {
        if (current == null) return new float[]{PADDING * 2 + EQUIPMENT_WIDTH + HEAD_OFFSET + 1, HEIGHT};
        float nameWidth = bold().getStringWidth(name(current));
        return new float[]{PADDING * 2 + Math.max(50, Math.max(EQUIPMENT_WIDTH, nameWidth)) + HEAD_OFFSET + 1, HEIGHT};
    }

    public void render(float x, float y, float scale, boolean shapeOnly) {
        if (current == null) return;
        float a = fade.value();
        if (a <= 0.01F) return;

        EntityLivingBase target = current;
        float width = size()[0];

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);

        if (shapeOnly) {
            RenderUtils.drawRoundedRect(0, 0, width, HEIGHT, 4, alpha(Color.BLACK, a));
            GlStateManager.popMatrix();
            return;
        }

        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();

        RenderUtils.drawRoundedRect(0, 0, width, HEIGHT, 4, new Color(9, 9, 9, Math.round(0x80 * a)));

        text(bold(), name(target), PADDING + HEAD_OFFSET, baselineToTop(9, 6), Color.WHITE, a);

        float absorption = target.getAbsorptionAmount();
        float heartWidth = 5;
        String hp = HEALTH.format(target.getHealth() + absorption);
        CustomFontRenderer medium = medium();
        heart(width - PADDING * 2.5F + 0.42F, 24.6F, absorption > 0 ? HEART_GOLD : HEART_RED, a);
        text(medium, hp, width - PADDING - medium.getStringWidth(hp) - heartWidth - 0.25F, baselineToTop(28.5F, 5), Color.WHITE, a);

        float trueHealth = fraction(target), eased = health.value();
        float barWidth = width - PADDING * 2.75F - medium.getStringWidth(hp.length() > 2 ? hp : "88.") - heartWidth;
        float barX = PADDING - 0.125F, barY = 24.75F, barHeight = 4, radius = 5 / 3F;
        RoundedUtil.drawRound(barX, barY, barWidth, barHeight, radius, alpha(darker(second, 0.8F), 0.6F * a));
        if (eased > 0.01F) {
            RoundedUtil.drawGradientHorizontal(barX, barY, eased * barWidth, barHeight, radius,
                    alpha(darker(first, 0.6F), a), alpha(darker(second, 0.6F), a));
        }
        if (trueHealth > 0.01F) {
            RoundedUtil.drawGradientHorizontal(barX, barY, trueHealth * barWidth, barHeight, radius, alpha(first, a), alpha(second, a));
            RoundedUtil.drawGradientVertical(barX, barY, trueHealth * barWidth, barHeight, radius,
                    new Color(0, 0, 0, 0), new Color(0, 0, 0, Math.round(153 * a)));
        }

        float damage = target.maxHurtTime > 0 ? Math.max(0, target.hurtTime) / (float) target.maxHurtTime : 0;
        float shade = 1 - damage * 0.6F;
        face(target, PADDING + 0.25F, PADDING, 19.5F, shade, a);

        ItemStack[] equipment = {target.getHeldItem(), target.getEquipmentInSlot(4), target.getEquipmentInSlot(3),
                target.getEquipmentInSlot(2), target.getEquipmentInSlot(1)};
        for (int i = 0; i < equipment.length; i++) {
            RenderUtils.drawRoundedRect(i * 11.5F + PADDING + HEAD_OFFSET - 0.5F, PADDING + 8.5F, 10.5F, 10.5F, 1,
                    new Color(0, 0, 0, Math.round(51 * a)));
        }
        items(equipment, a);

        GlStateManager.popMatrix();
    }

    private static void items(ItemStack[] equipment, float a) {
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < equipment.length; i++) {
            ItemStack stack = equipment[i];

            if (stack == null || (stack.getItem() instanceof ItemBlock && a < 0.5F)) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(i * 11.6F + PADDING + HEAD_OFFSET - 0.5F, PADDING + 8.5F, 0);
            GlStateManager.scale(0.625F, 0.625F, 1);
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        for (int i = 0; i < equipment.length; i++) {
            String tag = enchantment(equipment[i]);
            if (tag == null) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(i * 11.6F + PADDING + HEAD_OFFSET - 0.5F, PADDING + 8.5F, 0);
            GlStateManager.scale(0.375F, 0.375F, 1);
            mc.fontRendererObj.drawStringWithShadow(tag, 2, 7, 0xFFFFFFFF);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static String enchantment(ItemStack stack) {
        if (stack == null) return null;
        String tag = null;
        for (Map.Entry<Integer, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            String name = ENCHANTMENTS.get(e.getKey());
            if (name != null) tag = name + e.getValue();
        }
        return tag;
    }

    private static void face(EntityLivingBase target, float x, float y, float size, float shade, float a) {
        ResourceLocation skin = target instanceof AbstractClientPlayer ? ((AbstractClientPlayer) target).getLocationSkin()
                : DefaultPlayerSkin.getDefaultSkin(target.getUniqueID());
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, shade, shade, a);
        float u0 = 8 / 64F, v0 = 8 / 64F, u1 = 16 / 64F, v1 = 16 / 64F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + size, 0).tex(u0, v1).endVertex();
        wr.pos(x + size, y + size, 0).tex(u1, v1).endVertex();
        wr.pos(x + size, y, 0).tex(u1, v0).endVertex();
        wr.pos(x, y, 0).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void heart(float x, float y, Color color, float a) {
        float w = 4.16F, h = 3.85F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * a);
        wr.pos(x + w / 2, y + h * 0.55F, 0).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).endVertex();
        for (int i = 0; i <= 48; i++) {
            double t = Math.PI * 2 * i / 48;
            double hx = 16 * Math.pow(Math.sin(t), 3);
            double hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);

            float px = x + (float) ((hx + 16) / 32 * w);
            float py = y + (float) ((12 - hy) / 29 * h);
            wr.pos(px, py, 0).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    private static void text(CustomFontRenderer font, String text, float x, float y, Color color, float a) {
        int alpha = Math.round(255 * a);
        if (alpha < 5) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        font.drawString(text, x, y, (alpha << 24) | (color.getRGB() & 0xFFFFFF));
    }

    private static float baselineToTop(float baseline, float size) {
        return baseline - ASCENT * size + 2;
    }

    private static CustomFontRenderer bold() {
        return FontProcess.getStyledFont("sans", 12, Font.BOLD);
    }

    private static CustomFontRenderer medium() {
        return FontProcess.getStyledFont("sans", 10, Font.PLAIN);
    }

    private static String name(EntityLivingBase target) {
        if (target instanceof EntityPlayer) {
            String shown = TargetInterfaceModule.disguiseName(target);
            if (!shown.equals(target.getName())) return "\247f" + shown;
            return "\247f" + target.getDisplayName().getFormattedText();
        }
        return "\247f" + target.getName();
    }

    private static float fraction(EntityLivingBase target) {
        float absorption = target.getAbsorptionAmount();
        return Math.max(0, Math.min(1, (target.getHealth() + absorption) / Math.max(1, target.getMaxHealth() + absorption)));
    }

    private static Color darker(Color c, float factor) {
        float f = 1 - factor;
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(255 * a))));
    }

    private static final class Anim {
        private final long duration;
        private float from, to, value;
        private long start;

        Anim(long duration) {
            this.duration = duration;
        }

        void set(float v) {
            from = to = value = v;
            start = 0;
        }

        void to(float v) {
            if (v != to) {
                from = value();
                to = v;
                start = System.currentTimeMillis();
            }
        }

        float value() {
            if (start == 0) return value = to;
            float t = Math.min(1F, (System.currentTimeMillis() - start) / (float) duration);
            float eased = t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
            return value = from + (to - from) * eased;
        }
    }
}
