package rip.tempest.modules.impl.movement.phase;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

public final class GrimPhase extends SubMode {
    public enum Release {
        Simple, Double, Desync, None
    }

    private ModeProperty<Release> releaseMode;
    private NumberProperty semiPackets;
    private Runnable turnOff;
    private final List<Packet<?>> held = new ArrayList<>();
    private boolean phasing;
    private boolean selfDisabled;

    public GrimPhase(ModeProperty<Release> releaseMode, NumberProperty semiPackets, Runnable turnOff) {
        this.releaseMode = releaseMode;
        this.semiPackets = semiPackets;
        this.turnOff = turnOff;
    }

    @Override
    protected void onEnable() {
        synchronized (held) {
            held.clear();
        }
        phasing = false;
        selfDisabled = false;
    }

    @Override
    protected void onDisable() {
        if (!inGame()) {
            synchronized (held) {
                held.clear();
            }
            return;
        }
        if (!selfDisabled && phasing) {
            if (releaseMode.getValue() != Release.None) {
                release(releaseMode.getValue());
            } else {
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, mc.thePlayer.onGround));
            }
        }
        List<Packet<?>> release;
        synchronized (held) {
            release = new ArrayList<>(held);
            held.clear();
        }
        for (Packet<?> packet : release) PacketUtils.sendPacketNoEvent(packet);
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (inGame() && e.getPacket() instanceof C03PacketPlayer) {
            synchronized (held) {
                held.add(e.getPacket());
            }
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (!inGame()) return;
        boolean inside = PlayerUtils.insideBlock();
        if (!phasing && inside) {
            for (int i = 0; i < semiPackets.getValue().intValue(); i++) {
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, mc.thePlayer.onGround));
            }
            phasing = true;
        } else if (phasing && !inside) {
            selfDisabled = true;
            turnOff.run();
        }
    };

    private static C03PacketPlayer.C06PacketPlayerPosLook here(double dx, double dy, double dz, boolean onGround) {
        return new C03PacketPlayer.C06PacketPlayerPosLook(mc.thePlayer.posX + dx, mc.thePlayer.posY + dy, mc.thePlayer.posZ + dz,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, onGround);
    }

    private static void release(Release mode) {
        boolean onGround = mc.thePlayer.onGround;
        switch (mode) {
            case Simple -> {
                PacketUtils.sendPacketNoEvent(here(-5000.0, 0, -5000.0, false));
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, onGround));
            }
            case Double -> {
                PacketUtils.sendPacketNoEvent(here(-5000.0, 0, -5000.0, false));
                PacketUtils.sendPacketNoEvent(here(5000.0, 0, 5000.0, false));
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, onGround));
            }
            case Desync -> {
                PacketUtils.sendPacketNoEvent(here(0, 0.0625, 0, false));
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, false));
                PacketUtils.sendPacketNoEvent(here(0, 0.03125, 0, true));
                PacketUtils.sendPacketNoEvent(here(0, 0, 0, onGround));
            }
            default -> {
            }
        }
    }
}
