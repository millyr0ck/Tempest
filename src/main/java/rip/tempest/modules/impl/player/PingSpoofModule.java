package rip.tempest.modules.impl.player;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Ping Spoof", category = ModuleCategory.PLAYER)
public final class PingSpoofModule extends Module {
    public enum Mode {
        Watchdog,
        Default,
        DirectCancellation
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Watchdog);
    private final NumberProperty spikeInterval = new NumberProperty("Spike Interval", 10000, () -> mode.getValue() == Mode.Watchdog, 5000, 20000, 500);
    private final NumberProperty spikeAmount = new NumberProperty("Spike Amount", 200, () -> mode.getValue() == Mode.Watchdog, 150, 500, 50);

    private final Deque<Packet<?>> packetQueue = new ArrayDeque<>();
    private final Timer spikeStopwatch = new Timer();
    private int stage = 0;

    private final ConcurrentLinkedQueue<Packet<?>> defaultQueue = new ConcurrentLinkedQueue<>();
    private final Timer defaultTimer = new Timer();

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        flushWatchdogQueue();
        defaultQueue.forEach(PacketUtils::sendPacketNoEvent);
        defaultQueue.clear();
        resetState();
        super.onDisable();
    }

    private void resetState() {
        packetQueue.clear();
        defaultQueue.clear();
        spikeStopwatch.reset();
        defaultTimer.reset();
        stage = 0;
    }

    private void flushWatchdogQueue() {
        while (!packetQueue.isEmpty()) {
            PacketUtils.sendPacketNoEvent(packetQueue.poll());
        }
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> resetState();

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (mode.getValue() != Mode.Watchdog) return;
        if (!event.isPre() || mc.getNetHandler() == null || !mc.getNetHandler().doneLoadingTerrain) return;

        if (stage == 0) {
            if (spikeStopwatch.hasTimeElapsed(MathUtils.getRandom(500, 1000))) {
                flushWatchdogQueue();
                spikeStopwatch.reset();
            }
            return;
        }

        if (stage == 1
                && spikeStopwatch.hasTimeElapsed(spikeInterval.getValue().intValue())
                && !isEnabled(FlightModule.class)
                && !isEnabled(ScaffoldModule.class)
                && AuraModule.target() == null) {
            spikeStopwatch.reset();
            stage = 2;
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> packetSendEventListener = event -> {
        switch (mode.getValue()) {
            case Watchdog:
                handleWatchdogSend(event);
                break;
            case Default:
                handleDefaultSend(event);
                break;
            case DirectCancellation:
                if (event.getPacket() instanceof C0FPacketConfirmTransaction
                        || event.getPacket() instanceof C00PacketKeepAlive) {
                    event.setCancelled();
                }
                break;
        }
    };

    private void handleWatchdogSend(PacketSendEvent event) {
        if (event.getPacket() == null || mc.getNetHandler() == null || !mc.getNetHandler().doneLoadingTerrain) return;

        if (event.getPacket() instanceof C03PacketPlayer) {
            final C03PacketPlayer packetPlayer = (C03PacketPlayer) event.getPacket();
            if (!packetPlayer.isMoving()) {
                event.setCancelled();
                return;
            }
            if (packetPlayer instanceof C03PacketPlayer.C05PacketPlayerLook) {
                event.setCancelled();
                return;
            }
        }

        switch (stage) {
            case 0:
                event.setCancelled();
                packetQueue.add(event.getPacket());
                break;
            case 1:
                flushWatchdogQueue();
                break;
            case 2:
                if (isEnabled(FlightModule.class)) {
                    spikeStopwatch.reset();
                    return;
                }
                if (event.getPacket() instanceof C03PacketPlayer
                        || event.getPacket() instanceof C00PacketKeepAlive
                        || event.getPacket() instanceof C0FPacketConfirmTransaction) {
                    packetQueue.add(event.getPacket());
                    event.setCancelled();
                }
                if (spikeStopwatch.hasTimeElapsed(spikeAmount.getValue().intValue())) {
                    flushWatchdogQueue();
                    spikeStopwatch.reset();
                    stage = 1;
                }
                break;
        }
    }

    private void handleDefaultSend(PacketSendEvent event) {
        if (event.getPacket() instanceof C0FPacketConfirmTransaction
                || event.getPacket() instanceof C00PacketKeepAlive) {
            defaultQueue.add(event.getPacket());
            event.setCancelled();
        }
        if (defaultTimer.hasTimeElapsed(1490)) {
            defaultQueue.forEach(PacketUtils::sendPacketNoEvent);
            defaultQueue.clear();
            defaultTimer.reset();
        }
    }

    @EventLink
    public final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        if (mode.getValue() != Mode.Watchdog) return;

        if (event.getPacket() instanceof S08PacketPlayerPosLook
                && !isEnabled(FlightModule.class)
                && mc.thePlayer != null && mc.thePlayer.ticksExisted > 10) {
            handlePosLook((S08PacketPlayerPosLook) event.getPacket());
            event.setCancelled();
        } else if (event.getPacket() instanceof S32PacketConfirmTransaction && stage == 0) {
            final S32PacketConfirmTransaction packet = (S32PacketConfirmTransaction) event.getPacket();
            if (packet.getWindowId() == 0 && packet.getActionNumber() < 0) {
                stage = 1;
            }
        }
    };

    private void handlePosLook(S08PacketPlayerPosLook packetIn) {
        final EntityPlayer entityplayer = mc.thePlayer;
        double d0 = packetIn.getX();
        double d1 = packetIn.getY();
        double d2 = packetIn.getZ();

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X)) {
            d0 += entityplayer.posX;
        } else {
            entityplayer.motionX = 0.0D;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y)) {
            d1 += entityplayer.posY;
        } else {
            entityplayer.motionY = 0.0D;
        }

        if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Z)) {
            d2 += entityplayer.posZ;
        } else {
            entityplayer.motionZ = 0.0D;
        }

        entityplayer.setPosition(d0, d1, d2);
        mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                entityplayer.posX, entityplayer.getEntityBoundingBox().minY, entityplayer.posZ, false));

        if (!mc.getNetHandler().doneLoadingTerrain) {
            entityplayer.prevPosX = entityplayer.posX;
            entityplayer.prevPosY = entityplayer.posY;
            entityplayer.prevPosZ = entityplayer.posZ;
            mc.getNetHandler().doneLoadingTerrain = true;
            mc.displayGuiScreen(null);
        }
    }

    private static boolean isEnabled(Class<? extends Module> moduleClass) {
        Module module = rip.tempest.Tempest.INSTANCE.getModuleManager().getModule(moduleClass);
        return module != null && module.isEnabled();
    }
}
