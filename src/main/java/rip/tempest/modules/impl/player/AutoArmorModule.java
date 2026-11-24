package rip.tempest.modules.impl.player;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.InventoryUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Armor", category = ModuleCategory.PLAYER)
public final class AutoArmorModule extends Module {


    public Timer startTimer = new Timer();
    public Timer timer = new Timer();
    public ModeProperty modeProperty = new ModeProperty<>("Mode", Mode.Open);
    public Property<Boolean> stop = new Property<>("Stop", true, () -> this.modeProperty.getValue() == Mode.Spoof);
    public NumberProperty startDelay = new NumberProperty("Start Delay", 150.0, 0.0, 1000.0, 1.0);
    public NumberProperty speed = new NumberProperty("Speed", 150.0, 0.0, 1000.0, 1.0);

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(modeProperty.getValue().toString());
    };

    private enum Mode {
        Open,
        Spoof
    }

    @EventLink
    private final Listener<MoveEvent> moveEventListener = event -> {
        EntityLivingBase target = AuraModule.target();

        if (this.modeProperty.getValue() != Mode.Spoof || mc.currentScreen == null && target == null) {
            if (this.modeProperty.getValue() == Mode.Open) {
                if (mc.currentScreen == null) {
                    this.startTimer.reset();
                }

                if (!this.startTimer.hasTimeElapsed(this.startDelay.getValue(), false)) {
                    return;
                }
            }

            if (InventoryUtils.timer.hasTimeElapsed(this.speed.getValue(), false)) {
                if (this.modeProperty.getValue() == Mode.Open && !(mc.currentScreen instanceof GuiInventory)) {
                    return;
                }

                for (int type = 1; type < 5; type++) {
                    if (mc.thePlayer.inventoryContainer.getSlot(4 + type).getHasStack()) {
                        ItemStack is = mc.thePlayer.inventoryContainer.getSlot(4 + type).getStack();
                        if (!InventoryUtils.isBestArmor(is, type)) {
                            InventoryUtils.openInv(true);
                            InventoryUtils.drop(4 + type);
                            if (!this.stop.getValue()) {
                                InventoryUtils.closeInv(true);
                            }

                            InventoryUtils.timer.reset();
                            if (this.speed.getValue() != 0.0) {
                                break;
                            }
                        }
                    }
                }

                for (int typex = 1; typex < 5; typex++) {
                    if ((double) InventoryUtils.timer.getTime() > this.speed.getValue()) {
                        for (int i = 9; i < 45; i++) {
                            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
                                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                                if (InventoryUtils.getProtection(is) > 0.0F && InventoryUtils.isBestArmor(is, typex) && !InventoryUtils.isBadStack(is, true, true)) {
                                    InventoryUtils.openInv(true);
                                    InventoryUtils.shiftClick(i);
                                    if (!this.stop.getValue()) {
                                        InventoryUtils.closeInv(true);
                                    }

                                    InventoryUtils.timer.reset();
                                    if (this.speed.getValue() != 0.0) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (InventoryUtils.timer.hasTimeElapsed(55.0, false)) {
                InventoryUtils.closeInv(true);
            }

            if (InventoryUtils.isInventoryOpen && this.stop.getValue()) {
                event.setForward(0);
                event.setStrafe(0);
                event.setJump(false);
                event.setSneak(false);
            }
        } else {
            InventoryUtils.closeInv(true);
        }
    };
}