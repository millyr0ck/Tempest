package rip.tempest.api.events.impl.game;

import rip.tempest.api.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.EntityLivingBase;

@Getter
@AllArgsConstructor
public class EntityHurtSoundEvent extends CancellableEvent {
    private final EntityLivingBase entity;
}
