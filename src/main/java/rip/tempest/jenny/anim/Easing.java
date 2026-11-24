package rip.tempest.jenny.anim;

public enum Easing {
    LINEAR, STEP,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE;

    public static Easing fromJson(String name) {
        if (name == null || name.isEmpty()) return LINEAR;
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) sb.append('_');
            sb.append(Character.toUpperCase(c));
        }
        try {
            return valueOf(sb.toString());
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }

    public double apply(double t, Double arg) {
        switch (this) {
            case STEP: return step(t, arg);
            case EASE_IN_SINE: return sin(t);
            case EASE_OUT_SINE: return 1 - sin(1 - t);
            case EASE_IN_OUT_SINE: return inOut(t, Easing::sin);
            case EASE_IN_QUAD: return t * t;
            case EASE_OUT_QUAD: return out(t, x -> x * x);
            case EASE_IN_OUT_QUAD: return inOut(t, x -> x * x);
            case EASE_IN_CUBIC: return t * t * t;
            case EASE_OUT_CUBIC: return out(t, x -> x * x * x);
            case EASE_IN_OUT_CUBIC: return inOut(t, x -> x * x * x);
            case EASE_IN_QUART: return Math.pow(t, 4);
            case EASE_OUT_QUART: return out(t, x -> Math.pow(x, 4));
            case EASE_IN_OUT_QUART: return inOut(t, x -> Math.pow(x, 4));
            case EASE_IN_QUINT: return Math.pow(t, 5);
            case EASE_OUT_QUINT: return out(t, x -> Math.pow(x, 5));
            case EASE_IN_OUT_QUINT: return inOut(t, x -> Math.pow(x, 5));
            case EASE_IN_EXPO: return exp(t);
            case EASE_OUT_EXPO: return out(t, Easing::exp);
            case EASE_IN_OUT_EXPO: return inOut(t, Easing::exp);
            case EASE_IN_CIRC: return circle(t);
            case EASE_OUT_CIRC: return out(t, Easing::circle);
            case EASE_IN_OUT_CIRC: return inOut(t, Easing::circle);
            case EASE_IN_BACK: return back(t, arg);
            case EASE_OUT_BACK: return out(t, x -> back(x, arg));
            case EASE_IN_OUT_BACK: return inOut(t, x -> back(x, arg));
            case EASE_IN_ELASTIC: return elastic(t, arg);
            case EASE_OUT_ELASTIC: return out(t, x -> elastic(x, arg));
            case EASE_IN_OUT_ELASTIC: return inOut(t, x -> elastic(x, arg));
            case EASE_IN_BOUNCE: return bounce(t, arg);
            case EASE_OUT_BOUNCE: return out(t, x -> bounce(x, arg));
            case EASE_IN_OUT_BOUNCE: return inOut(t, x -> bounce(x, arg));
            default: return t;
        }
    }

    private static double out(double t, java.util.function.DoubleUnaryOperator f) {
        return 1 - f.applyAsDouble(1 - t);
    }

    private static double inOut(double t, java.util.function.DoubleUnaryOperator f) {
        return t < 0.5 ? f.applyAsDouble(t * 2) / 2 : 1 - f.applyAsDouble((1 - t) * 2) / 2;
    }

    private static double sin(double t) {
        return 1 - Math.cos((float) ((t * Math.PI) / 2));
    }

    private static double circle(double t) {
        return 1 - Math.sqrt(1 - t * t);
    }

    private static double exp(double t) {
        return Math.pow(2, 10 * (t - 1));
    }

    private static double elastic(double t, Double bounciness) {
        double p = (bounciness == null ? 1 : bounciness) * Math.PI;
        return 1 - Math.pow(Math.cos((float) ((t * Math.PI) / 2)), 3) * Math.cos((float) (t * p));
    }

    private static double back(double t, Double s) {
        double p = s == null ? 1.70158 : s * 1.70158;
        return t * t * ((p + 1) * t - p);
    }

    private static double bounce(double x, Double s) {
        double k = s == null ? 0.5 : s;
        double q = (121.0 / 16.0) * x * x;
        double w = ((121.0 / 4.0) * k) * Math.pow(x - (6.0 / 11.0), 2) + 1 - k;
        double r = 121 * k * k * Math.pow(x - (9.0 / 11.0), 2) + 1 - k * k;
        double u = 484 * k * k * k * Math.pow(x - (10.5 / 11.0), 2) + 1 - k * k * k;
        return Math.min(Math.min(q, w), Math.min(r, u));
    }

    private static double step(double t, Double arg) {
        int steps = Math.max(2, arg != null ? arg.intValue() : 2);
        if (t < 0) return 0;
        int index = Math.min(steps - 1, (int) Math.floor(t * steps));
        return index / (double) steps;
    }
}
