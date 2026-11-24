package rip.tempest.modules.impl.player.bednuker;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.modules.impl.player.ClutchModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static rip.tempest.utils.Util.mc;

public final class B4BedNuker extends SubMode {
    public enum BreakMode {
        LEGIT("Legit"), INSTANT("Instant"), SWAP("Swap");

        private final String label;

        BreakMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static B4BedNuker active;

    private ModeProperty<BreakMode> breakMode;
    private NumberProperty breakSpeed, fov, range, rate;
    private Property<Boolean> allowKillAura, breakNearBlock, cancelKnockback, disableBreakEffects, disableRightClick, groundSpoof, ignoreSlow,
            maximizeSpeed, onlyWhileVisible, prioritizeKillAura, renderOutline, sendAnimations, silentSwing, skipVerificationSetting, movementFix;
    private Predicate<BlockPos> ownBed;

    private BlockPos target, nearBlock, nearBed, quickBlock;
    private boolean targetIsBed, skipVerification, sentSwing;
    private float progress, progressPerTick, vanillaProgress;
    private int rotateTick = -1, quickTicks, swapBack = -1, serverSlot = -1;
    private long waitTicks, counter, rateCounter;
    private C07PacketPlayerDigging.Action lastAction;

    private final Map<BlockPos, BlockPos> chosen = new HashMap<>();

    public B4BedNuker(ModeProperty<BreakMode> breakMode, NumberProperty breakSpeed, NumberProperty fov, NumberProperty range, NumberProperty rate,
                      Property<Boolean> allowKillAura, Property<Boolean> breakNearBlock, Property<Boolean> cancelKnockback,
                      Property<Boolean> disableBreakEffects, Property<Boolean> disableRightClick, Property<Boolean> groundSpoof,
                      Property<Boolean> ignoreSlow, Property<Boolean> maximizeSpeed, Property<Boolean> onlyWhileVisible,
                      Property<Boolean> prioritizeKillAura, Property<Boolean> renderOutline, Property<Boolean> sendAnimations,
                      Property<Boolean> silentSwing, Property<Boolean> skipVerificationSetting, Property<Boolean> movementFix,
                      Predicate<BlockPos> ownBed) {
        this.breakMode = breakMode;
        this.breakSpeed = breakSpeed;
        this.fov = fov;
        this.range = range;
        this.rate = rate;
        this.allowKillAura = allowKillAura;
        this.breakNearBlock = breakNearBlock;
        this.cancelKnockback = cancelKnockback;
        this.disableBreakEffects = disableBreakEffects;
        this.disableRightClick = disableRightClick;
        this.groundSpoof = groundSpoof;
        this.ignoreSlow = ignoreSlow;
        this.maximizeSpeed = maximizeSpeed;
        this.onlyWhileVisible = onlyWhileVisible;
        this.prioritizeKillAura = prioritizeKillAura;
        this.renderOutline = renderOutline;
        this.sendAnimations = sendAnimations;
        this.silentSwing = silentSwing;
        this.skipVerificationSetting = skipVerificationSetting;
        this.movementFix = movementFix;
        this.ownBed = ownBed;

        breakMode.addValueChangeListener((was, now) -> {
            if (active == this) reset();
        });
    }

    public static boolean holdsKillAura() {
        B4BedNuker nuker = active;
        if (nuker == null || nuker.target == null && nuker.nearBlock == null) return false;
        if (nuker.prioritizeKillAura.getValue() && AuraModule.target() != null) return false;
        if (!nuker.allowKillAura.getValue()) return true;

        return nuker.breakMode.getValue() == BreakMode.SWAP
                && (nuker.rotateTick != -1 || nuker.progress > 0 && nuker.progress + nuker.progressPerTick >= 1);
    }

    @Override
    protected void onEnable() {
        active = this;
        reset();
        serverSlot = inGame() ? mc.thePlayer.inventory.currentItem : -1;
    }

    @Override
    protected void onDisable() {
        if (target != null && inGame()) {
            swapBackNow();
            if (lastAction == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) abort(target);
        }
        reset();
        active = null;
    }

    private void reset() {
        clear();
        counter = waitTicks = rateCounter = 0;
        quickBlock = nearBed = nearBlock = null;
        rotateTick = -1;
        quickTicks = 0;
        swapBack = -1;
        progress = vanillaProgress = 0;
        skipVerification = false;
        targetIsBed = false;
        lastAction = null;
        chosen.clear();
    }

    private void clear() {
        quickBlock = null;
        nearBlock = null;
        target = null;
        quickTicks = 0;
        counter = 0;
        vanillaProgress = 0;
        progress = 0;
        skipVerification = false;
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> {
        chosen.clear();
        clear();
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        sentSwing = false;
        if (!inGame()) return;
        BlockPos aimAt = rotationTarget();
        if (aimAt != null) {
            float[] rot = toBlock(aimAt);
            RotationProcess.setRotations(new Vector2f(rot[0], rot[1]), 10, movementFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (e.isPre() && inGame()) run(e);
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (e.getPacket() instanceof C0APacketAnimation) {
            sentSwing = true;
        } else if (e.getPacket() instanceof C09PacketHeldItemChange change) {
            serverSlot = change.getSlotId();
        } else if (e.getPacket() instanceof C07PacketPlayerDigging digging && target != null
                && digging.getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!cancelKnockback.getValue() || target == null || !inGame()) return;
        if (e.getPacket() instanceof S12PacketEntityVelocity velocity && velocity.getEntityID() == mc.thePlayer.getEntityId()
                || e.getPacket() instanceof S27PacketExplosion) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<RightClickEvent> onRightClick = e -> {
        if (disableRightClick.getValue() && target != null && mc.currentScreen == null) e.setCancelled();
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = e -> {
        if (!renderOutline.getValue() || target == null || !inGame()) return;
        double x = target.getX() - mc.getRenderManager().viewerPosX, y = target.getY() - mc.getRenderManager().viewerPosY,
                z = target.getZ() - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = new AxisAlignedBB(x, y, z, x + 1, y + (targetIsBed ? 0.5625 : 1), z + 1);
        int rgb = ColorProcess.getColor().getRGB() & 0xFFFFFF;
        BedNukerRender.fill(box, 0x40000000 | rgb);
        BedNukerRender.outline(box, 0xC0000000 | rgb, 1.5F);
    };

    private void run(MotionEvent e) {
        if (!mc.thePlayer.capabilities.allowEdit) return;
        if (mc.currentScreen != null || Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled() || ClutchModule.isClutching()
                || prioritizeKillAura.getValue() && AuraModule.target() != null) {
            if (target != null) {
                if (progress != 0) abort(target);
                clear();
            }
            return;
        }

        if (quickBlock != null) {
            if (mc.thePlayer.capabilities.isCreativeMode) {
                quickBlock = null;
            } else {
                if (quickTicks > 0) {
                    quickTicks--;
                    if (sendAnimations.getValue()) swing();
                    if (breakMode.getValue() == BreakMode.SWAP && quickTicks == 1) rotateTick = 6;
                } else {
                    stop(quickBlock);
                    quickBlock = null;
                    if (groundSpoof.getValue() && !e.isOnGround()) e.setOnGround(true);
                    if (breakMode.getValue() == BreakMode.SWAP) rotateTick = -1;
                    else waitTicks = 2;
                }
                return;
            }
        }
        tick(e);
        swapTicks();
    }

    private void tick(MotionEvent e) {
        if (waitTicks > 0 && --waitTicks != 0) {
            if (waitTicks == 1 && breakMode.getValue() == BreakMode.INSTANT && lastAction == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) swapBackNow();
            return;
        }

        BlockPos pos = target;
        if (pos != null) {
            boolean air = block(pos) == Blocks.air;
            if (breakNearBlock.getValue() && !air && !(block(pos) instanceof BlockBed) && nearBed != null && block(nearBed) instanceof BlockBed) {
                BlockPos other = otherBedHalf(nearBed);
                if (skipVerification) {
                    swapBackNow();
                    clear();
                    skipVerification = true;
                    return;
                }
                if (open(nearBed) || open(other)) {
                    swapBackNow();
                    if (progress != 0) abort(target);
                    clear();
                    return;
                }
            }
            if (air || !canReach(pos)) {
                swapBackNow();
                if (progress != 0 && lastAction == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) abort(target);
                clear();
                return;
            }
            switch (breakMode.getValue()) {
                case LEGIT -> legit(e, pos);
                case INSTANT -> {
                    if (rotateTick == 0) {
                        rotateTick = 1;
                        swapToTool(target);
                        quickBreak(target, quickBreakTicks());
                    } else {
                        clear();
                    }
                }
                case SWAP -> swap(e, pos);
            }
            return;
        }

        long every = Math.max(1, Math.round(rate.getValue() * 20));
        if (++rateCounter % every == 0) scan();
    }

    private void legit(MotionEvent e, BlockPos pos) {
        if (counter >= 2) {
            if (++counter >= 10) clear();
            return;
        }
        boolean started = false;
        if (progress == 0) {
            MovingObjectPosition over = mc.objectMouseOver;
            if (GameSettings.isKeyDown(mc.gameSettings.keyBindAttack) && over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && block(over.getBlockPos()) instanceof BlockBed) {
                abort(target);
            }
            started = true;
            swapToTool(target);
            start(target);
        } else if (onlyWhileVisible.getValue() && !visible(pos)) {
            abort(target);
            clear();
            return;
        }
        progressPerTick = breakSpeed(block(pos), heldStack(), ignoreSlow.getValue());
        progress += progressPerTick;
        double head = speed() - 1;
        if (head > 0 && progress < head) progress = (float) head;
        if (progress >= 1) {
            stop(target);
            if (groundSpoof.getValue() && !e.isOnGround()) e.setOnGround(true);
            waitTicks = 2;
            counter = 2;
            return;
        }
        if (!started && sendAnimations.getValue() && !sentSwing) swing();
        mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), target, (int) (progress * 10) - 1);
    }

    private void swap(MotionEvent e, BlockPos pos) {
        if (block(pos) instanceof BlockBed) {
            if (++counter % 10 == 0) {
                target = null;
                counter = 0;
            }
            return;
        }
        int tool = bestToolSlot(block(pos));
        Block block = block(pos);
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(tool == -1 ? mc.thePlayer.inventory.currentItem : tool);
        double head = speed() - 1;
        double cap = (1 - head) / 2 - 0.01;
        progressPerTick = (float) Math.min(breakSpeed(block, stack, ignoreSlow.getValue()), cap);
        float vanilla = (float) Math.min(breakSpeed(block, stack, false), cap);
        float slowed = slowFor(block, progressPerTick);
        if (progressPerTick == slowed) {
            if (progress < head) {
                if (progress == 0) vanillaProgress = 0;
                progress = (float) head;
            }
        } else {
            progressPerTick = slowed;
        }
        if (progress < 1 && progress + progressPerTick >= 1) {
            if (tool != -1 && tool != mc.thePlayer.inventory.currentItem) {
                swapBack = mc.thePlayer.inventory.currentItem;
                sendSlot(tool);
            }
            rotateTick = 1;
        } else if (groundSpoof.getValue() && !e.isOnGround() && rotateTick == 3) {
            e.setOnGround(true);
        } else if (vanillaProgress >= 1.1F && mc.thePlayer.onGround && lastAction == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
            clear();
            swapBackNow();
            return;
        }
        progress += progressPerTick;
        vanillaProgress += vanilla;
        if (sendAnimations.getValue() && !sentSwing && lastAction == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK && rotateTick != 5) swing();
    }

    private void swapTicks() {
        if (breakMode.getValue() != BreakMode.SWAP) return;
        if (rotateTick == 4) {
            rotateTick = -1;
            if (target != null) {
                if (block(target) instanceof BlockBed) quickBreak(target, fatigue() == 0 ? quickBreakTicks() : 17);
                else start(target);
            }
        } else if (rotateTick == 5) {
            rotateTick = -1;
            if (target != null) {
                stop(target);
                if (skipVerificationSetting.getValue()) skipVerification = true;
            }
        }
    }

    private BlockPos rotationTarget() {
        switch (breakMode.getValue()) {
            case LEGIT -> {
                if (counter > 2) return null;
                return nearBlock != null ? nearBlock : target;
            }
            case INSTANT -> {
                if (rotateTick == -1) return null;
                if (rotateTick == 0 && target != null) return target;
                if (rotateTick == 1 && quickBlock != null) return quickBlock;
                rotateTick = -1;
                return null;
            }
            case SWAP -> {
                if (rotateTick == -1) return null;
                rotateTick += 2;
                return target;
            }
        }
        return null;
    }

    private void scan() {
        int radius = (int) (Math.ceil(range.getValue()) + 2);
        for (int y = radius; y >= -radius; y--) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state.getBlock() != Blocks.bed || state.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT || ownBed.test(pos)) continue;
                    BlockPos[] halves = {pos, pos.offset(state.getValue(BlockBed.FACING))};
                    if (!canReach(halves[0]) && !canReach(halves[1])) continue;
                    float field = fov.getValue().floatValue();
                    if (field < 360 && !inFov(field, halves[0]) && !inFov(field, halves[1])) continue;

                    if (onlyWhileVisible.getValue()) {
                        BlockPos seen = null;
                        if (visible(halves[0])) seen = halves[0];
                        if (visible(halves[1])) seen = seen == null ? halves[1] : closer(halves[0], halves[1]);
                        if (seen != null) setTarget(seen);
                        return;
                    }
                    BlockPos half = chosen.get(halves[0]);
                    if (half == null || !canReach(half)) {
                        half = closer(halves[0], halves[1]);
                        chosen.put(halves[0], half);
                    }
                    if (breakNearBlock.getValue() && nearBlock == null) {
                        if (open(halves[0]) || open(halves[1])) {
                            setTarget(half);
                            return;
                        }
                        nearBed = half;
                        nearBlock = fastestAround(halves);
                        if (nearBlock != null) setTarget(nearBlock);
                        return;
                    }
                    setTarget(half);
                    return;
                }
            }
        }
    }

    private void setTarget(BlockPos pos) {
        target = pos;
        targetIsBed = block(pos) == Blocks.bed;
        if (breakMode.getValue() != BreakMode.LEGIT) rotateTick = 0;
    }

    private void quickBreak(BlockPos pos, int ticks) {
        start(pos);
        quickBlock = pos;
        quickTicks = ticks;
    }

    private int quickBreakTicks() {
        return maximizeSpeed.getValue() ? 2 : 4;
    }

    private double speed() {
        return Math.min(1.8, breakSpeed.getValue());
    }

    private void start(BlockPos pos) {
        if (!sentSwing) swing();
        lastAction = C07PacketPlayerDigging.Action.START_DESTROY_BLOCK;
        PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(lastAction, pos, faceToward(pos)));
        if (!mc.thePlayer.capabilities.isCreativeMode) swing();
    }

    private void stop(BlockPos pos) {
        mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), pos, -1);
        lastAction = C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK;
        PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(lastAction, pos, faceToward(pos)));
        if (!disableBreakEffects.getValue()) mc.theWorld.playAuxSFX(2001, pos, Block.getStateId(mc.theWorld.getBlockState(pos)));
        swing();
    }

    private void abort(BlockPos pos) {
        mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), pos, -1);
        if (mc.thePlayer.capabilities.isCreativeMode) return;
        lastAction = C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK;
        PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(lastAction, pos, EnumFacing.DOWN));
    }

    private void swing() {
        if (silentSwing.getValue()) PacketUtils.sendPacket(new C0APacketAnimation());
        else mc.thePlayer.swingItem();
        sentSwing = true;
    }

    private void swapToTool(BlockPos pos) {
        int tool = bestToolSlot(block(pos));
        if (tool != -1 && tool != mc.thePlayer.inventory.currentItem) {
            swapBack = mc.thePlayer.inventory.currentItem;
            sendSlot(tool);
        }
    }

    private void swapBackNow() {
        if (swapBack != -1) {
            sendSlot(swapBack);
            swapBack = -1;
        }
    }

    private void sendSlot(int slot) {
        if (serverSlot == slot) return;
        PacketUtils.sendPacket(new C09PacketHeldItemChange(slot));
        serverSlot = slot;
    }

    private ItemStack heldStack() {
        return mc.thePlayer.inventory.getStackInSlot(serverSlot >= 0 && serverSlot < 9 ? serverSlot : mc.thePlayer.inventory.currentItem);
    }

    private boolean open(BlockPos pos) {
        if (skipVerification) {
            skipVerification = false;
            return true;
        }
        boolean bed = block(pos) == Blocks.bed;
        boolean glass = maximizeSpeed.getValue();
        for (int i = 0; i < 6; i++) {
            Block block = block(offset(i, pos));
            if (block == Blocks.air || block instanceof BlockLiquid || block == Blocks.fire || block == Blocks.ladder || block == Blocks.iron_bars) return true;
            if (block == Blocks.ender_chest || block == Blocks.enchanting_table || block == Blocks.skull || block == Blocks.anvil || block == Blocks.tripwire
                    || block == Blocks.vine || block == Blocks.flower_pot) {
                return true;
            }
            if (block instanceof BlockChest || block instanceof BlockDoor || block instanceof BlockTrapDoor || block instanceof BlockFence
                    || block instanceof BlockFenceGate || block instanceof BlockWall || block instanceof BlockStairs) {
                return true;
            }
            if (glass && (block == Blocks.glass || block == Blocks.stained_glass)) return true;
            if (i != 1 && block instanceof BlockSlab) return true;
            if (i >= 2 && (!bed && block == Blocks.bed || block == Blocks.end_portal_frame)) return true;
        }
        return false;
    }

    private static BlockPos offset(int i, BlockPos pos) {
        return switch (i) {
            case 0 -> pos.up();
            case 1 -> pos.down();
            case 2 -> pos.north();
            case 3 -> pos.south();
            case 4 -> pos.east();
            default -> pos.west();
        };
    }

    private static BlockPos otherBedHalf(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) facing = facing.getOpposite();
        return pos.offset(facing);
    }

    private BlockPos fastestAround(BlockPos[] halves) {
        BlockPos best = null;
        float bestSpeed = -1;
        for (BlockPos half : halves) {
            for (int i = 0; i < 6; i++) {
                if (i == 1) continue;
                BlockPos side = offset(i, half);
                Block block = block(side);
                if (block == Blocks.bed || !canReach(side)) continue;
                int tool = bestToolSlot(block);
                float speed = breakSpeed(block, mc.thePlayer.inventory.getStackInSlot(tool == -1 ? mc.thePlayer.inventory.currentItem : tool), true);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = side;
                } else if (speed == bestSpeed) {
                    best = closer(best, side);
                }
            }
        }
        return best;
    }

    private float slowFor(Block block, float f) {
        if (!ignoreSlow.getValue()) return f;
        double d = 1.0;
        if (fatigue() > 0) {
            if (block == Blocks.end_stone) {
                if (f == 0.044444446F) d = 0.4;
            } else if (block == Blocks.hardened_clay || block == Blocks.stained_hardened_clay) {
                if (f == 0.10666667F) d = 0.3;
            } else if ((block == Blocks.planks || block == Blocks.log || block == Blocks.log2) && f == 0.06666667F) {
                d = 0.6;
            }
        } else if (block == Blocks.obsidian && f == 0.012F) {
            d = 0.999;
        }
        return d != 1.0 ? (float) (f * d) : f;
    }

    private static int fatigue() {
        return mc.thePlayer.isPotionActive(Potion.digSlowdown) ? mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier() + 1 : 0;
    }

    private static float breakSpeed(Block block, ItemStack stack, boolean ignoreSlowdowns) {
        if (block == null) return 0;
        float hardness = block.getBlockHardness(mc.theWorld, mc.thePlayer.getPosition());
        if (hardness < 0) return 0;
        boolean harvest = block.getMaterial().isToolNotRequired() || stack != null && stack.canHarvestBlock(block);
        return digSpeed(stack, block, ignoreSlowdowns) / hardness / (harvest ? 30.0F : 100.0F);
    }

    private static float digSpeed(ItemStack stack, Block block, boolean ignoreSlowdowns) {
        float f = toolSpeed(stack, block);
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) f *= 1.0F + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        if (!ignoreSlowdowns) {
            if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
                switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                    case 0 -> f *= 0.3F;
                    case 1 -> f *= 0.09F;
                    case 2 -> f *= 0.0027F;
                    default -> f *= 8.1E-4F;
                }
            }
            if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) f /= 5.0F;
            if (!mc.thePlayer.onGround) f /= 5.0F;
        }
        return f;
    }

    private static float toolSpeed(ItemStack stack, Block block) {
        float f = stack == null ? 1.0F : stack.getItem().getStrVsBlock(stack, block);
        int efficiency;
        if (f > 1.0F && (efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack)) > 0) f += efficiency * efficiency + 1;
        return f;
    }

    private static int bestToolSlot(Block block) {
        float best = 1.0F;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            float speed = toolSpeed(stack, block);
            if (speed > best) {
                best = speed;
                slot = i;
            }
        }
        return slot;
    }

    private static float[] toBlock(BlockPos pos) {
        double x = pos.getX() + 0.45 - mc.thePlayer.posX, y = pos.getY() + 0.45 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()),
                z = pos.getZ() + 0.45 - mc.thePlayer.posZ;
        double flat = Math.sqrt(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(y, flat));
        return new float[]{mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw), pitch};
    }

    private static Vec3 look(float yaw, float pitch) {
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI), sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F), sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    private boolean canReach(BlockPos pos) {
        float[] rot = toBlock(pos);
        Vec3 from = mc.thePlayer.getPositionEyes(1.0F), dir = look(rot[0], rot[1]);
        double reach = range.getValue();
        Vec3 to = from.addVector(dir.xCoord * reach, dir.yCoord * reach, dir.zCoord * reach);
        Block block = block(pos);
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);
        return box != null && box.calculateIntercept(from, to) != null;
    }

    private EnumFacing faceToward(BlockPos pos) {
        float[] rot = toBlock(pos);
        Vec3 from = mc.thePlayer.getPositionEyes(1.0F), dir = look(rot[0], rot[1]);
        double reach = range.getValue();
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, from.addVector(dir.xCoord * reach, dir.yCoord * reach, dir.zCoord * reach));
        return hit != null && hit.sideHit != null ? hit.sideHit : EnumFacing.UP;
    }

    private static boolean inFov(float fov, BlockPos pos) {
        double yawTo = Math.toDegrees(Math.atan2(pos.getZ() + 0.5 - mc.thePlayer.posZ, pos.getX() + 0.5 - mc.thePlayer.posX)) - 90.0;
        double difference = MathHelper.wrapAngleTo180_double((mc.thePlayer.rotationYaw - yawTo) % 360.0);
        return Math.abs(difference) < fov * 0.5;
    }

    private static boolean visible(BlockPos pos) {
        Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        for (double d = 0; d <= 0.5; d += 0.5) {
            double y = pos.getY() + d;
            if (sees(pos, eyes, new Vec3(pos.getX() + 1, y, pos.getZ() + 0.5)) || sees(pos, eyes, new Vec3(pos.getX(), y, pos.getZ() + 0.5))
                    || sees(pos, eyes, new Vec3(pos.getX() + 0.5, y, pos.getZ() + 1)) || sees(pos, eyes, new Vec3(pos.getX() + 0.5, y, pos.getZ()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sees(BlockPos pos, Vec3 from, Vec3 to) {
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, to, false, false, false);
        return hit == null || hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(hit.getBlockPos());
    }

    private static BlockPos closer(BlockPos a, BlockPos b) {
        if (a == null) return b;
        if (b == null) return a;
        return mc.thePlayer.getDistanceSq(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5) > mc.thePlayer.getDistanceSq(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) ? b : a;
    }

    private static Block block(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }
}
