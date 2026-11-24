package rip.tempest.jenny.anim;

public class Keyframe {
    public final float time;
    public final float[] value;

    public final Easing easing;
    public final Double easingArg;

    public Keyframe(float time, float[] value) {
        this(time, value, Easing.LINEAR, null);
    }

    public Keyframe(float time, float[] value, Easing easing, Double easingArg) {
        this.time = time;
        this.value = value;
        this.easing = easing;
        this.easingArg = easingArg;
    }
}
