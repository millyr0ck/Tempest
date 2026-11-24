package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

public final class WatchdogNewTunedSpeed extends SpeedBase {
    private final double hopSpeed;

    private final double drop;

    private final double coastDivisor;

    private final float jump;

    private final double floor;

    public WatchdogNewTunedSpeed(double hopSpeed, double drop, double coastDivisor, float jump, double floor) {
        this.hopSpeed = hopSpeed;
        this.drop = drop;
        this.coastDivisor = coastDivisor;
        this.jump = jump;
        this.floor = floor;
    }

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
                    e.setY(mc.thePlayer.motionY = jumpMotion(jump));
                    moveSpeed = baseSpeed(hopSpeed);
                }
            }
            case 2 -> moveSpeed = lastDist - drop * (lastDist - baseSpeed());
            default -> {
                if (blocked()) stage = 0;
                moveSpeed = lastDist - lastDist / coastDivisor;
            }
        }

        moveSpeed = Math.max(moveSpeed, baseSpeed() * floor);
        setSpeed(e, moveSpeed);
    }
}
