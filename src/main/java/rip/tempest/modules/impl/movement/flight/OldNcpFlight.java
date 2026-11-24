package rip.tempest.modules.impl.movement.flight;

import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;

import static rip.tempest.utils.Util.mc;

public final class OldNcpFlight extends SubMode {
    private static final double BASE = 0.2873D;

    private NumberProperty speed, decay, boostTimer, boostTicks;
    private Property<Boolean> waitForDamage, useTimer;

    private double moveSpeed;
    private int stage, boostTick;
    private boolean damage;

    public OldNcpFlight(NumberProperty speed, NumberProperty decay, NumberProperty boostTimer, NumberProperty boostTicks,
                        Property<Boolean> waitForDamage, Property<Boolean> useTimer) {
        this.speed = speed;
        this.decay = decay;
        this.boostTimer = boostTimer;
        this.boostTicks = boostTicks;
        this.waitForDamage = waitForDamage;
        this.useTimer = useTimer;
    }

    @Override
    protected void onEnable() {
        moveSpeed = 0.0D;
        stage = 0;
        boostTick = 0;
        damage = false;
    }

    @Override
    protected void onDisable() {
        if (inGame()) {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }
        mc.timer.timerSpeed = 1.0F;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame()) return;
        if (e.getPacket() instanceof S12PacketEntityVelocity velocity && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            damage = true;
            e.setCancelled();
        } else if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            moveSpeed = base();
            boostTick = Integer.MAX_VALUE;
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame()) return;
        if (!damage && stage == 1) {
            MovementUtils.setSpeed(e, 0.0D);
            return;
        }
        if (mc.thePlayer.isCollidedHorizontally || !MovementUtils.isMoving()) {
            moveSpeed = base();
        }

        switch (stage) {
            case 0:
                if (mc.thePlayer.fallDistance < 3.75F) {
                    for (int i = 0; i < 60; i++) {
                        vClip(0.0625D, false);
                        vClip(0.0D, false);
                    }
                } else {
                    stage = 2;
                }
                vClip(0.0D, true);
                if (!waitForDamage.getValue()) damage = true;
                break;
            case 1:
                moveSpeed = MovementUtils.getBaseMoveSpeed(0.3D);
                break;
            case 2:
                mc.thePlayer.motionY = 0.3999D;
                e.setY(0.3999D);
                moveSpeed *= 2.149D;

            case 3:
                moveSpeed = base() * speed.getValue();
                boostTick = 0;
                break;
            default:
                moveSpeed -= moveSpeed / decay.getValue();
                mc.thePlayer.motionY = 0.0D;
                e.setY(0.0D);
                if (!onGround(1.0E-4D)) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY - 1.0E-14D, mc.thePlayer.posZ);
                }
                break;
        }

        if (stage >= 3) {
            float normal = useTimer.getValue() ? 1.0766F : 1.0F;
            float boost = boostTimer.getValue().floatValue();
            int ticks = boostTicks.getValue().intValue();
            mc.timer.timerSpeed = boostTick < ticks && boost > normal ? boost - (boost - normal) * boostTick / ticks : normal;
            if (boostTick < Integer.MAX_VALUE) boostTick++;
        }

        stage++;
        mc.thePlayer.jumpMovementFactor = 0.0F;
        MovementUtils.setSpeed(e, Math.max(moveSpeed, base()));
    };

    private static double base() {
        return MovementUtils.getBaseMoveSpeed(BASE);
    }

    private static void vClip(double offset, boolean ground) {
        PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY + offset, mc.thePlayer.posZ, ground));
    }

    private static boolean onGround(double height) {
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, -height, 0.0D)).isEmpty();
    }
}
