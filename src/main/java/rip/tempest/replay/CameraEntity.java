package rip.tempest.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.world.World;

/**
 * The player you are while watching a replay: an invisible spectator that flies through
 * everything. The viewer moves it every frame (not every tick, so it stays smooth at any replay
 * speed, paused included), so a tick only keeps its interpolation still. It never sends anything
 * (the replay's connection drops whatever the game tries to send).
 *
 * <p>Based on ReplayMod's CameraEntity (GPL-3.0-or-later).
 */
public final class CameraEntity extends EntityPlayerSP {
    public CameraEntity(Minecraft mc, World world, NetHandlerPlayClient handler, StatFileWriter stats) {
        super(mc, world, handler, stats);
        this.noClip = true;
    }

    public void moveTo(double x, double y, double z) {
        setPosition(x, y, z);
        lastTickPosX = prevPosX = x;
        lastTickPosY = prevPosY = y;
        lastTickPosZ = prevPosZ = z;
    }

    public void turnTo(float yaw, float pitch) {
        prevRotationYaw = rotationYaw = yaw;
        prevRotationPitch = rotationPitch = Math.max(-90f, Math.min(90f, pitch));
    }

    @Override
    public void onUpdate() {
        ++ticksExisted;
        noClip = true;
        motionX = motionY = motionZ = 0;
        lastTickPosX = prevPosX = posX;
        lastTickPosY = prevPosY = posY;
        lastTickPosZ = prevPosZ = posZ;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        prevRotationYawHead = rotationYawHead = rotationYaw;
        prevCameraYaw = cameraYaw = 0f;
        prevCameraPitch = cameraPitch = 0f;
        prevDistanceWalkedModified = distanceWalkedModified;
    }

    @Override
    public void onLivingUpdate() {
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    public boolean isInRangeToRender3d(double x, double y, double z) {
        return false;
    }

    @Override
    public void swingItem() {
    }

    @Override
    public float getFovModifier() {
        return ReplayViewer.cameraFov();
    }
}
