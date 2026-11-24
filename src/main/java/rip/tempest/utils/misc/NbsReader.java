package rip.tempest.utils.misc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class NbsReader {
    private static final int MAX_FILE = 8 << 20;
    private static final int MAX_NOTES = 500_000;
    private static final int MAX_STRING = 1 << 16;

    private NbsReader() {
    }

    public record Note(int tick, int layer, int instrument, int key, int velocity) {
    }

    public record Song(String name, String author, double ticksPerSecond, int vanillaInstruments, List<Note> notes, int[] layerVolumes) {
        public int volume(int layer) {
            return layer >= 0 && layer < layerVolumes.length ? layerVolumes[layer] : 100;
        }
    }

    public static Song read(File file) throws IOException {
        if (file.length() > MAX_FILE) throw new IOException("File too big");
        return read(Files.readAllBytes(file.toPath()));
    }

    public static Song read(byte[] data) throws IOException {
        Input in = new Input(data);

        int first = in.unsignedShort();
        boolean classic = first != 0;
        int version = 0, vanillaInstruments = 10;
        if (!classic) {
            version = in.unsignedByte();
            vanillaInstruments = in.unsignedByte();
            if (version >= 3) in.unsignedShort();
        }
        int layerCount = in.unsignedShort();
        String name = in.string();
        String author = in.string();
        String originalAuthor = in.string();
        in.string();
        double tempo = in.unsignedShort() / 100.0;
        in.skip(3);
        in.skip(20);
        in.string();
        if (version >= 4) in.skip(4);

        List<Note> notes = new ArrayList<>();
        int tick = -1;
        while (true) {
            int tickJump = in.unsignedShort();
            if (tickJump == 0) break;
            tick += tickJump;
            int layer = -1;
            while (true) {
                int layerJump = in.unsignedShort();
                if (layerJump == 0) break;
                layer += layerJump;
                int instrument = in.unsignedByte();
                int key = in.unsignedByte();
                int velocity = 100;
                if (version >= 4) {
                    velocity = in.unsignedByte();
                    in.skip(1);
                    in.skip(2);
                }
                if (notes.size() >= MAX_NOTES) throw new IOException("Too many notes");
                notes.add(new Note(tick, layer, instrument, key, velocity));
            }
        }

        int[] volumes = new int[Math.min(layerCount, 4096)];
        java.util.Arrays.fill(volumes, 100);
        try {
            for (int i = 0; i < volumes.length; i++) {
                in.string();
                if (version >= 4) in.skip(1);
                volumes[i] = in.unsignedByte();
                if (version >= 2) in.skip(1);
            }
        } catch (IOException ignored) {
        }

        if (tempo <= 0 || tempo > 100) tempo = 10;
        String shownAuthor = !author.isEmpty() ? author : originalAuthor;
        return new Song(name, shownAuthor, tempo, vanillaInstruments, notes, volumes);
    }

    private static final class Input {
        private final byte[] data;
        private int pos;

        Input(byte[] data) {
            this.data = data;
        }

        private void need(int count) throws IOException {
            if (count < 0 || pos + count > data.length) throw new IOException("Unexpected end of file");
        }

        int unsignedByte() throws IOException {
            need(1);
            return data[pos++] & 0xFF;
        }

        int unsignedShort() throws IOException {
            need(2);
            int value = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8;
            pos += 2;
            return value;
        }

        int integer() throws IOException {
            need(4);
            int value = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8 | (data[pos + 2] & 0xFF) << 16 | (data[pos + 3] & 0xFF) << 24;
            pos += 4;
            return value;
        }

        String string() throws IOException {
            int length = integer();
            if (length < 0 || length > MAX_STRING) throw new IOException("Bad string length");
            need(length);
            String value = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }

        void skip(int count) throws IOException {
            need(count);
            pos += count;
        }
    }
}
