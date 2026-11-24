package rip.tempest.interfaces.click.exhibition;

import rip.tempest.interfaces.click.exhibition.components.CategoryButton;
import rip.tempest.interfaces.click.exhibition.components.MainPanel;
import rip.tempest.interfaces.click.exhibition.components.Slider;
import rip.tempest.interfaces.click.exhibition.components.TextBox;
import rip.tempest.interfaces.click.exhibition.ui.SkeetMenu;
import rip.tempest.interfaces.click.exhibition.util.ExhibitionFonts;
import rip.tempest.interfaces.click.exhibition.util.Opacity;
import rip.tempest.interfaces.click.exhibition.util.RenderingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class ExhibitionClickInterface extends GuiScreen {
    public final MainPanel mainPanel;

    public ExhibitionClickInterface() {
        ExhibitionFonts.load();
        mainPanel = new MainPanel("Exhibition", 50, 50, new SkeetMenu());
    }

    public static double twoDScale() {
        ScaledResolution scaledRes = new ScaledResolution(Minecraft.getMinecraft());
        return (scaledRes.getScaleFactor() / Math.pow(scaledRes.getScaleFactor(), 2.0D)) * 2;
    }

    public static double pixelScale() {
        return twoDScale() * new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
    }

    @Override
    public void initGui() {
        super.initGui();

        if (!mainPanel.isOpen) {
            SkeetMenu.opacity = new Opacity(0);

            mainPanel.searchFocused = false;
        }
        mainPanel.isOpen = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawMenu(mouseX, mouseY);
    }

    public void drawMenu(int mouseX, int mouseY) {
        GlStateManager.pushMatrix();
        double twoDscale = twoDScale();
        GlStateManager.scale(twoDscale, twoDscale, twoDscale);

        mainPanel.draw((int) (mouseX / twoDscale), (int) (mouseY / twoDscale));

        GlStateManager.scale(1 / twoDscale, 1 / twoDscale, 1 / twoDscale);

        if (mc.displayHeight <= 480) {
            String warning = "\247c\247lThe window is too small for the GUI to fit.";
            RenderingUtil.rectangle(0, 0, mc.displayWidth, 12, -1);
            mc.fontRendererObj.drawString(warning, 240 - mc.fontRendererObj.getStringWidth(warning) / 2F, 2, -1, false);
        }
        GlStateManager.popMatrix();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            double twoDscale = twoDScale();
            int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            mainPanel.handleMouseInput((int) (x / twoDscale), (int) (y / twoDscale), wheel);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        double twoDscale = twoDScale();
        mainPanel.mouseMovedOrUp((int) (mouseX / twoDscale), (int) (mouseY / twoDscale), mouseButton);
        super.mouseReleased((int) (mouseX / twoDscale), (int) (mouseY / twoDscale), mouseButton);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int clickedButton) throws IOException {
        double twoDscale = twoDScale();
        mainPanel.mouseClicked((int) (mouseX / twoDscale), (int) (mouseY / twoDscale), clickedButton);
        super.mouseClicked((int) (mouseX / twoDscale), (int) (mouseY / twoDscale), clickedButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        mainPanel.keyPressed(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        this.mainPanel.isOpen = false;
        mainPanel.dragging = false;
        mainPanel.searchFocused = false;
        Keyboard.enableRepeatEvents(false);
        for (CategoryButton button : mainPanel.typeButton) {
            for (TextBox textBox : button.categoryPanel.textBoxes) {
                textBox.isTyping = false;
                textBox.isFocused = false;
            }
            for (Slider slider : button.categoryPanel.sliders) {
                slider.dragging = false;
            }
            if (button.categoryPanel.configTextBox != null) {
                button.categoryPanel.configTextBox.isTyping = false;
                button.categoryPanel.configTextBox.isFocused = false;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
