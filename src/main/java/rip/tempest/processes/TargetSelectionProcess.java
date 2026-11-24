package rip.tempest.processes;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.RotationUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.util.vector.Vector2f;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static rip.tempest.utils.Util.mc;

public class TargetSelectionProcess {
    @Getter
    @Setter
    private static EntityLivingBase target;
    @Getter
    private static List<Entity> targetList = new CopyOnWriteArrayList<>();
    private static final Timer switchTimer = new Timer();
    @Getter
    @Setter
    private static Enum mode;
    @Getter
    @Setter
    private static Enum entities;
    @Getter
    @Setter
    private static boolean dontTargetTeams;
    @Getter
    @Setter
    private static float seekRange;
    @Getter
    @Setter
    private static int switchTime;

    private static Priority priority = Priority.Normal;
    private int targetIndex;

    public static Priority getPriority() {
        return priority;
    }

    public static void setPriority(Priority priority) {
        TargetSelectionProcess.priority = priority == null ? Priority.Normal : priority;
    }

    public TargetSelectionProcess() {
        mode = Mode.Adaptive;
        entities = Entities.Optimal;
        dontTargetTeams = false;
        seekRange = 4.2f;
        switchTime = 2;
    }

    public enum Mode {
        Adaptive,
        Switch,
        Single
    }

    public enum Entities {
        Optimal,
        Players,
        All
    }

    public enum Priority {
        Normal,
        Crosshair,
        Health
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        targetList = getTargets(seekRange);

        if (targetList.isEmpty()) {
            target = null;
            return;
        }

        selectTarget();
    };

    @EventLink
    public final Listener<AttackEvent> attackEventListener = event -> event.target = target;

    private void selectTarget() {
        if (targetList.isEmpty()) {
            target = null;
            return;
        }

        switch (mode) {
            case Mode.Single:
                target = (EntityLivingBase) targetList.getFirst();
                break;

            case Mode.Switch:
                if (targetIndex >= targetList.size()) {
                    targetIndex = 0;
                }

                if (switchTimer.hasTimeElapsed(switchTime * 100)) {
                    targetIndex = (targetIndex + 1) % targetList.size();
                    switchTimer.reset();
                }
                target = (EntityLivingBase) targetList.get(targetIndex);
                break;

            case Mode.Adaptive:

                target = priority != Priority.Normal || rip.tempest.modules.impl.visuals.PitModule.huntsBounties() ? (EntityLivingBase) targetList.getFirst()
                        : (EntityLivingBase) targetList.stream()
                        .min(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)))
                        .orElse(null);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.entities);
        }
    }

    public static List<Entity> targetsWithin(float range) {
        List<Entity> targets = getTargets(range);
        if (priority == Priority.Normal && !rip.tempest.modules.impl.visuals.PitModule.huntsBounties()) {
            targets.sort(Comparator.comparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)));
        }
        return targets;
    }

    private static List<Entity> getTargets(float range) {
        List<Entity> targets = findTargets(range);

        targets.removeIf(rip.tempest.modules.impl.visuals.PitModule::belowHuntedBounty);
        if (rip.tempest.modules.impl.visuals.PitModule.huntsBounties() && targets.size() > 1) {
            targets.sort(Comparator.comparingInt((Entity e) -> -rip.tempest.modules.impl.visuals.PitModule.bountyOf(e))
                    .thenComparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)));
            return targets;
        }
        if (priority != Priority.Normal && targets.size() > 1) {
            Comparator<Entity> order = priority == Priority.Crosshair
                    ? Comparator.comparingDouble(TargetSelectionProcess::crosshairAngle)
                    : Comparator.comparingDouble(e -> ((EntityLivingBase) e).getHealth() + ((EntityLivingBase) e).getAbsorptionAmount());

            targets.sort(order.thenComparingDouble(e -> mc.thePlayer.getDistanceToEntity(e)));
        }
        return targets;
    }

    private static double crosshairAngle(Entity entity) {
        Vector2f to = RotationUtils.calculate(entity);
        double yaw = MathHelper.wrapAngleTo180_float(to.x - mc.thePlayer.rotationYaw);
        double pitch = to.y - mc.thePlayer.rotationPitch;
        return Math.hypot(yaw, pitch);
    }

    private static List<Entity> findTargets(float range) {
        return mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityLivingBase)
                .filter(entity -> entity != mc.thePlayer)
                .filter(entity -> !entity.isDead)
                .filter(entity -> ((EntityLivingBase) entity).getHealth() > 0)
                .filter(entity -> mc.thePlayer.getDistanceToEntity(entity) <= range)
                .filter(entity -> !AntiBotModule.botList.contains(entity))
                .filter(entity -> !AntiBotModule.isHologram(entity))
                .filter(entity -> !rip.tempest.modules.impl.client.MCFModule.isExcluded(entity))
                .filter(TargetSelectionProcess::isValidEntity)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    private static boolean isValidEntity(Entity entity) {
        if (dontTargetTeams && inTeam(mc.thePlayer, entity)) return false;

        return switch (entities) {
            case Entities.Optimal -> entity instanceof EntityPlayer || entity instanceof EntityMob;
            case Entities.Players -> entity instanceof EntityPlayer;
            case Entities.All -> true;
            default -> throw new IllegalStateException("Unexpected value: " + entities);
        };
    }

    private static boolean inTeam(@NonNull ICommandSender entity0, @NonNull ICommandSender entity1) {
        String s = "\u00a7" + teamColor(entity0);

        return entity0.getDisplayName().getFormattedText().contains(s)
                && entity1.getDisplayName().getFormattedText().contains(s);
    }

    private static @NonNull String teamColor(@NonNull ICommandSender player) {
        Matcher matcher = Pattern.compile("\u00a7(.).*\u00a7r").matcher(player.getDisplayName().getFormattedText());
        return matcher.find() ? matcher.group(1) : "f";
    }
}
