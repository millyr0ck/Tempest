package rip.tempest.modules.impl.player.nofall;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockAir;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;

import java.util.Arrays;

import static rip.tempest.utils.Util.mc;

public final class WatchdogNoFall extends SubMode {
    private static final double[] BLOCK_HEIGHTS = {0.015625, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0};

    private static final double REACH = 0.05;

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame() || mc.thePlayer.fallDistance < minFallDistance() || overVoid()) return;

        final int basePosY = (int) e.getPosY();
        final double baseOffset = e.getPosY() - basePosY;

        Double[] heights = Arrays.stream(BLOCK_HEIGHTS).boxed().toArray(Double[]::new);
        Arrays.sort(heights, (a, b) -> Double.compare(Math.abs(a - baseOffset), Math.abs(b - baseOffset)));

        double yDif = 2;
        double closest = 0;
        for (int i = 0; i < heights.length && (yDif > 0.0 || yDif <= -REACH); i++) {
            closest = heights[i];
            yDif = closest - baseOffset;
        }

        if (yDif != 2 && yDif >= -REACH && yDif < 0.0) {
            e.setOnGround(true);
            e.setPosY(basePosY + closest);
            mc.thePlayer.fallDistance = 0.0F;
        }
    };

    private static double minFallDistance() {
        double distance = ServerUtils.isOnHypixel() && "BLITZ SG".equals(ServerUtils.sidebarTitle()) ? 4.0D : 3.0D;
        if (mc.thePlayer.isPotionActive(Potion.jump)) {
            distance += mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1.0F;
        }
        return distance;
    }

    private static boolean overVoid() {
        for (double y = mc.thePlayer.posY; y > 0.0; y--) {
            if (!(mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ)).getBlock() instanceof BlockAir)) {
                return false;
            }
        }
        return true;
    }
}
