package rip.tempest.modules.impl.visuals.cosmetics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

final class TailPhysics {
    static final double PX_PER_BLOCK = 16.0 / 0.9375;

    static final double FEET_Y = 1.5078125 * 16.0;

    static final class Profile {
        int points;
        double segmentPx;

        double rootStiffness, tipStiffness;

        double drag;

        double gravity;

        double baseAngle, bend;

        double crouchAngle, crouchBend;

        double runDrop;

        double idleSway, runSway, swayPeriod;

        double flick;
    }

    private static final double BEND_STIFFNESS = 0.3;

    private static final int SUBSTEPS = 8;

    private final Profile profile;
    private final double segment;
    private final double[][] pos, prev;
    private final double[][] shown, shownPrev;
    private final double[] root = new double[3];

    private double frameX, frameY, frameZ;
    private float frameYaw;
    private int lastTick = Integer.MIN_VALUE;
    private boolean settled;

    TailPhysics(Profile profile) {
        this.profile = profile;
        this.segment = profile.segmentPx / PX_PER_BLOCK;
        int n = profile.points;
        pos = new double[n][3];
        prev = new double[n][3];
        shown = new double[n][3];
        shownPrev = new double[n][3];
    }

    int count() {
        return profile.points;
    }

    boolean ready() {
        return settled;
    }

    void reset() {
        settled = false;
    }

    void step(EntityPlayer player) {
        double speed = Math.hypot(player.posX - player.prevPosX, player.posZ - player.prevPosZ);
        step(player.ticksExisted, player.posX, player.posY, player.posZ, player.renderYawOffset, speed,
                player.isSneaking(), !player.onGround && !player.capabilities.isFlying);
    }

    void step(int tick, double x, double y, double z, float yaw, double horizontalSpeed, boolean crouching, boolean airborne) {
        if (tick == lastTick) return;
        boolean gap = lastTick == Integer.MIN_VALUE || tick < lastTick || tick - lastTick > 10;
        lastTick = tick;

        boolean jumped = settled && Math.abs(x - frameX) + Math.abs(y - frameY) + Math.abs(z - frameZ) > 4.0;

        double time = (tick + 0.5) / 20.0;
        double speed = Math.min(1.0, horizontalSpeed / 0.28);
        double[][] target = restPose(crouching, airborne, time, speed);

        if (!settled || gap || jumped) {
            for (int i = 0; i < pos.length; i++) {
                copy(target[i], pos[i]);
                copy(target[i], prev[i]);
                copy(target[i], shown[i]);
                copy(target[i], shownPrev[i]);
            }
            moveFrame(x, y, z, yaw, false);
            settled = true;
            return;
        }

        for (int i = 0; i < pos.length; i++) copy(pos[i], shownPrev[i]);

        double startX = frameX, startY = frameY, startZ = frameZ;
        float startYaw = frameYaw, turn = MathHelper.wrapAngleTo180_float(yaw - startYaw);
        double drag = Math.pow(profile.drag, 1.0 / SUBSTEPS);
        double bend = 1.0 - Math.pow(1.0 - BEND_STIFFNESS, 1.0 / SUBSTEPS);
        double force = 1.0 / (SUBSTEPS * SUBSTEPS);

        for (int step = 1; step <= SUBSTEPS; step++) {
            double f = step / (double) SUBSTEPS;

            moveFrame(startX + (x - startX) * f, startY + (y - startY) * f, startZ + (z - startZ) * f,
                    startYaw + turn * (float) f, true);
            integrate(target, drag, force);
            copy(root, pos[0]);
            copy(root, prev[0]);
            smooth(target, bend);
            keepLength();
            collide();
            keepLength();
        }

        for (int i = 0; i < pos.length; i++) copy(pos[i], shown[i]);
    }

    private void integrate(double[][] target, double drag, double force) {
        int n = pos.length;
        for (int i = 1; i < n; i++) {
            double t = i / (double) (n - 1);
            double stiffness = (profile.rootStiffness + (profile.tipStiffness - profile.rootStiffness) * t) * force;
            double[] p = pos[i], q = prev[i], goal = target[i];
            for (int axis = 0; axis < 3; axis++) {
                double velocity = (p[axis] - q[axis]) * drag;
                q[axis] = p[axis];
                p[axis] += velocity + (goal[axis] - p[axis]) * stiffness;
            }
            p[1] -= profile.gravity * force;
        }
    }

    double[] point(int i, float partialTicks) {
        double[] a = shownPrev[i], b = shown[i];
        double bx = a[0] + (b[0] - a[0]) * partialTicks;
        double by = a[1] + (b[1] - a[1]) * partialTicks;
        double bz = a[2] + (b[2] - a[2]) * partialTicks;
        return new double[]{bx * PX_PER_BLOCK, FEET_Y - by * PX_PER_BLOCK, bz * PX_PER_BLOCK};
    }

    private double[][] restPose(boolean crouching, boolean airborne, double time, double speed) {
        Profile p = profile;

        rootPosition(crouching);

        double angle = crouching ? p.crouchAngle : p.baseAngle - p.runDrop * speed + (airborne ? 8.0 : 0.0);
        double bend = crouching ? p.crouchBend : p.bend * (1.0 - 0.55 * speed);
        double sway = p.idleSway + (p.runSway - p.idleSway) * speed;
        double phase = time * Math.PI * 2.0 / p.swayPeriod;

        int n = p.points;
        double[][] out = new double[n][3];
        copy(root, out[0]);
        for (int i = 1; i < n; i++) {
            double t = i / (double) (n - 1);
            double elevation = Math.toRadians(angle + bend * (i - 1));
            double side = Math.toRadians(sway * Math.pow(t, 1.2) * Math.sin(phase - i * 0.45)
                    + p.flick * t * t * Math.sin(phase * 2.7 + 1.3 - i * 0.6));
            double[] previous = out[i - 1];
            out[i][0] = previous[0] + segment * Math.sin(side) * Math.cos(elevation);
            out[i][1] = previous[1] + segment * Math.sin(elevation);
            out[i][2] = previous[2] + segment * Math.cos(side) * Math.cos(elevation);
        }
        return out;
    }

    private void rootPosition(boolean crouching) {
        double y = 11.4, z = 2.1;
        if (crouching) {
            double lean = 0.5;
            double leanedY = y * Math.cos(lean) - z * Math.sin(lean);
            double leanedZ = y * Math.sin(lean) + z * Math.cos(lean);
            y = leanedY + 3.2;
            z = leanedZ;
        }
        root[0] = 0;
        root[1] = (FEET_Y - y) / PX_PER_BLOCK;
        root[2] = z / PX_PER_BLOCK;
    }

    private void moveFrame(double x, double y, double z, float yaw, boolean carry) {
        if (carry) {
            double oldCos = MathHelper.cos(frameYaw * 0.017453292F), oldSin = MathHelper.sin(frameYaw * 0.017453292F);
            double newCos = MathHelper.cos(yaw * 0.017453292F), newSin = MathHelper.sin(yaw * 0.017453292F);
            for (int i = 1; i < pos.length; i++) {
                rebase(pos[i], oldCos, oldSin, newCos, newSin, x, y, z);
                rebase(prev[i], oldCos, oldSin, newCos, newSin, x, y, z);
            }
        }
        frameX = x;
        frameY = y;
        frameZ = z;
        frameYaw = yaw;
    }

    private void rebase(double[] p, double oldCos, double oldSin, double newCos, double newSin, double x, double y, double z) {
        double worldX = frameX + p[0] * oldCos + p[2] * oldSin;
        double worldY = frameY + p[1];
        double worldZ = frameZ + p[0] * oldSin - p[2] * oldCos;

        double dx = worldX - x, dz = worldZ - z;
        p[0] = dx * newCos + dz * newSin;
        p[1] = worldY - y;
        p[2] = dx * newSin - dz * newCos;
    }

    private void keepLength() {
        for (int i = 1; i < pos.length; i++) {
            double[] a = pos[i - 1], b = pos[i];
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-6) {
                dz = segment;
                length = segment;
            }
            double scale = segment / length;
            shift(i, a[0] + dx * scale - b[0], a[1] + dy * scale - b[1], a[2] + dz * scale - b[2]);
        }
    }

    private void smooth(double[][] target, double amount) {
        for (int i = 1; i < pos.length - 1; i++) {
            double[] before = pos[i - 1], here = pos[i], after = pos[i + 1];
            double[] restBefore = target[i - 1], rest = target[i], restAfter = target[i + 1];
            double[] move = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                double neighbours = ((before[axis] - restBefore[axis]) + (after[axis] - restAfter[axis])) * 0.5;
                move[axis] = (neighbours - (here[axis] - rest[axis])) * amount;
            }
            shift(i, move[0], move[1], move[2]);
        }

        int tip = pos.length - 1;
        double[] move = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double neighbour = pos[tip - 1][axis] - target[tip - 1][axis];
            move[axis] = (neighbour - (pos[tip][axis] - target[tip][axis])) * amount * 0.5;
        }
        shift(tip, move[0], move[1], move[2]);
    }

    private void collide() {
        for (int i = 1; i < pos.length; i++) {
            double[] p = pos[i], q = prev[i];
            double back = root[2] - 0.01;
            if (Math.abs(p[0]) < 0.27 && p[2] < back) {
                p[2] = back;
                q[2] = back;
            }
            if (p[1] < 0.03) {
                p[1] = 0.03;
                q[1] = 0.03;
            }
        }
    }

    private void shift(int i, double dx, double dy, double dz) {
        double[] p = pos[i], q = prev[i];
        p[0] += dx;
        p[1] += dy;
        p[2] += dz;
        q[0] += dx;
        q[1] += dy;
        q[2] += dz;
    }

    private static void copy(double[] from, double[] to) {
        to[0] = from[0];
        to[1] = from[1];
        to[2] = from[2];
    }
}
