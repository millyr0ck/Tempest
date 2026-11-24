package rip.tempest.modules.impl.combat.velocity;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.processes.LagProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import static rip.tempest.utils.Util.mc;

public final class HardVelocity extends SubMode {
    private NumberProperty resetChance, targetRange, maxDelay, delayChance;
    private Property<Boolean> requireMouseDown, requireAim, delayKnockback, onlyInAir, onlyLooking, onlyLeftMouse;

    private boolean jumpHeld, forwardHeld;
    private long forwardUntil;

    private volatile boolean resetPending;
    private volatile long resetDue;
    private volatile double resetSpeed;

    private volatile boolean delaying, delayApplied;
    private volatile long delayUntil;
    private volatile int holdMillis;

    private volatile boolean targetNear, delayReady;

    private boolean ignoreFall;
    private long ignoreFallUntil;
    private double maxFall, lastFall;

    public HardVelocity(NumberProperty resetChance, NumberProperty targetRange, Property<Boolean> requireMouseDown, Property<Boolean> requireAim,
                        Property<Boolean> delayKnockback, NumberProperty maxDelay, NumberProperty delayChance, Property<Boolean> onlyInAir,
                        Property<Boolean> onlyLooking, Property<Boolean> onlyLeftMouse) {
        this.resetChance = resetChance;
        this.targetRange = targetRange;
        this.requireMouseDown = requireMouseDown;
        this.requireAim = requireAim;
        this.delayKnockback = delayKnockback;
        this.maxDelay = maxDelay;
        this.delayChance = delayChance;
        this.onlyInAir = onlyInAir;
        this.onlyLooking = onlyLooking;
        this.onlyLeftMouse = onlyLeftMouse;
    }

    @Override
    protected void onEnable() {
        clearReset();
        delaying = delayApplied = false;
        ignoreFall = false;
        maxFall = lastFall = 0;
    }

    @Override
    protected void onDisable() {
        clearReset();
        stopDelay();
        if (inGame()) {
            if (jumpHeld && !GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            if (forwardHeld && !GameSettings.isKeyDown(mc.gameSettings.keyBindForward)) KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        }
        jumpHeld = forwardHeld = false;
        forwardUntil = 0;
    }

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!inGame() || e.isCancelled()) return;

        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            clearReset();
            stopDelay();
            return;
        }

        if (!(e.getPacket() instanceof S12PacketEntityVelocity velocity) || velocity.getEntityID() != mc.thePlayer.getEntityId()) return;
        if (!targetNear || !resetAllowed()) return;

        double x = velocity.getMotionX() / 8000.0, z = velocity.getMotionZ() / 8000.0;
        double speed = Math.sqrt(x * x + z * z);
        if (!delayKnockback.getValue()) {
            schedule(speed, 0);
            return;
        }

        if (!delaying) {
            delayApplied = delayReady && roll(delayChance);
            holdMillis = delayApplied ? maxDelay.getValue().intValue() : 50;
            delaying = true;
            delayUntil = System.currentTimeMillis() + holdMillis + 50;
            LagProcess.spoof(holdMillis, true, true, false, false);
            LagProcess.enabled = true;
        }
        schedule(speed, Math.max(0, holdMillis - 50));
    };

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = e -> {
        if (!inGame()) return;
        if (mc.thePlayer.isDead) {
            clearReset();
            stopDelay();
            return;
        }

        targetNear = target() != null;
        delayReady = targetNear && delayConditionsMet();

        if (delaying) {
            if (!targetNear) {
                clearReset();
                stopDelay();
            } else if (delayApplied && !delayReady) {
                clearReset();
                stopDelay();
            } else if (System.currentTimeMillis() >= delayUntil) {
                stopDelay();
            } else {
                LagProcess.spoof(holdMillis, true, true, false, false);
            }
        }

        if (resetPending && System.currentTimeMillis() >= resetDue) {
            if (targetNear && !mc.thePlayer.isBurning() && !badEffects()) {
                if (!GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                    jumpHeld = true;
                }
                holdForward(resetSpeed);
            }
            clearReset();
        }

        trackFalls();
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (e.isPre() || !inGame()) return;
        if (jumpHeld) {
            if (!GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            jumpHeld = false;
        }
        if (forwardHeld && System.currentTimeMillis() >= forwardUntil) {
            if (!GameSettings.isKeyDown(mc.gameSettings.keyBindForward)) KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            forwardHeld = false;
        }
    };

    private void schedule(double speed, long delay) {
        resetSpeed = speed;
        resetDue = System.currentTimeMillis() + delay;
        resetPending = true;
    }

    private void clearReset() {
        resetPending = false;
        resetDue = 0;
        resetSpeed = 0;
    }

    private void stopDelay() {
        if (delaying) {
            LagProcess.dispatch();
            LagProcess.disable();
        }
        delaying = delayApplied = false;
    }

    private void holdForward(double speed) {
        long hold = speed <= 0.45 ? 180 : speed >= 0.75 ? 360 : 180 + Math.round(180 * (speed - 0.45) / 0.3);
        forwardUntil = Math.max(forwardUntil, System.currentTimeMillis() + hold);
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindForward)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            forwardHeld = true;
        }
    }

    private boolean resetAllowed() {
        if (fallIgnored(mc.thePlayer.onGround) || mc.thePlayer.isBurning() || badEffects()) return false;
        if (requireAim.getValue() && !aimingAtPlayer()) return false;
        if (requireMouseDown.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return false;
        return roll(resetChance);
    }

    private boolean delayConditionsMet() {
        if (onlyInAir.getValue() && mc.thePlayer.onGround) return false;
        if (onlyLooking.getValue() && !lookingAtPlayer(targetRange.getValue())) return false;
        return !onlyLeftMouse.getValue() || GameSettings.isKeyDown(mc.gameSettings.keyBindAttack);
    }

    private static boolean roll(NumberProperty chance) {
        return chance.getValue() >= 100 || Math.random() * 100 < chance.getValue();
    }

    private static boolean badEffects() {
        return mc.thePlayer.isPotionActive(Potion.jump) || mc.thePlayer.isPotionActive(Potion.poison) || mc.thePlayer.isPotionActive(Potion.wither);
    }

    private static boolean aimingAtPlayer() {
        MovingObjectPosition over = mc.objectMouseOver;
        return over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && over.entityHit instanceof EntityPlayer;
    }

    private EntityPlayer target() {
        double range = targetRange.getValue();
        EntityPlayer nearest = null;
        double best = range * range;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.getHealth() <= 0 || AntiBotModule.botList.contains(player)) continue;
            double distance = mc.thePlayer.getDistanceSqToEntity(player);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static boolean lookingAtPlayer(double range) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition wall = mc.theWorld.rayTraceBlocks(eyes, end, false, true, false);
        double limit = wall != null ? eyes.distanceTo(wall.hitVec) : range;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || AntiBotModule.botList.contains(player)) continue;
            float border = player.getCollisionBorderSize();
            AxisAlignedBB box = player.getEntityBoundingBox().expand(border, border, border);
            if (box.isVecInside(eyes)) return true;
            MovingObjectPosition hit = box.calculateIntercept(eyes, end);
            if (hit != null && eyes.distanceTo(hit.hitVec) <= limit) return true;
        }
        return false;
    }

    private void trackFalls() {
        if (mc.thePlayer.capabilities.allowFlying) {
            clearFalls();
        } else {
            boolean onGround = mc.thePlayer.onGround;
            maxFall = Math.max(maxFall, mc.thePlayer.fallDistance);
            if (onGround && Math.max(lastFall, maxFall) > 3.0) {
                ignoreFall = true;
                ignoreFallUntil = System.currentTimeMillis() + 750;
                maxFall = 0;
            }
            if (ignoreFall && System.currentTimeMillis() > ignoreFallUntil) clearFalls();
            if (onGround && !ignoreFall) maxFall = 0;
        }
        lastFall = mc.thePlayer.fallDistance;
    }

    private boolean fallIgnored(boolean onGround) {
        if (ignoreFall && System.currentTimeMillis() <= ignoreFallUntil) return true;
        return onGround && !mc.thePlayer.capabilities.allowFlying && (lastFall > 3.0 || maxFall > 3.0);
    }

    private void clearFalls() {
        ignoreFall = false;
        ignoreFallUntil = 0;
        maxFall = 0;
    }
}
