package rip.tempest.utils.misc;

import rip.tempest.utils.client.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisguiseManager {
    private DisguiseManager() {}

    private static final Map<String, Disguise> DISGUISES = new ConcurrentHashMap<>();

    public static final class Disguise {
        public final String name;
        public volatile ResourceLocation skin;
        public volatile String model = "default";

        Disguise(String name) {
            this.name = name;
        }
    }

    public static void disguise(String target, String disguiseAs) {
        final Disguise d = new Disguise(disguiseAs);
        DISGUISES.put(target.toLowerCase(), d);
        fetch(d, disguiseAs);
    }

    public static void remove(String target) {
        DISGUISES.remove(target.toLowerCase());
    }

    public static void clear() {
        DISGUISES.clear();
    }

    public static boolean isEmpty() {
        return DISGUISES.isEmpty();
    }

    public static int count() {
        return DISGUISES.size();
    }

    public static ResourceLocation getSkin(String realName) {
        final Disguise d = realName == null ? null : DISGUISES.get(realName.toLowerCase());
        return d == null ? null : d.skin;
    }

    public static String getModel(String realName) {
        final Disguise d = realName == null ? null : DISGUISES.get(realName.toLowerCase());
        return d == null ? null : d.model;
    }

    public static String getDisguiseName(String realName) {
        final Disguise d = realName == null ? null : DISGUISES.get(realName.toLowerCase());
        return d == null ? null : d.name;
    }

    private static void fetch(final Disguise d, final String name) {
        new Thread(() -> {
            try {
                final String uuid = requestUuid(name);
                if (uuid == null) {
                    schedule(() -> Logger.chatPrint("\247cDisguise: player '" + name + "' not found."));
                    return;
                }
                final String[] skin = requestSkin(uuid);
                if (skin == null || skin[0] == null) {
                    schedule(() -> Logger.chatPrint("\247cDisguise: '" + name + "' has no skin."));
                    return;
                }
                d.model = "slim".equals(skin[1]) ? "slim" : "default";
                final String url = skin[0];

                schedule(() -> {
                    d.skin = skinTexture(name, url);
                    Logger.chatPrint("\247aDisguise skin for '" + name + "' loaded.");
                });
            } catch (Exception ex) {
                schedule(() -> Logger.chatPrint("\247cDisguise: failed to fetch '" + name + "' (" + ex.getMessage() + ")."));
            }
        }, "Disguise-Fetch").start();
    }

    public static String[] lookupSkin(String name) throws Exception {
        final String uuid = requestUuid(name);
        return uuid == null ? null : requestSkin(uuid);
    }

    public static ResourceLocation skinTexture(String name, String url) {
        final ResourceLocation loc = new ResourceLocation("disguise/skins/" + name.toLowerCase());
        final TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        if (tm.getTexture(loc) == null) {
            final UUID off = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            final ThreadDownloadImageData tex = new ThreadDownloadImageData(
                    null, url, DefaultPlayerSkin.getDefaultSkin(off), new ImageBufferDownload());
            tm.loadTexture(loc, tex);
        }
        return loc;
    }

    private static void schedule(Runnable r) {
        Minecraft.getMinecraft().addScheduledTask(r);
    }

    private static String requestUuid(String name) throws Exception {
        final String body = get("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (body == null || body.isEmpty()) return null;
        final JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        return o.has("id") ? o.get("id").getAsString() : null;
    }

    private static String[] requestSkin(String uuid) throws Exception {
        final String body = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (body == null) return null;
        final JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        final JsonArray props = o.getAsJsonArray("properties");
        if (props == null) return null;

        String value = null;
        for (int i = 0; i < props.size(); i++) {
            final JsonObject p = props.get(i).getAsJsonObject();
            if ("textures".equals(p.get("name").getAsString())) {
                value = p.get("value").getAsString();
                break;
            }
        }
        if (value == null) return null;

        final String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        final JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
        if (textures == null || !textures.has("SKIN")) return null;

        final JsonObject skin = textures.getAsJsonObject("SKIN");
        final String url = skin.get("url").getAsString();
        String model = "default";
        if (skin.has("metadata")) {
            final JsonObject md = skin.getAsJsonObject("metadata");
            if (md.has("model")) model = md.get("model").getAsString();
        }
        return new String[]{url, model};
    }

    private static String get(String urlStr) throws Exception {
        final HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "Tempest");
        if (c.getResponseCode() != 200) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            final StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
