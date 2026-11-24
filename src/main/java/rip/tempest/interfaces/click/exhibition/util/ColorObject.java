package rip.tempest.interfaces.click.exhibition.util;

import rip.tempest.api.properties.impl.NumberProperty;

public class ColorObject {
    private final NumberProperty storage;

    public ColorObject(NumberProperty storage) {
        this.storage = storage;
    }

    private int argb() {
        return storage.getValue().intValue();
    }

    public int getRed() {
        return argb() >> 16 & 0xFF;
    }

    public int getGreen() {
        return argb() >> 8 & 0xFF;
    }

    public int getBlue() {
        return argb() & 0xFF;
    }

    public int getAlpha() {
        return argb() >>> 24;
    }

    public int getColorHex() {
        return argb();
    }

    public void updateColors(int red, int green, int blue, int alpha) {
        storage.setValue((double) Colors.getColor(red, green, blue, alpha));
    }
}
