package rip.tempest.api.events.impl.render;

import rip.tempest.api.events.CancellableEvent;
import net.minecraft.entity.EntityLivingBase;

public final class RenderNameTagEvent extends CancellableEvent {

    private final EntityLivingBase entityLivingBase;

    public RenderNameTagEvent(EntityLivingBase entityLivingBase) {
        this.entityLivingBase = entityLivingBase;
    }

    public EntityLivingBase getEntityLivingBase() {
        return entityLivingBase;
    }

}
