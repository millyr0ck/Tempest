package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S30PacketWindowItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Bypass", category = ModuleCategory.CLIENT, description = "Hypixel Watchdog bypass (transaction timing)")
public final class BypassModule extends Module {
    public enum Mode { WatchdogOff, Dong, Old }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.WatchdogOff);
    private final NumberProperty delay = new NumberProperty("Delay", 0, 0, 1000, 5);
    private final Property<Boolean> autoBypass = new Property<Boolean>("Auto Bypass", false);
    private final Property<Boolean> autoDong = new Property<Boolean>("Auto Dong", false);

    private final ConcurrentLinkedQueue<TimedPacket> pending = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> choked = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    public int bruh;
    public int randomDelay;
    public int lastUid;
    public int lastSentUid = -1;

    private long c13Last = 0;

    private long autoStart = -1;
    private int autoState = 0;

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        pending.clear();
        choked.clear();
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> worldChange();

    @EventLink
    public final Listener<rip.tempest.api.events.impl.world.TickEvent> onTick = e -> {
        if (mc.thePlayer == null) return;

        List<TimedPacket> toSend = new ArrayList<>();
        for (TimedPacket tp : pending) {
            if (tp.time <= System.currentTimeMillis()) toSend.add(tp);
        }
        for (TimedPacket tp : toSend) {
            pending.remove(tp);
            PacketUtils.sendPacketNoEvent(tp.packet);
        }

        setSuffix(mode.getValue().name());

        if (autoBypass.getValue() && ServerUtils.isOnHypixel() && autoState == 0 && mc.currentScreen == null
                && mc.thePlayer.ticksExisted > 25 && autoStart == -1) {
            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            if (held != null && held.getItem() != null && Item.getIdFromItem(held.getItem()) == 345) {
                autoStart = System.currentTimeMillis();
                autoState = 1;
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
            }
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (mc.currentScreen instanceof GuiConnecting || mc.currentScreen instanceof GuiDownloadTerrain
                || mc.currentScreen instanceof GuiMultiplayer || mc.currentScreen instanceof GuiDisconnected) {
            pending.clear();
        }

        if (autoBypass.getValue() && ServerUtils.isOnHypixel() && autoState < 2) {
            ScaledResolution sr = new ScaledResolution(mc);
            int cx = sr.getScaledWidth() / 2, cy = sr.getScaledHeight() / 2;
            boolean holdingCompass = mc.thePlayer != null
                    && mc.thePlayer.inventory.getCurrentItem() != null
                    && mc.thePlayer.inventory.getCurrentItem().getItem() != null
                    && Item.getIdFromItem(mc.thePlayer.inventory.getCurrentItem().getItem()) == 345;
            String msg = autoState == 0
                    ? (holdingCompass ? "§a§lWaiting to open Game Menu" : "§c§lHold the Compass in your hand")
                    : "§lListening for Game Menu...";
            int sw = mc.fontRendererObj.getStringWidth(msg);
            Gui.drawRect(cx - sw / 2 - 5, cy - 70, cx + sw / 2 + 5, cy - 50, 0x96000000);
            mc.fontRendererObj.drawStringWithShadow(msg, cx - sw / 2f, cy - 63, 0xFFFFFFFF);
        }

        if (lastSentUid > 1 && mode.getValue() == Mode.WatchdogOff) {
            ScaledResolution sr = new ScaledResolution(mc);
            int cx = sr.getScaledWidth() / 2, cy = sr.getScaledHeight() / 2;
            String msg = "§c§lWARNING §c| Bypass Error";
            int sw = mc.fontRendererObj.getStringWidth(msg);
            Gui.drawRect(cx - sw / 2 - 5, cy - 70, cx + sw / 2 + 5, cy - 50, 0x96000000);
            mc.fontRendererObj.drawStringWithShadow(msg, cx - sw / 2f, cy - 63, 0xFFFF5555);
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (mc.thePlayer == null) return;
        Packet<?> p = e.getPacket();

        if (p instanceof C00PacketKeepAlive && delay.getValue().longValue() > 0) {
            pending.add(new TimedPacket(p, delay.getValue().longValue()));
            e.setCancelled();
            return;
        }

        if (!(p instanceof C0FPacketConfirmTransaction)) return;
        C0FPacketConfirmTransaction tx = (C0FPacketConfirmTransaction) p;
        if (tx.getUid() >= 0) return;

        switch (mode.getValue()) {
            case Old:
                oldMode(e);
                break;
            case Dong:
                dongMode(e, tx);
                break;
            case WatchdogOff:
                watchdogOffMode(e, tx);
                break;
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!autoBypass.getValue() || !ServerUtils.isOnHypixel()) return;
        Packet<?> p = e.getPacket();
        if (p instanceof S2DPacketOpenWindow && autoState == 1) {
            S2DPacketOpenWindow open = (S2DPacketOpenWindow) p;
            if (open.getGuiId().contains("chest")) {
                long rtt = System.currentTimeMillis() - autoStart;
                int newDelay = Math.max(0, 500 - (int) (rtt / 2));

                newDelay = (newDelay / 5) * 5;
                delay.setValue((double) newDelay);
                NotificationManager.post(NotificationType.SUCCESS, "Bypass", "Delay set to " + newDelay + "ms");
                autoState = 2;
                e.setCancelled();
                PacketUtils.sendPacketNoEvent(new C0DPacketCloseWindow(open.getWindowId()));
            }
        } else if (p instanceof S30PacketWindowItems && autoState == 2) {
            autoState = 3;
            e.setCancelled();
        }
    };

    private void oldMode(PacketSendEvent e) {
        if (c13Delay(15_000)) {
            resetC13();
            flushChoked();
        }
    }

    private void dongMode(PacketSendEvent e, C0FPacketConfirmTransaction tx) {
        bruh++;
        if (bruh <= 5) {
            e.setCancelled();
            long minDelay = Math.max(250, delay.getValue().longValue());
            pending.add(new TimedPacket(new C0FPacketConfirmTransaction(tx.getWindowId(), tx.getUid(), tx.isAccepted()), minDelay));
            resetC13();
            lastSentUid = tx.getUid();
            lastUid = tx.getUid();
            return;
        }

        e.setCancelled();

        if (Math.abs(tx.getUid() - lastUid) > 5 && tx.getUid() != -1) {
            choked.add(tx);
            flushChoked();
            bruh = 0;
            lastUid = tx.getUid();
            return;
        }

        if ((bruh - 10) >= (45 + randomDelay)) {
            choked.add(tx);
            while (!choked.isEmpty()) {
                Packet<?> q = choked.poll();
                if (q != null) pending.add(new TimedPacket(q, 0));
            }
            lastSentUid = tx.getUid();
            randomDelay = 30 + random.nextInt(100);
            bruh = 10;
        } else {
            choked.add(tx);
        }

        resetC13();
        lastUid = tx.getUid();
    }

    private void watchdogOffMode(PacketSendEvent e, C0FPacketConfirmTransaction tx) {
        if (c13Delay(1000) && bruh > 0) {
            int max = choked.size();
            int sent = 0;
            while (!choked.isEmpty() && sent < max - 1) {
                Packet<?> q = choked.poll();
                if (q != null) {
                    sent++;
                    PacketUtils.sendPacketNoEvent(q);
                }
            }
            bruh = -1;
        }
        if (c13Delay(1000) && bruh == -1) {
            bruh = -2;
            lastSentUid = 1;
        }

        if (bruh == -2 && lastSentUid == -1) {
            flushChoked();
            bruh = -3;
        }

        if (bruh >= 0) {
            if (bruh == 0 && lastSentUid == -1) flushChoked();
            e.setCancelled();
            choked.add(tx);
            if (bruh == 0 || !c13Delay(5)) bruh++;
        } else {
            e.setCancelled();
            choked.add(tx);
            if (c13Delay(1000) && lastSentUid != 2) {
                resetC13();
                if (lastSentUid == 1) {
                    flushChoked();
                    resetPackets();
                    if (autoDong.getValue()) {
                        NotificationManager.post(NotificationType.WARNING, "Bypass", "Switching to Dong mode.");
                        bruh = 5;
                        lastUid = 0;
                        lastSentUid = -1;
                        mode.setValue(Mode.Dong);
                    } else {
                        NotificationManager.post(NotificationType.WARNING, "Bypass", "Possible WD ban – rejoin or finish quickly.");
                    }
                }
                lastSentUid++;
            }
            lastUid = tx.getUid();
        }
        resetC13();
    }

    private boolean c13Delay(long ms) {
        return System.currentTimeMillis() - c13Last >= ms;
    }

    private void resetC13() {
        c13Last = System.currentTimeMillis();
    }

    private void flushChoked() {
        while (!choked.isEmpty()) {
            Packet<?> q = choked.poll();
            if (q != null) PacketUtils.sendPacketNoEvent(q);
        }
        choked.clear();
    }

    private void resetPackets() {
        choked.clear();
    }

    private void worldChange() {
        bruh = 0;
        lastUid = 0;
        lastSentUid = -1;
        autoState = 0;
        autoStart = -1;
        if (mode.getValue() != Mode.WatchdogOff) resetPackets();
    }

    private void reset() {
        bruh = 0;
        lastUid = 0;
        lastSentUid = -1;
        autoState = 0;
        autoStart = -1;
        randomDelay = 0;
        pending.clear();
        choked.clear();
    }

    private static final class TimedPacket {
        final Packet<?> packet;
        final long time;
        TimedPacket(Packet<?> p, long delayMs) {
            this.packet = p;
            this.time = System.currentTimeMillis() + delayMs;
        }
    }
}
