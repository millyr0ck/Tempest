package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.item.ItemBlock;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Safe Walk", category = ModuleCategory.MOVEMENT)
public final class SafeWalkModule extends Module {
    private final Property<Boolean> blocksOnly = new Property<>("Blocks Only", false);
    private final Property<Boolean> backwardsOnly = new Property<>("Backwards Only", false);

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> mc.thePlayer.safeWalk = mc.thePlayer.onGround && (!mc.gameSettings.keyBindForward.isKeyDown() || !backwardsOnly.getValue()) &&
            ((mc.thePlayer.inventory.getCurrentItem() != null && mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock) ||
                    !this.blocksOnly.getValue());
}
