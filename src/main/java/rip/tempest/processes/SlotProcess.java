package rip.tempest.processes;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import static rip.tempest.utils.Util.mc;

/**
 * Switches the held hotbar slot for a module and switches it back afterwards. A "server" swap
 * really switches the slot, but the hotbar highlight, the item in your hand and the item your
 * player model holds keep showing what you had before, so the switch can't be seen.
 *
 * <p>From Yuri's SlotManager (AGPL-3.0). Yuri also made {@code getHeldItem()} itself report the
 * old item during a server swap; here only what is drawn changes, so every other module still
 * sees what is really held.
 */
public final class SlotProcess {
    private static int originalSlot = -1;
    private static boolean serverSwap;

    private SlotProcess() {
    }

    public static void swap(int slot, boolean server) {
        if (mc.thePlayer == null) return;
        if (originalSlot == -1) originalSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        serverSwap = server;
    }

    public static void swapBack() {
        if (mc.thePlayer != null && originalSlot >= 0) mc.thePlayer.inventory.currentItem = originalSlot;
        originalSlot = -1;
        serverSwap = false;
    }

    public static boolean isActive() {
        return originalSlot >= 0;
    }

    public static boolean isServerSwapActive() {
        return serverSwap && originalSlot >= 0;
    }

    public static ItemStack visibleStack(EntityPlayer player) {
        return player == mc.thePlayer && isServerSwapActive() ? player.inventory.getStackInSlot(originalSlot) : player.inventory.getCurrentItem();
    }

    public static int visibleSlot(EntityPlayer player) {
        return player == mc.thePlayer && isServerSwapActive() ? originalSlot : player.inventory.currentItem;
    }
}
