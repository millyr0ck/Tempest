package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import static rip.tempest.utils.Util.mc;

public final class YPortNcpSpeed extends SpeedBase {
    @Override
    protected void onEnable() {
        super.onEnable();
        mc.timer.timerSpeed = 1.0F;
    }

    @Override
    protected void onDisable() {
        if (mc.thePlayer != null) mc.thePlayer.speedInAir = 0.02F;
        mc.timer.timerSpeed = 1.0F;
        super.onDisable();
    }

    @EventLink
    public final Listener<JumpEvent> onJump = e -> {
        if (inGame()) e.setCancelled();
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre()) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;

        if (p.isInWater() || p.isInLava() || p.isOnLadder() || p.isInWeb) {
            p.speedInAir = 0.02F;
            return;
        }

        boolean moving = p.movementInput.moveForward != 0 || p.movementInput.moveStrafe != 0;

        if (moving) {
            if (p.onGround) {
                p.jump();
                p.speedInAir = 0.0223F;
            }
            strafe(p);
        } else {
            p.motionX = 0;
            p.motionZ = 0;
        }
    };

    @Override
    protected void move(MovePlayerEvent e) {}

    @Override
    protected void receive(PacketReceiveEvent e) {
        super.receive(e);
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            if (mc.thePlayer != null) mc.thePlayer.speedInAir = 0.02F;
            mc.timer.timerSpeed = 1.0F;
        }
    }

    private static void strafe(EntityPlayerSP p) {
        double speed = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        float yaw     = p.rotationYaw;
        float forward = p.movementInput.moveForward;
        float strafe  = p.movementInput.moveStrafe;

        if (forward == 0 && strafe == 0) {
            p.motionX = 0;
            p.motionZ = 0;
            return;
        }

        if (forward != 0) {
            if (strafe > 0) yaw += (forward > 0 ? -45 : 45);
            else if (strafe < 0) yaw += (forward > 0 ? 45 : -45);
            strafe  = 0;
            forward = forward > 0 ? 1 : -1;
        }

        double mx = Math.cos(Math.toRadians(yaw + 90));
        double mz = Math.sin(Math.toRadians(yaw + 90));
        p.motionX = forward * speed * mx + strafe * speed * mz;
        p.motionZ = forward * speed * mz - strafe * speed * mx;
    }
}
