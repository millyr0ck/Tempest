package rip.tempest.api.events.impl.player;

import rip.tempest.api.events.CancellableEvent;
import net.minecraft.entity.Entity;

public class AttackEvent extends CancellableEvent {

    public AttackEvent(Entity target) {
        this.target = target;
    }

    public Entity target;
}