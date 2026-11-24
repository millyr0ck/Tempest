package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.PlayerUtils;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.mc.RayCastUtils;
import rip.tempest.utils.misc.MovementFix;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Block In", category = ModuleCategory.PLAYER, description = "Encloses you in blocks to break beds safely")
public final class BlockInModule extends Module {
    private static final EnumFacing[] HORIZONTALS = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH
    };

    private static final int[][] SUPPORTS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}
    };
    private static final EnumFacing[] SUPPORT_FACES = {
            EnumFacing.DOWN, EnumFacing.UP, EnumFacing.SOUTH,
            EnumFacing.NORTH, EnumFacing.WEST, EnumFacing.EAST
    };

    private static final double REACH = 4.5;
    private static final double GRID_INSET = 0.05;
    private static final double GRID_STEP = 0.2;
    private static final int GRID_N = (int) Math.round(1.0 / GRID_STEP);

    public enum RotationMode {
        Silent("Silent"),
        Lock("Lock View");

        private final String name;

        RotationMode(String name) { this.name = name; }

        @Override
        public String toString() { return name; }
    }

    public final ModeProperty<RotationMode> rotationMode = new ModeProperty<>("Rotation Mode", RotationMode.Silent);
    public final NumberProperty speed = new NumberProperty("Rotation Speed", 10, 1, 30, 1);
    public final NumberProperty randomization = new NumberProperty("Randomization", 10, 0, 100, 1);
    public final NumberProperty rotationTol = new NumberProperty("Rotation Tolerance", 25, 5, 100, 1);
    public final Property<Boolean> roof = new Property<>("Place Roof", true);
    public final Property<Boolean> jumpForRoof = new Property<>("Jump For Roof", true, roof::getValue);
    public final Property<Boolean> autoDisable = new Property<>("Disable When Sealed", false);
    public final Property<Boolean> progressRing = new Property<>("Progress Ring", true);

    private boolean placing;
    private boolean slotSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;

    private BlockPos targetSupport;
    private EnumFacing targetFace;
    private BlockPos anchor;
    private long lastJumpAt;
    private float aimYaw, aimPitch;

    private int fillCount;
    private int lastFillCount = -1;
    private float ringProgress, ringStart, ringTarget;
    private long ringAnimAt;

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        stopPlacing();
        resetState();
        super.onDisable();
    }

    private void resetState() {
        targetSupport = null;
        targetFace = null;
        anchor = null;
        fillCount = 0;
        lastFillCount = -1;
        ringProgress = 0;
        ringTarget = 0;
    }

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen != null) { stopPlacing(); return; }

        countHoles();

        if (fillCount >= requiredCount()) {
            stopPlacing();
            if (autoDisable.getValue()) setEnabled(false);
            return;
        }

        plannedSlot = pickBlockSlot();
        if (plannedSlot == -1) { stopPlacing(); return; }

        targetSupport = null;
        targetFace = null;
        if (!selectTarget()) {
            long now = System.currentTimeMillis();
            if (jumpForRoof.getValue() && roof.getValue() && mc.thePlayer.onGround
                    && now - lastJumpAt > 600L
                    && sidesSealed() && PlayerUtils.isReplaceable(feet().up().up())) {
                lastJumpAt = now;
                mc.thePlayer.jump();
            }
            return;
        }

        if (!placing) startPlacing();
        equipPlannedSlot();

        Vector2f current = applyRotation();
        if (current == null) return;

        double tol = rotationTol.getValue();
        if (Math.abs(MathHelper.wrapAngleTo180_float(current.x - aimYaw)) > tol
                || Math.abs(current.y - aimPitch) > tol) {
            return;
        }

        MovingObjectPosition mop = RayCastUtils.rayCast(current, REACH);
        if (mop == null || mop.getBlockPos() == null || mop.hitVec == null) return;
        if (!mop.getBlockPos().equals(targetSupport) || mop.sideHit != targetFace) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                targetSupport, targetFace, mop.hitVec)) {
            mc.thePlayer.swingItem();
        }
    };

    private Vector2f applyRotation() {
        if (rotationMode.getValue() == RotationMode.Silent) {
            RotationProcess.setRotations(new Vector2f(aimYaw, aimPitch), speed.getValue(), MovementFix.NORMAL);
            return RotationProcess.rotations;
        }

        float step = (float) (speed.getValue() * 3.0);
        float curYaw = mc.thePlayer.rotationYaw;
        float curPitch = mc.thePlayer.rotationPitch;

        float dYaw = MathHelper.wrapAngleTo180_float(aimYaw - curYaw);
        float dPitch = aimPitch - curPitch;

        float newYaw = curYaw + clamp(dYaw, -step, step);
        float newPitch = clamp(curPitch + clamp(dPitch, -step, step), -90f, 90f);

        mc.thePlayer.rotationYaw = newYaw;
        mc.thePlayer.rotationPitch = newPitch;
        return new Vector2f(newYaw, newPitch);
    }

    private BlockPos feet() {
        if (anchor == null || mc.thePlayer.onGround) {
            anchor = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY),
                    MathHelper.floor_double(mc.thePlayer.posZ));
        }
        return anchor;
    }

    private int requiredCount() {
        return roof.getValue() ? 9 : 8;
    }

    private void countHoles() {
        BlockPos feet = feet();
        int filled = 0;

        if (roof.getValue() && !PlayerUtils.isReplaceable(feet.up().up())) filled++;
        for (EnumFacing dir : HORIZONTALS) {
            BlockPos side = feet.offset(dir);
            if (!PlayerUtils.isReplaceable(side)) filled++;
            if (!PlayerUtils.isReplaceable(side.up())) filled++;
        }

        fillCount = filled;
        if (fillCount != lastFillCount) {
            ringStart = ringProgress;
            ringTarget = Math.max(0f, Math.min(1f, fillCount / (float) requiredCount()));
            ringAnimAt = System.currentTimeMillis();
            lastFillCount = fillCount;
        }
    }

    private boolean sidesSealed() {
        BlockPos feet = feet();
        for (EnumFacing dir : HORIZONTALS) {
            BlockPos side = feet.offset(dir);
            if (PlayerUtils.isReplaceable(side) || PlayerUtils.isReplaceable(side.up())) return false;
        }
        return true;
    }

    private boolean intersectsPlayer(BlockPos p) {
        return mc.thePlayer.getEntityBoundingBox().intersectsWith(
                AxisAlignedBB.fromBounds(p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0));
    }

    private boolean hasSupport(BlockPos goal) {
        for (int[] off : SUPPORTS) {
            BlockPos support = goal.add(off[0], off[1], off[2]);
            if (PlayerUtils.isReplaceable(support)) continue;
            Block b = mc.theWorld.getBlockState(support).getBlock();
            if (PlayerUtils.isInteractable(b) || b instanceof BlockFence || b instanceof BlockWall) continue;
            return true;
        }
        return false;
    }

    private List<BlockPos> holes() {
        BlockPos feet = feet();
        List<BlockPos> out = new ArrayList<>(10);

        for (EnumFacing dir : HORIZONTALS) {
            BlockPos side = feet.offset(dir);
            if (PlayerUtils.isReplaceable(side)) out.add(side);
            if (PlayerUtils.isReplaceable(side.up())) out.add(side.up());
        }

        if (roof.getValue()) {
            BlockPos above = feet.up().up();
            if (PlayerUtils.isReplaceable(above)) {
                if (!hasSupport(above)) {
                    for (EnumFacing dir : HORIZONTALS) {
                        BlockPos step = above.offset(dir);
                        if (!PlayerUtils.isReplaceable(step) || !hasSupport(step)) continue;
                        out.add(step);
                        break;
                    }
                }
                out.add(above);
            }
        }
        return out;
    }

    private boolean selectTarget() {
        List<BlockPos> goals = holes();
        if (goals.isEmpty()) return false;

        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        ItemStack held = plannedSlot >= 0 && plannedSlot <= 8
                ? mc.thePlayer.inventory.mainInventory[plannedSlot] : null;
        if (held == null) return false;

        Vector2f server = rotationMode.getValue() == RotationMode.Silent && RotationProcess.rotations != null
                ? RotationProcess.rotations
                : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

        List<Candidate> candidates = new ArrayList<>();
        double jit = GRID_STEP * 0.1;
        double insetTop = 1 - GRID_INSET - 1e-3;
        double insetBot = GRID_INSET + 1e-3;

        for (BlockPos goal : goals) {
            if (intersectsPlayer(goal)) continue;
            for (int i = 0; i < SUPPORTS.length; i++) {
                int[] off = SUPPORTS[i];
                EnumFacing face = SUPPORT_FACES[i];
                BlockPos support = goal.add(off[0], off[1], off[2]);

                if (PlayerUtils.isReplaceable(support)) continue;
                Block block = mc.theWorld.getBlockState(support).getBlock();
                if (PlayerUtils.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) continue;

                if (support.distanceSqToCenter(eye.xCoord, eye.yCoord, eye.zCoord) > (REACH + 1) * (REACH + 1)) continue;

                double sx = support.getX(), sy = support.getY(), sz = support.getZ();

                for (int row = 0; row <= GRID_N; row++) {
                    boolean ltr = (row & 1) == 0;
                    double v = clamp01(row * GRID_STEP + jitter(jit));

                    for (int col = 0; col <= GRID_N; col++) {
                        double cu = clamp01(col * GRID_STEP + jitter(jit));
                        double u = ltr ? cu : 1.0 - cu;

                        double px, py, pz;
                        if (off[1] != 0) {
                            px = sx + u; pz = sz + v;
                            py = sy + (off[1] < 0 ? insetTop : insetBot);
                        } else if (off[2] != 0) {
                            px = sx + u; py = sy + v;
                            pz = sz + (off[2] < 0 ? insetTop : insetBot);
                        } else {
                            pz = sz + u; py = sy + v;
                            px = sx + (off[0] < 0 ? insetTop : insetBot);
                        }

                        Vector2f rot = RotationUtils.calculate(new Vec3(px, py, pz));
                        float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(rot.x - server.x));
                        float dPitch = Math.abs(rot.y - server.y);
                        candidates.add(new Candidate(dYaw + dPitch, rot, support, face, goal));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return false;

        candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (Candidate c : candidates) {
            MovingObjectPosition mop = RayCastUtils.rayCast(c.rotation, REACH);
            if (mop == null || mop.getBlockPos() == null) continue;
            if (!mop.getBlockPos().equals(c.support) || mop.sideHit != c.face) continue;
            if (!mop.getBlockPos().offset(mop.sideHit).equals(c.goal)) continue;

            targetSupport = c.support;
            targetFace = c.face;

            float r = randomization.getValue().floatValue() / 100f;
            aimYaw = c.rotation.x + (float) jitter(r * 1.5);
            aimPitch = c.rotation.y + (float) jitter(r * 1.0);
            return true;
        }
        return false;
    }

    private int pickBlockSlot() {
        int best = -1;
        int bestSize = 0;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[slot];
            if (s == null || s.stackSize == 0) continue;
            if (!(s.getItem() instanceof ItemBlock)) continue;
            Block block = ((ItemBlock) s.getItem()).getBlock();
            if (PlayerUtils.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) continue;
            if (s.stackSize > bestSize) { bestSize = s.stackSize; best = slot; }
        }
        return best;
    }

    private void startPlacing() {
        if (placing) return;
        placing = true;
        slotSwapped = false;
        prevSlot = mc.thePlayer.inventory.currentItem;
    }

    private void stopPlacing() {
        if (!placing) return;

        if (slotSwapped && prevSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = prevSlot;
        }
        placing = false;
        slotSwapped = false;
        prevSlot = -1;
        plannedSlot = -1;
    }

    private void equipPlannedSlot() {
        if (plannedSlot == -1) return;
        if (mc.thePlayer.inventory.currentItem != plannedSlot) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotSwapped = true;
        }
    }

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (!progressRing.getValue() || mc.thePlayer == null) return;
        setSuffix(fillCount + "/" + requiredCount());

        long elapsed = System.currentTimeMillis() - ringAnimAt;
        if (elapsed < 160L) {
            float t = elapsed / 160f;
            ringProgress = ringStart + (ringTarget - ringStart) * quadInOut(t);
        } else {
            ringProgress = ringTarget;
        }
        if (ringProgress <= 0.001f) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f;
        float cy = sr.getScaledHeight() / 2f;
        float radius = 11f, thickness = 2.5f;

        drawArc(cx, cy, radius, thickness, 0f, 360f, new Color(0, 0, 0, 120));

        boolean done = ringProgress >= 0.999f;
        Color c = done
                ? new Color(96, 214, 122)
                : blend(new Color(226, 88, 78), ColorProcess.getColor(), ringProgress);
        drawArc(cx, cy, radius, thickness, -90f, ringProgress * 360f, c);
    };

    private void drawArc(float cx, float cy, float radius, float thickness,
                         float startDeg, float sweepDeg, Color color) {
        float rIn = radius - thickness / 2f, rOut = radius + thickness / 2f;
        int steps = Math.max(2, (int) (Math.abs(sweepDeg) / 4f));

        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.color(
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * (i / (double) steps));
            double sin = Math.sin(a), cos = -Math.cos(a);
            org.lwjgl.opengl.GL11.glVertex2d(cx + sin * rIn, cy + cos * rIn);
            org.lwjgl.opengl.GL11.glVertex2d(cx + sin * rOut, cy + cos * rOut);
        }
        org.lwjgl.opengl.GL11.glEnd();

        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        RenderUtils.resetColor();
    }

    private static float quadInOut(float t) {
        return t < 0.5f ? 2f * t * t : -1f + (4f - 2f * t) * t;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private static double jitter(double range) {
        return range > 0 ? (Math.random() * 2 - 1) * range : 0;
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static final class Candidate {
        final double cost;
        final Vector2f rotation;
        final BlockPos support, goal;
        final EnumFacing face;

        Candidate(double cost, Vector2f rotation, BlockPos support, EnumFacing face, BlockPos goal) {
            this.cost = cost; this.rotation = rotation;
            this.support = support; this.face = face; this.goal = goal;
        }
    }
}
