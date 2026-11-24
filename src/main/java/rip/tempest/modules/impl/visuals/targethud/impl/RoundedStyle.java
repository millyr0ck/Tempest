package rip.tempest.modules.impl.visuals.targethud.impl;

import rip.tempest.modules.impl.visuals.targethud.FontAdapter;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.StencilUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

final class RoundedStyle {
    static final Color BG = new Color(0, 0, 0, 130);
    static final Color BAR_BG = new Color(255, 255, 255, 40);
    static final Color FACE_PLACEHOLDER = new Color(255, 255, 255, 25);
    static final Color SUB = new Color(190, 190, 190);
    static final Color WIN = new Color(90, 220, 130);
    static final Color LOSE = new Color(235, 85, 85);

    private RoundedStyle() {
    }

    static FontAdapter bold(TargetHuds huds, int size) {
        return FontAdapter.of("sfbold", size, huds.useCustomFont.getValue());
    }

    static FontAdapter regular(TargetHuds huds, int size) {
        return FontAdapter.of("sf", size, huds.useCustomFont.getValue());
    }

    static float smoothHealth(EntityLivingBase target, TargetHuds.TargetState state, float delta) {
        float health = target.isEntityAlive() ? target.getHealth() : 0f;
        if (state.displayHealth < 0f) {
            state.displayHealth = health;
        }
        state.displayHealth += (health - state.displayHealth) * Math.min(1f, delta * 10f);
        return Math.max(0f, Math.min(1f, state.displayHealth / target.getMaxHealth()));
    }

    static float hurtTint(TargetHuds.TargetState state, long now) {
        long elapsed = now - state.hurtAnimStart;
        float hurtProgress = elapsed >= 400 ? 1f : Math.max(0f, elapsed / 400f);
        return hurtProgress < 1f ? (1f - hurtProgress) * 0.7f : 0f;
    }

    static void drawFace(TargetHuds huds, EntityLivingBase target, float x, float y, float size, float radius,
                         float tint, float alpha) {
        if (!(target instanceof AbstractClientPlayer)) {
            RenderUtils.drawRoundedRect(x, y, size, size, radius, RenderUtils.applyOpacity(FACE_PLACEHOLDER, alpha));
            return;
        }

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

        huds.renderPlayerFace((AbstractClientPlayer) target, x, y, size, 1f, tint, alpha);

        if (!stencilWasEnabled) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    static void drawRing(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, Color color) {
        Glyphs.ring(cx, cy, radius, thickness, startDeg, sweepDeg, color);
    }

    static String fit(FontAdapter font, String text, float maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }
}
