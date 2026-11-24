package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import org.apache.commons.lang3.StringUtils;

import net.minecraft.network.play.server.S45PacketTitle;

import java.awt.*;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Authenticate", category = ModuleCategory.PLAYER)
public final class AutoAuthenticateModule extends Module {

    private final NumberProperty delay   = new NumberProperty("Delay", 2000, 500, 5000, 100);
    private final Property<String> password = new Property<>("Password", "");

    private long runAt, startAt;
    private String runCommand;

    private static final String[] PASSWORD_PLACEHOLDERS = {"password", "pass", "contrasena", "contraseña", "senha", "/logar"};



    @EventLink
    public final Listener<PacketReceiveEvent> packetListener = e -> {
        String msg = null;

        if (e.getPacket() instanceof S02PacketChat) {
            S02PacketChat packet = (S02PacketChat) e.getPacket();
            if (packet.getChatComponent() != null) {
                msg = packet.getChatComponent().getUnformattedText().toLowerCase();
            }
        } else if (e.getPacket() instanceof S45PacketTitle) {
            S45PacketTitle packet = (S45PacketTitle) e.getPacket();
            if (packet.getMessage() != null) {
                msg = packet.getMessage().getUnformattedText().toLowerCase();
            }
        }

        if (msg == null) return;

        String pw = (String) password.getValue();
        if (pw == null || pw.isEmpty()) return;
        if (msg.contains("segundos para logar") || msg.contains("segundos para registrar")
                || msg.contains("registrar") || msg.contains("logar") || msg.contains("register")|| msg.contains("login")) {
            if (runCommand == null) {
                if (msg.contains("registrar")|| msg.contains("register")) {
                    setRun("/register " + pw + " " + pw);
                } else {
                    setRun("/login " + pw);
                }
            }
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionListener = e -> {
        if (runAt > 0 && System.currentTimeMillis() >= runAt && runCommand != null) {
            mc.thePlayer.sendChatMessage(runCommand);
            reset();
        }
    };

    @EventLink
    public final Listener<Render2DEvent> render2DListener = e -> {
        if (runAt <= System.currentTimeMillis() || runCommand == null) return;

        ScaledResolution sr  = new ScaledResolution(mc);
        float width  = 120, height = 5;
        float left   = sr.getScaledWidth() / 2.0F - width / 2.0F;
        float top    = sr.getScaledHeight() / 2.0F + 30;

        float calc = (float)(System.currentTimeMillis() - startAt) / (float)(runAt - startAt);
        calc = Math.min(1, Math.max(0, calc));

        Color accent = ColorProcess.getColor();

        GlStateManager.pushMatrix();
        Gui.drawRect((int) left, (int) top, (int)(left + width), (int)(top + height),
                accent.darker().darker().getRGB());
        Gui.drawRect((int) left, (int) top, (int)(left + width * calc), (int)(top + height),
                accent.getRGB());
        GlStateManager.popMatrix();
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldListener = e -> reset();

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    private int countPasswordWords(String data) {
        int count = 0;
        for (String placeholder : PASSWORD_PLACEHOLDERS)
            count += StringUtils.countMatches(data, placeholder);
        return count;
    }

    private void setRun(String command) {
        long now   = System.currentTimeMillis();
        this.startAt    = now;
        this.runAt      = now + delay.getValue().longValue();
        this.runCommand = command.trim();

        if (mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "\247aAutoAuth\247f: logging in as \247e" + password.getValue()
                            + "\247f in \247b" + delay.getValue().intValue() + "ms\247f..."));
    }

    private void reset() {
        this.startAt = this.runAt = 0;
        this.runCommand = null;
    }
}