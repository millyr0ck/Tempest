package rip.tempest.modules.impl.player;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.combat.AutoPotModule;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.InventoryUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Random;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Manager", category = ModuleCategory.PLAYER)
public final class ManagerModule extends Module {
    public ModeProperty<Mode> modeProperty = new ModeProperty<>("Mode", Mode.Open);
    public Property<Boolean> stopProperty = new Property<>("Stop", true, () -> this.modeProperty.getValue() == Mode.Spoof);
    public Property<Boolean> throwGarbageProperty = new Property<>("Throw Garbage", true, this::tempest);

    public Property<Boolean> throwProjectilesProperty = new Property<>("Throw Eggs & Snowballs", true, () -> tempest() && throwGarbageProperty.getValue());

    public Property<Boolean> throwRodsProperty = new Property<>("Throw Fishing Rods", true, () -> exhibition() || throwGarbageProperty.getValue());
    public NumberProperty startDelayProperty = new NumberProperty("Start Delay", 150.0, this::tempest, 0.0, 1000.0, 1.0);
    public NumberProperty speedProperty = new NumberProperty("Speed", 150.0, this::tempest, 0.0, 1000.0, 1.0);
    public Property<Boolean> swordProperty = new Property<>("Sword", true, this::tempest);
    public NumberProperty swordSlotProperty = new NumberProperty("Sword Slot", 1.0, () -> tempest() ? swordProperty.getValue() : this.exhibitionReplace.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> axeProperty = new Property<>("Axe", true, this::tempest);
    public NumberProperty axeSlotProperty = new NumberProperty("Axe Slot", 2.0, () -> tempest() && axeProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> pickaxeProperty = new Property<>("Pickaxe", true, this::tempest);
    public NumberProperty pickaxeSlotProperty = new NumberProperty("Pickaxe", 3.0, () -> tempest() && pickaxeProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> shovelProperty = new Property<>("Shovel", false, this::tempest);
    public NumberProperty shovelSlotProperty = new NumberProperty("Shovel Slot", 4.0, () -> tempest() && shovelProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> bowProperty = new Property<>("Bow", false, this::tempest);
    public NumberProperty bowSlotProperty = new NumberProperty("Bow Slot", 5.0, () -> tempest() && bowProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> blocksProperty = new Property<>("Blocks", true, this::tempest);
    public NumberProperty blockSlotProperty = new NumberProperty("Block Slot", 6.0, () -> tempest() && blocksProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> projectilesProperty = new Property<>("Projectiles", true, this::tempest);
    public NumberProperty projectileSlotProperty = new NumberProperty("Projectile Slot", 7.0, () -> tempest() && projectilesProperty.getValue(), 1.0, 9.0, 1.0);
    public Property<Boolean> waterBucketProperty = new Property<>("Water Bucket", true, this::tempest);
    public NumberProperty waterBucketSloProperty = new NumberProperty("Water Bucket Slot", 8.0, () -> tempest() && waterBucketProperty.getValue(), 1.0, 9.0, 1.0);

    private final Property<Boolean> exhibitionToggle = new Property<>("Turn Off When Done", false, this::exhibition);

    private final Property<Boolean> exhibitionClosed = new Property<>("Clean While Closed", true, this::exhibition);
    private final NumberProperty exhibitionPotCap = new NumberProperty("Pot Cap", 1, this::exhibition, 1, 45, 1);
    private final NumberProperty exhibitionBlockCap = new NumberProperty("Block Cap", 128, this::exhibition, 0, 1728, 8);
    private final Property<Boolean> exhibitionSpawnEgg = new Property<>("Clean Spawn Eggs", true, this::exhibition);
    private final Property<Boolean> exhibitionArchery = new Property<>("Clean Archery", true, this::exhibition);
    private final Property<Boolean> exhibitionTools = new Property<>("Clean Tools", true, this::exhibition);
    private final Property<Boolean> exhibitionFood = new Property<>("Clean Food", true, this::exhibition);
    private final Property<Boolean> exhibitionReplace = new Property<>("Replace Sword", true, this::exhibition);
    private final Property<Boolean> exhibitionGapples = new Property<>("Gapples", false, this::exhibition);
    private final NumberProperty exhibitionGappleSlot = new NumberProperty("Gapple Slot", 1, () -> exhibition() && exhibitionGapples.getValue(), 1, 9, 1);

    public Timer startTimer = new Timer();
    public Timer timer = new Timer();

    private final Random random = new Random();
    private boolean isCleaning, firstEnable, swordSwapped;
    private final Timer swordTimer = new Timer();

    public enum Mode {
        Open,
        Spoof,

        Hypixel
    }

    private boolean tempest() {
        return modeProperty.getValue() != Mode.Hypixel;
    }

    private boolean exhibition() {
        return modeProperty.getValue() == Mode.Hypixel;
    }

    @Override
    public void onEnable() {
        isCleaning = false;
        swordSwapped = false;
        if (exhibitionToggle.getValue()) firstEnable = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        isCleaning = false;
        swordSwapped = false;
        InventoryUtils.closeInv(true);
        super.onDisable();
    }

    public static boolean throwsEggsAndSnowballs() {
        ManagerModule manager = Tempest.INSTANCE.getModuleManager().getModule(ManagerModule.class);
        if (manager == null || !manager.isEnabled()) return false;

        if (manager.exhibition()) return true;
        return manager.throwGarbageProperty.getValue() && manager.throwProjectilesProperty.getValue();
    }

    public static boolean throwsFishingRods() {
        ManagerModule manager = Tempest.INSTANCE.getModuleManager().getModule(ManagerModule.class);
        if (manager == null || !manager.isEnabled() || !manager.throwRodsProperty.getValue()) return false;
        return manager.exhibition() || manager.throwGarbageProperty.getValue();
    }

    private boolean isThrowawayProjectile(ItemStack stack) {
        if (stack.getItem() instanceof ItemFishingRod && this.throwGarbageProperty.getValue() && this.throwRodsProperty.getValue()) return true;
        return this.throwGarbageProperty.getValue() && this.throwProjectilesProperty.getValue()
                && (stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEgg);
    }

    @EventLink
    private final Listener<MoveEvent> moveEventListener = event -> {
        this.setSuffix(modeProperty.getValue().toString());
        if (exhibition()) return;
        if (this.modeProperty.getValue() == Mode.Spoof) {
            if (mc.currentScreen == null) {
                this.startTimer.reset();
            }
            if (!this.startTimer.hasTimeElapsed(this.startDelayProperty.getValue(), false)) {
                return;
            }
        }
        EntityLivingBase target = AuraModule.target();
        if (this.modeProperty.getValue() == Mode.Spoof && (mc.currentScreen != null || target != null)) {
            InventoryUtils.closeInv(true);
            return;
        }
        if (this.modeProperty.getValue() == Mode.Open && !(mc.currentScreen instanceof GuiInventory)) {
            return;
        }
        int i = 9;
        while (i < 45) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (InventoryUtils.timer.hasTimeElapsed(this.speedProperty.getValue(), false)) {
                    if (this.isThrowawayProjectile(is)) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 1, 4, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.swordSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemSword && is == InventoryUtils.bestSword() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.bestSword()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.swordSlotProperty.getValue())).getStack() != is && this.swordProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.swordSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.bowSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemBow && is == InventoryUtils.bestBow() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.bestBow()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.bowSlotProperty.getValue())).getStack() != is && this.bowProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.bowSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.pickaxeSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemPickaxe && is == InventoryUtils.bestPick() && is != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.bestPick()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.pickaxeSlotProperty.getValue())).getStack() != is && this.pickaxeProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.pickaxeSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.axeSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemAxe && is == InventoryUtils.bestAxe() && is != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.bestAxe()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.axeSlotProperty.getValue())).getStack() != is && this.axeProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.axeSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.shovelSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemSpade && is == InventoryUtils.bestShovel() && is != InventoryUtils.bestWeapon() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.bestShovel()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.shovelSlotProperty.getValue())).getStack() != is && this.shovelProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.shovelSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.blockSlotProperty.getValue() != 0.0 && is.getItem() instanceof ItemBlock && is == InventoryUtils.getBlockSlotInventory() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.getBlockSlotInventory()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.blockSlotProperty.getValue())).getStack() != is && this.blocksProperty.getValue()) {
                        if (mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.blockSlotProperty.getValue())).getStack() != null && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.blockSlotProperty.getValue())).getStack().getItem() instanceof ItemBlock && !InventoryUtils.invalidBlocks.contains(((ItemBlock) mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.blockSlotProperty.getValue())).getStack().getItem()).getBlock())) {
                            return;
                        }
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.blockSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.projectileSlotProperty.getValue() != 0.0 && is == InventoryUtils.getProjectileSlotInventory() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.getProjectileSlotInventory()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.projectileSlotProperty.getValue())).getStack() != is && this.projectilesProperty.getValue()) {
                        if (mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.projectileSlotProperty.getValue())).getStack() != null && (mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.projectileSlotProperty.getValue())).getStack().getItem() instanceof ItemSnowball || mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.projectileSlotProperty.getValue())).getStack().getItem() instanceof ItemEgg || mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.projectileSlotProperty.getValue())).getStack().getItem() instanceof ItemFishingRod)) {
                            return;
                        }
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.projectileSlotProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (this.waterBucketSloProperty.getValue() != 0.0 && is.getItem() == Items.water_bucket && is == InventoryUtils.getBucketSlotInventory() && mc.thePlayer.inventoryContainer.getInventory().contains(InventoryUtils.getBucketSlotInventory()) && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.shovelSlotProperty.getValue())).getStack() != is && this.waterBucketProperty.getValue()) {
                        if (mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.waterBucketSloProperty.getValue())).getStack() != null && mc.thePlayer.inventoryContainer.getSlot((int) (35.0 + this.waterBucketSloProperty.getValue())).getStack().getItem() == Items.water_bucket) {
                            return;
                        }
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, (int) (this.waterBucketSloProperty.getValue() - 1.0), 2, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) {
                            break;
                        }
                    } else if (InventoryUtils.isBadStack(is, true, true) && this.throwGarbageProperty.getValue()) {
                        InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 1, 4, mc.thePlayer);
                        if (!this.stopProperty.getValue()) {
                            InventoryUtils.closeInv(true);
                        }
                        InventoryUtils.timer.reset();
                        if (this.speedProperty.getValue() != 0.0) break;
                    }
                    if (InventoryUtils.timer.hasTimeElapsed(55.0, false)) {
                        InventoryUtils.closeInv(true);
                    }
                }
            }
            ++i;
        }
        if (InventoryUtils.isInventoryOpen && this.stopProperty.getValue()) {
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            event.setSneak(false);
        }
    };

    @EventLink
    private final Listener<TickEvent> exhibitionSwordListener = event -> {
        if (!exhibition() || mc.thePlayer == null || mc.theWorld == null) return;

        boolean inInventory = mc.currentScreen instanceof GuiInventory;
        if ((mc.currentScreen instanceof GuiContainer && !inInventory) || AutoPotModule.isPotting() || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            swordTimer.reset();
            swordSwapped = false;
            return;
        }
        swordSwapped = false;

        boolean closed = mc.currentScreen == null || mc.currentScreen instanceof GuiChat;
        if (!exhibitionReplace.getValue() || !(inInventory || (closed && exhibitionClosed.getValue()))) return;

        int slot = swordSlotProperty.getValue().intValue() - 1;
        boolean foundSword = false;
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            if (!(mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem() instanceof ItemSword)) continue;

            float itemDamage = getDamage(mc.thePlayer.inventoryContainer.getSlot(i).getStack());
            float currentDamage = getDamage(mc.thePlayer.inventoryContainer.getSlot(36 + slot).getStack());
            if (itemDamage > currentDamage && i - 36 != slot) {
                foundSword = true;
                if (swordTimer.hasTimeElapsed(300)) {
                    exhibitionSwap(i, slot);
                    return;
                }
                break;
            }
        }

        boolean foundGapple = false;
        if (exhibitionGapples.getValue()) {
            int gslot = exhibitionGappleSlot.getValue().intValue() - 1;
            for (int i = 9; i < 45; i++) {
                if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                Item item = mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem();
                if (item instanceof ItemAppleGold && (!mc.thePlayer.inventoryContainer.getSlot(36 + gslot).getHasStack()
                        || !(mc.thePlayer.inventoryContainer.getSlot(36 + gslot).getStack().getItem() instanceof ItemAppleGold))) {
                    foundGapple = true;
                    if (swordTimer.hasTimeElapsed(300)) {
                        exhibitionSwap(i, gslot);
                        return;
                    }
                    break;
                }
            }
        }

        if (!foundSword && !foundGapple) swordTimer.reset();
    };

    private void exhibitionSwap(int slot, int hotbar) {
        InventoryUtils.openInv(true);
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, hotbar, 2, mc.thePlayer);
        InventoryUtils.closeInv(true);
        swordTimer.reset();
        swordSwapped = true;
    }

    @EventLink
    private final Listener<MotionEvent> exhibitionCleanerListener = event -> {
        if (!exhibition() || mc.thePlayer == null) return;
        if (!mc.thePlayer.capabilities.allowEdit || swordSwapped || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer || AutoPotModule.isPotting()) {
            return;
        }
        if (ServerUtils.isGameStarting()) return;

        boolean inInventory = mc.currentScreen instanceof GuiInventory;
        boolean canClean = (exhibitionClosed.getValue() && (mc.currentScreen == null || mc.currentScreen instanceof GuiChat)) || inInventory;
        if (event.isPre() && canClean && random.nextInt(2) == 0) {
            for (int i = 9; i < 45; i++) {
                if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
                    ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                    if (isBad(is) && is != mc.thePlayer.getCurrentEquippedItem()) {
                        isCleaning = true;

                        if (!inInventory) InventoryUtils.openInv(true);
                        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 1, 4, mc.thePlayer);
                        InventoryUtils.timer.reset();
                        firstEnable = false;
                        break;
                    }
                }
                if (i == 44 && (isCleaning || firstEnable)) {
                    isCleaning = false;
                    if (!inInventory) InventoryUtils.closeInv(true);
                    if (exhibitionToggle.getValue()) toggle();
                }
            }
        } else if (!canClean) {
            isCleaning = false;
        }
    };

    private float getEfficiency(ItemStack itemTool) {
        float efficiency = ((ItemTool) itemTool.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
        if (efficiency > 1.0f) {
            int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemTool);
            efficiency += level * level + 1;
        }
        return efficiency;
    }

    private boolean isToolWorst(ItemStack item) {
        int axes = 0, pickaxes = 0, shovels = 0;
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            Item tool = mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem();
            if (!(tool instanceof ItemTool)) continue;
            if (tool instanceof ItemPickaxe && item.getItem() instanceof ItemPickaxe && axes < 2 && shovels < 2) {
                pickaxes++;
            } else if (tool instanceof ItemAxe && item.getItem() instanceof ItemAxe && pickaxes < 2 && shovels < 2) {
                axes++;
            } else if (tool instanceof ItemSpade && item.getItem() instanceof ItemSpade && pickaxes < 2 && axes < 2) {
                shovels++;
            }
        }

        if (pickaxes > 1 && item.getItem() instanceof ItemPickaxe) return worseThanBest(item, ItemPickaxe.class);
        if (axes > 1 && item.getItem() instanceof ItemAxe) return worseThanBest(item, ItemAxe.class);
        if (shovels > 1 && item.getItem() instanceof ItemSpade) return worseThanBest(item, ItemSpade.class);
        return false;
    }

    private boolean worseThanBest(ItemStack item, Class<? extends ItemTool> kind) {
        ItemStack bestStack = null;
        float current = getEfficiency(item);
        float best = -1;
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (kind.isInstance(stack.getItem())) {
                float efficiency = getEfficiency(stack);
                if (efficiency >= best) {
                    best = efficiency;
                    bestStack = stack;
                }
            }
        }
        return current < best || (current == best && !item.equals(bestStack));
    }

    private boolean isBad(ItemStack item) {
        if (item == null) return false;

        if (item.getItem() instanceof ItemSword) {
            int swordCount = 0;
            for (int i = 9; i < 45; i++) {
                if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()
                        && mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem() instanceof ItemSword) swordCount++;
            }
            if (swordCount > 1) {
                float bestDamage = -1;
                for (int i = 9; i < 45; i++) {
                    if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                    ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                    if (stack.getItem() instanceof ItemSword) bestDamage = Math.max(bestDamage, getDamage(stack));
                }
                if (getDamage(item) < bestDamage) return true;

                ItemStack keep = null;
                int slot = 36 + swordSlotProperty.getValue().intValue() - 1;
                for (int i = 9; i < 45; i++) {
                    if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                    ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                    if (!(stack.getItem() instanceof ItemSword) || getDamage(stack) != bestDamage) continue;
                    if (i == slot) {
                        keep = stack;
                        break;
                    }
                    if (keep == null) keep = stack;
                }
                return keep != item;
            }
        }

        if (item.getItem() instanceof ItemPotion itemPotion) {
            int potCount = 0;
            for (int i = 9; i < 45; i++) {
                if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();

                if (is.getItem() instanceof ItemPotion ip && ip.getColorFromDamage(is.getMetadata()) == itemPotion.getColorFromDamage(item.getMetadata())) {
                    potCount++;
                }
            }
            if (potCount > exhibitionPotCap.getValue().intValue()) return true;
        }

        if (item.getItem() instanceof ItemTool) {
            return exhibitionTools.getValue() || isToolWorst(item);
        }

        if (item.getItem() instanceof ItemArmor) {
            return !betterCheck(item) && !canEquip(item);
        }

        if (item.getItem() == Items.spawn_egg && exhibitionSpawnEgg.getValue()) return true;

        if (item.getItem() instanceof ItemFishingRod && throwRodsProperty.getValue()) return true;

        String name = item.getItem().getUnlocalizedName();
        return name.contains("tnt") ||
                name.contains("stick") ||
                name.contains("rod") ||
                name.contains("egg") ||
                name.contains("string") ||
                name.contains("flint") ||
                name.contains("compass") ||
                name.contains("feather") ||
                name.contains("bucket") ||
                (name.contains("chest") && !item.getDisplayName().toLowerCase().contains("collect")) ||
                name.contains("snow") ||
                name.contains("enchant") ||
                name.contains("exp") ||
                name.contains("shears") ||
                name.contains("anvil") ||
                name.contains("torch") ||
                name.contains("seeds") ||
                name.contains("leather") ||
                item.getItem() instanceof ItemGlassBottle ||
                name.contains("piston") ||
                (name.contains("potion") && isBadPotion(item)) ||
                item.getItem() instanceof ItemBlock && getBlockCount() > exhibitionBlockCap.getValue().intValue() ||
                (item.getItem() instanceof ItemFood && exhibitionFood.getValue() && !(item.getItem() instanceof ItemAppleGold)) ||
                ((item.getItem() instanceof ItemBow || name.contains("arrow")) && exhibitionArchery.getValue());
    }

    private double getProtectionValue(ItemStack stack) {
        if (stack.getItem() instanceof ItemArmor armor) {
            return armor.damageReduceAmount + (100 - armor.damageReduceAmount) * EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.0075D;
        }
        return 0;
    }

    private boolean betterCheck(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemArmor)) return false;
        String name = stack.getUnlocalizedName();
        return better(stack, 3, name.contains("chestplate")) || better(stack, 2, name.contains("leggings"))
                || better(stack, 1, name.contains("boots")) || better(stack, 4, name.contains("helmet"));
    }

    private boolean better(ItemStack stack, int equipmentSlot, boolean kind) {
        ItemStack worn = mc.thePlayer.getEquipmentInSlot(equipmentSlot);
        return kind && worn != null && getProtectionValue(stack) > getProtectionValue(worn);
    }

    private boolean canEquip(ItemStack stack) {
        String name = stack.getUnlocalizedName();
        return stack.getItem() instanceof ItemArmor && ((mc.thePlayer.getEquipmentInSlot(1) == null && name.contains("boots"))
                || (mc.thePlayer.getEquipmentInSlot(2) == null && name.contains("leggings"))
                || (mc.thePlayer.getEquipmentInSlot(3) == null && name.contains("chestplate"))
                || (mc.thePlayer.getEquipmentInSlot(4) == null && name.contains("helmet")));
    }

    private int getBlockCount() {
        int count = 0;
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (is.getItem() instanceof ItemBlock block && !InventoryUtils.invalidBlocks.contains(block.getBlock())) {
                count += is.stackSize;
            }
        }
        return count;
    }

    private boolean isBadPotion(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemPotion potion && ItemPotion.isSplash(stack.getItemDamage())) {
            for (PotionEffect effect : potion.getEffects(stack)) {
                int id = effect.getPotionID();
                if (id == Potion.poison.getId() || id == Potion.harm.getId() || id == Potion.moveSlowdown.getId() || id == Potion.weakness.getId()) {
                    return true;
                }
            }
        }
        return false;
    }

    private float getDamage(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemSword sword)) return 0;
        return EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25f + sword.getDamageVsEntity()
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack) * 0.01f;
    }
}
