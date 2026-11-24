package rip.tempest.interfaces.click;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.impl.client.ClientSettingsModule;

import java.awt.*;

public class ArrayListColorPickerUI {
    private final ColorPicker picker1 = new ColorPicker();
    private final ColorPicker picker2 = new ColorPicker();
    private boolean initialized;

    public int lastPicker1X;
    public int lastPicker1Y;
    public int lastPicker2X;
    public int lastPicker2Y;

    public static boolean isHiddenProperty(Property<?> property) {
        String label = property.getLabel();
        return "Picker Color 1".equals(label) || "Picker Color 2".equals(label);
    }

    public static boolean isColorModeProperty(Property<?> property) {
        return property instanceof ModeProperty && "Color".equals(property.getLabel());
    }

    public boolean isPickerActive(Property<?> property) {
        if (!isColorModeProperty(property)) return false;
        Object value = ((ModeProperty<?>) property).getValue();
        return value != null && "Picker".equals(value.toString());
    }

    public int getExtraHeight(Property<?> property) {
        return isPickerActive(property) ? (ColorPicker.TOTAL_HEIGHT + 8) * 2 : 0;
    }

    public void render(CustomFontRenderer font, Property<?> property, int x, int y, int width, int modeBlockHeight, int mouseX, int mouseY) {
        if (!isPickerActive(property)) return;

        ensureInitialized();
        syncToModule();

        int picker1Y = y + modeBlockHeight + 6;
        int picker2Y = picker1Y + ColorPicker.TOTAL_HEIGHT + 8;
        lastPicker1X = x + 4;
        lastPicker1Y = picker1Y;
        lastPicker2X = x + 4;
        lastPicker2Y = picker2Y;

        font.drawString("Color 1", lastPicker1X, picker1Y - 12, new Color(160, 160, 160).getRGB());
        font.drawString("Color 2", lastPicker2X, picker2Y - 12, new Color(160, 160, 160).getRGB());

        picker1.render(lastPicker1X, picker1Y, mouseX, mouseY);
        picker2.render(lastPicker2X, picker2Y, mouseX, mouseY);
    }

    public boolean mouseClicked(Property<?> property, int mouseX, int mouseY, int mouseButton) {
        if (!isPickerActive(property)) return false;
        if (picker1.mouseClicked(lastPicker1X, lastPicker1Y, mouseX, mouseY, mouseButton)) return true;
        return picker2.mouseClicked(lastPicker2X, lastPicker2Y, mouseX, mouseY, mouseButton);
    }

    public void mouseReleased() {
        picker1.mouseReleased();
        picker2.mouseReleased();
    }

    public void mouseDragged(int mouseX, int mouseY) {
        picker1.mouseDragged(lastPicker1X, lastPicker1Y, mouseX, mouseY);
        picker2.mouseDragged(lastPicker2X, lastPicker2Y, mouseX, mouseY);
    }

    public void onModeChanged(Object newValue) {
        if ("Picker".equals(String.valueOf(newValue))) {
            initialized = false;
        }
    }

    private void ensureInitialized() {
        if (initialized) return;
        picker1.setColor(ClientSettingsModule.getPickerColor1());
        picker2.setColor(ClientSettingsModule.getPickerColor2());
        initialized = true;
    }

    private void syncToModule() {
        ClientSettingsModule.setPickerColor1(picker1.getColor());
        ClientSettingsModule.setPickerColor2(picker2.getColor());
    }
}
