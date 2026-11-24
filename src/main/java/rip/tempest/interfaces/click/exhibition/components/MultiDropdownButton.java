package rip.tempest.interfaces.click.exhibition.components;

public class MultiDropdownButton {
    public String name;
    public final int index;
    public float x;
    public float y;
    public MultiDropdownBox box;

    public MultiDropdownButton(String name, int index, float x, float y, MultiDropdownBox box) {
        this.name = name;
        this.index = index;
        this.x = x;
        this.y = y;
        this.box = box;
    }

    public void draw(final float x, final float y) {
        box.panel.categoryButton.panel.theme.multiDropDownButtonDraw(this, box, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        box.panel.categoryButton.panel.theme.multiDropDownButtonMouseClicked(this, box, x, y, button);
    }
}
