package rip.tempest.utils.render.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import static rip.tempest.utils.Util.mc;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

public class GltfModel {
    private static class RenderGroup {
        FloatList positionList = new FloatList(), normalList = new FloatList(), uvList = new FloatList(), weightList = new FloatList();
        IntList indexList = new IntList(), partList = new IntList(), jointList = new IntList();

        float[] positions, normals, uvs;
        int[] indices;

        int[] joints;
        float[] weights;

        int[] parts;

        ResourceLocation textureLocation;
        boolean hasTexture;
        float[] baseColorFactor = {1f, 1f, 1f, 1f};

        int ibo, uvVbo, vbo;
        ByteBuffer stream;
        float[] interleaved;
        int uploadedVersion = Integer.MIN_VALUE;

        int vertexCount() {
            return positions.length / 3;
        }

        void freeze() {
            positions = positionList.toArray();
            normals = normalList.toArray();
            uvs = uvList.toArray();
            indices = indexList.toArray();
            partList.add(positions.length / 3);
            parts = partList.toArray();
            if (jointList.size() > 0) {
                joints = jointList.toArray();
                weights = weightList.toArray();
            }
            positionList = normalList = uvList = weightList = null;
            indexList = partList = jointList = null;
        }
    }

    private static final class FloatList {
        private float[] data = new float[1024];
        private int size;

        void add(float value) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
            data[size++] = value;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    private static final class IntList {
        private int[] data = new int[1024];
        private int size;

        void add(int value) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
            data[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    public static final class Skeleton {
        public final String[] names;

        public final int[] parent;
        public final float[][] local;

        public final float[][] anchor;
        public final float[][] restWorld;

        Skeleton(String[] names, int[] parent, float[][] local, float[][] anchor, float[][] restWorld) {
            this.names = names;
            this.parent = parent;
            this.local = local;
            this.anchor = anchor;
            this.restWorld = restWorld;
        }

        public int size() {
            return names.length;
        }
    }

    private List<RenderGroup> groups;
    private Skeleton skeleton;

    private float[][] nodeWorld;
    private int[] nodeParent;
    private int[] jointOfNode;
    private float[][] skinMatrices;
    private boolean hasTexture;
    private float robustHeight;

    private final String cacheKey;

    private JsonObject root;
    private File dir;
    private JsonArray bufferViews;
    private JsonArray accessors;
    private byte[][] buffers;
    private final Map<Integer, ResourceLocation> textureCache = new HashMap<>();

    public GltfModel(File gltfFile) throws IOException {
        this.cacheKey = "gltf_" + gltfFile.getAbsolutePath().hashCode();
        load(gltfFile, false);
        if (!hadSkins) return;

        float placedHeight = robustHeight;
        load(gltfFile, true);
        float ratio = robustHeight / Math.max(1e-6f, placedHeight);
        if (skeleton == null || ratio < 0.5f || ratio > 2f) load(gltfFile, false);
    }

    private boolean hadSkins;

    private void load(File gltfFile, boolean useSkins) throws IOException {
        dir = gltfFile.getParentFile();
        skeleton = null;
        fileJointToOurs = null;
        root = null;
        byte[] glbBinary = null;
        if (gltfFile.getName().toLowerCase().endsWith(".glb")) {
            byte[] data;
            try (InputStream in = new FileInputStream(gltfFile)) {
                data = in.readAllBytes();
            }
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            if (data.length < 20 || bb.getInt(0) != 0x46546C67) throw new IOException("not a .glb file: " + gltfFile.getName());
            int length = Math.min(bb.getInt(8), data.length);
            for (int off = 12; off + 8 <= length; ) {
                int chunkLength = bb.getInt(off), chunkType = bb.getInt(off + 4);
                if (chunkType == 0x4E4F534A) {
                    root = JsonParser.parseString(new String(data, off + 8, chunkLength, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                } else if (chunkType == 0x004E4942) {
                    glbBinary = java.util.Arrays.copyOfRange(data, off + 8, off + 8 + chunkLength);
                }
                off += 8 + ((chunkLength + 3) & ~3);
            }
            if (root == null) throw new IOException(".glb has no JSON chunk: " + gltfFile.getName());
        } else {
            try (Reader reader = new FileReader(gltfFile)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        JsonArray buffersJson = root.getAsJsonArray("buffers");
        buffers = new byte[buffersJson == null ? 0 : buffersJson.size()][];
        for (int i = 0; i < buffers.length; i++) {
            JsonObject bufObj = buffersJson.get(i).getAsJsonObject();

            buffers[i] = bufObj.has("uri") ? resolveBufferBytes(bufObj.get("uri").getAsString(), dir) : glbBinary;
            if (buffers[i] == null) throw new IOException("glTF buffer " + i + " has no data");
        }

        bufferViews = root.getAsJsonArray("bufferViews");
        accessors = root.getAsJsonArray("accessors");
        JsonArray meshesJson = root.getAsJsonArray("meshes");

        if (meshesJson == null || meshesJson.size() == 0) {
            throw new IOException("glTF file has no meshes: " + gltfFile.getName());
        }

        Map<Integer, RenderGroup> groupsByMaterial = new HashMap<>();
        JsonArray nodesJson = root.getAsJsonArray("nodes");
        JsonArray scenesJson = root.getAsJsonArray("scenes");

        if (nodesJson != null && scenesJson != null && scenesJson.size() > 0) {
            int sceneIdx = root.has("scene") ? root.get("scene").getAsInt() : 0;
            JsonArray rootNodes = scenesJson.get(sceneIdx).getAsJsonObject().getAsJsonArray("nodes");
            float[] identity = Matrix4.identity();
            nodeWorld = new float[nodesJson.size()][];
            nodeParent = new int[nodesJson.size()];
            java.util.Arrays.fill(nodeParent, -1);
            for (JsonElement rootNodeEl : rootNodes) resolveNode(nodesJson, rootNodeEl.getAsInt(), identity);
            JsonArray skins = root.getAsJsonArray("skins");
            hadSkins = skins != null && skins.size() > 0;
            jointOfNode = new int[nodesJson.size()];
            java.util.Arrays.fill(jointOfNode, -1);
            if (useSkins) buildSkeleton(nodesJson);
            for (JsonElement rootNodeEl : rootNodes) {
                traverseNode(nodesJson, rootNodeEl.getAsInt(), identity, meshesJson, groupsByMaterial);
            }
        } else {
            float[] identity = Matrix4.identity();
            for (int i = 0; i < meshesJson.size(); i++) {
                for (JsonElement primEl : meshesJson.get(i).getAsJsonObject().getAsJsonArray("primitives")) {
                    addPrimitive(primEl.getAsJsonObject(), identity, groupsByMaterial, -1);
                }
            }
        }

        groups = new ArrayList<>(groupsByMaterial.values());
        for (RenderGroup group : groups) group.freeze();
        hasTexture = groups.stream().anyMatch(g -> g.hasTexture);
        robustHeight = computeRobustHeight();
        nodeWorld = null;
        nodeParent = jointOfNode = null;
        skinMatrices = null;
        root = null;
        buffers = null;
    }

    private static float[] localMatrix(JsonObject node) {
        if (node.has("matrix")) {
            JsonArray arr = node.getAsJsonArray("matrix");
            float[] out = new float[16];
            for (int i = 0; i < 16; i++) out[i] = arr.get(i).getAsFloat();
            return out;
        }
        float[] t = {0, 0, 0}, r = {0, 0, 0, 1}, s = {1, 1, 1};
        if (node.has("translation")) for (int i = 0; i < 3; i++) t[i] = node.getAsJsonArray("translation").get(i).getAsFloat();
        if (node.has("rotation")) for (int i = 0; i < 4; i++) r[i] = node.getAsJsonArray("rotation").get(i).getAsFloat();
        if (node.has("scale")) for (int i = 0; i < 3; i++) s[i] = node.getAsJsonArray("scale").get(i).getAsFloat();
        return Matrix4.fromTRS(t, r, s);
    }

    private void resolveNode(JsonArray nodesJson, int index, float[] parentWorld) {
        JsonObject node = nodesJson.get(index).getAsJsonObject();
        nodeWorld[index] = Matrix4.multiply(parentWorld, localMatrix(node));
        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                nodeParent[child.getAsInt()] = index;
                resolveNode(nodesJson, child.getAsInt(), nodeWorld[index]);
            }
        }
    }

    private void buildSkeleton(JsonArray nodesJson) {
        JsonArray skins = root.getAsJsonArray("skins");
        if (skins == null || skins.size() == 0) return;
        JsonObject skin = skins.get(0).getAsJsonObject();
        JsonArray jointsJson = skin.getAsJsonArray("joints");
        int n = jointsJson.size();
        int[] nodes = new int[n];
        for (int j = 0; j < n; j++) nodes[j] = jointsJson.get(j).getAsInt();
        for (int j = 0; j < n; j++) if (nodeWorld[nodes[j]] == null) return;

        Integer[] order = new Integer[n];
        for (int j = 0; j < n; j++) order[j] = j;
        int[] depth = new int[n];
        for (int j = 0; j < n; j++) for (int p = nodeParent[nodes[j]]; p >= 0; p = nodeParent[p]) depth[j]++;
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(depth[a], depth[b]));
        int[] fileToOurs = new int[n];
        for (int k = 0; k < n; k++) fileToOurs[order[k]] = k;

        float[] ibm = skin.has("inverseBindMatrices") ? readAccessor(skin.get("inverseBindMatrices").getAsInt()) : null;
        String[] names = new String[n];
        int[] parent = new int[n];
        float[][] local = new float[n][], anchor = new float[n][], restWorld = new float[n][];
        skinMatrices = new float[n][];
        for (int k = 0; k < n; k++) jointOfNode[nodes[order[k]]] = k;
        for (int k = 0; k < n; k++) {
            int file = order[k], node = nodes[file];
            JsonObject json = nodesJson.get(node).getAsJsonObject();
            names[k] = json.has("name") ? json.get("name").getAsString() : "joint" + k;
            int parentNode = nodeParent[node];
            parent[k] = parentNode >= 0 ? jointOfNode[parentNode] : -1;
            local[k] = localMatrix(json);
            if (parent[k] < 0) anchor[k] = parentNode >= 0 ? nodeWorld[parentNode] : Matrix4.identity();
            restWorld[k] = nodeWorld[node];
            float[] inverseBind = ibm != null ? java.util.Arrays.copyOfRange(ibm, file * 16, file * 16 + 16) : Matrix4.identity();
            skinMatrices[k] = Matrix4.multiply(restWorld[k], inverseBind);
        }

        this.fileJointToOurs = fileToOurs;
        skeleton = new Skeleton(names, parent, local, anchor, restWorld);
    }

    private int[] fileJointToOurs;

    private int owningJoint(int node) {
        for (int p = node; p >= 0; p = nodeParent[p]) {
            if (jointOfNode[p] >= 0) return jointOfNode[p];
        }
        return -1;
    }

    private float computeRobustHeight() {
        int totalVerts = 0;
        for (RenderGroup g : groups) totalVerts += g.vertexCount();
        if (totalVerts == 0) return 0f;

        float[] ys = new float[totalVerts];
        int idx = 0;
        for (RenderGroup g : groups) {
            int n = g.vertexCount();
            for (int i = 0; i < n; i++) ys[idx++] = g.positions[i * 3 + 1];
        }
        java.util.Arrays.sort(ys);

        int trim = (int) (ys.length * 0.02);
        float lo = ys[trim];
        float hi = ys[ys.length - 1 - trim];
        return hi - lo;
    }

    private void traverseNode(JsonArray nodesJson, int nodeIndex, float[] parentMatrix,
                               JsonArray meshesJson, Map<Integer, RenderGroup> groupsByMaterial) {
        JsonObject node = nodesJson.get(nodeIndex).getAsJsonObject();

        float[] local;
        if (node.has("matrix")) {
            local = readMatrix(node.getAsJsonArray("matrix"));
        } else {
            float[] t = node.has("translation") ? readVec(node.getAsJsonArray("translation"), 3) : new float[]{0, 0, 0};
            float[] r = node.has("rotation") ? readVec(node.getAsJsonArray("rotation"), 4) : new float[]{0, 0, 0, 1};
            float[] s = node.has("scale") ? readVec(node.getAsJsonArray("scale"), 3) : new float[]{1, 1, 1};
            local = Matrix4.fromTRS(t, r, s);
        }
        float[] world = Matrix4.multiply(parentMatrix, local);

        if (node.has("mesh")) {
            JsonObject mesh = meshesJson.get(node.get("mesh").getAsInt()).getAsJsonObject();
            for (JsonElement primEl : mesh.getAsJsonArray("primitives")) {
                addPrimitive(primEl.getAsJsonObject(), world, groupsByMaterial, nodeIndex);
            }
        }

        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                traverseNode(nodesJson, child.getAsInt(), world, meshesJson, groupsByMaterial);
            }
        }
    }

    private void addPrimitive(JsonObject primitive, float[] world, Map<Integer, RenderGroup> groupsByMaterial, int nodeIndex) {
        JsonObject attributes = primitive.getAsJsonObject("attributes");
        if (primitive.has("mode") && primitive.get("mode").getAsInt() != 4) return;

        int posAccessorIdx = attributes.get("POSITION").getAsInt();
        float[] positions = readAccessor(posAccessorIdx);

        boolean hasNormals = attributes.has("NORMAL");
        float[] normals = hasNormals ? readAccessor(attributes.get("NORMAL").getAsInt()) : null;

        boolean hasUVs = attributes.has("TEXCOORD_0");
        float[] uvs = hasUVs ? readAccessor(attributes.get("TEXCOORD_0").getAsInt()) : null;

        int vertCount0 = positions.length / 3;
        int[] vJoints = null;
        float[] vWeights = null;
        JsonObject nodeJson = nodeIndex >= 0 ? root.getAsJsonArray("nodes").get(nodeIndex).getAsJsonObject() : null;
        boolean skinned = skeleton != null && nodeJson != null && nodeJson.has("skin") && nodeJson.get("skin").getAsInt() == 0
                && attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0");
        if (skeleton != null) {
            vJoints = new int[vertCount0 * 4];
            vWeights = new float[vertCount0 * 4];
            if (skinned) {
                float[] rawJoints = readAccessor(attributes.get("JOINTS_0").getAsInt());
                float[] rawWeights = readAccessor(attributes.get("WEIGHTS_0").getAsInt());
                for (int v = 0; v < vertCount0; v++) {
                    float sum = 0;
                    for (int k = 0; k < 4; k++) {
                        int file = (int) rawJoints[v * 4 + k];
                        float w = rawWeights[v * 4 + k];
                        boolean ok = w > 0 && file >= 0 && file < fileJointToOurs.length;
                        vJoints[v * 4 + k] = ok ? fileJointToOurs[file] : -1;
                        vWeights[v * 4 + k] = ok ? w : 0;
                        sum += vWeights[v * 4 + k];
                    }
                    if (sum <= 1e-6f) {
                        vJoints[v * 4] = -1;
                        vWeights[v * 4] = 1;
                        continue;
                    }
                    for (int k = 0; k < 4; k++) vWeights[v * 4 + k] /= sum;

                    float px = 0, py = 0, pz = 0, nx = 0, ny = 0, nz = 0;
                    float x = positions[v * 3], y = positions[v * 3 + 1], z = positions[v * 3 + 2];
                    for (int k = 0; k < 4; k++) {
                        int j = vJoints[v * 4 + k];
                        if (j < 0) continue;
                        float w = vWeights[v * 4 + k];
                        float[] m = skinMatrices[j];
                        px += w * (m[0] * x + m[4] * y + m[8] * z + m[12]);
                        py += w * (m[1] * x + m[5] * y + m[9] * z + m[13]);
                        pz += w * (m[2] * x + m[6] * y + m[10] * z + m[14]);
                        if (normals != null) {
                            float a = normals[v * 3], b = normals[v * 3 + 1], c = normals[v * 3 + 2];
                            nx += w * (m[0] * a + m[4] * b + m[8] * c);
                            ny += w * (m[1] * a + m[5] * b + m[9] * c);
                            nz += w * (m[2] * a + m[6] * b + m[10] * c);
                        }
                    }
                    positions[v * 3] = px;
                    positions[v * 3 + 1] = py;
                    positions[v * 3 + 2] = pz;
                    if (normals != null) {
                        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (len > 1e-8f) {
                            normals[v * 3] = nx / len;
                            normals[v * 3 + 1] = ny / len;
                            normals[v * 3 + 2] = nz / len;
                        }
                    }
                }
            } else {
                int owner = nodeIndex >= 0 ? owningJoint(nodeIndex) : -1;
                for (int v = 0; v < vertCount0; v++) {
                    vJoints[v * 4] = owner;
                    vWeights[v * 4] = 1;
                    for (int k = 1; k < 4; k++) vJoints[v * 4 + k] = -1;
                }
            }
            if (skinned) world = Matrix4.identity();
        }

        int[] indices;
        if (primitive.has("indices")) {
            indices = readIndexAccessor(primitive.get("indices").getAsInt());
        } else {
            int vertCount = positions.length / 3;
            indices = new int[vertCount];
            for (int i = 0; i < vertCount; i++) indices[i] = i;
        }

        int materialIdx = primitive.has("material") ? primitive.get("material").getAsInt() : -1;
        RenderGroup group = groupsByMaterial.computeIfAbsent(materialIdx, this::buildGroupForMaterial);
        group.partList.add(group.positionList.size() / 3);

        if (hasNormals) {
            int base = group.positionList.size() / 3;
            int vertCount = positions.length / 3;
            for (int v = 0; v < vertCount; v++) {
                float[] p = Matrix4.transformPoint(world, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]);
                group.positionList.add(p[0]);
                group.positionList.add(p[1]);
                group.positionList.add(p[2]);
                float[] n = Matrix4.transformDirection(world, normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]);
                group.normalList.add(n[0]);
                group.normalList.add(n[1]);
                group.normalList.add(n[2]);
                group.uvList.add(hasUVs ? uvs[v * 2] : 0f);
                group.uvList.add(hasUVs ? uvs[v * 2 + 1] : 0f);
                if (vJoints != null) addSkin(group, vJoints, vWeights, v);
            }
            for (int index : indices) group.indexList.add(base + index);
            return;
        }

        for (int i = 0; i + 2 < indices.length; i += 3) {
            float[][] corner = new float[3][];
            for (int c = 0; c < 3; c++) {
                int idx = indices[i + c];
                corner[c] = Matrix4.transformPoint(world, positions[idx * 3], positions[idx * 3 + 1], positions[idx * 3 + 2]);
            }
            float ax = corner[1][0] - corner[0][0], ay = corner[1][1] - corner[0][1], az = corner[1][2] - corner[0][2];
            float bx = corner[2][0] - corner[0][0], by = corner[2][1] - corner[0][1], bz = corner[2][2] - corner[0][2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            for (int c = 0; c < 3; c++) {
                int idx = indices[i + c];
                group.indexList.add(group.positionList.size() / 3);
                group.positionList.add(corner[c][0]);
                group.positionList.add(corner[c][1]);
                group.positionList.add(corner[c][2]);
                group.normalList.add(nx);
                group.normalList.add(ny);
                group.normalList.add(nz);
                group.uvList.add(hasUVs ? uvs[idx * 2] : 0f);
                group.uvList.add(hasUVs ? uvs[idx * 2 + 1] : 0f);
                if (vJoints != null) addSkin(group, vJoints, vWeights, idx);
            }
        }
    }

    private static void addSkin(RenderGroup group, int[] joints, float[] weights, int vertex) {
        for (int k = 0; k < 4; k++) {
            group.jointList.add(joints[vertex * 4 + k]);
            group.weightList.add(weights[vertex * 4 + k]);
        }
    }

    private RenderGroup buildGroupForMaterial(int materialIdx) {
        RenderGroup group = new RenderGroup();
        if (materialIdx < 0) return group;

        JsonArray materials = root.getAsJsonArray("materials");
        if (materials == null || materialIdx >= materials.size()) return group;

        JsonObject material = materials.get(materialIdx).getAsJsonObject();
        JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");

        JsonObject specGloss = null;
        if (material.has("extensions")) {
            JsonObject extensions = material.getAsJsonObject("extensions");
            if (extensions.has("KHR_materials_pbrSpecularGlossiness")) {
                specGloss = extensions.getAsJsonObject("KHR_materials_pbrSpecularGlossiness");
            }
        }

        JsonArray colorFactor = null;
        Integer textureIdx = null;
        if (pbr != null && pbr.has("baseColorTexture")) {
            textureIdx = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
            if (pbr.has("baseColorFactor")) colorFactor = pbr.getAsJsonArray("baseColorFactor");
        } else if (specGloss != null && specGloss.has("diffuseTexture")) {
            textureIdx = specGloss.getAsJsonObject("diffuseTexture").get("index").getAsInt();
            if (specGloss.has("diffuseFactor")) colorFactor = specGloss.getAsJsonArray("diffuseFactor");
        } else if (pbr != null && pbr.has("baseColorFactor")) {
            colorFactor = pbr.getAsJsonArray("baseColorFactor");
        } else if (specGloss != null && specGloss.has("diffuseFactor")) {
            colorFactor = specGloss.getAsJsonArray("diffuseFactor");
        }

        if (colorFactor != null) {
            for (int i = 0; i < 4 && i < colorFactor.size(); i++) {
                group.baseColorFactor[i] = colorFactor.get(i).getAsFloat();
            }
        }

        if (textureIdx != null) {
            try {
                group.textureLocation = loadTextureByIndex(textureIdx);
                group.hasTexture = group.textureLocation != null;
            } catch (Exception e) {
                System.err.println("[GltfModel] No texture loaded for material " + materialIdx + " (" + e.getMessage() + "), rendering untextured.");
            }
        }

        return group;
    }

    private ResourceLocation loadTextureByIndex(int textureIdx) throws IOException {
        if (textureCache.containsKey(textureIdx)) return textureCache.get(textureIdx);

        JsonArray textures = root.getAsJsonArray("textures");
        JsonArray images = root.getAsJsonArray("images");
        JsonObject textureObj = textures.get(textureIdx).getAsJsonObject();
        int imageIdx = textureObj.get("source").getAsInt();
        JsonObject imageObj = images.get(imageIdx).getAsJsonObject();

        BufferedImage image;
        if (imageObj.has("uri")) {
            String uri = imageObj.get("uri").getAsString();
            if (uri.startsWith("data:")) {
                int comma = uri.indexOf(',');
                byte[] bytes = Base64.getDecoder().decode(uri.substring(comma + 1));
                image = ImageIO.read(new ByteArrayInputStream(bytes));
            } else {
                File imageFile = new File(dir, uri);
                if (!imageFile.exists()) {
                    File flattened = new File(dir, new File(uri).getName());
                    if (flattened.exists()) imageFile = flattened;
                }
                image = ImageIO.read(imageFile);
            }
        } else if (imageObj.has("bufferView")) {
            int bvIdx = imageObj.get("bufferView").getAsInt();
            JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
            int bufferIdx = bv.get("buffer").getAsInt();
            int byteOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
            int byteLength = bv.get("byteLength").getAsInt();

            byte[] imgBytes = new byte[byteLength];
            System.arraycopy(buffers[bufferIdx], byteOffset, imgBytes, 0, byteLength);
            image = ImageIO.read(new ByteArrayInputStream(imgBytes));
        } else {
            return null;
        }

        if (image == null) return null;
        image = downscale(image);

        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation(cacheKey + "_tex" + textureIdx, texture);
        textureCache.put(textureIdx, location);
        return location;
    }

    private static final int MAX_TEXTURE_SIZE = 1024;

    private static BufferedImage downscale(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        while (Math.max(w, h) > MAX_TEXTURE_SIZE) {
            int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            BufferedImage next = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = next.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, nw, nh, null);
            g.dispose();
            image = next;
            w = nw;
            h = nh;
        }
        return image;
    }

    private byte[] resolveBufferBytes(String uri, File dir) throws IOException {
        if (uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            String base64 = uri.substring(comma + 1);
            return Base64.getDecoder().decode(base64);
        } else {
            File binFile = new File(dir, uri);
            try (InputStream in = new FileInputStream(binFile)) {
                return in.readAllBytes();
            }
        }
    }

    private float[] readAccessor(int accessorIdx) {
        JsonObject accessor = accessors.get(accessorIdx).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        String type = accessor.get("type").getAsString();
        int comps = switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalStateException("Unsupported accessor type: " + type);
        };
        float[] out = new float[count * comps];
        if (!accessor.has("bufferView")) return out;

        int componentType = accessor.get("componentType").getAsInt();
        int size = switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new IllegalStateException("Unsupported componentType: " + componentType);
        };
        boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();

        JsonObject bufferView = bufferViews.get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int bufferIdx = bufferView.get("buffer").getAsInt();
        int base = (bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0)
                + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
        int stride = bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : comps * size;
        ByteBuffer bb = ByteBuffer.wrap(buffers[bufferIdx]).order(ByteOrder.LITTLE_ENDIAN);

        if (componentType == 5126 && stride == comps * 4) {
            bb.position(base);
            bb.asFloatBuffer().get(out);
            return out;
        }
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < comps; c++) {
                int at = base + i * stride + c * size;
                float value = switch (componentType) {
                    case 5126 -> bb.getFloat(at);
                    case 5121 -> normalized ? (bb.get(at) & 0xFF) / 255f : (bb.get(at) & 0xFF);
                    case 5120 -> normalized ? Math.max(bb.get(at) / 127f, -1f) : bb.get(at);
                    case 5123 -> normalized ? (bb.getShort(at) & 0xFFFF) / 65535f : (bb.getShort(at) & 0xFFFF);
                    case 5122 -> normalized ? Math.max(bb.getShort(at) / 32767f, -1f) : bb.getShort(at);
                    default -> (float) (bb.getInt(at) & 0xFFFFFFFFL);
                };
                out[i * comps + c] = value;
            }
        }
        return out;
    }

    private int[] readIndexAccessor(int accessorIdx) {
        JsonObject accessor = accessors.get(accessorIdx).getAsJsonObject();
        int bufferViewIdx = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();

        JsonObject bufferView = bufferViews.get(bufferViewIdx).getAsJsonObject();
        int bufferIdx = bufferView.get("buffer").getAsInt();
        int byteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int accessorByteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;

        ByteBuffer bb = ByteBuffer.wrap(buffers[bufferIdx]).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(byteOffset + accessorByteOffset);

        int[] out = new int[count];

        switch (componentType) {
            case 5121 -> {
                for (int i = 0; i < count; i++) out[i] = bb.get() & 0xFF;
            }
            case 5123 -> {
                for (int i = 0; i < count; i++) out[i] = bb.getShort() & 0xFFFF;
            }
            case 5125 -> {
                IntBuffer ib = bb.asIntBuffer();
                ib.get(out);
            }
            default -> throw new IllegalStateException("Unsupported index componentType: " + componentType);
        }
        return out;
    }

    private float[] readVec(JsonArray arr, int size) {
        float[] out = new float[size];
        for (int i = 0; i < size; i++) out[i] = arr.get(i).getAsFloat();
        return out;
    }

    private float[] readMatrix(JsonArray arr) {
        float[] out = new float[16];
        for (int i = 0; i < 16; i++) out[i] = arr.get(i).getAsFloat();
        return out;
    }

    private static final int MAX_BATCH_TRIANGLES = 20000;

    private static final int LOADED = -1;

    public void render() {
        render(null, null, LOADED);
    }

    public void render(float[][] posedPositions, float[][] posedNormals, int version) {
        if (!OpenGlHelper.vboSupported) {
            renderImmediate(posedPositions, posedNormals);
            return;
        }
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        try {
            for (int g = 0; g < groups.size(); g++) {
                RenderGroup group = groups.get(g);
                if (group.indices.length == 0) continue;
                boolean posed = posedPositions != null && posedPositions[g] != null;
                upload(group, posed ? posedPositions[g] : group.positions,
                        posed ? posedNormals[g] : group.normals, posed ? version : LOADED);

                if (group.hasTexture) {
                    mc.getTextureManager().bindTexture(group.textureLocation);
                    GlStateManager.enableTexture2D();
                } else {
                    GlStateManager.disableTexture2D();
                }
                GlStateManager.color(group.baseColorFactor[0], group.baseColorFactor[1],
                        group.baseColorFactor[2], group.baseColorFactor[3]);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, group.vbo);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 24, 0L);
                GL11.glNormalPointer(GL11.GL_FLOAT, 24, 12L);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, group.uvVbo);
                GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, group.ibo);
                GL11.glDrawElements(GL11.GL_TRIANGLES, group.indices.length, GL11.GL_UNSIGNED_INT, 0L);
            }
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private void upload(RenderGroup group, float[] positions, float[] normals, int version) {
        if (group.ibo == 0) {
            group.ibo = GL15.glGenBuffers();
            ByteBuffer ib = ByteBuffer.allocateDirect(group.indices.length * 4).order(ByteOrder.nativeOrder());
            ib.asIntBuffer().put(group.indices);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, group.ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);

            group.uvVbo = GL15.glGenBuffers();
            ByteBuffer uv = ByteBuffer.allocateDirect(group.uvs.length * 4).order(ByteOrder.nativeOrder());
            uv.asFloatBuffer().put(group.uvs);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, group.uvVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uv, GL15.GL_STATIC_DRAW);

            group.vbo = GL15.glGenBuffers();
        }
        if (group.uploadedVersion == version) return;
        int n = group.vertexCount();
        if (group.stream == null) group.stream = ByteBuffer.allocateDirect(n * 24).order(ByteOrder.nativeOrder());
        if (group.interleaved == null) group.interleaved = new float[n * 6];
        float[] out = group.interleaved;
        for (int v = 0, p = 0, o = 0; v < n; v++, p += 3, o += 6) {
            out[o] = positions[p];
            out[o + 1] = positions[p + 1];
            out[o + 2] = positions[p + 2];
            out[o + 3] = normals[p];
            out[o + 4] = normals[p + 1];
            out[o + 5] = normals[p + 2];
        }
        group.stream.clear();
        group.stream.asFloatBuffer().put(out);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, group.vbo);

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, group.stream, version == LOADED ? GL15.GL_STATIC_DRAW : GL15.GL_STREAM_DRAW);
        group.uploadedVersion = version;
        if (version == LOADED) {
            group.stream = null;
            group.interleaved = null;
        }
    }

    public void dispose() {
        for (RenderGroup group : groups) {
            if (group.ibo != 0) GL15.glDeleteBuffers(group.ibo);
            if (group.uvVbo != 0) GL15.glDeleteBuffers(group.uvVbo);
            if (group.vbo != 0) GL15.glDeleteBuffers(group.vbo);
            group.ibo = group.uvVbo = group.vbo = 0;
            group.stream = null;
            group.interleaved = null;
            group.uploadedVersion = Integer.MIN_VALUE;
        }
        for (ResourceLocation location : textureCache.values()) {
            if (location != null) mc.getTextureManager().deleteTexture(location);
        }
        textureCache.clear();
    }

    private void renderImmediate(float[][] posedPositions, float[][] posedNormals) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        for (int g = 0; g < groups.size(); g++) {
            RenderGroup group = groups.get(g);
            int[] indices = group.indices;
            if (indices.length == 0) continue;
            float[] positions = posedPositions != null && posedPositions[g] != null ? posedPositions[g] : group.positions;
            float[] normals = posedNormals != null && posedNormals[g] != null ? posedNormals[g] : group.normals;
            float[] uvs = group.uvs;

            if (group.hasTexture) {
                mc.getTextureManager().bindTexture(group.textureLocation);
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.color(group.baseColorFactor[0], group.baseColorFactor[1],
                    group.baseColorFactor[2], group.baseColorFactor[3]);

            int batchVerts = MAX_BATCH_TRIANGLES * 3;
            for (int batchStart = 0; batchStart < indices.length; batchStart += batchVerts) {
                int batchEnd = Math.min(batchStart + batchVerts, indices.length);
                wr.begin(GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_NORMAL);
                try {
                    for (int k = batchStart; k < batchEnd; k++) {
                        int i = indices[k];
                        wr.pos(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
                                .tex(uvs[i * 2], uvs[i * 2 + 1])
                                .normal(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])
                                .endVertex();
                    }
                } catch (Exception e) {
                    System.err.println("[GltfModel] Skipped a bad vertex batch: " + e);
                } finally {
                    try {
                        tess.draw();
                    } catch (Exception drawEx) {
                        System.err.println("[GltfModel] Tessellator draw failed after a bad batch: " + drawEx);
                    }
                }
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public boolean hasTexture() {
        return hasTexture;
    }

    public int groupCount() {
        return groups.size();
    }

    public float[] groupPositions(int group) {
        return groups.get(group).positions;
    }

    public float[] groupNormals(int group) {
        return groups.get(group).normals;
    }

    public int[] groupParts(int group) {
        return groups.get(group).parts;
    }

    public Skeleton skeleton() {
        return skeleton;
    }

    public int[] groupJoints(int group) {
        return groups.get(group).joints;
    }

    public float[] groupWeights(int group) {
        return groups.get(group).weights;
    }

    public int vertexCount() {
        int total = 0;
        for (RenderGroup group : groups) total += group.vertexCount();
        return total;
    }

    public float getHeight() {
        return robustHeight;
    }

    private static final class Matrix4 {
        static float[] identity() {
            return new float[]{
                    1, 0, 0, 0,
                    0, 1, 0, 0,
                    0, 0, 1, 0,
                    0, 0, 0, 1
            };
        }

        static float[] multiply(float[] a, float[] b) {
            float[] out = new float[16];
            for (int col = 0; col < 4; col++) {
                for (int row = 0; row < 4; row++) {
                    float sum = 0;
                    for (int k = 0; k < 4; k++) {
                        sum += a[k * 4 + row] * b[col * 4 + k];
                    }
                    out[col * 4 + row] = sum;
                }
            }
            return out;
        }

        static float[] transformPoint(float[] m, float x, float y, float z) {
            return new float[]{
                    m[0] * x + m[4] * y + m[8] * z + m[12],
                    m[1] * x + m[5] * y + m[9] * z + m[13],
                    m[2] * x + m[6] * y + m[10] * z + m[14]
            };
        }

        static float[] transformDirection(float[] m, float x, float y, float z) {
            float nx = m[0] * x + m[4] * y + m[8] * z;
            float ny = m[1] * x + m[5] * y + m[9] * z;
            float nz = m[2] * x + m[6] * y + m[10] * z;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            return new float[]{nx, ny, nz};
        }

        static float[] fromTRS(float[] t, float[] r, float[] s) {
            float x = r[0], y = r[1], z = r[2], w = r[3];
            float x2 = x + x, y2 = y + y, z2 = z + z;
            float xx = x * x2, xy = x * y2, xz = x * z2;
            float yy = y * y2, yz = y * z2, zz = z * z2;
            float wx = w * x2, wy = w * y2, wz = w * z2;
            float sx = s[0], sy = s[1], sz = s[2];

            float[] out = new float[16];
            out[0] = (1 - (yy + zz)) * sx;
            out[1] = (xy + wz) * sx;
            out[2] = (xz - wy) * sx;
            out[3] = 0;
            out[4] = (xy - wz) * sy;
            out[5] = (1 - (xx + zz)) * sy;
            out[6] = (yz + wx) * sy;
            out[7] = 0;
            out[8] = (xz + wy) * sz;
            out[9] = (yz - wx) * sz;
            out[10] = (1 - (xx + yy)) * sz;
            out[11] = 0;
            out[12] = t[0];
            out[13] = t[1];
            out[14] = t[2];
            out[15] = 1;
            return out;
        }
    }
}
