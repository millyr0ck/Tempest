package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.render.Glyphs;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Hitmarkers", category = ModuleCategory.VISUALS)
public final class HitmarkersModule extends Module {
    private enum Mode {
        Hit,
        Exhibition
    }

    private static final ResourceLocation PICTURE = new ResourceLocation("tempest/images/hitmarker.png");

    private static final long CONFIRM_WINDOW = 800L;

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Hit);
    private final NumberProperty duration = new NumberProperty("Duration", 450, 100, 1500, 25, Representation.MILLISECONDS);
    private final NumberProperty size = new NumberProperty("Size", 7, this::hit, 3, 20, 0.5);
    private final NumberProperty gap = new NumberProperty("Gap", 3, this::hit, 0, 12, 0.5);
    private final NumberProperty pictureSize = new NumberProperty("Picture Size", 20, () -> !hit(), 8, 64, 1);

    private final Map<Integer, Long> attacked = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastHurt = new ConcurrentHashMap<>();
    private volatile long markedAt = -1L;

    private boolean hit() {
        return mode.getValue() == Mode.Hit;
    }

    @Override
    public void onEnable() {
        attacked.clear();
        lastHurt.clear();
        markedAt = System.currentTimeMillis();
    }

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!(e.getPacket() instanceof C02PacketUseEntity use) || use.getAction() != C02PacketUseEntity.Action.ATTACK || mc.theWorld == null) return;

        Entity entity = mc.theWorld.getEntityByID(use.entityId);

        lastHurt.putIfAbsent(use.entityId, entity instanceof EntityLivingBase living ? living.hurtTime : 0);
        attacked.put(use.entityId, System.currentTimeMillis());
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (mc.theWorld == null || !(e.getPacket() instanceof S19PacketEntityStatus status)) return;

        if (status.getOpCode() != 2 && status.getOpCode() != 3) return;

        Entity entity = status.getEntity(mc.theWorld);
        if (entity == null) return;

        Long at = attacked.get(entity.getEntityId());
        if (at != null && System.currentTimeMillis() - at <= CONFIRM_WINDOW) {
            markedAt = System.currentTimeMillis();
        }
    };

    @EventLink
    public final Listener<PreUpdateEvent> onUpdate = e -> {
        if (mc.theWorld == null) return;

        long now = System.currentTimeMillis();
        attacked.values().removeIf(at -> now - at > CONFIRM_WINDOW);
        lastHurt.keySet().retainAll(attacked.keySet());

        for (Integer id : attacked.keySet()) {
            if (!(mc.theWorld.getEntityByID(id) instanceof EntityLivingBase living)) continue;

            int previous = lastHurt.getOrDefault(id, 0);
            if (living.hurtTime > previous) markedAt = now;
            lastHurt.put(id, living.hurtTime);
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (mc.thePlayer == null) return;

        long elapsed = System.currentTimeMillis() - markedAt;
        if (markedAt < 0 || elapsed >= duration.getValue()) return;

        float progress = (float) (elapsed / duration.getValue());
        float alpha = 1 - progress * progress;
        if (alpha < 0.03f) return;

        float pop = 1 + 0.35f * (1 - Math.min(1, elapsed / 90f));

        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f, cy = sr.getScaledHeight() / 2f;

        if (hit()) {
            drawStrokes(cx, cy, alpha, pop);
        } else {
            drawPicture(cx, cy, alpha, pop);
        }
    };

    private void drawStrokes(float cx, float cy, float alpha, float pop) {
        int a = Math.round(255 * alpha);
        float inner = gap.getValue().floatValue() * pop;
        float outer = inner + size.getValue().floatValue() * pop;
        float diagonal = (float) Math.sqrt(0.5);

        Color edge = new Color(0, 0, 0, a), fill = new Color(255, 255, 255, a);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                float x1 = cx + dx * inner * diagonal, y1 = cy + dy * inner * diagonal;
                float x2 = cx + dx * outer * diagonal, y2 = cy + dy * outer * diagonal;
                Glyphs.stroke(x1, y1, x2, y2, 3.2f, edge);
                Glyphs.stroke(x1, y1, x2, y2, 1.6f, fill);
            }
        }
    }

    private void drawPicture(float cx, float cy, float alpha, float pop) {
        float half = pictureSize.getValue().floatValue() * pop / 2f;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        mc.getTextureManager().bindTexture(PICTURE);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(cx - half, cy + half, 0).tex(0, 1).endVertex();
        wr.pos(cx + half, cy + half, 0).tex(1, 1).endVertex();
        wr.pos(cx + half, cy - half, 0).tex(1, 0).endVertex();
        wr.pos(cx - half, cy - half, 0).tex(0, 0).endVertex();
        tessellator.draw();

        GlStateManager.enableAlpha();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
