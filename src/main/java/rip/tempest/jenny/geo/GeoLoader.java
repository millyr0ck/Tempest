package rip.tempest.jenny.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeoLoader {
    public static GeoModel load(ResourceLocation location) {
        try {
            String path = "/assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
            InputStream is = GeoLoader.class.getResourceAsStream(path);
            if (is == null) throw new IOException("Jenny resource not found: " + path);
            String json = readAll(is);
            return parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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

    private static GeoModel parse(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        JsonObject geo = geometries.get(0).getAsJsonObject();
        JsonObject desc = geo.getAsJsonObject("description");

        int texW = desc.get("texture_width").getAsInt();
        int texH = desc.get("texture_height").getAsInt();

        Map<String, GeoBone> allBones = new LinkedHashMap<>();

        if (geo.has("bones")) {
            for (JsonElement boneElem : geo.getAsJsonArray("bones")) {
                JsonObject b = boneElem.getAsJsonObject();
                String name = b.get("name").getAsString();
                String parent = b.has("parent") ? b.get("parent").getAsString() : null;
                float[] pivot = vec3(b, "pivot");
                float[] rotation = b.has("rotation") ? vec3Arr(b.getAsJsonArray("rotation")) : null;

                GeoBone bone = new GeoBone(name, parent, pivot, rotation);

                if (b.has("cubes")) {
                    for (JsonElement cubeElem : b.getAsJsonArray("cubes")) {
                        bone.cubes.add(parseCube(cubeElem.getAsJsonObject()));
                    }
                }

                allBones.put(name, bone);
            }
        }

        List<GeoBone> rootBones = new ArrayList<>();
        for (GeoBone bone : allBones.values()) {
            if (bone.parentName == null) {
                rootBones.add(bone);
            } else {
                GeoBone parent = allBones.get(bone.parentName);
                if (parent != null) parent.children.add(bone);
                else rootBones.add(bone);
            }
        }

        return new GeoModel(texW, texH, rootBones, allBones);
    }

    private static GeoCube parseCube(JsonObject c) {
        float[] origin = vec3Arr(c.getAsJsonArray("origin"));
        float[] size = vec3Arr(c.getAsJsonArray("size"));
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0f;
        float[] localPivot = c.has("pivot") ? vec3Arr(c.getAsJsonArray("pivot")) : null;
        float[] localRotation = c.has("rotation") ? vec3Arr(c.getAsJsonArray("rotation")) : null;

        GeoFace north = null, south = null, east = null, west = null, up = null, down = null;

        if (c.has("uv")) {
            JsonElement uvElem = c.get("uv");
            if (uvElem.isJsonObject()) {
                JsonObject uvObj = uvElem.getAsJsonObject();
                north = parseFace(uvObj, "north");
                south = parseFace(uvObj, "south");
                east = parseFace(uvObj, "east");
                west = parseFace(uvObj, "west");
                up = parseFace(uvObj, "up");
                down = parseFace(uvObj, "down");
            }
        }

        return new GeoCube(origin, size, inflate, localPivot, localRotation,
                north, south, east, west, up, down);
    }

    private static GeoFace parseFace(JsonObject uvObj, String face) {
        if (!uvObj.has(face)) return null;
        JsonObject f = uvObj.getAsJsonObject(face);
        JsonArray uvArr = f.getAsJsonArray("uv");
        JsonArray sizeArr = f.getAsJsonArray("uv_size");
        float[] uv = {uvArr.get(0).getAsFloat(), uvArr.get(1).getAsFloat()};
        float[] uvSize = {sizeArr.get(0).getAsFloat(), sizeArr.get(1).getAsFloat()};
        return new GeoFace(uv, uvSize);
    }

    private static float[] vec3(JsonObject obj, String key) {
        if (!obj.has(key)) return new float[]{0, 0, 0};
        return vec3Arr(obj.getAsJsonArray(key));
    }

    private static float[] vec3Arr(JsonArray arr) {
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
    }
}
