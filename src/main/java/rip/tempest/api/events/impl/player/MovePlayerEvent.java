package rip.tempest.api.events.impl.player;

import rip.tempest.api.events.CancellableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public final class MovePlayerEvent extends CancellableEvent {
    private double x, y, z;
}
