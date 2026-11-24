package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.modules.Module;

public class Button {
    public float x;
    public float y;
    public String name;
    public CategoryPanel panel;
    public boolean enabled;
    public Module module;
    public boolean isBinding;

    public Button(final CategoryPanel panel, final String name, final float x, final float y, Module module) {
        this.panel = panel;
        this.name = name;
        this.x = x;
        this.y = y;
        this.module = module;
        this.enabled = module.isEnabled();
    }

    public void draw(final float x, final float y) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.buttonDraw(this, x, y, this.panel);
        }
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.buttonMouseClicked(this, x, y, button, this.panel);
    }

    public void keyPressed(int key) {
        panel.categoryButton.panel.theme.buttonKeyPressed(this, key);
    }
}
