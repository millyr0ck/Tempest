package rip.tempest.utils.render;

import rip.tempest.Tempest;
import rip.tempest.modules.impl.visuals.AnimationsModule;
import rip.tempest.processes.ColorProcess;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class SwordTrail {
    private static final double[] TIP = {15.0 / 16, 15.0 / 16, 0.5};
    private static final double[] INNER = {8.0 / 16, 8.0 / 16, 0.5};

    private static final float PROGRESS_STEP = 0.004F;

    private static final int MAX_STEPS_PER_FRAME = 24;
    private static final int MAX_SAMPLES = 2048;
    private static final int SUBDIVISIONS = 2;

    private static final float BASE_ALPHA = 0.22F;

    private static final float[][] STRANDS = {
            {0.00F, 1.00F, 0.0F, 0.6F},
            {0.08F, 0.75F, 1.7F, 1.0F},
            {0.20F, 0.50F, 3.1F, 1.4F},
            {0.36F, 0.30F, 4.6F, 1.9F},
    };

    @FunctionalInterface
    public interface Pose {
        void apply(float swingProgress);
    }

    private static final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private static final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

    private static int segment;
    private static float lastProgress;
    private static boolean wasSwinging;
    private static long lastCapture;

    private SwordTrail() {
    }

    private static boolean enabled() {
        return AnimationsModule.swingTrail.getValue()
                && Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled();
    }

    public static void capture(ItemStack stack, float progress, boolean swingInProgress, Pose pose) {
        long now = System.nanoTime();
        long frame = lastCapture == 0 ? 0 : Math.min(now - lastCapture, 100_000_000L);
        lastCapture = now;

        boolean sword = enabled() && pose != null && stack != null && stack.getItem() instanceof ItemSword;
        boolean swinging = sword && (swingInProgress || progress > 0);

        if (!swinging) {
            if (wasSwinging && sword && lastProgress < 1F) {
                sweep(stack, pose, lastProgress, 1F, false, now - frame, now);
            }
            wasSwinging = false;
            return;
        }

        if (!wasSwinging || progress < lastProgress) {
            segment++;
            sweep(stack, pose, 0F, progress, true, now - frame, now);
        } else {
            sweep(stack, pose, lastProgress, progress, false, now - frame, now);
        }
        wasSwinging = true;
        lastProgress = progress;
    }

    private static void sweep(ItemStack stack, Pose pose, float from, float to, boolean includeStart, long startTime, long endTime) {
        int steps = Math.max(1, Math.min(MAX_STEPS_PER_FRAME, (int) Math.ceil(Math.abs(to - from) / PROGRESS_STEP)));
        IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
        for (int i = includeStart ? 0 : 1; i <= steps; i++) {
            float t = i / (float) steps;
            GlStateManager.pushMatrix();
            pose.apply(from + (to - from) * t);

            GlStateManager.scale(2.0F, 2.0F, 2.0F);
            model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.FIRST_PERSON);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
            matrix.clear();
            GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, matrix);
            GlStateManager.popMatrix();

            samples.addLast(new Sample(startTime + (long) ((endTime - startTime) * (double) t), segment, transform(TIP), transform(INNER)));
        }
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
    }

    public static void render() {
        long now = System.nanoTime();
        double life = AnimationsModule.trailLength.getValue() * 1_000_000.0;
        while (!samples.isEmpty() && now - samples.peekFirst().time > life) samples.removeFirst();
        if (samples.size() < 2 || !enabled()) return;

        boolean client = AnimationsModule.trailColor.getValue() == AnimationsModule.TrailColor.Client;
        Color head = client ? ColorProcess.colors.getFirst() : Color.WHITE;
        Color tail = client ? ColorProcess.colors.getSecond() : Color.WHITE;
        float opacity = AnimationsModule.trailOpacity.getValue().floatValue();

        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        mc.entityRenderer.disableLightmap();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        List<Sample> run = new ArrayList<>();
        for (Sample sample : samples) {
            if (!run.isEmpty() && run.get(run.size() - 1).segment != sample.segment) {
                drawRun(run, now, life, head, tail, opacity);
                run.clear();
            }
            run.add(sample);
        }
        drawRun(run, now, life, head, tail, opacity);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        mc.entityRenderer.enableLightmap();
        GlStateManager.popMatrix();
    }

    private static void drawRun(List<Sample> run, long now, double life, Color head, Color tail, float opacity) {
        if (run.size() < 2) return;

        List<double[]> tips = new ArrayList<>(), inners = new ArrayList<>();
        List<Double> births = new ArrayList<>();
        for (int i = 0; i < run.size() - 1; i++) {
            Sample p0 = run.get(Math.max(0, i - 1)), p1 = run.get(i), p2 = run.get(i + 1), p3 = run.get(Math.min(run.size() - 1, i + 2));
            for (int s = 0; s < SUBDIVISIONS; s++) {
                float t = s / (float) SUBDIVISIONS;
                tips.add(catmullRom(p0.tip, p1.tip, p2.tip, p3.tip, t));
                inners.add(catmullRom(p0.inner, p1.inner, p2.inner, p3.inner, t));
                births.add(p1.time + (p2.time - p1.time) * (double) t);
            }
        }
        Sample last = run.get(run.size() - 1);
        tips.add(last.tip);
        inners.add(last.inner);
        births.add((double) last.time);

        int count = tips.size();
        double nowMs = now / 1_000_000.0;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();

        for (float[] strand : STRANDS) {
            float position = strand[0], strength = strand[1], phase = strand[2], spread = strand[3];
            double[][] centres = new double[count][];
            double[][] edges = new double[count][];
            float[] alphas = new float[count];
            Color[] colors = new Color[count];

            for (int i = 0; i < count; i++) {
                double[] tip = tips.get(i), inner = inners.get(i);
                float age = (float) Math.min(1.0, (now - births.get(i)) / life);
                double bx = inner[0] - tip[0], by = inner[1] - tip[1], bz = inner[2] - tip[2];
                double length = Math.max(1.0E-4, Math.sqrt(bx * bx + by * by + bz * bz));
                double ux = bx / length, uy = by / length, uz = bz / length;

                double width = length * (0.05 + 0.32 * age) * spread;
                double waver = length * 0.18 * age * Math.sin(phase + births.get(i) / 1_000_000.0 * 0.03 + nowMs * 0.005);
                double lift = length * 0.12 * age * age;
                double cx = tip[0] + bx * position + ux * waver;
                double cy = tip[1] + by * position + uy * waver + lift;
                double cz = tip[2] + bz * position + uz * waver;

                centres[i] = new double[]{cx, cy, cz};
                edges[i] = new double[]{ux * width, uy * width, uz * width};

                float taper = smoothstep(0F, 0.15F, i / (float) (count - 1));
                alphas[i] = opacity * strength * BASE_ALPHA * fade(age) * smoothstep(0F, 0.05F, age) * taper;
                colors[i] = blend(head, tail, age);
            }

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < count - 1; i++) {
                for (int side = -1; side <= 1; side += 2) {
                    vertex(buffer, centres[i], edges[i], side, colors[i], 0F);
                    vertex(buffer, centres[i], edges[i], 0, colors[i], alphas[i]);
                    vertex(buffer, centres[i + 1], edges[i + 1], 0, colors[i + 1], alphas[i + 1]);
                    vertex(buffer, centres[i + 1], edges[i + 1], side, colors[i + 1], 0F);
                }
            }
            tessellator.draw();
        }
    }

    private static void vertex(WorldRenderer buffer, double[] centre, double[] edge, int side, Color color, float alpha) {
        buffer.pos(centre[0] + edge[0] * side, centre[1] + edge[1] * side, centre[2] + edge[2] * side)
                .color(color.getRed(), color.getGreen(), color.getBlue(), (int) (Math.max(0F, Math.min(1F, alpha)) * 255))
                .endVertex();
    }

    private static float fade(float age) {
        float remaining = 1F - age;
        return remaining * remaining * (3F - 2F * remaining);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0F, Math.min(1F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3F - 2F * t);
    }

    private static Color blend(Color from, Color to, float amount) {
        if (from.equals(to)) return from;
        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static double[] transform(double[] point) {
        double x = point[0], y = point[1], z = point[2];
        return new double[]{
                matrix.get(0) * x + matrix.get(4) * y + matrix.get(8) * z + matrix.get(12),
                matrix.get(1) * x + matrix.get(5) * y + matrix.get(9) * z + matrix.get(13),
                matrix.get(2) * x + matrix.get(6) * y + matrix.get(10) * z + matrix.get(14)
        };
    }

    private static double[] catmullRom(double[] p0, double[] p1, double[] p2, double[] p3, float t) {
        double t2 = t * t, t3 = t2 * t;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = 0.5 * (2 * p1[i] + (p2[i] - p0[i]) * t
                    + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2
                    + (3 * p1[i] - p0[i] - 3 * p2[i] + p3[i]) * t3);
        }
        return out;
    }

    private record Sample(long time, int segment, double[] tip, double[] inner) {
    }
}
