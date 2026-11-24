package rip.tempest.api.notifications;

public class Notification {
    public static final long ENTER_MILLIS = 420L;
    public static final long EXIT_MILLIS = 300L;

    public static final long UPDATE_MILLIS = 280L;

    private static final long ICON_DELAY_MILLIS = 140L;
    private static final long ICON_MILLIS = 320L;

    private final String key;
    private final String title;
    private final boolean accentDescription;
    private String description;
    private NotificationType type;
    private NotificationType previousType;
    private long duration;

    private final long created;
    private long shownSince;
    private long updated;
    private long exitStart = -1L;

    public float shownWidth = -1f;

    public int repeats;

    public Notification(NotificationType type, String title, String description, float seconds) {
        this(type, title, description, seconds, null, false);
    }

    public Notification(NotificationType type, String title, String description, float seconds,
                        String key, boolean accentDescription) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.duration = (long) (seconds * 1000f);
        this.key = key;
        this.accentDescription = accentDescription;
        this.created = System.currentTimeMillis();
        this.shownSince = created;
    }

    public void update(NotificationType type, String description, float seconds, long now) {
        this.previousType = this.type;
        this.type = type;
        this.description = description;
        this.duration = (long) (seconds * 1000f);
        this.updated = now;
        this.shownSince = now;
    }

    public float enter(long now) {
        return clamp((now - created) / (float) ENTER_MILLIS);
    }

    public float exit(long now) {
        return exitStart < 0L ? 0f : clamp((now - exitStart) / (float) EXIT_MILLIS);
    }

    public boolean isExiting() {
        return exitStart >= 0L;
    }

    public void beginExit(long now) {
        if (exitStart < 0L) exitStart = now;
    }

    public boolean expired(long now) {
        return now - shownSince >= duration;
    }

    public boolean stale(long now) {
        return exitStart < 0L && now - shownSince >= duration + 1500L;
    }

    public float remaining(long now) {
        return duration <= 0L ? 0f : 1f - clamp((now - shownSince) / (float) duration);
    }

    public float settle(long now) {
        return updated == 0L ? 1f : clamp((now - updated) / (float) UPDATE_MILLIS);
    }

    public float icon(long now) {
        long start = Math.max(created + ICON_DELAY_MILLIS, updated);
        return clamp((now - start) / (float) ICON_MILLIS);
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationType getPreviousType() {
        return previousType;
    }

    public boolean hasAccentDescription() {
        return accentDescription;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
