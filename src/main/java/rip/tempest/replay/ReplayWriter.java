package rip.tempest.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes one recording in ReplayMod's .mcpr layout. While recording, everything goes into a
 * "name.mcpr.tmp" folder (so a crash loses nothing); when the recording ends the folder is zipped
 * into "name.mcpr":
 *
 * <ul>
 * <li>recording.tmcpr: every packet as [int time in ms][int length][packet id + data], in the
 * 1.8 protocol (47), starting with the login-success packet;</li>
 * <li>metaData.json: what ReplayMod's ReplayMetaData holds;</li>
 * <li>mods.json: the mods the replay needs (none).</li>
 * </ul>
 *
 * <p>Disk writes happen on a thread of their own, in the order they were asked for.
 *
 * <p>Based on ReplayMod (GPL-3.0-or-later): its recording PacketListener and ReplayStudio's
 * replay file layout.
 */
final class ReplayWriter {
    static final String PACKETS = "recording.tmcpr", META = "metaData.json", MODS = "mods.json";
    private static final Gson GSON = new GsonBuilder().create();

    static final class Metadata {
        boolean singleplayer;
        String serverName;
        String customServerName;
        int duration;
        long date;
        String mcversion = "1.8.9";
        String fileFormat = "MCPR";

        int fileFormatVersion = 14;
        int protocol = 47;
        String generator;
        int selfId = -1;
        String[] players = new String[0];
    }

    final Metadata meta;
    private final File folder, temp;
    private final String name;
    private final DataOutputStream packets;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Replay writer");
        thread.setDaemon(true);
        return thread;
    });
    private volatile IOException failure;

    private static final java.util.Set<String> BUSY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    ReplayWriter(File folder, String name, Metadata meta) throws IOException {
        this.folder = folder;
        this.name = name;
        this.meta = meta;
        this.temp = new File(folder, name + ".mcpr.tmp");
        BUSY.add(temp.getName());
        if (!temp.isDirectory() && !temp.mkdirs()) throw new IOException("can't make " + temp);
        this.packets = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(temp, PACKETS)), 1 << 16));
        writeText(new File(temp, MODS), "{\"requiredMods\":[]}");
        saveMetadata();
    }

    void write(int time, byte[] packet) {
        io.execute(() -> {
            if (failure != null) return;
            try {
                packets.writeInt(time);
                packets.writeInt(packet.length);
                packets.write(packet);
            } catch (IOException e) {
                failure = e;
            }
        });
    }

    void saveMetadata() {
        String json;
        synchronized (meta) {
            json = GSON.toJson(meta);
        }
        io.execute(() -> {
            try {
                writeText(new File(temp, META), json);
            } catch (IOException e) {
                failure = e;
            }
        });
    }

    File finish(int duration) throws IOException {
        synchronized (meta) {
            meta.duration = duration;
        }
        saveMetadata();
        io.execute(() -> {
            try {
                packets.close();
            } catch (IOException e) {
                failure = e;
            }
        });
        io.shutdown();
        try {
            if (!io.awaitTermination(60, TimeUnit.SECONDS)) throw new IOException("replay writer timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while saving", e);
        }
        try {
            if (failure != null) throw failure;
            File out = pack(folder, temp, name);
            deleteFolder(temp);
            return out;
        } finally {
            BUSY.remove(temp.getName());
        }
    }

    private static File pack(File folder, File temp, String name) throws IOException {
        File out = new File(folder, name + ".mcpr");
        for (int i = 1; out.exists(); i++) out = new File(folder, name + "_" + i + ".mcpr");
        File partial = new File(folder, out.getName() + ".part");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(partial)))) {
            for (String entry : new String[]{META, PACKETS, MODS}) {
                File file = new File(temp, entry);
                if (!file.isFile()) continue;
                zip.putNextEntry(new ZipEntry(entry));
                try (InputStream in = new FileInputStream(file)) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
        }
        Files.move(partial.toPath(), out.toPath());
        return out;
    }

    static int recoverLeftovers(File folder) {
        File[] leftovers = folder.listFiles(f -> f.isDirectory() && f.getName().endsWith(".mcpr.tmp") && !BUSY.contains(f.getName()));
        if (leftovers == null) return 0;
        int recovered = 0;
        for (File temp : leftovers) {
            File packets = new File(temp, PACKETS), meta = new File(temp, META);
            if (!packets.isFile() || !meta.isFile()) continue;
            try {
                int lastTime = trimToWholePackets(packets);
                Metadata data = GSON.fromJson(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8), Metadata.class);
                if (data == null) continue;
                data.duration = Math.max(data.duration, lastTime);
                writeText(meta, GSON.toJson(data));
                String name = temp.getName().substring(0, temp.getName().length() - ".mcpr.tmp".length());
                pack(folder, temp, name);
                deleteFolder(temp);
                recovered++;
            } catch (Exception ignored) {
            }
        }
        return recovered;
    }

    private static int trimToWholePackets(File file) throws IOException {
        long good = 0;
        int lastTime = 0;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(file)))) {
            long length = file.length();
            while (good + 8 <= length) {
                int time;
                int size;
                try {
                    time = in.readInt();
                    size = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                if (size < 0 || good + 8 + size > length) break;
                in.skipBytes(size);
                good += 8 + size;
                lastTime = time;
            }
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(good);
        }
        return lastTime;
    }

    private static void writeText(File file, String text) throws IOException {
        File partial = new File(file.getParentFile(), file.getName() + ".part");
        Files.write(partial.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1 << 16];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
    }

    private static void deleteFolder(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
        dir.delete();
    }
}
