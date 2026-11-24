package rip.tempest.api.config;

import rip.tempest.Tempest;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.RotationLearnerProcess;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

public final class Config implements Serializable {
    private final String name;
    private final File file;

    public Config(String name) {
        this.name = name;
        this.file = new File(ConfigManager.CONFIGS_DIR, name + ConfigManager.EXTENSION);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    @Override
    public JsonObject save() {
        JsonObject jsonObject = saveSettings();

        JsonObject rotationPreset = RotationLearnerProcess.exportLoadedPreset();
        if (rotationPreset != null) jsonObject.add("RotationPreset", rotationPreset);
        return jsonObject;
    }

    public JsonObject saveSettings() {
        JsonObject jsonObject = new JsonObject();
        JsonObject modulesObject = new JsonObject();
        JsonObject draggingObject = new JsonObject();

        for (rip.tempest.modules.Module module : Tempest.INSTANCE.getModuleManager().getModules())
            modulesObject.add(module.getLabel(), module.save(false));

        for (String key : DraggingProcess.components.keySet()) {
            draggingObject.add(key, DraggingProcess.components.get(key).save());
        }

        jsonObject.add("Modules", modulesObject);
        jsonObject.add("Dragging", draggingObject);
        return jsonObject;
    }

    @Override
    public void load(JsonObject object) {
        if (object.has("Modules")) {
            JsonObject modulesObject = object.getAsJsonObject("Modules");

            for (rip.tempest.modules.Module module : Tempest.INSTANCE.getModuleManager().getModules()) {
                JsonObject saved = module.savedEntry(modulesObject);
                if (saved != null) module.load(saved);
            }
        }
        if (object.has("RotationPreset") && object.get("RotationPreset").isJsonObject()) {
            RotationLearnerProcess.importPreset(object.getAsJsonObject("RotationPreset"));
        }
        if (object.has("Dragging")) {
            JsonObject draggingObject = object.getAsJsonObject("Dragging");
            for (String key : draggingObject.keySet()) {
                JsonObject componentData = draggingObject.getAsJsonObject(key);
                if (!DraggingProcess.components.containsKey(key)) {
                    DraggingProcess.components.put(key, new DraggingProcess.DraggableComponent(0, 0));
                }
                DraggingProcess.components.get(key).load(componentData);
            }
        }
    }
}
