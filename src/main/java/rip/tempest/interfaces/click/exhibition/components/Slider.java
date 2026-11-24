package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.impl.NumberProperty;

public class Slider {
    public static final float NARROW_WIDTH = 38, WIDE_WIDTH = 82;

    public float x;
    public float y;
    public float width = NARROW_WIDTH;
    public String name;
    public NumberProperty setting;
    public CategoryPanel panel;
    public boolean dragging;
    public double dragX;
    public double lastDragX;

    public Slider(CategoryPanel panel, float x, float y, NumberProperty setting) {
        this.panel = panel;
        this.x = x;
        this.y = y;
        this.setting = setting;
        this.name = setting.getLabel();
        panel.categoryButton.panel.theme.SliderContructor(this, panel);
    }

    public void draw(final float x, final float y) {
        panel.categoryButton.panel.theme.SliderDraw(this, x, y, this.panel);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.SliderMouseClicked(this, x, y, button, this.panel);
    }

    public void mouseReleased(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.SliderMouseMovedOrUp(this, x, y, button, this.panel);
    }
}
