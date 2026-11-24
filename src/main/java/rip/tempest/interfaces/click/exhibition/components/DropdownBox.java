package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.SelectProperty;

import java.util.ArrayList;
import java.util.List;

public class DropdownBox {
    public static final float NARROW_WIDTH = 40, WIDE_WIDTH = 85;

    public Property<?> setting;
    public float x;
    public float y;
    public float width = NARROW_WIDTH;
    public List<DropdownButton> buttons = new ArrayList<>();
    public CategoryPanel panel;
    public boolean active;

    public int scrollRow;
    public int visibleRows = Integer.MAX_VALUE;

    public DropdownBox(Property<?> setting, float x, float y, CategoryPanel panel) {
        this.setting = setting;
        this.panel = panel;
        this.x = x;
        this.y = y;
        rebuildButtons();
    }

    public String getName() {
        return setting.getLabel();
    }

    public List<String> getOptions() {
        List<String> options = new ArrayList<>();
        if (setting instanceof ModeProperty) {
            for (Object value : ((ModeProperty<?>) setting).getValues())
                options.add(value.toString());
        } else if (setting instanceof SelectProperty) {
            options.addAll(((SelectProperty) setting).getOptions());
        }
        return options;
    }

    public String getSelected() {
        Object value = setting.getValue();
        return value == null ? "" : value.toString();
    }

    public void setSelected(String option) {
        if (setting instanceof ModeProperty) {
            for (Object value : ((ModeProperty<?>) setting).getValues()) {
                if (value.toString().equals(option)) {
                    setting.setValueObj(value);
                    return;
                }
            }
        } else if (setting instanceof SelectProperty) {
            ((SelectProperty) setting).setValue(option);
        }
    }

    public void rebuildButtons() {
        buttons.clear();
        float offset = 10;
        for (String value : getOptions()) {
            buttons.add(new DropdownButton(value, x, y + offset, this));
            offset += 9;
        }
    }

    public List<DropdownButton> visibleButtons() {
        int from = Math.max(0, Math.min(scrollRow, buttons.size()));
        return buttons.subList(from, (int) Math.min(buttons.size(), (long) from + visibleRows));
    }

    public void setPosition(float x, float y) {
        float dx = x - this.x, dy = y - this.y;
        this.x = x;
        this.y = y;
        for (DropdownButton button : buttons) {
            button.x += dx;
            button.y += dy;
        }
    }

    public void draw(final float x, final float y) {
        if (panel.visible) {
            panel.categoryButton.panel.theme.dropDownDraw(this, x, y, this.panel);
        }
    }

    public void mouseClicked(final int x, final int y, final int button) {
        panel.categoryButton.panel.theme.dropDownMouseClicked(this, x, y, button, this.panel);
    }
}
