package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.interfaces.click.exhibition.util.ColorObject;

public class HSVColorPicker {
    public float x;
    public float y;
    public ColorPreview colorPreview;
    public ColorObject color;
    public boolean selectingOpacity;
    public boolean selectingColor;
    public boolean selectingHue;
    public float hue;
    public float saturation;
    public float brightness;
    public float opacity;

    public HSVColorPicker(float x, float y, ColorPreview colorPreview, ColorObject color) {
        this.x = x;
        this.y = y;
        this.colorPreview = colorPreview;
        this.color = color;
        colorPreview.categoryPanel.panel.theme.colorPickerConstructor(this, x, y);
    }

    public void draw(final float x, final float y) {
        colorPreview.categoryPanel.panel.theme.colorPickerDraw(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        colorPreview.categoryPanel.panel.theme.colorPickerClick(this, x, y, button);
    }

    public void mouseReleased(final int x, final int y, final int button) {
        colorPreview.categoryPanel.panel.theme.colorPickerMovedOrUp(this, x, y, button);
    }
}
