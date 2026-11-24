package rip.tempest.interfaces.click.exhibition.components;

public class ConfigButton {
    public float x;
    public float y;
    public ConfigList configList;
    public ButtonType buttonType;

    public ConfigButton(ConfigList configList, float x, float y, ButtonType buttonType) {
        this.x = x;
        this.y = y;
        this.buttonType = buttonType;
        this.configList = configList;
    }

    public void draw(final float x, final float y) {
        configList.categoryPanel.categoryButton.panel.theme.configButtonDraw(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        configList.categoryPanel.categoryButton.panel.theme.configButtonMouseClicked(this, x, y, button);
    }

    public enum ButtonType {
        LOAD("Load"), SAVE("Save"), DELETE("Delete"), CREATE("Create"), OPEN_FOLDER("Open Folder");

        private final String label;

        ButtonType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
