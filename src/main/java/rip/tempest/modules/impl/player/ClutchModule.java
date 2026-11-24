package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.ClickEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.InventoryUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.material.Material;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Clutch", category = ModuleCategory.PLAYER)
public final class ClutchModule extends Module {
    private final ModeProperty<Trigger> trigger = new ModeProperty<>("Trigger", Trigger.Knockback);

    private final NumberProperty minimumFall = new NumberProperty("Minimum Fall", 10, 3, 20, 1);
    private final NumberProperty reach = new NumberProperty("Reach", 4.5, 0.5, 4.5, 0.1);

    private final NumberProperty rotationSpeed = new NumberProperty("Rotation Speed", 40, 5, 180, 5);

    private final NumberProperty rotationTolerance = new NumberProperty("Rotation Tolerance", 25, 20, 100, 1);

    private final NumberProperty maxBlocks = new NumberProperty("Max Blocks", 10, 0, 20, 1);

    private final Property<Boolean> predict = new Property<>("Predict Position", true);
    private final Property<Boolean> moveFix = new Property<>("Move Fix", true);

    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = {{-HALF_WIDTH, -HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH}, {-HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, HALF_WIDTH}};

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("log2", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    private static boolean clutching;

    private boolean armed, active, landed;
    private int checkTicks, landedTick;
    private int lastHurtTime = -1;

    private boolean aiming, placing;
    private float aimYaw, aimPitch;
    private BlockPos targetPos;
    private EnumFacing targetSide;
    private BlockPos lastPlaced;
    private int blocksPlaced;
    private int previousSlot = -1;
    private boolean swappedSlot;

    public static boolean isClutching() {
        return clutching;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetTrigger();
        lastHurtTime = -1;
        aiming = placing = false;
        blocksPlaced = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.thePlayer != null) stop();
        resetTrigger();
        clutching = false;
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        resetTrigger();
        aiming = placing = swappedSlot = false;
        previousSlot = -1;
        targetPos = lastPlaced = null;
        clutching = false;
    };

    @EventLink(Priorities.HIGH)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        setSuffix(trigger.getValue().toString());

        if (mc.thePlayer.onGround) blocksPlaced = 0;
        updateTrigger();

        if (!active || mc.currentScreen != null) {
            stop();
            return;
        }

        boolean canPlace = false;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));

        if (canPlaceThrough(below)) {
            int slot = pickBlockSlot();
            if (slot != -1) {
                Aim aim = findAim(mc.thePlayer.inventory.mainInventory[slot]);
                if (aim != null) {
                    targetPos = aim.hit.getBlockPos();
                    targetSide = aim.hit.sideHit;
                    aimYaw = aim.yaw;
                    aimPitch = aim.pitch;
                    aiming = true;
                }

                if (aiming) {
                    if (!placing && !swappedSlot) previousSlot = mc.thePlayer.inventory.currentItem;
                    placing = true;
                    canPlace = true;

                    if (slot != mc.thePlayer.inventory.currentItem) {
                        mc.thePlayer.inventory.currentItem = slot;
                        swappedSlot = true;
                    }
                }
            }
        }

        if (!canPlace) {
            placing = false;
            if (!aiming) restoreSlot();
        }

        clutching = aiming;
        if (!aiming) return;

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);

        RotationProcess.setRotations(new Vector2f(aimYaw, aimPitch), rotationSpeed.getValue() / 36.0,
                moveFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);

        if (placing) tryPlace();
    };

    @EventLink
    public final Listener<ClickEvent> clickEventListener = event -> {
        if (clutching) event.setCancelled();
    };

    @EventLink
    public final Listener<RightClickEvent> rightClickEventListener = event -> {
        if (clutching) event.setCancelled();
    };

    private void tryPlace() {
        Vector2f view = RotationProcess.rotations;
        if (view == null || targetPos == null) return;

        MovingObjectPosition hit = rayTraceBlock(reach.getValue(), view.x, view.y);
        if (hit == null || !targetPos.equals(hit.getBlockPos()) || hit.sideHit != targetSide) return;

        int limit = maxBlocks.getValue().intValue();
        if (limit != 0 && blocksPlaced >= limit) return;

        Vector2f sent = mc.thePlayer.getPreviousRotation();
        double tolerance = rotationTolerance.getValue();
        if (Math.abs(MathHelper.wrapAngleTo180_float(view.x - sent.x)) > tolerance || Math.abs(view.y - sent.y) > tolerance) return;

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(),
                hit.getBlockPos(), hit.sideHit, hit.hitVec)) {
            if (hit.sideHit != EnumFacing.UP) blocksPlaced++;
            lastPlaced = hit.getBlockPos();
            mc.thePlayer.swingItem();
        }
    }

    private void stop() {
        restoreSlot();
        targetPos = null;
        targetSide = null;
        lastPlaced = null;
        blocksPlaced = 0;
        placing = false;
        previousSlot = -1;

        if (aiming) {
            aiming = false;
            if (mc.currentScreen == null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem));
            }
        }
        clutching = false;
    }

    private void restoreSlot() {
        if (swappedSlot && previousSlot != -1 && previousSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        swappedSlot = false;
    }

    private void resetTrigger() {
        armed = active = landed = false;
        checkTicks = 0;
    }

    private void updateTrigger() {
        int ticks = mc.thePlayer.ticksExisted;
        int hurtTime = mc.thePlayer.hurtTime;

        boolean startedFalling = trigger.getValue() == Trigger.AnyFall && !mc.thePlayer.onGround && mc.thePlayer.motionY < 0 && !armed;
        if (hurtTime > lastHurtTime || startedFalling) {
            armed = true;
            checkTicks = 0;
            landed = false;
        }
        lastHurtTime = hurtTime;

        if (armed && !active && !landed) {
            if (checkTicks % 3 == 0 && willFallFar(minimumFall.getValue())) active = true;
            checkTicks++;
        }

        if (landed) {
            boolean expired = ticks - landedTick >= 10;
            boolean jumped = mc.gameSettings.keyBindJump.isKeyDown();
            boolean rising = !mc.thePlayer.onGround && mc.thePlayer.motionY > 0;
            if (expired || jumped || rising) resetTrigger();
        }

        if (active && mc.thePlayer.onGround && hurtTime < mc.thePlayer.maxHurtTime - 2 && !landed) {
            landed = true;
            landedTick = ticks;
            if (!willFallSoon()) resetTrigger();
        }

        if (!active && !landed && mc.thePlayer.onGround && hurtTime == 0) {
            armed = false;
            checkTicks = 0;
        }
    }

    private boolean willFallFar(double minimum) {
        double startY = mc.thePlayer.posY;
        Prediction prediction = Prediction.fromPlayer();
        for (int tick = 0; tick < 60; tick++) {
            prediction.tick(false);
            if (prediction.onGround) return false;
            if (startY - prediction.posY > minimum) return true;
        }
        return false;
    }

    private boolean willFallSoon() {
        Prediction prediction = Prediction.fromPlayer();
        for (int tick = 0; tick < 10; tick++) {
            prediction.tick(true);
            if (!prediction.onGround && prediction.motionY < 0) return true;
        }
        return false;
    }

    private Aim findAim(ItemStack held) {
        Vec3 position = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);

        Vec3 future = position;
        if (predict.getValue()) {
            Prediction prediction = Prediction.fromPlayer();
            for (int tick = 0; tick < 20; tick++) {
                prediction.tick(false);
                if (prediction.posY < position.yCoord - 2 || prediction.onGround) break;
            }
            future = prediction.position();
        }

        int feetX = MathHelper.floor_double(position.xCoord);
        int feetY = MathHelper.floor_double(position.yCoord);
        int feetZ = MathHelper.floor_double(position.zCoord);

        List<Candidate> candidates = new ArrayList<>();
        for (int y = feetY - 1; y >= feetY - 4; y--) {
            for (int x = feetX - 5; x <= feetX + 4; x++) {
                for (int z = feetZ - 5; z <= feetZ + 4; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (canPlaceThrough(pos)) continue;

                    double now = distanceSquared(position, pos);
                    double score = predict.getValue() ? now * 0.3 + distanceSquared(future, pos) * 0.7 : now;

                    if (pos.equals(lastPlaced)) score *= 0.95;
                    candidates.add(new Candidate(score, pos));
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(a.score, b.score));

        for (Candidate candidate : candidates) {
            Aim aim = findAimAt(held, candidate.pos, eye, isUnderPlayer(candidate.pos, position));
            if (aim != null) return aim;
        }
        return null;
    }

    private boolean isUnderPlayer(BlockPos pos, Vec3 position) {
        if (pos.getY() >= MathHelper.floor_double(position.yCoord)) return false;
        for (double[] corner : CORNERS) {
            if (pos.getX() == MathHelper.floor_double(position.xCoord + corner[0])
                    && pos.getZ() == MathHelper.floor_double(position.zCoord + corner[1])) return true;
        }
        return false;
    }

    private Aim findAimAt(ItemStack held, BlockPos cell, Vec3 eye, boolean underPlayer) {
        double inset = 0.05, step = 0.2, jitter = step * 0.1;
        boolean southFace = Math.abs(eye.zCoord - (cell.getZ() + 1)) < Math.abs(eye.zCoord - cell.getZ());
        boolean eastFace = Math.abs(eye.xCoord - (cell.getX() + 1)) < Math.abs(eye.xCoord - cell.getX());

        Vector2f sent = mc.thePlayer.getPreviousRotation();
        float baseYaw = MathHelper.wrapAngleTo180_float(sent.x), basePitch = sent.y;
        int points = (int) Math.round(1 / step);

        List<float[]> views = new ArrayList<>();

        views.add(new float[]{0f, baseYaw, basePitch});

        for (int row = 0; row <= points; row++) {
            double v = clamp01(row * step + random(-jitter, jitter));
            for (int column = 0; column <= points; column++) {
                double u = clamp01(column * step + random(-jitter, jitter));

                if (underPlayer) addView(views, eye, cell.getX() + u, cell.getY() + 1 - inset, cell.getZ() + v, baseYaw, basePitch);
                addView(views, eye, cell.getX() + u, cell.getY() + v, southFace ? cell.getZ() + 1 - inset : cell.getZ() + inset, baseYaw, basePitch);
                addView(views, eye, eastFace ? cell.getX() + 1 - inset : cell.getX() + inset, cell.getY() + v, cell.getZ() + u, baseYaw, basePitch);
            }
        }
        views.sort((a, b) -> Float.compare(a[0], b[0]));

        for (float[] view : views) {
            float yaw = sent.x + MathHelper.wrapAngleTo180_float(view[1] - sent.x);
            MovingObjectPosition hit = rayTraceBlock(reach.getValue(), yaw, view[2]);
            if (hit == null || !cell.equals(hit.getBlockPos())) continue;

            EnumFacing face = hit.sideHit;

            if (face == EnumFacing.DOWN || (face == EnumFacing.UP && !underPlayer)) continue;
            if (!((ItemBlock) held.getItem()).canPlaceBlockOnSide(mc.theWorld, hit.getBlockPos(), face, mc.thePlayer, held)) continue;

            return new Aim(hit, yaw, view[2]);
        }
        return null;
    }

    private static void addView(List<float[]> views, Vec3 eye, double x, double y, double z, float baseYaw, float basePitch) {
        double dx = x - eye.xCoord, dy = y - eye.yCoord, dz = z - eye.zCoord;
        float yaw = MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(dz, dx)) - 90F);
        float pitch = MathHelper.clamp_float((float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90F, 90F);
        float cost = Math.abs(MathHelper.wrapAngleTo180_float(yaw - baseYaw)) + Math.abs(pitch - basePitch);
        views.add(new float[]{cost, yaw, pitch});
    }

    private static MovingObjectPosition rayTraceBlock(double distance, float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLookCustom(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
        return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK ? null : hit;
    }

    private int pickBlockSlot() {
        int current = mc.thePlayer.inventory.currentItem;
        if (!swappedSlot && isUsableBlock(current)) return current;

        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int slot = 8; slot >= 0; slot--) {
            if (!isUsableBlock(slot)) continue;

            Block block = ((ItemBlock) mc.thePlayer.inventory.mainInventory[slot].getItem()).getBlock();
            ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            Integer known = id == null ? null : BLOCK_SCORE.get(id.getResourcePath());
            int score = known == null ? -1 : known;

            if (score > bestScore || (score == bestScore && slot == current)) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private boolean isUsableBlock(int slot) {
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return false;

        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return (block.isFullBlock() || block instanceof BlockGlass || block instanceof BlockStainedGlass)
                && !InventoryUtils.blacklist.contains(block);
    }

    private static boolean canPlaceThrough(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Material material = block.getMaterial();
        return material == Material.air || material == Material.water || material == Material.lava || block == Blocks.fire;
    }

    private static double distanceSquared(Vec3 point, BlockPos block) {
        double dx = point.xCoord - Math.max(block.getX(), Math.min(block.getX() + 1, point.xCoord));
        double dy = point.yCoord - Math.max(block.getY(), Math.min(block.getY() + 1, point.yCoord));
        double dz = point.zCoord - Math.max(block.getZ(), Math.min(block.getZ() + 1, point.zCoord));
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : value > 1 ? 1 : value;
    }

    private static double random(double min, double max) {
        return min + Math.random() * (max - min);
    }

    public enum Trigger {
        Knockback("Knockback"),
        AnyFall("Any Fall");

        private final String display;

        Trigger(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final class Candidate {
        final double score;
        final BlockPos pos;

        Candidate(double score, BlockPos pos) {
            this.score = score;
            this.pos = pos;
        }
    }

    private static final class Aim {
        final MovingObjectPosition hit;
        final float yaw, pitch;

        Aim(MovingObjectPosition hit, float yaw, float pitch) {
            this.hit = hit;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Prediction {
        private AxisAlignedBB box;
        private double motionX, motionY, motionZ;
        private double posY;
        private boolean onGround;

        static Prediction fromPlayer() {
            Prediction prediction = new Prediction();
            prediction.box = mc.thePlayer.getEntityBoundingBox();
            prediction.motionX = mc.thePlayer.motionX;
            prediction.motionY = mc.thePlayer.motionY;
            prediction.motionZ = mc.thePlayer.motionZ;
            prediction.posY = mc.thePlayer.posY;
            prediction.onGround = mc.thePlayer.onGround;
            return prediction;
        }

        Vec3 position() {
            return new Vec3((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
        }

        void tick(boolean standStill) {
            if (standStill) {
                motionX = 0.0;
                motionZ = 0.0;
            }

            motionY -= 0.08;
            move(motionX, motionY, motionZ);
            motionY *= 0.9800000190734863;
            motionX *= 0.91;
            motionZ *= 0.91;
        }

        private void move(double x, double y, double z) {
            double wantedX = x, wantedY = y, wantedZ = z;

            List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(x, y, z));
            for (AxisAlignedBB collision : collisions) y = collision.calculateYOffset(box, y);
            box = box.offset(0.0, y, 0.0);

            for (AxisAlignedBB collision : collisions) x = collision.calculateXOffset(box, x);
            box = box.offset(x, 0.0, 0.0);

            for (AxisAlignedBB collision : collisions) z = collision.calculateZOffset(box, z);
            box = box.offset(0.0, 0.0, z);

            onGround = wantedY != y && wantedY < 0.0;
            posY = box.minY;

            if (wantedX != x) motionX = 0.0;
            if (wantedY != y) motionY = 0.0;
            if (wantedZ != z) motionZ = 0.0;
        }
    }
}
