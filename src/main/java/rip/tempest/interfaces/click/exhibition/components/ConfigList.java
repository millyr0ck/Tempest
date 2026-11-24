package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.api.config.ConfigManager;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigList {
    public float x;
    public float y;
    public String[] configs;
    public boolean hovering;
    public float amountScrolled;
    public int selectedConfigID = -1;
    public CategoryPanel categoryPanel;
    public ConfigTextBox configTextBox;
    public List<ConfigButton> configButtonList = new ArrayList<>();

    public ConfigList(float x, float y, CategoryPanel categoryPanel) {
        this.x = x;
        this.y = y;
        this.categoryPanel = categoryPanel;
        this.configs = listConfigs();
        configButtonList.add(new ConfigButton(this, x + 88, y + 0, ConfigButton.ButtonType.LOAD));
        configButtonList.add(new ConfigButton(this, x + 88, y + 10, ConfigButton.ButtonType.SAVE));
        configButtonList.add(new ConfigButton(this, x + 88, y + 20, ConfigButton.ButtonType.DELETE));
        configButtonList.add(new ConfigButton(this, x + 88, y + 30, ConfigButton.ButtonType.CREATE));
        configButtonList.add(new ConfigButton(this, x + 88, y + 40, ConfigButton.ButtonType.OPEN_FOLDER));
        configTextBox = categoryPanel.configTextBox;
    }

    public static String[] listConfigs() {
        File[] files = ConfigManager.CONFIGS_DIR.listFiles();
        if (files == null) return new String[0];
        return Arrays.stream(files)
                .filter(file -> "json".equalsIgnoreCase(FilenameUtils.getExtension(file.getName())))
                .map(file -> FilenameUtils.removeExtension(file.getName()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toArray(String[]::new);
    }

    public void draw(final float x, final float y) {
        configButtonList.forEach(o -> o.draw(x, y));
        categoryPanel.categoryButton.panel.theme.configListDraw(this, x, y);
    }

    public void mouseClicked(final int x, final int y, final int button) {
        categoryPanel.categoryButton.panel.theme.configListMouseClicked(this, x, y, button);
        configButtonList.forEach(o -> o.mouseClicked(x, y, button));
    }
}
