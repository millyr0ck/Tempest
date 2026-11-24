package rip.tempest.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class BackgroundSaver {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> lastWritten = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastModified = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Tempest Config Saver");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private BackgroundSaver() {
    }

    public static void save(File file, Supplier<JsonObject> json) {
        EXECUTOR.execute(() -> {
            try {
                write(file, PRETTY.toJson(json.get()));
            } catch (Throwable ignored) {
            }
        });
    }

    private static void write(File file, String text) throws IOException {
        String key = file.getAbsolutePath();

        Long modified = lastModified.get(key);
        if (file.exists() && text.equals(lastWritten.get(key)) && modified != null && modified == file.lastModified()) return;

        File temp = new File(file.getPath() + ".tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write(text);
        }

        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp.toPath());
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(text);
            }
        }

        lastWritten.put(key, text);
        lastModified.put(key, file.lastModified());
    }
}
