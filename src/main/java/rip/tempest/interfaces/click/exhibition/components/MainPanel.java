package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.interfaces.click.exhibition.ui.UI;

import java.util.ArrayList;

public class MainPanel {
    public boolean isOpen;
    public float x;
    public float y;
    public String headerString;
    public float dragX;
    public float dragY;
    public float lastDragX;
    public float lastDragY;
    public boolean dragging;
    public final UI theme;

    public static String searchQuery = "";
    public boolean searchFocused;

    public ArrayList<CategoryButton> typeButton;

    public MainPanel(String header, float x, float y, UI theme) {
        this.headerString = header;
        this.x = x;
        this.y = y;
        this.theme = theme;
        typeButton = new ArrayList<>();
        theme.panelConstructor(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int state) {
        theme.panelMouseClicked(this, x, y, state);
    }

    public void mouseMovedOrUp(final int x, final int y, final int state) {
        theme.panelMouseMovedOrUp(this, x, y, state);
    }

    public void draw(int mouseX, int mouseY) {
        theme.mainPanelDraw(this, mouseX, mouseY);
    }

    public void keyPressed(char typedChar, int key) {
        theme.mainPanelKeyPress(this, typedChar, key);
    }

    public void handleMouseInput(int mouseX, int mouseY, int wheel) {
        theme.handleMouseInput(this, mouseX, mouseY, wheel);
    }
}
