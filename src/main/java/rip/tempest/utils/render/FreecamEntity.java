package rip.tempest.utils.render;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class FreecamEntity extends Entity {
    public FreecamEntity(World world, double x, double y, double z, float yaw, float pitch) {
        super(world);
        this.noClip = true;
        this.setPosition(x, y, z);
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        this.lastTickPosX = x;
        this.lastTickPosY = y;
        this.lastTickPosZ = z;
        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
        this.prevRotationYaw = yaw;
        this.prevRotationPitch = pitch;
    }

    @Override
    public float getEyeHeight() {
        return 0.0F;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompound) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {
    }
}
