package rip.tempest.modules.impl.client.disabler;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.modules.SubMode;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.stats.StatList;

import java.awt.*;
import java.util.Locale;

import static rip.tempest.utils.Util.mc;

public final class HoverDisabler extends SubMode {
    private static final long FREE_MS = 5000L;

    private static final long HOLD_MS = 5000L;
    private static final long URGENT_MS = 1500L;
    private static final float POP_MS = 200f;
    private static final float BAR_W = 92f, BAR_H = 2f;
    private static final Color URGENT = new Color(235, 70, 70);
    private static final int GREY = new Color(205, 205, 205).getRGB(),
            BAR_BORDER = new Color(0, 0, 0, 170).getRGB(), BAR_TRACK = new Color(0, 0, 0, 110).getRGB();

    public static volatile boolean freePhase = false;

    private Runnable turnOff;

    private final Timer timer = new Timer();
    private volatile boolean hovering, free;
    private long shownAt;

    public HoverDisabler(Runnable turnOff) {
        this.turnOff = turnOff;
    }

    @Override
    protected void onEnable() {
        hovering = false;
        free = false;
        if (inGame() && MovementUtils.isOnGround()) {
            start(false);
        }
    }

    @Override
    protected void onDisable() {
        hovering = false;
        free = false;
        freePhase = false;
    }

    private void start(boolean sendPosition) {
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        if (sendPosition) {
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, true));
        }
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.16D, z, true));
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.07D, z, true));
        hovering = true;
        timer.reset();
        shownAt = System.currentTimeMillis();
        NotificationManager.post(NotificationType.INFO, "Disabler: Hover", "Wait for 5 seconds.", 5);
        mc.thePlayer.isAirBorne = true;
        mc.thePlayer.triggerAchievement(StatList.jumpStat);
    }

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (hovering && !free) {
            e.setX(0.0D);
            e.setY(0.0D);
            e.setZ(0.0D);
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        if (free) {
            if (timer.hasTimeElapsed(FREE_MS)) turnOff.run();
        } else if (!hovering) {
            if (MovementUtils.isOnGround()) start(true);
        } else {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionY = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
            mc.thePlayer.jumpMovementFactor = 0.0F;
            mc.thePlayer.noClip = true;
            mc.thePlayer.onGround = false;
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (hovering && !free && e.getPacket() instanceof C03PacketPlayer) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (hovering && !free && e.getPacket() instanceof S08PacketPlayerPosLook) {
            timer.reset();
            free = true;
            freePhase = true;
            mc.addScheduledTask(() -> NotificationManager.post(NotificationType.INFO, "Disabler: Hover", "Do whatever you want for 5 seconds!", 5));
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (!hovering || !inGame()) return;

        boolean open = free;
        long elapsed = timer.getTime();
        long remaining = Math.max(0L, FREE_MS - elapsed);
        float pop = Math.min(1f, (System.currentTimeMillis() - shownAt) / POP_MS);
        float scale = 1f - (1f - pop) * (1f - pop) * (1f - pop);

        CustomFontRenderer font = FontProcess.getFont("bold");
        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f, y = sr.getScaledHeight() / 2f - 30f;
        float barX = cx - BAR_W / 2f, barY = y + font.getHeight() + 3f;

        Color accent = ColorProcess.getColor();
        if (open && remaining < URGENT_MS) {
            accent = RenderUtils.interpolateColorC(accent, URGENT, 1f - (float) remaining / URGENT_MS);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.translate(-cx, -y, 0f);

        String time = String.format(Locale.ROOT, "%.1fs", (open ? remaining : elapsed) / 1000.0);
        String suffix = open ? " left" : " held";
        float timeW = font.getStringWidth(time);
        float x = cx - (timeW + font.getStringWidth(suffix)) / 2f;
        font.drawStringWithShadow(time, x, y, accent.getRGB());
        font.drawStringWithShadow(suffix, x + timeW, y, GREY);

        Gui.drawRect(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, BAR_BORDER);
        Gui.drawRect(barX, barY, barX + BAR_W, barY + BAR_H, BAR_TRACK);
        float fill = open ? (float) remaining / FREE_MS : Math.min(1f, (float) elapsed / HOLD_MS);
        Gui.drawRect(barX, barY, barX + BAR_W * fill, barY + BAR_H, accent.getRGB());

        GlStateManager.popMatrix();
    };
}
