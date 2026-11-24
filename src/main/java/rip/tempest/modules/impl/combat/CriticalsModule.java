package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.movement.LongJumpModule;
import rip.tempest.modules.impl.movement.SpeedModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumParticleTypes;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Criticals", category = ModuleCategory.COMBAT)
public final class CriticalsModule extends Module {
    public static ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Edit);

    private final NumberProperty delay = new NumberProperty("Delay", 490, () -> mode.getValue() == Mode.Watchdog, 0, 1000, 10, Representation.MILLISECONDS);

    private enum Mode {
        Vanilla,
        Legit,
        Visual,
        Edit,
        NCP,
        Watchdog,

        Packet2,

        Packet
    }

    private static final double[] PACKET_OFFSETS = {0.0625101, 0.0, 0.0000101, 0.0};

    private static final double[] WATCHDOG_OFFSETS = {0.056f, 0.016f, 0.003f};

    private final Timer watchdogTimer = new Timer();
    private int groundTicks;
    private boolean activateJump;

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        if (e.isPre() && mc.thePlayer != null) {
            groundTicks = MovementUtils.isOnGround() ? groundTicks + 1 : 0;
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> packetSendEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (mode.getValue() == Mode.Edit) {
            if (e.getPacket() instanceof C03PacketPlayer packet) {
                packet.onGround = false;
            }
        }

        if (mode.getValue() == Mode.Watchdog && e.getPacket() instanceof C0APacketAnimation && hasTarget()
                && ServerUtils.isOnHypixel() && groundTicks > 1 && watchdogTimer.hasTimeElapsed(delay.getValue())) {
            for (double offset : WATCHDOG_OFFSETS) {
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                        mc.thePlayer.posX,
                        mc.thePlayer.posY + offset + StrictMath.random() * 0.0003F,
                        mc.thePlayer.posZ,
                        false));
            }
            watchdogTimer.reset();
        }
    };

    @EventLink(value = io.github.nevalackin.homoBus.Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> packet2Listener = e -> {
        if (mode.getValue() != Mode.Packet2 || e.isCancelled() || mc.thePlayer == null) return;
        if (!(e.getPacket() instanceof C02PacketUseEntity use) || use.getAction() != C02PacketUseEntity.Action.ATTACK) return;
        if (!packet2Ready(use.getEntityFromWorld(mc.theWorld))) return;

        e.setCancelled();
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.125F, z, false));
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.046599998474120774, z, false));
        PacketUtils.sendPacketNoEvent(use);
        PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, true));
    };

    private boolean packet2Ready(Entity target) {
        if (!mc.thePlayer.onGround || !mc.thePlayer.isCollidedVertically || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return false;
        if (Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class).isEnabled()
                || Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class).isEnabled()) return false;
        if (Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class).isEnabled() && MovementUtils.isMoving()) return false;
        return !(target instanceof EntityLivingBase living) || living.hurtTime <= 1;
    }

    private boolean hasTarget() {
        return AuraModule.target() != null || mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null;
    }

    @Override
    public void onEnable() {
        watchdogTimer.reset();
        groundTicks = 0;
        super.onEnable();
    }

    @EventLink
    public final Listener<StrafeEvent> strafeEventListener = e -> {
        if (mode.getValue() == Mode.Legit) {
            if (activateJump) {
                mc.thePlayer.jump();
                activateJump = false;
            }
        }
    };

    @EventLink
    public final Listener<AttackEvent> attackEventListener = e -> {
        if (mode.getValue() == Mode.NCP) {
            boolean willCritLegit = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && !mc.thePlayer.isRiding();

            if (willCritLegit) {
                return;
            }

            PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.000000271875, mc.thePlayer.posZ, false));
            PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0., mc.thePlayer.posZ, false));
        }

        if (mode.getValue() == Mode.Vanilla) {
            mc.thePlayer.onCriticalHit(e.target);
        }

        if (mode.getValue() == Mode.Legit) {
            if (mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isRiding() && !mc.gameSettings.keyBindJump.isKeyDown()) {
                activateJump = true;
            }
        }

        if (mode.getValue() == Mode.Visual) {
            mc.effectRenderer.emitParticleAtEntity(e.target, EnumParticleTypes.CRIT);
        }

        if (mode.getValue() == Mode.Packet && packetReady()) {
            double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
            for (double off : PACKET_OFFSETS) {
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y + off, z, off == 0.0));
            }
        }
    };

    private boolean packetReady() {
        if (!mc.thePlayer.onGround || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) return false;
        rip.tempest.modules.ModuleManager mgr = rip.tempest.Tempest.INSTANCE.getModuleManager();
        if (mgr.getModule(rip.tempest.modules.impl.movement.FlightModule.class).isEnabled()) return false;
        if (mgr.getModule(rip.tempest.modules.impl.movement.LongJumpModule.class).isEnabled()) return false;
        return true;
    }

    @Override
    public void onDisable() {
        activateJump = false;
        super.onDisable();
    }
}
