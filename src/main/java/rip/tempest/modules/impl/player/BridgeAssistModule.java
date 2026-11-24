package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Bridge Assist", category = ModuleCategory.PLAYER)
public final class BridgeAssistModule extends Module {
    private final ModeProperty<SneakMode> mode = new ModeProperty<>("Mode", SneakMode.SNEAK_INPUT);
    private final NumberProperty edgeOffset = new NumberProperty("Edge Offset", 0, this::sneakMode, 0, 0.3, 0.01);
    private final NumberProperty unsneakDelay = new NumberProperty("Unsneak Delay", 50, this::sneakMode, 50, 300, 5);
    private final ModeProperty<Doubleshift> onDoubleshift = new ModeProperty<>("On Doubleshift", Doubleshift.NONE, this::sneakMode);
    private final ModeProperty<OnJump> onJump = new ModeProperty<>("On Jump", OnJump.SNEAK, this::sneakMode);
    private final NumberProperty sneakOnJump = new NumberProperty("Sneak On Jump", 0, () -> sneakMode() && onJump.getValue() == OnJump.SNEAK, 0, 500, 5);
    private final NumberProperty sneakEvery = new NumberProperty("Sneak Every", 1, () -> mode.getValue() == SneakMode.PRE_PLACE, 1, 8, 1);
    private final Property<Boolean> sneakKeyPressed = new Property<>("Only With Sneak Key", false, this::sneakMode);
    private final Property<Boolean> holdingBlocks = new Property<>("Only Holding Blocks", false);
    private final Property<Boolean> holdingRightClick = new Property<>("Only Holding Right Click", false);
    private final Property<Boolean> lookingDown = new Property<>("Only Looking Down", false);
    private final Property<Boolean> notMovingForward = new Property<>("Only Not Moving Forward", false);
    private final Property<Boolean> blocksHud = new Property<>("Blocks HUD", false);

    public enum SneakMode {
        SNEAK_INPUT("Sneak (Input)"), SNEAK_PACKET("Sneak (Packet)"), PRE_PLACE("Pre Place");

        private final String label;

        SneakMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Doubleshift {
        NONE("None"), AIM("Aim"), PREVENT("Prevent");

        private final String label;

        Doubleshift(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum OnJump {
        NOTHING("Nothing"), SNEAK("Sneak"), AIM("Aim");

        private final String label;

        OnJump(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private boolean sneakInjected, blockJustPlaced, sneakRestorePending;

    private int jumpHoldStart = -1, jumpHoldTicks = -1, releaseStart = -1, releaseTicks = -1;

    private int placements, pulsesQueued;
    private boolean pulseActive;

    private boolean clicksAllowed = true, sawEdgeHit;
    private long edgeHitAt = System.currentTimeMillis();

    private long hudStart, lastPlacement;

    @Override
    public void onDisable() {
        if (inGame() && inputMode()) KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneakKeyDown());
        sneakInjected = false;
        placements = pulsesQueued = 0;
        pulseActive = false;
        hudStart = lastPlacement = 0;
        clicksAllowed = true;
        sawEdgeHit = false;
        edgeHitAt = System.currentTimeMillis();
        clearDelays();
        super.onDisable();
    }

    @EventLink
    public final Listener<MoveEvent> onMove = e -> {
        setSuffix(mode.getValue().toString());
        if (!inGame()) return;

        if (mode.getValue() == SneakMode.PRE_PLACE) {
            if (pulseActive()) {
                e.setSneak(true);
                if (!pastEdge()) pulseActive = false;
            }
            return;
        }
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;

        boolean keyDown = sneakKeyDown();
        boolean keyCondition = sneakKeyPressed.getValue();
        if (keyDown && !keyCondition) {
            clearDelays();
        } else if (keyCondition && !keyDown) {
            restoreInput(e);
        } else if (notMovingForward.getValue() && e.getForward() > 0) {
            conditionsFailed(e);
        } else if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) {
            conditionsFailed(e);
        } else if (holdingBlocks.getValue() && !holdingBlock()) {
            conditionsFailed(e);
        } else if (holdingRightClick.getValue() && !GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)) {
            conditionsFailed(e);
        } else if (keyCondition && e.getForward() == 0 && e.getStrafe() == 0) {
            restoreInput(e);
        } else if (onJump.getValue() == OnJump.SNEAK && e.isJump() && mc.thePlayer.onGround && (e.getForward() != 0 || e.getStrafe() != 0)) {
            jumpHoldStart = mc.thePlayer.ticksExisted;
            jumpHoldTicks = Math.max(1, randomTicks(sneakOnJump.getValue() / 50.0));
            startSneak(e, true);
        } else {
            double edge = edgeDistance();
            if (!Double.isNaN(edge)) {
                if (edge > edgeOffset.getValue()) {
                    startSneak(e, true);
                } else if (sneakInjected) {
                    tryRelease(e);
                } else if (keyCondition) {
                    restoreInput(e);
                }
            } else if (e.isJump() && (onJump.getValue() != OnJump.SNEAK || sneakOnJump.getValue() <= 0 || e.getForward() == 0 && e.getStrafe() == 0)) {
                if (sneakInjected) tryRelease(e);
            } else if (mc.thePlayer.onGround) {
                startSneak(e, true);
            } else if (sneakInjected) {
                tryRelease(e);
            }
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!(e.getPacket() instanceof C08PacketPlayerBlockPlacement placement) || placement.getPlacedBlockDirection() == 255) return;
        long now = System.currentTimeMillis();
        if (!recentPlacement(now)) hudStart = now;
        lastPlacement = now;

        if (mode.getValue() == SneakMode.PRE_PLACE && countsForPreplace(placement)) {
            if (++placements >= sneakEvery.getValue().intValue()) {
                placements = 0;
                pulsesQueued = 1;
            }
        }
        if (sneakInjected && sneakKeyPressed.getValue()) blockJustPlaced = true;
    };

    @EventLink
    public final Listener<RightClickEvent> onRightClick = e -> {
        if (!sneakMode() || !inGame()) return;
        updatePrevent();
        if (onDoubleshift.getValue() == Doubleshift.PREVENT && !clicksAllowed) e.setCancelled();
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (!inGame()) return;
        boolean jumpAim = sneakMode() && onJump.getValue() == OnJump.AIM;
        boolean doubleshiftAim = doubleshiftAim();
        if (mode.getValue() != SneakMode.PRE_PLACE && !jumpAim && !doubleshiftAim) return;
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;
        if (jumpAim && !doubleshiftAim && !GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) return;
        if (!holdingBlock()) return;
        if (holdingRightClick.getValue() && !GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)) return;
        if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) return;
        if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0) return;

        float pitch = RotationProcess.isActive() && RotationProcess.rotations != null ? RotationProcess.rotations.y : mc.thePlayer.rotationPitch;
        float aim = placementPitch(pitch, mc.playerController.getBlockReachDistance());
        if (!Float.isNaN(aim)) RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, aim), 0.6, MovementFix.OFF);
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (!inGame()) return;
        updatePrevent();
        if (!blocksHud.getValue()) return;
        float alpha = hudAlpha(System.currentTimeMillis());
        if (alpha < 0.05f) return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;
        String count = "§l" + hotbarBlocks(), label = " Blocks";
        int countWidth = fr.getStringWidth(count), width = countWidth + fr.getStringWidth(label);
        float x = sr.getScaledWidth() / 2f - width / 2f, y = sr.getScaledHeight() / 2f + 12f;
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255))) << 24;
        GlStateManager.enableBlend();
        fr.drawStringWithShadow(count, x, y, ColorProcess.getColor().getRGB() & 0xFFFFFF | a);
        fr.drawStringWithShadow(label, x + countWidth, y, 0xFFFFFF | a);
        GlStateManager.disableBlend();
    };

    private void startSneak(MoveEvent e, boolean freshEdge) {
        if (inputMode()) KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        e.setSneak(true);
        sneakInjected = true;
        if (freshEdge) releaseStart = -1;
        restorePendingSneak(e);
    }

    private void tryRelease(MoveEvent e) {
        if (keepSneaking()) startSneak(e, false);
        else stopSneak(e);
    }

    private void stopSneak(MoveEvent e) {
        if (inputMode()) {
            boolean physical = physicalSneak();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), physical);
            e.setSneak(physical);
        } else if (!sneakKeyPressed.getValue()) {
            e.setSneak(false);
        } else if (!sneakInjected || !sneakKeyDown() || !blockJustPlaced && mc.thePlayer.onGround) {
            if (sneakRestorePending) e.setSneak(false);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            e.setSneak(false);
            sneakRestorePending = true;
        }
        sneakInjected = false;
        blockJustPlaced = false;
        clearDelays();
    }

    private void restorePendingSneak(MoveEvent e) {
        if (sneakRestorePending && sneakKeyDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            e.setSneak(true);
        }
        sneakRestorePending = false;
    }

    private void restoreInput(MoveEvent e) {
        sneakInjected = false;
        clearDelays();
        if (inputMode()) {
            boolean physical = physicalSneak();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), physical);
            e.setSneak(physical);
        } else if (sneakKeyPressed.getValue()) {
            restorePendingSneak(e);
        }
    }

    private void conditionsFailed(MoveEvent e) {
        sneakInjected = false;
        clearDelays();
        if (inputMode()) {
            boolean down = sneakKeyDown();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), down);
            e.setSneak(down);
        } else if (sneakKeyPressed.getValue()) {
            restorePendingSneak(e);
        }
    }

    private boolean keepSneaking() {
        int now = mc.thePlayer.ticksExisted;
        if (releaseStart < 0 && jumpHoldStart < 0) {
            releaseStart = now;
            releaseTicks = randomTicks((unsneakDelay.getValue() - 50) / 50.0);
        }
        if (jumpHoldStart >= 0 && now - jumpHoldStart < jumpHoldTicks) return true;
        return releaseStart >= 0 && now - releaseStart < releaseTicks;
    }

    private void clearDelays() {
        jumpHoldStart = jumpHoldTicks = releaseStart = releaseTicks = -1;
    }

    private static int randomTicks(double ticks) {
        int whole = (int) ticks;
        return whole + (Math.random() < ticks - whole ? 1 : 0);
    }

    private boolean pulseActive() {
        if (pulsesQueued > 0) {
            pulsesQueued--;
            pulseActive = true;
        }
        return pulseActive;
    }

    private boolean countsForPreplace(C08PacketPlayerBlockPlacement placement) {
        if (!inGame() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return false;
        if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0) return false;
        if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) return false;
        if (holdingRightClick.getValue() && !GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)) return false;
        return !holdingBlocks.getValue() || placement.getStack() != null && placement.getStack().getItem() instanceof ItemBlock;
    }

    private boolean pastEdge() {
        double edge = edgeDistance();
        return Double.isNaN(edge) || edge > 0;
    }

    private double edgeDistance() {
        AxisAlignedBB box = predictedBox();
        AxisAlignedBB below = new AxisAlignedBB(box.minX, box.minY - 0.01, box.minZ, box.maxX, box.minY, box.maxZ);
        List<AxisAlignedBB> ground = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, below);
        if (ground.isEmpty()) return Double.NaN;
        double x = (box.minX + box.maxX) / 2, z = (box.minZ + box.maxZ) / 2;
        double best = Double.POSITIVE_INFINITY;
        for (AxisAlignedBB block : ground) {
            best = Math.min(best, Math.max(outside(x, block.minX, block.maxX), outside(z, block.minZ, block.maxZ)));
        }
        return best;
    }

    private static double outside(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0;
    }

    private static AxisAlignedBB predictedBox() {
        float strafe = mc.thePlayer.movementInput.moveStrafe * 0.98f, forward = mc.thePlayer.movementInput.moveForward * 0.98f;
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.ridingEntity == null) {
            strafe *= 0.2f;
            forward *= 0.2f;
        }
        float speed = moveSpeed();
        float length = strafe * strafe + forward * forward;
        double motionX = mc.thePlayer.motionX, motionZ = mc.thePlayer.motionZ;
        if (length >= 1.0E-4f) {
            length = Math.max(1.0f, MathHelper.sqrt_float(length));
            strafe *= speed / length;
            forward *= speed / length;
            float yaw = mc.thePlayer.rotationYaw * (float) Math.PI / 180.0f;
            float sin = MathHelper.sin(yaw), cos = MathHelper.cos(yaw);
            motionX += strafe * cos - forward * sin;
            motionZ += forward * cos + strafe * sin;
        }

        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        List<AxisAlignedBB> walls = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(motionX, 0, motionZ));
        for (AxisAlignedBB wall : walls) motionX = wall.calculateXOffset(box, motionX);
        box = box.offset(motionX, 0, 0);
        for (AxisAlignedBB wall : walls) motionZ = wall.calculateZOffset(box, motionZ);
        return box.offset(0, 0, motionZ);
    }

    private static float moveSpeed() {
        if (!mc.thePlayer.onGround) return 0.02f;
        BlockPos under = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        float slip = mc.theWorld.getBlockState(under).getBlock().slipperiness * 0.91f;
        return mc.thePlayer.getAIMoveSpeed() * (0.16277136f / (slip * slip * slip));
    }

    private boolean doubleshiftAim() {
        if (!sneakMode() || onDoubleshift.getValue() != Doubleshift.AIM || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return false;
        if (!diagonalEdgePose() || !holdingBlock()) return false;
        double edge = edgeDistance();
        if (!Double.isNaN(edge) && edge <= edgeOffset.getValue()) return false;
        MovingObjectPosition hit = trace(mc.playerController.getBlockReachDistance(), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        return blockHit(hit) && !onSideEdge(hit);
    }

    private void updatePrevent() {
        long now = System.currentTimeMillis();
        if (sneakMode() && onDoubleshift.getValue() == Doubleshift.PREVENT && mc.currentScreen == null && !mc.thePlayer.capabilities.isFlying
                && diagonalEdgePose()) {
            if (holdingBlock()) {
                MovingObjectPosition over = mc.objectMouseOver;
                if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    if (!blockHit(over) || onSideEdge(over)) {
                        clicksAllowed = true;
                        sawEdgeHit = true;
                        edgeHitAt = now;
                    } else if (!sawEdgeHit || now - edgeHitAt >= 300) {
                        clicksAllowed = false;
                    }
                }
                return;
            }
        }
        clicksAllowed = true;
        sawEdgeHit = false;
        edgeHitAt = now;
    }

    private static boolean diagonalEdgePose() {
        if (!mc.thePlayer.onGround || mc.thePlayer.movementInput.jump) return false;
        if (mc.thePlayer.movementInput.moveForward >= -0.01f || Math.abs(mc.thePlayer.movementInput.moveStrafe) > 0.01f || mc.thePlayer.rotationPitch < 75) return false;
        float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) % 90f;
        if (yaw < 0) yaw += 90f;
        return yaw > 20f && yaw < 70f;
    }

    private static boolean blockHit(MovingObjectPosition hit) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.hitVec != null && hit.getBlockPos() != null;
    }

    private static boolean onSideEdge(MovingObjectPosition hit) {
        double x = hit.hitVec.xCoord - hit.getBlockPos().getX(), y = hit.hitVec.yCoord - hit.getBlockPos().getY(), z = hit.hitVec.zCoord - hit.getBlockPos().getZ();
        EnumFacing side = hit.sideHit;
        if (side == null || y < 0 || y > 1) return false;
        double across;
        if (side == EnumFacing.NORTH || side == EnumFacing.SOUTH) across = x;
        else if (side == EnumFacing.WEST || side == EnumFacing.EAST) across = z;
        else return false;
        double into;
        if (across <= 0.12) into = 0.12 - across;
        else if (across >= 0.88) into = across - 0.88;
        else return false;
        return y <= 0.68 + Math.min(1, Math.max(0, into / 0.12)) * 0.22;
    }

    private static float placementPitch(float currentPitch, double reach) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        int y = MathHelper.floor_double(box.minY) - 1;
        List<BlockPos> blocks = new ArrayList<>();
        List<EnumFacing> faces = new ArrayList<>();
        for (int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++) {
            for (int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (replaceable(pos)) continue;
                for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
                    if (replaceable(pos.offset(face))) {
                        blocks.add(pos);
                        faces.add(face);
                    }
                }
            }
        }
        if (blocks.isEmpty()) return Float.NaN;

        float yaw = mc.thePlayer.rotationYaw, best = Float.NaN, bestDelta = Float.POSITIVE_INFINITY;
        for (float pitch = 60f; pitch <= 90f; ) {
            pitch += Math.max(0.4f, Math.min(1.8f, 1f + (float) (Math.random() * 2 - 1) * 0.38f));
            float at = Math.min(pitch, 90f);
            MovingObjectPosition hit = trace(reach, yaw, at);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.sideHit != null
                    && hit.sideHit.getAxis().isHorizontal()) {
                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i).equals(hit.getBlockPos()) && faces.get(i) == hit.sideHit) {
                        float delta = Math.abs(at - currentPitch);
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            best = at;
                        }
                        break;
                    }
                }
            }
            if (pitch >= 90f) break;
        }
        return best;
    }

    private static MovingObjectPosition trace(double reach, float yaw, float pitch) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI), sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F), sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
        return mc.theWorld.rayTraceBlocks(eyes, eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach), false, false, true);
    }

    private static boolean replaceable(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block.isReplaceable(mc.theWorld, pos);
    }

    private boolean sneakMode() {
        return mode.getValue() != SneakMode.PRE_PLACE;
    }

    private boolean inputMode() {
        return mode.getValue() == SneakMode.SNEAK_INPUT;
    }

    private static boolean inGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private static boolean sneakKeyDown() {
        return GameSettings.isKeyDown(mc.gameSettings.keyBindSneak);
    }

    private boolean physicalSneak() {
        return sneakKeyDown() && !sneakKeyPressed.getValue();
    }

    private static boolean holdingBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    private static int hotbarBlocks() {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemBlock block && !(block.getBlock() instanceof BlockLadder)) count += stack.stackSize;
        }
        return count;
    }

    private boolean recentPlacement(long now) {
        return lastPlacement > 0 && now - lastPlacement < 1300;
    }

    private float hudAlpha(long now) {
        if (hudStart <= 0 || lastPlacement <= 0 || now < hudStart) return 0;
        long sinceStart = now - hudStart, sinceLast = now - lastPlacement;
        if (sinceStart < 120) return sinceStart / 120f;
        if (sinceLast <= 1000) return 1;
        if (sinceLast < 1300) return 1 - (sinceLast - 1000) / 300f;
        hudStart = lastPlacement = 0;
        return 0;
    }
}
