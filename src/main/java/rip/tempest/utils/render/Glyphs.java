package rip.tempest.utils.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public final class Glyphs {
    private static final float FEATHER = 0.5f;
    private static final int CAP_SEGMENTS = 8;

    private static boolean cullWasEnabled;

    private Glyphs() {
    }

    public static void stroke(float x1, float y1, float x2, float y2, float thickness, Color color) {
        float a = color.getAlpha() / 255f;
        if (a <= 0f || thickness <= 0f) return;

        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float half = thickness / 2f;
        if (length < 1.0E-3f) {
            disc(x1, y1, half, color);
            return;
        }

        float ux = dx / length, uy = dy / length;
        float nx = -uy, ny = ux;
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float[] offsets = {-half - FEATHER, -half, half, half + FEATHER};
        float[] alphas = {0f, a, a, 0f};

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int band = 0; band < 3; band++) {
            float near = offsets[band], far = offsets[band + 1];

            GL11.glColor4f(r, g, b, alphas[band + 1]);
            GL11.glVertex2f(x1 + nx * far, y1 + ny * far);
            GL11.glVertex2f(x2 + nx * far, y2 + ny * far);
            GL11.glColor4f(r, g, b, alphas[band]);
            GL11.glVertex2f(x2 + nx * near, y2 + ny * near);
            GL11.glVertex2f(x1 + nx * near, y1 + ny * near);
        }
        GL11.glEnd();

        double angle = Math.atan2(dy, dx);
        fan(x1, y1, half, angle + Math.PI / 2, Math.PI, r, g, b, a);
        fan(x2, y2, half, angle - Math.PI / 2, Math.PI, r, g, b, a);
        end();
    }

    public static void path(float thickness, float progress, Color color, float... points) {
        if (progress <= 0f) return;

        float total = 0f;
        for (int i = 0; i + 3 < points.length; i += 2) {
            total += distance(points[i], points[i + 1], points[i + 2], points[i + 3]);
        }

        float remaining = total * Math.min(1f, progress);
        for (int i = 0; i + 3 < points.length && remaining > 1.0E-3f; i += 2) {
            float x1 = points[i], y1 = points[i + 1], x2 = points[i + 2], y2 = points[i + 3];
            float length = distance(x1, y1, x2, y2);
            float part = length <= 0f ? 1f : Math.min(1f, remaining / length);
            stroke(x1, y1, x1 + (x2 - x1) * part, y1 + (y2 - y1) * part, thickness, color);
            remaining -= length;
        }
    }

    public static void disc(float cx, float cy, float radius, Color color) {
        float a = color.getAlpha() / 255f;
        if (a <= 0f || radius <= 0f) return;

        begin();
        fan(cx, cy, radius, 0, Math.PI * 2, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
        end();
    }

    public static void ring(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, Color color) {
        float a = color.getAlpha() / 255f;
        if (sweepDeg <= 0f || a <= 0f) return;

        int segments = Math.max(12, (int) Math.ceil(sweepDeg / 4f));
        float inner = radius - thickness / 2f, outer = radius + thickness / 2f;
        float[] radii = {inner - FEATHER, inner, outer, outer + FEATHER};
        float[] alphas = {0f, a, a, 0f};
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < segments; i++) {
            double from = Math.toRadians(startDeg + sweepDeg * i / segments);
            double to = Math.toRadians(startDeg + sweepDeg * (i + 1) / segments);
            float cosFrom = (float) Math.cos(from), sinFrom = (float) Math.sin(from);
            float cosTo = (float) Math.cos(to), sinTo = (float) Math.sin(to);

            for (int band = 0; band < 3; band++) {
                float near = radii[band], far = radii[band + 1];
                GL11.glColor4f(r, g, b, alphas[band]);
                GL11.glVertex2f(cx + cosFrom * near, cy + sinFrom * near);
                GL11.glVertex2f(cx + cosTo * near, cy + sinTo * near);
                GL11.glColor4f(r, g, b, alphas[band + 1]);
                GL11.glVertex2f(cx + cosTo * far, cy + sinTo * far);
                GL11.glVertex2f(cx + cosFrom * far, cy + sinFrom * far);
            }
        }
        GL11.glEnd();
        end();
    }

    public static void bar(float x, float y, float width, float height, Color color) {
        float a = color.getAlpha() / 255f;
        if (a <= 0f || width <= 0f || height <= 0f) return;

        begin();
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        end();
    }

    public static void gradient(float x, float y, float width, float height, Color left, Color right) {
        if (width <= 0f || height <= 0f) return;

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(left.getRed() / 255f, left.getGreen() / 255f, left.getBlue() / 255f, left.getAlpha() / 255f);
        GL11.glVertex2f(x, y + height);
        GL11.glColor4f(right.getRed() / 255f, right.getGreen() / 255f, right.getBlue() / 255f, right.getAlpha() / 255f);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glColor4f(left.getRed() / 255f, left.getGreen() / 255f, left.getBlue() / 255f, left.getAlpha() / 255f);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        end();
    }

    public static void gradientOutline(float x, float y, float width, float height, float radius, float thickness,
                                       Color first, Color second, boolean vertical) {
        outline(x, y, width, height, radius, thickness, first, second, vertical, Double.NaN);
    }

    public static void waveOutline(float x, float y, float width, float height, float radius, float thickness,
                                   Color first, Color second, double phase) {
        outline(x, y, width, height, radius, thickness, first, second, false, phase);
    }

    public static void waveGradient(float x, float y, float width, float height, Color first, Color second, double phase) {
        if (width <= 0f || height <= 0f) return;

        int segments = Math.max(8, (int) Math.ceil(width / 2f));
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < segments; i++) {
            float left = x + width * i / segments, right = x + width * (i + 1) / segments;
            Color from = waveColor(first, second, (float) i / segments, phase);
            Color to = waveColor(first, second, (float) (i + 1) / segments, phase);
            color(from);
            GL11.glVertex2f(left, y + height);
            color(to);
            GL11.glVertex2f(right, y + height);
            GL11.glVertex2f(right, y);
            color(from);
            GL11.glVertex2f(left, y);
        }
        GL11.glEnd();
        end();
    }

    public static float waveAmount(float position, double phase) {
        return (float) (0.5 + 0.5 * Math.sin(position * Math.PI * 2.0 - phase));
    }

    public static Color waveColor(Color first, Color second, float position, double phase) {
        float t = waveAmount(position, phase);
        return new Color(
                Math.round(first.getRed() + (second.getRed() - first.getRed()) * t),
                Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * t),
                Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * t),
                Math.round(first.getAlpha() + (second.getAlpha() - first.getAlpha()) * t));
    }

    private static void color(Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    private static void outline(float x, float y, float width, float height, float radius, float thickness,
                                Color first, Color second, boolean vertical, double phase) {
        if (width <= 0f || height <= 0f || thickness <= 0f) return;
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f));

        int steps = Math.max(4, (int) Math.ceil(r * 2f));
        float[][] centres = {{x + r, y + r}, {x + width - r, y + r}, {x + width - r, y + height - r}, {x + r, y + height - r}};
        float[] starts = {180f, 270f, 0f, 90f};
        int count = centres.length * (steps + 1);
        float[] px = new float[count], py = new float[count], nx = new float[count], ny = new float[count];
        int n = 0;
        for (int corner = 0; corner < centres.length; corner++) {
            for (int i = 0; i <= steps; i++) {
                double angle = Math.toRadians(starts[corner] + 90f * i / steps);
                nx[n] = (float) Math.cos(angle);
                ny[n] = (float) Math.sin(angle);
                px[n] = centres[corner][0] + nx[n] * r;
                py[n] = centres[corner][1] + ny[n] * r;
                n++;
            }
        }

        float half = thickness / 2f;
        float[] offsets = {-half - FEATHER, -half, half, half + FEATHER};
        float[] fades = {0f, 1f, 1f, 0f};

        begin();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;

            float stepLength = distance(px[i], py[i], px[j], py[j]);
            int pieces = Double.isNaN(phase) ? 1 : Math.max(1, (int) Math.ceil(stepLength / 2f));
            for (int piece = 0; piece < pieces; piece++) {
                float a = (float) piece / pieces, b = (float) (piece + 1) / pieces;
                float ax = px[i] + (px[j] - px[i]) * a, ay = py[i] + (py[j] - py[i]) * a;
                float bx = px[i] + (px[j] - px[i]) * b, by = py[i] + (py[j] - py[i]) * b;
                float anx = nx[i] + (nx[j] - nx[i]) * a, any = ny[i] + (ny[j] - ny[i]) * a;
                float bnx = nx[i] + (nx[j] - nx[i]) * b, bny = ny[i] + (ny[j] - ny[i]) * b;
                for (int band = 0; band < 3; band++) {
                    float near = offsets[band], far = offsets[band + 1];
                    gradientVertex(ax + anx * near, ay + any * near, fades[band], x, y, width, height, first, second, vertical, phase);
                    gradientVertex(bx + bnx * near, by + bny * near, fades[band], x, y, width, height, first, second, vertical, phase);
                    gradientVertex(bx + bnx * far, by + bny * far, fades[band + 1], x, y, width, height, first, second, vertical, phase);
                    gradientVertex(ax + anx * far, ay + any * far, fades[band + 1], x, y, width, height, first, second, vertical, phase);
                }
            }
        }
        GL11.glEnd();
        end();
    }

    private static void gradientVertex(float vx, float vy, float fade, float x, float y, float width, float height,
                                       Color first, Color second, boolean vertical, double phase) {
        float position = Math.max(0f, Math.min(1f, vertical ? (vy - y) / height : (vx - x) / width));
        float t = Double.isNaN(phase) ? position : waveAmount(position, phase);
        GL11.glColor4f(
                (first.getRed() + (second.getRed() - first.getRed()) * t) / 255f,
                (first.getGreen() + (second.getGreen() - first.getGreen()) * t) / 255f,
                (first.getBlue() + (second.getBlue() - first.getBlue()) * t) / 255f,
                (first.getAlpha() + (second.getAlpha() - first.getAlpha()) * t) / 255f * fade);
        GL11.glVertex2f(vx, vy);
    }

    public static void check(float cx, float cy, float size, float thickness, float progress, Color color) {
        path(thickness, progress, color,
                cx - size * 0.52f, cy + size * 0.04f,
                cx - size * 0.14f, cy + size * 0.40f,
                cx + size * 0.54f, cy - size * 0.36f);
    }

    public static void cross(float cx, float cy, float size, float thickness, float progress, Color color) {
        float reach = size * 0.40f;
        path(thickness, progress * 2f, color, cx - reach, cy - reach, cx + reach, cy + reach);
        path(thickness, progress * 2f - 1f, color, cx + reach, cy - reach, cx - reach, cy + reach);
    }

    public static void info(float cx, float cy, float size, float thickness, float progress, Color color) {
        disc(cx, cy - size * 0.52f, thickness * 0.62f, fade(color, progress * 3f));
        path(thickness, progress * 1.5f - 0.5f, color, cx, cy - size * 0.12f, cx, cy + size * 0.56f);
    }

    public static void warning(float cx, float cy, float size, float thickness, float progress, Color color) {
        path(thickness, progress * 1.5f, color, cx, cy - size * 0.56f, cx, cy + size * 0.12f);
        disc(cx, cy + size * 0.52f, thickness * 0.62f, fade(color, progress * 3f - 2f));
    }

    public static void chevron(float cx, float cy, float size, float thickness, boolean up, Color color) {
        float tip = up ? -size * 0.5f : size * 0.5f;
        path(thickness, 1f, color, cx - size, cy - tip, cx, cy + tip, cx + size, cy - tip);
    }

    public static void play(float cx, float cy, float size, Color color) {
        float a = color.getAlpha() / 255f;
        if (a <= 0f) return;

        float left = cx - size * 0.55f, right = cx + size * 0.75f;
        float top = cy - size * 0.8f, bottom = cy + size * 0.8f;

        begin();
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, cy);
        GL11.glEnd();
        end();

        path(FEATHER, 1f, color, left, top, left, bottom, right, cy, left, top);
    }

    private static void fan(float cx, float cy, float radius, double startAngle, double sweep,
                            float r, float g, float b, float a) {
        int segments = Math.max(CAP_SEGMENTS, (int) Math.ceil(sweep / (Math.PI / CAP_SEGMENTS)));
        float outer = radius + FEATHER;

        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < segments; i++) {
            double from = startAngle + sweep * i / segments;
            double to = startAngle + sweep * (i + 1) / segments;
            float cosFrom = (float) Math.cos(from), sinFrom = (float) Math.sin(from);
            float cosTo = (float) Math.cos(to), sinTo = (float) Math.sin(to);

            GL11.glColor4f(r, g, b, a);
            GL11.glVertex2f(cx, cy);
            GL11.glVertex2f(cx + cosTo * radius, cy + sinTo * radius);
            GL11.glVertex2f(cx + cosFrom * radius, cy + sinFrom * radius);

            GL11.glVertex2f(cx + cosFrom * radius, cy + sinFrom * radius);
            GL11.glVertex2f(cx + cosTo * radius, cy + sinTo * radius);
            GL11.glColor4f(r, g, b, 0f);
            GL11.glVertex2f(cx + cosTo * outer, cy + sinTo * outer);

            GL11.glVertex2f(cx + cosTo * outer, cy + sinTo * outer);
            GL11.glVertex2f(cx + cosFrom * outer, cy + sinFrom * outer);
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex2f(cx + cosFrom * radius, cy + sinFrom * radius);
        }
        GL11.glEnd();
    }

    private static void begin() {
        cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();

        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        if (cullWasEnabled) GlStateManager.enableCull();

        GlStateManager.resetColor();
    }

    private static Color fade(Color color, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * clamped));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
