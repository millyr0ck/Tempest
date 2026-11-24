package rip.tempest.processes;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.utils.client.Logger;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

public final class PlayerRecorder {
    private static final PlayerRecorder INSTANCE = new PlayerRecorder();
    private static final float ANGLE_STEP = 360.0F / 256.0F;
    private static final double PLACE_REACH = 5.5;

    private static boolean recording;
    private static String targetName;
    private static File csvFile, summaryFile;
    private static BufferedWriter out;

    private static final Object LOCK = new Object();
    private static int targetId = Integer.MIN_VALUE;
    private static double serverX, serverY, serverZ;
    private static boolean havePosition, haveRotation;
    private static float serverYaw, serverPitch, serverHeadYaw;
    private static boolean serverOnGround, sneaking, sprinting, usingItem;
    private static String heldItem = "-";
    private static int swingsThisTick, rotationPacketsThisTick;
    private static final List<BlockPos> placedThisTick = new ArrayList<>();

    private static long startMs;
    private static int tick;
    private static boolean wasVisible;
    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;
    private static boolean haveLast;
    private static boolean lastOnGround = true;
    private static final Summary summary = new Summary();

    private PlayerRecorder() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static String targetName() {
        return targetName;
    }

    public static boolean start(String name) {
        if (recording) stop();
        File folder = new File(mc.mcDataDir, Tempest.NAME + "/rots");
        if (!folder.exists() && !folder.mkdirs()) return false;

        String safeName = name.replaceAll("[^A-Za-z0-9_]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        csvFile = new File(folder, safeName + "-" + stamp + ".csv");
        summaryFile = new File(folder, safeName + "-" + stamp + "-summary.txt");
        try {
            out = new BufferedWriter(new FileWriter(csvFile));
            out.write("tick,ms,x,y,z,yaw,pitch,headYaw,dYaw,dPitch,rotPackets,hSpeed,vSpeed,moveYaw,yawVsMove,"
                    + "onGround,sneak,sprint,using,held,swing,placed,placedRel");
            out.newLine();
        } catch (IOException e) {
            out = null;
            return false;
        }

        synchronized (LOCK) {
            targetId = Integer.MIN_VALUE;
            havePosition = haveRotation = false;
            sneaking = sprinting = usingItem = false;
            heldItem = "-";
            swingsThisTick = rotationPacketsThisTick = 0;
            placedThisTick.clear();
        }
        targetName = name;
        startMs = System.currentTimeMillis();
        tick = 0;
        wasVisible = false;
        haveLast = false;
        lastOnGround = true;
        summary.reset();
        recording = true;
        Tempest.INSTANCE.getEventBus().subscribe(INSTANCE);
        return true;
    }

    public static List<String> stop() {
        if (!recording) return new ArrayList<>();
        recording = false;
        Tempest.INSTANCE.getEventBus().unsubscribe(INSTANCE);
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        out = null;

        List<String> lines = summary.lines(targetName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.newLine();
            writer.write("Note: the server only relays rotations in 1.40625-degree steps, and only once they have changed");
            writer.newLine();
            writer.write("by about 5.6 degrees; positions come every other tick on the ground. Small turns never arrive.");
            writer.newLine();
        } catch (IOException ignored) {
        }
        lines.add("Saved to Tempest/rots/" + csvFile.getName() + " (+ summary).");
        return lines;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (mc.theWorld == null) return;
        Packet<?> packet = e.getPacket();
        synchronized (LOCK) {
            int id = targetId;
            if (id == Integer.MIN_VALUE) return;

            if (packet instanceof S14PacketEntity move) {
                Entity entity = move.getEntity(mc.theWorld);
                if (entity == null || entity.getEntityId() != id) return;
                if (havePosition) {
                    serverX += move.func_149062_c() / 32.0;
                    serverY += move.func_149061_d() / 32.0;
                    serverZ += move.func_149064_e() / 32.0;
                }
                if (move.func_149060_h()) {
                    serverYaw = move.func_149066_f() * ANGLE_STEP;
                    serverPitch = move.func_149063_g() * ANGLE_STEP;
                    haveRotation = true;
                    rotationPacketsThisTick++;
                }
                serverOnGround = move.getOnGround();
            } else if (packet instanceof S18PacketEntityTeleport teleport && teleport.getEntityId() == id) {
                serverX = teleport.getX() / 32.0;
                serverY = teleport.getY() / 32.0;
                serverZ = teleport.getZ() / 32.0;
                serverYaw = teleport.getYaw() * ANGLE_STEP;
                serverPitch = teleport.getPitch() * ANGLE_STEP;
                serverOnGround = teleport.getOnGround();
                havePosition = haveRotation = true;
                rotationPacketsThisTick++;
            } else if (packet instanceof S19PacketEntityHeadLook head) {
                Entity entity = head.getEntity(mc.theWorld);
                if (entity != null && entity.getEntityId() == id) serverHeadYaw = head.getYaw() * ANGLE_STEP;
            } else if (packet instanceof S0BPacketAnimation animation && animation.getEntityID() == id) {
                if (animation.getAnimationType() == 0) swingsThisTick++;
            } else if (packet instanceof S1CPacketEntityMetadata metadata && metadata.getEntityId() == id) {
                List<DataWatcher.WatchableObject> changed = metadata.func_149376_c();
                if (changed != null) {
                    for (DataWatcher.WatchableObject object : changed) {
                        if (object.getDataValueId() == 0 && object.getObject() instanceof Byte flags) {
                            sneaking = (flags & 0x02) != 0;
                            sprinting = (flags & 0x08) != 0;
                            usingItem = (flags & 0x10) != 0;
                        }
                    }
                }
            } else if (packet instanceof S04PacketEntityEquipment equipment && equipment.getEntityID() == id) {
                if (equipment.getEquipmentSlot() == 0) {
                    ItemStack stack = equipment.getItemStack();
                    heldItem = stack == null ? "-" : stack.getItem().getUnlocalizedName().replace("tile.", "").replace("item.", "");
                }
            } else if (packet instanceof S23PacketBlockChange change) {
                notePlacement(change.getBlockPosition(), change.getBlockState());
            } else if (packet instanceof S22PacketMultiBlockChange changes) {
                for (S22PacketMultiBlockChange.BlockUpdateData data : changes.getChangedBlocks()) {
                    notePlacement(data.getPos(), data.getBlockState());
                }
            }
        }
    };

    private static void notePlacement(BlockPos pos, IBlockState state) {
        if (!havePosition || state == null || state.getBlock().getMaterial().isReplaceable()) return;
        double dx = pos.getX() + 0.5 - serverX, dy = pos.getY() + 0.5 - (serverY + 1.62), dz = pos.getZ() + 0.5 - serverZ;
        if (dx * dx + dy * dy + dz * dz > PLACE_REACH * PLACE_REACH) return;
        if (!mc.theWorld.getBlockState(pos).getBlock().getMaterial().isReplaceable()) return;
        placedThisTick.add(pos);
    }

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (mc.theWorld == null || mc.thePlayer == null || out == null) return;

        EntityPlayer target = null;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && player.getName().equalsIgnoreCase(targetName)) {
                target = player;
                break;
            }
        }
        if (target == null) {
            if (wasVisible) Logger.chatPrint(true, "§e" + targetName + " is out of view; recording resumes when they're back.");
            wasVisible = false;
            synchronized (LOCK) {
                targetId = Integer.MIN_VALUE;
            }
            haveLast = false;
            return;
        }

        double x, y, z;
        float yaw, pitch, headYaw;
        boolean onGround, sneak, sprint, using;
        String held;
        int swings, rotPackets;
        List<BlockPos> placed;
        synchronized (LOCK) {
            if (targetId != target.getEntityId() || !havePosition) {
                targetId = target.getEntityId();
                serverX = target.serverPosX / 32.0;
                serverY = target.serverPosY / 32.0;
                serverZ = target.serverPosZ / 32.0;
                serverYaw = target.rotationYaw;
                serverPitch = target.rotationPitch;
                serverHeadYaw = target.rotationYawHead;
                serverOnGround = target.onGround;
                sneaking = target.isSneaking();
                sprinting = target.isSprinting();
                usingItem = target.isUsingItem();
                ItemStack stack = target.getHeldItem();
                heldItem = stack == null ? "-" : stack.getItem().getUnlocalizedName().replace("tile.", "").replace("item.", "");
                havePosition = haveRotation = true;
            }
            x = serverX;
            y = serverY;
            z = serverZ;
            yaw = serverYaw;
            pitch = serverPitch;
            headYaw = serverHeadYaw;
            onGround = serverOnGround;
            sneak = sneaking;
            sprint = sprinting;
            using = usingItem;
            held = heldItem;
            swings = swingsThisTick;
            rotPackets = rotationPacketsThisTick;
            placed = new ArrayList<>(placedThisTick);
            swingsThisTick = 0;
            rotationPacketsThisTick = 0;
            placedThisTick.clear();
        }
        if (!wasVisible) Logger.chatPrint(true, "§aRecording " + target.getName() + ".");
        wasVisible = true;

        float dYaw = haveLast ? MathHelper.wrapAngleTo180_float(yaw - lastYaw) : 0;
        float dPitch = haveLast ? pitch - lastPitch : 0;
        double mx = haveLast ? x - lastX : 0, my = haveLast ? y - lastY : 0, mz = haveLast ? z - lastZ : 0;
        double hSpeed = Math.hypot(mx, mz);
        boolean moving = hSpeed > 0.01;
        float moveYaw = moving ? (float) (Math.toDegrees(Math.atan2(mz, mx)) - 90.0) : Float.NaN;
        float yawVsMove = moving ? MathHelper.wrapAngleTo180_float(yaw - moveYaw) : Float.NaN;

        StringBuilder placedRel = new StringBuilder();
        for (BlockPos pos : placed) {
            if (placedRel.length() > 0) placedRel.append('|');
            placedRel.append(pos.getX() - MathHelper.floor_double(x)).append(':')
                    .append(pos.getY() - MathHelper.floor_double(y)).append(':')
                    .append(pos.getZ() - MathHelper.floor_double(z));
        }

        try {
            out.write(String.format(Locale.ROOT, "%d,%d,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%.4f,%.4f,%s,%s,%b,%b,%b,%b,%s,%d,%d,%s",
                    tick, System.currentTimeMillis() - startMs, x, y, z, yaw, pitch, headYaw, dYaw, dPitch, rotPackets,
                    hSpeed, my, moving ? String.format(Locale.ROOT, "%.2f", moveYaw) : "", moving ? String.format(Locale.ROOT, "%.2f", yawVsMove) : "",
                    onGround, sneak, sprint, using, held, swings, placed.size(), placedRel));
            out.newLine();
            if (tick % 20 == 0) out.flush();
        } catch (IOException ignored) {
        }

        summary.add(dYaw, dPitch, rotPackets, pitch, hSpeed, moving, yawVsMove, onGround, lastOnGround, sneak, sprint, swings, placed.size(), held);
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        lastOnGround = onGround;
        haveLast = true;
        tick++;
    };

    private static final class Summary {
        int ticks, rotationTicks, placements, swings, jumps, sprintTicks, sneakTicks, groundTicks, movingTicks;
        int lastPlaceTick = -1, placeGaps, placeGapTotal;
        double turnTotal, turnMax, pitchTurnTotal, speedTotal, speedMax;
        double pitchPlaceTotal, pitchPlaceMin = 999, pitchPlaceMax = -999;
        double vsMoveX, vsMoveZ;
        int vsMoveCount;
        final Map<String, Integer> heldCounts = new HashMap<>();

        void reset() {
            ticks = rotationTicks = placements = swings = jumps = sprintTicks = sneakTicks = groundTicks = movingTicks = 0;
            lastPlaceTick = -1;
            placeGaps = placeGapTotal = 0;
            turnTotal = turnMax = pitchTurnTotal = speedTotal = speedMax = 0;
            pitchPlaceTotal = 0;
            pitchPlaceMin = 999;
            pitchPlaceMax = -999;
            vsMoveX = vsMoveZ = 0;
            vsMoveCount = 0;
            heldCounts.clear();
        }

        void add(float dYaw, float dPitch, int rotPackets, float pitch, double hSpeed, boolean moving, float yawVsMove,
                 boolean onGround, boolean wasOnGround, boolean sneak, boolean sprint, int swingCount, int placedCount, String held) {
            ticks++;
            if (rotPackets > 0 && (dYaw != 0 || dPitch != 0)) {
                rotationTicks++;
                turnTotal += Math.abs(dYaw);
                turnMax = Math.max(turnMax, Math.abs(dYaw));
                pitchTurnTotal += Math.abs(dPitch);
            }
            if (moving) {
                movingTicks++;
                speedTotal += hSpeed;
                speedMax = Math.max(speedMax, hSpeed);
            }
            if (wasOnGround && !onGround) jumps++;
            if (onGround) groundTicks++;
            if (sprint) sprintTicks++;
            if (sneak) sneakTicks++;
            swings += swingCount;
            if (placedCount > 0) {
                placements += placedCount;
                pitchPlaceTotal += pitch * placedCount;
                pitchPlaceMin = Math.min(pitchPlaceMin, pitch);
                pitchPlaceMax = Math.max(pitchPlaceMax, pitch);
                if (moving && !Float.isNaN(yawVsMove)) {
                    vsMoveX += Math.cos(Math.toRadians(yawVsMove));
                    vsMoveZ += Math.sin(Math.toRadians(yawVsMove));
                    vsMoveCount++;
                }
                if (lastPlaceTick >= 0) {
                    placeGaps++;
                    placeGapTotal += ticks - lastPlaceTick;
                }
                lastPlaceTick = ticks;
            }
            heldCounts.merge(held, 1, Integer::sum);
        }

        List<String> lines(String name) {
            List<String> lines = new ArrayList<>();
            double seconds = Math.max(0.05, ticks / 20.0);
            lines.add(String.format(Locale.ROOT, "%s: %d ticks seen (%.1f s).", name, ticks, seconds));
            if (ticks == 0) return lines;
            lines.add(String.format(Locale.ROOT, "Placed %d blocks (%.2f/s, every %.1f ticks), %d swings (%.2f/s).",
                    placements, placements / seconds, placeGaps > 0 ? placeGapTotal / (double) placeGaps : 0.0, swings, swings / seconds));
            if (placements > 0) {
                String facing = vsMoveCount > 0
                        ? String.format(Locale.ROOT, "%.0f deg from where they move", Math.toDegrees(Math.atan2(vsMoveZ, vsMoveX)))
                        : "n/a";
                lines.add(String.format(Locale.ROOT, "Pitch when placing: avg %.1f (%.1f to %.1f); yaw when placing: %s.",
                        pitchPlaceTotal / placements, pitchPlaceMin, pitchPlaceMax, facing));
            }
            lines.add(String.format(Locale.ROOT, "Turns seen on %d ticks (%.0f%%): avg %.1f deg yaw / %.1f deg pitch, biggest %.1f deg.",
                    rotationTicks, 100.0 * rotationTicks / ticks, rotationTicks > 0 ? turnTotal / rotationTicks : 0.0,
                    rotationTicks > 0 ? pitchTurnTotal / rotationTicks : 0.0, turnMax));
            lines.add(String.format(Locale.ROOT, "Speed: avg %.2f b/s moving, top %.2f b/s; %d jumps; on ground %.0f%%, sprinting %.0f%%, sneaking %.0f%%.",
                    movingTicks > 0 ? speedTotal / movingTicks * 20.0 : 0.0, speedMax * 20.0, jumps,
                    100.0 * groundTicks / ticks, 100.0 * sprintTicks / ticks, 100.0 * sneakTicks / ticks));
            String topHeld = heldCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
            lines.add("Held most: " + topHeld + ".");
            return lines;
        }
    }
}
