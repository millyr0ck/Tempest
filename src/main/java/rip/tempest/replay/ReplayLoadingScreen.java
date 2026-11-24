package rip.tempest.replay;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuBackdrop;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.utils.render.Glyphs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;

public final class ReplayLoadingScreen extends GuiScreen {
    private final String title;

    public ReplayLoadingScreen(String title) {
        this.title = title;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        MenuTheme.beginFrame();
        draw(width, height, "Opening " + title, "", -1f);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static void draw(int width, int height, String message, String detail, float progress) {
        MenuBackdrop.draw(width, height);
        CustomFontRenderer large = MenuTheme.large(), body = MenuTheme.body();
        float cx = width / 2f, cy = height / 2f;
        large.drawCenteredString(message, cx, cy - 22f, MenuTheme.text(MenuTheme.TEXT, 1f));
        if (!detail.isEmpty()) body.drawCenteredString(detail, cx, cy - 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, 1f));
        if (progress >= 0f) {
            float barWidth = 160f, left = cx - barWidth / 2f, y = cy + 16f;
            Glyphs.stroke(left, y, left + barWidth, y, 3f, MenuTheme.white(0.1f));
            float end = left + barWidth * MenuTheme.clamp(progress);
            if (end > left + 0.5f) Glyphs.stroke(left, y, end, y, 3f, MenuTheme.accent());
        }
    }

    static void drawFrame(String message, String detail, float progress) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        Framebuffer framebuffer = mc.getFramebuffer();
        GlStateManager.pushMatrix();
        GlStateManager.clear(16640);
        framebuffer.bindFramebuffer(true);
        GlStateManager.enableTexture2D();
        mc.entityRenderer.setupOverlayRendering();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        MenuTheme.beginFrame();
        draw(resolution.getScaledWidth(), resolution.getScaledHeight(), message, detail, progress);
        framebuffer.unbindFramebuffer();
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        framebuffer.framebufferRender(mc.displayWidth, mc.displayHeight);
        GlStateManager.popMatrix();
        mc.updateDisplay();
    }
}
