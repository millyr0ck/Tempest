package rip.tempest.utils.render.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SkeletalRig implements Poser {
    private enum Role { BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    private final GltfModel model;
    private final GltfModel.Skeleton skeleton;
    private final float[] toBiped;
    private final float[][] restBiped;
    private final float[][] restInverse;
    private final Role[] roles;
    private final float[][] restDirection;
    private final float[][] posed, deform;
    private final String description;

    private SkeletalRig(GltfModel model, float yawDegrees, float tiltDegrees) {
        this.model = model;
        this.skeleton = model.skeleton();
        int n = skeleton.size();

        toBiped = mul4(mul4(rotation4(rotY((float) Math.toRadians(yawDegrees))), rotation4(rotX((float) Math.toRadians(tiltDegrees)))),
                new float[]{-1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        restBiped = new float[n][];
        restInverse = new float[n][];
        for (int j = 0; j < n; j++) {
            restBiped[j] = mul4(toBiped, skeleton.restWorld[j]);
            restInverse[j] = invertAffine(skeleton.restWorld[j]);
        }
        roles = new Role[n];
        restDirection = new float[n][];
        posed = new float[n][16];
        deform = new float[n][];

        List<String> found = new ArrayList<>();
        assign(Role.BODY, findBody(), found, "body");
        assign(Role.HEAD, find("head", null), found, "head");
        int ra = findUpperArm("r"), la = findUpperArm("l");
        int rl = findUpperLeg("r"), ll = findUpperLeg("l");
        assign(Role.RIGHT_ARM, ra, found, null);
        assign(Role.LEFT_ARM, la, found, null);
        assign(Role.RIGHT_LEG, rl, found, null);
        assign(Role.LEFT_LEG, ll, found, null);
        if (ra >= 0 && la >= 0) found.add("arms");
        if (rl >= 0 && ll >= 0) found.add("legs");
        description = "own skeleton (" + n + " bones): " + String.join(", ", found);
    }

    public static SkeletalRig build(GltfModel model, float yawDegrees, float tiltDegrees) {
        if (model.skeleton() == null || model.skeleton().size() == 0) return null;
        SkeletalRig rig = new SkeletalRig(model, yawDegrees, tiltDegrees);
        boolean limbs = false;
        for (Role role : rig.roles) {
            if (role != null && role != Role.BODY && role != Role.HEAD) limbs = true;
        }
        return limbs ? rig : null;
    }

    @Override
    public String describe() {
        return description;
    }

    private void assign(Role role, int joint, List<String> found, String label) {
        if (joint < 0 || roles[joint] != null) return;
        roles[joint] = role;
        if (label != null) found.add(label);
        if (role != Role.BODY && role != Role.HEAD) {
            float[] from = position(restBiped[joint]);
            float[] dir = null;
            for (int c = 0; c < skeleton.size(); c++) {
                if (skeleton.parent[c] == joint) {
                    float[] to = position(restBiped[c]);
                    dir = new float[]{to[0] - from[0], to[1] - from[1], to[2] - from[2]};
                    if (length(dir) > 1e-5f) break;
                    dir = null;
                }
            }
            if (dir == null) dir = apply3(rotation3(restBiped[joint]), 0, 1, 0);
            restDirection[joint] = normalize(dir);
        }
    }

    private static String clean(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int colon = n.lastIndexOf(':');
        if (colon >= 0) n = n.substring(colon + 1);
        return n.replace("mixamorig", "");
    }

    private static String side(String name) {
        String n = clean(name);
        if (n.startsWith("left") || n.endsWith("left")) return "l";
        if (n.startsWith("right") || n.endsWith("right")) return "r";
        if (n.matches(".*[._\\- ]l(\\.\\d+)?$") || n.matches("^l[._\\- ].*")) return "l";
        if (n.matches(".*[._\\- ]r(\\.\\d+)?$") || n.matches("^r[._\\- ].*")) return "r";
        return null;
    }

    private static String stem(String name) {
        String n = clean(name).replace("left", "").replace("right", "");
        n = n.replaceAll("[._\\- ][lr](\\.\\d+)?$", "").replaceAll("^[lr][._\\- ]", "");
        return n.replaceAll("[^a-z0-9]", "");
    }

    private int depth(int joint) {
        int d = 0;
        for (int p = skeleton.parent[joint]; p >= 0; p = skeleton.parent[p]) d++;
        return d;
    }

    private int find(String stems, String wantSide) {
        int best = -1;
        for (int j = 0; j < skeleton.size(); j++) {
            String name = skeleton.names[j];
            if (wantSide != null && !wantSide.equals(side(name))) continue;
            String s = stem(name);
            for (String option : stems.split(",")) {
                if (s.equals(option) && (best < 0 || depth(j) < depth(best))) best = j;
            }
        }
        return best;
    }

    private int findBody() {
        int spine = -1;
        for (int j = 0; j < skeleton.size(); j++) {
            String s = stem(skeleton.names[j]);
            if ((s.startsWith("spine") || s.equals("chest") || s.equals("torso") || s.equals("upperchest"))
                    && (spine < 0 || depth(j) < depth(spine))) spine = j;
        }
        return spine;
    }

    private int findUpperArm(String side) {
        int j = find("upperarm,arm,uparm,humerus", side);
        return j;
    }

    private int findUpperLeg(String side) {
        int j = find("upperleg,upleg,thigh,femur", side);

        return j >= 0 ? j : find("leg", side);
    }

    @Override
    public void pose(AutoRig.Angles a, float[][] outPositions, float[][] outNormals) {
        int n = skeleton.size();
        float spread = a.armSpread;
        for (int j = 0; j < n; j++) {
            int p = skeleton.parent[j];
            float[] parentMatrix = p >= 0 ? posed[p] : mul4(toBiped, skeleton.anchor[j]);
            mul4(parentMatrix, skeleton.local[j], posed[j]);
            Role role = roles[j];
            if (role != null) {
                float[] turn = switch (role) {
                    case BODY -> mul3(rotY(a.bodyY), rotX(a.bodyX));
                    case HEAD -> mul3(rotY(a.headY), rotX(a.headX));
                    case RIGHT_ARM -> limb(a.rightArmX, a.rightArmY, a.rightArmZ);
                    case LEFT_ARM -> limb(a.leftArmX, a.leftArmY, a.leftArmZ);
                    case RIGHT_LEG -> limb(a.rightLegX, a.rightLegY, a.rightLegZ);
                    case LEFT_LEG -> limb(a.leftLegX, a.leftLegY, a.leftLegZ);
                };
                float[] rest = rotation3(restBiped[j]);
                float[] target;
                if (role == Role.BODY || role == Role.HEAD) {
                    target = mul3(turn, rest);
                } else {
                    float s = role == Role.RIGHT_ARM ? -spread : role == Role.LEFT_ARM ? spread : 0f;
                    float[] down = {(float) Math.sin(s), (float) Math.cos(s), 0f};
                    target = mul3(turn, mul3(arc(restDirection[j], down), rest));
                }
                for (int c = 0; c < 3; c++) {
                    for (int r = 0; r < 3; r++) posed[j][c * 4 + r] = target[c * 3 + r];
                }
            }
            deform[j] = mul4(posed[j], restInverse[j]);
        }

        for (int g = 0; g < model.groupCount(); g++) {
            float[] pos = model.groupPositions(g), nrm = model.groupNormals(g);
            int[] joints = model.groupJoints(g);
            float[] weights = model.groupWeights(g);
            float[] outP = outPositions[g], outN = outNormals[g];
            int count = pos.length / 3;
            for (int v = 0; v < count; v++) {
                float m0 = 0, m1 = 0, m2 = 0, m4 = 0, m5 = 0, m6 = 0, m8 = 0, m9 = 0, m10 = 0, m12 = 0, m13 = 0, m14 = 0;
                for (int k = 0; k < 4; k++) {
                    float w = weights[v * 4 + k];
                    if (w == 0f) continue;
                    int j = joints[v * 4 + k];
                    float[] d = j >= 0 ? deform[j] : toBiped;
                    m0 += w * d[0]; m1 += w * d[1]; m2 += w * d[2];
                    m4 += w * d[4]; m5 += w * d[5]; m6 += w * d[6];
                    m8 += w * d[8]; m9 += w * d[9]; m10 += w * d[10];
                    m12 += w * d[12]; m13 += w * d[13]; m14 += w * d[14];
                }
                int i = v * 3;
                float x = pos[i], y = pos[i + 1], z = pos[i + 2];
                outP[i] = m0 * x + m4 * y + m8 * z + m12;
                outP[i + 1] = m1 * x + m5 * y + m9 * z + m13;
                outP[i + 2] = m2 * x + m6 * y + m10 * z + m14;
                float nx = nrm[i], ny = nrm[i + 1], nz = nrm[i + 2];
                float ox = m0 * nx + m4 * ny + m8 * nz, oy = m1 * nx + m5 * ny + m9 * nz, oz = m2 * nx + m6 * ny + m10 * nz;
                float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (len > 1e-8f) {
                    ox /= len;
                    oy /= len;
                    oz /= len;
                }
                outN[i] = ox;
                outN[i + 1] = oy;
                outN[i + 2] = oz;
            }
        }
    }

    private static float[] limb(float x, float y, float z) {
        return mul3(rotZ(z), mul3(rotY(y), rotX(x)));
    }

    private static float[] rotX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{1, 0, 0, 0, c, s, 0, -s, c};
    }

    private static float[] rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, 0, -s, 0, 1, 0, s, 0, c};
    }

    private static float[] rotZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, s, 0, -s, c, 0, 0, 0, 1};
    }

    private static float[] arc(float[] from, float[] to) {
        float[] axis = {from[1] * to[2] - from[2] * to[1], from[2] * to[0] - from[0] * to[2], from[0] * to[1] - from[1] * to[0]};
        float cos = from[0] * to[0] + from[1] * to[1] + from[2] * to[2];
        float sin = length(axis);
        if (sin < 1e-6f) {
            if (cos > 0) return new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};

            axis = Math.abs(from[0]) < 0.9f ? new float[]{0, -from[2], from[1]} : new float[]{from[2], 0, -from[0]};
            sin = 0;
            cos = -1;
        }
        axis = normalize(axis);
        float x = axis[0], y = axis[1], z = axis[2], t = 1 - cos;
        return new float[]{
                t * x * x + cos, t * x * y + sin * z, t * x * z - sin * y,
                t * x * y - sin * z, t * y * y + cos, t * y * z + sin * x,
                t * x * z + sin * y, t * y * z - sin * x, t * z * z + cos};
    }

    private static float[] mul3(float[] a, float[] b) {
        float[] out = new float[9];
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                out[c * 3 + r] = a[r] * b[c * 3] + a[3 + r] * b[c * 3 + 1] + a[6 + r] * b[c * 3 + 2];
            }
        }
        return out;
    }

    private static float[] rotation3(float[] m) {
        return new float[]{m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]};
    }

    private static float[] rotation4(float[] r) {
        return new float[]{r[0], r[1], r[2], 0, r[3], r[4], r[5], 0, r[6], r[7], r[8], 0, 0, 0, 0, 1};
    }

    private static float[] apply3(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[3] * y + m[6] * z, m[1] * x + m[4] * y + m[7] * z, m[2] * x + m[5] * y + m[8] * z};
    }

    private static float[] mul4(float[] a, float[] b) {
        return mul4(a, b, new float[16]);
    }

    private static float[] mul4(float[] a, float[] b, float[] out) {
        float[] tmp = out == a || out == b ? new float[16] : out;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                tmp[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
            }
        }
        if (tmp != out) System.arraycopy(tmp, 0, out, 0, 16);
        return out;
    }

    private static float[] invertAffine(float[] m) {
        float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
        float A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        float det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12f) det = 1e-12f;
        float inv = 1f / det;
        float[] r = new float[16];
        r[0] = A * inv;
        r[4] = -(b * i - c * h) * inv;
        r[8] = (b * f - c * e) * inv;
        r[1] = B * inv;
        r[5] = (a * i - c * g) * inv;
        r[9] = -(a * f - c * d) * inv;
        r[2] = C * inv;
        r[6] = -(a * h - b * g) * inv;
        r[10] = (a * e - b * d) * inv;
        float tx = m[12], ty = m[13], tz = m[14];
        r[12] = -(r[0] * tx + r[4] * ty + r[8] * tz);
        r[13] = -(r[1] * tx + r[5] * ty + r[9] * tz);
        r[14] = -(r[2] * tx + r[6] * ty + r[10] * tz);
        r[15] = 1;
        return r;
    }

    private static float[] position(float[] m) {
        return new float[]{m[12], m[13], m[14]};
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] normalize(float[] v) {
        float l = length(v);
        return l < 1e-8f ? new float[]{0, 1, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
