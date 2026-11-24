package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.BlinkProcess;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.render.GlUtils;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.AxisAlignedBB;

import java.awt.*;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Blink", category = ModuleCategory.CLIENT)
public class BlinkModule extends Module {

    public ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Constant);
    public NumberProperty delay = new NumberProperty("Delay", 20, () -> mode.getValue() == Mode.Pulse, 0, 100, 1);
    public static final Property<Boolean> renderBlinkPos = new Property<>("Render Blink Pos", true);
    public static ModeProperty<RenderMode> renderModeProperty = new ModeProperty<>("Render Mode", RenderMode.Box, () -> renderBlinkPos.getValue());

    public enum Mode {
        Constant,
        Pulse
    }

    private double blinkedX, blinkedY, blinkedZ;
    private boolean hasStoredPosition = false;
    Timer timer = new Timer();

    private enum RenderMode {
        Box,
        Player
    }

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (mode.getValue() == Mode.Constant) {
            BlinkProcess.enable();
        } else if (mode.getValue() == Mode.Pulse) {
            if (timer.hasTimeElapsed(delay.getValue().longValue() * 10)) {
                BlinkProcess.disable();
                timer.reset();
            } else {
                BlinkProcess.enable();
            }
        }
    };

    @EventLink
    public Listener<MotionEvent> motionEventListener = event -> {
        if (event.isPre() && !hasStoredPosition) {
            blinkedX = mc.thePlayer.posX;
            blinkedY = mc.thePlayer.posY;
            blinkedZ = mc.thePlayer.posZ;
            hasStoredPosition = true;
        }
    };

    @EventLink
    public Listener<Render3DEvent> render3DEventListener = event -> {
        if (!hasStoredPosition || !renderBlinkPos.getValue() || mc.gameSettings.thirdPersonView == 0) return;

        double x = blinkedX - mc.getRenderManager().viewerPosX;
        double y = blinkedY - mc.getRenderManager().viewerPosY;
        double z = blinkedZ - mc.getRenderManager().viewerPosZ;

        if (renderModeProperty.getValue() == RenderMode.Box) {
            AxisAlignedBB bb = new AxisAlignedBB(
                    x - 0.3, y, z - 0.3,
                    x + 0.3, y + 1.8, z + 0.3
            );

            Color color = ColorProcess.getColor();
            RenderUtils.start3D();
            GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f,
                    color.getBlue() / 255f, color.getAlpha() / 255f);
            RenderUtils.drawBoundingBox(bb);
            RenderUtils.stop3D();
            GlUtils.resetColor();
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);

            mc.getRenderManager().renderEntityStatic(mc.thePlayer, mc.timer.renderPartialTicks, false);

            GlStateManager.popMatrix();
        }
    };

    @Override
    public void onDisable() {
        BlinkProcess.disable();
        
        hasStoredPosition = false;
        super.onDisable();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        hasStoredPosition = false;
    }
}