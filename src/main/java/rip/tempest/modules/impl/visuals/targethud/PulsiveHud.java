package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.utils.kit.render.RoundedUtil;
import rip.tempest.utils.kit.render.StencilUtil;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.awt.Font;

import static rip.tempest.utils.Util.mc;

public final class PulsiveHud {
    public enum Style {Pulsive, Pulse, PulseSlim, PulseArmor, PulseDuel}

    private static final float RADIUS = 8, HEAD = 23;

    private static final float TITLE_HEIGHT = 8;
    private static final long GROW_MS = 255;
    private static final Color WIN = new Color(0x55FF7F), LOSE = new Color(0xFF5555), EVEN = new Color(0xAAAAAA);

    private EntityLivingBase current;

    private float health = -1, trail = -1;

    private float growth;
    private boolean forwards;
    private long lastFrame;

    public void update(EntityLivingBase target) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;

        if (target != null) {
            if (target != current) health = trail = -1;
            current = target;
            forwards = true;
        } else {
            forwards = false;
        }
        growth = Math.max(0, Math.min(1, growth + (forwards ? dt : -dt) / GROW_MS));
        if (!forwards && growth <= 0) current = null;

        if (current != null) {
            float full = fraction(current);
            if (health < 0) health = trail = full;

            health += (full - health) * (1 - (float) Math.pow(0.95, dt * 60 / 1000F));
            trail += (full - trail) * (1 - (float) Math.pow(0.985, dt * 60 / 1000F));
            if (trail < health) trail = health;
        }
    }

    public boolean showing() {
        return current != null;
    }

    public float[] size(Style style) {
        String name = current == null ? "Player" : name(current);
        return switch (style) {
            case Pulsive -> new float[]{66 + flashy().getStringWidth(current == null ? "Player" : current.getName()), 40};
            case Pulse -> new float[]{Math.max(112, 44 + medium().getStringWidth(name) + 8), 40};
            case PulseDuel -> new float[]{Math.max(118, 44 + Math.max(medium().getStringWidth(name), regular().getStringWidth(duelText()[0])) + 8), 40};
            case PulseSlim -> new float[]{Math.max(104, 32 + medium().getStringWidth(name) + 10 + medium().getStringWidth(healthNumber()) + 6), 28};
            case PulseArmor -> new float[]{Math.max(124, 44 + medium().getStringWidth(name) + 10 + medium().getStringWidth(healthNumber()) + 6), 44};
        };
    }

    private float scale() {
        return growth * growth * (3 - 2 * growth);
    }

    public void render(Style style, float x, float y, boolean shapeOnly) {
        if (current == null) return;
        float s = scale();
        if (s <= 0.01F) return;

        float[] size = size(style);
        float w = size[0], h = size[1];
        float radius = style == Style.Pulsive ? RADIUS : 6;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + w / 2, y + h / 2, 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-w / 2, -h / 2, 0);

        if (shapeOnly) {
            RoundedUtil.drawRound(0, 0, w, h, radius, alpha(ColorProcess.getColor(), s));
        } else {
            switch (style) {
                case Pulsive -> drawPulsive(w, h, s);
                case Pulse -> drawPulse(w, h, s, false);
                case PulseDuel -> drawPulse(w, h, s, true);
                case PulseSlim -> drawSlim(w, h, s);
                case PulseArmor -> drawArmor(w, h, s);
            }
        }
        GlStateManager.popMatrix();
    }

    private void drawPulsive(float w, float h, float s) {
        EntityLivingBase target = current;
        Color client = ColorProcess.getColor();

        RoundedUtil.drawGradientVertical(0, 0, w, h, RADIUS, alpha(client, 0.1F), alpha(client.darker(), 0.1F));
        RoundedUtil.drawRound(0, 0, w, h, RADIUS, new Color(0, 0, 0, 100));

        float bar = (w - 8) * health;
        if (bar > 0) Glyphs.bar(4, h - 10, bar, 4, alpha(client, s));

        face(target, 4, 4, HEAD, 6);

        text(medium(), name(target), 31, 9, Color.WHITE);
        text(regular(), "Health: " + Math.round(target.getHealth() * 10) / 10.0, 31, TITLE_HEIGHT + 11, Color.WHITE);
    }

    private void drawPulse(float w, float h, float s, boolean duel) {
        EntityLivingBase target = current;
        card(w, h, 6, true);

        face(target, 5, 5, 26, 5);
        text(medium(), name(target), 37, 8, Color.WHITE);

        if (duel) {
            String[] duelText = duelText();
            Color c = duelText[1].equals("w") ? WIN : duelText[1].equals("l") ? LOSE : EVEN;
            text(regular(), duelText[0], 37, 20, c);
        } else {
            String hp = healthNumber() + " HP";
            text(regular(), hp, 37, 20, Color.WHITE);
            String dist = String.format(" %.1fm", mc.thePlayer.getDistanceToEntity(target));
            text(regular(), dist, 37 + regular().getStringWidth(hp) + 2, 20, new Color(150, 150, 150));
        }

        bar(5, h - 7, w - 10, 3, s);
    }

    private void drawSlim(float w, float h, float s) {
        EntityLivingBase target = current;
        card(w, h, 6, true);

        face(target, 4, 4, 20, 4);
        text(medium(), name(target), 29, 6, Color.WHITE);
        String hp = healthNumber();
        text(medium(), hp, w - 6 - medium().getStringWidth(hp), 6, ColorProcess.getColor());

        bar(29, 18, w - 29 - 6, 3, s);
    }

    private void drawArmor(float w, float h, float s) {
        EntityLivingBase target = current;
        card(w, h, 6, true);

        face(target, 5, 5, 26, 5);
        text(medium(), name(target), 37, 7, Color.WHITE);
        String hp = healthNumber();
        text(medium(), hp, w - 6 - medium().getStringWidth(hp), 7, ColorProcess.getColor());

        ItemStack[] gear = {target.getHeldItem(), target.getEquipmentInSlot(4), target.getEquipmentInSlot(3),
                target.getEquipmentInSlot(2), target.getEquipmentInSlot(1)};
        float itemX = 37, itemY = 18;
        for (int i = 0; i < gear.length; i++) {
            RoundedUtil.drawRound(itemX + i * 13, itemY, 11.5F, 11.5F, 2, new Color(0, 0, 0, 70));
        }
        if (s > 0.5F) {
            RenderHelper.enableGUIStandardItemLighting();
            for (int i = 0; i < gear.length; i++) {
                if (gear[i] == null) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(itemX + i * 13 + 0.75F, itemY + 0.75F, 0);
                GlStateManager.scale(0.625F, 0.625F, 1);
                mc.getRenderItem().renderItemAndEffectIntoGUI(gear[i], 0, 0);
                GlStateManager.popMatrix();
            }
            RenderHelper.disableStandardItemLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1, 1, 1, 1);
        }

        bar(5, h - 7, w - 10, 3, s);
    }

    private static void card(float w, float h, float radius, boolean tint) {
        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        RoundedUtil.drawRound(0, 0, w, h, radius, new Color(10, 10, 12, 150));
        if (tint) RoundedUtil.drawGradientHorizontal(0, 0, w, h, radius, alpha(first, 0.12F), alpha(second, 0.06F));
    }

    private void bar(float x, float y, float width, float height, float s) {
        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        float r = height / 2;
        RoundedUtil.drawRound(x, y, width, height, r, new Color(0, 0, 0, 90));
        if (trail > 0.01F) RoundedUtil.drawRound(x, y, Math.max(height, width * trail), height, r, alpha(first.darker().darker(), 0.8F * s));
        if (health > 0.01F) RoundedUtil.drawGradientHorizontal(x, y, Math.max(height, width * health), height, r, alpha(first, s), alpha(second, s));
    }

    private static void face(EntityLivingBase target, float x, float y, float size, float radius) {
        StencilUtil.initStencilToWrite();
        RoundedUtil.drawRound(x, y, size, size, radius, Color.WHITE);
        StencilUtil.readStencilBuffer(1);

        ResourceLocation skin = target instanceof AbstractClientPlayer ? ((AbstractClientPlayer) target).getLocationSkin()
                : DefaultPlayerSkin.getDefaultSkin(target.getUniqueID());
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();

        float hurt = target.maxHurtTime > 0 ? Math.max(0, target.hurtTime) / (float) target.maxHurtTime : 0;
        GlStateManager.color(1, 1 - hurt * 0.5F, 1 - hurt * 0.5F, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(size / 8F, size / 8F, 1);
        Gui.drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);

        StencilUtil.uninitStencilBuffer();
    }

    private static void text(CustomFontRenderer font, String s, float x, float y, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        font.drawString(s, x, y, color.getRGB() | 0xFF000000);
    }

    private String healthNumber() {
        if (current == null) return "20.0";
        return String.valueOf(Math.round((current.getHealth() + current.getAbsorptionAmount()) * 10) / 10.0);
    }

    private String[] duelText() {
        if (current == null || mc.thePlayer == null) return new String[]{"Even", "e"};
        float diff = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) - (current.getHealth() + current.getAbsorptionAmount());
        float rounded = Math.round(Math.abs(diff) * 10) / 10F;
        if (current == mc.thePlayer || rounded == 0) return new String[]{"Even", "e"};
        String by = rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
        return diff > 0 ? new String[]{"Winning by " + by, "w"} : new String[]{"Losing by " + by, "l"};
    }

    private static String name(EntityLivingBase target) {
        if (target instanceof EntityPlayer) return TargetInterfaceModule.disguiseName(target);
        return target.getName();
    }

    private static CustomFontRenderer medium() {
        return FontProcess.getStyledFont("museosansbold", 18, Font.PLAIN);
    }

    private static CustomFontRenderer regular() {
        return FontProcess.getStyledFont("museosans", 16, Font.PLAIN);
    }

    private static CustomFontRenderer flashy() {
        return FontProcess.getStyledFont("cobblersans", 18, Font.PLAIN);
    }

    private static float fraction(EntityLivingBase target) {
        float absorption = target.getAbsorptionAmount();
        return Math.max(0, Math.min(1, (target.getHealth() + absorption) / Math.max(1, target.getMaxHealth() + absorption)));
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(255 * a))));
    }
}
