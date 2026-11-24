package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

public final class WatchdogFastSpeed extends SpeedBase {
    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        final boolean scaffold = scaffold();
        hopCycle(e, 0.4001F,
                scaffold ? 1.399999976158142D : speedPotion() ? (jumpPotion() ? 2.025F : 2.2F) : 1.899999976158142D,
                scaffold ? 0.715D : speedPotion() ? (jumpPotion() ? 0.895F : 0.72F) : 0.68F,
                159.9D);
    }
}
