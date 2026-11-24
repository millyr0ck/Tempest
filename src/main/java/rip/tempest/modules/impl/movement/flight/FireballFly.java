package rip.tempest.modules.impl.movement.flight;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.modules.Module;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class FireballFly extends SubMode {
    private static final C08PacketPlayerBlockPlacement USE =
            new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, null, 0.0F, 0.0F, 0.0F);

    private Module module;

    private boolean thrown;
    private boolean flying;
    private int fireballSlot;
    private int lastHurtTime;

    public FireballFly(Module module) {
        this.module = module;
    }

    @Override
    protected void onEnable() {
        thrown = false;
        flying = false;
        fireballSlot = -1;
        lastHurtTime = 0;
    }

    @Override
    protected void onDisable() {
        if (inGame()) mc.timer.timerSpeed = 1.0F;
    }

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame() || flying) return;
        e.setCancelled();
    };

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        if (!thrown) {
            int slot = findFireballSlot();
            if (slot == -1) {
                NotificationManager.post(NotificationType.WARNING, "Fireball Fly", "No fire charges in hotbar");
                module.toggle();
                return;
            }
            fireballSlot = slot;

            if (mc.thePlayer.inventory.currentItem != slot) {
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
            }
            e.setPitch(-89.4F);
            PacketUtils.sendPacketNoEvent(USE);

            int held = mc.thePlayer.inventory.currentItem;
            if (fireballSlot != held) {
                PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(held));
            }

            thrown = true;
        }

        if (!flying) {
            int ht = mc.thePlayer.hurtTime;
            if (ht > lastHurtTime && ht >= 8) {
                flying = true;
            }
            lastHurtTime = ht;
            mc.thePlayer.motionY = 0;
            return;
        }

        mc.thePlayer.motionY = mc.gameSettings.keyBindJump.isKeyDown() ? 0.5
                : mc.gameSettings.keyBindSneak.isKeyDown() ? -0.5 : 0;
    };

    private static int findFireballSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() instanceof ItemFireball) return i;
        }
        return -1;
    }
}
