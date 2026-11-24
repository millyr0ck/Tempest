package rip.tempest.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0APacketUseBed;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.util.BlockPos;

/**
 * Puts your own player into the recording. The server tells everyone else where you are, but not
 * you, so this writes the packets it would have sent: you appearing, your movement and turning
 * (a move every other tick, a full teleport when the step is too big or every 100 ticks), head
 * turn, velocity, arm swings, what you hold and wear, riding, sleeping and block breaking.
 *
 * <p>Ported from ReplayMod's RecordingEventHandler (GPL-3.0-or-later), its 1.8 paths: positions in
 * 1/32 block, relative moves within 4 blocks. Sleeping is noted on the tick it starts, as the
 * Forge 1.8 version did from its sleep event.
 */
final class OwnPlayerRecorder {
    private static final int POS_UPDATE_FREQUENCY = 2;
    private static final int RESOLUTION = 32;
    private static final double MAX_RELATIVE = 4.0 * RESOLUTION;
    private static final int EQUIPMENT_SLOTS = 5;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ReplayRecorder recorder;

    private Double lastX, lastY, lastZ;
    private int ticksSincePosUpdate, ticksSinceCorrection;
    private final ItemStack[] items = new ItemStack[EQUIPMENT_SLOTS];
    private boolean wasSleeping;
    private int lastRiding = -1;
    private Integer lastHeadYaw;

    OwnPlayerRecorder(ReplayRecorder recorder) {
        this.recorder = recorder;
    }

    void spawn() {
        spawn(true);
    }

    void spawn(boolean listed) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        if (!listed && (mc.getNetHandler() == null || mc.getNetHandler().getPlayerInfo(player.getUniqueID()) == null)) return;
        recorder.save(new S0CPacketSpawnPlayer(player));
        lastX = lastY = lastZ = null;
        java.util.Arrays.fill(items, null);
        lastRiding = -1;
        wasSleeping = false;
        lastHeadYaw = null;
    }

    void tick() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        try {
            boolean force = false;
            if (lastX == null || lastY == null || lastZ == null) {
                force = true;
                lastX = player.posX;
                lastY = player.posY;
                lastZ = player.posZ;
            }
            if (++ticksSinceCorrection >= 100) {
                ticksSinceCorrection = 0;
                force = true;
            }

            long dx = Math.round((player.posX - lastX) * RESOLUTION);
            long dy = Math.round((player.posY - lastY) * RESOLUTION);
            long dz = Math.round((player.posZ - lastZ) * RESOLUTION);

            if (force || Math.abs(dx) > MAX_RELATIVE || Math.abs(dy) > MAX_RELATIVE || Math.abs(dz) > MAX_RELATIVE) {
                recorder.save(new S18PacketEntityTeleport(player));
                lastX = player.posX;
                lastY = player.posY;
                lastZ = player.posZ;
            } else if (++ticksSincePosUpdate >= POS_UPDATE_FREQUENCY) {
                ticksSincePosUpdate = 0;
                byte yaw = (byte) ((int) (player.rotationYaw * 256.0F / 360.0F));
                byte pitch = (byte) ((int) (player.rotationPitch * 256.0F / 360.0F));
                recorder.save(new S14PacketEntity.S17PacketEntityLookMove(player.getEntityId(), (byte) dx, (byte) dy, (byte) dz, yaw, pitch, player.onGround));
                lastX += (double) dx / RESOLUTION;
                lastY += (double) dy / RESOLUTION;
                lastZ += (double) dz / RESOLUTION;
            }

            int headYaw = (int) (player.rotationYawHead * 256.0F / 360.0F);
            if (lastHeadYaw == null || lastHeadYaw != headYaw) {
                recorder.save(new S19PacketEntityHeadLook(player, (byte) headYaw));
                lastHeadYaw = headYaw;
            }

            recorder.save(new S12PacketEntityVelocity(player.getEntityId(), player.motionX, player.motionY, player.motionZ));

            if (player.isSwingInProgress && player.swingProgressInt == 0) {
                recorder.save(new S0BPacketAnimation(player, 0));
            }

            for (int slot = 0; slot < EQUIPMENT_SLOTS; slot++) {
                ItemStack stack = player.getEquipmentInSlot(slot);
                if (!ItemStack.areItemStacksEqual(items[slot], stack)) {
                    items[slot] = stack != null ? stack.copy() : null;
                    recorder.save(new S04PacketEntityEquipment(player.getEntityId(), slot, stack));
                }
            }

            Entity vehicle = player.ridingEntity;
            int vehicleId = vehicle == null ? -1 : vehicle.getEntityId();
            if (lastRiding != vehicleId) {
                lastRiding = vehicleId;
                recorder.save(new S1BPacketEntityAttach(0, player, vehicle));
            }

            if (player.isPlayerSleeping() && !wasSleeping && player.playerLocation != null) {
                recorder.save(new S0APacketUseBed(player, player.playerLocation));
                wasSleeping = true;
            } else if (!player.isPlayerSleeping() && wasSleeping) {
                recorder.save(new S0BPacketAnimation(player, 2));
                wasSleeping = false;
            }
        } catch (Exception e) {
        }
    }

    void blockBreak(int breakerId, BlockPos pos, int progress) {
        EntityPlayerSP player = mc.thePlayer;
        if (player != null && breakerId == player.getEntityId()) {
            recorder.save(new S25PacketBlockBreakAnim(breakerId, pos, progress));
        }
    }
}
