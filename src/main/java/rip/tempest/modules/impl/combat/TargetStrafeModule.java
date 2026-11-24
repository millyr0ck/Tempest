package rip.tempest.modules.impl.combat;

import rip.tempest.processes.RotationProcess;
import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.JumpEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.combat.targetstrafe.ExhibitionPath;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.movement.LongJumpModule;
import rip.tempest.modules.impl.movement.SpeedModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PlayerUtils;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.misc.Pair;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Target Strafe", category = ModuleCategory.COMBAT)
public final class TargetStrafeModule extends Module {
    private enum Mode {
        Tempest,

        Exhibition
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Tempest);
    private final NumberProperty range = new NumberProperty("Range", 3.0, this::tempest, 0.5, 6.0, 0.1);
    private final NumberProperty speed = new NumberProperty("Strafe Speed", 1.0, this::tempest, 0.1, 2.0, 0.05);
    private final Property<Boolean> holdJump = new Property<>("Hold Jump", false, this::tempest);
    private final Property<Boolean> strictMode = new Property<>("Strict Mode", false, this::tempest);
    private final Property<Boolean> adaptiveRange = new Property<>("Adaptive Range", true, this::tempest);
    private final Property<Boolean> autoDirection = new Property<>("Auto Direction", true, this::tempest);

    private final ModeProperty<ExhibitionPath.TargetMode> targetMode = new ModeProperty<>("Target Mode", ExhibitionPath.TargetMode.Nearby, this::exhibition);
    private final NumberProperty targetRange = new NumberProperty("Target Range", 5.0,
            () -> exhibition() && targetMode.getValue() == ExhibitionPath.TargetMode.Nearby, 1.0, 10.0, 0.1);
    private final ModeProperty<ExhibitionPath.PathMode> pathMode = new ModeProperty<>("Path Mode", ExhibitionPath.PathMode.Normal, this::exhibition);
    private final NumberProperty radius = new NumberProperty("Radius", 2.0, this::exhibition, 0.1, 5.0, 0.1);
    private final Property<Boolean> behind = new Property<>("Behind", false, this::exhibition);
    private final NumberProperty offset = new NumberProperty("Offset", 0, () -> exhibition() && behind.getValue(), -180, 180, 5);
    private final Property<Boolean> teams = new Property<>("Teams", false, () -> exhibition() && targetMode.getValue() == ExhibitionPath.TargetMode.Nearby);
    private final Property<Boolean> antiStuck = new Property<>("Anti-Stuck", true, this::exhibition);
    private final Property<Boolean> antiVoid = new Property<>("Anti-Void", false, () -> exhibition() && antiStuck.getValue());
    private final NumberProperty jitterDistance = new NumberProperty("Jitter Dist", 0, this::exhibition, 0, 2, 0.1);
    private final NumberProperty jitterFrequency = new NumberProperty("Jitter Freq", 0.5, () -> exhibition() && jitterDistance.getValue() > 0, 0, 2, 0.1);

    private final Property<Boolean> renderPath = new Property<>("Render Path", true);
    private final NumberProperty pathPoints = new NumberProperty("Path Points", 32, () -> renderPath.getValue(), 8, 64, 4);
    private final Property<Boolean> renderTarget = new Property<>("Render Target", true);

    private final NumberProperty lineThickness = new NumberProperty("Line Thickness", 4, () -> renderPath.getValue() || renderTarget.getValue(), 1, 10, 0.5);
    private final NumberProperty lineOpacity = new NumberProperty("Line Opacity", 220, () -> renderPath.getValue() || renderTarget.getValue(), 50, 255, 5);

    private final ExhibitionPath exhibitionPath = new ExhibitionPath(radius, targetRange, targetMode, pathMode, behind, teams,
            antiStuck, antiVoid, offset, jitterDistance, jitterFrequency);

    private boolean tempest() {
        return mode.getValue() == Mode.Tempest;
    }

    private boolean exhibition() {
        return mode.getValue() == Mode.Exhibition;
    }

    public static float runYaw() {
        TargetStrafeModule module = steeringModule();
        if (module == null) {
            return mc.thePlayer.rotationYaw;
        }
        if (!module.exhibition()) {
            return module.yaw;
        }
        return module.yaw - ExhibitionPath.inputOffset(mc.thePlayer.movementInput.moveForward, mc.thePlayer.movementInput.moveStrafe);
    }

    public static boolean steering() {
        return steeringModule() != null;
    }

    public static boolean strictInput() {
        TargetStrafeModule module = steeringModule();
        return module != null && !module.exhibition() && module.strictMode.getValue();
    }

    private static TargetStrafeModule steeringModule() {
        if (mc.thePlayer == null) return null;
        TargetStrafeModule module = Tempest.INSTANCE.getModuleManager().getModule(TargetStrafeModule.class);
        if (module == null || !module.isEnabled() || !module.active || module.target == null) return null;
        return module;
    }

    private double circleRadius() {
        return exhibition() ? radius.getValue() : range.getValue();
    }

    private float yaw;
    private Entity target;
    private boolean left, colliding;
    private boolean active;
    private final List<Vector3d> pathTrail = new ArrayList<>();
    private int directionSwitchTicks = 0;

    @EventLink(value = Priorities.HIGH)
    public final Listener<JumpEvent> onJump = event -> {
        setSuffix(exhibition() ? pathMode.getValue().toString() : range.getValue().toString());
        if (target != null && active) {
            event.setYaw(yaw);
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<StrafeEvent> onStrafe = event -> {
        if (target != null && active && RotationProcess.fixesMovement()) return;

        if (target != null && active && exhibition()) {
            event.setYaw(yaw - ExhibitionPath.inputOffset(event.getForward(), event.getStrafe()));
            return;
        }

        if (target != null && active) {
            event.setYaw(yaw);

            if (strictMode.getValue()) {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
            }
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (exhibition()) {
            updateExhibition();
            return;
        }

        ScaffoldModule scaffold = Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class);

        if (scaffold != null && scaffold.isEnabled()) {
            active = false;
            target = null;
            return;
        }

        if (!Tempest.INSTANCE.getModuleManager().getModule(AuraModule.class).isEnabled()) {
            active = false;
            target = null;
            return;
        }

        SpeedModule speedModule = Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class);
        FlightModule flight = Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class);

        boolean canStrafe = (flight != null && flight.isEnabled()) || (speedModule != null && speedModule.isEnabled());

        if (!strictMode.getValue()) {
            if (holdJump.getValue() && !mc.gameSettings.keyBindJump.isKeyDown()) {
                active = false;
                target = null;
                return;
            }

            if (!canStrafe || !mc.gameSettings.keyBindForward.isKeyDown()) {
                active = false;
                target = null;
                return;
            }
        }

        EntityLivingBase auraTarget = AuraModule.target();
        final List<Entity> targets = auraTarget == null ? Collections.emptyList() : Collections.singletonList(auraTarget);

        if (targets.isEmpty()) {
            active = false;
            target = null;
            return;
        }

        active = true;
        target = targets.get(0);

        if (target == null) {
            return;
        }

        directionSwitchTicks++;

        boolean shouldSwitchDirection = false;

        if (mc.thePlayer.isCollidedHorizontally) {
            shouldSwitchDirection = true;
        }

        if (!strictMode.getValue() && !PlayerUtils.isBlockUnder(5, false)) {
            shouldSwitchDirection = true;
        }

        if (autoDirection.getValue() && directionSwitchTicks >= 40) {
            double currentDist = mc.thePlayer.getDistanceToEntity(target);
            if (currentDist < range.getValue() - 0.5) {
                shouldSwitchDirection = true;
                directionSwitchTicks = 0;
            }
        }

        if (shouldSwitchDirection && !colliding) {
            left = !left;
            colliding = true;
        } else if (!shouldSwitchDirection) {
            colliding = false;
        }

        double effectiveRange = range.getValue();

        if (adaptiveRange.getValue()) {
            double distance = mc.thePlayer.getDistanceToEntity(target);
            effectiveRange = MathHelper.clamp_double(distance, range.getValue() * 0.5, range.getValue());
        }

        float baseYaw = RotationUtils.calculate(target).getX();
        float offset = (90 + 45) * (left ? -1 : 1);
        yaw = baseYaw + offset;

        final double strafeRange = effectiveRange + Math.random() / 100f;
        final double posX = -MathHelper.sin((float) Math.toRadians(yaw)) * strafeRange + target.posX;
        final double posZ = MathHelper.cos((float) Math.toRadians(yaw)) * strafeRange + target.posZ;

        yaw = RotationUtils.calculate(new Vector3d(posX, target.posY, posZ)).getX();

        this.yaw = yaw;
        mc.thePlayer.movementYaw = this.yaw;

        if (strictMode.getValue()) {
            double currentSpeed = speed.getValue() * MovementUtils.getBaseMoveSpeed();
            MovementUtils.setSpeed(currentSpeed, this.yaw, 0, 1);
        }

        updatePathTrail();
    };

    private void updateExhibition() {
        active = false;
        target = null;

        ScaffoldModule scaffold = Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class);
        if (scaffold != null && scaffold.isEnabled()) return;

        exhibitionPath.antiStuck();
        target = exhibitionPath.findTarget();
        if (target == null) return;

        updatePathTrail();

        SpeedModule speedModule = Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class);
        FlightModule flight = Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class);
        LongJumpModule longJump = Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class);
        boolean canStrafe = speedModule != null && speedModule.isEnabled() || flight != null && flight.isEnabled()
                || longJump != null && longJump.isEnabled();
        if (!canStrafe) return;

        double[] point = exhibitionPath.point(target);
        yaw = RotationUtils.calculate(new Vector3d(point[0], target.posY, point[1])).getX();
        active = true;

        mc.thePlayer.movementYaw = runYaw();
    }

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (target == null) return;

        if (renderPath.getValue()) {
            renderStrafeCircle();
        }

        if (renderTarget.getValue()) {
            renderTargetIndicator();
        }
    };

    private void updatePathTrail() {
        if (!renderPath.getValue() || target == null) return;

        pathTrail.clear();
        int points = pathPoints.getValue().intValue();

        for (int i = 0; i < points; i++) {
            float angle = (float) (2 * Math.PI * i / points);
            float yawAngle = RotationUtils.calculate(target).getX() + (float) Math.toDegrees(angle);

            double x = -MathHelper.sin((float) Math.toRadians(yawAngle)) * circleRadius() + target.posX;
            double z = MathHelper.cos((float) Math.toRadians(yawAngle)) * circleRadius() + target.posZ;

            pathTrail.add(new Vector3d(x, target.posY, z));
        }
    }

    private static Color clientColor(int index, int count, int alpha) {
        Pair<Color, Color> colors = ColorProcess.colors;
        Color color = RenderUtils.interpolateColorsBackAndForth(15, (int) (360.0D * index / count), colors.getFirst(), colors.getSecond(), false);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void glColor(Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    private void renderStrafeCircle() {
        if (pathTrail.isEmpty()) return;

        float partial = mc.timer.renderPartialTicks;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial - mc.getRenderManager().viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial - mc.getRenderManager().viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial - mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        drawBand(x, y + 0.01D, z, circleRadius(), RotationUtils.calculate(target).getX(), pathTrail.size());
        GlStateManager.popMatrix();
    }

    private void renderTargetIndicator() {
        if (target == null) {
            return;
        }

        float partialTicks = mc.timer.renderPartialTicks;

        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks
                - mc.getRenderManager().viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks
                - mc.getRenderManager().viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks
                - mc.getRenderManager().viewerPosZ;

        double time = System.currentTimeMillis() / 1000.0D;
        double bob = Math.sin(time * 3.0D) * 0.35D;

        GlStateManager.pushMatrix();
        drawBand(x, y + target.height + 0.65D + bob, z, 0.55D, 0.0F, 72);
        GlStateManager.popMatrix();
    }

    private void drawBand(double x, double y, double z, double radius, float startYaw, int points) {
        double half = lineThickness.getValue() * 0.02D / 2.0D;
        double inner = Math.max(0.0D, radius - half), outer = radius + half;
        int alpha = lineOpacity.getValue().intValue();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= points; i++) {
            int index = i % points;
            double angle = Math.toRadians(startYaw + 360.0D * index / points);
            double dx = -Math.sin(angle), dz = Math.cos(angle);
            Color color = clientColor(index, points, alpha);
            wr.pos(x + dx * inner, y, z + dz * inner).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
            wr.pos(x + dx * outer, y, z + dz * outer).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).endVertex();
        }
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Override
    public void onEnable() {
        exhibitionPath.reset();
    }

    @Override
    public void onDisable() {
        active = false;
        target = null;
        pathTrail.clear();
        directionSwitchTicks = 0;
    }
}
