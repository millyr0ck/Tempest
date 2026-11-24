package rip.tempest.utils.render.knife;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class KnifeSounds {
    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 2, true, false);
    private static final int BLOCK_FRAMES = 441, LINE_FRAMES = 2048, MAX_VOICES = 16;
    private static final long CLOSE_AFTER_MS = 3000L;

    private static final class Sound {
        final float volumeLow, volumeHigh, pitchLow, pitchHigh;
        final String[] files;

        Sound(final float volumeLow, final float volumeHigh, final float pitchLow, final float pitchHigh, final String[] files) {
            this.volumeLow = volumeLow;
            this.volumeHigh = volumeHigh;
            this.pitchLow = pitchLow;
            this.pitchHigh = pitchHigh;
            this.files = files;
        }
    }

    private static final class Request {
        final Sound sound;
        final float gain;
        final boolean single;

        Request(final Sound sound, final float gain) {
            this(sound, gain, false);
        }

        Request(final Sound sound, final float gain, final boolean single) {
            this.sound = sound;
            this.gain = gain;
            this.single = single;
        }
    }

    private static final class Voice {
        final short[] samples;
        final double step;
        final float gain;
        double frame;

        Voice(final short[] samples, final double step, final float gain) {
            this.samples = samples;
            this.step = step;
            this.gain = gain;
        }
    }

    private final File folder;
    private final Map<String, Sound> table = new HashMap<>();
    private final Random random = new Random();

    private final List<Request> requests = new ArrayList<>();
    private Thread thread;
    private boolean stopRequested;
    private boolean failed;

    private final Map<String, short[]> loaded = new HashMap<>();
    private final List<Voice> voices = new ArrayList<>();
    private final int[] accumulated = new int[BLOCK_FRAMES * 2];

    public KnifeSounds(final File folder) {
        this.folder = folder;
        try {
            for (final String line : Files.readAllLines(new File(folder, "sounds.txt").toPath(), StandardCharsets.UTF_8)) {
                final String[] parts = line.trim().split(" ");
                if (parts.length < 6) continue;
                final String[] files = new String[parts.length - 5];
                System.arraycopy(parts, 5, files, 0, files.length);
                table.put(parts[0], new Sound(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4]), files));
            }
        } catch (final IOException | NumberFormatException exception) {
            System.err.println("Inspect: could not read the sound list: " + exception);
            failed = true;
        }
    }

    public void play(final String name, final float gain) {
        play(name, gain, false);
    }

    public void playSingle(final String name, final float gain) {
        play(name, gain, true);
    }

    private void play(final String name, final float gain, final boolean single) {
        final Sound sound = table.get(name.toLowerCase(Locale.ROOT));
        if (sound == null) return;
        synchronized (requests) {
            if (failed) return;
            requests.add(new Request(sound, gain, single));
            if (thread == null) {
                thread = new Thread(this::run, "Knife Sounds");
                thread.setDaemon(true);
                thread.setPriority(Thread.MAX_PRIORITY - 1);
                thread.start();
            }
        }
    }

    public void stop() {
        synchronized (requests) {
            requests.clear();
            stopRequested = true;
        }
    }

    private void run() {
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, LINE_FRAMES * 4);
            line.start();
            final short[] mixed = new short[BLOCK_FRAMES * 2];
            final ByteBuffer bytes = ByteBuffer.allocate(BLOCK_FRAMES * 4).order(ByteOrder.LITTLE_ENDIAN);
            long lastSound = System.currentTimeMillis();
            while (true) {
                final List<Request> taken;
                synchronized (requests) {
                    if (stopRequested) {
                        voices.clear();
                        stopRequested = false;
                    }
                    taken = new ArrayList<>(requests);
                    requests.clear();
                    if (taken.isEmpty() && voices.isEmpty() && System.currentTimeMillis() - lastSound > CLOSE_AFTER_MS) {
                        thread = null;
                        return;
                    }
                }
                for (final Request request : taken) start(request);
                if (mix(mixed)) lastSound = System.currentTimeMillis();
                bytes.clear();
                for (final short sample : mixed) bytes.putShort(sample);
                line.write(bytes.array(), 0, bytes.capacity());
            }
        } catch (final LineUnavailableException | RuntimeException exception) {
            System.err.println("Inspect: could not play sounds: " + exception);
            synchronized (requests) {
                failed = true;
                thread = null;
                requests.clear();
            }
        } finally {
            if (line != null) line.close();
        }
    }

    private void start(final Request request) {
        final Sound sound = request.sound;
        final short[] samples = load(sound.files[random.nextInt(sound.files.length)]);
        if (samples.length == 0) return;
        if (request.single) voices.removeIf(voice -> voice.samples == samples);
        final float volume = sound.volumeLow + (sound.volumeHigh - sound.volumeLow) * random.nextFloat();
        final float pitch = sound.pitchLow + (sound.pitchHigh - sound.pitchLow) * random.nextFloat();
        if (voices.size() >= MAX_VOICES) voices.remove(0);
        voices.add(new Voice(samples, pitch / 100.0, volume * request.gain));
    }

    private short[] load(final String file) {
        final short[] known = loaded.get(file);
        if (known != null) return known;
        short[] samples = new short[0];
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(new File(folder, file + ".wav"));
             AudioInputStream converted = AudioSystem.getAudioInputStream(FORMAT, raw)) {
            final byte[] bytes = converted.readAllBytes();
            samples = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        } catch (final IOException | UnsupportedAudioFileException | IllegalArgumentException exception) {
            System.err.println("Inspect: could not read " + file + ": " + exception);
        }
        loaded.put(file, samples);
        return samples;
    }

    private boolean mix(final short[] out) {
        final boolean active = !voices.isEmpty();
        java.util.Arrays.fill(accumulated, 0);
        for (final Iterator<Voice> iterator = voices.iterator(); iterator.hasNext(); ) {
            final Voice voice = iterator.next();
            final int frames = voice.samples.length / 2;
            for (int f = 0; f < BLOCK_FRAMES; f++) {
                final int a = (int) voice.frame;
                if (a + 1 >= frames) {
                    iterator.remove();
                    break;
                }
                final float t = (float) (voice.frame - a);
                final short[] s = voice.samples;
                accumulated[f * 2] += (int) ((s[a * 2] + (s[a * 2 + 2] - s[a * 2]) * t) * voice.gain);
                accumulated[f * 2 + 1] += (int) ((s[a * 2 + 1] + (s[a * 2 + 3] - s[a * 2 + 1]) * t) * voice.gain);
                voice.frame += voice.step;
            }
        }
        for (int k = 0; k < out.length; k++) {
            out[k] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, accumulated[k]));
        }
        return active;
    }
}
