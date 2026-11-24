package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Potion HUD", category = ModuleCategory.VISUALS, description = "Radium's potion and status lines")
public final class PotionHUDModule extends Module {
    private static final String DRAG_KEY = "PotionHUD";

    private static final int LINE = 12;

    private final Property<Boolean> potions = new Property<>("Potions", true);
    private final Property<Boolean> fps = new Property<>("FPS", true);
    private final Property<Boolean> coords = new Property<>("Coords", false);
    private final Property<Boolean> customFont = new Property<>("Custom Font", true);
    public static final Property<Boolean> hideAmbient = new Property<>("Hide Beacon Effects", false);

    private double lastW = -1, lastH = -1;

    private static final class Line {
        final String text;
        final int color;

        Line(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    @EventLink
    public final Listener<Render2DEvent> render2DEventListener = e -> {
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        List<Line> lines = new ArrayList<>();
        if (coords.getValue()) {
            lines.add(new Line(String.format("X: \2477%.0f \247FY: \2477%.1f \247FZ: \2477%.0f",
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ), -1));
        }
        if (fps.getValue()) lines.add(new Line("FPS: " + Minecraft.getDebugFPS(), -1));
        if (potions.getValue()) {
            for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
                if (hideAmbient.getValue() && effect.getIsAmbient()) continue;
                Potion potion = Potion.potionTypes[effect.getPotionID()];
                if (potion == null) continue;
                String text = I18n.format(potion.getName()) + " " + (effect.getAmplifier() + 1) + " \2477" + Potion.getDurationString(effect);
                lines.add(new Line(text, potion.getLiquidColor() | 0xFF000000));
            }
        }

        CustomFontRenderer font = customFont.getValue() ? FontProcess.getFont("radium") : FontProcess.getFont("mc");
        float width = 0;
        for (Line line : lines) width = Math.max(width, font.getStringWidth(line.text));
        float height = Math.max(LINE, lines.size() * LINE);
        width = Math.max(width, 40);

        ScaledResolution sr = new ScaledResolution(mc);
        float sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d == null) {
            d = new DraggingProcess.DraggableComponent(sw - 2 - width, sh - height);
            DraggingProcess.components.put(DRAG_KEY, d);
        }

        double oldW = lastW >= 0 ? lastW : width, oldH = lastH >= 0 ? lastH : height;
        boolean anchorRight = d.getX() + oldW / 2 > sw / 2;
        boolean anchorBottom = d.getY() + oldH / 2 > sh / 2;
        if (lastW >= 0) {
            if (anchorRight) d.setX(d.getX() + lastW - width);
            if (anchorBottom) d.setY(d.getY() + lastH - height);
        }

        d.setX(Math.max(0, Math.min(sw - width, d.getX())));
        d.setY(Math.max(0, Math.min(sh - height, d.getY())));
        d.setWidth(width);
        d.setHeight(height);
        lastW = width;
        lastH = height;

        float left = (float) d.getX(), right = left + width;

        float y = anchorBottom ? (float) (d.getY() + height - LINE) : (float) d.getY();
        GlStateManager.enableBlend();
        for (Line line : lines) {
            float x = anchorRight ? right - font.getStringWidth(line.text) : left;
            font.drawStringWithShadow(line.text, x, y, line.color);
            y += anchorBottom ? -LINE : LINE;
        }
        GlStateManager.color(1, 1, 1, 1);
    };

    @Override
    public void onEnable() {
        lastW = lastH = -1;
        super.onEnable();
    }
}
