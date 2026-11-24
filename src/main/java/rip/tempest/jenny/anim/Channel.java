package rip.tempest.jenny.anim;

import java.util.List;

public class Channel {
    public final List<Keyframe> keyframes;

    public Channel(List<Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    public float[] sample(float t) {
        if (keyframes.isEmpty()) return new float[]{0, 0, 0};
        Keyframe first = keyframes.get(0);

        if (keyframes.size() == 1 || t <= first.time) return first.value;

        Keyframe prev = first;
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe next = keyframes.get(i);
            if (t <= next.time) {
                float span = next.time - prev.time;
                if (span <= 0) return next.value;
                double alpha = (t - prev.time) / span;
                return lerp(prev.value, next.value, (float) next.easing.apply(alpha, next.easingArg));
            }
            prev = next;
        }
        return prev.value;
    }

    static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }
}
