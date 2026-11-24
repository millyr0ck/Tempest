package rip.tempest.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CameraPath {
    static final float MIN_SECONDS = 0.1f, MAX_SECONDS = 600f;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Keyframe {
        int time;
        double x, y, z;
        float yaw, pitch;

        float seconds;

        Keyframe(int time, double x, double y, double z, float yaw, float pitch) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public int time() {
            return time;
        }

        public float seconds() {
            return seconds;
        }
    }

    record Sample(double time, double x, double y, double z, float yaw, float pitch, double speed) {
    }

    private static final class Saved {
        List<Keyframe> keyframes = new ArrayList<>();
    }

    private final List<Keyframe> keyframes = new ArrayList<>();
    private final File file;

    private CameraPath(File file) {
        this.file = file;
    }

    public static File fileFor(File replay) {
        String name = replay.getName();
        if (name.endsWith(".mcpr")) name = name.substring(0, name.length() - 5);
        return new File(replay.getParentFile(), name + ".path.json");
    }

    static CameraPath load(File replay) {
        CameraPath path = new CameraPath(fileFor(replay));
        if (path.file.isFile()) {
            try {
                Saved saved = GSON.fromJson(new String(Files.readAllBytes(path.file.toPath()), StandardCharsets.UTF_8), Saved.class);
                if (saved != null && saved.keyframes != null) {
                    for (Keyframe keyframe : saved.keyframes) if (keyframe != null) path.keyframes.add(keyframe);
                }
                path.tidy();
            } catch (Exception e) {
                System.err.println("[Replay] couldn't read " + path.file.getName() + ": " + e);
            }
        }
        return path;
    }

    private void save() {
        try {
            if (keyframes.isEmpty()) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            Saved saved = new Saved();
            saved.keyframes = keyframes;
            Files.write(file.toPath(), GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[Replay] couldn't save " + file.getName() + ": " + e);
        }
    }

    private void tidy() {
        keyframes.sort((a, b) -> Integer.compare(a.time, b.time));
        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe keyframe = keyframes.get(i);
            keyframe.seconds = i == 0 ? 0f : clampSeconds(keyframe.seconds);
        }
    }

    public List<Keyframe> keyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public int size() {
        return keyframes.size();
    }

    public Keyframe get(int index) {
        return keyframes.get(index);
    }

    public float duration() {
        float total = 0f;
        for (Keyframe keyframe : keyframes) total += keyframe.seconds;
        return total;
    }

    int add(int time, double x, double y, double z, float yaw, float pitch) {
        int index = keyframes.size();
        while (index > 0 && keyframes.get(index - 1).time > time) index--;
        keyframes.add(index, new Keyframe(time, x, y, z, yaw, pitch));
        retime(index);
        retime(index + 1);
        save();
        return index;
    }

    void remove(int index) {
        if (index < 0 || index >= keyframes.size()) return;
        keyframes.remove(index);
        retime(index);
        save();
    }

    int replace(int index, int time, double x, double y, double z, float yaw, float pitch) {
        if (index < 0 || index >= keyframes.size()) return add(time, x, y, z, yaw, pitch);
        Keyframe old = keyframes.get(index);
        if (old.time == time) {
            old.x = x;
            old.y = y;
            old.z = z;
            old.yaw = yaw;
            old.pitch = pitch;
            save();
            return index;
        }
        keyframes.remove(index);
        retime(index);
        return add(time, x, y, z, yaw, pitch);
    }

    void clear() {
        keyframes.clear();
        save();
    }

    void changeSeconds(int index, float change) {
        if (index <= 0 || index >= keyframes.size()) return;
        Keyframe keyframe = keyframes.get(index);
        keyframe.seconds = clampSeconds(Math.round((keyframe.seconds + change) * 10f) / 10f);
        save();
    }

    private void retime(int index) {
        if (index < 0 || index >= keyframes.size()) return;
        Keyframe keyframe = keyframes.get(index);
        if (index == 0) {
            keyframe.seconds = 0f;
            return;
        }
        int gap = keyframe.time - keyframes.get(index - 1).time;
        keyframe.seconds = gap > 0 ? clampSeconds(gap / 1000f) : 2f;
    }

    private static float clampSeconds(float seconds) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }

    private double[] times() {
        double[] times = new double[keyframes.size()];
        for (int i = 1; i < times.length; i++) times[i] = times[i - 1] + keyframes.get(i).seconds;
        return times;
    }

    Sample sample(double seconds) {
        int n = keyframes.size();
        if (n == 0) return null;
        if (n == 1) {
            Keyframe only = keyframes.get(0);
            return new Sample(only.time, only.x, only.y, only.z, only.yaw, only.pitch, 0);
        }
        double[] t = times();
        double at = Math.max(0, Math.min(t[n - 1], seconds));
        int i = 0;
        while (i < n - 2 && at > t[i + 1]) i++;
        double h = t[i + 1] - t[i], u = h > 0 ? (at - t[i]) / h : 1;

        double[] yaw = new double[n];
        yaw[0] = keyframes.get(0).yaw;
        for (int j = 1; j < n; j++) yaw[j] = yaw[j - 1] + wrap(keyframes.get(j).yaw - keyframes.get(j - 1).yaw);

        Keyframe a = keyframes.get(i), b = keyframes.get(i + 1);
        double x = hermite(t, i, u, h, j -> keyframes.get(j).x);
        double y = hermite(t, i, u, h, j -> keyframes.get(j).y);
        double z = hermite(t, i, u, h, j -> keyframes.get(j).z);
        double turn = hermite(t, i, u, h, j -> yaw[j]);
        double pitch = hermite(t, i, u, h, j -> keyframes.get(j).pitch);
        double time = a.time + (b.time - a.time) * u;
        double speed = h > 0 ? (b.time - a.time) / (h * 1000.0) : 0;
        return new Sample(time, x, y, z, (float) turn, (float) Math.max(-90, Math.min(90, pitch)), speed);
    }

    private double hermite(double[] t, int i, double u, double h, java.util.function.IntToDoubleFunction value) {
        double p0 = value.applyAsDouble(i), p1 = value.applyAsDouble(i + 1);
        double m0 = slope(t, i, value), m1 = slope(t, i + 1, value);
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * h * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * h * m1;
    }

    private double slope(double[] t, int j, java.util.function.IntToDoubleFunction value) {
        if (j <= 0 || j >= t.length - 1) return 0;
        double span = t[j + 1] - t[j - 1];
        return span > 0 ? (value.applyAsDouble(j + 1) - value.applyAsDouble(j - 1)) / span : 0;
    }

    private static double wrap(double degrees) {
        degrees %= 360.0;
        if (degrees >= 180.0) degrees -= 360.0;
        if (degrees < -180.0) degrees += 360.0;
        return degrees;
    }
}
