package rip.tempest.modules.impl.visuals.cosmetics;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

final class Shapes {
    private static final double PX = 1.0 / 16.0;

    private Shapes() {
    }

    static void begin() {
        Tessellator.getInstance().getWorldRenderer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_NORMAL);
    }

    static void end() {
        Tessellator.getInstance().draw();
    }

    static void box(double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] c = {(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2};
        double[] a = {x0, y0, z0}, b = {x1, y0, z0}, d = {x1, y1, z0}, e = {x0, y1, z0};
        double[] f = {x0, y0, z1}, g = {x1, y0, z1}, h = {x1, y1, z1}, i = {x0, y1, z1};
        quad(a, b, d, e, c);
        quad(f, g, h, i, c);
        quad(a, b, g, f, c);
        quad(e, d, h, i, c);
        quad(a, e, i, f, c);
        quad(b, d, h, g, c);
    }

    static void prism(double[] p0, double[] p1, double[] p2, double z0, double z1) {
        double[] c = {(p0[0] + p1[0] + p2[0]) / 3, (p0[1] + p1[1] + p2[1]) / 3, (z0 + z1) / 2};
        double[] a0 = {p0[0], p0[1], z0}, b0 = {p1[0], p1[1], z0}, c0 = {p2[0], p2[1], z0};
        double[] a1 = {p0[0], p0[1], z1}, b1 = {p1[0], p1[1], z1}, c1 = {p2[0], p2[1], z1};
        tri(a0, b0, c0, c);
        tri(a1, b1, c1, c);
        quad(a0, b0, b1, a1, c);
        quad(b0, c0, c1, b1, c);
        quad(c0, a0, a1, c1, c);
    }

    static void cylinder(double cx, double yTop, double cz, double radius, double height, int segments) {
        double yBottom = yTop + height;
        double[] c = {cx, yTop + height / 2, cz};
        double[] top = {cx, yTop, cz}, bottom = {cx, yBottom, cz};
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            double[] t0 = ring(cx, yTop, cz, radius, a0), t1 = ring(cx, yTop, cz, radius, a1);
            double[] b0 = ring(cx, yBottom, cz, radius, a0), b1 = ring(cx, yBottom, cz, radius, a1);
            quad(t0, t1, b1, b0, c);
            tri(top, t0, t1, c);
            tri(bottom, b0, b1, c);
        }
    }

    static void cone(double cx, double yBase, double cz, double radius, double height, double tipX, double tipZ, int segments) {
        double[] apex = {cx + tipX, yBase - height, cz + tipZ};
        double[] base = {cx, yBase, cz};
        double[] c = {cx + tipX / 3, yBase - height / 3, cz + tipZ / 3};
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            double[] r0 = ring(cx, yBase, cz, radius, a0), r1 = ring(cx, yBase, cz, radius, a1);
            tri(r0, r1, apex, c);
            tri(base, r1, r0, c);
        }
    }

    static void tube(double cx, double yTop, double cz, double innerRadius, double outerRadius, double height, int segments) {
        double yBottom = yTop + height;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            double[] ot0 = ring(cx, yTop, cz, outerRadius, a0), ot1 = ring(cx, yTop, cz, outerRadius, a1);
            double[] ob0 = ring(cx, yBottom, cz, outerRadius, a0), ob1 = ring(cx, yBottom, cz, outerRadius, a1);
            double[] it0 = ring(cx, yTop, cz, innerRadius, a0), it1 = ring(cx, yTop, cz, innerRadius, a1);
            double[] ib0 = ring(cx, yBottom, cz, innerRadius, a0), ib1 = ring(cx, yBottom, cz, innerRadius, a1);
            double mid = (a0 + a1) / 2;
            double nx = Math.cos(mid), nz = Math.sin(mid);
            quadNormal(ot0, ot1, ob1, ob0, nx, 0, nz);
            quadNormal(it0, it1, ib1, ib0, -nx, 0, -nz);
            quadNormal(it0, it1, ot1, ot0, 0, -1, 0);
            quadNormal(ib0, ib1, ob1, ob0, 0, 1, 0);
        }
    }

    static void beam(double[] start, double[] end, double r0, double r1) {
        double[] u = sub(end, start);
        double length = len(u);
        if (length < 1.0E-6) return;
        u = scale(u, 1 / length);
        double[] up = Math.abs(u[1]) > 0.9 ? new double[]{1, 0, 0} : new double[]{0, -1, 0};
        double[] v = norm(cross(u, up));
        double[] w = norm(cross(u, v));

        double[][] s = corners(start, v, w, r0), e = corners(end, v, w, r1);
        double[] c = scale(add(start, end), 0.5);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quad(s[i], s[j], e[j], e[i], c);
        }
        quad(s[0], s[1], s[2], s[3], c);
        quad(e[0], e[1], e[2], e[3], c);
    }

    static void tube(double[][] points, double[] radii, int sides) {
        int n = points.length;
        if (n < 2) return;

        double[][][] rings = new double[n][sides][];
        double[][][] normals = new double[n][sides][];
        double[] normal = null, tangent = null;
        for (int i = 0; i < n; i++) {
            double[] along = sub(points[Math.min(i + 1, n - 1)], points[Math.max(i - 1, 0)]);
            if (len(along) > 1.0E-9) tangent = norm(along);
            else if (tangent == null) tangent = new double[]{0, 0, 1};

            if (normal == null) {
                double[] helper = Math.abs(tangent[1]) > 0.9 ? new double[]{1, 0, 0} : new double[]{0, -1, 0};
                normal = norm(cross(tangent, helper));
            } else {
                double[] carried = sub(normal, scale(tangent, dot(normal, tangent)));
                if (len(carried) > 1.0E-9) normal = norm(carried);
            }
            double[] binormal = cross(tangent, normal);

            for (int k = 0; k < sides; k++) {
                double angle = Math.PI * 2 * k / sides;
                double[] out = add(scale(normal, Math.cos(angle)), scale(binormal, Math.sin(angle)));
                normals[i][k] = out;
                rings[i][k] = add(points[i], scale(out, radii[i]));
            }
        }

        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < sides; k++) {
                int next = (k + 1) % sides;
                quadNormals(rings[i][k], rings[i][next], rings[i + 1][next], rings[i + 1][k],
                        normals[i][k], normals[i][next], normals[i + 1][next], normals[i + 1][k]);
            }
        }

        double[] tip = add(points[n - 1], scale(tangent, radii[n - 1] * 1.4));
        for (int k = 0; k < sides; k++) {
            int next = (k + 1) % sides;
            quadNormals(rings[n - 1][k], rings[n - 1][next], tip, tip,
                    normals[n - 1][k], normals[n - 1][next], tangent, tangent);
        }
    }

    static void quadNormals(double[] a, double[] b, double[] c, double[] d,
                            double[] na, double[] nb, double[] nc, double[] nd) {
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        double[][] corners = {a, b, c, d};
        double[][] cornerNormals = {na, nb, nc, nd};
        for (int i = 0; i < 4; i++) {
            double[] p = corners[i], q = cornerNormals[i];
            buffer.pos(p[0] * PX, p[1] * PX, p[2] * PX).normal((float) q[0], (float) q[1], (float) q[2]).endVertex();
        }
    }

    static void octa(double x, double y, double z, double r) {
        double[] c = {x, y, z};
        double[] px = {x + r, y, z}, nx = {x - r, y, z}, py = {x, y + r, z}, ny = {x, y - r, z}, pz = {x, y, z + r}, nz = {x, y, z - r};
        double[][] ring = {px, pz, nx, nz};
        for (int i = 0; i < 4; i++) {
            double[] a = ring[i], b = ring[(i + 1) % 4];
            tri(a, b, ny, c);
            tri(a, b, py, c);
        }
    }

    static void tri(double[] a, double[] b, double[] c, double[] centre) {
        quad(a, b, c, c, centre);
    }

    static void quad(double[] a, double[] b, double[] c, double[] d, double[] centre) {
        double[] n = cross(sub(b, a), sub(c, a));
        if (len(n) < 1.0E-9) n = cross(sub(c, a), sub(d, a));
        n = norm(n);
        double[] face = {(a[0] + b[0] + c[0] + d[0]) / 4, (a[1] + b[1] + c[1] + d[1]) / 4, (a[2] + b[2] + c[2] + d[2]) / 4};
        if (dot(n, sub(face, centre)) < 0) n = scale(n, -1);
        quadNormal(a, b, c, d, n[0], n[1], n[2]);
    }

    static void quadNormal(double[] a, double[] b, double[] c, double[] d, double nx, double ny, double nz) {
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        float fx = (float) nx, fy = (float) ny, fz = (float) nz;
        for (double[] p : new double[][]{a, b, c, d}) {
            buffer.pos(p[0] * PX, p[1] * PX, p[2] * PX).normal(fx, fy, fz).endVertex();
        }
    }

    private static double[] ring(double cx, double y, double cz, double radius, double angle) {
        return new double[]{cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius};
    }

    private static double[][] corners(double[] p, double[] v, double[] w, double r) {
        return new double[][]{
                add(p, add(scale(v, r), scale(w, r))),
                add(p, add(scale(v, -r), scale(w, r))),
                add(p, add(scale(v, -r), scale(w, -r))),
                add(p, add(scale(v, r), scale(w, -r)))
        };
    }

    static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] scale(double[] a, double s) {
        return new double[]{a[0] * s, a[1] * s, a[2] * s};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double len(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] norm(double[] a) {
        double l = len(a);
        return l < 1.0E-9 ? new double[]{0, 1, 0} : scale(a, 1 / l);
    }
}
