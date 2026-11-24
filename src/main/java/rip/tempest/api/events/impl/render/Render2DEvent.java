package rip.tempest.api.events.impl.render;

import rip.tempest.api.events.Event;
import net.minecraft.client.gui.ScaledResolution;

public final class Render2DEvent implements Event {

    private final float partialTicks;

    public Render2DEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

}
