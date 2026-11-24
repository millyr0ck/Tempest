package rip.tempest.modules;

import rip.tempest.Tempest;
import rip.tempest.api.properties.impl.ModeProperty;

import java.util.EnumMap;
import java.util.Map;

public final class SubModes<E extends Enum<E>> {
    private final ModeProperty<E> property;
    private final Map<E, SubMode> modes;
    private SubMode active;

    public SubModes(Module module, ModeProperty<E> property, Class<E> type) {
        this.property = property;
        this.modes = new EnumMap<>(type);
        property.addValueChangeListener((was, now) -> {
            if (module.isEnabled()) select(now);
        });
    }

    public SubModes<E> add(E mode, SubMode handler) {
        modes.put(mode, handler);
        return this;
    }

    public void enable() {
        select(property.getValue());
    }

    public void disable() {
        select(null);
    }

    public boolean handles(E mode) {
        return modes.containsKey(mode);
    }

    private void select(E mode) {
        SubMode next = mode == null ? null : modes.get(mode);
        if (next == active) return;
        if (active != null) {
            Tempest.INSTANCE.getEventBus().unsubscribe(active);
            active.onDisable();
        }
        active = next;
        if (active != null) {
            active.onEnable();
            Tempest.INSTANCE.getEventBus().subscribe(active);
        }
    }
}
