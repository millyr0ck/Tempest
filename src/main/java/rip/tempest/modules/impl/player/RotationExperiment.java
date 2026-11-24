package rip.tempest.modules.impl.player;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.RayCastUtils;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static rip.tempest.utils.Util.mc;

final class RotationExperiment {
    private static final float SPEED_MIN = 0.4f, SPEED_MAX = 10f;
    private static final float SPREAD_MAX = 0.6f;
    private static final float EASE_MIN = 0.25f;
    private static final float YAW_JITTER_MAX = 4f, PITCH_JITTER_MAX = 2f, PITCH_BIAS_MAX = 4f;
    private static final float OVERSHOOT_CHANCE_MAX = 0.5f, OVERSHOOT_MIN = 0.5f, OVERSHOOT_MAX = 6f;
    private static final int DELAY_MAX = 3;

    private static final float BASE_SPEED = 5f;

    private static final int CANDIDATES = 80;

    static final class Profile {
        int id;
        String status = "TRIED";

        String anticheat = "";

        float speed;

        float spread;

        float ease;

        float yawJitter, pitchJitter, pitchBias;

        float overshootChance, overshootDegrees;

        int delay;

        boolean wander;

        static Profile of(int id, float speed, float spread, float ease, float yawJitter, float pitchJitter, float pitchBias,
                          float overshootChance, float overshootDegrees, int delay, boolean wander) {
            Profile p = new Profile();
            p.id = id;
            p.speed = speed;
            p.spread = spread;
            p.ease = ease;
            p.yawJitter = yawJitter;
            p.pitchJitter = pitchJitter;
            p.pitchBias = pitchBias;
            p.overshootChance = overshootChance;
            p.overshootDegrees = overshootDegrees;
            p.delay = delay;
            p.wander = wander;
            return p;
        }

        int degreesPerTick() {
            return Math.round(speed * 36f);
        }

        String values() {
            return String.format(Locale.ROOT,
                    "speed=%.2f spread=%.2f ease=%.2f yawJitter=%.2f pitchJitter=%.2f pitchBias=%.2f overshoot=%.2f overshootDeg=%.2f delay=%d wander=%b",
                    speed, spread, ease, yawJitter, pitchJitter, pitchBias, overshootChance, overshootDegrees, delay, wander);
        }

        Profile on(String otherAnticheat) {
            Profile copy = new Profile();
            copy.id = id;
            copy.anticheat = otherAnticheat;
            copy.speed = speed;
            copy.spread = spread;
            copy.ease = ease;
            copy.yawJitter = yawJitter;
            copy.pitchJitter = pitchJitter;
            copy.pitchBias = pitchBias;
            copy.overshootChance = overshootChance;
            copy.overshootDegrees = overshootDegrees;
            copy.delay = delay;
            copy.wander = wander;
            return copy;
        }

        float[] normalised() {
            return new float[]{
                    (float) (Math.log(speed / SPEED_MIN) / Math.log(SPEED_MAX / SPEED_MIN)),
                    spread / SPREAD_MAX,
                    (ease - EASE_MIN) / (1f - EASE_MIN),
                    yawJitter / YAW_JITTER_MAX,
                    pitchJitter / PITCH_JITTER_MAX,
                    (pitchBias + PITCH_BIAS_MAX) / (2f * PITCH_BIAS_MAX),
                    overshootChance / OVERSHOOT_CHANCE_MAX,
                    (overshootDegrees - OVERSHOOT_MIN) / (OVERSHOOT_MAX - OVERSHOOT_MIN),
                    delay / (float) DELAY_MAX,
                    wander ? 1f : 0f
            };
        }
    }

    private static final float[] WEIGHTS = {2f, 1f, 1f, 1f, 0.7f, 0.8f, 0.8f, 0.6f, 1f, 0.6f};

    private final Random random = new Random();

    private final List<Profile> tried = new ArrayList<>();
    private final Supplier<String> anticheatSetting;
    private boolean loaded;
    private int lastId;
    private Profile current;

    private final ArrayDeque<float[]> delayed = new ArrayDeque<>();
    private int lastTick = Integer.MIN_VALUE;
    private float lastTargetYaw = Float.NaN;
    private int overshootTicks;
    private float overshootYaw;

    RotationExperiment(Supplier<String> anticheatSetting) {
        this.anticheatSetting = anticheatSetting;
    }

    static RotationExperiment fixed(Profile profile) {
        RotationExperiment fixed = new RotationExperiment(() -> "");
        fixed.current = profile;
        return fixed;
    }

    private String anticheat() {
        String name = anticheatSetting.get();
        return name == null ? "" : name.trim();
    }

    Profile current() {
        if (current == null) pick(SPEED_MIN, SPEED_MAX, null);
        return current;
    }

    void faster() {
        float speed = current().speed;
        if (speed >= SPEED_MAX * 0.95f) {
            pick(SPEED_MAX * 0.8f, SPEED_MAX, "Already near the fastest; picked another fast one.");
        } else {
            pick(Math.min(SPEED_MAX, speed * 1.15f), Math.min(SPEED_MAX, speed * 2f), null);
        }
        announce("Faster", speed);
    }

    void slower() {
        float speed = current().speed;
        if (speed <= SPEED_MIN * 1.05f) {
            pick(SPEED_MIN, SPEED_MIN * 1.3f, "Already near the slowest; picked another slow one.");
        } else {
            pick(Math.max(SPEED_MIN, speed * 0.5f), Math.max(SPEED_MIN, speed * 0.87f), null);
        }
        announce("Slower", speed);
    }

    void reroll() {
        float speed = current().speed;
        pick(SPEED_MIN, SPEED_MAX, null);
        announce("New", speed);
    }

    void markWorking() {
        Profile p = current();
        String ac = anticheat();
        mark(p, "WORKS");
        NotificationManager.post(NotificationType.SUCCESS, "Experiment #" + p.id,
                ac.isEmpty() ? "Marked working and saved." : "Marked working on " + ac + " and saved.");
        chat("§a#" + p.id + " works" + (ac.isEmpty() ? "" : " on " + ac) + "§7: " + p.values());
        if (ac.isEmpty()) chat("§eTip: name the anticheat in Scaffold's Anticheat setting so it's saved with it.");
    }

    void markFlagged() {
        Profile p = current();
        String ac = anticheat();
        mark(p, "FLAGGED");
        float speed = p.speed;
        pick(SPEED_MIN, SPEED_MAX, null);
        announce("#" + p.id + " flagged" + (ac.isEmpty() ? "" : " on " + ac), speed);
    }

    private void announce(String what, float oldSpeed) {
        Profile p = current;
        NotificationManager.post(NotificationType.INFO, what + ": now #" + p.id,
                String.format(Locale.ROOT, "Speed %.2f to %.2f (%d°/tick)", oldSpeed, p.speed, p.degreesPerTick()));
    }

    private static void chat(String message) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("§7[Experiment] " + message));
    }

    private void pick(float low, float high, String note) {
        load();
        String ac = anticheat();

        if (current != null) entryFor(current, ac);
        Profile best = null;
        double bestDistance = -1;
        for (int i = 0; i < CANDIDATES; i++) {
            Profile candidate = randomProfile(low, Math.max(low, high));
            double distance = nearestTried(candidate);
            if (distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        best.id = ++lastId;
        best.anticheat = ac;
        tried.add(best);
        current = best;
        resetTickState();
        save(best, "TRIED");
        if (note != null) chat(note);
    }

    private Profile randomProfile(float speedLow, float speedHigh) {
        Profile p = new Profile();

        p.speed = (float) Math.exp(Math.log(speedLow) + random.nextDouble() * (Math.log(speedHigh) - Math.log(speedLow)));
        p.spread = random.nextFloat() * SPREAD_MAX;
        p.ease = EASE_MIN + random.nextFloat() * (1f - EASE_MIN);
        p.yawJitter = random.nextFloat() * YAW_JITTER_MAX;
        p.pitchJitter = random.nextFloat() * PITCH_JITTER_MAX;
        p.pitchBias = (random.nextFloat() * 2f - 1f) * PITCH_BIAS_MAX;
        p.overshootChance = random.nextFloat() * OVERSHOOT_CHANCE_MAX;
        p.overshootDegrees = OVERSHOOT_MIN + random.nextFloat() * (OVERSHOOT_MAX - OVERSHOOT_MIN);
        p.delay = random.nextInt(DELAY_MAX + 1);
        p.wander = random.nextBoolean();
        return p;
    }

    private double nearestTried(Profile candidate) {
        String ac = anticheat();
        float[] a = candidate.normalised();
        double nearest = Double.MAX_VALUE;
        for (Profile p : tried) {
            if (!p.anticheat.equalsIgnoreCase(ac)) continue;
            float[] b = p.normalised();
            double sum = 0;
            for (int i = 0; i < a.length; i++) {
                double d = (a[i] - b[i]) * WEIGHTS[i];
                sum += d * d;
            }
            nearest = Math.min(nearest, sum);
        }
        return nearest == Double.MAX_VALUE ? nearest : Math.sqrt(nearest);
    }

    private int triedCount() {
        String ac = anticheat();
        int count = 0;
        for (Profile p : tried) if (p.anticheat.equalsIgnoreCase(ac)) count++;
        return count;
    }

    private Profile entryFor(Profile p, String ac) {
        for (Profile t : tried) {
            if (t.id == p.id && t.anticheat.equalsIgnoreCase(ac)) return t;
        }
        Profile entry = p.on(ac);
        tried.add(entry);
        save(entry, "TRIED");
        return entry;
    }

    float apply(Vector2f aim, float modeSpeed, boolean modeChoseSpeed, BlockPos blockFace, EnumFacing face, boolean strict) {
        Profile p = current();
        int tick = mc.thePlayer.ticksExisted;
        boolean newTick = tick != lastTick;
        lastTick = tick;

        if (newTick) {
            delayed.addLast(new float[]{aim.x, aim.y});
        } else if (!delayed.isEmpty()) {
            float[] latest = delayed.peekLast();
            latest[0] = aim.x;
            latest[1] = aim.y;
        }
        while (delayed.size() > p.delay + 1) delayed.removeFirst();
        float[] lagged = delayed.peekFirst();
        float yaw = lagged[0], pitch = lagged[1];

        if (newTick) {
            if (!Float.isNaN(lastTargetYaw)) {
                float turn = MathHelper.wrapAngleTo180_float(yaw - lastTargetYaw);
                if (Math.abs(turn) > 10f && random.nextFloat() < p.overshootChance) {
                    overshootTicks = 1 + random.nextInt(2);
                    overshootYaw = Math.signum(turn) * p.overshootDegrees;
                }
            }
            lastTargetYaw = yaw;
            if (overshootTicks > 0) overshootTicks--;
        }
        if (overshootTicks > 0) yaw += overshootYaw;

        if (p.yawJitter > 0f || p.pitchJitter > 0f || p.pitchBias != 0f) {
            float jitteredYaw = yaw + (random.nextFloat() * 2f - 1f) * p.yawJitter;
            float jitteredPitch = MathHelper.clamp_float(pitch + p.pitchBias + (random.nextFloat() * 2f - 1f) * p.pitchJitter, -90f, 90f);
            if (RayCastUtils.overBlock(new Vector2f(jitteredYaw, jitteredPitch), face, blockFace, strict)) {
                yaw = jitteredYaw;
                pitch = jitteredPitch;
            }
        }

        Vector2f from = RotationProcess.lastRotations;
        if (p.ease < 1f && from != null) {
            float yawLeft = MathHelper.wrapAngleTo180_float(yaw - from.x), pitchLeft = pitch - from.y;
            if (Math.abs(yawLeft) + Math.abs(pitchLeft) > 1.5f) {
                yaw = from.x + yawLeft * p.ease;
                pitch = from.y + pitchLeft * p.ease;
            }
        }

        aim.set(yaw, pitch);

        float speed = modeChoseSpeed ? modeSpeed * (p.speed / BASE_SPEED) : p.speed;
        return Math.max(0.05f, speed * (1f + (random.nextFloat() * 2f - 1f) * p.spread));
    }

    Function<Vector2f, Boolean> wander(BlockPos blockFace, EnumFacing face, boolean strict) {
        if (!current().wander) return null;
        return rotation -> RayCastUtils.overBlock(rotation, face, blockFace, strict);
    }

    void resetTickState() {
        delayed.clear();
        lastTick = Integer.MIN_VALUE;
        lastTargetYaw = Float.NaN;
        overshootTicks = 0;
    }

    void drawPanel() {
        Profile p = current();
        CustomFontRenderer font = FontProcess.getFont("tempest");
        String ac = anticheat();
        String[] lines = {
                "Experiment #" + p.id + " · " + (ac.isEmpty() ? "no anticheat named" : ac + " (" + triedCount() + " tried)"),
                String.format(Locale.ROOT, "speed %.2f  ±%d%%  (%d°/tick)", p.speed, Math.round(p.spread * 100), p.degreesPerTick()),
                String.format(Locale.ROOT, "ease %.2f   delay %d tick%s", p.ease, p.delay, p.delay == 1 ? "" : "s"),
                String.format(Locale.ROOT, "jitter %.1f° / %.1f°   bias %+.1f°", p.yawJitter, p.pitchJitter, p.pitchBias),
                String.format(Locale.ROOT, "overshoot %d%% × %.1f°   wander %s", Math.round(p.overshootChance * 100), p.overshootDegrees, p.wander ? "on" : "off"),
                "[ slower   ] faster   \\ new",
                "' works   ; flagged"
        };

        float pitchY = font.getHeight() + 4f;
        float width = 0f;
        for (String line : lines) width = Math.max(width, font.getStringWidth(line));
        width += 10f;
        float height = lines.length * pitchY + 5f;
        ScaledResolution sr = new ScaledResolution(mc);
        float x = 6f, y = sr.getScaledHeight() / 2f - height / 2f;

        RenderUtils.drawRoundedRect(x, y, width, height, 4f, new Color(12, 12, 14, 170));
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? ColorProcess.getColor().getRGB() : i >= lines.length - 2 ? 0xFF9A9AA2 : 0xFFE6E6EA;
            font.drawStringWithShadow(lines[i], x + 5f, y + 5f + i * pitchY, color);
        }
    }

    private static File file() {
        return new File(mc.mcDataDir, Tempest.NAME + "/scaffold-rotations.txt");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        File file = file();
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Profile p = parse(line.trim());
                if (p == null) continue;
                lastId = Math.max(lastId, p.id);

                Profile existing = null;
                for (Profile t : tried) if (t.id == p.id && t.anticheat.equalsIgnoreCase(p.anticheat)) existing = t;
                if (existing != null) existing.status = p.status;
                else tried.add(p);
            }
        } catch (Exception ignored) {
        }
    }

    private static Profile parse(String line) {
        if (!line.startsWith("#")) return null;
        String anticheat = "";
        int acAt = line.indexOf(" ac=");
        if (acAt >= 0) {
            anticheat = line.substring(acAt + 4).trim();
            line = line.substring(0, acAt);
        }
        String[] parts = line.split("\\s+");
        if (parts.length < 3) return null;
        try {
            Profile p = new Profile();
            p.id = Integer.parseInt(parts[0].substring(1));
            p.status = parts[1];
            p.anticheat = anticheat;
            for (int i = 2; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq < 0) continue;
                String key = parts[i].substring(0, eq), value = parts[i].substring(eq + 1);
                switch (key) {
                    case "speed" -> p.speed = Float.parseFloat(value);
                    case "spread" -> p.spread = Float.parseFloat(value);
                    case "ease" -> p.ease = Float.parseFloat(value);
                    case "yawJitter" -> p.yawJitter = Float.parseFloat(value);
                    case "pitchJitter" -> p.pitchJitter = Float.parseFloat(value);
                    case "pitchBias" -> p.pitchBias = Float.parseFloat(value);
                    case "overshoot" -> p.overshootChance = Float.parseFloat(value);
                    case "overshootDeg" -> p.overshootDegrees = Float.parseFloat(value);
                    case "delay" -> p.delay = Integer.parseInt(value);
                    case "wander" -> p.wander = Boolean.parseBoolean(value);
                    default -> {
                    }
                }
            }
            return p.speed > 0f && p.ease > 0f ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void mark(Profile p, String status) {
        Profile entry = entryFor(p, anticheat());
        entry.status = status;
        save(entry, status);
    }

    private static void save(Profile p, String status) {
        try {
            File file = file();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.println("#" + p.id + " " + status + " " + p.values() + (p.anticheat.isEmpty() ? "" : " ac=" + p.anticheat));
            }
        } catch (Exception ignored) {
        }
    }
}
