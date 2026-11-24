package rip.tempest.modules.impl.player.bednuker;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.SubMode;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

public final class ExhibitionBedNuker extends SubMode {
    private static final double RANGE = 6.0D;
    private static final double SPAWN_RADIUS = 30.0D;

    private Property<Boolean> beds, wheat, cake, dragonEgg, renderOutline, whitelistOwnBed, movementFix;

    private BlockPos blockBreaking;
    private volatile Vec3 teleported;

    public ExhibitionBedNuker(Property<Boolean> beds, Property<Boolean> wheat, Property<Boolean> cake, Property<Boolean> dragonEgg,
                              Property<Boolean> renderOutline, Property<Boolean> whitelistOwnBed, Property<Boolean> movementFix) {
        this.beds = beds;
        this.wheat = wheat;
        this.cake = cake;
        this.dragonEgg = dragonEgg;
        this.renderOutline = renderOutline;
        this.whitelistOwnBed = whitelistOwnBed;
        this.movementFix = movementFix;
    }

    @Override
    protected void onEnable() {
        blockBreaking = null;
        teleported = null;
    }

    @Override
    protected void onDisable() {
        blockBreaking = null;
        teleported = null;
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> {
        blockBreaking = null;
        teleported = null;
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!(e.getPacket() instanceof S08PacketPlayerPosLook packet) || mc.thePlayer == null) return;
        if (mc.thePlayer.ticksExisted > 10
                && (Math.abs((int) packet.getX() - packet.getX()) == 0.5 || Math.abs((int) packet.getZ() - packet.getZ()) == 0.5)) {
            teleported = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        }
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        blockBreaking = null;
        if (!inGame() || nearSpawn()) return;

        blockBreaking = findBlock();
        if (blockBreaking == null) return;

        Vector2f rotation = rotationTo(blockBreaking);
        RotationProcess.setRotations(rotation, 10, movementFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (e.isPre() || blockBreaking == null || !inGame()) return;

        if (mc.playerController.getBlockHitDelay() > 0) {
            mc.playerController.setBlockHitDelay(0);
        }

        MovingObjectPosition hit = hitResult(blockBreaking);
        if (hit == null) return;

        Block block = mc.theWorld.getBlockState(blockBreaking).getBlock();
        if (block == Blocks.bed || block == Blocks.wheat) {
            if (mc.playerController.onPlayerDamageBlock(blockBreaking, hit.sideHit)) {
                mc.thePlayer.swingItem();
            }
        } else if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getCurrentEquippedItem(),
                blockBreaking, hit.sideHit, hit.hitVec)) {
            mc.thePlayer.swingItem();
        }
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = e -> {
        if (!renderOutline.getValue() || blockBreaking == null || !inGame()) return;

        Block block = mc.theWorld.getBlockState(blockBreaking).getBlock();
        AxisAlignedBB bb = block.getSelectedBoundingBox(mc.theWorld, blockBreaking);
        AxisAlignedBB box = bb.offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);

        int rgb = ColorProcess.getColor().getRGB() & 0xFFFFFF;
        BedNukerRender.fill(box, 0x40000000 | rgb);
        BedNukerRender.outline(box, 0xC0000000 | rgb, 1.5F);
    };

    private BlockPos findBlock() {
        boolean column = mc.thePlayer.isSneaking();

        for (int y = 6; y >= -6; --y) {
            for (int x = -6; x <= 6; ++x) {
                for (int z = -6; z <= 6; ++z) {
                    if ((x != 0 || z != 0) == column) continue;
                    if (Math.sqrt(x * x + y * y + z * z) > RANGE) continue;

                    BlockPos pos = new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!wanted(block) || hitResult(pos) == null) continue;

                    if (block == Blocks.wheat && block instanceof BlockCrops crops && crops.canGrow(mc.theWorld, pos, state, true)) continue;

                    return pos;
                }
            }
        }
        return null;
    }

    private boolean wanted(Block block) {
        return block == Blocks.bed && beds.getValue()
                || block == Blocks.wheat && wheat.getValue()
                || block == Blocks.cake && cake.getValue()
                || block == Blocks.dragon_egg && dragonEgg.getValue();
    }

    private static Vec3 center(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        AxisAlignedBB bb = block.getSelectedBoundingBox(mc.theWorld, pos);
        return new Vec3(bb.minX + (bb.maxX - bb.minX) / 2, bb.minY + (bb.maxY - bb.minY) / 2, bb.minZ + (bb.maxZ - bb.minZ) / 2);
    }

    private static MovingObjectPosition hitResult(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        return block.collisionRayTrace(mc.theWorld, pos, eyes, center(pos));
    }

    private static Vector2f rotationTo(BlockPos pos) {
        Vec3 middle = center(pos);
        double diffX = middle.xCoord - mc.thePlayer.posX;
        double diffY = middle.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = middle.zCoord - mc.thePlayer.posZ;
        double distance = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, distance));
        return new Vector2f(mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw),
                mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch));
    }

    private boolean nearSpawn() {
        if (!whitelistOwnBed.getValue() || !inBedWars()) return false;

        if (teleported != null && gameStarting()) teleported = null;

        Vec3 spawn = teleported;
        return spawn != null && mc.thePlayer.getDistance(spawn.xCoord, mc.thePlayer.posY, spawn.zCoord) < SPAWN_RADIUS;
    }

    private static ScoreObjective sidebar() {
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = null;

        ScorePlayerTeam team = scoreboard.getPlayersTeam(mc.thePlayer.getName());
        if (team != null) {
            int color = team.getChatFormat().getColorIndex();
            if (color >= 0) objective = scoreboard.getObjectiveInDisplaySlot(3 + color);
        }
        return objective != null ? objective : scoreboard.getObjectiveInDisplaySlot(1);
    }

    private static boolean inBedWars() {
        ScoreObjective objective = sidebar();
        return objective != null
                && EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()).toLowerCase().contains("bed wars");
    }

    private static boolean gameStarting() {
        ScoreObjective objective = sidebar();
        if (objective == null) return false;

        Scoreboard scoreboard = objective.getScoreboard();
        for (Score score : scoreboard.getSortedScores(objective)) {
            String name = score.getPlayerName();
            if (name == null || name.startsWith("#")) continue;

            String line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(name), name);
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(line).toLowerCase();
            if (plain.contains("starting in") || plain.contains("waiting..")) return true;
        }
        return false;
    }
}
