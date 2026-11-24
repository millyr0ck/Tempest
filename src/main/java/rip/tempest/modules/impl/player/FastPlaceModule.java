package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.item.ItemBlock;

import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;


import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Fast Place", category = ModuleCategory.PLAYER)
public final class FastPlaceModule extends Module {

    private final NumberProperty delay = new NumberProperty("Delay", 1, 0, 3, 1);

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        setSuffix(delay.getValue().toString());

        if (mc.thePlayer == null || mc.thePlayer.inventory == null || mc.thePlayer.inventory.getCurrentItem() == null || !event.isPre()) {
            return;
        }

        if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock) {
            mc.rightClickDelayTimer = Math.min(mc.rightClickDelayTimer, this.delay.getValue().intValue());
        }
    };
}
