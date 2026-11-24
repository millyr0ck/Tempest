package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.Property;

public class Checkbox {
    public CategoryPanel panel;
    public boolean enabled;
    public float x;
    public float y;
    public String name;
    public Property<Boolean> setting;

    public Checkbox(CategoryPanel panel, float x, float y, Property<Boolean> setting) {
        this.panel = panel;
        this.name = setting.getLabel();
        this.x = x;
        this.y = y;
        this.setting = setting;
        this.enabled = setting.getValue();
    }

    public void draw(final float x, final float y) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.checkBoxDraw(this, x, y, this.panel);
        }
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.checkBoxMouseClicked(this, x, y, button, this.panel);
    }
}
