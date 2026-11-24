package rip.tempest.interfaces.click.exhibition.components;

public class DropdownButton {
    public String name;
    public float x;
    public float y;
    public DropdownBox box;

    public DropdownButton(String name, float x, float y, DropdownBox box) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.box = box;
    }

    public void draw(final float x, final float y) {
        box.panel.categoryButton.panel.theme.dropDownButtonDraw(this, box, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        box.panel.categoryButton.panel.theme.dropDownButtonMouseClicked(this, box, x, y, button);
    }
}
