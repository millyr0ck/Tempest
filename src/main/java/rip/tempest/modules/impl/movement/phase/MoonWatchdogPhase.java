package rip.tempest.modules.impl.movement.phase;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.BlockCollideEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S02PacketChat;

import static rip.tempest.utils.Util.mc;

public final class MoonWatchdogPhase extends SubMode {
    private static final double STEP = 0.2D;

    private boolean newMode;

    public MoonWatchdogPhase(boolean newMode) {
        this.newMode = newMode;
    }

    @EventLink
    public final Listener<BlockCollideEvent> onCollide = e -> {
        if (inGame() && PlayerUtils.insideBlock()) {
            e.setCollisionBoundingBox(null);
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (inGame() && PlayerUtils.insideBlock()) {
            e.setY(mc.thePlayer.motionY = 0.0D);
            MovementUtils.setSpeed(e, newMode ? 0.26D : 0.3D);
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!inGame()) return;

        if (e.isPre()) {
            e.setPitch(90.0F);
            return;
        }
        if (!mc.thePlayer.isCollidedHorizontally || mc.thePlayer.isOnLadder()) return;

        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
        double forward = mc.thePlayer.movementInput.moveForward * STEP, strafe = mc.thePlayer.movementInput.moveStrafe * STEP;
        double x = forward * lookX + strafe * lookZ;
        double z = forward * lookZ - strafe * lookX;

        PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX + x, mc.thePlayer.posY, mc.thePlayer.posZ + z, false));
        if (newMode) {
            for (int i = 1; i < 10; i++) {
                PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, 8.9D, mc.thePlayer.posZ, false));
            }
        } else {
            for (int i = 0; i < 10; i++) {
                PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, 1 + i, mc.thePlayer.posZ, false));
            }
        }
        mc.thePlayer.setPosition(mc.thePlayer.posX + x, mc.thePlayer.posY, mc.thePlayer.posZ + z);
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S02PacketChat chat
                && chat.getChatComponent().getUnformattedText().contains("You cannot go past the border.")) {
            e.setCancelled();
        }
    };
}
