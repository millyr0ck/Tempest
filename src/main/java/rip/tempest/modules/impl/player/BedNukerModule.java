package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.TeleportEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.player.bednuker.B4BedNuker;
import rip.tempest.modules.impl.player.bednuker.ExhibitionBedNuker;
import rip.tempest.modules.impl.player.bednuker.JadeBedNuker;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockLiquid;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Bed Nuker", category = ModuleCategory.PLAYER)
public final class BedNukerModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);
    private final Property<Boolean> keep = new Property<>("Keep Break Progress When Out Of Range", false, this::classic);
    private final Property<Boolean> throughWalls = new Property<>("Through Walls", false, this::classic);
    private final Property<Boolean> emptySurrounding = new Property<>("Empty Surrounding", true, () -> classic() && !throughWalls.getValue());
    private final Property<Boolean> rotations = new Property<>("Rotate", true, this::classic);
    private final Property<Boolean> importantRotationsOnly = new Property<>("Only Rotate at Start and Stop", true, this::classic);
    private final Property<Boolean> whitelistOwnBed = new Property<>("Whitelist Own Bed", true);
    private final Property<Boolean> slowDownInAir = new Property<>("Slow Down In Air", false, this::classic);
    private final Property<Boolean> movementCorrection = new Property<>("Movement Fix", true);

    private final ModeProperty<B4BedNuker.BreakMode> breakMode = new ModeProperty<>("Break Mode", B4BedNuker.BreakMode.LEGIT, this::b4);
    private final NumberProperty breakSpeed = new NumberProperty("Break Speed", 1, () -> jade() || b4(), 1, 2, 0.01);
    private final Property<Boolean> ignoreFatigue = new Property<>("Ignore Mining Fatigue", false, this::jade);
    private final Property<Boolean> ignoreSlow = new Property<>("Ignore Slow", false, this::b4);
    private final Property<Boolean> groundSpoof = new Property<>("Ground Spoof", false, () -> jade() || b4());
    private final NumberProperty range = new NumberProperty("Range", 4.5, () -> jade() || b4(), 1, 8, 0.1);
    private final NumberProperty fov = new NumberProperty("FOV", 180, () -> jade() || b4(), 20, 360, 5);
    private final NumberProperty rate = new NumberProperty("Rate", 0.2, this::b4, 0.05, 3, 0.05);
    private final Property<Boolean> blockZapper = new Property<>("Block Zapper", false, this::jade);
    private final Property<Boolean> prioritizeKillAura = new Property<>("Prioritize KillAura", false, () -> jade() || b4());
    private final Property<Boolean> allowKillAura = new Property<>("Allow KillAura", false, this::b4);
    private final Property<Boolean> breakNearBlock = new Property<>("Break Near Block", false, this::b4);
    private final Property<Boolean> cancelKnockback = new Property<>("Cancel Knockback", false, this::b4);
    private final Property<Boolean> disableBreakEffects = new Property<>("Disable Break Effects", false, this::b4);
    private final Property<Boolean> disableRightClick = new Property<>("Disable Right Click", true, this::b4);
    private final Property<Boolean> maximizeSpeed = new Property<>("Maximize Speed", false, this::b4);
    private final Property<Boolean> onlyWhileVisible = new Property<>("Only While Visible", false, this::b4);
    private final Property<Boolean> sendAnimations = new Property<>("Send Animations", true, this::b4);
    private final Property<Boolean> silentSwing = new Property<>("Silent Swing", false, this::b4);
    private final Property<Boolean> skipVerification = new Property<>("Skip Break Verification", false, this::b4);
    private final Property<Boolean> switchBack = new Property<>("Switch Back", true, this::jade);
    private final Property<Boolean> renderOutline = new Property<>("Render Outline", true, () -> jade() || b4() || exhibition());

    private final Property<Boolean> breakBeds = new Property<>("Beds", true, this::exhibition);
    private final Property<Boolean> breakWheat = new Property<>("Wheat", true, this::exhibition);
    private final Property<Boolean> breakCake = new Property<>("Cake", true, this::exhibition);
    private final Property<Boolean> breakEggs = new Property<>("Dragon Egg", true, this::exhibition);

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.Jade, new JadeBedNuker(breakSpeed, ignoreFatigue, groundSpoof, range, fov, blockZapper, prioritizeKillAura, switchBack,
                    renderOutline, movementCorrection, () -> whitelistOwnBed.getValue() ? home() : null))
            .add(Mode.B4, new B4BedNuker(breakMode, breakSpeed, fov, range, rate, allowKillAura, breakNearBlock, cancelKnockback, disableBreakEffects,
                    disableRightClick, groundSpoof, ignoreSlow, maximizeSpeed, onlyWhileVisible, prioritizeKillAura, renderOutline, sendAnimations,
                    silentSwing, skipVerification, movementCorrection, this::nearHome))
            .add(Mode.Old, new ExhibitionBedNuker(breakBeds, breakWheat, breakCake, breakEggs, renderOutline, whitelistOwnBed, movementCorrection));

    private enum Mode {
        Instant,
        Normal,
        Jade,
        B4,

        Old
    }

    private boolean classic() {
        return !jade() && !b4() && !exhibition();
    }

    private boolean exhibition() {
        return mode.getValue() == Mode.Old;
    }

    private boolean jade() {
        return mode.getValue() == Mode.Jade;
    }

    private boolean b4() {
        return mode.getValue() == Mode.B4;
    }

    public static boolean holdsKillAura() {
        return JadeBedNuker.isNuking() || B4BedNuker.holdsKillAura();
    }

    private BlockPos home() {
        return home == null ? null : new BlockPos(home.getX(), home.getY(), home.getZ());
    }

    private boolean nearHome(BlockPos pos) {
        return whitelistOwnBed.getValue() && home != null && pos.distanceSq(home.getX(), home.getY(), home.getZ()) < 25 * 25;
    }

    private Vector3d block, lastBlock, home;
    private int delay;
    private boolean down;
    private float damage;

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (subModes.handles(mode.getValue())) return;
        delay--;
        if (delay > 0) return;

        if (block == null || mc.thePlayer.getDistance(block.getX(), block.getY(), block.getZ()) > 4 ||
                getBlock(block.getX(), block.getY(), block.getZ()) instanceof BlockAir) {
            updateBlock();

            if (down) {
                mc.gameSettings.keyBindAttack.setPressed(false);
                down = false;
            }

            if (block == null) return;
        }

        destroy();
    };

    @EventLink
    public final Listener<TeleportEvent> onTeleport = event -> {
        final double distance = mc.thePlayer.getDistance(event.getPosX(), event.getPosY(), event.getPosZ());

        if (distance > 40) {
            home = new Vector3d(event.getPosX(), event.getPosY(), event.getPosZ());
        }
    };

    private void updateBlock() {
        if (!(this.block == null || getBlock(this.block.x, this.block.y, this.block.z) instanceof BlockAir
                || mc.thePlayer.getDistance(this.block.x, this.block.y - mc.thePlayer.getEyeHeight(), this.block.z) > 9)) {
            return;
        }
        if (this.lastBlock != null && !keep.getValue()) {
            mc.playerController.curBlockDamageMP = 0;
        }

        lastBlock = block;
        block = findBlock();
    }

    private Vector3d findBlock() {
        if (home != null && mc.thePlayer.getDistanceSq(home.getX(), home.getY(), home.getZ()) < 35 * 35 && whitelistOwnBed.getValue()) {
            return null;
        }

        int beds = 0;

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    final Block block = getBlockRelativeToPlayer(x, y, z);
                    final Vector3d position = new Vector3d(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);

                    if (!(block instanceof BlockBed)) {
                        continue;
                    }

                    beds++;
                    if (beds <= 1) continue;

                    if (!throughWalls.getValue()) {
                        Vector2f rot = calculateRotation(position);
                        MovingObjectPosition mop = rayCast(rot, 4.5f);
                        if (mop == null || mop.hitVec.distanceTo(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY - mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)) > 4.5) {
                            continue;
                        }
                        BlockPos blockPos = mop.getBlockPos();
                        if (!blockPos.equals(new BlockPos(position.getX(), position.getY(), position.getZ()))) {
                            continue;
                        }
                    } else if (emptySurrounding.getValue()) {
                        Vector3d addVec = position;
                        double hardness = Double.MAX_VALUE;
                        boolean empty = false;

                        for (int addX = -4; addX <= 4; addX++) {
                            for (int addY = 0; addY <= 1; addY++) {
                                for (int addZ = -4; addZ <= 4; addZ++) {
                                    Block possibleBlock = getBlock(position.getX() + addX, position.getY() + addY, position.getZ() + addZ);

                                    if (possibleBlock instanceof BlockBed) {
                                        continue;
                                    }

                                    if (empty || (mc.thePlayer.getDistance(position.getX() + addX, position.getY() + addY, position.getZ() + addZ)) > 4.5)
                                        continue;

                                    if (getNeighbours(position.add(new Vector3d(addX, addY, addZ))).stream()
                                            .noneMatch(neighbour -> neighbour instanceof BlockBed)) {
                                        continue;
                                    }

                                    if (possibleBlock instanceof BlockAir || possibleBlock instanceof BlockLiquid) {
                                        empty = true;
                                        continue;
                                    }

                                    if (mc.thePlayer.getDistance(position.getX() + addX, position.getY() + addY - mc.thePlayer.getEyeHeight(), position.getZ() + addZ) > 4.5) {
                                        continue;
                                    }

                                    double possibleHardness = possibleBlock.getBlockHardness(mc.theWorld, new BlockPos(position.getX() + addX, position.getY() + addY, position.getZ() + addZ));

                                    if (possibleHardness < hardness) {
                                        hardness = possibleHardness;
                                        addVec = position.add(new Vector3d(addX, addY, addZ));
                                    }
                                }
                            }
                        }

                        if (!empty) {
                            if (addVec.equals(position)) {
                                return null;
                            } else {
                                return addVec;
                            }
                        }
                    }

                    return position;
                }
            }
        }

        return null;
    }

    private List<Block> getNeighbours(Vector3d blockPos) {
        List<Block> neighbours = new ArrayList<>();
        for (EnumFacing enumFacing : EnumFacing.values()) {
            if (enumFacing == EnumFacing.UP) continue;
            Vector3d neighbourPos = blockPos.add(new Vector3d(enumFacing.getDirectionVec().getX(), enumFacing.getDirectionVec().getY(), enumFacing.getDirectionVec().getZ()));
            neighbours.add(getBlock(neighbourPos));
        }
        return neighbours;
    }

    private void destroy() {
        boolean slowDown = this.slowDownInAir.getValue();
        boolean ground = mc.thePlayer.onGround;
        if (!slowDown) mc.thePlayer.onGround = true;

        BlockPos blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());

        switch (mode.getValue()) {
            case Instant:
                rotate();
                damage = mc.playerController.curBlockDamageMP;

                mc.thePlayer.swingItem();
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, EnumFacing.UP));
                mc.thePlayer.swingItem();
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, EnumFacing.UP));
                block = null;
                delay = 20;

                mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.DOWN);
                break;

            case Normal:
                damage = mc.playerController.curBlockDamageMP;
                rotate();
                mc.gameSettings.keyBindAttack.setPressed(true);
                down = true;
                break;
        }

        mc.thePlayer.onGround = ground;
    }

    private void rotate() {
        BlockPos blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());
        float blockHardness = getBlock(blockPos).getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, blockPos);

        if (importantRotationsOnly.getValue() && (mc.playerController.curBlockDamageMP != 0 && mc.playerController.curBlockDamageMP <= 1 - blockHardness - 0.001)) {
            return;
        }

        if (!this.rotations.getValue()) return;
        RotationProcess.setRotations(getRotations(), 10, movementCorrection.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
    }

    private Vector2f getRotations() {
        return calculateRotation(new Vector3d(Math.floor(block.getX()) + 0.5 + (Math.random() - 0.5) / 4,
                Math.floor(block.getY()) + 0.1,
                Math.floor(block.getZ()) + 0.5 + (Math.random() - 0.5) / 4));
    }

    private Vector2f calculateRotation(Vector3d target) {
        double x = target.getX() - mc.thePlayer.posX;
        double y = target.getY() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double z = target.getZ() - mc.thePlayer.posZ;

        double dist = Math.sqrt(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));

        return new Vector2f(yaw, pitch);
    }

    private MovingObjectPosition rayCast(Vector2f rotation, float range) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1);
        Vec3 rotationVector = mc.thePlayer.getVectorForRotation(rotation.y, rotation.x);
        Vec3 forward = eyes.addVector(rotationVector.xCoord * range, rotationVector.yCoord * range, rotationVector.zCoord * range);
        return mc.theWorld.rayTraceBlocks(eyes, forward, false, false, true);
    }

    private Block getBlock(double x, double y, double z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    private Block getBlock(Vector3d pos) {
        return getBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    private Block getBlock(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    private Block getBlockRelativeToPlayer(int x, int y, int z) {
        return getBlock(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);
    }

    @Override
    public void onEnable() {
        block = null;
        damage = 0;
        delay = 0;
        down = false;
        subModes.enable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        block = null;

        if (down) {
            mc.gameSettings.keyBindAttack.setPressed(false);
            down = false;
        }
    }
}
