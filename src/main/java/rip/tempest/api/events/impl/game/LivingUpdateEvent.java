package rip.tempest.api.events.impl.game;

import rip.tempest.api.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.Entity;

@Getter
@AllArgsConstructor
public final class LivingUpdateEvent implements Event {
    private final Entity entity;
}