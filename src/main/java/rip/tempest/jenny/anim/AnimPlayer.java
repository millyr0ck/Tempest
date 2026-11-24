package rip.tempest.jenny.anim;

import java.util.ArrayList;
import java.util.List;

public class AnimPlayer {
    private static final float BLEND_SECONDS = 0.25f;

    private NpcAnimation current;

    private boolean loop;
    private float time;
    private long lastNanos;

    private NpcAnimation previous;
    private float previousTime;
    private float blendElapsed = BLEND_SECONDS;

    private NpcAnimation overlay;
    private float overlayTime;

    private int nextEvent;
    private final List<String> firedEvents = new ArrayList<>();

    public void setAnimation(NpcAnimation anim) {
        setAnimation(anim, anim != null && anim.loop);
    }

    public void setAnimation(NpcAnimation anim, boolean loop) {
        if (anim == current) {
            this.loop = loop;
            return;
        }
        update();
        previous = current;
        previousTime = time;
        blendElapsed = previous == null ? BLEND_SECONDS : 0f;
        current = anim;
        this.loop = loop;
        time = 0f;
        nextEvent = 0;
        lastNanos = System.nanoTime();
    }

    public void update() {
        long now = System.nanoTime();
        float delta = lastNanos == 0 ? 0f : Math.min((now - lastNanos) / 1e9f, 0.25f);
        lastNanos = now;
        if (overlay != null && overlay.length > 0) {
            overlayTime = (overlayTime + delta) % overlay.length;
        }
        if (current == null) return;

        if (blendElapsed < BLEND_SECONDS) {
            blendElapsed += delta;
            if (blendElapsed < BLEND_SECONDS) return;
            delta = blendElapsed - BLEND_SECONDS;
            previous = null;
        }

        time += delta;
        fireEvents(Math.min(time, current.length));
        if (loop && current.length > 0) {
            while (time >= current.length) {
                time -= current.length;
                nextEvent = 0;
                fireEvents(time);
            }
        } else if (time > current.length) {
            time = current.length;
        }
    }

    private void fireEvents(float upTo) {
        float[] times = current.eventTimes;
        while (nextEvent < times.length && times[nextEvent] <= upTo) {
            firedEvents.add(current.eventNames[nextEvent]);
            nextEvent++;
        }
    }

    public List<String> drainEvents() {
        if (firedEvents.isEmpty()) return java.util.Collections.emptyList();
        List<String> out = new ArrayList<>(firedEvents);
        firedEvents.clear();
        return out;
    }

    public float[] getBoneRotation(String boneName) {
        return blended(boneName, 0, 0f);
    }

    public float[] getBonePosition(String boneName) {
        return blended(boneName, 1, 0f);
    }

    public float[] getBoneScale(String boneName) {
        return blended(boneName, 2, 1f);
    }

    public void setOverlay(NpcAnimation anim) {
        if (anim == overlay) return;
        overlay = anim;
        overlayTime = 0f;
    }

    private float[] blended(String boneName, int kind, float identity) {
        if (overlay != null) {
            float[] o = sample(overlay, boneName, kind, overlayTime);
            if (o != null) return o;
        }
        float[] to = sample(current, boneName, kind, time);
        if (previous == null || blendElapsed >= BLEND_SECONDS) return to;

        float[] from = sample(previous, boneName, kind, previousTime);
        if (from == null && to == null) return null;
        float[] rest = {identity, identity, identity};
        float a = blendElapsed / BLEND_SECONDS;
        return Channel.lerp(from != null ? from : rest, to != null ? to : rest, a);
    }

    private static float[] sample(NpcAnimation anim, String boneName, int kind, float t) {
        if (anim == null) return null;
        BoneAnim ba = anim.bones.get(boneName);
        if (ba == null) return null;
        Channel ch = kind == 0 ? ba.rotation : kind == 1 ? ba.position : ba.scale;
        if (ch == null || ch.keyframes.isEmpty()) return null;
        return ch.sample(t);
    }

    public NpcAnimation getCurrent() { return current; }

    public float getTime() { return time; }

    public boolean isFinished() {
        return current != null && !loop && blendElapsed >= BLEND_SECONDS && time >= current.length;
    }

    public void resetTime() {
        time = 0f;
        nextEvent = 0;
        lastNanos = System.nanoTime();
    }
}
