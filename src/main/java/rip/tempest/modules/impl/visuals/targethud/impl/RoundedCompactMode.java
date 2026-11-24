package rip.tempest.modules.impl.visuals.targethud.impl;

import rip.tempest.modules.impl.visuals.targethud.FontAdapter;
import rip.tempest.modules.impl.visuals.targethud.TargetHudMode;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Locale;

public final class RoundedCompactMode extends TargetHudMode {
    private static final int WIDTH = 112;
    private static final int HEIGHT = 26;
    private static final float PADDING = 4f;
    private static final float FACE_SIZE = 18f;
    private static final float GAP_FACE_TEXT = 5f;
    private static final float BAR_HEIGHT = 2.5f;
    private static final float RADIUS = 7f;

    private final TargetHuds huds;

    public RoundedCompactMode(TargetHuds huds) {
        super("Rounded Compact");
        this.huds = huds;
    }

    @Override
    public int getMinWidth() { return WIDTH; }

    @Override
    public int getHudHeight() { return HEIGHT; }

    @Override
    public int getLabelHeight() { return 0; }

    @Override
    public void draw(EntityLivingBase target, TargetHuds.TargetState state, double x, double y, long now, float delta) {
        FontAdapter nameFont = RoundedStyle.bold(huds, 16);
        FontAdapter bodyFont = RoundedStyle.regular(huds, 16);
        if (nameFont == null || bodyFont == null) return;

        float healthPercentage = RoundedStyle.smoothHealth(target, state, delta);
        float alpha = state.alpha;
        Color accent = RenderUtils.applyOpacity(ColorProcess.getColor(), alpha);

        RenderUtils.drawRoundOutline((float) x, (float) y, WIDTH, HEIGHT, RADIUS, -0.4f,
                RenderUtils.applyOpacity(RoundedStyle.BG, alpha), accent);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        float faceY = (HEIGHT - FACE_SIZE) / 2f;
        RoundedStyle.drawFace(huds, target, PADDING, faceY, FACE_SIZE, FACE_SIZE / 2f,
                RoundedStyle.hurtTint(state, now), alpha);

        float textX = PADDING + FACE_SIZE + GAP_FACE_TEXT;
        float right = WIDTH - PADDING - 2f;

        String health = String.format(Locale.US, "%.1f", state.displayHealth);
        float healthWidth = bodyFont.width(health);
        String name = RoundedStyle.fit(nameFont, target.getName(), right - textX - healthWidth - 4f);
        nameFont.draw(name, textX, faceY, RenderUtils.applyOpacity(Color.WHITE, alpha).getRGB());
        bodyFont.draw(health, right - healthWidth, faceY, accent.getRGB());

        float barY = faceY + FACE_SIZE - BAR_HEIGHT;
        float barWidth = right - textX;
        RenderUtils.drawRoundedRect(textX, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2f,
                RenderUtils.applyOpacity(RoundedStyle.BAR_BG, alpha));
        if (healthPercentage > 0f) {
            float progress = Math.min(barWidth, Math.max(BAR_HEIGHT, barWidth * healthPercentage));
            RenderUtils.drawRoundedRect(textX, barY, progress, BAR_HEIGHT, BAR_HEIGHT / 2f, accent);
        }

        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }
}
