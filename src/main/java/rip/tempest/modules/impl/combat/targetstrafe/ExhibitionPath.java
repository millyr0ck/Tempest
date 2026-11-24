package rip.tempest.modules.impl.combat.targetstrafe;

import rip.tempest.Tempest;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.movement.LongJumpModule;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.RotationUtils;
import net.minecraft.block.BlockAir;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static rip.tempest.utils.Util.mc;

public final class ExhibitionPath {
    public enum TargetMode {
        Nearby,
        AuraOnly {
            @Override
            public String toString() {
                return "Aura Only";
            }
        }
    }

    public enum PathMode {
        Normal,
        Adaptive
    }

    private static final int STEP = 10;
    private static final double SLIDE = 0.1D;
    private static final double ROOM = 0.4D;
    private static final double[][] CORNERS = {{0.4, 0.4}, {-0.4, 0.4}, {0.4, -0.4}, {-0.4, -0.4}};

    private final NumberProperty radius, targetRange, offset, jitterDistance, jitterFrequency;
    private final ModeProperty<TargetMode> targetMode;
    private final ModeProperty<PathMode> pathMode;
    private final Property<Boolean> behind, teams, antiStuck, antiVoid;

    private final Timer flipTimer = new Timer();
    private boolean reverse;

    public ExhibitionPath(NumberProperty radius, NumberProperty targetRange, ModeProperty<TargetMode> targetMode,
                          ModeProperty<PathMode> pathMode, Property<Boolean> behind, Property<Boolean> teams,
                          Property<Boolean> antiStuck, Property<Boolean> antiVoid, NumberProperty offset,
                          NumberProperty jitterDistance, NumberProperty jitterFrequency) {
        this.radius = radius;
        this.targetRange = targetRange;
        this.targetMode = targetMode;
        this.pathMode = pathMode;
        this.behind = behind;
        this.teams = teams;
        this.antiStuck = antiStuck;
        this.antiVoid = antiVoid;
        this.offset = offset;
        this.jitterDistance = jitterDistance;
        this.jitterFrequency = jitterFrequency;
    }

    public void reset() {
        reverse = false;
        flipTimer.reset();
    }

    public Entity findTarget() {
        if (targetMode.getValue() == TargetMode.AuraOnly) return AuraModule.target();

        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || AntiBotModule.botList.contains(player) || AntiBotModule.isHologram(player)) continue;

            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance > targetRange.getValue() || distance >= nearestDistance) continue;
            if (teams.getValue() && inTeam(player)) continue;

            nearest = player;
            nearestDistance = distance;
        }
        return nearest;
    }

    private static boolean inTeam(Entity other) {
        String own = mc.thePlayer.getDisplayName().getFormattedText();
        Matcher matcher = Pattern.compile("§(.).*§r").matcher(own);
        String code = "§" + (matcher.find() ? matcher.group(1) : "f");
        return own.contains(code) && other.getDisplayName().getFormattedText().contains(code);
    }

    public void antiStuck() {
        if (!antiStuck.getValue() || !flipTimer.hasTimeElapsed(450)) return;

        boolean overVoid = false;
        if (antiVoid.getValue() && MovementUtils.isMoving()) {
            overVoid = true;
            for (double[] corner : CORNERS) {
                if (groundBelow(mc.thePlayer.posX + corner[0], (int) mc.thePlayer.posY, mc.thePlayer.posZ + corner[1])) {
                    overVoid = false;
                    break;
                }
            }
        }

        if (mc.thePlayer.isCollidedHorizontally || overVoid) {
            reverse = !reverse;
            flipTimer.reset();
        }
    }

    public double[] point(Entity target) {
        final boolean adaptive = pathMode.getValue() == PathMode.Adaptive;
        final boolean flying = isFlying();
        final double motionY = mc.thePlayer.motionY;
        final AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();

        double rad = radius.getValue();
        if (jitterDistance.getValue() > 0) {
            rad += Math.sin((1 + jitterFrequency.getValue()) * mc.thePlayer.ticksExisted / 5D) * jitterDistance.getValue();
        }

        double toTarget = MathHelper.wrapAngleTo180_float(RotationUtils.calculate(new Vector3d(target.posX, target.posY, target.posZ)).getX() - 90F);
        double start = Math.round(toTarget / STEP) * (double) STEP;

        double lastRad = rad;
        List<double[]> points = new ArrayList<>();

        for (int i = 0; i <= (behind.getValue() ? 10 : 2); i++) {
            double angle = Math.toRadians((reverse ? -i : i) * STEP + start);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double rotX = rad * cos, rotZ = rad * sin;

            if (adaptive) {
                List<double[]> valid = new ArrayList<>();

                for (int n = 1; n < 30; n++) {
                    addIfValid(valid, box, target, rad + SLIDE * n, cos, sin, motionY, flying);
                }
                for (int n = 1; n < (int) (rad / SLIDE); n++) {
                    addIfValid(valid, box, target, rad - SLIDE * n, cos, sin, motionY, flying);
                }

                double closestDifference = Double.MAX_VALUE;
                double reference = lastRad;
                for (double[] point : valid) {
                    double pointRad = Math.sqrt(point[0] * point[0] + point[1] * point[1]);
                    double difference = Math.abs(reference - pointRad);
                    if (difference < closestDifference) {
                        closestDifference = difference;
                        lastRad = pointRad;
                        rotX = point[0];
                        rotZ = point[1];
                    }
                }
            }

            points.add(new double[]{rotX, rotZ});
        }

        if (behind.getValue()) {
            double[] best = null;
            for (double[] point : points) {
                double current = Math.abs(MathHelper.wrapAngleTo180_float(yawToPoint(target, point[0], point[1])));
                double bestSoFar = best == null ? 0 : Math.abs(MathHelper.wrapAngleTo180_float(yawToPoint(target, best[0], best[1])));
                if (current >= bestSoFar && current > 150) best = point;
            }
            if (best != null) return new double[]{target.posX + best[0], target.posZ + best[1]};
        }

        double[] last = points.get(points.size() - 1);
        return new double[]{target.posX + last[0], target.posZ + last[1]};
    }

    private void addIfValid(List<double[]> valid, AxisAlignedBB box, Entity target, double slid, double cos, double sin,
                            double motionY, boolean flying) {
        double slidX = slid * cos, slidZ = slid * sin;
        if (fits(box, target, slidX, slidZ, motionY) && !overVoid(target, slidX, slidZ, flying)) {
            valid.add(new double[]{slidX, slidZ});
        }
    }

    private boolean fits(AxisAlignedBB box, Entity target, double rotX, double rotZ, double motionY) {
        double diffX = (target.posX + rotX) - mc.thePlayer.posX;
        double diffZ = (target.posZ + rotZ) - mc.thePlayer.posZ;

        AxisAlignedBB moved = box.offset(diffX, 0, diffZ);
        double correctedY = motionY;
        for (AxisAlignedBB other : mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, moved.addCoord(0, correctedY, 0))) {
            correctedY = other.calculateYOffset(moved, correctedY);
        }
        if (correctedY != motionY) correctedY += 0.1D;

        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.offset(diffX, correctedY, diffZ).expand(ROOM, 0, ROOM)).isEmpty();
    }

    private static boolean overVoid(Entity target, double rotX, double rotZ, boolean flying) {
        return !flying && !groundBelow(target.posX + rotX, (int) target.posY, target.posZ + rotZ);
    }

    private static boolean groundBelow(double x, int fromY, double z) {
        for (int y = fromY; y >= 0; y--) {
            if (!(mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BlockAir)) return true;
        }
        return false;
    }

    private static boolean isFlying() {
        FlightModule flight = Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class);
        LongJumpModule longJump = Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class);
        return flight != null && flight.isEnabled() || longJump != null && longJump.isEnabled();
    }

    private float yawToPoint(Entity target, double posX, double posZ) {
        double yawToEntity;
        if (posZ < 0.0D && posX < 0.0D) {
            yawToEntity = 90.0D + Math.toDegrees(Math.atan(posZ / posX));
        } else if (posZ < 0.0D && posX > 0.0D) {
            yawToEntity = -90.0D + Math.toDegrees(Math.atan(posZ / posX));
        } else {
            yawToEntity = Math.toDegrees(-Math.atan(posX / posZ));
        }

        float lastDelta = MathHelper.clamp_float((target.rotationYaw - target.prevRotationYaw) * 6F, -90, 90);
        return MathHelper.wrapAngleTo180_float(-((target.rotationYaw + lastDelta + offset.getValue().floatValue()) - (float) yawToEntity));
    }

    public static float inputOffset(float forward, float strafe) {
        if (forward > 0) return strafe > 0 ? -45 : strafe < 0 ? 45 : 0;
        if (forward < 0) return strafe > 0 ? 225 : strafe < 0 ? 135 : 180;
        return strafe > 0 ? -90 : strafe < 0 ? 90 : 0;
    }
}
