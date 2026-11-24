package rip.tempest.utils.render.knife;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class KnifeModel {
    private static final float SCALE = 1f / 16f;

    static final int STRIDE = 32;
    private static final int MAX_ARM_VERTS = 40 * 36;
    private static final float PX = 1f / 64f;
    private static final float FOREARM_LENGTH = 16f;

    public static final class Sequence {
        public final String name;
        public final float fps;
        public final int frames;
        final float[] data;
        final List<SoundEvent> events = new ArrayList<>();

        Sequence(String name, float fps, int frames, float[] data) {
            this.name = name;
            this.fps = fps;
            this.frames = frames;
            this.data = data;
        }

        public float duration() {
            return (frames - 1) / fps;
        }
    }

    static final class SoundEvent {
        final float time;
        final String sound;

        SoundEvent(float time, String sound) {
            this.time = time;
            this.sound = sound;
        }
    }

    public static final class Skin {
        public final String id, diffuse, exponentMap;
        public final float boost, albedoBoost, exponent;
        public final float[] fresnel, envTint;

        Skin(String id, String diffuse, String exponentMap, float boost, float albedoBoost, float exponent, float[] fresnel, float[] envTint) {
            this.id = id;
            this.diffuse = diffuse;
            this.exponentMap = exponentMap;
            this.boost = boost;
            this.albedoBoost = albedoBoost;
            this.exponent = exponent;
            this.fresnel = fresnel;
            this.envTint = envTint;
        }
    }

    private static final class Arm {
        int forearm, hand;
        final int[][] fingers = new int[5][3];
    }

    public final File folder;
    public final int boneCount;
    public final Map<String, Sequence> sequences = new LinkedHashMap<>();
    public final Map<String, Skin> skins = new LinkedHashMap<>();

    private final int[] parents;
    private final float[] poseToBone;
    private final float[] mesh;
    private final float[] weights;
    private final byte[] boneIds;
    private final float[] world;
    private final float[] skinning;
    private final Arm rightArm, leftArm;
    private final int muzzleBone;
    private final float[] muzzleLocal;

    final ByteBuffer knifeVerts;
    final IntBuffer indices;
    final ByteBuffer armVerts;
    int armVertCount;

    public static KnifeModel load(final File root, final String name) throws IOException {
        return new KnifeModel(new File(root, name), ByteBuffer.wrap(readGzip(new File(root, name + ".knife"))).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static byte[] readGzip(final File file) throws IOException {
        try (InputStream in = new GZIPInputStream(new FileInputStream(file), 1 << 16)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 22);
            final byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private static String utf(final ByteBuffer in) {
        final byte[] bytes = new byte[in.getShort() & 0xffff];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private KnifeModel(final File folder, final ByteBuffer in) throws IOException {
        this.folder = folder;
        final byte[] magic = new byte[4];
        in.get(magic);
        final int version = in.getInt();
        if (magic[0] != 'T' || magic[1] != 'K' || magic[2] != 'N' || magic[3] != 'F' || version != 4) {
            throw new IOException("Not a knife file");
        }

        boneCount = in.getInt();
        parents = new int[boneCount];
        poseToBone = new float[boneCount * 12];
        final Map<String, Integer> boneIndex = new HashMap<>();
        for (int b = 0; b < boneCount; b++) {
            boneIndex.put(utf(in), b);
            parents[b] = in.getInt();
            for (int k = 0; k < 12; k++) poseToBone[b * 12 + k] = in.getFloat();
        }

        final int vertCount = in.getInt();
        mesh = new float[vertCount * 8];
        weights = new float[vertCount * 3];
        boneIds = new byte[vertCount * 3];
        for (int v = 0; v < vertCount; v++) {
            for (int k = 0; k < 8; k++) mesh[v * 8 + k] = in.getFloat();
            for (int k = 0; k < 3; k++) weights[v * 3 + k] = in.getFloat();
            for (int k = 0; k < 3; k++) boneIds[v * 3 + k] = in.get();
        }

        final int indexCount = in.getInt();
        indices = ByteBuffer.allocateDirect(indexCount * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int k = 0; k < indexCount; k++) indices.put(in.getInt());
        indices.flip();

        final int seqCount = in.getInt();
        for (int s = 0; s < seqCount; s++) {
            final String label = utf(in);
            final float fps = in.getFloat();
            final int frames = in.getInt();
            final float[] data = new float[frames * boneCount * 7];
            for (int k = 0; k < data.length; k++) data[k] = in.getFloat();
            sequences.put(label, new Sequence(label, fps, frames, data));
        }

        final int skinCount = in.getInt();
        for (int s = 0; s < skinCount; s++) {
            final String id = utf(in), diffuse = utf(in), exponentMap = utf(in);
            final float boost = in.getFloat(), albedo = in.getFloat(), exponent = in.getFloat();
            final float[] fresnel = {in.getFloat(), in.getFloat(), in.getFloat()};
            final float[] env = {in.getFloat(), in.getFloat(), in.getFloat()};
            skins.put(id, new Skin(id, diffuse, exponentMap, boost, albedo, exponent, fresnel, env));
        }

        final int eventCount = in.getInt();
        for (int e = 0; e < eventCount; e++) {
            final Sequence sequence = sequences.get(utf(in));
            final float cycle = in.getFloat();
            sequence.events.add(new SoundEvent(cycle * sequence.duration(), utf(in)));
        }

        muzzleBone = in.getInt();
        muzzleLocal = new float[]{in.getFloat(), in.getFloat(), in.getFloat()};

        world = new float[boneCount * 12];
        skinning = new float[boneCount * 12];
        knifeVerts = ByteBuffer.allocateDirect(vertCount * STRIDE).order(ByteOrder.nativeOrder());
        armVerts = ByteBuffer.allocateDirect(MAX_ARM_VERTS * STRIDE).order(ByteOrder.nativeOrder());

        final String rig = boneIndex.containsKey("ValveBiped.Bip01_R_Forearm") ? "ValveBiped" : "v_weapon";
        rightArm = findArm(boneIndex, rig, 'R');
        leftArm = boneIndex.containsKey(rig + ".Bip01_L_Forearm") ? findArm(boneIndex, rig, 'L') : null;
    }

    private static Arm findArm(final Map<String, Integer> index, final String rig, final char side) throws IOException {
        final Arm arm = new Arm();
        final String prefix = rig + ".Bip01_" + side + "_";
        arm.forearm = required(index, prefix + "Forearm");
        arm.hand = required(index, prefix + "Hand");
        for (int f = 0; f < 5; f++) {
            arm.fingers[f][0] = required(index, prefix + "Finger" + f);
            arm.fingers[f][1] = required(index, prefix + "Finger" + f + "1");
            arm.fingers[f][2] = required(index, prefix + "Finger" + f + "2");
        }
        return arm;
    }

    private static int required(final Map<String, Integer> index, final String bone) throws IOException {
        final Integer found = index.get(bone);
        if (found == null) throw new IOException("Missing bone " + bone);
        return found;
    }

    public void sample(final Sequence seq, final float time, final float[] out) {
        final float frame = Math.max(0f, Math.min(seq.frames - 1f, time * seq.fps));
        final int a = (int) frame;
        final int b = Math.min(seq.frames - 1, a + 1);
        final int stride = boneCount * 7;
        blend(seq.data, a * stride, seq.data, b * stride, frame - a, out);
    }

    public void blend(final float[] from, final int fromOffset, final float[] to, final int toOffset, final float t, final float[] out) {
        for (int k = 0; k < boneCount; k++) {
            final int i = fromOffset + k * 7, j = toOffset + k * 7, o = k * 7;
            out[o] = from[i] + (to[j] - from[i]) * t;
            out[o + 1] = from[i + 1] + (to[j + 1] - from[i + 1]) * t;
            out[o + 2] = from[i + 2] + (to[j + 2] - from[i + 2]) * t;
            final float dot = from[i + 3] * to[j + 3] + from[i + 4] * to[j + 4] + from[i + 5] * to[j + 5] + from[i + 6] * to[j + 6];
            final float sign = dot < 0f ? -1f : 1f;
            final float qx = from[i + 3] + (to[j + 3] * sign - from[i + 3]) * t;
            final float qy = from[i + 4] + (to[j + 4] * sign - from[i + 4]) * t;
            final float qz = from[i + 5] + (to[j + 5] * sign - from[i + 5]) * t;
            final float qw = from[i + 6] + (to[j + 6] * sign - from[i + 6]) * t;
            final float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            out[o + 3] = qx / len;
            out[o + 4] = qy / len;
            out[o + 5] = qz / len;
            out[o + 6] = qw / len;
        }
    }

    void build(final float[] pose, final float[] offset, final boolean showArms, final boolean slim) {
        solveBones(pose);
        skinKnife(offset);
        armVertCount = 0;
        armVerts.clear();
        if (showArms) {
            buildArm(rightArm, 40, 16, offset, slim);
            if (leftArm != null) buildArm(leftArm, 32, 48, offset, slim);
        }
        armVerts.position(0);
    }

    float[] muzzleView(final float[] offset) {
        if (muzzleBone < 0) return null;
        final int m = muzzleBone * 12;
        final float lx = muzzleLocal[0], ly = muzzleLocal[1], lz = muzzleLocal[2];
        final float x = world[m] * lx + world[m + 1] * ly + world[m + 2] * lz + world[m + 3];
        final float y = world[m + 4] * lx + world[m + 5] * ly + world[m + 6] * lz + world[m + 7];
        final float z = world[m + 8] * lx + world[m + 9] * ly + world[m + 10] * lz + world[m + 11];
        return new float[]{(-y + offset[0]) * SCALE, (z + offset[2]) * SCALE, (-x - offset[1]) * SCALE};
    }

    private void solveBones(final float[] pose) {
        for (int b = 0; b < boneCount; b++) {
            final int p = b * 7, m = b * 12;
            final float x = pose[p + 3], y = pose[p + 4], z = pose[p + 5], w = pose[p + 6];
            final float l00 = 1 - 2 * (y * y + z * z), l01 = 2 * (x * y - w * z), l02 = 2 * (x * z + w * y), l03 = pose[p];
            final float l10 = 2 * (x * y + w * z), l11 = 1 - 2 * (x * x + z * z), l12 = 2 * (y * z - w * x), l13 = pose[p + 1];
            final float l20 = 2 * (x * z - w * y), l21 = 2 * (y * z + w * x), l22 = 1 - 2 * (x * x + y * y), l23 = pose[p + 2];
            final int parent = parents[b];
            if (parent < 0) {
                world[m] = l00; world[m + 1] = l01; world[m + 2] = l02; world[m + 3] = l03;
                world[m + 4] = l10; world[m + 5] = l11; world[m + 6] = l12; world[m + 7] = l13;
                world[m + 8] = l20; world[m + 9] = l21; world[m + 10] = l22; world[m + 11] = l23;
            } else {
                final int q = parent * 12;
                for (int r = 0; r < 3; r++) {
                    final float a = world[q + r * 4], c = world[q + r * 4 + 1], e = world[q + r * 4 + 2];
                    world[m + r * 4] = a * l00 + c * l10 + e * l20;
                    world[m + r * 4 + 1] = a * l01 + c * l11 + e * l21;
                    world[m + r * 4 + 2] = a * l02 + c * l12 + e * l22;
                    world[m + r * 4 + 3] = a * l03 + c * l13 + e * l23 + world[q + r * 4 + 3];
                }
            }
            for (int r = 0; r < 3; r++) {
                final float a = world[m + r * 4], c = world[m + r * 4 + 1], e = world[m + r * 4 + 2];
                skinning[m + r * 4] = a * poseToBone[m] + c * poseToBone[m + 4] + e * poseToBone[m + 8];
                skinning[m + r * 4 + 1] = a * poseToBone[m + 1] + c * poseToBone[m + 5] + e * poseToBone[m + 9];
                skinning[m + r * 4 + 2] = a * poseToBone[m + 2] + c * poseToBone[m + 6] + e * poseToBone[m + 10];
                skinning[m + r * 4 + 3] = a * poseToBone[m + 3] + c * poseToBone[m + 7] + e * poseToBone[m + 11] + world[m + r * 4 + 3];
            }
        }
    }

    private void skinKnife(final float[] offset) {
        final int count = mesh.length / 8;
        knifeVerts.clear();
        for (int v = 0; v < count; v++) {
            final int i = v * 8;
            final float px = mesh[i], py = mesh[i + 1], pz = mesh[i + 2], nx = mesh[i + 3], ny = mesh[i + 4], nz = mesh[i + 5];
            float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
            for (int k = 0; k < 3; k++) {
                final float weight = weights[v * 3 + k];
                if (weight <= 0f) continue;
                final int m = (boneIds[v * 3 + k] & 0xff) * 12;
                x += weight * (skinning[m] * px + skinning[m + 1] * py + skinning[m + 2] * pz + skinning[m + 3]);
                y += weight * (skinning[m + 4] * px + skinning[m + 5] * py + skinning[m + 6] * pz + skinning[m + 7]);
                z += weight * (skinning[m + 8] * px + skinning[m + 9] * py + skinning[m + 10] * pz + skinning[m + 11]);
                a += weight * (skinning[m] * nx + skinning[m + 1] * ny + skinning[m + 2] * nz);
                b += weight * (skinning[m + 4] * nx + skinning[m + 5] * ny + skinning[m + 6] * nz);
                c += weight * (skinning[m + 8] * nx + skinning[m + 9] * ny + skinning[m + 10] * nz);
            }
            putVertex(knifeVerts, x, y, z, a, b, c, mesh[i + 6], mesh[i + 7], offset);
        }
        knifeVerts.position(0);
    }

    private static void putVertex(final ByteBuffer to, final float x, final float y, final float z, final float a, final float b, final float c, final float u, final float v, final float[] offset) {
        to.putFloat((-y + offset[0]) * SCALE);
        to.putFloat((z + offset[2]) * SCALE);
        to.putFloat((-x - offset[1]) * SCALE);
        to.putFloat(-b);
        to.putFloat(c);
        to.putFloat(-a);
        to.putFloat(u);
        to.putFloat(v);
    }

    private void buildArm(final Arm arm, final int skinU, final int skinV, final float[] offset, final boolean slim) {
        final int width = slim ? 3 : 4;
        final float handX = world[arm.hand * 12 + 3], handY = world[arm.hand * 12 + 7], handZ = world[arm.hand * 12 + 11];
        final float[] hand = {handX, handY, handZ};
        final int index = arm.fingers[1][0], pinky = arm.fingers[4][0];
        final float[] across = {world[index * 12 + 3] - world[pinky * 12 + 3], world[index * 12 + 7] - world[pinky * 12 + 7], world[index * 12 + 11] - world[pinky * 12 + 11]};

        final float[] toElbow = {world[arm.forearm * 12 + 3] - handX, world[arm.forearm * 12 + 7] - handY, world[arm.forearm * 12 + 11] - handZ};
        normalize(toElbow);
        final float[] elbow = {handX + toElbow[0] * FOREARM_LENGTH, handY + toElbow[1] * FOREARM_LENGTH, handZ + toElbow[2] * FOREARM_LENGTH};
        final float[] forearmUv = {skinU, skinV + 4, skinV + 16, width};
        box(elbow, hand, across, 1.2f, 1.2f, forearmUv, offset);

        final float[] knuckles = new float[3];
        for (int f = 1; f < 5; f++) {
            final int b = arm.fingers[f][0] * 12;
            knuckles[0] += world[b + 3] * .25f;
            knuckles[1] += world[b + 7] * .25f;
            knuckles[2] += world[b + 11] * .25f;
        }
        final float palmHalf = Math.max(1f, length(across) * .5f + .3f);
        box(hand, knuckles, across, palmHalf, .55f, new float[]{skinU, skinV + 13, skinV + 16, width}, offset);

        for (int f = 0; f < 5; f++) {
            float[] last = position(arm.fingers[f][0]);
            final float half = f == 0 ? .42f : .36f;
            float[] step = null;
            for (int s = 1; s < 3; s++) {
                final float[] next = position(arm.fingers[f][s]);
                box(last, next, across, half, half, new float[]{skinU, skinV + 14, skinV + 16, width}, offset);
                step = new float[]{next[0] - last[0], next[1] - last[1], next[2] - last[2]};
                last = next;
            }
            final float[] tip = {last[0] + step[0] * .85f, last[1] + step[1] * .85f, last[2] + step[2] * .85f};
            box(last, tip, across, half * .9f, half * .9f, new float[]{skinU, skinV + 14, skinV + 16, width}, offset);
        }
    }

    private float[] position(final int bone) {
        return new float[]{world[bone * 12 + 3], world[bone * 12 + 7], world[bone * 12 + 11]};
    }

    private static float length(final float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void normalize(final float[] v) {
        final float len = length(v);
        if (len > 1e-5f) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }

    private void box(final float[] a, final float[] b, final float[] ref, final float halfSide, final float halfUp, final float[] uv, final float[] offset) {
        final float[] dir = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        if (length(dir) < 1e-4f) return;
        normalize(dir);
        final float along = ref[0] * dir[0] + ref[1] * dir[1] + ref[2] * dir[2];
        final float[] side = {ref[0] - dir[0] * along, ref[1] - dir[1] * along, ref[2] - dir[2] * along};
        normalize(side);
        final float[] up = {dir[1] * side[2] - dir[2] * side[1], dir[2] * side[0] - dir[0] * side[2], dir[0] * side[1] - dir[1] * side[0]};

        final float width = uv[3];
        final float u0 = uv[0], v0 = uv[1], v1 = uv[2];
        final float[][] faces = {
                {+1, 0, u0, 4},
                {0, +1, u0 + 4, width},
                {-1, 0, u0 + 4 + width, 4},
                {0, -1, u0 + 8 + width, width}
        };
        for (final float[] f : faces) {
            final float nx = side[0] * f[0] + up[0] * f[1], ny = side[1] * f[0] + up[1] * f[1], nz = side[2] * f[0] + up[2] * f[1];
            final float tx = up[0] * f[0] - side[0] * f[1], ty = up[1] * f[0] - side[1] * f[1], tz = up[2] * f[0] - side[2] * f[1];
            final float ns = f[0] != 0 ? halfSide : halfUp, ts = f[0] != 0 ? halfUp : halfSide;
            quad(a, b, nx * ns - tx * ts, ny * ns - ty * ts, nz * ns - tz * ts, nx * ns + tx * ts, ny * ns + ty * ts, nz * ns + tz * ts,
                    nx, ny, nz, f[2] * PX, (f[2] + f[3]) * PX, v0 * PX, v1 * PX, offset);
        }
        cap(a, side, up, halfSide, halfUp, -dir[0], -dir[1], -dir[2], uv, offset);
        cap(b, side, up, halfSide, halfUp, dir[0], dir[1], dir[2], uv, offset);
    }

    private void quad(final float[] a, final float[] b, final float x0, final float y0, final float z0, final float x1, final float y1, final float z1,
                      final float nx, final float ny, final float nz, final float u0, final float u1, final float v0, final float v1, final float[] offset) {
        final float[][] corners = {
                {a[0] + x0, a[1] + y0, a[2] + z0, u0, v0},
                {a[0] + x1, a[1] + y1, a[2] + z1, u1, v0},
                {b[0] + x1, b[1] + y1, b[2] + z1, u1, v1},
                {b[0] + x0, b[1] + y0, b[2] + z0, u0, v1}
        };
        for (final int k : new int[]{0, 1, 2, 0, 2, 3}) {
            putVertex(armVerts, corners[k][0], corners[k][1], corners[k][2], nx, ny, nz, corners[k][3], corners[k][4], offset);
            armVertCount++;
        }
    }

    private void cap(final float[] at, final float[] side, final float[] up, final float halfSide, final float halfUp, final float nx, final float ny, final float nz, final float[] uv, final float[] offset) {
        final float u = (uv[0] + 4) * PX, v = (uv[2] - 1) * PX;
        final float[] signs = {-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1};
        for (int k = 0; k < 6; k++) {
            final float s = signs[k * 2] * halfSide, t = signs[k * 2 + 1] * halfUp;
            putVertex(armVerts, at[0] + side[0] * s + up[0] * t, at[1] + side[1] * s + up[1] * t, at[2] + side[2] * s + up[2] * t, nx, ny, nz, u, v, offset);
            armVertCount++;
        }
    }
}
