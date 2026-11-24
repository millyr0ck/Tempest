package rip.tempest.modules.impl.player.nofall;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import static rip.tempest.utils.Util.mc;

public final class GrimServer19NoFall extends SubMode {
    private Property<Boolean> newestGrim;
    private boolean shouldNoFall;
    private volatile boolean shouldJump;

    private float fallen;

    public GrimServer19NoFall(Property<Boolean> newestGrim) {
        this.newestGrim = newestGrim;
    }

    @Override
    protected void onEnable() {
        shouldNoFall = false;
    }

    @EventLink(value = Priorities.MEDIUM)
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame() || mc.thePlayer.ticksExisted < 10 || mc.thePlayer.isCollidedHorizontally || e.isCancelled()) return;
        if (e.getPacket() instanceof S12PacketEntityVelocity velocity && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            if (velocity.getMotionY() > 0 || mc.thePlayer.ticksSinceVelocity <= 14 || mc.thePlayer.onGroundTicks <= 1) shouldJump = true;
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame() || mc.thePlayer.isCollidedHorizontally) return;
        float fall = fallen;
        if (mc.thePlayer.motionY > 0.1) shouldNoFall = false;
        if (fall > 3.0F) shouldNoFall = true;

        if (shouldNoFall && mc.thePlayer.onGround) {
            if (newestGrim.getValue()) {
                PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.01, mc.thePlayer.posZ, true));
            }
            e.setCancelled();
            PacketUtils.sendPacket(new C03PacketPlayer(true));
            mc.gameSettings.keyBindJump.setPressed(false);
            fall = 0.0F;
        }
        if (shouldNoFall) mc.gameSettings.keyBindJump.setPressed(false);

        if (newestGrim.getValue() && distanceToGround() > 5.0 && mc.thePlayer.offGroundTicks == 9 && mc.thePlayer.ticksSinceTeleport > 200) {
            mc.thePlayer.motionY = predictedMotion(mc.thePlayer.motionY, 10);
        }
        fallen = fall;
    };

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<MotionEvent> onMotionTrack = e -> {
        if (!e.isPre() || !inGame()) return;
        double drop = mc.thePlayer.lastTickPosY - mc.thePlayer.posY;
        if (drop > 0.0) fallen += (float) drop;
        if (e.isOnGround()) fallen = 0.0F;
    };

    @EventLink
    public final Listener<MoveEvent> onMoveInput = e -> {
        if (!inGame() || mc.thePlayer.isCollidedHorizontally) return;
        if (shouldJump && shouldNoFall) {
            e.setJump(true);
            shouldJump = false;
        }
    };

    private static double predictedMotion(double motion, int ticks) {
        for (int i = 0; i < ticks; i++) motion = (motion - 0.08) * 0.98F;
        return motion;
    }

    private static double distanceToGround() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int minX = MathHelper.floor_double(box.minX + 1.0E-7), maxX = MathHelper.floor_double(box.maxX - 1.0E-7);
        int minZ = MathHelper.floor_double(box.minZ + 1.0E-7), maxZ = MathHelper.floor_double(box.maxZ - 1.0E-7);
        double highest = Double.NEGATIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = MathHelper.floor_double(box.minY) - 1; y >= 0; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    AxisAlignedBB solid = state.getBlock().getCollisionBoundingBox(mc.theWorld, pos, state);
                    if (solid != null) {
                        highest = Math.max(highest, solid.maxY);
                        break;
                    }
                }
            }
        }
        return highest == Double.NEGATIVE_INFINITY ? Double.POSITIVE_INFINITY : box.minY - highest;
    }
}
