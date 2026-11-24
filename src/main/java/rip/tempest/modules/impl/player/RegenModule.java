package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Regen", category = ModuleCategory.PLAYER)
public final class RegenModule extends Module {
    private enum Mode {
        Normal("Normal"),
        NCP("NCP");

        final String name;
        Mode(String name) { this.name = name; }

        @Override
        public String toString() { return name; }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);

    private final NumberProperty health  = new NumberProperty("Minimum Health", 15, () -> mode.getValue() == Mode.Normal, 1, 20, 1);
    private final NumberProperty packets = new NumberProperty("Speed", 20, () -> mode.getValue() == Mode.Normal, 1, 100, 1);

    private final NumberProperty ncpWaitTicks   = new NumberProperty("Wait Ticks", 15, () -> mode.getValue() == Mode.NCP, 15, 25, 1);
    private final NumberProperty ncpEatPackets  = new NumberProperty("Eat Packets", 18, () -> mode.getValue() == Mode.NCP, 1, 19, 1);
    private final NumberProperty ncpRegenTicks  = new NumberProperty("Regen Ticks", 8, () -> mode.getValue() == Mode.NCP, 0, 20, 1);
    private final NumberProperty ncpRegenPerTick = new NumberProperty("Regen C03 / Tick", 2, () -> mode.getValue() == Mode.NCP, 1, 4, 1);

    private int regenTicks;
    private int appleCooldown;

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (!e.isPre()) return;

        switch (mode.getValue()) {
            case Normal -> {
                if (mc.thePlayer.getHealth() < health.getValue().floatValue()) {
                    for (int i = 0; i < packets.getValue().intValue(); i++) {
                        PacketUtils.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(
                                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                                mc.thePlayer.onGround));
                    }
                }
            }
            case NCP -> {
                if (appleCooldown > 0) appleCooldown--;

                ItemStack held = mc.thePlayer.getHeldItem();
                boolean isGapple = held != null && held.getItem() == Items.golden_apple;

                if (mc.thePlayer.isUsingItem() && isGapple) {
                    mc.timer.timerSpeed = 1.0F;
                    if (mc.thePlayer.getItemInUseDuration() >= ncpWaitTicks.getValue().intValue()) {
                        int n = ncpEatPackets.getValue().intValue();
                        for (int i = 0; i < n; i++) {
                            mc.getNetHandler().addToSendQueue(new C03PacketPlayer(mc.thePlayer.onGround));
                        }
                        mc.playerController.onStoppedUsingItem(mc.thePlayer);
                        regenTicks = ncpRegenTicks.getValue().intValue();
                        appleCooldown = 4;
                    }
                    return;
                }

                boolean hasRegen = mc.thePlayer.isPotionActive(Potion.regeneration);
                if (regenTicks > 0 && hasRegen && mc.thePlayer.onGround
                        && mc.thePlayer.getHealth() < mc.thePlayer.getMaxHealth()) {
                    int r = ncpRegenPerTick.getValue().intValue();
                    for (int i = 0; i < r; i++) {
                        mc.getNetHandler().addToSendQueue(new C03PacketPlayer(true));
                    }
                    regenTicks--;
                } else if (!hasRegen) {
                    regenTicks = 0;
                }
            }
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> packetReceiveListener = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            regenTicks = 0;
            appleCooldown = 4;
        }
    };

    @Override
    public void onDisable() {
        regenTicks = 0;
        appleCooldown = 0;
        mc.timer.timerSpeed = 1.0F;
        super.onDisable();
    }
}
