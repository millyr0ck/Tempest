package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class AstralisLowHopSpeed extends SpeedBase {
    private boolean up;
    private int airTicks;

    @Override
    protected void onEnable() {
        super.onEnable();
        up = false;
        airTicks = 0;
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        if (!inGame()) return;

        airTicks = mc.thePlayer.onGround ? 0 : airTicks + 1;

        mc.thePlayer.motionY += switch (airTicks) {
            case 1 -> 0.05D;
            case 2 -> 0.012D;
            case 3 -> -0.135D;
            case 4 -> -0.2D;
            default -> 0.0D;
        };

        if (mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            HopUtil.strafeOrStop(MovementUtils.getSpeed());
            up = false;
            return;
        }

        if (airTicks == 1) {
            HopUtil.strafeOrStop(Math.max(perfectValue(0.33F, 0.4F, 0.48F), MovementUtils.getSpeed()));
        }

        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        if (HopUtil.solid(new BlockPos(x, y + mc.thePlayer.motionY, z)) && airTicks > 2) {
            HopUtil.strafeOrStop(MovementUtils.getSpeed());
        }

        if (airTicks >= 2 && (HopUtil.fullBlock(x, y + mc.thePlayer.motionY * 3.0D, z) || airTicks == 9)) {
            if (!up) {
                mc.thePlayer.motionY += 0.075D;
                up = true;
            } else {
                up = false;
            }
            HopUtil.strafeOrStop(MovementUtils.getSpeed());
        }
    };

    private static float perfectValue(float noSpeed, float speedOne, float speedTwo) {
        return switch (HopUtil.speedLevel()) {
            case 0 -> noSpeed;
            case 1 -> speedOne;
            case 2 -> speedTwo;
            default -> 0.0F;
        };
    }
}
