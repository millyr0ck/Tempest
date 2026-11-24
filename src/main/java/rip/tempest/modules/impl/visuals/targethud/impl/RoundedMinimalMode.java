package rip.tempest.modules.impl.visuals.targethud.impl;

import rip.tempest.modules.impl.visuals.targethud.FontAdapter;
import rip.tempest.modules.impl.visuals.targethud.TargetHudMode;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.kit.render.RoundedUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Locale;

public final class RoundedMinimalMode extends TargetHudMode {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 28;
    private static final float PADDING = 7f;
    private static final float RADIUS = 7f;
    private static final float BAR_HEIGHT = 3f;

    private final TargetHuds huds;

    public RoundedMinimalMode(TargetHuds huds) {
        super("Rounded Minimal");
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
        FontAdapter nameFont = RoundedStyle.bold(huds, 18);
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

        float right = WIDTH - PADDING;
        float barY = HEIGHT - PADDING - BAR_HEIGHT;
        float nameY = (barY - nameFont.height()) / 2f + 1f;

        String health = String.format(Locale.US, "%.1f HP", state.displayHealth);
        float healthWidth = bodyFont.width(health);
        bodyFont.draw(health, right - healthWidth, nameY + (nameFont.height() - bodyFont.height()) / 2f,
                RenderUtils.applyOpacity(RoundedStyle.SUB, alpha).getRGB());

        String name = RoundedStyle.fit(nameFont, target.getName(), right - PADDING - healthWidth - 4f);
        nameFont.draw(name, PADDING, nameY, RenderUtils.applyOpacity(Color.WHITE, alpha).getRGB());

        float barWidth = right - PADDING;
        RenderUtils.drawRoundedRect(PADDING, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2f,
                RenderUtils.applyOpacity(RoundedStyle.BAR_BG, alpha));
        if (healthPercentage > 0f) {
            float progress = Math.min(barWidth, Math.max(BAR_HEIGHT, barWidth * healthPercentage));
            RoundedUtil.drawGradientHorizontal(PADDING, barY, progress, BAR_HEIGHT, BAR_HEIGHT / 2f,
                    RenderUtils.applyOpacity(ColorProcess.colors.getFirst(), alpha),
                    RenderUtils.applyOpacity(ColorProcess.colors.getSecond(), alpha));
        }

        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }
}
