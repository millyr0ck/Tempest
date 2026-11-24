package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.StencilUtils;
import rip.tempest.utils.kit.render.RoundedUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class TempestHuds {
    public enum Style {
        Modern(153, 46.75f),
        Legacy(150, 46),

        Trail(150, 46),
        Slim(130, 24),
        Duel(170, 40),
        Armor(150, 46),

        Segments(150, 40);

        public final float width, height;

        Style(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final DecimalFormat HEALTH = new DecimalFormat("0.0");
    private static final Color BOX = new Color(30, 30, 30, 180);
    private static final Color TRACK = new Color(25, 25, 25, 220);
    private static final Color MUTED = new Color(180, 180, 180);
    private static final Color THEIRS = new Color(225, 75, 75);

    private EntityLivingBase shown;

    private float pop;
    private long lastFrame;
    private final Eased health = new Eased(500f, 0f);

    private final Eased share = new Eased(500f, 0.5f);

    private float trail;
    private long trailHeld;

    public void update(EntityLivingBase target) {
        if (target != null) shown = target;

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float step = dt * 1000f / 300f;
        pop = Math.max(0f, Math.min(1f, pop + (target != null ? step : -step)));
        if (pop == 0f) shown = null;
        if (shown == null) return;

        float now01 = health.update(fraction(shown), now);

        if (now01 >= trail) {
            trail = now01;
            trailHeld = now;
        } else if (now - trailHeld > 450) {
            trail += (now01 - trail) * Math.min(1f, dt * 5f);
        }

        float yours = mc.thePlayer != null ? Math.max(0f, mc.thePlayer.getHealth()) : 0f;
        float theirs = Math.max(0f, shown.getHealth());
        share.update(yours + theirs > 0 ? yours / (yours + theirs) : 0.5f, now);
    }

    public float popScale() {
        return shown == null ? 0f : (float) (-(Math.cos(Math.PI * pop) - 1) / 2);
    }

    public void render(Style style, float x, float y, float scale, boolean shapeOnly) {
        float popScale = popScale();
        if (popScale <= 0f || shown == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(style.width / 2f, style.height / 2f, 0);
        GlStateManager.scale(popScale, popScale, 1);
        GlStateManager.translate(-style.width / 2f, -style.height / 2f, 0);

        switch (style) {
            case Modern -> {
                GlStateManager.pushMatrix();
                GlStateManager.scale(MODERN_SCALE, MODERN_SCALE, 1);
                modern(style, shapeOnly);
                GlStateManager.popMatrix();
            }
            case Legacy -> legacy(style, shapeOnly);
            case Trail -> trail(style, shapeOnly);
            case Slim -> slim(style, shapeOnly);
            case Duel -> duel(style, shapeOnly);
            case Armor -> armor(style, shapeOnly);
            case Segments -> segments(style, shapeOnly);
        }

        GlStateManager.popMatrix();
    }

    private static final float MODERN_SCALE = 0.85f;

    private void modern(Style style, boolean shapeOnly) {
        float width = style.width / MODERN_SCALE, height = style.height / MODERN_SCALE, padding = 8, avatar = height - padding * 2, avatarRadius = 8;
        RenderUtils.drawRoundedRect(0, 0, width, height, 12, new Color(18, 18, 20, 150));
        if (shapeOnly) return;

        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        float contentX = avatar + padding * 2, contentWidth = width - avatar - padding * 3;

        font("sans", 14, Font.BOLD).drawString(name(shown), contentX, textTop(padding - 1), 0xFFFFFFFF);
        font("sans", 11, Font.PLAIN).drawString(HEALTH.format(shown.getHealth()) + " HP",
                contentX, textTop(padding + 15), new Color(160, 160, 165).getRGB());

        float barY = height - padding - 8;
        RenderUtils.drawRoundedRect(contentX, barY, contentWidth, 8, 4, new Color(35, 35, 40));
        float fill = contentWidth * health.value;
        if (fill > 0) {
            RoundedUtil.drawGradientHorizontal(contentX - 1, barY, fill + 1, 8, Math.min(4, (fill + 1) / 2f), first, second);
        }

        RenderUtils.drawRoundedRect(padding, padding, avatar, avatar, avatarRadius, new Color(45, 45, 50));
        drawRoundedFace(shown, padding, padding, avatar, avatarRadius);
        Glyphs.gradientOutline(padding, padding, avatar, avatar, avatarRadius, 1.5f, first, second, true);
    }

    private void legacy(Style style, boolean shapeOnly) {
        float width = style.width, height = style.height, padding = 3, head = height - padding * 2;
        Glyphs.bar(0, 0, width, height, BOX);
        if (shapeOnly) return;

        float startX = head + padding * 2;
        float barWidth = width - (startX + padding * 2), barHeight = 12;
        float barY = height - (barHeight + padding);

        Glyphs.bar(startX, barY, barWidth, barHeight, TRACK);
        Glyphs.bar(startX, barY, barWidth * health.value, barHeight, ColorProcess.getColor());

        drawFace(shown, padding, padding, head, Color.WHITE);

        font("sfui", 12, Font.PLAIN).drawString(name(shown), startX, textTop(padding), 0xFFFFFFFF);
        font("sfui", 10, Font.PLAIN).drawString(HEALTH.format(shown.getHealth()) + " Health",
                startX, textTop(padding + 14), MUTED.getRGB());
    }

    private void trail(Style style, boolean shapeOnly) {
        float width = style.width, height = style.height, padding = 3, head = height - padding * 2;
        Glyphs.bar(0, 0, width, height, BOX);
        if (shapeOnly) return;

        float startX = head + padding * 2;
        float barWidth = width - (startX + padding * 2), barHeight = 12;
        float barY = height - (barHeight + padding);

        Glyphs.bar(startX, barY, barWidth, barHeight, TRACK);
        Glyphs.bar(startX, barY, barWidth * trail, barHeight, new Color(255, 255, 255, 110));
        Glyphs.bar(startX, barY, barWidth * health.value, barHeight, ColorProcess.getColor());

        drawFace(shown, padding, padding, head, hurtTint(shown));

        float counted = health.value * shown.getMaxHealth();
        font("sfui", 12, Font.PLAIN).drawString(name(shown), startX, textTop(padding), 0xFFFFFFFF);
        font("sfui", 10, Font.PLAIN).drawString(HEALTH.format(counted) + " Health", startX, textTop(padding + 14), MUTED.getRGB());
    }

    private void slim(Style style, boolean shapeOnly) {
        float width = style.width, height = style.height, padding = 3, head = height - padding * 2;
        Glyphs.bar(0, 0, width, height, BOX);
        if (shapeOnly) return;

        CustomFontRenderer font = font("sfui", 10, Font.PLAIN);
        float startX = head + padding * 2, right = width - padding - 1;
        String hp = HEALTH.format(shown.getHealth());
        float hpWidth = font.getStringWidth(hp);

        drawFace(shown, padding, padding, head, Color.WHITE);
        font.drawString(fit(font, name(shown), right - hpWidth - 4 - startX), startX, textTop(2), 0xFFFFFFFF);
        font.drawString(hp, right - hpWidth, textTop(2), ColorProcess.getColor().getRGB());

        float barY = height - padding - 4, barWidth = right - startX;
        Glyphs.bar(startX, barY, barWidth, 3, TRACK);
        Glyphs.bar(startX, barY, barWidth * health.value, 3, ColorProcess.getColor());
    }

    private void duel(Style style, boolean shapeOnly) {
        float width = style.width, height = style.height, padding = 3, head = 22;
        Glyphs.bar(0, 0, width, height, BOX);
        if (shapeOnly || mc.thePlayer == null) return;

        CustomFontRenderer nameFont = font("sfui", 10, Font.PLAIN), hpFont = font("sfui", 9, Font.PLAIN);
        float middle = width / 2f;
        float yours = mc.thePlayer.getHealth(), theirs = shown.getHealth();

        drawFace(mc.thePlayer, padding, padding, head, Color.WHITE);
        drawFace(shown, width - padding - head, padding, head, Color.WHITE);

        float leftX = padding + head + 3, rightX = width - padding - head - 3;

        float nameRoom = middle - 17 - leftX;
        String you = fit(nameFont, name(mc.thePlayer), nameRoom), them = fit(nameFont, name(shown), nameRoom);
        nameFont.drawString(you, leftX, textTop(padding), 0xFFFFFFFF);
        nameFont.drawString(them, rightX - nameFont.getStringWidth(them), textTop(padding), 0xFFFFFFFF);
        String yourHp = HEALTH.format(yours), theirHp = HEALTH.format(theirs);
        hpFont.drawString(yourHp, leftX, textTop(padding + 11), MUTED.getRGB());
        hpFont.drawString(theirHp, rightX - hpFont.getStringWidth(theirHp), textTop(padding + 11), MUTED.getRGB());

        float lead = yours - theirs;
        String leadText = (lead > 0 ? "+" : "") + HEALTH.format(lead);
        int leadColor = lead > 0.05f ? new Color(95, 220, 125).getRGB() : lead < -0.05f ? THEIRS.getRGB() : MUTED.getRGB();
        nameFont.drawString(leadText, middle - nameFont.getStringWidth(leadText) / 2f, textTop(padding + 5), leadColor);

        float barX = padding, barWidth = width - padding * 2, barY = height - padding - 7, split = barX + barWidth * share.value;
        Glyphs.bar(barX, barY, barWidth, 7, TRACK);
        Glyphs.bar(barX, barY, split - barX, 7, ColorProcess.getColor());
        Glyphs.bar(split, barY, barX + barWidth - split, 7, THEIRS);
        Glyphs.bar(split - 0.5f, barY - 1, 1, 9, new Color(255, 255, 255, 210));
    }

    private void armor(Style style, boolean shapeOnly) {
        float width = style.width, height = style.height, padding = 3, head = height - padding * 2;
        Glyphs.bar(0, 0, width, height, BOX);
        if (shapeOnly) return;

        CustomFontRenderer font = font("sfui", 12, Font.PLAIN), small = font("sfui", 10, Font.PLAIN);
        float startX = head + padding * 2, right = width - padding * 2;
        String hp = HEALTH.format(shown.getHealth());
        float hpWidth = small.getStringWidth(hp);

        drawFace(shown, padding, padding, head, Color.WHITE);
        font.drawString(fit(font, name(shown), right - hpWidth - 4 - startX), startX, textTop(padding), 0xFFFFFFFF);
        small.drawString(hp, right - hpWidth, textTop(padding + 1), ColorProcess.getColor().getRGB());

        float barY = height - padding - 6, barWidth = right - startX;
        Glyphs.bar(startX, barY, barWidth, 6, TRACK);
        Glyphs.bar(startX, barY, barWidth * health.value, 6, ColorProcess.getColor());

        List<ItemStack> items = new ArrayList<>();
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack piece = shown.getCurrentArmor(slot);
            if (piece != null) items.add(piece);
        }
        if (shown.getHeldItem() != null) items.add(shown.getHeldItem());

        float iconSize = 12, iconY = padding + 14;
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < items.size(); i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(startX + i * (iconSize + 3), iconY, 0);
            GlStateManager.scale(iconSize / 16f, iconSize / 16f, 1);
            mc.getRenderItem().renderItemAndEffectIntoGUI(items.get(i), 0, 0);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
    }

    private void segments(Style style, boolean shapeOnly) {
        float w = style.width, h = style.height, pad = 3, head = h - pad * 2;
        Glyphs.bar(0, 0, w, h, BOX);
        if (shapeOnly) return;

        drawFace(shown, pad, pad, head, hurtTint(shown));
        Color accent = ColorProcess.getColor();
        CustomFontRenderer font = font("sfui", 12, Font.PLAIN), small = font("sfui", 10, Font.PLAIN);
        float x = head + pad * 2, right = w - pad * 2;
        String hp = HEALTH.format(health.value * shown.getMaxHealth());
        float hpWidth = small.getStringWidth(hp);
        font.drawString(fit(font, name(shown), right - hpWidth - 4 - x), x, textTop(pad), 0xFFFFFFFF);
        small.drawString(hp, right - hpWidth, textTop(pad + 1), accent.getRGB());

        int count = 10;
        float gap = 1.5f, barHeight = 8, barY = h - pad - barHeight;
        float segment = (right - x - gap * (count - 1)) / count;
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            float sx = x + i * (segment + gap);
            float fill = clamp01(health.value * count - i), ghost = clamp01(trail * count - i);
            Glyphs.bar(sx, barY, segment, barHeight, TRACK);
            if (ghost > fill) Glyphs.bar(sx + segment * fill, barY, segment * (ghost - fill), barHeight, new Color(255, 255, 255, 130));
            if (fill > 0f) {
                float shimmer = 0.82f + 0.18f * (float) Math.sin(now / 260.0 - i * 0.55);
                Glyphs.bar(sx, barY, segment * fill, barHeight, brighten(accent, shimmer));
            }
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static Color brighten(Color color, float factor) {
        return new Color(Math.min(255, Math.round(color.getRed() * factor)), Math.min(255, Math.round(color.getGreen() * factor)),
                Math.min(255, Math.round(color.getBlue() * factor)), color.getAlpha());
    }

    private static final class Eased {
        private final float duration;
        private float from, to, value, last = Float.NaN;
        private long start;

        Eased(float duration, float initial) {
            this.duration = duration;
            this.value = this.from = this.to = initial;
        }

        float update(float target, long now) {
            if (target != last) {
                from = value;
                to = target;
                start = now;
                last = target;
            }
            float t = Math.min(1f, (now - start) / duration);
            float eased = t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
            return value = from + (to - from) * eased;
        }
    }

    private static float fraction(EntityLivingBase entity) {
        return Math.max(0f, Math.min(1f, entity.getHealth() / Math.max(1f, entity.getMaxHealth())));
    }

    private static CustomFontRenderer font(String file, float px, int style) {
        return FontProcess.getStyledFont(file, px * 2f, style);
    }

    private static float textTop(float lineTop) {
        return lineTop + 2;
    }

    private static String fit(CustomFontRenderer font, String text, float width) {
        if (font.getStringWidth(text) <= width) return text;
        String cut = text;
        while (cut.length() > 1 && font.getStringWidth(cut + "..") > width) cut = cut.substring(0, cut.length() - 1);
        return cut + "..";
    }

    private static String name(EntityLivingBase target) {
        return target instanceof EntityPlayer ? TargetInterfaceModule.disguiseName(target) : target.getName();
    }

    private static Color hurtTint(EntityLivingBase target) {
        float hurt = Math.max(0f, Math.min(1f, target.hurtTime / 10f)) * 0.55f;
        int other = Math.round(255 * (1f - hurt));
        return new Color(255, other, other);
    }

    private static ResourceLocation skin(EntityLivingBase target) {
        return target instanceof AbstractClientPlayer ? ((AbstractClientPlayer) target).getLocationSkin()
                : DefaultPlayerSkin.getDefaultSkin(target.getUniqueID());
    }

    private static void drawFace(EntityLivingBase target, float x, float y, float size, Color tint) {
        ResourceLocation skin = skin(target);
        drawSkinPart(skin, x, y, size, 8, 8, tint);
        if (!(target instanceof EntityPlayer) || ((EntityPlayer) target).func_175148_a(EnumPlayerModelParts.HAT)) {
            drawSkinPart(skin, x, y, size, 40, 8, tint);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void drawSkinPart(ResourceLocation skin, float x, float y, float size, int u, int v, Color tint) {
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, 1);
        float u0 = u / 64f, v0 = v / 64f, u1 = (u + 8) / 64f, v1 = (v + 8) / 64f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + size, 0).tex(u0, v1).endVertex();
        buffer.pos(x + size, y + size, 0).tex(u1, v1).endVertex();
        buffer.pos(x + size, y, 0).tex(u1, v0).endVertex();
        buffer.pos(x, y, 0).tex(u0, v0).endVertex();
        tessellator.draw();
    }

    private static void drawRoundedFace(EntityLivingBase target, float x, float y, float size, float radius) {
        boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        StencilUtils.checkSetupFBO(mc.getFramebuffer());
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(false, false, false, false);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        RenderUtils.drawRoundedRect(x, y, size, size, radius, Color.WHITE);
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        drawFace(target, x, y, size, Color.WHITE);

        if (!stencilWasEnabled) GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}
