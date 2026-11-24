package rip.tempest.api.notifications;

import java.awt.Color;

public enum NotificationType {
    SUCCESS(new Color(92, 224, 140)),
    DISABLE(new Color(244, 104, 104)),
    INFO(new Color(110, 170, 255)),
    WARNING(new Color(250, 196, 70));

    private final Color color;

    NotificationType(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}
