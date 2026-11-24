package rip.tempest.jenny.render;

import rip.tempest.jenny.anim.AnimPlayer;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.geo.GeoBone;
import rip.tempest.jenny.geo.GeoModel;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BoneMath {
    private static final float[] EYE_FROM_HEAD = {0f, 2.25f, -3.5f};

    private BoneMath() {
    }

    public static double[] playerEyeOffset(NpcEntity npc) {
        GeoModel model = npc.getModel();
        if (model == null) return null;
        GeoBone cam = model.bones.get("boyCam");
        if (cam != null) return offset(npc, model, cam, cam.pivot);
        GeoBone head = model.bones.get("Head2");
        if (head == null) return null;
        float[] p = {head.pivot[0] + EYE_FROM_HEAD[0], head.pivot[1] + EYE_FROM_HEAD[1], head.pivot[2] + EYE_FROM_HEAD[2]};
        return offset(npc, model, head, p);
    }

    static double[] offset(NpcEntity npc, GeoModel model, GeoBone bone, float[] geoPoint) {
        Deque<GeoBone> path = new ArrayDeque<>();
        for (GeoBone b = bone; b != null; b = b.parentName == null ? null : model.bones.get(b.parentName)) {
            path.addFirst(b);
        }

        float[] m = identity();
        m = mul(m, rotate(180f - npc.rotationYaw, 0f, 1f, 0f));
        m = mul(m, scale(1f / 16f, 1f / 16f, 1f / 16f));

        AnimPlayer anim = npc.getAnimPlayer();
        for (GeoBone b : path) {
            float[] pos = anim.getBonePosition(b.name);
            if (pos != null) m = mul(m, translate(-pos[0], pos[1], pos[2]));

            float px = -b.pivot[0], py = b.pivot[1], pz = b.pivot[2];
            m = mul(m, translate(px, py, pz));
            float[] rot = npc.boneRotation(b);
            float x = rot[0], y = rot[1], z = rot[2];
            if (z != 0f) m = mul(m, rotate(z, 0f, 0f, 1f));
            if (y != 0f) m = mul(m, rotate(-y, 0f, 1f, 0f));
            if (x != 0f) m = mul(m, rotate(-x, 1f, 0f, 0f));
            float[] sc = anim.getBoneScale(b.name);
            if (sc != null) m = mul(m, scale(sc[0], sc[1], sc[2]));
            m = mul(m, translate(-px, -py, -pz));
        }

        float vx = -geoPoint[0], vy = geoPoint[1], vz = geoPoint[2];
        return new double[]{
                m[0] * vx + m[4] * vy + m[8] * vz + m[12],
                m[1] * vx + m[5] * vy + m[9] * vz + m[13],
                m[2] * vx + m[6] * vy + m[10] * vz + m[14]
        };
    }

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    private static float[] scale(float x, float y, float z) {
        float[] m = identity();
        m[0] = x;
        m[5] = y;
        m[10] = z;
        return m;
    }

    private static float[] rotate(float degrees, float x, float y, float z) {
        double r = Math.toRadians(degrees);
        float c = (float) Math.cos(r), s = (float) Math.sin(r), t = 1f - c;
        return new float[]{
                x * x * t + c, y * x * t + z * s, x * z * t - y * s, 0,
                x * y * t - z * s, y * y * t + c, y * z * t + x * s, 0,
                x * z * t + y * s, y * z * t - x * s, z * z * t + c, 0,
                0, 0, 0, 1
        };
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[col * 4 + k];
                o[col * 4 + row] = sum;
            }
        }
        return o;
    }
}
