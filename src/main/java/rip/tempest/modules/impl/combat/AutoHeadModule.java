package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ServerUtils;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Head", category = ModuleCategory.COMBAT)
public final class AutoHeadModule extends Module {
    public enum Mode {
        Heads, Soup
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Heads);

    private final NumberProperty health = new NumberProperty("Health", 15.5, this::headsMode, 1, 20, 0.5);
    private final NumberProperty delay = new NumberProperty("Delay", 350, this::headsMode, 100, 1000, 50, Representation.MILLISECONDS);
    private final NumberProperty resHealth = new NumberProperty("Res Health", 8.75, this::headsMode, 0.5, 10, 0.25);
    private final Property<Boolean> soup = new Property<>("Soup", true, this::headsMode);
    private final Property<Boolean> heads = new Property<>("Heads", true, this::headsMode);
    private final Property<Boolean> mutton = new Property<>("Mutton", true, this::headsMode);
    private final Property<Boolean> gapples = new Property<>("Gapples", true, this::headsMode);
    private final Property<Boolean> steak = new Property<>("Steak", true, this::headsMode);
    private final Property<Boolean> bread = new Property<>("Bread", true, this::headsMode);
    private final Property<Boolean> healingEgg = new Property<>("Healing Egg", true, this::headsMode);

    private final NumberProperty soupHealth = new NumberProperty("Soup Health", 15, () -> mode.getValue() == Mode.Soup, 1, 20, 1);
    private final NumberProperty soupDelay = new NumberProperty("Soup Delay", 50, () -> mode.getValue() == Mode.Soup, 0, 100, 5);
    private final Timer soupTimer = new Timer();
    private int soupAttackTicks;
    private long soupNextEat;

    private boolean headsMode() {
        return mode.getValue() == Mode.Heads;
    }

    private static final Item[] INSTANT = {Items.skull, Items.baked_potato, Items.magma_cream, Items.mutton};

    private static boolean healing;
    private final Timer timer = new Timer();
    private int lastItem = -1;

    public static boolean isHealing() {
        return healing;
    }

    @Override
    public void onDisable() {
        healing = false;
        lastItem = -1;
        super.onDisable();
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mode.getValue() != Mode.Soup || mc.thePlayer == null) return;

        this.soupAttackTicks++;

        if (mc.currentScreen != null) {
            this.soupAttackTicks = 0;
        }

        if (mc.thePlayer.onGroundTicks <= 1 || !soupTimer.hasTimeElapsed(soupNextEat) || soupAttackTicks < 10 || Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled()) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);

            if (stack == null) {
                continue;
            }

            final Item item = stack.getItem();

            if (item instanceof ItemSoup && mc.thePlayer.getHealth() <= this.soupHealth.getValue().floatValue()) {
                mc.thePlayer.inventory.currentItem = i;

                mc.playerController.syncCurrentPlayItem();
                PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));

                this.soupNextEat = soupDelay.getValue().longValue() * 10;
                soupTimer.reset();
                break;
            }
        }
    };

    @EventLink
    public final Listener<AttackEvent> onAttack = event -> this.soupAttackTicks = 0;

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || mc.thePlayer == null || mode.getValue() != Mode.Heads) return;

        int slot = findSlot();
        float max = mc.thePlayer.getMaxHealth(), hp = mc.thePlayer.getHealth();
        float minHealth = health.getValue().floatValue();
        ItemStack found = slot != -1 ? mc.thePlayer.inventoryContainer.getSlot(slot).getStack() : null;

        boolean shouldEat = found != null && found.getItem() == Items.cooked_beef;

        boolean shouldResistance = found != null && found.getItem() == Items.mutton && max == 20
                ? hp / 2F <= resHealth.getValue().floatValue()
                : (hp / max) <= resHealth.getValue().floatValue() / 10F;
        boolean shouldHeal = (max == 20 ? hp <= minHealth : (hp / max) <= minHealth / 20F)
                || (shouldEat && mc.thePlayer.getFoodStats().needFood()) || shouldResistance;

        if (lastItem != -1 && healing) {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer(e.isOnGround()));
            PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange((lastItem + 1) % 9));
            PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(lastItem));
            PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            healing = false;
            lastItem = -1;
            return;
        }

        if (slot != -1 && shouldHeal && timer.hasTimeElapsed(delay.getValue())) {
            int swapTo = 6;
            if (slot > 36) {
                swapTo = slot - 36;
            } else {
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 6, 2, mc.thePlayer);
            }

            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (oneNine() && ServerUtils.isOnHypixel() && stack != null && !instant(stack.getItem())) {
                lastItem = swapTo;

                if (AuraModule.holdsBlock() || mc.thePlayer.isBlocking()) {
                    PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                }
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(lastItem));
                PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(stack));
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange((lastItem + 1) % 9));
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(lastItem));
            } else {
                int current = mc.thePlayer.inventory.currentItem;
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem = swapTo));
                PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem = current));
            }
            healing = true;
            timer.reset();
        } else {
            healing = false;
        }
    };

    private static boolean instant(Item item) {
        for (Item i : INSTANT) if (item == i) return true;
        return false;
    }

    private static boolean oneNine() {
        try {
            return ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_9);
        } catch (Throwable t) {
            return false;
        }
    }

    private int findSlot() {
        boolean pit = ServerUtils.sidebarTitle().toUpperCase().contains("PIT");
        float max = mc.thePlayer.getMaxHealth(), hp = mc.thePlayer.getHealth();
        float minHealth = health.getValue().floatValue();
        boolean shouldHeal = max == 20 ? hp <= minHealth : (hp / max) <= minHealth / 20F;
        boolean regen = mc.thePlayer.isPotionActive(Potion.regeneration);
        boolean needsRegenOrAbsorption = (!regen || mc.thePlayer.getAbsorptionAmount() <= 0
                || mc.thePlayer.getActivePotionEffect(Potion.regeneration).getDuration() < 5) && shouldHeal;
        boolean shouldResistance = max == 20 ? hp / 2F <= resHealth.getValue().floatValue() : (hp / max) <= resHealth.getValue().floatValue() / 10F;
        boolean oneNine = oneNine();

        int slot = -1, priority = -1;
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            Item item = is.getItem();

            boolean shouldMutton = mutton.getValue() && item == Items.mutton && shouldResistance && !mc.thePlayer.isPotionActive(Potion.resistance);
            boolean shouldEgg = healingEgg.getValue() && needsRegenOrAbsorption && is.getDisplayName().equalsIgnoreCase("\247cFirst-Aid Egg");
            boolean shouldInstantHead = heads.getValue() && needsRegenOrAbsorption
                    && (item == Items.skull || item == Items.baked_potato || item == Items.magma_cream);

            if (priority < 1 && shouldInstantHead) {
                slot = i;
                priority = 1;
                continue;
            }
            if (priority < 2 && shouldEgg) {
                slot = i;
                priority = 2;
                continue;
            }
            if (priority < 3 && shouldMutton) {
                slot = i;
                priority = 3;
                continue;
            }

            if (oneNine && (item == Items.golden_apple || item == Items.cooked_beef || item == Items.bread)) {
                if (pit) {
                    if (gapples.getValue() && priority < 3 && needsRegenOrAbsorption && item == Items.golden_apple) {
                        slot = i;
                        priority = 3;
                        continue;
                    }
                    if (bread.getValue() && priority < 2 && item == Items.bread
                            && (!regen || mc.thePlayer.getActivePotionEffect(Potion.regeneration).getAmplifier() < 2)) {
                        slot = i;
                        priority = 2;
                        continue;
                    }
                    if (steak.getValue() && priority < 2 && needsRegenOrAbsorption && item == Items.cooked_beef) {
                        slot = i;
                        priority = 2;
                        continue;
                    }
                } else {
                    if (gapples.getValue() && priority < 3 && needsRegenOrAbsorption && item == Items.golden_apple) {
                        slot = i;
                        priority = 3;
                        continue;
                    }
                    if (steak.getValue() && priority < 1 && mc.thePlayer.getFoodStats().needFood() && item == Items.cooked_beef) {
                        slot = i;
                        priority = 1;
                        continue;
                    }
                    if (bread.getValue() && priority < 1 && mc.thePlayer.getFoodStats().needFood() && item == Items.bread) {
                        slot = i;
                        priority = 1;
                        continue;
                    }
                }
            }

            if (soup.getValue() && priority == -1 && item == Items.mushroom_stew) {
                slot = i;
                priority = 0;
            }
        }
        return slot;
    }
}
