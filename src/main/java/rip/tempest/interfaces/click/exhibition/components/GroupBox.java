package rip.tempest.interfaces.click.exhibition.components;

public class GroupBox {
    public float x;
    public float y;
    public float width;
    public float height;
    public String label;
    public boolean renderLabel;
    public CategoryPanel categoryPanel;

    public GroupBox(String label, CategoryPanel categoryPanel, float x, float y, float height) {
        this(label, categoryPanel, x, y, 90, height, false);
    }

    public GroupBox(String label, CategoryPanel categoryPanel, float x, float y, float width, float height, boolean renderLabel) {
        this.x = x;
        this.y = y;
        this.label = label;
        this.width = width;
        this.height = height;
        this.renderLabel = renderLabel;
        this.categoryPanel = categoryPanel;
    }

    public void draw(final float x, final float y) {
        categoryPanel.categoryButton.panel.theme.groupBoxDraw(this, x, y);
    }
}
