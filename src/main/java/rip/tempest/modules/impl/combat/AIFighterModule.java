package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.RotationProcess;
import rip.tempest.processes.TargetSelectionProcess;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PathFinderUtils;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.misc.MovementFix;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "AI Fighter", category = ModuleCategory.COMBAT)
public class AIFighterModule extends Module {

    public static ModeProperty<TargetSelectionProcess.Mode> mode = new ModeProperty<>("Mode", TargetSelectionProcess.Mode.Adaptive);
    public static ModeProperty<TargetSelectionProcess.Entities> entities = new ModeProperty<>("Entities", TargetSelectionProcess.Entities.Optimal);
    public static ModeProperty<Rotations> rotations = new ModeProperty<>("Rotations", Rotations.Player);
    private final NumberProperty speed = new NumberProperty("Rotation Speed", 5, 0, 10, 1);
    public static final Property<Boolean> fix = new Property<>("Move Fix", true, () -> rotations.getValue() == Rotations.Server);
    public static final Property<Boolean> newCombat = new Property<>("New Combat Delays", false);
    private static final NumberProperty min = new NumberProperty("Min CPS", 9.0, () -> !newCombat.getValue(), 0.0, 20.0, 0.5);
    private static final NumberProperty max = new NumberProperty("Max CPS", 13.0, () -> !newCombat.getValue(), 0.0, 20.0, 0.5);
    private final NumberProperty switchSpeed = new NumberProperty("Switch Speed", 2, () -> mode.getValue() == TargetSelectionProcess.Mode.Switch, 0, 10, 1);
    public static NumberProperty seekRange = new NumberProperty("Seek Range", 20, 3, 100, 0.1);
    public static NumberProperty killRange = new NumberProperty("Kill Range", 3, 3, 6, 0.1);
    private final Property<Boolean> teams = new Property<>("Teams", false);
    private final Property<Boolean> renderPath = new Property<>("Render Path", true);

    public static EntityLivingBase target;
    List<Entity> targetList = new CopyOnWriteArrayList<>();
    private List<Vec3> path;
    static long delay = 0;
    private static final Timer attackTimer = new Timer();
    static int elapsedTicks = 0;

    public enum Rotations {
        Player,
        Server
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        setSuffix(mode.getValue().toString());

        TargetSelectionProcess.setMode(mode.getValue());
        TargetSelectionProcess.setEntities(entities.getValue());
        TargetSelectionProcess.setSeekRange(seekRange.getValue().floatValue());
        TargetSelectionProcess.setDontTargetTeams(teams.getValue());
        TargetSelectionProcess.setSwitchTime(switchSpeed.getValue().intValue());

        targetList = TargetSelectionProcess.getTargetList();
        target = TargetSelectionProcess.getTarget();

        mc.gameSettings.keyBindForward.setPressed(target != null && !(mc.thePlayer.getDistanceToEntity(target) <= killRange.getValue()));
        mc.gameSettings.keyBindJump.setPressed(mc.thePlayer.isCollidedHorizontally || mc.thePlayer.isInWater());

        if (targetList.isEmpty()) {
            target = null;
            return;
        }

        if (target == null) {
            return;
        }

        Vector2f rotation = RotationUtils.calculate(target, mode.getValue() == TargetSelectionProcess.Mode.Adaptive, seekRange.getValue());

        if (rotations.getValue() == Rotations.Player) {
            float yawDiff = MathHelper.wrapAngleTo180_float(rotation.x - mc.thePlayer.rotationYaw);
            float pitchDiff = rotation.y - mc.thePlayer.rotationPitch;

            float maxRotationStep = speed.getValue().intValue() * 18.0f;
            yawDiff = MathHelper.clamp_float(yawDiff, -maxRotationStep, maxRotationStep);
            pitchDiff = MathHelper.clamp_float(pitchDiff, -maxRotationStep, maxRotationStep);

            mc.thePlayer.rotationYaw += yawDiff;
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + pitchDiff, -90.0f, 90.0f);
        } else {
            RotationProcess.setRotations(rotation, speed.getValue().intValue(), fix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
        }

        if (renderPath.getValue() && mc.thePlayer.getDistanceToEntity(target) <= seekRange.getValue()) {
            path = PathFinderUtils.computePath(
                new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                new Vec3(target.posX, target.posY, target.posZ),
                true
            );
        }

        attack();
    };

    @EventLink
    public final Listener<TickEvent> tickEventListener = e -> {
        elapsedTicks++;
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (!renderPath.getValue() || path == null || target == null) {
            return;
        }

        Vec3 lastVector = null;

        for (final Vec3 vector : path) {
            if (lastVector != null) {
                RenderUtils.drawLine(lastVector.xCoord, lastVector.yCoord + 0.01, lastVector.zCoord,
                                   vector.xCoord, vector.yCoord + 0.01, vector.zCoord,
                                   Color.WHITE, 1);
            }
            lastVector = vector;
        }
    };

    private void attack() {
        if (target == null || !hitTimerDone() || mc.thePlayer.getDistanceToEntity(target) > killRange.getValue()) return;

        mc.clickMouse();
    }

    private static boolean hitTimerDone() {
        boolean returnVal = false;
        if (!newCombat.getValue()) {
            if (attackTimer.hasTimeElapsed(delay, false)) {
                returnVal = true;
                attackTimer.reset();
                delay = (long) (1000 / MathUtils.getRandom(max.getValue().floatValue(), Math.min(min.getValue().floatValue(), max.getValue().floatValue() - 1)));
            }
        } else {
            if (elapsedTicks >= getNewCombatDelay()) {
                elapsedTicks = 0;
                returnVal = true;
            }
        }
        return returnVal;
    }

    private static int getNewCombatDelay() {
        int toolDelay = 3;
        if (mc.thePlayer.inventory.getCurrentItem() == null) {
            return toolDelay;
        } else {
            if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemSword) {
                toolDelay = 12;
            }
            if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemTool) {
                toolDelay = 20;
            }
            if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemPickaxe) {
                toolDelay = 16;
            }
            if (mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemAxe) {
                toolDelay = 25;
            }
        }
        return toolDelay;
    }

    @Override
    public void onEnable() {
        delay = (long) (1000 / MathUtils.getRandom(max.getValue().floatValue(), Math.max(min.getValue().floatValue(), max.getValue().floatValue() - 1)));
        super.onEnable();
    }

    @Override
    public void onDisable() {
        target = null;
        targetList.clear();
        super.onDisable();
    }
}
