package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.Property;
import rip.tempest.interfaces.click.exhibition.util.ExhibitionFonts;
import rip.tempest.interfaces.click.exhibition.util.Opacity;

public class TextBox {
    public String textString;
    public float x;
    public float y;
    public Property<String> setting;
    public CategoryPanel panel;
    public boolean isFocused;
    public boolean isTyping;
    public Opacity opacity = new Opacity(255);
    public boolean backwards;
    public int cursorPos;
    public float offset;

    public TextBox(Property<String> setting, float x, float y, CategoryPanel panel) {
        this.x = x;
        this.y = y;
        this.panel = panel;
        this.setting = setting;
        this.textString = setting.getValue();
        this.cursorPos = textString.length();

        float width = ExhibitionFonts.fss.getWidth(textString.substring(0, cursorPos));
        float barOffset = (width - offset);
        if (barOffset < 0) {
            offset += barOffset;
        }

        if (barOffset > 82) {
            offset += (barOffset - 82);
        }
    }

    public void draw(final float x, final float y) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.textBoxDraw(this, x, y);
        }
    }

    public void mouseClicked(final int x, final int y, final int button) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.textBoxMouseClicked(this, x, y, button);
        }
    }

    public void keyPressed(char typedChar, int key) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.textBoxKeyPressed(this, typedChar, key);
        }
    }
}
