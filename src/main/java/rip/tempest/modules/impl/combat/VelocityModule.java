package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.combat.velocity.AstralisGrimVelocity;
import rip.tempest.modules.impl.combat.velocity.HardVelocity;
import rip.tempest.modules.impl.combat.velocity.PacketVelocity;
import rip.tempest.modules.impl.combat.velocity.RiseGrimVelocity;
import rip.tempest.processes.BadPacketsProcess;
import rip.tempest.processes.LagProcess;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Velocity", category = ModuleCategory.COMBAT)
public final class VelocityModule extends Module {
    public static final ModeProperty<Mode> modeProperty = new ModeProperty<>("Mode", Mode.Edit);

    private ModeProperty<LegitMode> legitMode = new ModeProperty<>("Legit Mode", LegitMode.Tick, () -> modeProperty.getValue() == Mode.Legit);
    private Property<Boolean> onlyCombat = new Property<>("Enable only during combat", true, () -> modeProperty.getValue() == Mode.Legit);
    private NumberProperty chance = new NumberProperty("Chance", 100, () -> modeProperty.getValue() == Mode.Legit, 0, 100, 1);
    private NumberProperty tickTicks = new NumberProperty("Ticks", 0, () -> modeProperty.getValue() == Mode.Legit && legitMode.getValue() == LegitMode.Tick, 0, 20, 1);
    private NumberProperty hitHits = new NumberProperty("Hits", 0, () -> modeProperty.getValue() == Mode.Legit && legitMode.getValue() == LegitMode.Hit, 0, 20, 1);

    public NumberProperty horizontal = new NumberProperty("Horizontal", 100, () -> modeProperty.getValue() == Mode.Edit, 0, 100, 1);
    public NumberProperty vertical = new NumberProperty("Vertical", 100, () -> modeProperty.getValue() == Mode.Edit, 0, 100, 1);

    public NumberProperty reduceX = new NumberProperty("Reduce X", 100, () -> modeProperty.getValue() == Mode.Reduce, 0, 100, 1);
    public NumberProperty reduceZ = new NumberProperty("Reduce Z", 100, () -> modeProperty.getValue() == Mode.Reduce, 0, 100, 1);

    private final NumberProperty delay = new NumberProperty("Delay", 10, () -> modeProperty.getValue() == Mode.Delay, 1, 50, 1);
    private final Property<Boolean> legit = new Property<>("Legit Lag", true, () -> modeProperty.getValue() == Mode.Delay);

    private final Property<Boolean> smartCombat = new Property<>("Smart Combat", false, () -> modeProperty.getValue() == Mode.RiseGrim);
    private final Property<Boolean> lookDown = new Property<>("Look Down", true, () -> modeProperty.getValue() == Mode.RiseGrim);

    private final NumberProperty grimValue = new NumberProperty("Grim Value", 3, () -> modeProperty.getValue() == Mode.AstralisGrim, 1, 10, 1);

    private final NumberProperty resetChance = new NumberProperty("Reset Chance", 100, this::hard, 0, 100, 1);
    private final NumberProperty targetRange = new NumberProperty("Target Range", 5.3, this::hard, 3, 12, 0.1);
    private final Property<Boolean> requireMouseDown = new Property<>("Require Mouse Down", false, this::hard);
    private final Property<Boolean> requireAim = new Property<>("Require Aim", false, this::hard);
    private final Property<Boolean> delayKnockback = new Property<>("Delay Knockback", true, this::hard);
    private final NumberProperty maxDelay = new NumberProperty("Max Delay", 200, () -> hard() && delayKnockback.getValue(), 50, 1000, 10);
    private final NumberProperty delayChance = new NumberProperty("Delay Chance", 100, () -> hard() && delayKnockback.getValue(), 0, 100, 1);
    private final Property<Boolean> onlyInAir = new Property<>("Delay Only In Air", true, () -> hard() && delayKnockback.getValue());
    private final Property<Boolean> onlyLooking = new Property<>("Delay Only Looking At Player", true, () -> hard() && delayKnockback.getValue());
    private final Property<Boolean> onlyLeftMouse = new Property<>("Delay Only With Left Mouse", true, () -> hard() && delayKnockback.getValue());

    private final NumberProperty packetHorizontal = new NumberProperty("Packet Horizontal", 0, () -> modeProperty.getValue() == Mode.Packet, -200, 200, 1);
    private final NumberProperty packetVertical = new NumberProperty("Packet Vertical", 0, () -> modeProperty.getValue() == Mode.Packet, -200, 200, 1);
    private final Property<Boolean> packetAlert = new Property<>("KB Check Alert", true, () -> modeProperty.getValue() == Mode.Packet);
    private final Property<Boolean> packetNearbyOnly = new Property<>("Nearby Only", false, () -> modeProperty.getValue() == Mode.Packet);

    private final SubModes<Mode> subModes = new SubModes<>(this, modeProperty, Mode.class)
            .add(Mode.Packet, new PacketVelocity(packetHorizontal, packetVertical, packetAlert, packetNearbyOnly))
            .add(Mode.RiseGrim, new RiseGrimVelocity(smartCombat, lookDown))
            .add(Mode.AstralisGrim, new AstralisGrimVelocity(grimValue))
            .add(Mode.Hard, new HardVelocity(resetChance, targetRange, requireMouseDown, requireAim, delayKnockback, maxDelay, delayChance,
                    onlyInAir, onlyLooking, onlyLeftMouse));

    private boolean hard() {
        return modeProperty.getValue() == Mode.Hard;
    }

    public enum Mode {
        Legit,
        Edit,
        Grim,
        Cancel,
        Reverse,
        Reduce,
        Delay,

        RiseGrim("Grim2"),
        AstralisGrim("Grim1"),

        Hard,

        Packet;

        private final String display;

        Mode() {
            this.display = null;
        }

        Mode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display != null ? display : name();
        }
    }

    public enum LegitMode {
        Tick, Hit
    }

    boolean delayed = false;
    private boolean velocity;
    private int limit = 0;
    private boolean reset = false;

    @Override
    public void onEnable() {
        stop();
        subModes.enable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        stop();
        super.onDisable();
    }

    @EventLink
    public final Listener<PacketReceiveEvent> packetReceiveEventListener = event -> {
        if (modeProperty.getValue() == Mode.Edit) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
                if (p.getEntityID() == mc.thePlayer.getEntityId()) {
                    p.setMotionX((int) (p.getMotionX() * horizontal.getValue() / 100.0));
                    p.setMotionZ((int) (p.getMotionZ() * horizontal.getValue() / 100.0));
                    p.setMotionY((int) (p.getMotionY() * vertical.getValue() / 100.0));
                }
            }
        }

        if (modeProperty.getValue() == Mode.Reverse) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
                if (p.getEntityID() == mc.thePlayer.getEntityId()) {
                    p.setMotionX(p.getMotionX() * -1);
                    p.setMotionZ(p.getMotionZ() * -1);
                    p.setMotionY(p.getMotionY() * -1);
                }
            }
        }

        if (modeProperty.getValue() == Mode.Cancel) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
                if (p.getEntityID() == mc.thePlayer.getEntityId()) {
                    event.setCancelled();
                }
            }
        }

        if (modeProperty.getValue() == Mode.Legit) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
                if (p.getEntityID() == mc.thePlayer.getEntityId()) {
                    if (legitMode.getValue() == LegitMode.Tick || legitMode.getValue() == LegitMode.Hit) {
                        double direction = Math.atan2(p.getMotionX(), p.getMotionZ());
                        double degreePlayer = MovementUtils.direction();
                        double degreePacket = Math.floorMod((int) Math.toDegrees(direction), 360);
                        double angle = Math.abs(degreePacket + degreePlayer);
                        double threshold = 120.0;
                        angle = Math.floorMod((int) angle, 360);
                        boolean inRange = angle >= 180 - threshold / 2 && angle <= 180 + threshold / 2;
                        if (inRange) {
                            reset = true;
                        }
                    }
                }
            }
        }
    };

    @EventLink
    private final Listener<MotionEvent> motionEventListener = event -> {
        setSuffix(modeProperty.getValue() == Mode.Packet
                ? packetVertical.getValue().intValue() + "% " + packetHorizontal.getValue().intValue() + "%"
                : modeProperty.getValue().toString());

        if (modeProperty.getValue() == Mode.Delay) {
            if (mc.thePlayer.hurtTime != 0 && !mc.thePlayer.isBurning()) {
                LagProcess.spoof(delay.getValue().intValue() * 10, legit.getValue(), true, legit.getValue(), false);
                delayed = true;
            }
        }

        if (modeProperty.getValue() == Mode.Delay && delayed && !event.isPre()) {
            if (mc.thePlayer.hurtTime == 0 || mc.thePlayer.isBurning()) {
                LagProcess.dispatch();
                LagProcess.disable();
                delayed = false;
            }
        }
    };

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (modeProperty.getValue() == Mode.Grim) {
            if (velocity && !BadPacketsProcess.bad()) {
                PacketUtils.sendSilentPacket(new C07PacketPlayerDigging((mc.objectMouseOver != null && mc.thePlayer.isSwingInProgress && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? C07PacketPlayerDigging.Action.START_DESTROY_BLOCK : C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK),
                        new BlockPos(mc.thePlayer), EnumFacing.UP));
                velocity = false;
            }
        }
    };
    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PacketReceiveEvent> onReceiveLow = event -> {
        if (modeProperty.getValue() != Mode.Grim) return;
        if (event.getPacket() instanceof S12PacketEntityVelocity wrapper) {
            if (wrapper.getEntityID() == mc.thePlayer.getEntityId()) {
                event.setCancelled(true);
                velocity = true;
            }
        }
    };

    @EventLink
    private final Listener<AttackEvent> attackEventListener = event -> {
        if (modeProperty.getValue() == Mode.Reduce) {
            if (event.target instanceof EntityLivingBase && mc.thePlayer.hurtTime > 0) {
                mc.thePlayer.motionX *= reduceX.getValue() / 100.0;
                mc.thePlayer.motionZ *= reduceZ.getValue() / 100.0;
            }
        }
    };

    @EventLink
    private final Listener<StrafeEvent> strafeEventListener = event -> {
        if (modeProperty.getValue() != Mode.Legit) return;
        if (checkLiquids() || !applyChance())
            return;

        if (legitMode.getValue() == LegitMode.Tick || legitMode.getValue() == LegitMode.Hit && reset) {
            if (!mc.gameSettings.keyBindJump.isKeyDown() && shouldJump() && mc.thePlayer.isSprinting()
                    && mc.thePlayer.hurtTime == 9
                    || (!onlyCombat.getValue() && mc.gameSettings.keyBindAttack.isKeyDown())
                    || mc.thePlayer.onGround) {
                mc.gameSettings.keyBindJump.setPressed(true);
                limit = 0;
            }
            reset = false;
            return;
        }

        switch (legitMode.getValue()) {
            case Tick: {
                limit++;
            }
            break;

            case Hit: {
                if (mc.thePlayer.hurtTime == 9) {
                    limit++;
                }
            }
            break;
        }
    };

    private boolean shouldJump() {
        if (modeProperty.getValue() == Mode.Legit) {
            return switch (legitMode.getValue()) {
                case Tick -> {
                    double random = MathUtils.getRandom(tickTicks.getValue(), tickTicks.getValue() + 0.1);
                    yield limit >= random;
                }
                case Hit -> {
                    double random = MathUtils.getRandom(hitHits.getValue(), hitHits.getValue() + 0.1);
                    yield limit >= random;
                }
            };
        }
        return false;
    }

    private boolean checkLiquids() {
        if (modeProperty.getValue() == Mode.Legit) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                return false;
            }
            return Stream.<Supplier<Boolean>>of(mc.thePlayer::isInLava, mc.thePlayer::isBurning, mc.thePlayer::isInWater,
                    () -> mc.thePlayer.isInWeb).map(Supplier::get).anyMatch(Boolean.TRUE::equals);
        }
        return false;
    }

    private void stop() {
        if (modeProperty.getValue() == Mode.Legit) {
            limit = 0;
            reset = false;
        }
    }

    private boolean applyChance() {
        if (modeProperty.getValue() == Mode.Legit) {
            Supplier<Boolean> chanceCheck = () -> {
                return chance.getValue() != 100.0D && Math.random() >= chance.getValue() / 100.0D;
            };

            return Stream.of(chanceCheck).map(Supplier::get).anyMatch(Boolean.TRUE::equals);
        }
        return false;
    }
}
