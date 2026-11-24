package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.LagProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

@ModuleInfo(label = "Fake Lag", category = ModuleCategory.CLIENT)
public class FakeLagModule extends Module {

    private final NumberProperty delay = new NumberProperty("Delay", 200, 50, 2000, 5);
    private final Property<Boolean> teleports = new Property<>("Delay Teleports", false);
    private final Property<Boolean> velocity = new Property<>("Delay Velocity", false);
    private final Property<Boolean> entities = new Property<>("Delay Entity Movements", false);



    @EventLink
    public Listener<MotionEvent> motionEventListener = event -> {
        setSuffix(delay.getValue().toString());
        if (event.isPre()) return;
        LagProcess.spoof(delay.getValue().intValue(), true, velocity.getValue(),
                teleports.getValue(), entities.getValue());
    };
}
