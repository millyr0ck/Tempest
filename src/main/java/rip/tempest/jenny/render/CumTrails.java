package rip.tempest.jenny.render;

import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.geo.GeoBone;
import rip.tempest.jenny.geo.GeoModel;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class CumTrails {
    private static final Random RNG = new Random();
    private static final List<Trail> TRAILS = new ArrayList<>();

    private CumTrails() {
    }

    public static void creampie(NpcEntity npc) {
        GeoModel model = npc.getModel();
        if (model == null || !model.bones.containsKey("creampiePos")) return;
        double rad = Math.toRadians(npc.rotationYaw);
        TRAILS.add(new Trail(npc, "creampiePos", -Math.sin(rad) * 0.6, 0.0, Math.cos(rad) * 0.6, 100, 0.6f, 0.5f));
    }

    public static void clear(NpcEntity npc) {
        TRAILS.removeIf(t -> t.owner == npc);
    }

    public static void clearAll() {
        TRAILS.clear();
    }

    public static void tick() {
        Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            Trail t = it.next();
            if (t.owner.isDead) {
                it.remove();
                continue;
            }
            t.update();
        }
    }

    public static void render(float partialTicks, double camX, double camY, double camZ) {
        if (TRAILS.isEmpty()) return;
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        for (Trail t : TRAILS) {
            t.sort();
            wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION_COLOR);
            Vec3 last = null;
            for (Segment s : t.segments) {
                double x = s.prevX + (s.x - s.prevX) * partialTicks;
                double y = s.prevY + (s.y - s.prevY) * partialTicks;
                double z = s.prevZ + (s.z - s.prevZ) * partialTicks;
                Vec3 p = new Vec3(x, y, z);
                if (last != null && last.distanceTo(p) > t.maxDistance) {
                    tess.draw();
                    wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION_COLOR);
                }
                wr.pos(x - camX, y - camY, z - camZ).color(255, 255, 255, 255).endVertex();
                last = p;
            }
            tess.draw();
        }
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    private static final class Trail {
        final NpcEntity owner;
        final String bone;
        final double dirX, dirY, dirZ;
        final int max;
        final float randomness;
        final float maxDistance;
        final List<Segment> segments = new ArrayList<>();

        Trail(NpcEntity owner, String bone, double dirX, double dirY, double dirZ, int max, float randomness, float maxDistance) {
            this.owner = owner;
            this.bone = bone;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.max = max;
            this.randomness = randomness;
            this.maxDistance = maxDistance;
        }

        void update() {
            if (segments.size() < max) {
                GeoModel model = owner.getModel();
                GeoBone b = model == null ? null : model.bones.get(bone);
                if (b != null) {
                    double[] o = BoneMath.offset(owner, model, b, b.pivot);
                    for (int i = 0; i < 6; i++) {
                        segments.add(new Segment(owner.posX + o[0], owner.posY + o[1], owner.posZ + o[2],
                                dirX + (RNG.nextFloat() * 2f - 1f) * randomness,
                                dirY + (RNG.nextFloat() * 2f - 1f) * randomness,
                                dirZ + (RNG.nextFloat() * 2f - 1f) * randomness));
                    }
                }
            }
            for (Segment s : segments) s.update();
        }

        void sort() {
            for (int i = 1; i < segments.size(); i++) {
                Segment seg = segments.get(i);
                int j = i - 1;
                while (j >= 0 && seg.distSq(segments.get(j)) < seg.distSq(segments.get(j + 1))) {
                    segments.set(j + 1, segments.get(j));
                    j--;
                }
                segments.set(j + 1, seg);
            }
        }
    }

    private static final class Segment {
        double x, y, z, prevX, prevY, prevZ;
        double vx, vy, vz;
        boolean stuck;

        Segment(double x, double y, double z, double vx, double vy, double vz) {
            this.x = prevX = x;
            this.y = prevY = y;
            this.z = prevZ = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }

        void update() {
            prevX = x;
            prevY = y;
            prevZ = z;
            if (stuck) return;
            vx *= 0.95;
            vy = (vy - 0.4905) * 0.95;
            vz *= 0.95;
            double nx = x + vx * 0.05, ny = y + vy * 0.05, nz = z + vz * 0.05;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null && mc.theWorld.getBlockState(new BlockPos(nx, ny, nz)).getBlock().getMaterial() != Material.air) {
                stuck = true;
                return;
            }
            x = nx;
            y = ny;
            z = nz;
        }

        double distSq(Segment o) {
            double dx = x - o.x, dy = y - o.y, dz = z - o.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
