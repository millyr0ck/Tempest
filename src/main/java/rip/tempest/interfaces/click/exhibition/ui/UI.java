package rip.tempest.interfaces.click.exhibition.ui;

import rip.tempest.interfaces.click.exhibition.components.*;

public abstract class UI {
    public abstract void mainPanelDraw(MainPanel panel, int mouseX, int mouseY);

    public abstract void mainPanelKeyPress(MainPanel panel, char typedChar, int key);

    public abstract void panelConstructor(MainPanel mainPanel, float x, float y);

    public abstract void panelMouseClicked(MainPanel mainPanel, int x, int y, int button);

    public abstract void panelMouseMovedOrUp(MainPanel mainPanel, int x, int y, int button);

    public abstract void handleMouseInput(MainPanel panel, int mouseX, int mouseY, int wheel);

    public abstract void categoryButtonConstructor(CategoryButton button, MainPanel panel);

    public abstract void categoryButtonMouseClicked(CategoryButton button, MainPanel panel, int x, int y, int mouse);

    public abstract void categoryButtonMouseReleased(CategoryButton button, int x, int y, int mouse);

    public abstract void categoryButtonDraw(CategoryButton button, float x, float y);

    public abstract void categoryPanelConstructor(CategoryPanel categoryPanel, CategoryButton categoryButton, float x, float y);

    public abstract void categoryPanelMouseClicked(CategoryPanel categoryPanel, int x, int y, int mouse);

    public abstract void categoryPanelDraw(CategoryPanel categoryPanel, float x, float y);

    public abstract void categoryPanelMouseMovedOrUp(CategoryPanel categoryPanel, int x, int y, int mouse);

    public abstract void groupBoxDraw(GroupBox groupBox, float x, float y);

    public abstract void buttonMouseClicked(Button button, int x, int y, int mouse, CategoryPanel panel);

    public abstract void buttonDraw(Button button, float x, float y, CategoryPanel panel);

    public abstract void buttonKeyPressed(Button button, int key);

    public abstract void checkBoxMouseClicked(Checkbox checkbox, int x, int y, int mouse, CategoryPanel panel);

    public abstract void checkBoxDraw(Checkbox checkbox, float x, float y, CategoryPanel panel);

    public abstract void dropDownMouseClicked(DropdownBox box, int x, int y, int mouse, CategoryPanel panel);

    public abstract void dropDownDraw(DropdownBox box, float x, float y, CategoryPanel panel);

    public abstract void dropDownButtonMouseClicked(DropdownButton button, DropdownBox box, int x, int y, int mouse);

    public abstract void dropDownButtonDraw(DropdownButton button, DropdownBox box, float x, float y);

    public abstract void multiDropDownMouseClicked(MultiDropdownBox box, int x, int y, int mouse, CategoryPanel panel);

    public abstract void multiDropDownDraw(MultiDropdownBox box, float x, float y, CategoryPanel panel);

    public abstract void multiDropDownButtonMouseClicked(MultiDropdownButton button, MultiDropdownBox box, int x, int y, int mouse);

    public abstract void multiDropDownButtonDraw(MultiDropdownButton button, MultiDropdownBox box, float x, float y);

    public abstract void SliderContructor(Slider slider, CategoryPanel panel);

    public abstract void SliderMouseClicked(Slider slider, int x, int y, int mouse, CategoryPanel panel);

    public abstract void SliderMouseMovedOrUp(Slider slider, int x, int y, int mouse, CategoryPanel panel);

    public abstract void SliderDraw(Slider slider, float x, float y, CategoryPanel panel);

    public abstract void colorConstructor(ColorPreview colorPreview, float x, float y);

    public abstract void colorPrewviewDraw(ColorPreview colorPreview, float x, float y);

    public abstract void colorPickerConstructor(HSVColorPicker picker, float x, float y);

    public abstract void colorPickerDraw(HSVColorPicker picker, float x, float y);

    public abstract void colorPickerClick(HSVColorPicker picker, float x, float y, int mouse);

    public abstract void colorPickerMovedOrUp(HSVColorPicker picker, float x, float y, int mouse);

    public abstract void configButtonDraw(ConfigButton configButton, float x, float y);

    public abstract void configButtonMouseClicked(ConfigButton configButton, float x, float y, int mouse);

    public abstract void configListDraw(ConfigList configList, float x, float y);

    public abstract void configListMouseClicked(ConfigList configList, float x, float y, int mouse);

    public abstract void textBoxDraw(TextBox textBox, float x, float y);

    public abstract void textBoxMouseClicked(TextBox textBox, int x, int y, int mouse);

    public abstract void textBoxKeyPressed(TextBox textBox, char typedChar, int key);
}
