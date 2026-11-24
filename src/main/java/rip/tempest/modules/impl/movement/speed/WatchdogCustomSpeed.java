package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

public final class WatchdogCustomSpeed extends SpeedBase {
    private Property<Boolean> strafeBypass;
    private NumberProperty jumpValue, groundFriction, groundBoost;

    public WatchdogCustomSpeed(Property<Boolean> strafeBypass, NumberProperty jumpValue, NumberProperty groundFriction, NumberProperty groundBoost) {
        this.strafeBypass = strafeBypass;
        this.jumpValue = jumpValue;
        this.groundFriction = groundFriction;
        this.groundBoost = groundBoost;
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame() && strafeBypass.getValue()) {
            offGroundHeight(e);
        }
        super.update(e);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        double boost = groundBoost.getValue();
        if (speedPotion() || jumpPotion()) {
            boost -= 0.2D;
        }

        hopCycle(e, jumpValue.getValue().floatValue(),
                scaffold() ? 1.399999976158142D : boost,
                speedPotion() ? groundFriction.getValue() : groundFriction.getValue() + 0.10000000149011612D,
                159.0D);
    }
}
