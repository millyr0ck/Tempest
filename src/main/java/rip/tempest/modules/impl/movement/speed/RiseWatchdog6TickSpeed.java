package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.player.PostStrafeEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockStairs;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import static rip.tempest.utils.Util.mc;

public final class RiseWatchdog6TickSpeed extends SpeedBase {
    private int tickCounter;
    private boolean onStairs, started;

    @Override
    protected void onEnable() {
        super.onEnable();
        tickCounter = 0;
        onStairs = false;
        started = false;
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        if (!inGame()) return;
        if (mc.thePlayer.onGround) MovementUtils.stop();
        mc.thePlayer.stepHeight = 0.6F;
    }

    private boolean setup() {
        return tickCounter < 23 && tickCounter > 7;
    }

    private static int csk() {
        return mc.thePlayer.ticksSinceVelocity;
    }

    @EventLink
    public final Listener<MoveEvent> onMoveInput = e -> {
        if (started && setup()) e.setJump(false);
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (started && setup()) {
            e.setX(0.0D);
            e.setZ(0.0D);
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame()) return;
        EntityPlayerSP player = mc.thePlayer;

        if (!started) {
            if (!player.onGround) return;
            started = true;
            player.jump();
            return;
        }

        if (player.onGround) onStairs = false;

        AxisAlignedBB box = player.getEntityBoundingBox();
        for (double x = box.minX; x < box.maxX && !onStairs; x += 0.3D) {
            for (double z = box.minZ; z < box.maxZ; z += 0.3D) {
                if (mc.theWorld.getBlockState(new BlockPos(x, box.minY - 0.05D, z)).getBlock() instanceof BlockStairs) {
                    onStairs = true;
                    break;
                }
            }
        }

        double moveDir = MathHelper.wrapAngleTo180_double(Math.toDegrees(MovementUtils.direction()));
        double motionDir = MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(player.motionZ, player.motionX)) - 90.0D);
        if (player.hurtTime == 9 && tickCounter < 23) tickCounter = 23;

        int air = player.offGroundTicks;
        boolean speed = player.isPotionActive(Potion.moveSpeed);

        if (tickCounter > 24 && !onStairs && csk() > 5) {
            if (air == 0) {
                if (tickCounter <= 48 && (speed || tickCounter <= 66)) {
                    HopUtil.strafe(MovementUtils.getAllowedHorizontalDistance() - 0.01D);
                } else {
                    HopUtil.strafe(MovementUtils.getAllowedHorizontalDistance());
                }
                if (player.onGround) player.jump();
            }

            if (air == 1) {
                int level = HopUtil.speedLevel();
                HopUtil.strafe(level >= 2 ? 0.48D : level == 1 ? 0.405D : 0.33D);
                player.motionY = 0.31D;
            }

            if (air == 2) {
                if (speed) {
                    player.motionX *= 1.005D;
                    player.motionZ *= 1.005D;
                }
                player.motionY -= 0.11D;
            }

            if (air == 3) player.motionY -= 0.22D;

            if (air == 4) {
                if (speed) {
                    if (tickCounter > 66) {
                        player.motionX *= 1.01D;
                        player.motionZ *= 1.01D;
                    }
                } else {
                    player.motionX *= 1.005D;
                    player.motionZ *= 1.005D;
                }
            }

            if (air == 5 && HopUtil.collides(player.motionY * 2.0D)) {
                player.motionY += 0.085D;
                if (speed) {
                    if (tickCounter > 66) HopUtil.strafe(MovementUtils.getAllowedHorizontalDistance() * 1.069D);
                } else {
                    HopUtil.strafe();
                }
            }

            if (HopUtil.block(player.posX, player.posY + player.motionY, player.posZ) != Blocks.air) {
                if (speed) {
                    if (tickCounter > 66) HopUtil.strafe(MovementUtils.getAllowedHorizontalDistance() * 1.059D);
                } else {
                    HopUtil.strafe();
                }
            }

            player.stepHeight = 0.2F;
            if (MovementUtils.getSpeed() < 0.125D) HopUtil.strafe(0.125D);

            if (angleDifference(moveDir, motionDir) > 40.0D && speed) {
                player.motionX *= 0.74D;
                player.motionZ *= 0.74D;
            }
        }

        if (tickCounter < 8 || onStairs || csk() < 6) {
            if (player.onGround) player.jump();

            if (player.onGround && !speed) {
                HopUtil.strafe(MovementUtils.getAllowedHorizontalDistance() - 0.01D);
            } else if (player.onGround) {
                HopUtil.strafe(0.26D);
            }

            if (MovementUtils.getSpeed() < 0.125D) HopUtil.strafe(0.125D);

            if (air == 1) {
                HopUtil.strafe();
                player.motionY += 0.057F;
            }
            if (air == 3) player.motionY -= 0.1309F;
            if (air == 4) player.motionY -= 0.2D;
        }

        if (tickCounter == 24) HopUtil.strafe(0.125D);
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame() || !started) return;

        tickCounter++;
        if (tickCounter < 23) {
            if (mc.thePlayer.onGround && tickCounter > 3) {
                e.setPosY(e.getPosY() + (tickCounter % 2 != 0 ? 0.296875D : 0.001D));
                e.setOnGround(false);
            }
        } else if (mc.thePlayer.onGround) {
            e.setPosY(e.getPosY() + 0.001D);
        }
    };

    @EventLink
    public final Listener<JumpEvent> onJump = e -> {
        if (!started) return;
        e.setJumpMotion(tickCounter >= 23 && !onStairs && csk() >= 6 ? 0.4F : 0.42F);
    };

    @EventLink
    public final Listener<PostStrafeEvent> onPostStrafe = e -> {
        if (!inGame()) return;
        double moveDir = MathHelper.wrapAngleTo180_double(Math.toDegrees(MovementUtils.direction()));
        double motionDir = MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(mc.thePlayer.motionZ, mc.thePlayer.motionX)) - 90.0D);
        if (angleDifference(moveDir, motionDir) > 90.0D) {
            HopUtil.strafeAt(MovementUtils.getSpeed(), (float) motionDir - 180.0F);
        }
    };

    private static double angleDifference(double a, double b) {
        return Math.min(Math.abs(a - b), Math.min(Math.abs(a - 360.0D) - Math.abs(b), Math.abs(b - 360.0D) - Math.abs(a)));
    }
}
