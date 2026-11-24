package rip.tempest.api.properties.impl;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FilteredModeProperty<T extends Enum<T>> extends ModeProperty<T> {
    private final Predicate<T> allowed;
    private final Supplier<Object> context;
    private final Map<Object, T[]> cache = new HashMap<>();

    public FilteredModeProperty(final String label, final T value, final Supplier<Boolean> dependency, final Predicate<T> allowed, final Supplier<Object> context) {
        super(label, value, dependency);
        this.allowed = allowed;
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T[] getValues() {
        return cache.computeIfAbsent(context.get(), key -> {
            final T[] all = super.getValues();
            final List<T> kept = new ArrayList<>();
            for (final T value : all) if (allowed.test(value)) kept.add(value);
            return kept.toArray((T[]) Array.newInstance(all.getClass().getComponentType(), 0));
        });
    }

    @Override
    public void setValue(final int index) {
        setValue(getValues()[index]);
    }
}
