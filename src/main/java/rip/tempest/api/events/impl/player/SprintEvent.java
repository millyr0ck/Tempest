package rip.tempest.api.events.impl.player;

import rip.tempest.api.events.CancellableEvent;
import rip.tempest.api.events.Event;

public final class SprintEvent implements Event {

    private boolean sprinting;

    public SprintEvent(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }
}
