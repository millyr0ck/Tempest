package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.interfaces.click.exhibition.util.Opacity;
import rip.tempest.modules.ModuleCategory;

public class CategoryButton {
    public float x;
    public float y;
    public String name;

    public final ModuleCategory category;
    public MainPanel panel;
    public boolean enabled;
    public CategoryPanel categoryPanel;
    public Opacity fade;

    public CategoryButton(MainPanel panel, String name, ModuleCategory category, float x, float y) {
        this.fade = new Opacity(0);
        this.panel = panel;
        this.name = name;
        this.category = category;
        this.x = x;
        this.y = y;
        panel.theme.categoryButtonConstructor(this, this.panel);
    }

    public void draw(final float x, final float y) {
        panel.theme.categoryButtonDraw(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.theme.categoryButtonMouseClicked(this, this.panel, x, y, button);
    }

    public void mouseReleased(final int x, final int y, final int button) {
        panel.theme.categoryButtonMouseReleased(this, x, y, button);
    }
}
