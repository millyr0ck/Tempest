package rip.tempest.processes;

import rip.tempest.api.config.Serializable;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DraggingProcess implements Serializable {
    public static final Map<String, DraggableComponent> components = new HashMap<>();
    private static String draggingComponent = null;
    private static double dragStartX, dragStartY;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static class DraggableComponent {
        private double x, y, width, height;

        public DraggableComponent(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }

        private double slackLeft, slackTop, slackRight, slackBottom;

        public void setSlack(double left, double top, double right, double bottom) {
            slackLeft = Math.max(0, left);
            slackTop = Math.max(0, top);
            slackRight = Math.max(0, right);
            slackBottom = Math.max(0, bottom);
        }

        public JsonObject save() {
            JsonObject object = new JsonObject();
            object.addProperty("x", x);
            object.addProperty("y", y);
            return object;
        }

        public void load(JsonObject object) {
            if (object.has("x")) x = object.get("x").getAsDouble();
            if (object.has("y")) y = object.get("y").getAsDouble();
        }
    }

    public static boolean isDragging(String key) {
        return key.equals(draggingComponent);
    }

    public static void update() {
        keepOnScreen();
        if (!(mc.currentScreen instanceof GuiChat)) draggingComponent = null;
    }

    public static void render() {
        if (mc.currentScreen instanceof GuiChat) handleDragging();
    }

    private static void keepOnScreen() {
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        ScaledResolution sr = new ScaledResolution(mc);
        double screenW = sr.getScaledWidth(), screenH = sr.getScaledHeight();
        for (DraggableComponent component : components.values()) {
            double width = Math.min(screenW, Math.max(1, component.getWidth()));
            double height = Math.min(screenH, Math.max(1, component.getHeight()));
            component.setX(Math.max(-component.slackLeft, Math.min(screenW - width + component.slackRight, component.getX())));
            component.setY(Math.max(-component.slackTop, Math.min(screenH - height + component.slackBottom, component.getY())));
        }
    }

    private static void handleDragging() {
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        boolean isLeftMouseDown = Mouse.isButtonDown(0);

        for (Map.Entry<String, DraggableComponent> entry : components.entrySet()) {
            DraggableComponent component = entry.getValue();
            if (component.getWidth() <= 1 && component.getHeight() <= 1)
                continue;

            double x = component.getX();
            double y = component.getY();
            double width = component.getWidth();
            double height = component.getHeight();

            Gui.drawRect((int) x - 2, (int) y - 2, (int) (x + width) + 2, (int) (y + height) + 2,
                    new Color(120, 120, 120, 70).getRGB());
        }

        if (draggingComponent != null) {
            if (isLeftMouseDown) {
                DraggableComponent component = components.get(draggingComponent);
                double x = mouseX - dragStartX, y = mouseY - dragStartY;

                boolean snap = !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                        && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
                double screenW = sr.getScaledWidth(), screenH = sr.getScaledHeight();
                if (snap && rip.tempest.modules.impl.client.ClientSettingsModule.snapGrid.getValue()) drawGrid(screenW, screenH);
                guideX = guideY = Double.NaN;
                if (snap) {
                    double[] snapped = snap(draggingComponent, component, x, y, screenW, screenH);
                    x = snapped[0];
                    y = snapped[1];
                }
                component.setX(x);
                component.setY(y);
                drawGuides(screenW, screenH);
            } else {
                draggingComponent = null;
            }
        } else if (isLeftMouseDown) {
            List<Map.Entry<String, DraggableComponent>> reversed = new ArrayList<>(components.entrySet());
            Collections.reverse(reversed);
            for (Map.Entry<String, DraggableComponent> entry : reversed) {
                DraggableComponent component = entry.getValue();
                if (component.getWidth() <= 1 && component.getHeight() <= 1)
                    continue;

                double x = component.getX();
                double y = component.getY();
                double width = component.getWidth();
                double height = component.getHeight();

                if (mouseX >= x - 2 && mouseX <= x + width + 2 && mouseY >= y - 2 && mouseY <= y + height + 2) {
                    draggingComponent = entry.getKey();
                    dragStartX = mouseX - component.getX();
                    dragStartY = mouseY - component.getY();
                    break;
                }
            }
        }
    }

    private static final double SNAP_RANGE = 4;

    private static double guideX = Double.NaN, guideY = Double.NaN;

    private static double[] snap(String key, DraggableComponent dragged, double x, double y, double screenW, double screenH) {
        boolean elements = rip.tempest.modules.impl.client.ClientSettingsModule.snapElements.getValue();
        boolean grid = rip.tempest.modules.impl.client.ClientSettingsModule.snapGrid.getValue();
        double gridSize = rip.tempest.modules.impl.client.ClientSettingsModule.gridSize.getValue();
        double w = dragged.getWidth(), h = dragged.getHeight();

        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
        if (elements) {
            xs.add(0.0); xs.add(screenW / 2); xs.add(screenW);
            ys.add(0.0); ys.add(screenH / 2); ys.add(screenH);
            for (Map.Entry<String, DraggableComponent> entry : components.entrySet()) {
                DraggableComponent other = entry.getValue();
                if (entry.getKey().equals(key) || other.getWidth() <= 1 && other.getHeight() <= 1) continue;
                xs.add(other.getX()); xs.add(other.getX() + other.getWidth() / 2); xs.add(other.getX() + other.getWidth());
                ys.add(other.getY()); ys.add(other.getY() + other.getHeight() / 2); ys.add(other.getY() + other.getHeight());
            }
        }

        double[] ax = snapAxis(x, w, xs), ay = snapAxis(y, h, ys);
        if (ax != null) {
            x = ax[0];
            guideX = ax[1];
        } else if (grid && gridSize > 0) x = Math.round(x / gridSize) * gridSize;
        if (ay != null) {
            y = ay[0];
            guideY = ay[1];
        } else if (grid && gridSize > 0) y = Math.round(y / gridSize) * gridSize;
        return new double[]{x, y};
    }

    private static double[] snapAxis(double start, double size, List<Double> guides) {
        double best = SNAP_RANGE + 1, newStart = 0, guide = 0;
        double[] offsets = {0, size / 2, size};
        for (double g : guides) {
            for (double offset : offsets) {
                double d = Math.abs(start + offset - g);
                if (d < best) {
                    best = d;
                    newStart = g - offset;
                    guide = g;
                }
            }
        }
        return best <= SNAP_RANGE ? new double[]{newStart, guide} : null;
    }

    private static void drawGrid(double screenW, double screenH) {
        double size = rip.tempest.modules.impl.client.ClientSettingsModule.gridSize.getValue();
        if (size < 2) return;
        Color line = new Color(255, 255, 255, 14);
        for (double gx = 0; gx <= screenW; gx += size) rip.tempest.utils.render.Glyphs.bar((float) gx, 0, 0.5f, (float) screenH, line);
        for (double gy = 0; gy <= screenH; gy += size) rip.tempest.utils.render.Glyphs.bar(0, (float) gy, (float) screenW, 0.5f, line);
    }

    private static void drawGuides(double screenW, double screenH) {
        Color accent = ColorProcess.getColor();
        Color c = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200);
        if (!Double.isNaN(guideX)) rip.tempest.utils.render.Glyphs.bar((float) guideX - 0.25f, 0, 0.5f, (float) screenH, c);
        if (!Double.isNaN(guideY)) rip.tempest.utils.render.Glyphs.bar(0, (float) guideY - 0.25f, (float) screenW, 0.5f, c);
    }

    @Override
    public JsonObject save() {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, DraggableComponent> entry : components.entrySet()) {
            object.add(entry.getKey(), entry.getValue().save());
        }
        return object;
    }

    @Override
    public void load(JsonObject object) {
        for (Map.Entry<String, DraggableComponent> entry : components.entrySet()) {
            if (object.has(entry.getKey())) {
                entry.getValue().load(object.getAsJsonObject(entry.getKey()));
            }
        }
    }
}
