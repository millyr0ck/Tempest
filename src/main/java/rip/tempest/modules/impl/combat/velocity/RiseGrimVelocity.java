package rip.tempest.modules.impl.combat.velocity;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.SubMode;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ViaPackets;
import rip.tempest.utils.misc.MovementFix;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static rip.tempest.utils.Util.mc;

public final class RiseGrimVelocity extends SubMode {
    public static volatile boolean holding;

    public static volatile boolean rotating;

    private Property<Boolean> smartCombat, rotate;
    private final List<Packet<?>> held = new ArrayList<>();
    private final Set<BlockPos> placedBlocks = ConcurrentHashMap.newKeySet();
    private volatile boolean frozen;
    private volatile boolean flushing;
    private boolean motionSaved;
    private double savedX, savedY, savedZ;

    public RiseGrimVelocity(Property<Boolean> smartCombat, Property<Boolean> rotate) {
        this.smartCombat = smartCombat;
        this.rotate = rotate;
    }

    @Override
    protected void onEnable() {
        rotating = false;
        flush();
        holding = false;
        frozen = false;
        placedBlocks.clear();
    }

    @Override
    protected void onDisable() {
        rotating = false;
        flush();
        holding = false;
        frozen = false;
        placedBlocks.clear();
    }

    private static boolean working() {
        return mc.thePlayer.ticksSinceTeleport >= 7 && !mc.thePlayer.isInWeb;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame() || flushing || !working()) return;
        Packet<?> packet = e.getPacket();

        if (packet instanceof S12PacketEntityVelocity velocity && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            if (mc.thePlayer.onGround) {
                frozen = true;
            } else {
                hold(packet);
                holding = true;
            }
            e.setCancelled();
        }

        if (holding && (packet instanceof S32PacketConfirmTransaction || packet instanceof S18PacketEntityTeleport
                || packet instanceof S14PacketEntity || packet instanceof S1CPacketEntityMetadata
                || packet instanceof S08PacketPlayerPosLook)) {
            hold(packet);
            e.setCancelled();
        }

        if (packet instanceof S23PacketBlockChange change
                && placedBlocks.remove(change.getBlockPosition()) && placedBlocks.isEmpty()) {
            frozen = false;
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame()) return;
        if (working() || !mc.thePlayer.onGround) {
            if (frozen) e.setCancelled();
            if (mc.thePlayer.offGroundTicks > 25 && holding) {
                holding = false;
                flush();
            }
        } else if (hasHeld() || frozen || holding) {
            frozen = false;
            placedBlocks.clear();
            holding = false;
            motionSaved = false;
            flush();
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame() || mc.thePlayer.ticksSinceTeleport < 7 || mc.thePlayer.isInWeb) return;
        if (mc.thePlayer.onGroundTicks > 3 && mc.thePlayer.onGround && holding) mc.thePlayer.jump();

        if (frozen) {
            if (!motionSaved) {
                savedX = mc.thePlayer.motionX;
                savedY = mc.thePlayer.motionY;
                savedZ = mc.thePlayer.motionZ;
                motionSaved = true;
            }
        } else if (motionSaved) {
            mc.thePlayer.motionX = savedX;
            mc.thePlayer.motionY = savedY;
            mc.thePlayer.motionZ = savedZ;
            motionSaved = false;
        }
    };

    @EventLink
    public final Listener<JumpEvent> onJump = e -> {
        if (!inGame() || !working() || !mc.thePlayer.onGround || !holding) return;
        frozen = true;
        holding = false;

        double motionX = mc.thePlayer.motionX, motionY = mc.thePlayer.motionY, motionZ = mc.thePlayer.motionZ;
        flush();
        mc.thePlayer.motionX = motionX;
        mc.thePlayer.motionY = motionY;
        mc.thePlayer.motionZ = motionZ;
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (!inGame()) return;
        if (!frozen) {
            rotating = false;
            return;
        }

        rotating = rotate.getValue();
        if (rotating && !smartCombat.getValue()) {
            RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, (float) (90.0 - Math.random() * 0.1)), 10, MovementFix.NORMAL);
        }

        BlockPos feet = new BlockPos(mc.thePlayer);
        BlockPos below = feet.down();
        float hitX = (float) (mc.thePlayer.posX - feet.getX());
        float hitZ = (float) (mc.thePlayer.posZ - feet.getZ());
        if (ViaPackets.atLeast(ProtocolVersion.v1_19)) {
            ViaPackets.useItemOn(below, EnumFacing.UP, hitX, 1.0F, hitZ);
        } else {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(below, EnumFacing.UP.getIndex(), mc.thePlayer.getHeldItem(), hitX, 1.0F, hitZ));
        }
        placedBlocks.add(below);
    };

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> {
        synchronized (held) {
            held.clear();
        }
        holding = false;
        frozen = false;
        placedBlocks.clear();
    };

    private void hold(Packet<?> packet) {
        synchronized (held) {
            held.add(packet);
        }
    }

    private boolean hasHeld() {
        synchronized (held) {
            return !held.isEmpty();
        }
    }

    private void flush() {
        List<Packet<?>> release;
        synchronized (held) {
            release = new ArrayList<>(held);
            held.clear();
        }
        flushing = true;
        try {
            for (Packet<?> packet : release) PacketUtils.receivePacketNoEvent(packet);
        } finally {
            flushing = false;
        }
    }
}
