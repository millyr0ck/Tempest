package rip.tempest.jenny.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class AnimLoader {

    public static Map<String, NpcAnimation> load(ResourceLocation location) {
        try {
            String path = "/assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
            InputStream is = AnimLoader.class.getResourceAsStream(path);
            if (is == null) throw new IOException("Jenny resource not found: " + path);
            String json = readAll(is);
            return parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private static Map<String, NpcAnimation> parse(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonObject animationsObj = root.getAsJsonObject("animations");
        Map<String, NpcAnimation> result = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : animationsObj.entrySet()) {
            String animName = entry.getKey();
            try {
                result.put(animName, parseAnimation(animName, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                System.err.println("[Jenny] Skipping animation " + animName + ": " + e);
            }
        }
        return result;
    }

    private static NpcAnimation parseAnimation(String animName, JsonObject animObj) {
        boolean loop = animObj.has("loop") && animObj.get("loop").getAsBoolean();
        float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1f;

        Map<String, BoneAnim> boneAnims = new LinkedHashMap<>();
        if (animObj.has("bones")) {
            JsonObject bonesObj = animObj.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
                String boneName = boneEntry.getKey();
                JsonObject boneObj = boneEntry.getValue().getAsJsonObject();
                Channel rot = boneObj.has("rotation") ? parseChannel(boneObj.get("rotation")) : null;
                Channel pos = boneObj.has("position") ? parseChannel(boneObj.get("position")) : null;
                Channel scale = boneObj.has("scale") ? parseChannel(boneObj.get("scale")) : null;
                boneAnims.put(boneName, new BoneAnim(rot, pos, scale));
            }
        }
        List<Map.Entry<Float, String>> events = new ArrayList<>();
        if (animObj.has("sound_effects") && animObj.get("sound_effects").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : animObj.getAsJsonObject("sound_effects").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject fx = e.getValue().getAsJsonObject();
                if (fx.has("effect")) events.add(new AbstractMap.SimpleEntry<>(parseTime(e.getKey()), fx.get("effect").getAsString()));
            }
        }
        events.sort(Map.Entry.comparingByKey());
        float[] times = new float[events.size()];
        String[] names = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            times[i] = events.get(i).getKey();
            names[i] = events.get(i).getValue();
        }
        return new NpcAnimation(animName, loop, length, boneAnims, times, names);
    }

    private static Channel parseChannel(JsonElement elem) {
        List<Keyframe> keyframes = new ArrayList<>();

        if (elem.isJsonObject() && elem.getAsJsonObject().has("vector")) {
            keyframes.add(new Keyframe(0f, arrToVec3(elem.getAsJsonObject().getAsJsonArray("vector"))));
        } else if (elem.isJsonObject()) {
            JsonObject timeMap = elem.getAsJsonObject();
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(timeMap.entrySet());
            entries.sort(Comparator.comparingDouble(e -> parseTime(e.getKey())));

            for (Map.Entry<String, JsonElement> e : entries) {
                float time = parseTime(e.getKey());
                float[] value = parseKeyframeValue(e.getValue());
                Easing easing = Easing.LINEAR;
                Double easingArg = null;
                if (e.getValue().isJsonObject()) {
                    JsonObject kf = e.getValue().getAsJsonObject();
                    if (kf.has("easing")) easing = Easing.fromJson(kf.get("easing").getAsString());
                    if (kf.has("easingArgs") && kf.get("easingArgs").isJsonArray()
                            && kf.getAsJsonArray("easingArgs").size() > 0) {
                        easingArg = (double) component(kf.getAsJsonArray("easingArgs").get(0));
                    }
                }
                keyframes.add(new Keyframe(time, value, easing, easingArg));
            }
        } else if (elem.isJsonArray()) {
            float[] value = arrToVec3(elem.getAsJsonArray());
            keyframes.add(new Keyframe(0f, value));
        }

        return new Channel(keyframes);
    }

    private static float parseTime(String key) {
        try { return Float.parseFloat(key); } catch (NumberFormatException e) { return 0f; }
    }

    private static float[] parseKeyframeValue(JsonElement elem) {
        if (elem.isJsonArray()) return arrToVec3(elem.getAsJsonArray());
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("vector")) return arrToVec3(obj.getAsJsonArray("vector"));
            if (obj.has("post")) {
                JsonElement post = obj.get("post");
                if (post.isJsonArray()) return arrToVec3(post.getAsJsonArray());
                if (post.isJsonObject() && post.getAsJsonObject().has("vector"))
                    return arrToVec3(post.getAsJsonObject().getAsJsonArray("vector"));
            }
        }
        return new float[]{0, 0, 0};
    }

    private static float[] arrToVec3(JsonArray arr) {
        return new float[]{component(arr.get(0)), component(arr.get(1)), component(arr.get(2))};
    }

    private static float component(JsonElement elem) {
        if (elem.getAsJsonPrimitive().isNumber()) return elem.getAsFloat();
        return KeyframeExpression.eval(elem.getAsString());
    }
}
