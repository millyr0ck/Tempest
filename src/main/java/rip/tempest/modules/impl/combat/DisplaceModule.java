package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.ClickEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.player.ClutchModule;
import rip.tempest.processes.BlinkProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.misc.MovementFix;
import de.florianmichael.viamcp.fixes.AttackOrder;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Displace", category = ModuleCategory.COMBAT)
public final class DisplaceModule extends Module {
    private static final ModeProperty<Method> method = new ModeProperty<>("Method", Method.JADE);
    private static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Left);

    private static final NumberProperty angle = new NumberProperty("Angle", 100, () -> method.getValue() == Method.B4, 10, 180, 5);

    private static final NumberProperty cooldown = new NumberProperty("Cooldown", 0.5, () -> method.getValue() != Method.JADE_BLATANT, 0.1, 8, 0.05);

    private static final NumberProperty blatantDelay = new NumberProperty("Blatant Delay", 0, () -> method.getValue() == Method.JADE_BLATANT, 0, 500, 50);

    private static final Property<Boolean> onlyOnTap = new Property<>("Only On Tap", false, () -> method.getValue() == Method.JADE_BLATANT);

    private static final NumberProperty maxHurtTime = new NumberProperty("Max Hurt Time", 3, 0, 8, 1);

    private static final Property<Boolean> hideRotations = new Property<>("Hide Rotations", false);

    private static final Property<Boolean> moveFix = new Property<>("Move Fix", true);
    private static final Property<Boolean> onlyKnockbackItems = new Property<>("Only Knockback Items", true);

    private static final Property<Boolean> overrideAttacks = new Property<>("Override Attacks", true);
    private static final Property<Boolean> playersOnly = new Property<>("Players Only", false);
    private static final Property<Boolean> stopWhenBreaking = new Property<>("Stop When Breaking", false);
    private static final Property<Boolean> renderArrow = new Property<>("Render Arrow", true);

    private static final Property<Boolean> manual = new Property<>("Manual", true);
    private static final Property<Boolean> autoClicker = new Property<>("AutoClicker", true);
    private static final Property<Boolean> killAura = new Property<>("KillAura", true);

    private static final double SNAP_SPEED = 50.0D;
    private static final int LEFT = 0, RIGHT = 1, NEITHER = -1;

    private static final int[] DROP_DISTANCES = {1, 2, 3, 4, 6, 8};
    private static final double DROP_DEPTH = 6.0D;

    private static final double VOID_DEPTH = 20.0D;
    private static final long ARROW_MILLIS = 600L;
    private static final Random RANDOM = new Random();

    private EntityLivingBase queuedTarget;

    private EntityLivingBase strikeTarget;
    private double strikeReach;

    private int holdTicks;
    private long lastDisplace;

    private int side = NEITHER, lastSide = NEITHER;
    private boolean ownsBlink;

    private int ticks, turnTick = -1, lastClickTick = -100;
    private boolean turnedLeft, strafeNext;

    private float voidYaw = Float.NaN;
    private int scanTarget = -1, scanTick = -100;
    private double scanX, scanY, scanZ;
    private boolean scanFalling;
    private float scanYaw;
    private int scanSide;

    private final Map<Integer, Integer> blatantHits = new HashMap<>();

    private EntityLivingBase arrowTarget;
    private float arrowYaw;
    private long arrowStart;

    private static DisplaceModule instance() {
        return Tempest.INSTANCE.getModuleManager().getModule(DisplaceModule.class);
    }

    public static boolean isTurning() {
        DisplaceModule displace = instance();
        return displace.isEnabled() && displace.strikeTarget != null;
    }

    public static boolean holdsAttack(EntityLivingBase target) {
        DisplaceModule displace = instance();
        if (!displace.isEnabled() || !killAura.getValue()) return false;
        if (displace.holdTicks > 0) return true;
        return overrideAttacks.getValue() && displace.canDisplace(target, false, false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reset();
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = e -> reset();

    private void reset() {
        releaseBlink();
        queuedTarget = strikeTarget = arrowTarget = null;
        holdTicks = 0;
        side = lastSide = NEITHER;
        turnTick = -1;
        strafeNext = false;
        scanTarget = -1;
        blatantHits.clear();
    }

    @EventLink(Priorities.HIGH)
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        setSuffix(method.getValue() + " " + mode.getValue());
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        ticks++;

        if (holdTicks > 0) holdTicks--;

        if (strikeTarget != null) {
            EntityLivingBase target = strikeTarget;
            strikeTarget = null;
            if (target.isEntityAlive() && mc.thePlayer.getDistanceToEntity(target) <= strikeReach) strike(target);
            holdTicks = 3;
            releaseBlink();
            return;
        }

        EntityLivingBase target = queuedTarget;
        queuedTarget = null;
        boolean fromAura = false;
        if (target == null && killAura.getValue()) {
            target = auraTarget();
            fromAura = target != null;
        }

        if (target == null && method.getValue() == Method.JADE_BLATANT && attackTapped() && !auraActive()) {
            target = crosshairTarget();
        }
        if (target == null || !target.isEntityAlive() || ClutchModule.isClutching()) return;
        if (method.getValue() == Method.JADE_BLATANT) {
            pruneBlatantHits();
            if (!blatantAllowed(target)) return;
        }

        float yaw = mc.thePlayer.rotationYaw, pitch = mc.thePlayer.rotationPitch;
        if (fromAura) {
            Vec3 middle = new Vec3(target.posX, target.posY + target.getEyeHeight() * 0.5D, target.posZ);
            float[] toTarget = RotationUtils.getRotationsTo(mc.thePlayer.getPositionEyes(1.0F), middle);
            yaw = toTarget[0];
            pitch = toTarget[1];
        } else if (method.getValue() != Method.B4 && RotationProcess.lastServerRotations != null) {
            yaw = RotationProcess.lastServerRotations.x;
        }
        yaw = turnedYaw(yaw);

        if (renderArrow.getValue()) {
            arrowTarget = target;
            arrowYaw = yaw;
            arrowStart = System.currentTimeMillis();
        }
        if (hideRotations.getValue() && !BlinkProcess.enabled) {
            BlinkProcess.enable();
            ownsBlink = true;
        }

        RotationProcess.setRotations(new Vector2f(yaw, pitch), SNAP_SPEED, moveFix.getValue() ? MovementFix.TRADITIONAL : MovementFix.OFF);
        strikeTarget = target;

        strikeReach = mc.thePlayer.getDistanceToEntity(target) + 0.5D;
        lastDisplace = System.currentTimeMillis();
        turnTick = ticks;
    };

    @EventLink
    public final Listener<ClickEvent> clickEventListener = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        lastClickTick = ticks;

        if (strikeTarget != null || queuedTarget != null) {
            e.setCancelled();
            return;
        }

        if (!(AutoClickerModule.clicking ? autoClicker.getValue() : manual.getValue())) return;

        if (auraActive()) return;

        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;

        if (canDisplace(over.entityHit, true, true)) {
            e.setCancelled();
            queuedTarget = (EntityLivingBase) over.entityHit;

            lastDisplace = System.currentTimeMillis();
        } else if (overrideAttacks.getValue() && canDisplace(over.entityHit, false, false)) {
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<MoveEvent> moveEventListener = e -> {
        if (method.getValue() == Method.B4 || mc.thePlayer == null) {
            strafeNext = false;
            return;
        }
        if (turnTick == ticks) {
            if (EnchantmentHelper.getKnockbackModifier(mc.thePlayer) == 0 && movementKeyHeld()) {
                e.setForward(1.0F);
                strafeNext = method.getValue() == Method.JADE_BLATANT;
            }
        } else if (strafeNext) {
            strafeNext = false;
            e.setStrafe(turnedLeft ? -1.0F : 1.0F);
        }
    };

    private void strike(EntityLivingBase target) {
        AttackEvent event = new AttackEvent(target);
        Tempest.INSTANCE.getEventBus().post(event);
        if (event.isCancelled()) return;

        AttackOrder.sendFixedAttack(mc.thePlayer, target);
    }

    private void releaseBlink() {
        if (ownsBlink) {
            BlinkProcess.disable();
            ownsBlink = false;
        }
    }

    private static boolean auraActive() {
        return AuraModule.target() != null;
    }

    private EntityLivingBase auraTarget() {
        if (!auraActive()) return null;

        EntityLivingBase target = AuraModule.target();
        if (mc.thePlayer.getDistanceToEntity(target) > AuraModule.attackRange()) return null;
        return canDisplace(target, true, true) ? target : null;
    }

    private EntityLivingBase crosshairTarget() {
        if (mc.currentScreen != null || !(AutoClickerModule.clicking ? autoClicker.getValue() : manual.getValue())) return null;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return null;
        return canDisplace(over.entityHit, true, true) ? (EntityLivingBase) over.entityHit : null;
    }

    private boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) return false;
        EntityLivingBase living = (EntityLivingBase) entity;

        if (checkHurtTime && living.hurtTime > maxHurtTime.getValue()) return false;
        if (playersOnly.getValue() && !(entity instanceof EntityPlayer)) return false;
        if (stopWhenBreaking.getValue() && mc.playerController.getIsHittingBlock()) return false;

        int knockback = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
        if (knockback == 0 && (onlyKnockbackItems.getValue() || !mc.thePlayer.isSprinting())) return false;

        if (checkCooldown && method.getValue() != Method.JADE_BLATANT
                && System.currentTimeMillis() - lastDisplace < cooldown.getValue() * 1000.0D - 20.0D) return false;

        if (mode.getValue() == Mode.Void) {
            if (method.getValue() == Method.B4) {
                side = lastSide = dropSide(living, lastSide);
                return side != NEITHER;
            }
            return jadeVoid(living);
        }
        return true;
    }

    private float turnedYaw(float yaw) {
        if (method.getValue() == Method.B4) return yaw + offset();

        float turned;
        if (mode.getValue() == Mode.Void && !Float.isNaN(voidYaw)) {
            turned = voidYaw;
            turnedLeft = side == LEFT;
            voidYaw = Float.NaN;
        } else {
            boolean left = switch (mode.getValue()) {
                case Left -> true;
                case Right -> false;
                case Random -> RANDOM.nextBoolean();
                case Void -> side == LEFT;
            };
            turnedLeft = left;
            turned = yaw + (left ? -90.0F : 90.0F);
        }
        side = NEITHER;

        return method.getValue() == Method.JADE ? turned + (RANDOM.nextFloat() * 20.0F - 10.0F) : turned;
    }

    private float offset() {
        boolean left = switch (mode.getValue()) {
            case Left -> true;
            case Right -> false;
            case Random -> RANDOM.nextBoolean();
            case Void -> side == LEFT;
        };
        side = NEITHER;

        float turn = angle.getValue().floatValue();
        return (left ? -turn : turn) + (RANDOM.nextFloat() - 0.5F) * 10.0F;
    }

    private int dropSide(EntityLivingBase target, int previous) {
        double alongX = target.posX - mc.thePlayer.posX, alongZ = target.posZ - mc.thePlayer.posZ;
        double length = Math.sqrt(alongX * alongX + alongZ * alongZ);
        if (length < 0.001D) return NEITHER;
        alongX /= length;
        alongZ /= length;

        double rightX = -alongZ, rightZ = alongX;

        int left = 0, right = 0, behind = 0;
        for (int distance : DROP_DISTANCES) {
            int weight = distance < 6 ? 2 : 1;
            for (int lane = -1; lane <= 1; lane++) {
                if (isDrop(target.posX + alongX * distance + rightX * lane, target.posY, target.posZ + alongZ * distance + rightZ * lane)) behind += weight;
                if (isDrop(target.posX - rightX * distance + alongX * lane, target.posY, target.posZ - rightZ * distance + alongZ * lane)) left += weight;
                if (isDrop(target.posX + rightX * distance + alongX * lane, target.posY, target.posZ + rightZ * distance + alongZ * lane)) right += weight;
            }
        }

        if (left == 0 && right == 0) return NEITHER;
        if (behind >= left && behind >= right) return NEITHER;
        if (left == right && previous != NEITHER) return previous;
        return left > right ? LEFT : RIGHT;
    }

    private static boolean isDrop(double x, double y, double z) {
        return mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - DROP_DEPTH, z), true) == null;
    }

    private static boolean movementKeyHeld() {
        return mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean attackTapped() {
        boolean tapped = lastClickTick >= ticks - 1;
        return onlyOnTap.getValue() ? tapped : tapped || GameSettings.isKeyDown(mc.gameSettings.keyBindAttack);
    }

    private boolean blatantAllowed(EntityLivingBase target) {
        Integer first = blatantHits.get(target.getEntityId());
        if (first != null && ticks - first < 10) {
            int wait = (int) Math.ceil(blatantDelay.getValue() / 50.0);
            return wait <= 0 || ticks - first >= wait;
        }
        blatantHits.put(target.getEntityId(), ticks);
        return true;
    }

    private void pruneBlatantHits() {
        Iterator<Map.Entry<Integer, Integer>> it = blatantHits.entrySet().iterator();
        while (it.hasNext()) {
            Entity entity = mc.theWorld.getEntityByID(it.next().getKey());
            if (!(entity instanceof EntityLivingBase) || entity.isDead || ((EntityLivingBase) entity).deathTime != 0) it.remove();
        }
    }

    private boolean jadeVoid(EntityLivingBase target) {
        double facing = Math.toRadians(towards(target)) + Math.PI / 2;
        boolean stick = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() == Items.stick
                && EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        if (voidAlong(target, facing, stick ? 4.0 : 2.0) > 0) return false;

        boolean reuse = target.getEntityId() == scanTarget && ticks - scanTick <= 4
                && sq(target.posX - scanX) + sq(target.posY - scanY) + sq(target.posZ - scanZ) <= 0.1225;
        if (!reuse) {
            voidYaw = Float.NaN;
            side = NEITHER;
            scanFalling = fallingAnyway(target);
            if (!scanFalling) findBestVoid(target, facing);
            scanTarget = target.getEntityId();
            scanTick = ticks;
            scanX = target.posX;
            scanY = target.posY;
            scanZ = target.posZ;
            scanYaw = voidYaw;
            scanSide = side;
        } else {
            voidYaw = scanYaw;
            side = scanSide;
        }
        return !scanFalling && !Float.isNaN(voidYaw);
    }

    private static float towards(EntityLivingBase target) {
        return (float) Math.toDegrees(Math.atan2(target.posZ - mc.thePlayer.posZ, target.posX - mc.thePlayer.posX)) - 90.0F;
    }

    private boolean fallingAnyway(EntityLivingBase target) {
        double eye = target.posY + target.getEyeHeight(), x = target.posX, z = target.posZ;
        if (!overVoid(x, eye, z)) return false;
        boolean all = true;
        int best = 0, bestAngle = -1;
        for (int a = 0; a < 360; a += 10) {
            double rad = Math.toRadians(a), c = Math.cos(rad), s = Math.sin(rad);
            int count = 0;
            for (int i = 1; i <= 4; i++) {
                double d = i * 0.5;
                if (overVoid(x + c * d, eye, z + s * d)) count++;
                else all = false;
            }
            if (count > best) {
                best = count;
                bestAngle = a;
            }
        }
        if (best > 0 && bestAngle >= 0) {
            double rad = Math.toRadians(bestAngle), c = Math.cos(rad), s = Math.sin(rad);
            if (mc.theWorld.rayTraceBlocks(new Vec3(x, eye, z), new Vec3(x + c * 2.0, eye, z + s * 2.0)) == null) aimAlong(target, rad);
        }
        return all;
    }

    private void findBestVoid(EntityLivingBase target, double facing) {
        double x = target.posX, z = target.posZ, eye = target.posY + target.getEyeHeight(), chest = target.posY + 1.25;
        int mostReachable = 0, mostReachableAngle = -1, nearestAngle = -1;
        double nearest = Double.MAX_VALUE;
        boolean any = false;
        double[] open = new double[36];

        for (int a = 0; a < 360; a += 10) {
            double rad = Math.toRadians(a), c = Math.cos(rad), s = Math.sin(rad);
            double clear = clearance(x, z, eye, chest, c, s, 12.0);
            open[a / 10] = clear;
            int reachable = 0;
            double first = -1;
            for (int i = 1; i <= 24; i++) {
                double d = i * 0.5;
                if (overVoid(x + c * d, eye, z + s * d)) {
                    if (d <= clear) reachable++;
                    if (first < 0) first = d;
                }
            }
            if (first > 0) {
                any = true;
                if (first < nearest) {
                    nearest = first;
                    nearestAngle = a;
                }
                if (reachable > mostReachable) {
                    mostReachable = reachable;
                    mostReachableAngle = a;
                }
            }
        }
        if (!any) return;

        int chosen;
        if (mostReachable > 0) {
            chosen = mostReachableAngle;
        } else {
            int bestAround = 0, bestAroundAngle = -1;
            for (int a = 0; a < 360; a += 10) {
                double clear = open[a / 10];
                if (clear < 1.0) continue;
                double rad = Math.toRadians(a), c = Math.cos(rad), s = Math.sin(rad);
                double reach = Math.min(clear, 4.0), px = x + c * reach, pz = z + s * reach;
                int around = 0;
                for (int b = 0; b < 360; b += 45) {
                    double rb = Math.toRadians(b), cb = Math.cos(rb), sb = Math.sin(rb);
                    for (int k = 1; k <= 4; k++) {
                        if (overVoid(px + cb * k, eye, pz + sb * k)) around++;
                    }
                }
                if (around > bestAround) {
                    bestAround = around;
                    bestAroundAngle = a;
                }
            }
            chosen = bestAround > 0 ? bestAroundAngle : nearestAngle;
        }

        double rad = Math.toRadians(chosen);
        if (mostReachable > 0) {
            double across = bestAcross(target, facing);
            if (!Double.isNaN(across)) rad = across;
        }
        aimAlong(target, rad);
    }

    private double bestAcross(EntityLivingBase target, double facing) {
        for (int k = 0; k <= 5; k++) {
            double best = Double.NaN;
            int bestCount = 0;
            for (int offset : k == 0 ? new int[]{0} : new int[]{-k, k}) {
                double turn = Math.toRadians(90.0 + offset);
                double one = facing - turn, other = facing + turn;
                int a = voidAlong(target, one, 12.0), b = voidAlong(target, other, 12.0);
                if (a > 0 && b > 0 && Math.max(a, b) > bestCount) {
                    bestCount = Math.max(a, b);
                    best = a >= b ? one : other;
                }
            }
            if (!Double.isNaN(best)) return best;
        }
        return Double.NaN;
    }

    private int voidAlong(EntityLivingBase target, double rad, double limit) {
        double x = target.posX, z = target.posZ, eye = target.posY + target.getEyeHeight(), chest = target.posY + 1.25;
        double c = Math.cos(rad), s = Math.sin(rad);
        double clear = clearance(x, z, eye, chest, c, s, Math.min(12.0, limit));
        int count = 0;
        for (int i = 1; i <= 24; i++) {
            double d = i * 0.5;
            if (d > clear || d > limit) break;
            if (overVoid(x + c * d, eye, z + s * d)) count++;
        }
        return count;
    }

    private static double clearance(double x, double z, double eye, double chest, double c, double s, double reach) {
        double clear = reach;
        MovingObjectPosition high = mc.theWorld.rayTraceBlocks(new Vec3(x, eye, z), new Vec3(x + c * reach, eye, z + s * reach));
        if (high != null) clear = Math.hypot(high.hitVec.xCoord - x, high.hitVec.zCoord - z);
        MovingObjectPosition low = mc.theWorld.rayTraceBlocks(new Vec3(x, chest, z), new Vec3(x + c * reach, chest, z + s * reach));
        if (low != null) clear = Math.min(clear, Math.hypot(low.hitVec.xCoord - x, low.hitVec.zCoord - z));
        return clear;
    }

    private void aimAlong(EntityLivingBase target, double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        voidYaw = (float) Math.toDegrees(Math.atan2(-c, s));
        double dx = target.posX - mc.thePlayer.posX, dz = target.posZ - mc.thePlayer.posZ;
        side = dx * s - dz * c > 0 ? LEFT : RIGHT;
    }

    private static boolean overVoid(double x, double y, double z) {
        return mc.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(x, y - VOID_DEPTH, z), true) == null;
    }

    private static double sq(double value) {
        return value * value;
    }

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = e -> {
        if (arrowTarget == null || mc.thePlayer == null) return;

        long elapsed = System.currentTimeMillis() - arrowStart;
        if (elapsed > ARROW_MILLIS || !renderArrow.getValue()) {
            arrowTarget = null;
            return;
        }

        float alpha = smooth(elapsed / 100.0F) * (1.0F - smooth((elapsed - (ARROW_MILLIS - 150L)) / 150.0F));
        float travelled = 1.0F - elapsed / 450.0F;
        float slide = travelled <= 0.0F ? 1.0F : 1.0F - travelled * travelled * travelled;
        drawArrow(arrowYaw, arrowTarget, alpha, slide * 0.6D, mc.timer.renderPartialTicks);
    };

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static final double ARROW_GAP = 0.24D;
    private static final double ARROW_BODY_LENGTH = 0.74D;
    private static final double ARROW_BODY_HALF_HEIGHT = 0.08D;
    private static final double ARROW_HEAD_BACKSET = 0.18D;
    private static final double ARROW_HEAD_LENGTH = 0.52D;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.30D;

    private void drawArrow(float yaw, EntityLivingBase target, float alpha, double slide, float partialTicks) {
        double centerX = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks;
        double centerY = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks + (double) target.height * 0.5D;
        double centerZ = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks;

        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double baseOffset = (double) target.width * 0.5D + ARROW_GAP + slide;
        double tailX = centerX + forwardX * baseOffset;
        double tailZ = centerZ + forwardZ * baseOffset;
        double bodyEndX = tailX + forwardX * ARROW_BODY_LENGTH;
        double bodyEndZ = tailZ + forwardZ * ARROW_BODY_LENGTH;
        double headBackX = tailX + forwardX * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double headBackZ = tailZ + forwardZ * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double tipX = bodyEndX + forwardX * ARROW_HEAD_LENGTH;
        double tipZ = bodyEndZ + forwardZ * ARROW_HEAD_LENGTH;

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.82F * alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.95F * alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void arrowVertex(double x, double y, double z, double verticalOffset, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + verticalOffset - viewerY, z - viewerZ);
    }

    public enum Method {
        JADE("Jade"),
        JADE_BLATANT("Jade Blatant"),
        B4("B4");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Mode {
        Left,
        Right,
        Random,
        Void
    }
}
