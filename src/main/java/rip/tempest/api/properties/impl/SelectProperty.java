package rip.tempest.api.properties.impl;

import rip.tempest.api.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SelectProperty extends Property<String> {
    private List<String> options = new ArrayList<>();
    private Runnable onOpen;

    public SelectProperty(String label) {
        super(label, "");
    }

    public SelectProperty(String label, Supplier<Boolean> dependency) {
        super(label, "", dependency);
    }

    public void setOnOpen(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    public void onOpen() {
        if (onOpen != null) onOpen.run();
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;

        String v = getValue();
        if (!options.isEmpty() && (v == null || v.isEmpty() || !options.contains(v))) {
            setValue(options.get(0));
        }
    }
}
