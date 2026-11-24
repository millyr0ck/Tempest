package rip.tempest.processes;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import com.google.gson.JsonObject;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.util.vector.Vector2f;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static rip.tempest.utils.Util.mc;

/**
 * Learns how real players turn as they hit, for Kill Aura's ML rotations. While recording, each
 * time another player within 6 blocks starts a swing, how far they turned that tick is written to
 * the preset as a "yaw,pitch" line. A loaded preset then nudges Kill Aura's aim by turns drawn from
 * it. Presets live in Tempest/rotationpresets; "ai" and "legit" come bundled.
 *
 * <p>From Yuri's RotationLearnerManager (AGPL-3.0). Changed here: preset names may only use
 * letters, digits, '-' and '_', so a name can never point outside the presets folder, and a preset
 * carried inside a config is rebuilt from the numbers it parses to instead of being copied byte for
 * byte, and never replaces a preset you already have.
 */
public final class RotationLearnerProcess {
    public static final RotationLearnerProcess INSTANCE = new RotationLearnerProcess();

    private static final File PRESET_DIR = new File(Tempest.NAME, "rotationpresets");
    private static final String BUNDLED_PATH = "/assets/minecraft/tempest/rotationpresets/";
    private static final String BUNDLED_MANIFEST = BUNDLED_PATH + "manifest.txt";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private static final double CAPTURE_RANGE = 6.0;
    private static final int RESERVOIR_CAPACITY = 300;
    private static final int FLUSH_INTERVAL = 20;

    private static final int MAX_IMPORT_BYTES = 1 << 20;
    private static final Random RANDOM = new Random();

    private static final ThreadPoolExecutor IO_EXECUTOR = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(512), r -> {
        Thread t = new Thread(r, "rotation-preset-io");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardOldestPolicy());

    private static final Map<Integer, Float> lastYaw = new HashMap<>();
    private static final Map<Integer, Float> lastPitch = new HashMap<>();
    private static final Map<Integer, Boolean> lastSwing = new HashMap<>();

    private static volatile boolean recording;
    private static volatile String activePresetName;
    private static BufferedWriter activeWriter;
    private static int flushCounter;
    private static volatile RotationModel activeModel;
    private static volatile String activeModelName;

    private static float lastAppliedYawDelta;
    private static float lastAppliedPitchDelta;

    private RotationLearnerProcess() {
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.theWorld == null || mc.thePlayer == null || !recording) return;

        for (EntityPlayer other : mc.theWorld.playerEntities) {
            if (other == mc.thePlayer) continue;

            int id = other.getEntityId();
            float yaw = other.rotationYaw;
            float pitch = other.rotationPitch;
            boolean swinging = other.isSwingInProgress;

            Float prevYaw = lastYaw.get(id);
            Float prevPitch = lastPitch.get(id);
            Boolean prevSwing = lastSwing.get(id);

            if (prevSwing != null && !prevSwing && swinging && prevYaw != null
                    && mc.thePlayer.getDistanceToEntity(other) <= CAPTURE_RANGE) {
                float yawDelta = MathHelper.wrapAngleTo180_float(yaw - prevYaw);
                float pitchDelta = pitch - prevPitch;
                if (yawDelta != 0f && pitchDelta != 0f) {
                    queueSample(yawDelta, pitchDelta);
                }
            }

            lastYaw.put(id, yaw);
            lastPitch.put(id, pitch);
            lastSwing.put(id, swinging);
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = event -> {
        lastYaw.clear();
        lastPitch.clear();
        lastSwing.clear();
    };

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    private static void queueSample(float yawDelta, float pitchDelta) {
        IO_EXECUTOR.execute(() -> {
            if (activeWriter == null) return;
            try {
                activeWriter.write(yawDelta + "," + pitchDelta);
                activeWriter.newLine();
                if (++flushCounter >= FLUSH_INTERVAL) {
                    activeWriter.flush();
                    flushCounter = 0;
                }
            } catch (IOException ignored) {
            }
        });
    }

    public static boolean startRecording(String name) {
        if (!isValidName(name)) return false;
        stopRecording();
        recording = true;
        activePresetName = name;
        IO_EXECUTOR.execute(() -> {
            try {
                PRESET_DIR.mkdirs();
                activeWriter = new BufferedWriter(new FileWriter(presetFile(name), true));
                flushCounter = 0;
            } catch (IOException ignored) {
            }
        });
        return true;
    }

    public static void stopRecording() {
        if (!recording) return;
        recording = false;
        IO_EXECUTOR.execute(RotationLearnerProcess::closeWriterQuietly);
    }

    private static void closeWriterQuietly() {
        if (activeWriter == null) return;
        try {
            activeWriter.flush();
            activeWriter.close();
        } catch (IOException ignored) {
        }
        activeWriter = null;
    }

    public static boolean loadPreset(String name) {
        if (!isValidName(name)) return false;

        File file = presetFile(name);
        InputStream input;
        try {
            input = file.exists() ? new FileInputStream(file) : RotationLearnerProcess.class.getResourceAsStream(BUNDLED_PATH + name + ".csv");
        } catch (IOException e) {
            return false;
        }
        RotationModel model = buildModel(readSamples(input));
        if (model == null) return false;

        activeModel = model;
        activeModelName = name;
        return true;
    }

    public static void unloadPreset() {
        activeModel = null;
        activeModelName = null;
    }

    public static List<String> listPresets() {
        LinkedHashSet<String> names = new LinkedHashSet<>();

        PRESET_DIR.mkdirs();
        File[] files = PRESET_DIR.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".csv") && isValidName(name.substring(0, name.length() - 4))) {
                    names.add(name.substring(0, name.length() - 4));
                }
            }
        }

        names.addAll(listBundledPresets());
        return new ArrayList<>(names);
    }

    private static List<String> listBundledPresets() {
        List<String> names = new ArrayList<>();
        try (InputStream input = RotationLearnerProcess.class.getResourceAsStream(BUNDLED_MANIFEST)) {
            if (input == null) return names;
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (isValidName(line)) names.add(line);
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    public static boolean deletePreset(String name) {
        if (!isValidName(name)) return false;
        File file = presetFile(name);
        return file.exists() && file.delete();
    }

    public static boolean isRecording() {
        return recording;
    }

    public static String getActivePresetName() {
        return activePresetName;
    }

    public static boolean hasModelLoaded() {
        return activeModel != null;
    }

    public static String getLoadedModelName() {
        return activeModelName;
    }

    public static void resetSmoothing() {
        lastAppliedYawDelta = 0f;
        lastAppliedPitchDelta = 0f;
    }

    public static Vector2f humanize(Vector2f rotation, float weight, float ease) {
        RotationModel model = activeModel;
        if (model == null) return rotation;

        float rawYawDelta;
        float rawPitchDelta;

        if (!model.yawReservoir.isEmpty() && RANDOM.nextBoolean()) {
            int index = RANDOM.nextInt(model.yawReservoir.size());
            rawYawDelta = model.yawReservoir.get(index);
            rawPitchDelta = model.pitchReservoir.get(index);
        } else {
            rawYawDelta = (float) (model.yawMean + RANDOM.nextGaussian() * model.yawStdDev);
            rawPitchDelta = (float) (model.pitchMean + RANDOM.nextGaussian() * model.pitchStdDev);
        }

        float clampedEase = MathHelper.clamp_float(ease, 0.01f, 1.0f);
        float easedYawDelta = lastAppliedYawDelta + (rawYawDelta - lastAppliedYawDelta) * clampedEase;
        float easedPitchDelta = lastAppliedPitchDelta + (rawPitchDelta - lastAppliedPitchDelta) * clampedEase;

        lastAppliedYawDelta = easedYawDelta;
        lastAppliedPitchDelta = easedPitchDelta;

        float yaw = rotation.x + easedYawDelta * weight;
        float pitch = MathHelper.clamp_float(rotation.y + easedPitchDelta * weight, -90f, 90f);
        return new Vector2f(yaw, pitch);
    }

    public static JsonObject exportLoadedPreset() {
        String name = activeModelName;
        if (name == null) return null;

        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        File file = presetFile(name);
        if (file.exists()) {
            try {
                object.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath())));
            } catch (IOException ignored) {
            }
        }
        return object;
    }

    public static void importPreset(JsonObject object) {
        try {
            String name = object.get("name").getAsString();
            if (!isValidName(name)) return;

            if (!object.has("data")) {
                loadPreset(name);
                return;
            }

            byte[] bytes = Base64.getDecoder().decode(object.get("data").getAsString());
            if (bytes.length > MAX_IMPORT_BYTES) return;

            List<float[]> samples = readSamples(new ByteArrayInputStream(bytes));
            RotationModel model = buildModel(samples);
            if (model == null) return;

            File file = presetFile(name);
            if (!file.exists()) {
                IO_EXECUTOR.execute(() -> writeSamples(file, samples));
            }

            activeModel = model;
            activeModelName = name;
        } catch (RuntimeException ignored) {
        }
    }

    private static void writeSamples(File file, List<float[]> samples) {
        PRESET_DIR.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (float[] sample : samples) {
                writer.write(sample[0] + "," + sample[1]);
                writer.newLine();
            }
        } catch (IOException ignored) {
        }
    }

    private static List<float[]> readSamples(InputStream input) {
        List<float[]> samples = new ArrayList<>();
        if (input == null) return samples;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                try {
                    float yawDelta = Float.parseFloat(line.substring(0, comma).trim());
                    float pitchDelta = Float.parseFloat(line.substring(comma + 1).trim());
                    if (Float.isFinite(yawDelta) && Float.isFinite(pitchDelta)) {
                        samples.add(new float[]{yawDelta, pitchDelta});
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return samples;
    }

    private static RotationModel buildModel(List<float[]> samples) {
        if (samples.isEmpty()) return null;

        double yawSum = 0, pitchSum = 0;
        double yawSumSq = 0, pitchSumSq = 0;
        int count = 0;
        List<Float> yawReservoir = new ArrayList<>(RESERVOIR_CAPACITY);
        List<Float> pitchReservoir = new ArrayList<>(RESERVOIR_CAPACITY);

        for (float[] sample : samples) {
            float yawDelta = sample[0];
            float pitchDelta = sample[1];
            yawSum += yawDelta;
            pitchSum += pitchDelta;
            yawSumSq += yawDelta * (double) yawDelta;
            pitchSumSq += pitchDelta * (double) pitchDelta;
            count++;

            if (yawReservoir.size() < RESERVOIR_CAPACITY) {
                yawReservoir.add(yawDelta);
                pitchReservoir.add(pitchDelta);
            } else {
                int replaceIndex = RANDOM.nextInt(count);
                if (replaceIndex < RESERVOIR_CAPACITY) {
                    yawReservoir.set(replaceIndex, yawDelta);
                    pitchReservoir.set(replaceIndex, pitchDelta);
                }
            }
        }

        float yawMean = (float) (yawSum / count);
        float pitchMean = (float) (pitchSum / count);

        RotationModel model = new RotationModel();
        model.yawMean = yawMean;
        model.pitchMean = pitchMean;
        model.yawStdDev = (float) Math.sqrt(Math.max(0, yawSumSq / count - yawMean * yawMean));
        model.pitchStdDev = (float) Math.sqrt(Math.max(0, pitchSumSq / count - pitchMean * pitchMean));
        model.yawReservoir = yawReservoir;
        model.pitchReservoir = pitchReservoir;
        return model;
    }

    private static File presetFile(String name) {
        return new File(PRESET_DIR, name + ".csv");
    }

    private static final class RotationModel {
        float yawMean;
        float pitchMean;
        float yawStdDev;
        float pitchStdDev;
        List<Float> yawReservoir;
        List<Float> pitchReservoir;
    }
}
