package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.server.S27PacketExplosion;

import static rip.tempest.utils.Util.mc;

public final class WatchdogSpeed extends SpeedBase {
    public enum BoostMode {
        Set,
        Addition
    }

    private NumberProperty startingSpeed, damageBoost;
    private Property<Boolean> damageJump;
    private ModeProperty<BoostMode> boostMode;

    private volatile S27PacketExplosion velocity;
    private Double verticalVelocity;

    public WatchdogSpeed(NumberProperty startingSpeed, NumberProperty damageBoost, ModeProperty<BoostMode> boostMode, Property<Boolean> damageJump) {
        this.startingSpeed = startingSpeed;
        this.damageBoost = damageBoost;
        this.boostMode = boostMode;
        this.damageJump = damageJump;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        velocity = null;
        verticalVelocity = null;
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        velocity = null;
        verticalVelocity = null;
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void receive(PacketReceiveEvent e) {
        super.receive(e);
        if (e.getPacket() instanceof S27PacketExplosion packet
                && (Math.abs(packet.func_149149_c()) > 0.0F || packet.func_149144_d() > 0.0F || Math.abs(packet.func_149147_e()) > 0.0F)) {
            velocity = packet;
        }
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        stage++;
        switch (stage) {
            case 1 -> {
                if (mc.thePlayer.onGround) {
                    if (MovementUtils.isMoving()) {
                        e.setY(mc.thePlayer.motionY = jumpMotion(0.42F));
                    }

                    if (moveSpeed <= baseSpeed()) {
                        moveSpeed = baseSpeed(startingSpeed.getValue());
                    } else if (!scaffold()) {
                        moveSpeed *= speedPotion() ? (jumpPotion() ? 1.8F : 1.95F) : 1.7000000476837158D;
                    } else {
                        moveSpeed *= 1.25D;
                    }
                }
            }
            case 2 -> {
                double boost = scaffold() ? 0.725D : 0.6600000262260437D;
                moveSpeed = lastDist - boost * (lastDist - baseSpeed());
            }
            default -> {
                if (blocked()) stage = 0;
                moveSpeed = lastDist - lastDist / 159.9D;
            }
        }

        S27PacketExplosion explosion = velocity;
        if (explosion != null) {
            double x = explosion.func_149149_c();
            double y = explosion.func_149144_d();
            double z = explosion.func_149147_e();
            double horizontal = Math.hypot(x, z);

            if (damageJump.getValue()) {
                verticalVelocity = verticalVelocity == null ? y : Math.max(verticalVelocity, y);
            }

            if (boostMode.getValue() == BoostMode.Set) {
                moveSpeed = Math.max(moveSpeed, horizontal * damageBoost.getValue());
            } else {
                moveSpeed += horizontal * damageBoost.getValue();
            }
            velocity = null;
        }

        if (verticalVelocity != null) {
            if (verticalVelocity > mc.thePlayer.motionY && verticalVelocity <= 0.41999998688697815D) {
                e.setY(mc.thePlayer.motionY = verticalVelocity);
            }
            verticalVelocity = null;
        }

        moveSpeed = Math.max(moveSpeed, baseSpeed());
        setSpeed(e, moveSpeed);
    }

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame()) {
            if (e.getPosY() % 0.015625D == 0.0D && MovementUtils.isMoving()) {
                e.setPosY(e.getPosY() + 1.0E-14D);
                e.setOnGround(true);
            }
            if (mc.thePlayer.motionY > 0.3D) {
                e.setOnGround(false);
            }
        }
        super.update(e);
    }
}
