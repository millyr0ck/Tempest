package rip.tempest.jenny.sound;

import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.jenny.entity.NpcEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SceneSounds {
    private static final Random RNG = new Random();
    private static final Map<CharacterType, JsonObject> TABLES = new HashMap<>();
    private static Map<String, Integer> sizes;

    private static final Map<String, Integer> lastIndex = new HashMap<>();

    private SceneSounds() {
    }

    public static void handle(NpcEntity npc, String event) {
        JsonObject table = table(npc.getCharacterType());
        if (table == null || !table.has(event)) return;
        JsonObject entry = table.getAsJsonObject(event);
        JsonArray ops = entry.getAsJsonArray("ops");
        boolean oneOf = entry.has("oneOf") && entry.get("oneOf").getAsBoolean();

        List<JsonObject> girlSounds = new ArrayList<>();
        for (JsonElement e : ops) {
            JsonObject op = e.getAsJsonObject();
            if (op.has("sound")) {
                String field = op.get("sound").getAsString();
                if (oneOf && field.startsWith("GIRLS_")) girlSounds.add(op);
                else play(npc, op);
            } else if (op.has("say")) {
                npc.say(op.get("say").getAsString());
            } else if (op.has("player")) {
                npc.sayAsPlayer(op.get("player").getAsString());
            } else if (op.has("meter")) {
                npc.addCumProgress(op.get("meter").getAsFloat());
            }
        }
        if (!girlSounds.isEmpty()) play(npc, girlSounds.get(RNG.nextInt(girlSounds.size())));
    }

    private static void play(NpcEntity npc, JsonObject op) {
        String field = op.get("sound").getAsString();
        Integer size = sizes().get(field);
        if (size == null || size == 0) return;
        int index = op.get("index").getAsInt();
        if (index < 0 || index >= size) index = randomIndex(field, size);
        float volume = op.get("volume").getAsFloat();

        String name = field.toLowerCase().replace('_', '.');
        String[] parts = name.split("\\.");
        String category = parts.length > 2 ? parts[2] : parts[1];
        ResourceLocation sound = new ResourceLocation("sexmod." + name + "." + category + index);
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(sound, Math.min(volume, 1f), 1f,
                (float) npc.posX, (float) (npc.posY + 1.0), (float) npc.posZ));
    }

    private static int randomIndex(String field, int size) {
        int last = lastIndex.getOrDefault(field, -1);
        int index;
        int attempts = 0;
        do {
            index = RNG.nextInt(size);
        } while (++attempts < 10 && index == last && size > 1);
        lastIndex.put(field, index);
        return index;
    }

    private static JsonObject table(CharacterType type) {
        return TABLES.computeIfAbsent(type, t -> {
            JsonElement e = read("/assets/jenny/events/" + t.id + ".json");
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        });
    }

    private static Map<String, Integer> sizes() {
        if (sizes == null) {
            sizes = new HashMap<>();
            JsonElement e = read("/assets/jenny/events/sizes.json");
            if (e != null && e.isJsonObject()) {
                for (Map.Entry<String, JsonElement> s : e.getAsJsonObject().entrySet()) {
                    sizes.put(s.getKey(), s.getValue().getAsInt());
                }
            }
        }
        return sizes;
    }

    private static JsonElement read(String path) {
        try (InputStream in = SceneSounds.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
