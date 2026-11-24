package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Water Bucket", category = ModuleCategory.PLAYER)
public final class WaterBucketModule extends Module {
    private final NumberProperty minimumFall = new NumberProperty("Minimum Fall", 5, 3, 40, 0.5);
    private final Property<Boolean> pickupWater = new Property<>("Pickup Water", true);

    private final Property<Boolean> silentAim = new Property<>("Silent Aim", true);
    private final Property<Boolean> switchToItem = new Property<>("Switch To Item", true);
    private final Property<Boolean> switchBack = new Property<>("Switch Back", true, () -> switchToItem.getValue());

    private static final long ATTEMPT_MILLIS = 500L;

    private static final double SNAP_SPEED = 50.0D;

    private enum Stage {
        Idle,

        Placed,

        PickedUp
    }

    private Stage stage = Stage.Idle;
    private long placedAt;

    private float aimPitch;
    private int previousSlot = -1;

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.thePlayer != null) finish();
        stage = Stage.Idle;
        placedAt = 0L;
        aimPitch = 0.0F;
        previousSlot = -1;
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = e -> {
        stage = Stage.Idle;
        aimPitch = 0.0F;
        previousSlot = -1;
    };

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (mc.thePlayer == null || mc.theWorld == null || mc.isGamePaused()) return;

        update();

        if (aimPitch != 0.0F) {
            RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, aimPitch), SNAP_SPEED, MovementFix.OFF);
        }
    };

    private void update() {
        if (stage == Stage.Placed) {
            boolean over = false;
            if (!mc.thePlayer.onGround) {
                over = System.currentTimeMillis() - placedAt > ATTEMPT_MILLIS;
            } else {
                if (pickupWater.getValue() && holding(Items.bucket)) {
                    MovingObjectPosition ground = groundInView();
                    if (ground != null) {
                        use(ground);
                        stage = silentAim.getValue() ? Stage.PickedUp : Stage.Idle;
                    }
                }
                over = stage != Stage.PickedUp;
            }
            if (over) finish();
            return;
        }

        if (stage == Stage.PickedUp) {
            finish();
            return;
        }

        if (aimPitch != 0.0F && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
            aimPitch = 0.0F;
            return;
        }

        boolean falling = !mc.thePlayer.capabilities.isFlying && !mc.thePlayer.capabilities.isCreativeMode
                && !mc.thePlayer.onGround && mc.thePlayer.fallDistance >= minimumFallNow();
        int slot = falling ? waterSlot() : -1;

        if (slot == -1) {
            if (aimPitch != 0.0F) finish();
            return;
        }

        if (!silentAim.getValue()) {
            if (mc.thePlayer.rotationPitch < 77.0F) return;
        } else if (aimPitch == 0.0F) {
            if (heightAboveGround() > 1.0D) {
                aimPitch = mc.thePlayer.movementInput.moveForward != 1.0F ? 90.0F : 81 + (int) (3.0D * Math.random());
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - placedAt < ATTEMPT_MILLIS) return;

        MovingObjectPosition ground = groundInView();

        if (ground == null) return;

        if (holding(Items.water_bucket) || select(slot)) {
            placedAt = now;
            use(ground);
            stage = Stage.Placed;
        } else {
            aimPitch = 0.0F;
        }
    }

    private void finish() {
        stage = Stage.Idle;
        aimPitch = 0.0F;

        if (previousSlot != -1) {
            if (switchBack.getValue()) mc.thePlayer.inventory.currentItem = previousSlot;
            previousSlot = -1;
        }
    }

    private static boolean holding(Item item) {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() == item;
    }

    private MovingObjectPosition groundInView() {
        float pitch = silentAim.getValue() ? aimPitch : mc.thePlayer.rotationPitch;
        double reach = mc.playerController.getBlockReachDistance();

        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLookCustom(mc.thePlayer.rotationYaw, pitch);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach), false, false, false);

        boolean top = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.sideHit == EnumFacing.UP;
        return top ? hit : null;
    }

    private static void use(MovingObjectPosition ground) {
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), ground.getBlockPos(), ground.sideHit, ground.hitVec);

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
    }

    private double minimumFallNow() {
        PotionEffect jumpBoost = mc.thePlayer.getActivePotionEffect(Potion.jump);
        return minimumFall.getValue() - 1.5D + (jumpBoost == null ? 0 : jumpBoost.getAmplifier() + 1);
    }

    private static double heightAboveGround() {
        Vec3 feet = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(feet, feet.addVector(0.0D, -100.0D, 0.0D), false, true, false);
        return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK ? -1.0D : feet.yCoord - hit.hitVec.yCoord;
    }

    private static int waterSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.water_bucket) return slot;
        }
        return -1;
    }

    private boolean select(int slot) {
        if (!switchToItem.getValue()) return false;

        if (previousSlot == -1) previousSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        return true;
    }
}
