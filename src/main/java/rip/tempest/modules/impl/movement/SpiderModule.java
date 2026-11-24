package rip.tempest.modules.impl.movement;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.RotationProcess;
import rip.tempest.processes.SlotProcess;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.RayCastUtils;
import rip.tempest.utils.mc.ScaffoldUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Spider", category = ModuleCategory.MOVEMENT)
public final class SpiderModule extends Module {
    private static ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);

    private final Property<Boolean> hideSwap = new Property<>("Hide Swap", true, () -> mode.getValue() == Mode.Grim);

    private final NumberProperty lookSpeed = new NumberProperty("Look Speed", 6, () -> mode.getValue() == Mode.Grim, 1, 10, 0.5);

    public enum Mode {
        Vanilla,
        Verus,
        Grim
    }

    private int climbFloor = Integer.MIN_VALUE;

    private boolean climbing;

    @Override
    public void onDisable() {
        stopClimbing();
        super.onDisable();
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        switch (mode.getValue()) {
            case Verus -> {
                if (mc.thePlayer.isCollidedHorizontally) {
                    if (mc.thePlayer.ticksExisted % 2 == 0) {
                        mc.thePlayer.jump();
                    }
                }
            }
            case Vanilla -> {
                if (mc.thePlayer.isCollidedHorizontally) {
                    mc.thePlayer.jump();
                }
            }
            case Grim -> climbGrim();
        }
    };

    @EventLink
    public final Listener<MoveEvent> onMoveInput = event -> {
        if (wantsToClimb() && mc.thePlayer.onGround) event.setJump(true);
    };

    private void climbGrim() {
        if (!wantsToClimb()) {
            stopClimbing();
            return;
        }

        SlotProcess.swap(ScaffoldUtils.getBlockSlot(), hideSwap.getValue());
        climbing = true;
        if (mc.thePlayer.onGround) climbFloor = MathHelper.floor_double(mc.thePlayer.posY + 1.0E-3);
        if (climbFloor == Integer.MIN_VALUE) return;

        RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, (float) (90.0 - Math.random() * 0.1)),
                lookSpeed.getValue(), MovementFix.NORMAL);

        BlockPos space = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), climbFloor, MathHelper.floor_double(mc.thePlayer.posZ));
        BlockPos support = space.down();
        if (mc.thePlayer.onGround || mc.thePlayer.posY < climbFloor + 1.0) return;
        if (!mc.theWorld.getBlockState(space).getBlock().isReplaceable(mc.theWorld, space)) return;
        if (mc.theWorld.getBlockState(support).getBlock().getCollisionBoundingBox(mc.theWorld, support, mc.theWorld.getBlockState(support)) == null) return;

        Vector2f sent = RotationProcess.lastServerRotations;
        if (sent == null || !RayCastUtils.overBlock(sent, EnumFacing.UP, support, true)) return;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !support.equals(over.getBlockPos()) || over.sideHit != EnumFacing.UP) return;

        ItemStack blocks = mc.thePlayer.inventory.getCurrentItem();
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, blocks, support, EnumFacing.UP, over.hitVec)) {
            PacketUtils.sendPacket(new C0APacketAnimation());
        }
    }

    private boolean wantsToClimb() {
        if (mode.getValue() != Mode.Grim || mc.thePlayer == null) return false;
        ScaffoldModule scaffold = Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class);
        return mc.thePlayer.isCollidedHorizontally && mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.thePlayer.isOnLadder() && (scaffold == null || !scaffold.isEnabled())
                && ScaffoldUtils.getBlockSlot() != -1;
    }

    private void stopClimbing() {
        if (climbing) {
            SlotProcess.swapBack();
            climbing = false;
        }
        climbFloor = Integer.MIN_VALUE;
    }
}
