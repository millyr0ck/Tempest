package rip.tempest.modules.impl.movement;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.ItemSlowdownEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.player.TeleportEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.BadPacketsProcess;
import rip.tempest.processes.BlinkProcess;
import rip.tempest.utils.mc.InventoryUtils;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Slow", category = ModuleCategory.MOVEMENT)
public final class NoSlowModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);
    private final NumberProperty amount = new NumberProperty("Amount", 2, () -> mode.getValue() == Mode.Ticks, 2, 5, 1);
    public final Property<Boolean> food = new Property<>("Food", true, () -> mode.getValue() != Mode.Blink && mode.getValue() != Mode.Hypixel);
    public final Property<Boolean> potion = new Property<>("Potion", true, () -> mode.getValue() != Mode.Blink && mode.getValue() != Mode.Hypixel);
    public final Property<Boolean> sword = new Property<>("Sword", true, () -> mode.getValue() != Mode.Blink && mode.getValue() != Mode.Hypixel);
    public final Property<Boolean> bow = new Property<>("Bow", true, () -> mode.getValue() != Mode.Blink && mode.getValue() != Mode.Hypixel);

    private enum Mode {
        Vanilla("Vanilla"),
        UpdatedNCP("Updated NCP"),
        Hypixel("Hypixel"),
        Blink("Blink"),
        Switch("Switch"),
        Ticks("Ticks"),
        Grim("Grim"),

        AstolfoWatchdog("NCP2");

        public final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static boolean isUsing;
    private int disable;
    public int tick;

    private boolean slowedLastTick, grimSlowThisTick;

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<StrafeEvent> strafeListener = e -> {
        if (mode.getValue() != Mode.Grim || !grimSlowThisTick) return;
        grimSlowThisTick = false;
        e.setForward(e.getForward() * 0.2F);
        e.setStrafe(e.getStrafe() * 0.2F);
    };

    public static boolean sprintsWhileUsing() {
        NoSlowModule module = Tempest.INSTANCE.getModuleManager().getModule(NoSlowModule.class);
        return module.isEnabled() && module.mode.getValue() == Mode.Grim && module.grimCovers(mc.thePlayer.getHeldItem());
    }

    private boolean grimCovers(ItemStack held) {
        if (held == null) return false;
        Item item = held.getItem();
        return food.getValue() && item instanceof ItemFood || potion.getValue() && item instanceof ItemPotion
                || sword.getValue() && item instanceof ItemSword || bow.getValue() && item instanceof ItemBow;
    }

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        setSuffix(mode.getValue().toString());

        this.disable++;

        if (mode.getValue() == Mode.Blink) {
            if (mc.thePlayer.isUsingItem()) {
                if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemFood) {
                    BlinkProcess.enable();
                    isUsing = true;
                }
            } else if (isUsing) {
                BlinkProcess.disable();
                isUsing = false;
            }
        }

        if (mode.getValue() == Mode.AstolfoWatchdog && mc.thePlayer.getItemInUse() != null && mc.thePlayer.isUsingItem()
                && MovementUtils.isMoving() && !mc.thePlayer.isBlocking() && mc.thePlayer.ticksExisted % 5 == 0) {
            PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.playerController.getCurrentPlayerItem()));
        }

        if (mc.thePlayer.isUsingItem()) {
            if (mode.getValue() == Mode.Hypixel && this.tick == 0 && InventoryUtils.isHoldingSword()) {
                PacketUtils.sendPacket(new C09PacketHeldItemChange((mc.thePlayer.inventory.currentItem + 1) % 9));
                PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                ++this.tick;
            }
        } else {
            this.tick = 0;
        }
    };

    @EventLink
    public final Listener<ItemSlowdownEvent> itemSlowdownEventListener = e -> {
        switch (mode.getValue()) {
            case Vanilla:
            case AstolfoWatchdog:
                if (food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
                    e.setCancelled();
                }
                if (potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
                    e.setCancelled();
                }
                if (sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                    e.setCancelled();
                }
                if (bow.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {
                    e.setCancelled();
                }
                break;
            case Hypixel:
                if (sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                    e.setCancelled();
                }
                break;
            case Blink:
                if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
                    e.setCancelled();
                }
                break;
            case Ticks:
                if (mc.thePlayer.onGroundTicks % amount.getValue() != 0 && MovementUtils.isOnGround()) {
                    if (food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
                        e.setCancelled();
                    }
                    if (potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
                        e.setCancelled();
                    }
                    if (sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                        e.setCancelled();
                    }
                    if (bow.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {
                        e.setCancelled();
                    }
                }
                break;
            case Switch:
                if (food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
                    e.setCancelled();
                    if (!BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    }
                }
                if (potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
                    e.setCancelled();
                    if (!BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    }
                }
                if (sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                    e.setCancelled();
                    if (!BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    }
                }
                if (bow.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {
                    e.setCancelled();
                    if (!BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    }
                }
                break;
            case Grim:

                grimSlowThisTick = false;
                if (!e.isUseItem() || mc.thePlayer.isRiding() || e.isCancelled()) {
                    slowedLastTick = false;
                } else if (grimCovers(mc.thePlayer.getHeldItem())) {
                    e.setCancelled();
                    grimSlowThisTick = !slowedLastTick;
                    slowedLastTick = grimSlowThisTick;
                } else {
                    slowedLastTick = true;
                }
                break;
            case UpdatedNCP:
                if (food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
                    e.setCancelled();
                    if (this.disable > 10 && !BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    }
                }
                if (potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
                    e.setCancelled();
                    if (this.disable > 10 && !BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    }
                }
                if (sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
                    e.setCancelled();
                    if (this.disable > 10 && !BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    }
                }
                if (bow.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {
                    e.setCancelled();
                    if (this.disable > 10 && !BadPacketsProcess.bad(false, true, true, false, false)) {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    }
                }
                break;
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = e -> {
        isUsing = false;
        disable = 0;
    };

    @EventLink
    public final Listener<TeleportEvent> teleportEventListener = e -> {
        isUsing = false;
        disable = 0;
    };

    @Override
    public void onDisable() {
        isUsing = false;
        super.onDisable();
    }
}
