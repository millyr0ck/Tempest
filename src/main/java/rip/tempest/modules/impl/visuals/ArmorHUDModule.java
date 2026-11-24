package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Armor HUD", category = ModuleCategory.VISUALS, description = "Shows equipped armor and its durability")
public final class ArmorHUDModule extends Module {
    private static final String DRAG_KEY = "ArmorHUD";

    public static final ModeProperty<Layout> layout = new ModeProperty<>("Layout", Layout.Horizontal);
    public static final Property<Boolean> showHeld = new Property<>("Show Held Item", true);
    public static final Property<Boolean> durability = new Property<>("Durability Bar", true);
    public static final Property<Boolean> percent = new Property<>("Show Percent", false);
    public static final Property<Boolean> background = new Property<>("Background", true);
    public static final NumberProperty scale = new NumberProperty("Scale", 1.0, 0.5, 2.0, 0.05);
    public static final NumberProperty spacing = new NumberProperty("Spacing", 4, 0, 12, 1);

    public enum Layout {
        Horizontal("Horizontal"),
        Vertical("Vertical");

        private final String name;

        Layout(String name) { this.name = name; }

        @Override
        public String toString() { return name; }
    }

    private static final int SLOT = 16;

    private final Map<Integer, Float> barAnim = new HashMap<>();

    private final Map<Integer, Float> slotAnim = new HashMap<>();
    private long lastMs;

    @EventLink
    public final Listener<Render2DEvent> render2DEventListener = e -> {
        render();
    };

    @EventLink
    public final Listener<ShaderEvent> shaderEventListener = e -> render();

    @Override
    public void onEnable() {
        super.onEnable();
        barAnim.clear();
        slotAnim.clear();
        lastMs = 0L;
    }

    private float ease(Map<Integer, Float> store, int key, float target, float speed, float dt) {
        Float cur = store.get(key);
        if (cur == null) {
            store.put(key, target);
            return target;
        }
        float next = cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - next) < 0.001f) next = target;
        store.put(key, next);
        return next;
    }

    private void render() {
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        long now = System.currentTimeMillis();
        float dt = lastMs == 0 ? 1f / 60f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;

        ItemStack[] items = new ItemStack[5];
        for (int i = 0; i < 4; i++) items[i] = mc.thePlayer.inventory.armorInventory[3 - i];
        items[4] = showHeld.getValue() ? mc.thePlayer.inventory.getCurrentItem() : null;

        boolean horizontal = layout.getValue() == Layout.Horizontal;
        float sc = scale.getValue().floatValue();
        float gap = spacing.getValue().floatValue();
        float cell = SLOT + gap;
        boolean bars = durability.getValue();
        float slotH = SLOT + (bars ? 4 : 0);

        float shown = 0;
        for (int i = 0; i < items.length; i++) {
            shown += ease(slotAnim, i, items[i] != null ? 1f : 0f, 14f, dt);
        }

        float w, h;
        if (horizontal) {
            w = (shown <= 0 ? 0 : shown * cell - gap) * sc;
            h = slotH * sc;
        } else {
            w = SLOT * sc;
            h = (shown <= 0 ? 0 : shown * (slotH + gap) - gap) * sc;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d == null) {
            d = new DraggingProcess.DraggableComponent(sr.getScaledWidth() / 2.0 + 100, sr.getScaledHeight() - 60);
            DraggingProcess.components.put(DRAG_KEY, d);
        }

        d.setWidth(Math.max(w, 40));
        d.setHeight(Math.max(h, SLOT));

        if (shown <= 0.01f) return;

        float x = (float) d.getX(), y = (float) d.getY();
        Color accent = ColorProcess.getColor();
        CustomFontRenderer fr = FontProcess.getFont("tempest");

        if (background.getValue()) {
            Gui.drawRect(x - 3, y - 3, x + w + 3, y + h + 3, new Color(0, 0, 0, 110).getRGB());
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(sc, sc, 1f);

        float cursor = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            float present = slotAnim.getOrDefault(i, 0f);
            if (present <= 0.01f) continue;

            float ix = horizontal ? cursor : 0;
            float iy = horizontal ? 0 : cursor;

            float slide = (1f - present) * 4f;

            if (stack != null) {
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();
                GlStateManager.color(1f, 1f, 1f, present);
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack,
                        (int) (ix + (horizontal ? slide : 0)), (int) (iy + (horizontal ? 0 : slide)));
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack,
                        (int) (ix + (horizontal ? slide : 0)), (int) (iy + (horizontal ? 0 : slide)), null);
                GlStateManager.disableDepth();
                RenderHelper.disableStandardItemLighting();
                GlStateManager.color(1f, 1f, 1f, 1f);
            }

            if (bars && stack != null && stack.getMaxDamage() > 0) {
                float frac = 1f - stack.getItemDamage() / (float) stack.getMaxDamage();
                float eased = ease(barAnim, i, frac, 9f, dt);

                float bx = ix, by = iy + SLOT + 1, bw = SLOT;
                Gui.drawRect(bx, by, bx + bw, by + 2, applyAlpha(new Color(0, 0, 0, 190), present).getRGB());
                Color c = durabilityColor(eased, accent);
                if (eased > 0.001f) {
                    Gui.drawRect(bx, by, bx + bw * eased, by + 2, applyAlpha(c, present).getRGB());
                }
            } else if (bars) {
                barAnim.remove(i);
            }

            if (percent.getValue() && stack != null && stack.getMaxDamage() > 0) {
                float frac = 1f - stack.getItemDamage() / (float) stack.getMaxDamage();
                String txt = Math.round(frac * 100) + "%";
                fr.drawStringWithShadow(txt, ix + (SLOT - fr.getStringWidth(txt)) / 2f, iy - fr.getHeight() - 1,
                        applyAlpha(durabilityColor(frac, accent), present).getRGB());
            }

            cursor += (horizontal ? cell : slotH + gap) * present;
        }

        GlStateManager.popMatrix();
        RenderUtils.resetColor();
    }

    private Color durabilityColor(float frac, Color accent) {
        if (frac >= 0.999f) return accent;
        if (frac > 0.5f) {
            float t = (frac - 0.5f) / 0.5f;
            return blend(new Color(230, 200, 60), new Color(70, 210, 100), t);
        }
        float t = frac / 0.5f;
        return blend(new Color(220, 60, 60), new Color(230, 200, 60), t);
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Color applyAlpha(Color c, float mul) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                (int) Math.max(0, Math.min(255, c.getAlpha() * mul)));
    }
}
