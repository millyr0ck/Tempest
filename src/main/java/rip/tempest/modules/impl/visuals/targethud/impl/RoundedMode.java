package rip.tempest.modules.impl.visuals.targethud.impl;

import rip.tempest.modules.impl.visuals.targethud.FontAdapter;
import rip.tempest.modules.impl.visuals.targethud.TargetHudMode;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.StencilUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Locale;

/**
 * Rounded glass card: face, name, slim health bar, HP and distance. Adapted from Yuri's own
 * "Yuri" target HUD (YuriMode, AGPL-3.0), renamed because Yuri's trademark terms reserve its
 * name. Yuri's RoundedUtils calls map onto Tempest's identical RenderUtils ones, and the
 * stencil used to round the face is set up on the framebuffer first.
 */
public final class RoundedMode extends TargetHudMode {
    private final TargetHuds parentModule;

    private static final int WIDTH = 130;
    private static final int HEIGHT = 36;
    private static final float PADDING = 4f;
    private static final float FACE_SIZE = 28f;
    private static final float GAP_FACE_TEXT = 4f;
    private static final float GAP_NAME_BAR = 3f;
    private static final float GAP_BAR_SUB = 3f;
    private static final float BAR_HEIGHT = 3f;
    private static final float RADIUS = 6f;

    private static final Color BG_COLOR = new Color(0, 0, 0, 130);
    private static final Color BAR_BG_COLOR = new Color(255, 255, 255, 40);
    private static final Color FACE_PLACEHOLDER_COLOR = new Color(255, 255, 255, 25);
    private static final Color SUB_BASE_COLOR = new Color(190, 190, 190);

    private float cachedAlpha = -1f;
    private int cachedBaseRGB;
    private Color cachedBgColor;
    private Color cachedAccentColor;
    private Color cachedBarBgColor;
    private Color cachedFacePlaceholderColor;
    private Color cachedWhiteColor;
    private Color cachedSubColor;

    public RoundedMode(TargetHuds parentModule) {
        super("Rounded");
        this.parentModule = parentModule;
    }

    @Override
    public int getMinWidth() { return WIDTH; }

    @Override
    public int getHudHeight() { return HEIGHT; }

    @Override
    public int getLabelHeight() { return 0; }

    @Override
    public void draw(EntityLivingBase targetEntity, TargetHuds.TargetState state,
                     double x, double y, long now, float delta) {
        boolean custom = parentModule.useCustomFont.getValue();
        FontAdapter nameFont = FontAdapter.of("sfbold", 18, custom);
        FontAdapter bodyFont = FontAdapter.of("sf", 16, custom);
        if (nameFont == null || bodyFont == null) return;

        float health = targetEntity.isEntityAlive() ? targetEntity.getHealth() : 0f;
        float maxHealth = targetEntity.getMaxHealth();

        if (state.displayHealth < 0f) {
            state.displayHealth = health;
        }
        state.displayHealth += (health - state.displayHealth) * Math.min(1f, delta * 10f);
        float healthPercentage = Math.max(0f, Math.min(1f, state.displayHealth / maxHealth));

        float alpha = state.alpha;
        Color baseColor = ColorProcess.getColor();
        int baseRGB = baseColor.getRGB();

        if (alpha != cachedAlpha || baseRGB != cachedBaseRGB) {
            cachedAlpha = alpha;
            cachedBaseRGB = baseRGB;
            cachedBgColor = RenderUtils.applyOpacity(BG_COLOR, alpha);
            cachedAccentColor = RenderUtils.applyOpacity(baseColor, alpha);
            cachedBarBgColor = RenderUtils.applyOpacity(BAR_BG_COLOR, alpha);
            cachedFacePlaceholderColor = RenderUtils.applyOpacity(FACE_PLACEHOLDER_COLOR, alpha);
            cachedWhiteColor = RenderUtils.applyOpacity(Color.WHITE, alpha);
            cachedSubColor = RenderUtils.applyOpacity(SUB_BASE_COLOR, alpha);
        }

        Color bgColor = cachedBgColor;
        Color accentColor = cachedAccentColor;
        Color barBgColor = cachedBarBgColor;
        Color facePlaceholderColor = cachedFacePlaceholderColor;
        Color whiteColor = cachedWhiteColor;
        Color subColor = cachedSubColor;

        String nameText = targetEntity.getName();
        String subText = String.format(Locale.US, "%.1f HP  •  %.1fm", state.displayHealth,
                mc.thePlayer.getDistanceToEntity(targetEntity));

        float width = WIDTH;
        float height = HEIGHT;

        RenderUtils.drawRoundOutline((float) x, (float) y, width, height, RADIUS, -0.4f, bgColor, accentColor);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        float faceX = PADDING;
        float faceY = (height - FACE_SIZE) / 2f;

        if (targetEntity instanceof AbstractClientPlayer) {
            long elapsed = now - state.hurtAnimStart;
            float hurtProgress = elapsed >= 400 ? 1f : Math.max(0f, elapsed / 400f);
            float tintAmount = hurtProgress < 1f ? (1f - hurtProgress) * 0.7f : 0f;

            boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

            StencilUtils.checkSetupFBO(mc.getFramebuffer());
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(false, false, false, false);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

            RenderUtils.drawRoundedRect(faceX, faceY, FACE_SIZE, FACE_SIZE, 6f, Color.WHITE);

            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            parentModule.renderPlayerFace((AbstractClientPlayer) targetEntity, faceX, faceY, FACE_SIZE, 1f, tintAmount, alpha);

            if (!stencilWasEnabled) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        } else {
            RenderUtils.drawRoundedRect(faceX, faceY, FACE_SIZE, FACE_SIZE, 3f, facePlaceholderColor);
        }

        float textX = faceX + FACE_SIZE + GAP_FACE_TEXT;
        float barWidth = width - textX - PADDING;

        float nameHeight = nameFont.height();
        float lineHeight = bodyFont.height();
        float textStackHeight = nameHeight + GAP_NAME_BAR + BAR_HEIGHT + GAP_BAR_SUB + lineHeight;
        float textY = (height - textStackHeight) / 2f;

        float nameWidth = nameFont.width(nameText);
        nameFont.draw(nameText, textX + Math.max(0f, (barWidth - nameWidth) / 2f) - 5f, textY, whiteColor.getRGB());
        textY += nameHeight + GAP_NAME_BAR;

        RenderUtils.drawRoundedRect(textX, textY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2f, barBgColor);
        if (healthPercentage > 0f) {
            float progressWidth = Math.min(barWidth, Math.max(BAR_HEIGHT, barWidth * healthPercentage));
            RenderUtils.drawRoundedRect(textX, textY, progressWidth, BAR_HEIGHT, BAR_HEIGHT / 2f, accentColor);
        }
        textY += BAR_HEIGHT + GAP_BAR_SUB;

        float subWidth = bodyFont.width(subText);
        bodyFont.draw(subText, textX + Math.max(0f, (barWidth - subWidth) / 2f) - 4f, textY, subColor.getRGB());

        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }
}
