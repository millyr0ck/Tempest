package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class NullPlaceDisabler extends SubMode {
    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0, mc.thePlayer.posZ);
        PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(below, 1, null, 0.0F, 0.0F, 0.0F));
    };
}
