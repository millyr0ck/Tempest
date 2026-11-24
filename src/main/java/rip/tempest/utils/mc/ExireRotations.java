package rip.tempest.utils.mc;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.util.vector.Vector2f;
import rip.tempest.processes.RotationProcess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class ExireRotations {
    public enum Mode {
        Normal("Normal"),
        Backwards("Backwards"),
        Angle("Angle"),
        InvalidPitch("Invalid Pitch"),
        Smart("Smart"),
        Hypixel("Hypixel");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private ExireRotations() {
    }

    public static void compute(Mode mode, BlockPos pos, EnumFacing face, float[] target) {
        boolean hasBlock = pos != null && face != null;
        float moveYaw = moveKeysYaw();
        Vector2f rotation = null;

        switch (mode) {
            case Normal -> {
                if (hasBlock) rotation = aimAtBlock(pos, face);
            }
            case Backwards -> {
                if (hasBlock && pos.getY() >= mc.thePlayer.posY) {
                    float pitch = pitch(pos, face, mc.thePlayer.rotationYaw - 180.0F);
                    Vector2f found = bruteforce(pos, face, true, false, moveYaw);
                    rotation = new Vector2f((found != null ? found : aimAtBlock(pos, face)).x, pitch);
                } else {
                    rotation = new Vector2f(moveYaw - 180.0F, 79.5F);
                }
            }
            case Angle -> {
                if (hasBlock) {
                    float yaw = faceYaw(face, moveYaw);
                    rotation = new Vector2f(yaw, pitch(pos, face, yaw));
                }
            }
            case InvalidPitch -> {
                if (hasBlock) rotation = orAim(bruteforce(pos, face, true, true, moveYaw), pos, face);
            }
            case Smart, Hypixel -> {
                if (!hasBlock) {
                    float pitch = (float) (80.0 + Math.round(Math.random() * 0.2 * 100.0) / 100.0);
                    rotation = new Vector2f(moveYaw - 180.0F, pitch);
                } else {
                    rotation = orAim(bruteforce(pos, face, mode == Mode.Smart, false, moveYaw), pos, face);
                }
            }
        }

        if (rotation == null) return;
        target[0] = rotation.x;
        target[1] = MathHelper.clamp_float(Math.round(rotation.y * 1000.0F) / 1000.0F, -90.0F, 90.0F);
    }

    private static Vector2f bruteforce(BlockPos pos, EnumFacing face, boolean strict, boolean invalidPitch, float moveYaw) {
        for (float yaw = moveYaw - 180.0F; yaw <= moveYaw + 180.0F; yaw += 45.0F) {
            Float pitch = bruteforcePitch(pos, face, yaw, strict, invalidPitch);
            if (pitch != null) return new Vector2f(yaw, pitch);
        }
        return null;
    }

    private static Float bruteforcePitch(BlockPos pos, EnumFacing face, float yaw, boolean strict, boolean invalidPitch) {
        List<Float> pitches = new ArrayList<>();
        float limit = invalidPitch ? 180.0F : 90.0F;
        for (float pitch = 0.0F; pitch < limit; pitch += 0.5F) {
            if (RayCastUtils.overBlock(new Vector2f(yaw, pitch), face, pos, strict)) pitches.add(pitch);
        }
        if (pitches.isEmpty()) return null;
        Collections.sort(pitches);
        int size = pitches.size();
        return size % 2 == 1 ? pitches.get(size / 2) : (pitches.get(size / 2 - 1) + pitches.get(size / 2)) / 2.0F;
    }

    private static float pitch(BlockPos pos, EnumFacing face, float yaw) {
        Float pitch = bruteforcePitch(pos, face, yaw, true, false);
        return pitch != null ? pitch : 78.5F;
    }

    private static Vector2f orAim(Vector2f found, BlockPos pos, EnumFacing face) {
        return found != null ? found : aimAtBlock(pos, face);
    }

    private static Vector2f aimAtBlock(BlockPos pos, EnumFacing face) {
        double x = pos.getX() + 0.5 - mc.thePlayer.posX + face.getFrontOffsetX() / 2.0;
        double z = pos.getZ() + 0.5 - mc.thePlayer.posZ + face.getFrontOffsetZ() / 2.0;
        double y = pos.getY() + 0.5;
        double below = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y;
        double distance = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        float pitch = (float) Math.toDegrees(Math.atan2(below, distance));
        float last = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations.x : mc.thePlayer.rotationYaw;
        return new Vector2f(last + MathHelper.wrapAngleTo180_float(yaw - last), pitch);
    }

    private static float faceYaw(EnumFacing face, float moveYaw) {
        return switch (face) {
            case NORTH -> 0.0F;
            case SOUTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> -90.0F;
            default -> moveYaw - 180.0F;
        };
    }

    private static float moveKeysYaw() {
        float yaw = mc.thePlayer.rotationYaw;
        int forwardKeys = 0, strafeKeys = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) forwardKeys++;
        if (mc.gameSettings.keyBindBack.isKeyDown()) forwardKeys--;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) strafeKeys++;
        if (mc.gameSettings.keyBindRight.isKeyDown()) strafeKeys--;
        if (forwardKeys < 0) yaw += 180.0F;
        float forward = forwardKeys < 0 ? -0.5F : forwardKeys > 0 ? 0.5F : 1.0F;
        if (strafeKeys > 0) yaw -= 90.0F * forward;
        else if (strafeKeys < 0) yaw += 90.0F * forward;
        return yaw;
    }
}
