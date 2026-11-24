package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

public final class WatchdogNewSpeed extends SpeedBase {
    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame()) {
            offGroundHeight(e);
        }
        super.update(e);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        stage++;
        switch (stage) {
            case 1 -> {
                lastDist = 0.0D;
                if (MovementUtils.isMoving() && mc.thePlayer.onGround) {
                    e.setY(mc.thePlayer.motionY = jumpMotion(0.42F));
                    moveSpeed = 0.6D;
                }
            }
            case 2 -> moveSpeed = lastDist - 0.66D * (lastDist - baseSpeed());
            default -> {
                if (blocked()) stage = 0;
                moveSpeed = lastDist - lastDist / 159.9D;
            }
        }

        moveSpeed = Math.max(moveSpeed, baseSpeed());
        setSpeed(e, moveSpeed);
    }
}
