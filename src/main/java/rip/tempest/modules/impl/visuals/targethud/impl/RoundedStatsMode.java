package rip.tempest.modules.impl.visuals.targethud.impl;

import rip.tempest.modules.impl.visuals.targethud.FontAdapter;
import rip.tempest.modules.impl.visuals.targethud.TargetHudMode;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RoundedStatsMode extends TargetHudMode {
    private static final int WIDTH = 140;
    private static final int HEIGHT = 48;
    private static final float PADDING = 6f;
    private static final float FACE_SIZE = 32f;
    private static final float FACE_RADIUS = 7f;
    private static final float RADIUS = 7f;
    private static final float BAR_HEIGHT = 3f;
    private static final float GAP_NAME_BAR = 4f;
    private static final float GAP_BAR_ROW = 4f;
    private static final float ICON_SIZE = 8f;
    private static final float ICON_SPACING = 9f;

    private final TargetHuds huds;

    public RoundedStatsMode(TargetHuds huds) {
        super("Rounded Stats");
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
        FontAdapter tagFont = RoundedStyle.bold(huds, 14);
        if (nameFont == null || bodyFont == null || tagFont == null) return;

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
        RoundedStyle.drawFace(huds, target, PADDING, faceY, FACE_SIZE, FACE_RADIUS,
                RoundedStyle.hurtTint(state, now), alpha);

        float textX = PADDING + FACE_SIZE + PADDING;
        float right = WIDTH - PADDING;
        float rowHeight = Math.max(ICON_SIZE, bodyFont.height());
        float stackHeight = nameFont.height() + GAP_NAME_BAR + BAR_HEIGHT + GAP_BAR_ROW + rowHeight;
        float rowY = (HEIGHT - stackHeight) / 2f;

        float yours = mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount();
        float theirs = target.getHealth() + target.getAbsorptionAmount();
        String tag = yours - theirs > 0.5f ? "WIN" : theirs - yours > 0.5f ? "LOSE" : "TIE";
        Color tagColor = tag.equals("WIN") ? RoundedStyle.WIN : tag.equals("LOSE") ? RoundedStyle.LOSE : RoundedStyle.SUB;

        float tagHeight = tagFont.height() + 2f;
        float tagWidth = tagFont.width(tag) + 6f;
        float tagX = right - tagWidth;
        float tagY = rowY + (nameFont.height() - tagHeight) / 2f;
        RenderUtils.drawRoundedRect(tagX, tagY, tagWidth, tagHeight, tagHeight / 2f, RenderUtils.applyOpacity(tagColor, 0.22f * alpha));
        tagFont.draw(tag, tagX + 3f, tagY + 1f, RenderUtils.applyOpacity(tagColor, alpha).getRGB());

        String name = RoundedStyle.fit(nameFont, target.getName(), tagX - textX - 4f);
        nameFont.draw(name, textX, rowY, RenderUtils.applyOpacity(Color.WHITE, alpha).getRGB());
        rowY += nameFont.height() + GAP_NAME_BAR;

        float barWidth = right - textX;
        RenderUtils.drawRoundedRect(textX, rowY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2f,
                RenderUtils.applyOpacity(RoundedStyle.BAR_BG, alpha));
        if (healthPercentage > 0f) {
            float progress = Math.min(barWidth, Math.max(BAR_HEIGHT, barWidth * healthPercentage));
            RenderUtils.drawRoundedRect(textX, rowY, progress, BAR_HEIGHT, BAR_HEIGHT / 2f, accent);
        }
        rowY += BAR_HEIGHT + GAP_BAR_ROW;

        String health = String.format(Locale.US, "%.1f HP", state.displayHealth);
        bodyFont.draw(health, right - bodyFont.width(health), rowY + (rowHeight - bodyFont.height()) / 2f,
                RenderUtils.applyOpacity(RoundedStyle.SUB, alpha).getRGB());

        if (alpha > 0.5f) {
            drawItems(target, textX, rowY + (rowHeight - ICON_SIZE) / 2f);
        }

        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private void drawItems(EntityLivingBase target, float x, float y) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack armour = target.getCurrentArmor(slot);
            if (armour != null) items.add(armour);
        }
        if (target.getHeldItem() != null) items.add(target.getHeldItem());
        if (items.isEmpty()) return;

        RenderHelper.enableGUIStandardItemLighting();
        for (ItemStack item : items) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(ICON_SIZE / 16f, ICON_SIZE / 16f, 1f);
            mc.getRenderItem().renderItemAndEffectIntoGUI(item, 0, 0);
            GlStateManager.popMatrix();
            x += ICON_SPACING;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
    }
}
