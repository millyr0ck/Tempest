package rip.tempest.modules.impl.client;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.client.disabler.GrimInventoryMoveDisabler;
import rip.tempest.modules.impl.client.disabler.GrimSpectateDisabler;
import rip.tempest.modules.impl.client.disabler.HalfTickDisabler;
import rip.tempest.modules.impl.client.disabler.HoverDisabler;
import rip.tempest.modules.impl.client.disabler.NoPayloadDisabler;
import rip.tempest.modules.impl.client.disabler.NullPlaceDisabler;
import rip.tempest.modules.impl.client.disabler.PosCancelDisabler;
import rip.tempest.modules.impl.client.disabler.SentinelMoonDisabler;
import rip.tempest.modules.impl.client.disabler.SlothDisabler;
import rip.tempest.modules.impl.client.disabler.TimeoutDisabler;
import rip.tempest.modules.impl.client.disabler.TransactionSpamDisabler;
import rip.tempest.modules.impl.client.disabler.TrojanDisabler;
import rip.tempest.modules.impl.client.disabler.VehicleDisabler;
import rip.tempest.modules.impl.client.disabler.WatchdogDisabler;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.movement.SpeedModule;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import io.netty.buffer.Unpooled;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.network.play.server.S40PacketDisconnect;

import java.util.LinkedHashSet;
import java.util.UUID;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Disabler", category = ModuleCategory.CLIENT)
public final class DisablerModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VerusCombat);

    private final Property<Boolean> autoDisableOnFlag = new Property<>("Auto Disable on Flag", true,
            () -> mode.getValue() == Mode.Sentinel);
    private final Property<Boolean> warningMessages = new Property<>("Warnings", true,
            () -> mode.getValue() == Mode.Sentinel);

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.GrimInventoryMove, new GrimInventoryMoveDisabler())
            .add(Mode.GrimSpectate, new GrimSpectateDisabler())
            .add(Mode.Watchdog, new WatchdogDisabler(false))
            .add(Mode.WatchdogDev, new WatchdogDisabler(true))
            .add(Mode.SentinelMoon, new SentinelMoonDisabler())
            .add(Mode.VerusSemi, new HalfTickDisabler(false))
            .add(Mode.Sloth, new SlothDisabler())
            .add(Mode.Trojan, new TrojanDisabler())
            .add(Mode.Timeout, new TimeoutDisabler())
            .add(Mode.NoPayload, new NoPayloadDisabler())
            .add(Mode.PosCancel, new PosCancelDisabler())
            .add(Mode.NullPlace, new NullPlaceDisabler())
            .add(Mode.TransactionSpam, new TransactionSpamDisabler())
            .add(Mode.Vehicle, new VehicleDisabler())
            .add(Mode.Hover, new HoverDisabler(() -> setEnabled(false)));

    private enum Mode {
        VerusCombat("Verus Combat"),
        VulcanReach("Vulcan Reach"),
        Miniblox("Miniblox"),
        Sentinel("Sentinel"),

        GrimInventoryMove("Grim Inventory Move"),
        GrimSpectate("Grim Spectate"),

        Watchdog("Watchdog"),
        WatchdogDev("Watchdog Dev"),
        SentinelMoon("Sentinel Moon"),
        VerusSemi("Verus Semi"),
        Sloth("Sloth"),
        Trojan("Trojan"),
        Timeout("Timeout"),
        NoPayload("No Payload"),
        PosCancel("Pos Cancel"),
        NullPlace("Null Place"),
        TransactionSpam("Transaction Spam"),
        Vehicle("Vehicle"),

        Hover("Hover");

        public String name;

        Mode(String name) { this.name = name; }

        public String toString() { return name; }
    }

    private boolean transaction;

    private final LinkedHashSet<PacketUtils.TimedPacket> packetQueue = new LinkedHashSet<>();
    private boolean sentinelNotified = false;

    @Override
    public void onEnable() {
        subModes.enable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        if (mode.getValue() == Mode.Sentinel) {
            sendAllPackets(true);
            sentinelNotified = false;
        }
        super.onDisable();
    }

    @EventLink
    private final Listener<PreUpdateEvent> preUpdateEventListener = event -> {
        setSuffix(mode.getValue().toString());

        if (mode.getValue() == Mode.Sentinel) {
            updateSentinel();
        }
    };

    @EventLink
    private final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        switch (mode.getValue()) {
            case VerusCombat -> {
                if (event.getPacket() instanceof S32PacketConfirmTransaction) {
                    event.setCancelled();
                    PacketUtils.sendSilentPacket(new C0FPacketConfirmTransaction(
                            (transaction ? 1 : -1),
                            (short) (transaction ? 1 : -1),
                            transaction));
                    transaction = !transaction;
                }
            }

            case Sentinel -> {
                if (event.getPacket() instanceof S00PacketKeepAlive) {
                    event.setCancelled();
                    synchronized (packetQueue) {
                        packetQueue.add(new PacketUtils.TimedPacket(event.getPacket()));
                    }
                }

                if (event.getPacket() instanceof S40PacketDisconnect) {
                    sendAllPackets(true);
                }
            }
        }
    };

    @EventLink
    private final Listener<PacketSendEvent> packetSendEventListener = event -> {
        if (mode.getValue() == Mode.VulcanReach) {
            if (event.getPacket() instanceof C02PacketUseEntity useEntity) {
                Entity victim = mc.theWorld.getEntityByID(useEntity.entityId);
                if (victim == null) return;

                if (useEntity.getAction().equals(C02PacketUseEntity.Action.ATTACK)) {
                    double playerX = mc.thePlayer.posX;
                    double playerY = mc.thePlayer.posY;
                    double playerZ = mc.thePlayer.posZ;

                    double targetX = victim.posX;
                    double targetY = victim.posY;
                    double targetZ = victim.posZ;

                    double dx = targetX - playerX;
                    double dy = targetY - playerY;
                    double dz = targetZ - playerZ;

                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance < 3.0) return;

                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double normalizedX = dx / length;
                    double normalizedZ = dz / length;

                    double moveDistance = Math.min(0.21, length);

                    mc.thePlayer.motionX = 0.08f;
                    mc.thePlayer.setPosition(
                            playerX + normalizedX * moveDistance,
                            playerY,
                            playerZ + normalizedZ * moveDistance);
                }
            }
        }

        if (mode.getValue() == Mode.Miniblox) {
            if (event.getPacket() instanceof C03PacketPlayer) {
                FlightModule flight = Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class);
                if (flight != null && flight.isEnabled()) {
                    return;
                }

                PacketBuffer packetbuffer = new PacketBuffer(Unpooled.buffer());
                packetbuffer.writeDouble(mc.thePlayer.lastTickPosX);
                packetbuffer.writeDouble(mc.thePlayer.lastTickPosY);
                packetbuffer.writeDouble(mc.thePlayer.lastTickPosZ);
                packetbuffer.writeFloat(mc.thePlayer.rotationYaw);
                packetbuffer.writeFloat(mc.thePlayer.rotationPitch);
                packetbuffer.writeFloat(mc.thePlayer.movementInput.moveForward);
                packetbuffer.writeFloat(mc.thePlayer.movementInput.moveStrafe);
                packetbuffer.writeBoolean(mc.thePlayer.movementInput.jump);
                packetbuffer.writeBoolean(mc.thePlayer.movementInput.sneak);
                packetbuffer.writeBoolean(mc.thePlayer.onGround);

                C17PacketCustomPayload payload = new C17PacketCustomPayload("miniblox:movepacket", packetbuffer);
                PacketUtils.sendSilentPacket(payload);
            }
        }
    };

    private void updateSentinel() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;

        if (mc.isSingleplayer()) {
            toggle();
            return;
        }

        NetworkPlayerInfo info = null;
        try {
            UUID uuid = mc.thePlayer.getGameProfile().getId();
            info = mc.getNetHandler().getPlayerInfo(uuid);
        } catch (Exception ignored) {}

        if (info != null) {
            int ping = info.getResponseTime();

            if (ping > 500) {
                if (!sentinelNotified) {
                    Logger.chatPrint("§aSentinel: Ping > 500ms — bypass active. You can now fly/speed.");
                    sentinelNotified = true;
                }
            } else {
                if (warningMessages.getValue()) {
                    SpeedModule speed = Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class);
                    FlightModule flight = Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class);

                    if (speed != null && speed.isEnabled()) {
                        Logger.chatPrint("§6Sentinel: Wait until bypass is active before using Speed.");
                        speed.setEnabled(false);
                    }

                    if (flight != null && flight.isEnabled()) {
                        Logger.chatPrint("§6Sentinel: Wait until bypass is active before using Flight.");
                        flight.setEnabled(false);
                    }
                }
            }
        }

        sendAllPackets(false);
    }

    private void sendAllPackets(boolean all) {
        synchronized (packetQueue) {
            packetQueue.removeIf(timed -> {
                long tickCount = mc.thePlayer != null ? mc.thePlayer.ticksExisted : 0;
                long delay = tickCount < 150 ? 5000L : 10000L;

                if (all || timed.getTime() <= System.currentTimeMillis() - delay) {
                    PacketUtils.receiveSilentPacket(timed.getPacket());
                    return true;
                }
                return false;
            });
        }
    }
}
