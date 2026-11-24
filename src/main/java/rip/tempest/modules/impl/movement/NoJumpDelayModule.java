package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Jump Delay", category = ModuleCategory.MOVEMENT)
public final class NoJumpDelayModule extends Module {
    public final Property<Boolean> test = new Property<>("Test", true);



    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre()) return;
        mc.thePlayer.jumpTicks = 0;
    };
}
