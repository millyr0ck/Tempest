package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CategoryPanel {
    public float x;
    public float y;
    public boolean visible;
    public CategoryButton categoryButton;
    public String headerString;
    public ArrayList<Button> buttons = new ArrayList<>();
    public ArrayList<Slider> sliders = new ArrayList<>();
    public ArrayList<DropdownBox> dropdownBoxes = new ArrayList<>();
    public ArrayList<MultiDropdownBox> multiDropdownBoxes = new ArrayList<>();
    public ArrayList<Checkbox> checkboxes = new ArrayList<>();
    public ArrayList<ColorPreview> colorPreviews = new ArrayList<>();
    public ArrayList<GroupBox> groupBoxes = new ArrayList<>();
    public ArrayList<TextBox> textBoxes = new ArrayList<>();
    public ConfigTextBox configTextBox;
    public ConfigList configList;

    public float scroll;
    public float maxScroll;

    public final Map<Module, Button> buttonCache = new HashMap<>();
    public final Map<Property<?>, Object> settingCache = new HashMap<>();

    public CategoryPanel(String name, CategoryButton categoryButton, float x, float y) {
        this.headerString = name;
        this.x = x;
        this.y = y;
        this.categoryButton = categoryButton;
        this.visible = false;
        categoryButton.panel.theme.categoryPanelConstructor(this, categoryButton, x, y);
    }

    public void clearLayout() {
        buttons.clear();
        sliders.clear();
        dropdownBoxes.clear();
        multiDropdownBoxes.clear();
        checkboxes.clear();
        groupBoxes.clear();
        textBoxes.clear();
    }

    public void draw(final float x, final float y) {
        categoryButton.panel.theme.categoryPanelDraw(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        categoryButton.panel.theme.categoryPanelMouseClicked(this, x, y, button);
    }

    public void mouseReleased(final int x, final int y, final int button) {
        categoryButton.panel.theme.categoryPanelMouseMovedOrUp(this, x, y, button);
    }
}
