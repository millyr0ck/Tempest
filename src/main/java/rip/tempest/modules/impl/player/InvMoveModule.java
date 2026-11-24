package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.SprintEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.InventoryUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.potion.Potion;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "InvMove", category = ModuleCategory.PLAYER)
public class InvMoveModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);

    private enum Mode {
        Normal,
        Hypixel,

        Grim
    }

    private boolean sentFirstOpen = false;
    private boolean failedClientStatus = false;
    private boolean failedCloseWindow = false;

    KeyBinding[] moveKeys = new KeyBinding[]{mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump};;

    @EventLink
    private final Listener<PreUpdateEvent> preUpdateEventListener = event -> {
        setSuffix(mode.getValue().toString());
        if (!(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof GuiContainerCreative)) {
            KeyBinding[] keyBindingArray = this.moveKeys;
            int n = this.moveKeys.length;
            int n2 = 0;

            while (n2 < n) {
                KeyBinding bind = keyBindingArray[n2];
                KeyBinding.setKeyBindState(bind.getKeyCode(), Keyboard.isKeyDown((int) bind.getKeyCode()));
                ++n2;
            }

            if (mode.getValue() == Mode.Hypixel && (mc.currentScreen instanceof GuiInventory || InventoryUtils.isInventoryOpen)
                    && !holdingInventoryItems()) {
                if (!sentFirstOpen) {
                    PacketUtils.sendSilentPacket(new C0DPacketCloseWindow());
                    sentFirstOpen = true;
                }

                int safePacketTick = mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 3 : 4;

                if (mc.thePlayer.ticksExisted % safePacketTick == 0) {
                    PacketUtils.sendSilentPacket(new C0DPacketCloseWindow());
                } else if (mc.thePlayer.ticksExisted % safePacketTick == 1) {
                    PacketUtils.sendSilentPacket(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                }
            } else {
                sentFirstOpen = false;
            }
        }
    };

    private static boolean holdingInventoryItems() {
        if (mc.thePlayer.inventory.getItemStack() != null) return true;
        for (int slot = 1; slot <= 4; slot++) {
            if (mc.thePlayer.inventoryContainer.getSlot(slot).getHasStack()) return true;
        }
        return false;
    }

    @EventLink
    private final Listener<SprintEvent> sprintEventListener = event -> {
        if (mode.getValue() == Mode.Grim && (mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiChest)) {
            mc.thePlayer.setSprinting(false);
            event.setSprinting(false);
        }
    };

    @EventLink
    private final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        if (mode.getValue() == Mode.Hypixel) {
            if (event.getPacket() instanceof C16PacketClientStatus) {
                if (this.failedClientStatus) {
                    event.setCancelled();
                }
                this.failedClientStatus = true;
            }
            if (event.getPacket() instanceof C0DPacketCloseWindow) {
                if (this.failedCloseWindow) {
                    event.setCancelled();
                }
                this.failedCloseWindow = true;
            }
        }
    };

    @Override
    public void onDisable() {
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindForward) || mc.currentScreen != null) {
            mc.gameSettings.keyBindForward.setPressed(false);
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindBack) || mc.currentScreen != null) {
            mc.gameSettings.keyBindBack.setPressed(false);
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindRight) || mc.currentScreen != null) {
            mc.gameSettings.keyBindRight.setPressed(false);
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindLeft) || mc.currentScreen != null) {
            mc.gameSettings.keyBindLeft.setPressed(false);
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindJump) || mc.currentScreen != null) {
            mc.gameSettings.keyBindJump.setPressed(false);
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSprint) || mc.currentScreen != null) {
            mc.gameSettings.keyBindSprint.setPressed(false);
        }
        this.sentFirstOpen = false;
        this.failedClientStatus = false;
        this.failedCloseWindow = false;
        super.onDisable();
    }
}
