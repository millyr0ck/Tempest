package rip.tempest.modules.impl.movement.flight;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.potion.Potion;

import java.awt.Color;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

public final class WatchdogFlight extends SubMode {
    public enum BoostMode {
        Normal,
        Damage
    }

    private Property<Boolean> viewBobbing, boost, extraBoost, safeDamage, blink, blinkIndicator, dropFly;
    private ModeProperty<BoostMode> boostMode;
    private NumberProperty blinkLength, speed, timerSpeed, timerLength;

    private final Queue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private final Timer timer = new Timer();

    private volatile boolean blinking;
    private boolean hasDamaged, goodToGo, vanillaFly;
    private int level;
    private double moveSpeed, lastDist, postStage;

    public WatchdogFlight(Property<Boolean> viewBobbing, Property<Boolean> boost, ModeProperty<BoostMode> boostMode,
                          Property<Boolean> extraBoost, Property<Boolean> safeDamage, Property<Boolean> blink,
                          Property<Boolean> blinkIndicator, NumberProperty blinkLength, Property<Boolean> dropFly,
                          NumberProperty speed, NumberProperty timerSpeed, NumberProperty timerLength) {
        this.viewBobbing = viewBobbing;
        this.boost = boost;
        this.boostMode = boostMode;
        this.extraBoost = extraBoost;
        this.safeDamage = safeDamage;
        this.blink = blink;
        this.blinkIndicator = blinkIndicator;
        this.blinkLength = blinkLength;
        this.dropFly = dropFly;
        this.speed = speed;
        this.timerSpeed = timerSpeed;
        this.timerLength = timerLength;
    }

    @Override
    protected void onEnable() {
        reset();
        if (!inGame()) return;

        moveSpeed = MovementUtils.getBaseMoveSpeed(0.2873);
        if (dropFly.getValue() && mc.thePlayer.fallDistance >= 3.0F) {
            NotificationManager.post(NotificationType.INFO, "Flight", "Activated Exploit Fly!", 2f);
        }
    }

    @Override
    protected void onDisable() {
        if (inGame()) {
            mc.timer.timerSpeed = 1.0F;
            flush();
        }
        reset();
    }

    private void reset() {
        held.clear();
        timer.reset();
        blinking = false;
        hasDamaged = false;
        goodToGo = false;
        vanillaFly = false;
        level = 0;
        moveSpeed = 0.0D;
        lastDist = 0.0D;
        postStage = 0.0D;
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> reset();

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!inGame()) return;

        if (!e.isPre()) {
            double xDist = mc.thePlayer.posX - mc.thePlayer.prevPosX;
            double zDist = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
            lastDist = Math.sqrt(xDist * xDist + zDist * zDist);
            return;
        }

        if (viewBobbing.getValue() && MovementUtils.isMoving()) {
            mc.thePlayer.cameraYaw = 0.081F;
        }

        vanillaFly = dropFly.getValue() && mc.thePlayer.fallDistance >= 3.0F;

        if (extraBoost.getValue()) {
            if (!timer.hasTimeElapsed(timerLength.getValue()) && timer.hasTimeElapsed(10) && goodToGo) {
                mc.timer.timerSpeed = timerSpeed.getValue().floatValue();
            } else {
                mc.timer.timerSpeed = 1.0F;
            }
            if (mc.thePlayer.hurtResistantTime == 19 && level < 20) {
                timer.reset();
            }
        }

        if (!boost.getValue()) {
            level++;
        }
        double jump = jumpMotion();

        if (level > 3) {
            if (level % 3 == 0) {
                postStage += 1.0E-13D + MathUtils.getRandom(1.0E-14D, 1.0E-13D);
            }
            e.setPosY(mc.thePlayer.posY + postStage);
        }

        if (ServerUtils.isOnHypixel()) {
            e.setOnGround(true);
        }
        mc.thePlayer.motionY = 0.0D;

        if (boostMode.getValue() == BoostMode.Damage && boost.getValue() && !hasDamaged) {
            PlayerUtils.damagePlayer();
            hasDamaged = true;
            e.setPosY(mc.thePlayer.posY);
        }

        if (!boost.getValue() && MovementUtils.isMoving() && mc.thePlayer.onGround) {
            mc.thePlayer.motionY = jump;
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame()) return;

        if (vanillaFly) {
            MovementUtils.setSpeed(e, speed.getValue() * 1.5D, MovementUtils.hypixelYawJitter());
            return;
        }

        final double base = MovementUtils.getBaseMoveSpeed(0.2873);
        if (!boost.getValue()) {
            MovementUtils.setSpeed(e, base, MovementUtils.hypixelYawJitter());
            return;
        }

        final boolean speedPotion = mc.thePlayer.isPotionActive(Potion.moveSpeed);
        if (MovementUtils.isMoving()) {
            if (level != 1) {
                if (level == 2) {
                    level++;
                    moveSpeed *= speed.getValue() - (speedPotion ? 0.3D : 0.0D);
                } else if (level == 3) {
                    level++;
                    moveSpeed = lastDist - 0.05D;
                } else {
                    level++;
                    moveSpeed = lastDist - lastDist / 159.9D;
                    if (mc.theWorld.getCollidingBoundingBoxes((Entity) mc.thePlayer,
                            mc.thePlayer.getEntityBoundingBox().offset(0.0D, mc.thePlayer.motionY, 0.0D)).size() > 0
                            || mc.thePlayer.isCollidedVertically) {
                        level = 1;
                    }
                }
            } else if (!goodToGo && (!safeDamage.getValue() || mc.thePlayer.hurtResistantTime == 19)) {
                goodToGo = true;
                e.setY(mc.thePlayer.motionY = potionJump() ? 0.8125D : 0.41999998688697815D);
                level = 2;
                moveSpeed = (1.6D - (speedPotion ? 0.11D : 0.0D)) * base;
            }
        } else {
            moveSpeed = 0.0D;
        }

        moveSpeed = Math.max(moveSpeed, base);
        if (!goodToGo && safeDamage.getValue()) {
            moveSpeed = 0.0D;
        }
        MovementUtils.setSpeed(e, moveSpeed, MovementUtils.hypixelYawJitter());
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!inGame()) return;
        if (!blink.getValue()) {
            if (blinking) {
                blinking = false;
                flush();
            }
            return;
        }

        if (mc.thePlayer.ticksExisted % Math.max(1, blinkLength.getValue().intValue()) != 0) {
            if (!blinking) {
                blinking = true;
                held.clear();
            }
        } else {
            blinking = false;
            flush();
        }

        if (blinking) {
            held.add(e.getPacket());
            e.setCancelled();
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = e -> {
        if (!blink.getValue() || !blinkIndicator.getValue() || mc.thePlayer == null) return;

        final int length = Math.max(1, blinkLength.getValue().intValue());
        final float progress = ((mc.thePlayer.ticksExisted % length) + e.getPartialTicks()) / length;

        final ScaledResolution sr = new ScaledResolution(mc);
        final int width = 100;
        final int left = sr.getScaledWidth() / 2 - width / 2;
        final int top = sr.getScaledHeight() / 2 + 40;

        Gui.drawRect(left - 1, top - 1, left + width + 1, top + 5, new Color(0, 0, 0, 120).getRGB());
        Gui.drawRect(left, top, left + Math.round(width * Math.min(progress, 1.0F)), top + 4, ColorProcess.getColor().getRGB());
    };

    private void flush() {
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    private static boolean potionJump() {
        return mc.thePlayer.isPotionActive(Potion.jump);
    }

    private static double jumpMotion() {
        return 0.42F + (potionJump() ? (mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F : 0.0F);
    }
}
