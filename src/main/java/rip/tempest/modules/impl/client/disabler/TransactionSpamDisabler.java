package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

public final class TransactionSpamDisabler extends SubMode {
    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        PacketUtils.sendPacketNoEvent(new C0FPacketConfirmTransaction(
                0, (short) MathUtils.getRandom(-32767, 32767), false));
    };
}
