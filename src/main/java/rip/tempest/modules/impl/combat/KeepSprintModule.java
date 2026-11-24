package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.player.HitSlowDownEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Keep Sprint", category = ModuleCategory.COMBAT)
public final class KeepSprintModule extends Module {

    public static final Property<Boolean> onlyInAirSprint = new Property<>("Keep Sprint Only In Air", false);

    @EventLink
    public final Listener<HitSlowDownEvent> hitSlowDownEventListener = e -> {
        if (!onlyInAirSprint.getValue() || !mc.thePlayer.onGround) {
            e.setSprint(true);
            e.setSlowDown(1.0);
        }
    };
}
