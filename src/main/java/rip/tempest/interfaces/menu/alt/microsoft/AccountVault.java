package rip.tempest.interfaces.menu.alt.microsoft;

import rip.tempest.Tempest;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AccountVault {
    private static final String PROTECT = "Add-Type -AssemblyName System.Security; "
            + "$b=[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd()); "
            + "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($b,$null,'CurrentUser'))";
    private static final String UNPROTECT = "Add-Type -AssemblyName System.Security; "
            + "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim()); "
            + "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,'CurrentUser'))";

    private AccountVault() {
    }

    public static synchronized boolean save(String name, String uuid, String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return false;
        String encrypted;
        try {
            encrypted = powershell(PROTECT, refreshToken);
        } catch (IOException e) {
            return false;
        }
        if (encrypted.isEmpty()) return false;

        List<String> lines = new ArrayList<>();
        for (String line : read()) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3 && !parts[0].equalsIgnoreCase(name) && !parts[1].equals(uuid)) lines.add(line);
        }
        lines.add(name + "|" + uuid + "|" + encrypted);
        return write(lines);
    }

    public static synchronized String load(String name) {
        for (String line : read()) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(name)) {
                try {
                    String token = powershell(UNPROTECT, parts[2]);
                    return token.isEmpty() ? null : token;
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static synchronized String uuid(String name) {
        for (String line : read()) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(name)) return parts[1];
        }
        return null;
    }

    public static synchronized void remove(String name) {
        List<String> lines = new ArrayList<>();
        boolean found = false;
        for (String line : read()) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(name)) found = true;
            else lines.add(line);
        }
        if (found) write(lines);
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, Tempest.NAME), "accounts.dat");
    }

    private static List<String> read() {
        File file = file();
        if (!file.exists()) return new ArrayList<>();
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) lines.add(line.trim());
            }
            return lines;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static boolean write(List<String> lines) {
        File file = file();
        file.getParentFile().mkdirs();
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static String powershell(String script, String input) throws IOException {
        String root = System.getenv("SystemRoot");
        File exe = root == null ? null : new File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
        if (exe == null || !exe.isFile()) throw new IOException("Windows PowerShell not found");

        Process process = new ProcessBuilder(exe.getPath(), "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            try (OutputStream in = process.getOutputStream()) {
                in.write(input.getBytes(StandardCharsets.UTF_8));
            }
            String output = readAll(process.getInputStream());
            if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("PowerShell took too long");
            if (process.exitValue() != 0) throw new IOException("PowerShell failed");
            return output.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } finally {
            process.destroy();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
