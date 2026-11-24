package rip.tempest.modules.impl.movement.phase;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;

import static rip.tempest.utils.Util.mc;

public final class ExhibitionHypixelPhase extends SubMode {
    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!inGame() || !mc.thePlayer.isCollidedHorizontally || mc.thePlayer.isOnLadder()) return;

        double multiplier = 0.162905382D + 0.00000000000094385093485943D * Math.random();
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
        double forward = mc.thePlayer.movementInput.moveForward * multiplier, strafe = mc.thePlayer.movementInput.moveStrafe * multiplier;
        double x = forward * lookX + strafe * lookZ;
        double z = forward * lookZ - strafe * lookX;

        PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX + x, mc.thePlayer.posY + 0.0000194385093485943D * Math.random(), mc.thePlayer.posZ + z, false));
        PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY - (0.48209538902354D + 0.00000000000094385093485943D * Math.random()), mc.thePlayer.posZ, false));
    };
}
