package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.utils.render.Glyphs;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Radar", category = ModuleCategory.VISUALS, description = "Astolfo's radar: nearby entities around you, forward is up")
public final class RadarModule extends Module {
    private final Property<Boolean> players = new Property<>("Players", true);
    private final Property<Boolean> animals = new Property<>("Animals", true);
    private final Property<Boolean> monsters = new Property<>("Monsters", false);
    private final Property<Boolean> passives = new Property<>("Passives", false);
    private final Property<Boolean> invisibles = new Property<>("Invisibles", false);
    private final Property<Boolean> local = new Property<>("Local", false);
    private final Property<Boolean> items = new Property<>("Items", false);
    private final NumberProperty spacing = new NumberProperty("Spacing", 1, 1, 5, 1);
    private final NumberProperty size = new NumberProperty("Size", 100, 50, 250, 1);

    private static final String DRAG_KEY = "Radar";
    private static final int DEFAULT_COLOR = new Color(200, 200, 200).getRGB();

    public RadarModule() {
        DraggingProcess.components.computeIfAbsent(DRAG_KEY, k -> new DraggingProcess.DraggableComponent(150, 150));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        DraggingProcess.DraggableComponent c = DraggingProcess.components.get(DRAG_KEY);
        if (c != null) {
            c.setWidth(0);
            c.setHeight(0);
        }
    }

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        DraggingProcess.DraggableComponent c = DraggingProcess.components.computeIfAbsent(DRAG_KEY,
                k -> new DraggingProcess.DraggableComponent(150, 150));
        int s = size.getValue().intValue();
        c.setWidth(s);
        c.setHeight(s);

        float x = (float) c.getX(), y = (float) c.getY(), w = s, h = s;
        Color client = ColorProcess.getColor();
        float px = 1f / new ScaledResolution(mc).getScaleFactor();

        Glyphs.bar(x - 0.5f, y - 0.5f, w + 1, h + 1, new Color(0, 0, 0, 150));
        Glyphs.bar(x, y, w, px, new Color(17, 17, 17, 197));
        Glyphs.bar(x, y + h - px, w, px, new Color(17, 17, 17, 197));
        Glyphs.bar(x, y + px, px, h - 2 * px, new Color(17, 17, 17, 197));
        Glyphs.bar(x + w - px, y + px, px, h - 2 * px, new Color(17, 17, 17, 197));
        Glyphs.bar(x + px, y + px, w - 2 * px, h - 2 * px, new Color(17, 17, 17, 50));
        Glyphs.bar(x, y, w, 0.5f, client);
        Glyphs.bar(x, y + 0.5f, w, 0.5f, client.darker());

        Glyphs.bar(x + w / 2f - px / 2f, y + 1, px, h - 1, Color.WHITE);
        Glyphs.bar(x, y + h / 2f - px / 2f, w, px, Color.WHITE);

        float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
        float spread = spacing.getValue().floatValue();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!shown(entity)) continue;
            float dx = (float) (mc.thePlayer.posX - entity.posX);
            float dz = (float) (mc.thePlayer.posZ - entity.posZ);
            float down = (float) (dz * Math.cos(yaw) - dx * Math.sin(yaw)) / spread;
            float across = (float) (dx * Math.cos(yaw) + dz * Math.sin(yaw)) / spread;
            float dotX = x + w / 2f + across, dotY = y + h / 2f + down;

            if (dotX < x) dotX = x;
            if (dotX > x + w - 3) dotX = x + w - 3;
            if (dotY < y + 0.5f) dotY = y + 1.5f;
            if (dotY >= y + h - 3) dotY = y + h - 3;
            Glyphs.bar(dotX, dotY, 2, 2, new Color(color(entity), true));
        }
        GlStateManager.color(1, 1, 1, 1);
    };

    private boolean shown(Entity entity) {
        boolean realPlayer = entity instanceof EntityPlayer && !AntiBotModule.botList.contains(entity) && entity != mc.thePlayer;
        boolean localShown = entity == mc.thePlayer && mc.gameSettings.thirdPersonView > 0 && local.getValue();
        if (realPlayer || localShown) {
            if (entity.isInvisible() && !invisibles.getValue()) return false;
            return players.getValue();
        }
        if (entity instanceof EntityMob && monsters.getValue()) return true;
        if (entity instanceof EntityAnimal && animals.getValue()) return true;
        if (entity instanceof EntityTameable && passives.getValue()) return true;
        if (entity instanceof EntityItem && items.getValue()) return true;
        return entity instanceof EntityAgeable && passives.getValue();
    }

    private int color(Entity entity) {
        if (entity instanceof EntityItem) return Color.WHITE.getRGB();
        if (entity == TargetInterfaceModule.auraTarget()) return ColorProcess.getColor().getRGB() | 0xFF000000;
        return DEFAULT_COLOR;
    }
}
