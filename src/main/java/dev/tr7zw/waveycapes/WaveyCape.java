package dev.tr7zw.waveycapes;

import dev.tr7zw.waveycapes.sim.StickSimulation3d;
import dev.tr7zw.waveycapes.sim.StickSimulation3d.Point;
import dev.tr7zw.waveycapes.util.Vector3;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WaveyCape {

    private static final int PART_COUNT = 16;
    private static final float CAPE_WIDTH = 10F / 16F;
    private static final float CAPE_HEIGHT = 1F;
    private static final float CAPE_DEPTH = 1F / 16F;
    private static final float PART_HEIGHT = CAPE_HEIGHT / PART_COUNT;
    /** Cape UVs follow the vanilla 64x32 layout; HD capes scale the same layout up. */
    private static final float TEXEL_U = 1F / 64F, TEXEL_V = 1F / 32F;

    /** Tunables, mirroring WaveyCapes' config screen. */
    public static final class Settings {
        public float gravity = 25;
        public float heightMultiplier = 6;
        public float straveMultiplier = 2;
        public boolean wind;
    }

    /**
     * One player's rope: the cape's 16 points, or anything else hung off the player that should
     * swing the same way (Tempest's cosmetic tails use one).
     */
    public static final class Rope {
        public final StickSimulation3d simulation = new StickSimulation3d();
        private int lastTick = Integer.MIN_VALUE;

        /**
         * WaveyCapes steps the rope at the end of every player tick. Tempest has no hook there,
         * so the step happens on the first frame after each new tick, which reads the same
         * post-tick state. A rope that hasn't been drawn for a while settles again, as on first
         * creation.
         *
         * @param gravity the direction the rope hangs in, in simulation space (-y is down the
         *                body, -x is behind it)
         */
        public void tick(EntityPlayer player, Settings settings, Vector3 gravity, int parts) {
            int tick = player.ticksExisted;
            if (lastTick == tick) return;

            boolean stale = lastTick == Integer.MIN_VALUE || tick < lastTick || tick - lastTick > 10;
            lastTick = tick;

            if (simulation.init(parts) || stale) {
                simulation.applyMovement(new Vector3(1f, 1f, 0));
                for (int i = 0; i < 5; i++) { // quickly doing a few simulation steps to get the cape int a stable configuration
                    simulate(player, simulation, settings, gravity);
                }
            }
            simulate(player, simulation, settings, gravity);
        }
    }

    private static final Vector3 DOWN = new Vector3(0, -1, 0);
    private static final Map<EntityPlayer, Rope> STATES = new WeakHashMap<>();

    private WaveyCape() {
    }

    /**
     * Draws the player's cape with whatever texture is bound. Call from the cape layer with the
     * player-model transform in place, where vanilla would call {@code renderCape}.
     */
    public static void render(EntityPlayer player, float partialTicks, Settings settings) {
        Rope rope = STATES.computeIfAbsent(player, p -> new Rope());
        rope.tick(player, settings, DOWN, PART_COUNT);
        drawSmoothCape(player, rope.simulation, partialTicks, settings);
    }

    // ── simulation ───────────────────────────────────────────────────────────────

    private static void simulate(EntityPlayer player, StickSimulation3d simulation, Settings settings, Vector3 gravity) {
        if (simulation.empty()) return;

        double d = player.chasingPosX - player.posX;
        double m = player.chasingPosZ - player.posZ;
        float n = player.renderYawOffset;
        double o = Math.sin(n * 0.017453292F);
        double p = -Math.cos(n * 0.017453292F);
        float heightMul = settings.heightMultiplier;
        float straveMul = settings.straveMultiplier;
        boolean underWater = player.isInsideOfMaterial(Material.water);
        if (underWater) {
            heightMul *= 2; // let the cape have more drag than the player underwater
        }
        // gives the cape a small swing when jumping/falling to not clip with
        // itself/simulate some air getting under it
        double fallHack = Math.max(0, Math.min(1, (player.prevPosY - player.posY) * 10));
        simulation.setGravity(underWater ? settings.gravity / 10f : settings.gravity);

        // Sideways movement relative to where the player faces (WaveyCapes' Vector2 strave).
        double strafeX = player.posX - player.prevPosX;
        double strafeZ = player.posZ - player.prevPosZ;
        double yaw = Math.toRadians(-player.rotationYaw);
        float strave = (float) (Math.cos(yaw) * strafeX - Math.sin(yaw) * strafeZ);

        boolean crouching = player.isSneaking();
        double changeX = (d * o + m * p) + fallHack + (crouching && !simulation.isSneaking() ? 3 : 0);
        double changeY = ((player.posY - player.prevPosY) * heightMul) + (crouching && !simulation.isSneaking() ? 1 : 0);
        double changeZ = -strave * straveMul;
        simulation.setSneaking(crouching);

        // 1.8 has no swimming pose, so a cape's gravity always points straight down the body.
        simulation.setGravityDirection(gravity.clone());
        simulation.applyMovement(new Vector3((float) changeX, (float) changeY, (float) changeZ));
        simulation.simulate();
    }

    // ── drawing ──────────────────────────────────────────────────────────────────

    private static void drawSmoothCape(EntityPlayer player, StickSimulation3d simulation, float delta, Settings settings) {
        if (simulation.empty()) return;

        float[][] matrices = new float[PART_COUNT][];
        Vector3[] frontNormals = new Vector3[PART_COUNT];
        Vector3[] backNormals = new Vector3[PART_COUNT];
        for (int part = 0; part < PART_COUNT; part++) {
            matrices[part] = partMatrix(player, simulation, delta, part, settings);
            float[] previous = matrices[Math.max(part - 1, 0)];
            frontNormals[part] = normal(previous, previous, matrices[part],
                    new Vector3(CAPE_WIDTH / 2F, part * PART_HEIGHT, -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, part * PART_HEIGHT, -CAPE_DEPTH),
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * PART_HEIGHT, -CAPE_DEPTH));
            backNormals[part] = normal(previous, previous, matrices[part],
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * PART_HEIGHT, 0),
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * PART_HEIGHT, 0),
                    new Vector3(CAPE_WIDTH / 2F, part * PART_HEIGHT, 0));
        }

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);

        for (int part = 0; part < PART_COUNT; part++) {
            float[] current = matrices[part];
            float[] previous = matrices[Math.max(part - 1, 0)];
            float top = part * PART_HEIGHT, bottom = (part + 1) * PART_HEIGHT;

            if (part == 0) {
                float minU = 1 * TEXEL_U, maxU = 11 * TEXEL_U, minV = 0, maxV = TEXEL_V;
                Vector3 n = normal(current, current, current,
                        new Vector3(CAPE_WIDTH / 2, 0, 0), new Vector3(-CAPE_WIDTH / 2, 0, 0), new Vector3(CAPE_WIDTH / 2, 0, CAPE_DEPTH));
                vertex(buffer, current, CAPE_WIDTH / 2, 0, 0, maxU, maxV, n);
                vertex(buffer, current, -CAPE_WIDTH / 2, 0, 0, minU, maxV, n);
                vertex(buffer, current, -CAPE_WIDTH / 2, 0, -CAPE_DEPTH, minU, minV, n);
                vertex(buffer, current, CAPE_WIDTH / 2, 0, -CAPE_DEPTH, maxU, minV, n);
            }

            if (part == PART_COUNT - 1) {
                float minU = 11 * TEXEL_U, maxU = 21 * TEXEL_U, minV = 0, maxV = TEXEL_V;
                Vector3 n = normal(current, current, current,
                        new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH), new Vector3(-CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                        new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, 0));
                vertex(buffer, current, CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, maxU, minV, n);
                vertex(buffer, current, -CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, minU, minV, n);
                vertex(buffer, current, -CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, minU, maxV, n);
                vertex(buffer, current, CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, maxU, maxV, n);
            }

            float minV = TEXEL_V * (part + 1);
            float maxV = minV + TEXEL_V;

            // Left edge.
            float minU = 0, maxU = TEXEL_U;
            Vector3 n = normal(current, current, previous,
                    new Vector3(-CAPE_WIDTH / 2F, bottom, 0), new Vector3(-CAPE_WIDTH / 2F, bottom, -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, top, 0));
            vertex(buffer, current, -CAPE_WIDTH / 2F, bottom, 0, minU, maxV, n);
            vertex(buffer, current, -CAPE_WIDTH / 2F, bottom, -CAPE_DEPTH, maxU, maxV, n);
            vertex(buffer, previous, -CAPE_WIDTH / 2F, top, -CAPE_DEPTH, maxU, minV, n);
            vertex(buffer, previous, -CAPE_WIDTH / 2F, top, 0, minU, minV, n);

            // Right edge.
            minU = 11 * TEXEL_U;
            maxU = 12 * TEXEL_U;
            n = normal(current, current, previous,
                    new Vector3(CAPE_WIDTH / 2F, bottom, -CAPE_DEPTH), new Vector3(CAPE_WIDTH / 2F, bottom, 0),
                    new Vector3(CAPE_WIDTH / 2F, top, -CAPE_DEPTH));
            vertex(buffer, current, CAPE_WIDTH / 2F, bottom, -CAPE_DEPTH, minU, maxV, n);
            vertex(buffer, current, CAPE_WIDTH / 2F, bottom, 0, maxU, maxV, n);
            vertex(buffer, previous, CAPE_WIDTH / 2F, top, 0, maxU, minV, n);
            vertex(buffer, previous, CAPE_WIDTH / 2F, top, -CAPE_DEPTH, minU, minV, n);

            // Front face, with normals averaged across neighbouring parts so the bend shades smoothly.
            minU = 1 * TEXEL_U;
            maxU = 11 * TEXEL_U;
            Vector3 normalTop = frontNormals[part].clone().add(frontNormals[Math.max(part - 1, 0)]).div(2);
            Vector3 normalBottom = frontNormals[part].clone().add(frontNormals[Math.min(part + 1, PART_COUNT - 1)]).div(2);
            vertex(buffer, previous, CAPE_WIDTH / 2, top, -CAPE_DEPTH, maxU, minV, normalTop);
            vertex(buffer, previous, -CAPE_WIDTH / 2, top, -CAPE_DEPTH, minU, minV, normalTop);
            vertex(buffer, current, -CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, minU, maxV, normalBottom);
            vertex(buffer, current, CAPE_WIDTH / 2, bottom, -CAPE_DEPTH, maxU, maxV, normalBottom);

            // Back face.
            minU = 12 * TEXEL_U;
            maxU = 22 * TEXEL_U;
            normalTop = backNormals[part].clone().add(backNormals[Math.max(part - 1, 0)]).div(2);
            normalBottom = backNormals[part].clone().add(backNormals[Math.min(part + 1, PART_COUNT - 1)]).div(2);
            vertex(buffer, previous, CAPE_WIDTH / 2, top, 0, minU, minV, normalTop);
            vertex(buffer, previous, -CAPE_WIDTH / 2, top, 0, maxU, minV, normalTop);
            vertex(buffer, current, -CAPE_WIDTH / 2, bottom, 0, maxU, maxV, normalBottom);
            vertex(buffer, current, CAPE_WIDTH / 2, bottom, 0, minU, maxV, normalBottom);
        }

        tessellator.draw();
    }

    /**
     * One cape slice's transform relative to the cape layer: the vanilla cape anchor, then the
     * slice moved and tilted to follow the rope.
     */
    private static float[] partMatrix(EntityPlayer player, StickSimulation3d simulation, float delta, int part, Settings settings) {
        List<Point> points = simulation.getPoints();
        float[] matrix = identity();

        // Modern capes sit off the back of a chestplate; vanilla 1.8 never moved them.
        if (player.getCurrentArmor(2) != null) {
            translate(matrix, 0.0F, -0.053125F, 0.06875F);
        }
        translate(matrix, 0.0F, 0.0F, 0.125F);

        // 1.8's body doesn't lean the cape layer when sneaking; vanilla's cape adds this instead.
        float lean = 0;
        if (player.isSneaking()) {
            translate(matrix, 0.0F, 0.142F, -0.0178F);
            lean = 25.0F;
        }

        float x = points.get(part).getLerpX(delta) - points.get(0).getLerpX(delta);
        if (x > 0) {
            x = 0;
        }
        float y = points.get(0).getLerpY(delta) - part - points.get(part).getLerpY(delta);
        float z = points.get(0).getLerpZ(delta) - points.get(part).getLerpZ(delta);
        float partRotation = rotation(delta, part, points);
        float windSwing = windSwing(part, settings.wind, player.isInsideOfMaterial(Material.water));

        // vanilla rotating and wind
        rotateX(matrix, 6.0F + lean + windSwing);
        rotateY(matrix, 180.0F);

        translate(matrix, -z / PART_COUNT, y / PART_COUNT, x / PART_COUNT); // movement from the simulation
        // offsetting so the rotation is on the cape part
        translate(matrix, 0, 0.48F / 16, -0.48F / 16);
        translate(matrix, 0, part * 1f / PART_COUNT, 0);
        rotateX(matrix, -partRotation); // apply actual rotation
        // undoing the rotation
        translate(matrix, 0, -part * 1f / PART_COUNT, 0);
        translate(matrix, 0, -0.48F / 16, 0.48F / 16);
        return matrix;
    }

    private static float rotation(float delta, int part, List<Point> points) {
        if (part == PART_COUNT - 1) {
            return rotation(delta, part - 1, points);
        }
        Vector3 angle = points.get(part + 1).getLerpedPos(delta).subtract(points.get(part).getLerpedPos(delta));
        return (float) (Math.toDegrees(Math.atan2(angle.x, angle.y)) + 180);
    }

    private static float windSwing(int part, boolean wind, boolean underwater) {
        if (!wind) return 0;
        long highlightedPart = (System.currentTimeMillis() / (underwater ? 9 : 3)) % 360;
        float relativePart = (float) (part + 1) / PART_COUNT;
        return (float) (Math.sin(Math.toRadians(relativePart * 360 - highlightedPart)) * 3);
    }

    private static void vertex(WorldRenderer buffer, float[] matrix, float x, float y, float z, float u, float v, Vector3 normal) {
        Vector3 pos = transform(matrix, x, y, z);
        buffer.pos(pos.x, pos.y, pos.z).tex(u, v).normal(normal.x, normal.y, normal.z).endVertex();
    }

    private static Vector3 normal(float[] matrix1, float[] matrix2, float[] matrix3, Vector3 vector1, Vector3 vector2, Vector3 vector3) {
        Vector3 a = transform(matrix1, vector1.x, vector1.y, vector1.z);
        Vector3 b = transform(matrix2, vector2.x, vector2.y, vector2.z);
        Vector3 c = transform(matrix3, vector3.x, vector3.y, vector3.z);
        b.subtract(a);
        c.subtract(a);
        return b.cross(c).normalize();
    }

    // ── column-major 4x4 matrices, composed the way glTranslate/glRotate would ──

    private static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1;
        return m;
    }

    private static void translate(float[] m, float x, float y, float z) {
        for (int row = 0; row < 4; row++) {
            m[12 + row] += m[row] * x + m[4 + row] * y + m[8 + row] * z;
        }
    }

    private static void rotateX(float[] m, float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        for (int row = 0; row < 4; row++) {
            float col1 = m[4 + row], col2 = m[8 + row];
            m[4 + row] = col1 * c + col2 * s;
            m[8 + row] = col2 * c - col1 * s;
        }
    }

    private static void rotateY(float[] m, float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        for (int row = 0; row < 4; row++) {
            float col0 = m[row], col2 = m[8 + row];
            m[row] = col0 * c - col2 * s;
            m[8 + row] = col0 * s + col2 * c;
        }
    }

    private static Vector3 transform(float[] m, float x, float y, float z) {
        return new Vector3(
                m[0] * x + m[4] * y + m[8] * z + m[12],
                m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]);
    }
}
