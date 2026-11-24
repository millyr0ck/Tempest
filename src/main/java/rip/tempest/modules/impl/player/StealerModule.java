package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.util.Vec3;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.Tempest;
import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.RayCastUtils;
import rip.tempest.utils.misc.MovementFix;
import net.minecraft.block.BlockChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.InventoryUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockTNT;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Stealer", category = ModuleCategory.PLAYER)
public final class StealerModule extends Module {
    private final NumberProperty startDelay = new NumberProperty("Start Delay", 50.0, 0.0, 1000.0, 25.0);
    private final NumberProperty minDelay = new NumberProperty("Min Delay", 100.0, 0.0, 1000.0, 25.0);
    private final NumberProperty maxDelay = new NumberProperty("Max Delay", 150.0, 0.0, 1000.0, 25.0);

    private final Property<Boolean> stealTrash = new Property<>("Steal Trash Items", false);

    private final Property<Boolean> tools = new Property<>("Tools", true);

    private final Property<Boolean> gearFirst = new Property<>("Gear First", true);
    private final Property<Boolean> randomOrder = new Property<>("Random Order", true);

    private final Property<Boolean> randomMiss = new Property<>("Random Miss", false);

    private final Property<Boolean> ignoreMenus = new Property<>("Ignore Menus", true);
    private final Property<Boolean> autoClose = new Property<>("Auto Close", true);

    private final Property<Boolean> grabMouse = new Property<>("Grab Mouse", false);

    private final Property<Boolean> chestAura = new Property<>("Chest Aura", false);
    private final NumberProperty auraDelay = new NumberProperty("Aura Delay", 250.0, chestAura::getValue, 0.0, 1000.0, 25.0);
    private final Property<Boolean> auraLockView = new Property<>("Aura Lock View", false, chestAura::getValue);
    private final Property<Boolean> auraAssist = new Property<>("Aura Assist", false, chestAura::getValue);

    private final java.util.Set<BlockPos> openedChests = new java.util.HashSet<>();
    private final java.util.Map<BlockPos, Integer> failedOpens = new java.util.HashMap<>();
    private TileEntityChest auraTarget;
    private Vec3 auraPoint;
    private long lastChestAction;
    private BlockPos pendingChest;
    private long pendingAt;

    private volatile int contentsWindow = -1;
    private boolean contentsSeen;

    private static final long CONTENTS_TIMEOUT_MS = 1500L;
    private static final long OPEN_TIMEOUT_MS = 1000L;

    private static final long CLOSE_GRACE_MS = 150L;

    private static final String[] JUNK = {"tnt", "stick", "string", "flint", "compass", "feather", "bucket", "chest",
            "snow", "rod", "enchant", "exp", "shears", "anvil", "torch", "seeds", "leather", "piston"};

    private final Random random = new Random();

    private ContainerChest open;
    private long openedAt, nextClickAt;

    private int[] rank = new int[0];
    private boolean missedLast;

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !grabMouse.getValue() || !Display.isActive()) return;
        if (!(mc.currentScreen instanceof GuiChest)) return;
        if (ignoreMenus.getValue() && !isLootChest(((GuiChest) mc.currentScreen).lowerChestInventory)) return;
        mc.mouseHelper.mouseXYChange();
        mc.mouseHelper.ungrabMouseCursor();
        mc.mouseHelper.grabMouseCursor();
    };

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> {
        openedChests.clear();
        failedOpens.clear();
        auraTarget = null;
        pendingChest = null;
        contentsWindow = -1;
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S30PacketWindowItems) {
            int id = ((S30PacketWindowItems) e.getPacket()).func_148911_c();
            if (id != 0) contentsWindow = id;
        }
    };

    @EventLink
    public final Listener<PreUpdateEvent> onAuraAim = e -> {
        auraTarget = null;
        auraPoint = null;
        if (!chestAura.getValue() || mc.thePlayer == null || mc.theWorld == null) return;
        long now = System.currentTimeMillis();
        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof GuiChest) {
                lastChestAction = now;
                if (pendingChest != null) {
                    markOpened(pendingChest);
                    failedOpens.remove(pendingChest);
                    pendingChest = null;
                }
            }
            return;
        }
        if (pendingChest != null) {
            if (now - pendingAt < OPEN_TIMEOUT_MS) return;
            int fails = failedOpens.merge(pendingChest, 1, Integer::sum);
            if (fails >= 3) markOpened(pendingChest);
            pendingChest = null;
        }
        if (auraAssist.getValue() && !Mouse.isButtonDown(1)) return;
        if (AuraModule.target() != null || Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled()) return;

        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double best = Double.MAX_VALUE;
        for (TileEntity tile : mc.theWorld.loadedTileEntityList) {
            if (!(tile instanceof TileEntityChest) || tile.isInvalid() || openedChests.contains(tile.getPos())) continue;
            BlockPos pos = tile.getPos();
            double distance = eye.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (distance > reach + 1 || distance >= best) continue;
            Vec3 point = visiblePoint(eye, pos, reach);
            if (point == null) continue;
            best = distance;
            auraTarget = (TileEntityChest) tile;
            auraPoint = point;
        }
        if (auraTarget == null) return;

        double dx = auraPoint.xCoord - eye.xCoord;
        double dy = auraPoint.yCoord - eye.yCoord;
        double dz = auraPoint.zCoord - eye.zCoord;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        yaw = mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        if (auraLockView.getValue()) {
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;
        }
        RotationProcess.setRotations(new Vector2f(yaw, pitch), 10, MovementFix.NORMAL);
    };

    @EventLink
    public final Listener<MotionEvent> onAuraOpen = e -> {
        if (e.isPre() || auraTarget == null || pendingChest != null || mc.currentScreen != null) return;
        if (System.currentTimeMillis() - lastChestAction < auraDelay.getValue().longValue()) return;

        Vector2f sent = RotationProcess.rotations != null ? RotationProcess.rotations
                : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        MovingObjectPosition hit = RayCastUtils.rayCast(sent, mc.playerController.getBlockReachDistance());
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) return;
        if (!(mc.theWorld.getBlockState(hit.getBlockPos()).getBlock() instanceof BlockChest)) return;

        if (openedChests.contains(hit.getBlockPos())) return;

        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                hit.getBlockPos(), hit.sideHit, hit.hitVec);
        mc.thePlayer.swingItem();
        lastChestAction = System.currentTimeMillis();
        pendingChest = hit.getBlockPos();
        pendingAt = lastChestAction;
    };

    private Vec3 visiblePoint(Vec3 eye, BlockPos pos, double reach) {
        double[][] offsets = {{0.5, 0.5, 0.5}, {0.5, 0.8, 0.5}, {0.5, 0.5, 0.1}, {0.5, 0.5, 0.9}, {0.1, 0.5, 0.5}, {0.9, 0.5, 0.5}, {0.5, 0.2, 0.5}};
        for (double[] o : offsets) {
            Vec3 point = new Vec3(pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2]);
            if (eye.distanceTo(point) > reach) continue;
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point, false, true, false);
            if (hit == null || hit.getBlockPos() == null) continue;
            BlockPos at = hit.getBlockPos();
            if (at.equals(pos) || (mc.theWorld.getBlockState(at).getBlock() instanceof BlockChest && at.distanceSq(pos) <= 1)) {
                return point;
            }
        }
        return null;
    }

    private void markOpened(BlockPos pos) {
        openedChests.add(pos);
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos next = pos.offset(side);
            if (mc.theWorld.getBlockState(next).getBlock() instanceof BlockChest) openedChests.add(next);
        }
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        if (!(mc.currentScreen instanceof GuiChest) || !(((GuiChest) mc.currentScreen).inventorySlots instanceof ContainerChest)) {
            open = null;
            return;
        }
        ContainerChest container = (ContainerChest) ((GuiChest) mc.currentScreen).inventorySlots;
        IInventory chest = container.getLowerChestInventory();
        if (ignoreMenus.getValue() && !isLootChest(chest)) return;

        long now = System.currentTimeMillis();
        if (open != container) {
            open = container;
            openedAt = now;
            contentsSeen = false;
            missedLast = false;
            shuffle(chest.getSizeInventory());
        }
        if (!contentsSeen) {
            if (contentsWindow != container.windowId && !hasAnyItem(chest) && now - openedAt < CONTENTS_TIMEOUT_MS) return;
            contentsSeen = true;
            openedAt = now;
            nextClickAt = now + startDelay.getValue().longValue();
        }

        if (now < nextClickAt || mc.thePlayer.inventory.getItemStack() != null) return;

        int slot = nextSlot(chest);
        if (slot >= 0) {
            if (randomMiss.getValue() && !missedLast && random.nextFloat() < 0.05f) {
                int empty = randomEmptySlot(chest);
                if (empty >= 0) {
                    click(container, empty);
                    missedLast = true;
                    nextClickAt = now + delay();
                    return;
                }
            }
            missedLast = false;
            click(container, slot);
            nextClickAt = now + delay();
        } else if (autoClose.getValue() && now - openedAt >= CLOSE_GRACE_MS) {
            mc.thePlayer.closeScreen();
            open = null;
        }
    };

    @Override
    public void onDisable() {
        open = null;
        auraTarget = null;
        pendingChest = null;
        super.onDisable();
    }

    private static boolean hasAnyItem(IInventory chest) {
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (chest.getStackInSlot(i) != null) return true;
        }
        return false;
    }

    private void click(ContainerChest container, int slot) {
        lastChestAction = System.currentTimeMillis();
        mc.playerController.windowClick(container.windowId, slot, 0, 1, mc.thePlayer);
    }

    private long delay() {
        double low = Math.min(minDelay.getValue(), maxDelay.getValue());
        double high = Math.max(minDelay.getValue(), maxDelay.getValue());
        return Math.round(low + random.nextDouble() * (high - low));
    }

    private void shuffle(int size) {
        rank = new int[size];
        List<Integer> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) order.add(i);
        if (randomOrder.getValue()) java.util.Collections.shuffle(order, random);
        for (int position = 0; position < size; position++) rank[order.get(position)] = position;
    }

    private int nextSlot(IInventory chest) {
        int best = -1, bestCategory = Integer.MAX_VALUE, bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack stack = chest.getStackInSlot(i);
            if (stack == null || !wanted(stack, i, chest) || !fits(stack)) continue;
            int category = gearFirst.getValue() ? category(stack) : 0;
            int order = i < rank.length ? rank[i] : i;
            if (category < bestCategory || (category == bestCategory && order < bestRank)) {
                best = i;
                bestCategory = category;
                bestRank = order;
            }
        }
        return best;
    }

    private int randomEmptySlot(IInventory chest) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (chest.getStackInSlot(i) == null) empty.add(i);
        }
        return empty.isEmpty() ? -1 : empty.get(random.nextInt(empty.size()));
    }

    private static int category(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemArmor || item instanceof ItemSword || item instanceof ItemBow) return 0;
        if (item instanceof ItemEnderPearl || item instanceof ItemAppleGold || item instanceof ItemPotion || item instanceof ItemTool) return 1;
        return 2;
    }

    private boolean wanted(ItemStack stack, int index, IInventory chest) {
        if (stealTrash.getValue()) return true;
        Item item = stack.getItem();

        if (item instanceof ItemArmor) {
            int type = ((ItemArmor) item).armorType;
            return isBest(stack, index, chest,
                    s -> s.getItem() instanceof ItemArmor && ((ItemArmor) s.getItem()).armorType == type,
                    InventoryUtils::getProtection);
        }
        if (item instanceof ItemSword) {
            return isBest(stack, index, chest, s -> s.getItem() instanceof ItemSword, InventoryUtils::getItemDamage);
        }
        if (item instanceof ItemBow) {
            return isBest(stack, index, chest, s -> s.getItem() instanceof ItemBow, InventoryUtils::getBowDamage);
        }
        if (item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade) {
            if (!tools.getValue()) return false;
            Class<?> kind = item instanceof ItemPickaxe ? ItemPickaxe.class : item instanceof ItemAxe ? ItemAxe.class : ItemSpade.class;
            return isBest(stack, index, chest, s -> kind.isInstance(s.getItem()), InventoryUtils::getToolRating);
        }
        return !isTrash(stack);
    }

    private static boolean isBest(ItemStack stack, int index, IInventory chest, Predicate<ItemStack> sameKind, ToDoubleFunction<ItemStack> score) {
        double value = score.applyAsDouble(stack);
        for (ItemStack yours : mc.thePlayer.inventory.armorInventory) {
            if (yours != null && sameKind.test(yours) && score.applyAsDouble(yours) >= value) return false;
        }
        for (ItemStack yours : mc.thePlayer.inventory.mainInventory) {
            if (yours != null && sameKind.test(yours) && score.applyAsDouble(yours) >= value) return false;
        }
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            if (i == index) continue;
            ItemStack other = chest.getStackInSlot(i);
            if (other == null || !sameKind.test(other)) continue;
            double otherValue = score.applyAsDouble(other);
            if (otherValue > value || (otherValue == value && i < index)) return false;
        }
        return true;
    }

    private static boolean isTrash(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemPotion) return isBadPotion(stack);
        if (item instanceof ItemGlassBottle) return true;

        if ((item instanceof ItemSnowball || item instanceof ItemEgg) && ManagerModule.throwsEggsAndSnowballs()) return true;
        if (item instanceof ItemFishingRod && ManagerModule.throwsFishingRods()) return true;

        if (item instanceof ItemSnowball || item instanceof ItemEgg || item instanceof ItemFishingRod
                || item == Items.water_bucket || item == Items.lava_bucket) return false;
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).getBlock();

            if (block instanceof BlockFalling || block instanceof BlockTNT || block instanceof BlockSlime) return true;
        }
        String name = item.getUnlocalizedName().toLowerCase(Locale.ROOT);
        for (String junk : JUNK) {
            if (name.contains(junk)) {
                return !(junk.equals("chest") && stack.getDisplayName().toLowerCase(Locale.ROOT).contains("collect"));
            }
        }
        return false;
    }

    private static boolean isBadPotion(ItemStack stack) {
        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        if (effects == null || effects.isEmpty()) return true;
        for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            if (id >= 0 && id < Potion.potionTypes.length && Potion.potionTypes[id] != null && Potion.potionTypes[id].isBadEffect()) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(ItemStack stack) {
        for (ItemStack yours : mc.thePlayer.inventory.mainInventory) {
            if (yours == null) return true;
            if (yours.isStackable() && yours.stackSize < yours.getMaxStackSize()
                    && yours.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(yours, stack)) return true;
        }
        return false;
    }

    private static boolean isLootChest(IInventory chest) {
        if (!chest.hasCustomName()) return true;
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(chest.getDisplayName().getUnformattedText());
        if (name == null) return true;
        name = name.trim().toLowerCase(Locale.ROOT);
        return name.equals("chest") || name.equals("large chest") || name.equals("low")
                || name.equals(StatCollector.translateToLocal("container.chest").toLowerCase(Locale.ROOT))
                || name.equals(StatCollector.translateToLocal("container.chestDouble").toLowerCase(Locale.ROOT));
    }
}
