package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Fast Use", category = ModuleCategory.PLAYER)
public final class FastUseModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Packet);
    public final NumberProperty speed = new NumberProperty("Speed", 15, () -> mode.getValue() == Mode.Packet, 1, 100, 1);

    private enum Mode { Packet, Timer }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre()) return;
        setSuffix(mode.getValue().name());

        if (!mc.thePlayer.isUsingItem()) {
            if (mode.getValue() == Mode.Timer) mc.timer.timerSpeed = 1.0f;
            return;
        }

        if (mode.getValue() == Mode.Packet) {
            if (mc.thePlayer.getItemInUseCount() == 31) {
                for (int i = 0; i <= speed.getValue().intValue(); i++) {
                    PacketUtils.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(
                            mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                            mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround));
                }
            }
        } else if (mode.getValue() == Mode.Timer) {
            int duration = 32 - mc.thePlayer.getItemInUseCount();
            mc.timer.timerSpeed = duration > 13 ? 1.3f : 1.0f;
        }
    };

    @Override
    public void onDisable() {
        mc.timer.timerSpeed = 1.0f;
        super.onDisable();
    }
}
