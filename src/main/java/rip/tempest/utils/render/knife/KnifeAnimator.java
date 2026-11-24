package rip.tempest.utils.render.knife;

import java.util.function.Consumer;

public final class KnifeAnimator {
    private static final float FADE_SECONDS = 0.1f;
    private static final String IDLE = "idle1";

    private final KnifeModel model;
    private final Consumer<String> soundPlayer;
    private final float[] pose;
    private final float[] fadeFrom;
    private final float[] sampled;

    private KnifeModel.Sequence current;
    private float time;
    private float eventsDoneUntil = -1f;
    private float fade = 1f;

    public KnifeAnimator(final KnifeModel model, final Consumer<String> soundPlayer) {
        this.model = model;
        this.soundPlayer = soundPlayer;
        pose = new float[model.boneCount * 7];
        fadeFrom = new float[model.boneCount * 7];
        sampled = new float[model.boneCount * 7];
        current = model.sequences.get(IDLE);
        model.sample(current, 0f, pose);
    }

    public boolean has(final String sequence) {
        return model.sequences.containsKey(sequence);
    }

    public float duration(final String sequence) {
        return model.sequences.get(sequence).duration();
    }

    public String current() {
        return current.name;
    }

    public void play(final String sequence) {
        final KnifeModel.Sequence next = model.sequences.get(sequence);
        if (next == null) return;
        System.arraycopy(pose, 0, fadeFrom, 0, pose.length);
        current = next;
        time = 0f;
        eventsDoneUntil = -1f;
        fade = 0f;
    }

    public float[] update(final float seconds) {
        time += seconds;
        for (final KnifeModel.SoundEvent event : current.events) {
            if (event.time > eventsDoneUntil && event.time <= time) soundPlayer.accept(event.sound);
        }
        eventsDoneUntil = time;
        final float length = current.duration();
        if (time >= length) {
            if (current.name.equals(IDLE)) {
                time %= length;
                eventsDoneUntil = -1f;
            } else {
                final float leftover = time - length;
                play(IDLE);
                time = leftover;
            }
        }
        model.sample(current, time, sampled);
        fade = Math.min(1f, fade + seconds / FADE_SECONDS);
        if (fade < 1f) {
            model.blend(fadeFrom, 0, sampled, 0, fade * fade * (3f - 2f * fade), pose);
        } else {
            System.arraycopy(sampled, 0, pose, 0, pose.length);
        }
        return pose;
    }
}
