package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.utils.kit.render.RoundedUtil;
import rip.tempest.utils.kit.render.StencilUtil;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.awt.Font;

import static rip.tempest.utils.Util.mc;

public final class StripHud {
    public enum Style {Fill, Line, Stack, Simple, Modern, Badge, Edge}

    private static final long OPEN_MS = 420, TRAIL_HOLD_MS = 300, POP_MS = 1100;
    private static final Color CARD = new Color(12, 12, 15, 194);
    private static final Color POP = new Color(0xFF8A7A), AHEAD = new Color(0x7BE0A0), BEHIND = new Color(0xFFB070), EVEN = new Color(0xC9CCD3);

    private float popAmount, lastHp = -1;
    private long popStart;

    private EntityLivingBase current;
    private float open;
    private boolean forwards;
    private long lastFrame;

    private float health = -1, trail = -1, number = -1, lastFull = -1;
    private long trailHoldUntil;

    public void update(EntityLivingBase target) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;

        if (target != null) {
            if (target != current) {
                health = trail = number = lastFull = lastHp = -1;
                popStart = 0;
            }
            current = target;
            forwards = true;
        } else {
            forwards = false;
        }
        open = Math.max(0, Math.min(1, open + (forwards ? dt : -dt) / OPEN_MS));
        if (!forwards && open <= 0) current = null;
        if (current == null) return;

        float full = fraction(current), hp = healthValue(current);
        if (health < 0) {
            health = trail = lastFull = full;
            number = hp;
        }
        if (full < lastFull - 0.0005F) trailHoldUntil = now + TRAIL_HOLD_MS;
        lastFull = full;
        if (lastHp >= 0 && hp < lastHp - 0.05F) {
            popAmount = lastHp - hp;
            popStart = now;
        }
        lastHp = hp;

        health += (full - health) * ease(dt, 110);
        number += (hp - number) * ease(dt, 90);
        if (Math.abs(hp - number) < 0.05F) number = hp;
        if (trail < health) trail = health;
        else if (now >= trailHoldUntil) trail += (health - trail) * ease(dt, 180);
    }

    private static float ease(float dt, float tau) {
        return 1 - (float) Math.exp(-dt / tau);
    }

    public boolean showing() {
        return current != null;
    }

    public float[] size(Style style) {
        String name = current == null ? "Player" : name(current);

        String hp = "20.0";
        return switch (style) {
            case Fill -> new float[]{Math.max(140, 18 + nameFont().getStringWidth(name) + 10 + numberFont().getStringWidth(hp) + 5), 16};
            case Line -> new float[]{Math.max(120, 5 + nameFont().getStringWidth(name) + 10 + numberFont().getStringWidth(hp) + 5), 14};
            case Stack -> new float[]{Math.max(130, 25 + nameFont().getStringWidth(name) + 10 + numberFont().getStringWidth(hp) + 5), 24};
            case Simple -> new float[]{Math.max(100, Math.max(5 + smallFont().getStringWidth(name) + 5, 5 + bigFont().getStringWidth(hp) + 4 + 40 + 5)), 24};

            case Modern -> new float[]{Math.max(130, 20 + nameFont().getStringWidth(name) + 6 + numberFont().getStringWidth("-10.0") + 4 + numberFont().getStringWidth(hp) + 5), 18};
            case Badge -> new float[]{Math.max(120, 28 + nameFont().getStringWidth(name) + 6), 26};
            case Edge -> new float[]{Math.max(140, 20 + nameFont().getStringWidth(name) + 8 + numberFont().getStringWidth(hp) + 5 + TAG_W + 5), 18};
        };
    }

    private static final float TAG_W = 24, TAG_H = 9;

    private static float radius(Style style) {
        return style == Style.Line ? 2.3F : 3;
    }

    public void render(Style style, float x, float y, boolean shapeOnly) {
        if (current == null) return;
        float t = unfold(open);
        if (t <= 0.01F) return;

        float[] size = size(style);
        float w = size[0], h = size[1], r = radius(style);
        float drawY = y + (1 - t) * 1.5F;
        float shown = w * t;

        if (shapeOnly) {
            RoundedUtil.drawRound(x, drawY, Math.max(r * 2, shown), h, r, alpha(ColorProcess.getColor(), t));
            return;
        }

        RenderUtils.startScissor(x, drawY - 1, shown, h + 2);
        float content = Math.max(0, Math.min(1, (t - 0.3F) / 0.7F));
        switch (style) {
            case Fill -> drawFill(x, drawY, w, h, content);
            case Line -> drawLine(x, drawY, w, h, content);
            case Stack -> drawStack(x, drawY, w, h, content);
            case Simple -> drawSimple(x, drawY, w, h, content);
            case Modern -> drawModern(x, drawY, w, h, content);
            case Badge -> drawBadge(x, drawY, w, h, content);
            case Edge -> drawEdge(x, drawY, w, h, content);
        }
        RenderUtils.endScissor();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static float unfold(float t) {
        float u = 1 - t;
        return 1 - u * u * u * u;
    }

    private void drawFill(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);

        StencilUtil.initStencilToWrite();
        RoundedUtil.drawRound(x, y, w, h, 3, Color.WHITE);
        StencilUtil.readStencilBuffer(1);
        if (trail > health + 0.001F) Glyphs.bar(x + w * health, y, w * (trail - health), h, new Color(255, 255, 255, 20));
        Glyphs.bar(x, y, w * health, h, alpha(brighten(client), 0.28F));
        Glyphs.bar(x, y + h - 1, w * health, 1, brighten(client));
        StencilUtil.uninitStencilBuffer();

        face(current, x + 2, y + 2, 12, 1.5F);
        text(nameFont(), name(current), x + 18, mid(nameFont(), y, h), Color.WHITE, a);
        String hp = number(number);
        text(numberFont(), hp, x + w - 5 - numberFont().getStringWidth(hp), mid(numberFont(), y, h), Color.WHITE, a);
    }

    private void drawLine(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 2.3F, CARD);

        float rowY = y + 1, rowH = h - 5;
        text(nameFont(), name(current), x + 5, mid(nameFont(), rowY, rowH), Color.WHITE, a);
        String hp = number(number);
        text(numberFont(), hp, x + w - 5 - numberFont().getStringWidth(hp), mid(numberFont(), rowY, rowH), client, a);

        bar(x + 5, y + h - 4, w - 10, 1, client, 30);
    }

    private void drawStack(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);

        face(current, x + 3, y + 3, 18, 1.7F);
        float textX = x + 25;
        text(nameFont(), name(current), textX, mid(nameFont(), y + 3, 9), Color.WHITE, a);
        String hp = number(number);
        text(numberFont(), hp, x + w - 5 - numberFont().getStringWidth(hp), mid(numberFont(), y + 3, 9), client, a);

        bar(textX, y + 15, w - (textX - x) - 5, 3, client, 18);
    }

    private void drawSimple(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);

        text(smallFont(), name(current), x + 5, mid(smallFont(), y + 3, 6), new Color(0xC9CCD3), a);
        String hp = number(number);
        text(bigFont(), hp, x + 5, mid(bigFont(), y + 11, 10), Color.WHITE, 1);

        float barX = x + 5 + bigFont().getStringWidth("20.0") + 4;
        bar(barX, y + 15, x + w - 5 - barX, 2, client, 20);
    }

    private void drawModern(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);
        face(current, x + 2.5F, y + 2.5F, 13, 1.4F);
        text(nameFont(), name(current), x + 20, mid(nameFont(), y, h), Color.WHITE, a);

        String hp = number(number);
        float hpX = x + w - 5 - numberFont().getStringWidth(hp);
        text(numberFont(), hp, hpX, mid(numberFont(), y, h), client, a);

        long since = System.currentTimeMillis() - popStart;
        if (popStart > 0 && since < POP_MS) {
            float t = since / (float) POP_MS;
            float alpha = t < 0.15F ? t / 0.15F : t > 0.7F ? 1 - (t - 0.7F) / 0.3F : 1;
            float rise = t < 0.15F ? 2 * (1 - t / 0.15F) : t > 0.7F ? -3 * (t - 0.7F) / 0.3F : -1 * (t - 0.15F) / 0.55F;
            String pop = "-" + number(popAmount);
            text(numberFont(), pop, hpX - 4 - numberFont().getStringWidth(pop), mid(numberFont(), y, h) + rise, POP, a * alpha);
        }

        StencilUtil.initStencilToWrite();
        RoundedUtil.drawRound(x, y, w, h, 3, Color.WHITE);
        StencilUtil.readStencilBuffer(1);
        Glyphs.bar(x, y + h - 1, w * health, 1, brighten(client));
        StencilUtil.uninitStencilBuffer();
    }

    private void drawBadge(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);

        float fx = x + 3, fy = y + 3, fs = 20;
        face(current, fx, fy, fs, 2);

        StencilUtil.initStencilToWrite();
        RoundedUtil.drawRound(fx, fy, fs, fs, 2, Color.WHITE);
        StencilUtil.readStencilBuffer(1);
        Glyphs.bar(fx, fy + fs - 7, fs, 7, new Color(10, 10, 12, 210));
        StencilUtil.uninitStencilBuffer();
        String hp = number(number);
        text(tagFont(), hp, fx + (fs - tagFont().getStringWidth(hp)) / 2F, mid(tagFont(), fy + fs - 7, 7), Color.WHITE, 1);

        float textX = x + 28;
        text(nameFont(), name(current), textX, mid(nameFont(), y + 4, 9), Color.WHITE, a);
        bar(textX, y + 17, w - (textX - x) - 5, 2, client, 20);
    }

    private void drawEdge(float x, float y, float w, float h, float a) {
        Color client = ColorProcess.getColor();
        RoundedUtil.drawRound(x, y, w, h, 3, CARD);
        face(current, x + 2.5F, y + 2.5F, 13, 1.4F);
        text(nameFont(), name(current), x + 20, mid(nameFont(), y, h), Color.WHITE, a);

        float tagX = x + w - 5 - TAG_W, tagY = y + (h - TAG_H) / 2F;
        String hp = number(number);
        text(numberFont(), hp, tagX - 5 - numberFont().getStringWidth(hp), mid(numberFont(), y, h), Color.WHITE, a);

        float lead = mc.thePlayer == null || current == mc.thePlayer ? 0 : healthValue(mc.thePlayer) - healthValue(current);
        String word = Math.abs(lead) < 1 ? "even" : lead > 0 ? "ahead" : "behind";
        Color c = Math.abs(lead) < 1 ? EVEN : lead > 0 ? AHEAD : BEHIND;
        RoundedUtil.drawRound(tagX, tagY, TAG_W, TAG_H, 1.7F, alpha(c, Math.abs(lead) < 1 ? 0.12F : 0.18F));
        text(tagFont(), word, tagX + (TAG_W - tagFont().getStringWidth(word)) / 2F, mid(tagFont(), tagY, TAG_H), c, a);

        StencilUtil.initStencilToWrite();
        RoundedUtil.drawRound(x, y, w, h, 3, Color.WHITE);
        StencilUtil.readStencilBuffer(1);
        Glyphs.bar(x, y + h - 1, w * health, 1, brighten(client));
        StencilUtil.uninitStencilBuffer();
    }

    private void bar(float x, float y, float width, float height, Color client, int trackAlpha) {
        Glyphs.bar(x, y, width, height, new Color(255, 255, 255, trackAlpha));
        if (trail > health + 0.001F) Glyphs.bar(x + width * health, y, width * (trail - health), height, new Color(255, 255, 255, 85));
        if (health > 0.001F) Glyphs.bar(x, y, width * health, height, brighten(client));
    }

    private Color brighten(Color c) {
        float hurt = hurt(current) * 0.35F;
        return new Color(Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * hurt)),
                Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * hurt)),
                Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * hurt)));
    }

    private static float hurt(EntityLivingBase target) {
        return target.maxHurtTime > 0 ? Math.max(0, target.hurtTime) / (float) target.maxHurtTime : 0;
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
        float hurt = hurt(target);
        GlStateManager.color(1, 1 - hurt * 0.5F, 1 - hurt * 0.5F, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(size / 8F, size / 8F, 1);
        Gui.drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 8, 8, 64, 64);

        Gui.drawScaledCustomSizeModalRect(0, 0, 40, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);

        StencilUtil.uninitStencilBuffer();
    }

    private static float mid(CustomFontRenderer font, float y, float h) {
        return y + (h - font.getHeight()) / 2F;
    }

    private static void text(CustomFontRenderer font, String s, float x, float y, Color color, float a) {
        if (a < 0.03F) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        int alpha = Math.max(5, Math.round(255 * a));
        font.drawString(s, x, y, (alpha << 24) | (color.getRGB() & 0xFFFFFF));
    }

    private static String number(float hp) {
        return String.format("%.1f", Math.max(0, hp));
    }

    private static String name(EntityLivingBase target) {
        if (target instanceof EntityPlayer) return TargetInterfaceModule.disguiseName(target);
        return target.getName();
    }

    private static CustomFontRenderer nameFont() {
        return FontProcess.getStyledFont("manropebold", 16, Font.PLAIN);
    }

    private static CustomFontRenderer numberFont() {
        return FontProcess.getStyledFont("manropeextrabold", 15, Font.PLAIN);
    }

    private static CustomFontRenderer smallFont() {
        return FontProcess.getStyledFont("manropebold", 12, Font.PLAIN);
    }

    private static CustomFontRenderer bigFont() {
        return FontProcess.getStyledFont("manropeextrabold", 20, Font.PLAIN);
    }

    private static CustomFontRenderer tagFont() {
        return FontProcess.getStyledFont("manropeextrabold", 11, Font.PLAIN);
    }

    private static float healthValue(EntityLivingBase target) {
        return target.getHealth() + target.getAbsorptionAmount();
    }

    private static float fraction(EntityLivingBase target) {
        float absorption = target.getAbsorptionAmount();
        return Math.max(0, Math.min(1, (target.getHealth() + absorption) / Math.max(1, target.getMaxHealth() + absorption)));
    }

    private static Color alpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(c.getAlpha() * a))));
    }
}
