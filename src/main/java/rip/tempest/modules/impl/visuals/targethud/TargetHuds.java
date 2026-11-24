package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.modules.impl.visuals.TargetInterfaceModule;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.TargetSelectionProcess;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static rip.tempest.utils.Util.mc;

/**
 * Yuri's target HUD system: every target fades in and out, extra Kill Aura targets fill a grid
 * beside the main one, and the HUD can follow the target on screen. The styles themselves are
 * {@link TargetHudMode}s in the impl package. Target HUD hands its Rounded-family modes to this.
 *
 * <p>Adapted from Yuri's TargetHudModule (AGPL-3.0). Changes:
 * <ul>
 *   <li>It's a helper owned by Tempest's Target HUD module rather than a module, and it uses
 *       Tempest's Kill Aura, target list and dragging ("TargetInterface", so it keeps the
 *       position Target HUD already saved).</li>
 *   <li>The hurt flash works: Yuri's styles read hurtAnimStart but nothing ever set it, so it's
 *       now set whenever the target's health drops.</li>
 *   <li>Health particles, the equipment row and the 3D-model helper were left out: no style
 *       kept in Tempest uses them.</li>
 *   <li>Raw GL blend calls go through GlStateManager.</li>
 * </ul>
 */
public final class TargetHuds {
    private static final int MAX_COLS = 3;
    private static final int MAX_ROWS = 4;
    private static final int INFO_SPACING_X = 145;
    private static final int INFO_SPACING_Y = 53;
    private static final float DELTA_NANOS_SCALE = 500_000_000f;
    private static final long MIN_DELTA_NANOS = 250_000L;
    private static final String COMPONENT = "TargetInterface";

    public final Property<Boolean> grid;
    public final Property<Boolean> useCustomFont;
    public final Property<Boolean> follow;
    public final NumberProperty followOffset;

    private long lastRender2DTimeNanos = 0L;
    private final Map<UUID, TargetState> targetStates = new LinkedHashMap<>();
    private final Set<UUID> activeTargetsThisFrame = new HashSet<>();
    private final List<EntityLivingBase> listToRender = new ArrayList<>();
    private final List<TargetState> allRenderStates = new ArrayList<>();
    private float[] followPos;

    public TargetHuds(Property<Boolean> grid, Property<Boolean> useCustomFont, Property<Boolean> follow, NumberProperty followOffset) {
        this.grid = grid;
        this.useCustomFont = useCustomFont;
        this.follow = follow;
        this.followOffset = followOffset;
    }

    public void reset() {
        targetStates.clear();
    }

    private static EntityLivingBase mainTarget() {
        return TargetInterfaceModule.previewing() ? mc.thePlayer : TargetInterfaceModule.auraTarget();
    }

    public void render3D() {
        followPos = null;
        if (!follow.getValue()) return;

        EntityLivingBase mainTarget = mainTarget();
        if (mainTarget == null) return;

        float partial = mc.timer.renderPartialTicks;
        double x = mainTarget.lastTickPosX + (mainTarget.posX - mainTarget.lastTickPosX) * partial;
        double y = mainTarget.lastTickPosY + (mainTarget.posY - mainTarget.lastTickPosY) * partial + mainTarget.height;
        double z = mainTarget.lastTickPosZ + (mainTarget.posZ - mainTarget.lastTickPosZ) * partial;

        float[] projected = project2D(
                (float) (x - mc.getRenderManager().viewerPosX),
                (float) (y - mc.getRenderManager().viewerPosY),
                (float) (z - mc.getRenderManager().viewerPosZ),
                new ScaledResolution(mc).getScaleFactor());

        if (projected != null && projected[2] >= 0.0f && projected[2] < 1.0f) {
            followPos = projected;
        }
    }

    public void render2D(TargetHudMode modeInstance) {
        long nowNanos = System.nanoTime();
        long deltaNanos = lastRender2DTimeNanos == 0L ? 0L : (nowNanos - lastRender2DTimeNanos);
        lastRender2DTimeNanos = nowNanos;

        if (deltaNanos < MIN_DELTA_NANOS) return;

        float delta = deltaNanos / DELTA_NANOS_SCALE;
        long now = System.currentTimeMillis();

        activeTargetsThisFrame.clear();
        listToRender.clear();

        EntityLivingBase mainTarget = mainTarget();

        if (mainTarget != null) {
            listToRender.add(mainTarget);
            activeTargetsThisFrame.add(mainTarget.getUniqueID());
        }

        if (grid.getValue()) {
            addAdditionalTargets(listToRender, activeTargetsThisFrame, mainTarget);
        }

        updateTargetStates(listToRender, activeTargetsThisFrame, delta, now);

        allRenderStates.clear();
        allRenderStates.addAll(targetStates.values());
        if (allRenderStates.isEmpty() || modeInstance == null) return;

        if (follow.getValue()) {
            double[] base = getFollowBase(mainTarget, modeInstance);
            if (base != null) {
                TargetState state = targetStates.get(mainTarget.getUniqueID());
                modeInstance.draw(mainTarget, state, base[0], base[1], now, delta);
            }
            return;
        }

        if (!DraggingProcess.components.containsKey(COMPONENT)) {
            ScaledResolution sr = new ScaledResolution(mc);
            DraggingProcess.components.put(COMPONENT, new DraggingProcess.DraggableComponent(
                    (sr.getScaledWidth() - modeInstance.getMinWidth()) / 2.0,
                    (double) sr.getScaledHeight() / 10));
        }

        DraggingProcess.DraggableComponent draggable = DraggingProcess.components.get(COMPONENT);
        renderGrid(allRenderStates, draggable, modeInstance, now, delta);
    }

    public void renderShader(ShaderEvent event, TargetHudMode modeInstance) {
        if (event.getShaderType() != ShaderEvent.ShaderType.BLUR) return;

        allRenderStates.clear();
        allRenderStates.addAll(targetStates.values());
        if (allRenderStates.isEmpty() || modeInstance == null) return;

        if (follow.getValue()) {
            double[] base = getFollowBase(mainTarget(), modeInstance);
            if (base != null) {
                int panelWidth = modeInstance.getMinWidth();
                int panelHeight = modeInstance.getHudHeight() + modeInstance.getLabelHeight();
                Gui.drawRect((int) base[0], (int) base[1], (int) (base[0] + panelWidth), (int) (base[1] + panelHeight), 0xFFFFFFFF);
            }
            return;
        }

        DraggingProcess.DraggableComponent draggable = DraggingProcess.components.get(COMPONENT);
        if (draggable == null) return;

        renderBlurMask(allRenderStates, draggable, modeInstance);
    }

    private double[] getFollowBase(EntityLivingBase mainTarget, TargetHudMode modeInstance) {
        if (mainTarget == null || followPos == null) return null;
        if (followPos[2] < 0.0f || followPos[2] >= 1.0f) return null;

        TargetState state = targetStates.get(mainTarget.getUniqueID());
        if (state == null || state.alpha <= 0.01f) return null;

        int panelWidth = modeInstance.getMinWidth();
        int panelHeight = modeInstance.getHudHeight() + modeInstance.getLabelHeight();
        return new double[]{
                followPos[0] - panelWidth / 2.0,
                followPos[1] - panelHeight - followOffset.getValue().floatValue()
        };
    }

    private void renderBlurMask(List<TargetState> states, DraggingProcess.DraggableComponent draggable, TargetHudMode modeInstance) {
        boolean useGrid = grid.getValue();
        int panelWidth = modeInstance.getMinWidth();
        int panelHeight = modeInstance.getHudHeight() + modeInstance.getLabelHeight();

        for (int i = 0; i < states.size(); i++) {
            if (!useGrid && i > 0) break;

            int col = i % MAX_COLS;
            int row = i / MAX_COLS;

            double baseX = draggable.getX() + col * INFO_SPACING_X;
            double baseY = draggable.getY() + row * INFO_SPACING_Y;

            Gui.drawRect((int) baseX, (int) baseY, (int) (baseX + panelWidth), (int) (baseY + panelHeight), 0xFFFFFFFF);
        }
    }

    private void addAdditionalTargets(List<EntityLivingBase> list, Set<UUID> active, EntityLivingBase mainTarget) {
        if (TargetInterfaceModule.previewing()) return;

        if (TargetInterfaceModule.multiStacked()) {
            for (EntityLivingBase entity : TargetInterfaceModule.extraTargets()) {
                if (mainTarget != null && entity.getUniqueID().equals(mainTarget.getUniqueID())) continue;
                if (list.size() >= MAX_COLS * MAX_ROWS) break;
                if (active.add(entity.getUniqueID())) list.add(entity);
            }
        }
    }

    private void updateTargetStates(List<EntityLivingBase> listToRender, Set<UUID> active, float delta, long now) {
        for (EntityLivingBase entity : listToRender) {
            UUID id = entity.getUniqueID();
            targetStates.putIfAbsent(id, new TargetState(entity));
            TargetState state = targetStates.get(id);
            state.entity = entity;
            state.active = true;
            state.alpha = Math.min(1f, state.alpha + delta * 4f);

            float health = entity.getHealth();
            if (health < state.lastActualHealth) {
                state.hurtAnimStart = now;
            }
            state.lastActualHealth = health;
        }

        Iterator<Map.Entry<UUID, TargetState>> it = targetStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetState> entry = it.next();
            if (!active.contains(entry.getKey())) {
                TargetState state = entry.getValue();
                state.active = false;
                state.alpha -= delta * 4f;
                if (state.alpha <= 0f) {
                    it.remove();
                }
            }
        }
    }

    private void renderGrid(List<TargetState> states, DraggingProcess.DraggableComponent draggable, TargetHudMode modeInstance, long now, float delta) {
        boolean useGrid = grid.getValue();
        int totalColumns = useGrid ? Math.min(states.size(), MAX_COLS) : 1;
        int totalRows = useGrid ? (int) Math.ceil((double) states.size() / MAX_COLS) : 1;

        if (totalColumns == 0) totalColumns = 1;
        if (totalRows == 0) totalRows = 1;

        int totalWidth = modeInstance.getMinWidth() + (totalColumns - 1) * INFO_SPACING_X;
        int totalHeight = (modeInstance.getHudHeight() + modeInstance.getLabelHeight()) + (totalRows - 1) * INFO_SPACING_Y;

        draggable.setWidth(totalWidth);
        draggable.setHeight(totalHeight);

        for (int i = 0; i < states.size(); i++) {
            if (!useGrid && i > 0) break;

            TargetState state = states.get(i);
            EntityLivingBase entity = state.entity;
            if (entity == null) continue;

            int col = i % MAX_COLS;
            int row = i / MAX_COLS;

            int baseX = col * INFO_SPACING_X;
            int baseY = row * INFO_SPACING_Y;

            modeInstance.draw(entity, state, draggable.getX() + baseX, draggable.getY() + baseY, now, delta);
        }
    }

    public void renderPlayerFace(AbstractClientPlayer player, float x, float y, float size, float scale, float tintAmount, float alpha) {
        GlStateManager.pushMatrix();
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.translate(-centerX, -centerY, 0);

        GlStateManager.color(1f, 1f - tintAmount, 1f - tintAmount, alpha);

        mc.getTextureManager().bindTexture(player.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8, 8, 8, 8, (int) size, (int) size, 64, 64);

        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    private static final FloatBuffer PROJECT_MODEL = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECT_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer PROJECT_VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer PROJECT_WINDOW = BufferUtils.createFloatBuffer(4);

    private static float[] project2D(float x, float y, float z, int scaleFactor) {
        FloatBuffer modelMatrix = PROJECT_MODEL;
        FloatBuffer projectionMatrix = PROJECT_PROJECTION;
        IntBuffer viewport = PROJECT_VIEWPORT;
        FloatBuffer windowPosition = PROJECT_WINDOW;
        modelMatrix.clear();
        projectionMatrix.clear();
        viewport.clear();
        windowPosition.clear();

        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, projectionMatrix);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        modelMatrix.rewind();
        projectionMatrix.rewind();
        viewport.rewind();

        if (GLU.gluProject(x, y, z, modelMatrix, projectionMatrix, viewport, windowPosition)) {
            return new float[]{
                    windowPosition.get(0) / scaleFactor,
                    (mc.displayHeight - windowPosition.get(1)) / scaleFactor,
                    windowPosition.get(2)
            };
        }
        return null;
    }

    public static final class TargetState {
        public EntityLivingBase entity;
        public float displayHealth;
        public float lastActualHealth;
        public float previousDisplayHealth;
        public long hurtAnimStart = 0;
        public float alpha = 0f;
        public boolean active = true;

        TargetState(EntityLivingBase entity) {
            this.entity = entity;
            this.displayHealth = entity.getHealth();
            this.previousDisplayHealth = entity.getHealth();
            this.lastActualHealth = entity.getHealth();
        }
    }
}
