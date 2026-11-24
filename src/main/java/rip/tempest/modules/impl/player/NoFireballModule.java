package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.BadPacketsProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Fireball", category = ModuleCategory.PLAYER)
public final class NoFireballModule extends Module {

    private final Property<Boolean> rotate = new Property<>("Rotate", true);

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (BadPacketsProcess.bad()) return;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityFireball && entity.getDistanceToEntity(mc.thePlayer) < 5) {
                if (this.rotate.getValue()) {
                    RotationProcess.setRotations(RotationUtils.calculate(entity), 10, MovementFix.NORMAL);
                }
                mc.clickMouse();
                break;
            }
        }
    };

}
