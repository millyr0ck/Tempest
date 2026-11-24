package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.impl.MultiModeProperty;

import java.util.ArrayList;
import java.util.List;

public class MultiDropdownBox {
    public static final float NARROW_WIDTH = 40, WIDE_WIDTH = 85;

    public String name;
    public MultiModeProperty<?> setting;
    public float x;
    public float y;
    public float width = NARROW_WIDTH;
    public List<MultiDropdownButton> buttons = new ArrayList<>();
    public CategoryPanel panel;
    public boolean active;

    public int scrollRow;
    public int visibleRows = Integer.MAX_VALUE;

    public MultiDropdownBox(MultiModeProperty<?> setting, float x, float y, CategoryPanel panel) {
        this.name = setting.getLabel();
        this.setting = setting;
        this.panel = panel;
        this.x = x;
        this.y = y;
        float offset = 10;
        Object[] values = setting.getValues();
        for (int i = 0; i < values.length; i++) {
            buttons.add(new MultiDropdownButton(values[i].toString(), i, x, y + offset, this));
            offset += 9;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean isSelected(int index) {
        MultiModeProperty raw = setting;
        return raw.isSelected((Enum) setting.getValues()[index]);
    }

    public void toggle(int index) {
        setting.setValue(index);
    }

    public List<MultiDropdownButton> visibleButtons() {
        int from = Math.max(0, Math.min(scrollRow, buttons.size()));
        return buttons.subList(from, (int) Math.min(buttons.size(), (long) from + visibleRows));
    }

    public void setPosition(float x, float y) {
        float dx = x - this.x, dy = y - this.y;
        this.x = x;
        this.y = y;
        for (MultiDropdownButton button : buttons) {
            button.x += dx;
            button.y += dy;
        }
    }

    public void draw(final float x, final float y) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.multiDropDownDraw(this, x, y, this.panel);
        }
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.multiDropDownMouseClicked(this, x, y, button, this.panel);
    }
}
