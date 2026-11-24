package rip.tempest.interfaces.menu;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public abstract class MenuPage extends GuiScreen {
    private static final long INTRO_MILLIS = 380L;

    protected long openTime, lastFrame;

    protected float delta, intro;

    protected float columnX, columnWidth, contentBottom;

    protected MenuPage() {
        openTime = lastFrame = System.currentTimeMillis();
    }

    protected abstract MenuDock.Page page();

    protected abstract void drawPage(int pointerX, int pointerY);

    protected boolean modal() {
        return false;
    }

    protected float maxColumnWidth() {
        return 680f;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        delta = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        intro = MenuTheme.easeOut((now - openTime) / (float) INTRO_MILLIS);

        MenuTheme.beginFrame();
        columnWidth = Math.min(width - MenuTheme.MARGIN * 2f, maxColumnWidth());
        columnX = (width - columnWidth) / 2f;
        contentBottom = MenuDock.top(height) - 12f;

        MenuBackdrop.draw(width, height);

        if (MenuTheme.astolfo() && page() != MenuDock.Page.HOME) {
            net.minecraft.client.gui.Gui.drawRect(0, 0, width, height, 0x8C000000);
        }

        boolean blocked = modal();
        int pointerX = blocked ? -1000 : mouseX, pointerY = blocked ? -1000 : mouseY;
        drawPage(pointerX, pointerY);
        MenuDock.draw(width, height, pointerX, pointerY, page(), delta);
        drawOverlay(mouseX, mouseY);
    }

    protected void drawOverlay(int mouseX, int mouseY) {
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!modal() && MenuDock.mouseClicked(this, page(), mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && page() != MenuDock.Page.HOME) MenuDock.open(this, MenuDock.Page.HOME);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
