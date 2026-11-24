package rip.tempest.modules.impl.player.bednuker;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.api.events.impl.game.ClickEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;
import java.util.function.Supplier;

import static rip.tempest.utils.Util.mc;

public final class JadeBedNuker extends SubMode {
    private static JadeBedNuker active;

    private NumberProperty breakSpeed, range, fov;
    private Property<Boolean> ignoreFatigue, groundSpoof, blockZapper, prioritizeKillAura, switchBack, renderOutline, movementFix;
    private Supplier<BlockPos> spawn;

    private final List<BlockPos[]> beds = new ArrayList<>();
    private int tick, aimedTick = -1;
    private BlockPos target;
    private boolean nuking, holdingKeys;
    private int previousSlot = -1;
    private BlockPos fadeBlock;
    private float fadeFrom = -1;

    public JadeBedNuker(NumberProperty breakSpeed, Property<Boolean> ignoreFatigue, Property<Boolean> groundSpoof, NumberProperty range, NumberProperty fov,
                        Property<Boolean> blockZapper, Property<Boolean> prioritizeKillAura, Property<Boolean> switchBack, Property<Boolean> renderOutline,
                        Property<Boolean> movementFix, Supplier<BlockPos> spawn) {
        this.breakSpeed = breakSpeed;
        this.ignoreFatigue = ignoreFatigue;
        this.groundSpoof = groundSpoof;
        this.range = range;
        this.fov = fov;
        this.blockZapper = blockZapper;
        this.prioritizeKillAura = prioritizeKillAura;
        this.switchBack = switchBack;
        this.renderOutline = renderOutline;
        this.movementFix = movementFix;
        this.spawn = spawn;
    }

    public static boolean isNuking() {
        JadeBedNuker nuker = active;
        return nuker != null && nuker.nuking && nuker.breakable(nuker.target) && !nuker.prioritizeKillAura.getValue();
    }

    public static MovingObjectPosition pointedOverride() {
        JadeBedNuker nuker = active;
        if (nuker == null || !ready() || !nuker.aimHits()) return null;
        MovingObjectPosition hit = nuker.traceTarget();
        return hit == null ? null : new MovingObjectPosition(hit.hitVec, hit.sideHit, nuker.target);
    }

    @Override
    protected void onEnable() {
        active = this;
        tick = 0;
        reset(false);
    }

    @Override
    protected void onDisable() {
        reset(true);
        beds.clear();
        active = null;
    }

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        tick++;
        if (!ready() || mc.currentScreen != null || !canMine()) {
            reset(true);
            return;
        }
        if (prioritizeKillAura.getValue() && AuraModule.target() != null) {
            reset(true);
            return;
        }

        double reach = range.getValue(), reachSq = reach * reach;
        scan(reach + 1);
        if (beds.isEmpty()) {
            reset(true);
            return;
        }
        Target next = nuking && breakable(target) ? targetAt(target, reachSq) : null;
        if (next == null) next = bestTarget(reachSq);
        if (next == null) {
            reset(true);
            return;
        }

        target = next.pos;
        nuking = true;
        selectTool(block(target));

        float lastYaw = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations.x : mc.thePlayer.rotationYaw;
        float[] aim = aimAt(box(target), mc.thePlayer.getPositionEyes(1.0F), lastYaw);
        RotationProcess.setRotations(new Vector2f(aim[0], aim[1]), 10, movementFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
        aimedTick = tick;
        updateKeys();
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        if (!ready() || !aimHits()) return;
        PlayerControllerMP controller = mc.playerController;
        if (!controller.getIsHittingBlock() || !target.equals(controller.getCurrentBlock())) return;
        if (groundSpoof.getValue() && !mc.thePlayer.onGround && controller.curBlockDamageMP > 0) {
            controller.curBlockDamageMP += 4 * block(target).getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, target);
        }
        float speed = speedMultiplier();
        float head = speed > 1 ? 1 - 1 / speed : 0;
        if (controller.curBlockDamageMP > 0 && controller.curBlockDamageMP < head) controller.curBlockDamageMP = head;
    };

    @EventLink
    public final Listener<ClickEvent> onClick = e -> {
        if (aimedThisTick()) e.setCancelled();
    };

    @EventLink
    public final Listener<RightClickEvent> onRightClick = e -> {
        if (aimedThisTick() && !(blockZapper.getValue() && holdingZapper())) e.setCancelled();
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = e -> {
        if (!renderOutline.getValue() || !ready() || !nuking || !breakable(target)) return;
        AxisAlignedBB box = box(target);
        if (box == null) return;
        int color = outlineColor();
        double x = mc.getRenderManager().viewerPosX, y = mc.getRenderManager().viewerPosY, z = mc.getRenderManager().viewerPosZ;
        BedNukerRender.fill(box.offset(-x, -y, -z).expand(0.002, 0.002, 0.002), color);
    };

    private static final class Target {
        final BlockPos pos;
        final Vec3 hit;
        final EnumFacing side;

        Target(BlockPos pos, Vec3 hit, EnumFacing side) {
            this.pos = pos;
            this.hit = hit;
            this.side = side;
        }
    }

    private void scan(double reach) {
        beds.clear();
        Set<BlockPos> feet = new HashSet<>();
        BlockPos at = new BlockPos(mc.thePlayer);
        int radius = (int) Math.ceil(reach);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos[] bed = bedAt(at.add(x, y, z));
                    if (bed == null || feet.contains(bed[0]) || !bedInReach(bed, eyes, reach) || !inView(bed, eyes)) continue;
                    feet.add(bed[0]);
                    beds.add(bed);
                }
            }
        }

        BlockPos home = spawn.get();
        if (home != null && mc.thePlayer.getDistanceSq(home) <= 800 && !beds.isEmpty()) {
            Vec3 centre = new Vec3(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5);
            beds.remove(Collections.min(beds, Comparator.comparingDouble(bed -> centre.squareDistanceTo(middle(bed)))));
        }
    }

    private static BlockPos[] bedAt(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBed)) return null;
        EnumFacing facing = state.getValue(BlockBed.FACING);
        BlockPos foot = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT ? pos : pos.offset(facing.getOpposite());
        IBlockState footState = mc.theWorld.getBlockState(foot);
        if (!(footState.getBlock() instanceof BlockBed) || footState.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT) return null;
        EnumFacing footFacing = footState.getValue(BlockBed.FACING);
        BlockPos head = foot.offset(footFacing);
        IBlockState headState = mc.theWorld.getBlockState(head);
        return headState.getBlock() instanceof BlockBed && headState.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD
                && headState.getValue(BlockBed.FACING) == footFacing ? new BlockPos[]{foot, head} : null;
    }

    private static boolean bedInReach(BlockPos[] bed, Vec3 eyes, double reach) {
        AxisAlignedBB box = bedBox(bed);
        double limit = reach * reach + 1.0E-4;
        return eyes.squareDistanceTo(closest(box, eyes)) <= limit || eyes.squareDistanceTo(centre(box)) <= limit;
    }

    private boolean inView(BlockPos[] bed, Vec3 eyes) {
        float field = fov.getValue().floatValue();
        if (field >= 360) return true;
        Vec3 to = middle(bed).subtract(eyes);
        double length = to.lengthVector();
        if (length < 1.0E-6) return true;
        Vec3 look = mc.thePlayer.getLook(1.0F);
        double cos = (look.xCoord * to.xCoord + look.yCoord * to.yCoord + look.zCoord * to.zCoord) / length;
        return Math.toDegrees(Math.acos(MathHelper.clamp_double(cos, -1, 1))) <= field * 0.5;
    }

    private Target bestTarget(double reachSq) {
        List<BlockPos[]> open = new ArrayList<>(), covered = new ArrayList<>();
        for (BlockPos[] bed : beds) (hasAirBeside(bed) ? open : covered).add(bed);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Comparator<BlockPos[]> nearest = Comparator.comparingDouble(bed -> eyes.squareDistanceTo(middle(bed)));
        open.sort(nearest);
        covered.sort(nearest);
        Target best = bestIn(open, reachSq);
        return best != null ? best : bestIn(covered, reachSq);
    }

    private Target bestIn(List<BlockPos[]> list, double reachSq) {
        float damage = mc.playerController.curBlockDamageMP;
        BlockPos mining = mc.playerController.getCurrentBlock();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        for (BlockPos[] bed : list) {
            List<Target> targets = breakTargets(bed, reachSq);
            if (targets.isEmpty()) continue;
            Target best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (Target t : targets) {
                float speed = maxBreakSpeed(block(t.pos));
                if (speed <= 0) continue;
                double score = 1.0 / speed;

                if (damage > 0.02F && t.pos.equals(mining)) score -= damage * 12.0;
                score += eyes.squareDistanceTo(t.hit) * 0.002;
                if (score < bestScore) {
                    bestScore = score;
                    best = t;
                }
            }
            return best;
        }
        return null;
    }

    private Target targetAt(BlockPos pos, double reachSq) {
        for (BlockPos[] bed : beds) {
            for (Target t : breakTargets(bed, reachSq)) {
                if (pos.equals(t.pos)) return t;
            }
        }
        return null;
    }

    private List<Target> breakTargets(BlockPos[] bed, double reachSq) {
        List<Target> targets = new ArrayList<>();
        if (hasAirBeside(bed)) {
            addTarget(targets, bed[0], reachSq);
            addTarget(targets, bed[1], reachSq);
            return targets;
        }
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos part : bed) {
            for (EnumFacing face : EnumFacing.values()) {
                if (face == EnumFacing.DOWN) continue;
                BlockPos around = part.offset(face);
                Block block = block(around);
                if (seen.contains(around) || block == Blocks.air || block instanceof BlockBed || block.getBlockHardness(mc.theWorld, around) < 0 || !canBreak(block)) {
                    continue;
                }
                seen.add(around);
                addTarget(targets, around, reachSq);
            }
        }
        return targets;
    }

    private void addTarget(List<Target> targets, BlockPos pos, double reachSq) {
        Block block = block(pos);
        if (block == Blocks.air || block.getBlockHardness(mc.theWorld, pos) < 0 || !canBreak(block)) return;
        AxisAlignedBB box = box(pos);
        if (box == null) return;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 point = closest(box, eyes);
        if (eyes.squareDistanceTo(point) > reachSq + 0.001) return;
        Vec3 past = point.addVector((point.xCoord - eyes.xCoord) * 0.01, (point.yCoord - eyes.yCoord) * 0.01, (point.zCoord - eyes.zCoord) * 0.01);
        MovingObjectPosition hit = block.collisionRayTrace(mc.theWorld, pos, eyes, past);
        EnumFacing side = facingToward(pos, point);
        if (hit != null && hit.hitVec != null && hit.sideHit != null && pos.equals(hit.getBlockPos())) {
            point = hit.hitVec;
            side = hit.sideHit;
        }
        if (block instanceof BlockBed && side == EnumFacing.DOWN) return;
        targets.add(new Target(pos, point, side));
    }

    private static boolean hasAirBeside(BlockPos[] bed) {
        for (BlockPos part : bed) {
            for (EnumFacing face : EnumFacing.values()) {
                if (mc.theWorld.getBlockState(part.offset(face)).getBlock() == Blocks.air) return true;
            }
        }
        return false;
    }

    private boolean aimedThisTick() {
        return aimedTick == tick && nuking && breakable(target) && canMine() && mc.currentScreen == null;
    }

    private boolean aimHits() {
        return aimedThisTick() && traceTarget() != null;
    }

    private MovingObjectPosition traceTarget() {
        if (target == null) return null;
        AxisAlignedBB box = box(target);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vector2f now = RotationProcess.isActive() && RotationProcess.rotations != null ? RotationProcess.rotations
                : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        Vector2f sent = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations : now;
        if (trace(box, eyes, range.getValue(), sent.x, sent.y) == null) return null;
        return trace(box, eyes, range.getValue(), now.x, now.y);
    }

    private static MovingObjectPosition trace(AxisAlignedBB box, Vec3 eyes, double reach, float yaw, float pitch) {
        if (box == null || eyes == null || reach <= 0 || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        if (box.isVecInside(eyes)) return new MovingObjectPosition(eyes, EnumFacing.UP);
        float rad = (float) (Math.PI / 180.0);
        float cosPitch = -MathHelper.cos(-pitch * rad);
        Vec3 end = eyes.addVector(MathHelper.sin(-yaw * rad - (float) Math.PI) * cosPitch * reach, MathHelper.sin(-pitch * rad) * reach,
                MathHelper.cos(-yaw * rad - (float) Math.PI) * cosPitch * reach);
        return box.calculateIntercept(eyes, end);
    }

    private static float[] aimAt(AxisAlignedBB box, Vec3 eyes, float lastYaw) {
        double x = (box.minX + box.maxX) * 0.5 - eyes.xCoord, y = (box.minY + box.maxY) * 0.5 - eyes.yCoord, z = (box.minZ + box.maxZ) * 0.5 - eyes.zCoord;
        double flat = Math.sqrt(x * x + z * z);
        float yaw = flat < 1.0E-6 ? lastYaw : lastYaw + MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(z, x)) - 90.0F - lastYaw);
        float pitch = (float) -Math.toDegrees(Math.atan2(y, flat));
        return new float[]{yaw, pitch};
    }

    private void updateKeys() {
        if (!aimedThisTick()) {
            restoreKeys();
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        holdingKeys = true;
        if (aimHits()) {
            if (blockZapper.getValue() && holdingZapper()) KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            else KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
        }
    }

    private void restoreKeys() {
        if (!holdingKeys) return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem));
        holdingKeys = false;
    }

    private void reset(boolean restoreSlot) {
        nuking = false;
        aimedTick = -1;
        if (restoreSlot && switchBack.getValue() && previousSlot != -1 && ready()) mc.thePlayer.inventory.currentItem = previousSlot;
        if (ready()) restoreKeys();
        else holdingKeys = false;
        target = null;
        fadeBlock = null;
        fadeFrom = -1;
        previousSlot = -1;
    }

    private void selectTool(Block block) {
        int slot = blockZapper.getValue() ? zapperSlot() : -1;
        if (slot < 0) slot = bestToolSlot(block);
        if (slot < 0 || slot == mc.thePlayer.inventory.currentItem) return;
        if (previousSlot == -1) previousSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
    }

    private float speedMultiplier() {
        float speed = breakSpeed.getValue().floatValue();
        if (ignoreFatigue.getValue() && mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            ItemStack held = mc.thePlayer.getHeldItem();
            speed = held != null && (held.getItem() == Items.wooden_axe || held.getItem() == Items.wooden_pickaxe) ? 1.8F : 2.5F;
        }
        return Math.max(1.0F, speed);
    }

    private boolean canBreak(Block block) {
        return !needsPickaxe(block) || bestToolSlot(block) >= 0;
    }

    private static boolean needsPickaxe(Block block) {
        if (block == null || block instanceof BlockBed) return false;
        Material material = block.getMaterial();
        return material == Material.iron || material == Material.anvil || material == Material.rock;
    }

    private float maxBreakSpeed(Block block) {
        float best = needsPickaxe(block) ? 0 : breakSpeed(block, null);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (effective(stack, block)) best = Math.max(best, breakSpeed(block, stack));
        }
        return best;
    }

    private int bestToolSlot(Block block) {
        int best = -1;
        float bestSpeed = needsPickaxe(block) ? 0 : breakSpeed(block, null);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!effective(stack, block)) continue;
            float speed = breakSpeed(block, stack);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private static boolean effective(ItemStack stack, Block block) {
        return stack != null && (!needsPickaxe(block) || stack.getItem() instanceof ItemPickaxe);
    }

    private float breakSpeed(Block block, ItemStack stack) {
        float hardness = block.getBlockHardness(mc.theWorld, null);
        if (hardness < 0) return 0;
        float strength = stack == null ? 1.0F : stack.getItem().getStrVsBlock(stack, block);
        if (strength > 1.0F) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            if (efficiency > 0) strength += efficiency * efficiency + 1;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) strength *= 1.0F + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        if (!ignoreFatigue.getValue()) {
            if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
                switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                    case 0 -> strength *= 0.3F;
                    case 1 -> strength *= 0.09F;
                    case 2 -> strength *= 0.0027F;
                    default -> strength *= 8.1E-4F;
                }
            }
            if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) strength /= 5.0F;
            if (!mc.thePlayer.onGround && !groundSpoof.getValue()) strength /= 5.0F;
        }
        boolean harvest = block.getMaterial().isToolNotRequired() || stack != null && stack.canHarvestBlock(block);
        return strength / hardness / (harvest ? 30.0F : 100.0F);
    }

    private static int zapperSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isZapper(mc.thePlayer.inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    private static boolean holdingZapper() {
        return isZapper(mc.thePlayer.getHeldItem());
    }

    private static boolean isZapper(ItemStack stack) {
        return stack != null && stack.getItem() == Items.prismarine_shard && stack.hasDisplayName()
                && EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()).equals("Block Zapper");
    }

    private int outlineColor() {
        BlockPos mining = mc.playerController.getCurrentBlock();
        boolean miningIt = target.equals(mining);
        float progress = miningIt ? Math.max(0, Math.min(1, mc.playerController.curBlockDamageMP)) : 0;
        float fade;
        if (!miningIt) {
            if (!target.equals(fadeBlock)) {
                fadeBlock = null;
                fadeFrom = -1;
            }
            fade = 0;
        } else {
            if (!target.equals(fadeBlock) || fadeFrom < 0 || progress + 0.01F < fadeFrom) {
                fadeBlock = target;
                fadeFrom = Math.min(progress, 0.99F);
            }
            float left = 1 - fadeFrom;
            fade = left <= 0.001F ? 1 : Math.max(0, Math.min(1, (progress - fadeFrom) / left));
        }
        int r = Math.round(247 + (155 - 247) * fade), g = Math.round(82 + (255 - 82) * fade), b = Math.round(82 + (130 - 82) * fade);
        return 0x66000000 | r << 16 | g << 8 | b;
    }

    private boolean breakable(BlockPos pos) {
        if (pos == null || !ready()) return false;
        Block block = block(pos);
        return block != Blocks.air && block.getBlockHardness(mc.theWorld, pos) >= 0;
    }

    private static boolean canMine() {
        return mc.thePlayer != null && !mc.thePlayer.isDead && mc.thePlayer.getHealth() > 0 && mc.thePlayer.capabilities.allowEdit
                && !mc.thePlayer.capabilities.isCreativeMode && !mc.thePlayer.isSpectator();
    }

    private static Block block(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    private static AxisAlignedBB box(BlockPos pos) {
        Block block = block(pos);
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        return block.getSelectedBoundingBox(mc.theWorld, pos);
    }

    private static AxisAlignedBB bedBox(BlockPos[] bed) {
        AxisAlignedBB a = box(bed[0]), b = box(bed[1]);
        return new AxisAlignedBB(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }

    private static Vec3 middle(BlockPos[] bed) {
        return centre(bedBox(bed));
    }

    private static Vec3 centre(AxisAlignedBB box) {
        return new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
    }

    private static Vec3 closest(AxisAlignedBB box, Vec3 from) {
        return new Vec3(MathHelper.clamp_double(from.xCoord, box.minX, box.maxX), MathHelper.clamp_double(from.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(from.zCoord, box.minZ, box.maxZ));
    }

    private static EnumFacing facingToward(BlockPos pos, Vec3 point) {
        double x = point.xCoord - (pos.getX() + 0.5), y = point.yCoord - (pos.getY() + 0.5), z = point.zCoord - (pos.getZ() + 0.5);
        return EnumFacing.getFacingFromVector((float) x, (float) y, (float) z);
    }

    private static boolean ready() {
        return mc.thePlayer != null && mc.theWorld != null && mc.playerController != null;
    }
}
