package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.player.BlockCollideEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.PlayerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.AxisAlignedBB;



import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Jesus", category = ModuleCategory.MOVEMENT)
public final class JesusModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);
    private final Property<Boolean> allowJump = new Property<>("Allow User Jump", true);

    private enum Mode {
        Vanilla,
        NCP
    }



    @EventLink
    public final Listener<BlockCollideEvent> onBlockAABB = event -> {
        if (event.getBlock() instanceof BlockLiquid && !mc.gameSettings.keyBindSneak.isKeyDown()) {
            final int x = event.getX();
            final int y = event.getY();
            final int z = event.getZ();

            event.setCollisionBoundingBox(AxisAlignedBB.fromBounds(x, y, z, x + 1, y + 1, z + 1));
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        setSuffix(mode.getValue().toString());
        if (!event.isPre()) return;
        if (mode.getValue() == Mode.NCP) {
            if (mc.thePlayer.ticksExisted % 2 == 0 && PlayerUtils.onLiquid()) {
                event.setPosY(event.getPosY() - 0.015625);
            }
        }
    };

    @EventLink
    public final Listener<JumpEvent> onJump = event -> {
        if (!allowJump.getValue() && PlayerUtils.onLiquid()) {
            event.setCancelled();
        }
    };
}
