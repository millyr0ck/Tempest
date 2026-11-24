package rip.tempest.utils.render.models;

import java.util.Arrays;

public final class AutoRig implements Poser {
    public static final int BODY = 0, HEAD = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    private static final int BINS = 120, MAX_SAMPLES = 200_000;

    final float[][] positions, normals;
    final byte[][] bones;
    final float[][] weights;

    public final boolean legs, arms, head;

    final float[] bodyPivot, neck, rightShoulder, leftShoulder, rightHip, leftHip;

    final float rightArmDown, leftArmDown;

    final float height;

    private AutoRig(float[][] positions, float[][] normals, byte[][] bones, float[][] weights, boolean legs, boolean arms, boolean head,
                    float[] bodyPivot, float[] neck, float[] rightShoulder, float[] leftShoulder, float[] rightHip, float[] leftHip,
                    float rightArmDown, float leftArmDown, float height) {
        this.positions = positions;
        this.normals = normals;
        this.bones = bones;
        this.weights = weights;
        this.legs = legs;
        this.arms = arms;
        this.head = head;
        this.bodyPivot = bodyPivot;
        this.neck = neck;
        this.rightShoulder = rightShoulder;
        this.leftShoulder = leftShoulder;
        this.rightHip = rightHip;
        this.leftHip = leftHip;
        this.rightArmDown = rightArmDown;
        this.leftArmDown = leftArmDown;
        this.height = height;
    }

    public String describe() {
        StringBuilder found = new StringBuilder();
        if (arms) found.append("arms");
        if (legs) found.append(found.length() > 0 ? ", " : "").append("legs");
        if (head) found.append(found.length() > 0 ? ", " : "").append("head");
        return found.length() == 0 ? "no limbs" : found.toString();
    }

    public boolean animatesLimbs() {
        return legs || arms;
    }

    static float[] standUp(float yawDegrees, float tiltDegrees) {
        float[] yaw = Pose.rotation(Pose.Y, (float) Math.toRadians(yawDegrees));
        float[] tilt = Pose.rotation(Pose.X, (float) Math.toRadians(tiltDegrees));
        float[] flip = {-1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0};
        return Pose.multiply(Pose.multiply(yaw, tilt), flip);
    }

    public static AutoRig build(GltfModel model, float yawDegrees, float tiltDegrees) {
        float[] turn = standUp(yawDegrees, tiltDegrees);
        int groups = model.groupCount();
        float[][] positions = new float[groups][], normals = new float[groups][];
        int total = 0;
        for (int g = 0; g < groups; g++) {
            float[] src = model.groupPositions(g), srcNormals = model.groupNormals(g);
            float[] dst = new float[src.length], dstNormals = new float[srcNormals.length];
            for (int i = 0; i < src.length; i += 3) {
                Pose.apply(turn, src, i, dst, i);
                Pose.applyRotation(turn, srcNormals, i, dstNormals, i);
            }
            positions[g] = dst;
            normals[g] = dstNormals;
            total += src.length / 3;
        }
        if (total == 0) return null;

        int step = Math.max(1, total / MAX_SAMPLES);
        int count = (total + step - 1) / step;
        float[] xs = new float[count], ys = new float[count], zs = new float[count];
        int n = 0, k = 0;
        for (float[] group : positions) {
            for (int i = 0; i < group.length; i += 3, k++) {
                if (k % step != 0 || n >= count) continue;
                xs[n] = group[i];
                ys[n] = group[i + 1];
                zs[n] = group[i + 2];
                n++;
            }
        }
        xs = Arrays.copyOf(xs, n);
        ys = Arrays.copyOf(ys, n);
        zs = Arrays.copyOf(zs, n);

        float feet = percentile(ys, 0.995f), top = percentile(ys, 0.005f);
        float height = feet - top;
        if (height <= 1e-6f) return null;
        float cx = percentile(xs, 0.5f), cz = percentile(zs, 0.5f);
        float binHeight = height / BINS;

        float[] minAbs = new float[BINS], maxAbs = new float[BINS];
        int[] binCount = new int[BINS];
        Arrays.fill(minAbs, Float.MAX_VALUE);
        float span = 0;
        for (int i = 0; i < n; i++) span = Math.max(span, Math.abs(xs[i] - cx));

        float cell = height / 100f;
        int cells = (int) Math.ceil(span / cell) * 2 + 3, middle = cells / 2;
        boolean[][] occupied = new boolean[BINS][cells];
        for (int i = 0; i < n; i++) {
            int bin = bin(feet - ys[i], binHeight);
            if (bin < 0) continue;
            float dx = xs[i] - cx, abs = Math.abs(dx);
            binCount[bin]++;
            minAbs[bin] = Math.min(minAbs[bin], abs);
            maxAbs[bin] = Math.max(maxAbs[bin], abs);
            int c = middle + Math.round(dx / cell);
            if (c >= 0 && c < cells) occupied[bin][c] = true;
        }
        float[] trunk = new float[BINS];
        for (int b = 0; b < BINS; b++) trunk[b] = trunkHalfWidth(occupied[b], middle) * cell;

        int crotchBin = -1;
        float gap = 0.012f * height;
        int runStart = -1;
        for (int b = 0; b < BINS; b++) {
            boolean open = binCount[b] > 0 && minAbs[b] > gap;
            boolean empty = binCount[b] == 0;
            if (open || (empty && runStart >= 0)) {
                if (runStart < 0) runStart = b;
            } else {
                if (runStart >= 0 && runStart <= BINS * 0.3f && b - runStart >= BINS * 0.08f) {
                    crotchBin = b;
                    break;
                }
                runStart = -1;
            }
        }
        float crotch = crotchBin * binHeight;
        boolean legs = crotchBin > 0 && crotch > 0.12f * height && crotch < 0.65f * height;
        if (!legs) crotchBin = (int) (BINS * 0.45f);

        float waist = medianOver(trunk, binCount, crotchBin + (int) (BINS * 0.04f), crotchBin + (int) (BINS * 0.2f));
        if (waist <= 0) waist = 0.08f * height;

        float reach = Math.max(2.0f * waist, 0.26f * height);
        int widest = -1;
        for (int b = crotchBin + 1; b < BINS; b++) {
            if (binCount[b] > 0 && maxAbs[b] > reach && (widest < 0 || maxAbs[b] > maxAbs[widest])) widest = b;
        }
        boolean arms = widest >= 0;
        int armLow = widest, armHigh = widest;
        if (arms) {
            while (armLow - 1 > crotchBin && maxAbs[armLow - 1] > reach) armLow--;
            while (armHigh + 1 < BINS && maxAbs[armHigh + 1] > reach) armHigh++;
        }
        float bandLow = (armLow - 1) * binHeight, bandHigh = (armHigh + 1) * binHeight;
        float chest = arms ? medianOver(trunk, binCount, armLow - (int) (BINS * 0.06f), armLow - 1) : waist;
        float shoulderX = Math.max(Math.max(chest, waist) * 1.1f, 0.05f * height);

        float shoulderHeight = (bandLow + bandHigh) / 2f;
        float rightDown = 0, leftDown = 0;
        float armZ = cz;

        float[] rightAxis = null, leftAxis = null;
        float rightRadius = 0, leftRadius = 0;
        if (arms) {
            double sum = 0, sumZ = 0;
            int at = 0;
            float far = 0;
            for (int i = 0; i < n; i++) {
                float h = feet - ys[i];
                if (h >= bandLow && h <= bandHigh) far = Math.max(far, Math.abs(xs[i] - cx));
            }
            for (int i = 0; i < n; i++) {
                float h = feet - ys[i], abs = Math.abs(xs[i] - cx);
                if (h >= bandLow && h <= bandHigh && abs >= shoulderX && abs <= shoulderX + 0.06f * height) {
                    sum += h;
                    sumZ += zs[i];
                    at++;
                }
            }
            if (at > 0) {
                shoulderHeight = (float) (sum / at);
                armZ = (float) (sumZ / at);
            }
            rightAxis = armAxis(xs, ys, zs, n, cx, feet, bandLow, bandHigh, far, shoulderX, shoulderHeight, armZ, true);
            leftAxis = armAxis(xs, ys, zs, n, cx, feet, bandLow, bandHigh, far, shoulderX, shoulderHeight, armZ, false);
            if (rightAxis != null && leftAxis != null) {
                rightRadius = armRadius(xs, ys, zs, n, cx, feet, bandLow, bandHigh, shoulderX, shoulderHeight, armZ, rightAxis, true, height);
                leftRadius = armRadius(xs, ys, zs, n, cx, feet, bandLow, bandHigh, shoulderX, shoulderHeight, armZ, leftAxis, false, height);
                float rightLength = length(rightAxis), leftLength = length(leftAxis);

                arms = rightLength > 0.12f * height && leftLength > 0.12f * height
                        && Math.abs(rightLength - leftLength) < 0.35f * Math.max(rightLength, leftLength)
                        && rightRadius < 0.45f * rightLength && leftRadius < 0.45f * leftLength;
            } else {
                arms = false;
            }
            if (arms) {
                float rightBelow = (float) Math.atan2(-rightAxis[1], rightAxis[0]);
                float leftBelow = (float) Math.atan2(-leftAxis[1], leftAxis[0]);

                rightDown = -((float) Math.PI / 2f - rightBelow);
                leftDown = (float) Math.PI / 2f - leftBelow;
            }
        }

        int searchFrom = arms ? armHigh + 1 : (int) (BINS * 0.7f);
        int neckBin = -1;

        for (int b = searchFrom; b < Math.min(BINS, searchFrom + (int) (BINS * 0.1f)); b++) {
            if (binCount[b] > 0 && (neckBin < 0 || trunk[b] < trunk[neckBin])) neckBin = b;
        }
        float neckHeight = neckBin >= 0 ? neckBin * binHeight : shoulderHeight;
        boolean head = neckBin >= 0 && height - neckHeight > 0.08f * height;
        float neckZ = cz;
        if (head) {
            double sumZ = 0;
            int at = 0;
            for (int i = 0; i < n; i++) {
                float h = feet - ys[i];
                if (Math.abs(h - neckHeight) <= binHeight * 1.5f) {
                    sumZ += zs[i];
                    at++;
                }
            }
            if (at > 0) neckZ = (float) (sumZ / at);
        }

        float rightHipX = cx - waist * 0.5f, leftHipX = cx + waist * 0.5f, rightHipZ = cz, leftHipZ = cz;
        if (legs) {
            double rx = 0, rz = 0, lx = 0, lz = 0;
            int rn = 0, ln = 0;
            for (int i = 0; i < n; i++) {
                float h = feet - ys[i];
                if (h < crotch - 0.08f * height || h > crotch) continue;
                if (xs[i] < cx) {
                    rx += xs[i];
                    rz += zs[i];
                    rn++;
                } else {
                    lx += xs[i];
                    lz += zs[i];
                    ln++;
                }
            }
            if (rn > 0) {
                rightHipX = (float) (rx / rn);
                rightHipZ = (float) (rz / rn);
            }
            if (ln > 0) {
                leftHipX = (float) (lx / ln);
                leftHipZ = (float) (lz / ln);
            }
        }
        if (legs) {
            float[] right = legSpread(xs, ys, zs, n, cx, feet, crotch, true), left = legSpread(xs, ys, zs, n, cx, feet, crotch, false);

            legs = right != null && left != null && right[0] <= 0.3f * height && left[0] <= 0.3f * height
                    && right[1] <= 0.35f * height && left[1] <= 0.35f * height
                    && Math.max(right[0], left[0]) <= 2f * Math.min(right[0], left[0])
                    && Math.max(right[1], left[1]) <= 2f * Math.min(right[1], left[1])
                    && Math.max(right[2], left[2]) <= 4f * Math.min(right[2], left[2]);
        }
        if (!legs && !arms) return null;

        float hipY = feet - (crotch + 0.03f * height);
        float[] rightHip = {rightHipX, hipY, rightHipZ}, leftHip = {leftHipX, hipY, leftHipZ};
        float[] rightShoulder = {cx - shoulderX, feet - shoulderHeight, armZ}, leftShoulder = {cx + shoulderX, feet - shoulderHeight, armZ};
        float[] neck = {cx, feet - neckHeight, neckZ};
        float[] bodyPivot = {cx, feet - (arms ? shoulderHeight : neckHeight), cz};

        float armBlend = 0.03f * height, legBlend = 0.035f * height, headBlend = 0.025f * height;
        byte[][] bones = new byte[groups][];
        float[][] weights = new float[groups][];
        for (int g = 0; g < groups; g++) {
            float[] group = positions[g];
            byte[] bone = new byte[group.length / 3];
            float[] weight = new float[group.length / 3];
            for (int v = 0; v < bone.length; v++) {
                float x = group[v * 3], h = feet - group[v * 3 + 1];
                float dx = x - cx, abs = Math.abs(dx);
                float along = Float.NaN;
                if (arms) {
                    float[] axis = dx < 0 ? rightAxis : leftAxis;
                    float radius = dx < 0 ? rightRadius : leftRadius;
                    along = tube(abs - shoulderX, h - shoulderHeight, group[v * 3 + 2] - armZ, axis, radius, armBlend);
                }
                if (!Float.isNaN(along)) {
                    bone[v] = (byte) (dx < 0 ? RIGHT_ARM : LEFT_ARM);
                    weight[v] = smooth((along + armBlend) / (2f * armBlend));
                } else if (legs && h < crotch + legBlend) {
                    bone[v] = (byte) (dx < 0 ? RIGHT_LEG : LEFT_LEG);
                    weight[v] = smooth((crotch + legBlend - h) / (2f * legBlend));
                } else if (head && h > neckHeight - headBlend) {
                    bone[v] = HEAD;
                    weight[v] = smooth((h - (neckHeight - headBlend)) / (2f * headBlend));
                }
            }

            if (head) {
                int[] parts = model.groupParts(g);
                for (int p = 0; p + 1 < parts.length; p++) {
                    int from = parts[p], to = parts[p + 1], heads = 0;
                    for (int v = from; v < to; v++) if (bone[v] == HEAD && weight[v] > 0.5f) heads++;
                    if (to > from && heads > 0.6f * (to - from)) {
                        for (int v = from; v < to; v++) {
                            bone[v] = HEAD;
                            weight[v] = 1f;
                        }
                    }
                }
            }
            bones[g] = bone;
            weights[g] = weight;
        }
        return new AutoRig(positions, normals, bones, weights, legs, arms, head, bodyPivot, neck, rightShoulder, leftShoulder,
                rightHip, leftHip, rightDown, leftDown, height);
    }

    private static float[] armAxis(float[] xs, float[] ys, float[] zs, int n, float cx, float feet, float bandLow, float bandHigh,
                                   float far, float shoulderX, float shoulderHeight, float armZ, boolean right) {
        double x = 0, h = 0, z = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            float dx = xs[i] - cx, height = feet - ys[i];
            if ((dx < 0) != right || height < bandLow || height > bandHigh || Math.abs(dx) < far * 0.85f) continue;
            x += Math.abs(dx);
            h += height;
            z += zs[i];
            count++;
        }
        if (count < 3) return null;
        return new float[]{(float) (x / count) - shoulderX, (float) (h / count) - shoulderHeight, (float) (z / count) - armZ};
    }

    private static float armRadius(float[] xs, float[] ys, float[] zs, int n, float cx, float feet, float bandLow, float bandHigh,
                                   float shoulderX, float shoulderHeight, float armZ, float[] axis, boolean right, float height) {
        float[] distances = new float[n];
        int m = 0;
        float lengthSq = axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2];
        for (int i = 0; i < n; i++) {
            float dx = xs[i] - cx, h = feet - ys[i];
            if ((dx < 0) != right || h < bandLow || h > bandHigh) continue;
            float qx = Math.abs(dx) - shoulderX, qy = h - shoulderHeight, qz = zs[i] - armZ;
            float t = (qx * axis[0] + qy * axis[1] + qz * axis[2]) / lengthSq;
            if (t < 0.3f || t > 0.7f) continue;
            float px = qx - t * axis[0], py = qy - t * axis[1], pz = qz - t * axis[2];
            distances[m++] = (float) Math.sqrt(px * px + py * py + pz * pz);
        }
        if (m < 10) return Float.MAX_VALUE;
        distances = Arrays.copyOf(distances, m);
        Arrays.sort(distances);
        return distances[(int) (m * 0.9f)] * 1.25f + 0.01f * height;
    }

    private static float tube(float qx, float qy, float qz, float[] axis, float radius, float blend) {
        float length = length(axis);
        float lengthSq = length * length;
        float t = (qx * axis[0] + qy * axis[1] + qz * axis[2]) / lengthSq;
        if (t < -blend / length || t > 1.25f) return Float.NaN;
        float px = qx - t * axis[0], py = qy - t * axis[1], pz = qz - t * axis[2];
        float distance = (float) Math.sqrt(px * px + py * py + pz * pz);
        return distance <= radius * (t > 0.75f ? 1.6f : 1.15f) ? t * length : Float.NaN;
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] legSpread(float[] xs, float[] ys, float[] zs, int n, float cx, float feet, float crotch, boolean right) {
        float[] x = new float[n], z = new float[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            float h = feet - ys[i];
            if ((xs[i] < cx) != right || h < 0.25f * crotch || h > 0.75f * crotch) continue;
            x[m] = xs[i];
            z[m] = zs[i];
            m++;
        }
        if (m < 12) return null;
        x = Arrays.copyOf(x, m);
        z = Arrays.copyOf(z, m);
        Arrays.sort(x);
        Arrays.sort(z);
        int lo = (int) (m * 0.05f), hi = (int) (m * 0.95f);
        return new float[]{x[hi] - x[lo], z[hi] - z[lo], m};
    }

    private static float trunkHalfWidth(boolean[] row, int middle) {
        int left = 0, right = 0, misses = 0;
        for (int c = middle; c >= 0; c--) {
            if (row[c]) {
                left = middle - c;
                misses = 0;
            } else if (++misses >= 3 && left > 0) {
                break;
            } else if (misses >= 6) {
                break;
            }
        }
        misses = 0;
        for (int c = middle; c < row.length; c++) {
            if (row[c]) {
                right = c - middle;
                misses = 0;
            } else if (++misses >= 3 && right > 0) {
                break;
            } else if (misses >= 6) {
                break;
            }
        }
        return Math.max(left, right);
    }

    private static int bin(float h, float binHeight) {
        int b = (int) (h / binHeight);
        return b < 0 || b >= BINS ? -1 : b;
    }

    private static float medianOver(float[] values, int[] counts, int from, int to) {
        from = Math.max(0, from);
        to = Math.min(values.length - 1, to);
        float[] picked = new float[Math.max(0, to - from + 1)];
        int m = 0;
        for (int b = from; b <= to; b++) if (counts[b] > 0 && values[b] > 0) picked[m++] = values[b];
        if (m == 0) return 0;
        picked = Arrays.copyOf(picked, m);
        Arrays.sort(picked);
        return picked[m / 2];
    }

    private static float percentile(float[] values, float at) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[Math.max(0, Math.min(sorted.length - 1, (int) (at * (sorted.length - 1))))];
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public static final class Angles {
        public float bodyX, bodyY, headX, headY;
        public float rightArmX, rightArmY, rightArmZ, leftArmX, leftArmY, leftArmZ;
        public float rightLegX, rightLegY, rightLegZ, leftLegX, leftLegY, leftLegZ;
        public float armSpread;
    }

    public void pose(Angles a, float[][] outPositions, float[][] outNormals) {
        float[][] bone = new float[6][];

        float[] body = Pose.about(bodyPivot, Pose.multiply(Pose.rotation(Pose.Y, a.bodyY), Pose.rotation(Pose.X, a.bodyX)));
        float[] lean = Pose.about(bodyPivot, Pose.rotation(Pose.X, a.bodyX));
        bone[BODY] = body;
        bone[HEAD] = Pose.multiply(Pose.follow(body, neck), Pose.about(neck,
                Pose.multiply(Pose.rotation(Pose.Y, a.headY), Pose.rotation(Pose.X, a.headX))));
        bone[RIGHT_ARM] = Pose.multiply(Pose.follow(body, rightShoulder), Pose.about(rightShoulder,
                limb(a.rightArmX, a.rightArmY, a.rightArmZ, rightArmDown + a.armSpread)));
        bone[LEFT_ARM] = Pose.multiply(Pose.follow(body, leftShoulder), Pose.about(leftShoulder,
                limb(a.leftArmX, a.leftArmY, a.leftArmZ, leftArmDown - a.armSpread)));

        bone[RIGHT_LEG] = Pose.multiply(Pose.follow(lean, rightHip), Pose.about(rightHip, limb(a.rightLegX, a.rightLegY, a.rightLegZ, 0)));
        bone[LEFT_LEG] = Pose.multiply(Pose.follow(lean, leftHip), Pose.about(leftHip, limb(a.leftLegX, a.leftLegY, a.leftLegZ, 0)));

        for (int g = 0; g < positions.length; g++) {
            float[] src = positions[g], srcNormals = normals[g];
            float[] dst = outPositions[g], dstNormals = outNormals[g];
            byte[] bones = this.bones[g];
            float[] weights = this.weights[g];
            for (int v = 0, i = 0; v < bones.length; v++, i += 3) {
                float w = weights[v];
                if (w <= 0f) {
                    Pose.apply(body, src, i, dst, i);
                    Pose.applyRotation(body, srcNormals, i, dstNormals, i);
                } else if (w >= 1f) {
                    float[] m = bone[bones[v]];
                    Pose.apply(m, src, i, dst, i);
                    Pose.applyRotation(m, srcNormals, i, dstNormals, i);
                } else {
                    Pose.blend(body, bone[bones[v]], w, src, srcNormals, i, dst, dstNormals);
                }
            }
        }
    }

    private static float[] limb(float x, float y, float z, float down) {
        float[] turn = Pose.multiply(Pose.multiply(Pose.rotation(Pose.Z, z), Pose.rotation(Pose.Y, y)), Pose.rotation(Pose.X, x));
        return down == 0 ? turn : Pose.multiply(turn, Pose.rotation(Pose.Z, down));
    }

    static final class Pose {
        static final int X = 0, Y = 1, Z = 2;

        static float[] rotation(int axis, float angle) {
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
            return switch (axis) {
                case X -> new float[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0};
                case Y -> new float[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0};
                default -> new float[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0};
            };
        }

        static float[] multiply(float[] a, float[] b) {
            float[] o = new float[12];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    o[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c];
                }
                o[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3];
            }
            return o;
        }

        static float[] about(float[] p, float[] turn) {
            float[] o = turn.clone();
            for (int r = 0; r < 3; r++) {
                o[r * 4 + 3] = turn[r * 4 + 3] + p[r] - (turn[r * 4] * p[0] + turn[r * 4 + 1] * p[1] + turn[r * 4 + 2] * p[2]);
            }
            return o;
        }

        static float[] follow(float[] parent, float[] joint) {
            float[] moved = new float[3];
            apply(parent, joint, 0, moved, 0);
            return new float[]{1, 0, 0, moved[0] - joint[0], 0, 1, 0, moved[1] - joint[1], 0, 0, 1, moved[2] - joint[2]};
        }

        static void apply(float[] m, float[] src, int i, float[] dst, int j) {
            float x = src[i], y = src[i + 1], z = src[i + 2];
            dst[j] = m[0] * x + m[1] * y + m[2] * z + m[3];
            dst[j + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
            dst[j + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
        }

        static void applyRotation(float[] m, float[] src, int i, float[] dst, int j) {
            float x = src[i], y = src[i + 1], z = src[i + 2];
            dst[j] = m[0] * x + m[1] * y + m[2] * z;
            dst[j + 1] = m[4] * x + m[5] * y + m[6] * z;
            dst[j + 2] = m[8] * x + m[9] * y + m[10] * z;
        }

        static void blend(float[] a, float[] b, float w, float[] src, float[] srcNormals, int i, float[] dst, float[] dstNormals) {
            float x = src[i], y = src[i + 1], z = src[i + 2], u = 1f - w;
            dst[i] = u * (a[0] * x + a[1] * y + a[2] * z + a[3]) + w * (b[0] * x + b[1] * y + b[2] * z + b[3]);
            dst[i + 1] = u * (a[4] * x + a[5] * y + a[6] * z + a[7]) + w * (b[4] * x + b[5] * y + b[6] * z + b[7]);
            dst[i + 2] = u * (a[8] * x + a[9] * y + a[10] * z + a[11]) + w * (b[8] * x + b[9] * y + b[10] * z + b[11]);
            float nx = srcNormals[i], ny = srcNormals[i + 1], nz = srcNormals[i + 2];
            float ox = u * (a[0] * nx + a[1] * ny + a[2] * nz) + w * (b[0] * nx + b[1] * ny + b[2] * nz);
            float oy = u * (a[4] * nx + a[5] * ny + a[6] * nz) + w * (b[4] * nx + b[5] * ny + b[6] * nz);
            float oz = u * (a[8] * nx + a[9] * ny + a[10] * nz) + w * (b[8] * nx + b[9] * ny + b[10] * nz);
            float length = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (length > 1e-6f) {
                ox /= length;
                oy /= length;
                oz /= length;
            }
            dstNormals[i] = ox;
            dstNormals[i + 1] = oy;
            dstNormals[i + 2] = oz;
        }
    }
}
