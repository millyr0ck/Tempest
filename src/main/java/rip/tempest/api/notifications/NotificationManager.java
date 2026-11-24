package rip.tempest.api.notifications;

import rip.tempest.Tempest;
import rip.tempest.modules.impl.visuals.NotificationsModule;

import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {
    private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();

    public static CopyOnWriteArrayList<Notification> getNotifications() {
        return notifications;
    }

    public static void post(NotificationType type, String title, String description) {
        post(type, title, description, defaultSeconds());
    }

    public static void post(NotificationType type, String title, String description, float time) {
        if (enabled()) {
            if (NotificationsModule.style.getValue() == NotificationsModule.Style.Exhibition
                    && type != NotificationType.WARNING && type != NotificationType.SUCCESS) {
                for (Notification notification : notifications) {
                    if (!notification.isExiting() && notification.getKey() == null && notification.getType() == type
                            && notification.getTitle().equals(title)) {
                        notification.update(type, description, time, System.currentTimeMillis());
                        notification.repeats++;
                        return;
                    }
                }
            }
            notifications.add(new Notification(type, title, description, time));
        }
    }

    public static void postToggle(String module, boolean on) {
        if (!enabled()) return;

        NotificationType type = on ? NotificationType.SUCCESS : NotificationType.DISABLE;
        String description = on ? "Enabled" : "Disabled";
        String title = module;
        String key = "toggle:" + module;

        for (Notification notification : notifications) {
            if (key.equals(notification.getKey()) && !notification.isExiting()) {
                notification.update(type, description, defaultSeconds(), System.currentTimeMillis());
                return;
            }
        }

        notifications.add(new Notification(type, title, description, defaultSeconds(), key, true));
    }

    private static boolean enabled() {
        return Tempest.INSTANCE.getModuleManager().getModule(NotificationsModule.class).isEnabled();
    }

    private static float defaultSeconds() {
        return NotificationsModule.duration.getValue().floatValue();
    }
}
