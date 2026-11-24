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

public final class RoundedRingMode extends TargetHudMode {
    private static final int WIDTH = 124;
    private static final int HEIGHT = 40;
    private static final float RADIUS = 8f;
    private static final float CENTER = HEIGHT / 2f;
    private static final float FACE_SIZE = 24f;
    private static final float RING_RADIUS = 15.5f;
    private static final float RING_THICKNESS = 2.2f;
    private static final float GAP_RING_TEXT = 5f;
    private static final float GAP_NAME_SUB = 3f;

    private final TargetHuds huds;

    public RoundedRingMode(TargetHuds huds) {
        super("Rounded Ring");
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

        RoundedStyle.drawRing(CENTER, CENTER, RING_RADIUS, RING_THICKNESS, -90f, 360f,
                RenderUtils.applyOpacity(RoundedStyle.BAR_BG, alpha));
        RoundedStyle.drawRing(CENTER, CENTER, RING_RADIUS, RING_THICKNESS, -90f, 360f * healthPercentage, accent);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float faceStart = CENTER - FACE_SIZE / 2f;
        RoundedStyle.drawFace(huds, target, faceStart, faceStart, FACE_SIZE, FACE_SIZE / 2f,
                RoundedStyle.hurtTint(state, now), alpha);

        float textX = CENTER + RING_RADIUS + RING_THICKNESS / 2f + GAP_RING_TEXT;
        float textWidth = WIDTH - textX - 6f;

        String name = RoundedStyle.fit(nameFont, target.getName(), textWidth);
        String sub = String.format(Locale.US, "%.1f HP · %.1fm", state.displayHealth,
                mc.thePlayer.getDistanceToEntity(target));

        float stackHeight = nameFont.height() + GAP_NAME_SUB + bodyFont.height();
        float textY = (HEIGHT - stackHeight) / 2f;
        nameFont.draw(name, textX, textY, RenderUtils.applyOpacity(Color.WHITE, alpha).getRGB());
        textY += nameFont.height() + GAP_NAME_SUB;
        bodyFont.draw(RoundedStyle.fit(bodyFont, sub, textWidth), textX, textY,
                RenderUtils.applyOpacity(RoundedStyle.SUB, alpha).getRGB());

        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }
}
