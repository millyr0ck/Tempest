package rip.tempest.modules.impl.combat.velocity;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.ViaPackets;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import static rip.tempest.utils.Util.mc;

public final class AstralisGrimVelocity extends SubMode {
    private NumberProperty grimValue;
    private volatile int ticks;
    private boolean saved;
    private double savedX, savedY, savedZ;

    public AstralisGrimVelocity(NumberProperty grimValue) {
        this.grimValue = grimValue;
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        saved = false;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (inGame() && e.getPacket() instanceof S12PacketEntityVelocity velocity && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            e.setCancelled();
            ticks = grimValue.getValue().intValue();
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (ticks > 0) {
            e.setCancelled();
            ticks--;
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = e -> {
        if (!inGame()) return;
        if (ticks > 0) {
            e.setCancelled();
            if (!saved) {
                savedX = mc.thePlayer.motionX;
                savedY = mc.thePlayer.motionY;
                savedZ = mc.thePlayer.motionZ;
                saved = true;
            }
        } else if (saved) {
            mc.thePlayer.motionX = savedX;
            mc.thePlayer.motionY = savedY;
            mc.thePlayer.motionZ = savedZ;
            saved = false;
        }
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (!inGame() || ticks <= 0) return;
        BlockPos feet = new BlockPos(mc.thePlayer);
        BlockPos below = feet.down();
        float hitX = (float) (mc.thePlayer.posX - feet.getX());
        float hitZ = (float) (mc.thePlayer.posZ - feet.getZ());
        if (ViaPackets.atLeast(ProtocolVersion.v1_19)) {
            ViaPackets.useItemOn(below, EnumFacing.UP, hitX, 1.0F, hitZ);
        } else {
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(below, EnumFacing.UP.getIndex(), mc.thePlayer.getHeldItem(), hitX, 1.0F, hitZ));
        }
    };
}
