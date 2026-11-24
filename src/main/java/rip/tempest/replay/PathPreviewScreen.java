package rip.tempest.replay;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public final class PathPreviewScreen extends GuiScreen {
    private final long opened = System.currentTimeMillis();

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ReplayViewer viewer = ReplayViewer.current();
        if (viewer == null) return;
        long shown = System.currentTimeMillis() - opened;
        float alpha = shown < 2500L ? 1f : 1f - (shown - 2500L) / 500f;
        if (alpha < 0.03f) return;
        MenuTheme.beginFrame();
        CustomFontRenderer font = MenuTheme.body(), small = MenuTheme.small();
        String lead = "Previewing  ", clock = ReplayViewer.preciseClock((int) (viewer.previewSeconds() * 1000))
                + " / " + ReplayViewer.preciseClock((int) (viewer.path.duration() * 1000)), hint = "Esc to stop";
        float w = font.getStringWidth(lead + clock) + 16f + small.getStringWidth(hint) + 12f, h = 18f;
        float x = (width - w) / 2f, y = ReplayHud.MARGIN;
        RenderUtils.drawRoundedRect(x, y, w, h, 6f, new Color(16, 16, 23, Math.round(210 * alpha)));
        float textY = y + (h - font.getHeight()) / 2f;
        font.drawString(lead, x + 8f, textY, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));
        font.drawString(clock, x + 8f + font.getStringWidth(lead), textY, MenuTheme.text(MenuTheme.TEXT, alpha));
        small.drawString(hint, x + w - 8f - small.getStringWidth(hint), y + (h - small.getHeight()) / 2f, MenuTheme.text(MenuTheme.TEXT_MUTED, alpha));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        ReplayViewer viewer = ReplayViewer.current();
        if (keyCode == Keyboard.KEY_ESCAPE && viewer != null) viewer.stopPreview();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
