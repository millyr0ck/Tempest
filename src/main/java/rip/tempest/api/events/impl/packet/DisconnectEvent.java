package rip.tempest.api.events.impl.packet;

import rip.tempest.api.events.Event;

public final class DisconnectEvent implements Event {

    private final String reason;

    public DisconnectEvent(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
