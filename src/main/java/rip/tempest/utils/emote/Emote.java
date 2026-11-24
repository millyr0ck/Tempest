package rip.tempest.utils.emote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public final class Emote {
    public enum Part { HEAD, TORSO, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    public enum Channel { PITCH, YAW, ROLL, X, Y, Z }

    public enum Easing {
        LINEAR { public float e(float t) { return t; } },
        EASEINOUTQUAD { public float e(float t) { return t < 0.5f ? 2 * t * t : 1 - ((-2 * t + 2) * (-2 * t + 2)) / 2f; } };

        public abstract float e(float t);

        public static Easing of(String s) {
            try { return valueOf(s); } catch (Exception ex) { return LINEAR; }
        }
    }

    public static final class KeyFrame {
        public final double tick;
        public final float value;
        public final Easing easing;
        public KeyFrame(double tick, float value, Easing easing) { this.tick = tick; this.value = value; this.easing = easing; }
    }

    public String name = "emote";
    public boolean loop = true;
    public int beginTick = 0, returnTick = 0, endTick = 0;

    private final EnumMap<Part, EnumMap<Channel, List<KeyFrame>>> channels = new EnumMap<>(Part.class);

    public void add(Part p, Channel c, KeyFrame k) {
        channels.computeIfAbsent(p, x -> new EnumMap<>(Channel.class))
                .computeIfAbsent(c, x -> new ArrayList<>())
                .add(k);
    }

    public void finish() {
        for (EnumMap<Channel, List<KeyFrame>> pm : channels.values()) {
            for (List<KeyFrame> l : pm.values()) {
                l.sort(Comparator.comparingDouble(k -> k.tick));

                for (int i = l.size() - 2; i >= 0; i--) {
                    if (l.get(i).tick == l.get(i + 1).tick) l.remove(i);
                }
            }
        }
    }

    public boolean animates(Part p) {
        return channels.containsKey(p);
    }

    public double loopTick(double elapsed) {
        if (loop && endTick > returnTick) {
            if (elapsed > endTick) {
                double len = endTick - returnTick;
                return returnTick + ((elapsed - returnTick) % len);
            }
            return elapsed;
        }
        return Math.min(elapsed, endTick);
    }

    public float value(Part p, Channel c, double tick, float def) {
        EnumMap<Channel, List<KeyFrame>> pm = channels.get(p);
        if (pm == null) return def;
        List<KeyFrame> l = pm.get(c);
        if (l == null || l.isEmpty()) return def;
        if (tick <= l.get(0).tick) return l.get(0).value;
        KeyFrame last = l.get(l.size() - 1);
        if (tick >= last.tick) return last.value;
        for (int i = 0; i < l.size() - 1; i++) {
            KeyFrame a = l.get(i), b = l.get(i + 1);
            if (tick >= a.tick && tick < b.tick) {
                double dt = b.tick - a.tick;
                if (dt <= 0) return a.value;
                float prog = (float) ((tick - a.tick) / dt);
                float e = b.easing.e(Math.max(0f, Math.min(1f, prog)));
                float out = a.value + (b.value - a.value) * e;
                return Float.isFinite(out) ? out : a.value;
            }
        }
        return last.value;
    }
}
