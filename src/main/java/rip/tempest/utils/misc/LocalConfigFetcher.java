package rip.tempest.utils.misc;

import rip.tempest.api.config.ConfigManager;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalConfigFetcher {
    public static List<String> fetchConfigList() {
        List<String> configs = new ArrayList<>();

        if (!ConfigManager.CONFIGS_DIR.exists()) {
            boolean ignored = ConfigManager.CONFIGS_DIR.mkdirs();
        }

        File[] files = ConfigManager.CONFIGS_DIR.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("json")) {
                    configs.add(FilenameUtils.removeExtension(file.getName()));
                }
            }
        }

        Collections.sort(configs, String.CASE_INSENSITIVE_ORDER);
        return configs;
    }

    public static boolean loadConfig(String configName) {
        return rip.tempest.Tempest.INSTANCE.getConfigManager().loadConfig(configName);
    }
}
