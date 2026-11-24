package rip.tempest.replay;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ReplayFile implements Closeable {
    private static final Gson GSON = new Gson();

    final File file;
    final ReplayWriter.Metadata meta;
    private final ZipFile zip;

    private ReplayFile(File file, ZipFile zip, ReplayWriter.Metadata meta) {
        this.file = file;
        this.zip = zip;
        this.meta = meta;
    }

    public static ReplayFile open(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            ReplayWriter.Metadata meta = readMetadata(zip);
            if (zip.getEntry(ReplayWriter.PACKETS) == null) throw new IOException("no " + ReplayWriter.PACKETS + " in " + file.getName());
            return new ReplayFile(file, zip, meta);
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e;
        }
    }

    public static Info info(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            ReplayWriter.Metadata meta = readMetadata(zip);
            return new Info(meta.singleplayer, meta.serverName, meta.customServerName, meta.duration, meta.date, meta.mcversion);
        } catch (Exception e) {
            return null;
        }
    }

    public record Info(boolean singleplayer, String serverName, String customServerName, int duration, long date, String version) {
        public String server() {
            if (customServerName != null && !customServerName.isEmpty()) return customServerName;
            return serverName == null || serverName.isEmpty() ? (singleplayer ? "Singleplayer" : "Unknown server") : serverName;
        }
    }

    private static ReplayWriter.Metadata readMetadata(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(ReplayWriter.META);
        if (entry == null) throw new IOException("no " + ReplayWriter.META);
        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            ReplayWriter.Metadata meta = GSON.fromJson(reader, ReplayWriter.Metadata.class);
            if (meta == null) throw new IOException("empty " + ReplayWriter.META);
            return meta;
        }
    }

    Packets packets() throws IOException {
        return new Packets(zip.getInputStream(zip.getEntry(ReplayWriter.PACKETS)));
    }

    int lastPacketTime() {
        int last = 0;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(zip.getEntry(ReplayWriter.PACKETS)), 1 << 16))) {
            while (true) {
                int time = in.readInt();
                int length = in.readInt();
                if (length < 0 || in.skipBytes(length) < length) break;
                last = time;
            }
        } catch (IOException ignored) {
        }
        return last;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    record Packet(int time, byte[] data) {
    }

    static final class Packets implements Closeable {
        private final DataInputStream in;
        private boolean ended;

        private Packets(InputStream stream) {
            this.in = new DataInputStream(new BufferedInputStream(stream, 1 << 16));
        }

        Packet next() throws IOException {
            if (ended) return null;
            try {
                int time = in.readInt();
                int length = in.readInt();
                if (length < 0 || length > (1 << 24)) throw new IOException("bad packet length " + length);
                byte[] data = new byte[length];
                in.readFully(data);
                return new Packet(time, data);
            } catch (EOFException e) {
                ended = true;
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
