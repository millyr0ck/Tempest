package rip.tempest.modules.impl.client;

import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.emote.Emote;
import rip.tempest.utils.emote.Emote.Channel;
import rip.tempest.utils.emote.Emote.Easing;
import rip.tempest.utils.emote.Emote.Part;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Emotes", category = ModuleCategory.CLIENT)
public final class EmoteModule extends Module {
    private static EmoteModule instance;
    private final SelectProperty emote = new SelectProperty("Emote");

    private final rip.tempest.api.properties.Property<Boolean> position =
            new rip.tempest.api.properties.Property<>("Translation", false);

    private final Map<String, Emote> cache = new HashMap<>();
    private String lastName;
    private long startMs;

    private final Set<ModelBiped> posed = Collections.newSetFromMap(new WeakHashMap<>());

    private static final EnumMap<Part, float[]> BASE = new EnumMap<>(Part.class);
    static {
        BASE.put(Part.HEAD, new float[]{0f, 0f, 0f});
        BASE.put(Part.TORSO, new float[]{0f, 0f, 0f});
        BASE.put(Part.RIGHT_ARM, new float[]{-5f, 2f, 0f});
        BASE.put(Part.LEFT_ARM, new float[]{5f, 2f, 0f});
        BASE.put(Part.RIGHT_LEG, new float[]{-1.9f, 12f, 0f});
        BASE.put(Part.LEFT_LEG, new float[]{1.9f, 12f, 0f});
    }

    public EmoteModule() {
        instance = this;
        emote.setOnOpen(() -> { try { refresh(); } catch (Exception ignored) {} });
        try { refresh(); } catch (Exception ignored) {}
    }

    @Override
    public void onEnable() {
        super.onEnable();
        refresh();
        startMs = System.currentTimeMillis();
        lastName = null;
    }

    private void refresh() {
        cache.clear();
        File dir = emotesDir();
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) names.add(f.getName().substring(0, f.getName().length() - 5));
        }
        Collections.sort(names);
        emote.setOptions(names);
        if (isEnabled()) {
            Logger.chatPrint(names.isEmpty()
                    ? "§eNo emotes in .minecraft/tempest/emotes/"
                    : "§aFound " + names.size() + " emote(s).");
        }
    }

    private static File emotesDir() {
        return new File(mc.mcDataDir, "tempest/emotes");
    }

    private Emote getEmote(String name) {
        if (name == null || name.isEmpty()) return null;
        if (cache.containsKey(name)) return cache.get(name);
        Emote e = null;
        File f = new File(emotesDir(), name + ".json");
        if (f.exists()) {
            try {
                e = parse(f);
            } catch (Exception ex) {
                Logger.chatPrint("§cEmote failed: " + name + " (" + ex.getMessage() + ")");
            }
        }
        cache.put(name, e);
        return e;
    }

    private static String jsonKey(Part p) {
        return switch (p) {
            case HEAD -> "head";
            case TORSO -> "torso";
            case RIGHT_ARM -> "rightArm";
            case LEFT_ARM -> "leftArm";
            case RIGHT_LEG -> "rightLeg";
            case LEFT_LEG -> "leftLeg";
        };
    }

    private Emote parse(File f) throws Exception {
        JsonObject root;
        try (FileReader r = new FileReader(f)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        }
        JsonObject e = root.getAsJsonObject("emote");
        Emote em = new Emote();
        em.name = root.has("name") ? root.get("name").getAsString() : f.getName().replace(".json", "");
        em.loop = e.has("isLoop") && Boolean.parseBoolean(e.get("isLoop").getAsString());
        em.beginTick = getInt(e, "beginTick", 0);
        em.returnTick = getInt(e, "returnTick", 0);
        em.endTick = getInt(e, "endTick", 0);

        JsonArray moves = e.getAsJsonArray("moves");
        for (JsonElement mv : moves) {
            JsonObject m = mv.getAsJsonObject();
            double tick = m.get("tick").getAsDouble();
            Easing ease = Easing.of(m.has("easing") ? m.get("easing").getAsString() : "LINEAR");
            for (Part p : Part.values()) {
                String key = jsonKey(p);
                if (!m.has(key)) continue;
                JsonObject po = m.getAsJsonObject(key);
                addChannel(em, p, Channel.PITCH, po, "pitch", tick, ease);
                addChannel(em, p, Channel.YAW, po, "yaw", tick, ease);
                addChannel(em, p, Channel.ROLL, po, "roll", tick, ease);
                addChannel(em, p, Channel.X, po, "x", tick, ease);
                addChannel(em, p, Channel.Y, po, "y", tick, ease);
                addChannel(em, p, Channel.Z, po, "z", tick, ease);
            }
        }
        em.finish();
        return em;
    }

    private static void addChannel(Emote em, Part p, Channel c, JsonObject po, String key, double tick, Easing ease) {
        if (po.has(key)) em.add(p, c, new Emote.KeyFrame(tick, po.get(key).getAsFloat(), ease));
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    public static void applyEmote(ModelBiped m, Entity entity, float ageInTicks) {
        if (instance == null || entity != mc.thePlayer) return;

        String name = instance.isEnabled() ? instance.emote.getValue() : null;
        Emote cur = name == null ? null : instance.getEmote(name);

        if (cur == null) {
            if (instance.isEnabled()) instance.setSuffix("none");
            if (instance.posed.remove(m)) resetModel(m);
            return;
        }
        instance.setSuffix(name);

        if (!name.equals(instance.lastName)) { instance.lastName = name; instance.startMs = System.currentTimeMillis(); }

        double elapsedTicks = (System.currentTimeMillis() - instance.startMs) / 1000.0 * 20.0;
        double tick = cur.loopTick(elapsedTicks);
        boolean pos = instance.position.getValue();

        for (Part p : Part.values()) {
            if (!cur.animates(p)) continue;
            ModelRenderer r = renderer(m, p);
            if (r == null) continue;

            r.rotateAngleX = safe(cur.value(p, Channel.PITCH, tick, 0f));
            r.rotateAngleY = safe(cur.value(p, Channel.YAW, tick, 0f));
            r.rotateAngleZ = safe(cur.value(p, Channel.ROLL, tick, 0f));

            if (pos) {
                float[] b = BASE.get(p);
                r.rotationPointX = b[0] + safe(cur.value(p, Channel.X, tick, 0f));
                r.rotationPointY = b[1] + safe(cur.value(p, Channel.Y, tick, 0f));
                r.rotationPointZ = b[2] + safe(cur.value(p, Channel.Z, tick, 0f));
            }
        }
        instance.posed.add(m);
    }

    private static float safe(float v) {
        return Float.isFinite(v) ? v : 0f;
    }

    private static void resetModel(ModelBiped m) {
        m.bipedRightLeg.rotateAngleZ = 0f;
        m.bipedLeftLeg.rotateAngleZ = 0f;
        m.bipedHead.rotateAngleZ = 0f;
        m.bipedBody.rotateAngleY = 0f;
        m.bipedBody.rotateAngleZ = 0f;
        for (Part p : Part.values()) {
            ModelRenderer r = renderer(m, p);
            if (r == null) continue;
            float[] b = BASE.get(p);
            r.rotationPointX = b[0];
            r.rotationPointY = b[1];
            r.rotationPointZ = b[2];
        }
    }

    private static ModelRenderer renderer(ModelBiped m, Part p) {
        return switch (p) {
            case HEAD -> m.bipedHead;
            case TORSO -> m.bipedBody;
            case RIGHT_ARM -> m.bipedRightArm;
            case LEFT_ARM -> m.bipedLeftArm;
            case RIGHT_LEG -> m.bipedRightLeg;
            case LEFT_LEG -> m.bipedLeftLeg;
        };
    }
}
