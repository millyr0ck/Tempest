package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.BlockCollideEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.movement.flight.DamageFly;
import rip.tempest.modules.impl.movement.flight.FireballFly;
import rip.tempest.modules.impl.movement.flight.OldNcpFlight;
import rip.tempest.modules.impl.movement.flight.PearlExploitFlight;
import rip.tempest.modules.impl.movement.flight.WatchdogFlight;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockAir;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Flight", category = ModuleCategory.MOVEMENT)
public final class FlightModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Motion);
    private final NumberProperty motionSpeed = new NumberProperty("Motion Speed", 0.9, () -> mode.getValue() == Mode.Motion, 0.1, 2.0, 0.1);
    private final NumberProperty teleportDelay = new NumberProperty("Teleport Delay", 5, () -> mode.getValue() == Mode.Packet, 1, 20, 1);
    private final NumberProperty teleportLength = new NumberProperty("Teleport Length", 5, () -> mode.getValue() == Mode.Packet, 1, 20, 1);
    private final NumberProperty timerAmount = new NumberProperty("Timer Amount", 1, () -> !ownClass(), 0.1, 3, 0.1);
    private final NumberProperty vanillaSpeed = new NumberProperty("Vanilla Speed", 1.0, () -> mode.getValue() == Mode.Vanilla, 0.1, 5.0, 0.1);
    private final Property<Boolean> fullStop = new Property<>("Stop on Disable", true);

    private final Property<Boolean> viewBobbing = new Property<>("View Bobbing", true, this::ownClass);
    private final Property<Boolean> boost = new Property<>("Boost", true, () -> mode.getValue() == Mode.Watchdog);
    private final ModeProperty<WatchdogFlight.BoostMode> boostMode = new ModeProperty<>("Boost Mode", WatchdogFlight.BoostMode.Damage,
            () -> mode.getValue() == Mode.Watchdog && boost.getValue());
    private final Property<Boolean> safeDamage = new Property<>("Safe Damage", true,
            () -> mode.getValue() == Mode.Watchdog && boost.getValue());
    private final Property<Boolean> extraBoost = new Property<>("Extra Boost", true,
            () -> mode.getValue() == Mode.Watchdog && boost.getValue());
    private final NumberProperty timerSpeed = new NumberProperty("Timer Speed", 2.5,
            () -> mode.getValue() == Mode.Watchdog && boost.getValue() && extraBoost.getValue(), 1.0, 4.0, 0.1);
    private final NumberProperty timerLength = new NumberProperty("Timer Length", 500,
            () -> mode.getValue() == Mode.Watchdog && boost.getValue() && extraBoost.getValue(), 100, 1500, 50, Representation.MILLISECONDS);
    private final NumberProperty watchdogSpeed = new NumberProperty("Watchdog Speed", 2.0, () -> mode.getValue() == Mode.Watchdog, 0.2, 10.0, 0.1);
    private final Property<Boolean> dropFly = new Property<>("Drop Fly", true, () -> mode.getValue() == Mode.Watchdog);
    private final Property<Boolean> blink = new Property<>("Blink", true, () -> mode.getValue() == Mode.Watchdog);
    private final Property<Boolean> blinkIndicator = new Property<>("Blink Indicator", true,
            () -> mode.getValue() == Mode.Watchdog && blink.getValue());
    private final NumberProperty blinkLength = new NumberProperty("Blink Length", 45,
            () -> mode.getValue() == Mode.Watchdog && blink.getValue(), 1, 300, 1, Representation.INT);

    private final NumberProperty pearlSpeed = new NumberProperty("Pearl Speed", 2.5, () -> mode.getValue() == Mode.PearlExploit, 0.1, 5.0, 0.05);
    private final Property<Boolean> toggleAura = new Property<>("Toggle Aura", true, () -> mode.getValue() == Mode.PearlExploit);

    private final NumberProperty ncpSpeed = new NumberProperty("Speed", 4.2000003, () -> mode.getValue() == Mode.OldNCP, 3.5, 5.5, 0.05);
    private final NumberProperty ncpDecay = new NumberProperty("Decay", 159, () -> mode.getValue() == Mode.OldNCP, 159, 600, 1);
    private final NumberProperty ncpBoostTimer = new NumberProperty("Boost Timer", 2, () -> mode.getValue() == Mode.OldNCP, 1, 3.5, 0.05);
    private final NumberProperty ncpBoostTicks = new NumberProperty("Boost Ticks", 15, () -> mode.getValue() == Mode.OldNCP, 1, 40, 1, Representation.INT);
    private final Property<Boolean> ncpWaitForDamage = new Property<>("Wait For Damage", false, () -> mode.getValue() == Mode.OldNCP);

    private final Property<Boolean> ncpUseTimer = new Property<>("Use Timer", false, () -> mode.getValue() == Mode.OldNCP);

    private enum Mode {
        Motion("Motion"),
        Verus("Verus"),
        Vanilla("Vanilla"),
        Timer("Timer"),
        Miniblox("Miniblox"),
        Collide("Collide"),
        Packet("Packet"),
        Watchdog("Watchdog"),
        PearlExploit("Pearl"),
        Fireball("Fireball Fly"),
        Damage("Damage Fly"),
        OldNCP("Old NCP");

        public final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.Watchdog, new WatchdogFlight(viewBobbing, boost, boostMode, extraBoost, safeDamage, blink,
                    blinkIndicator, blinkLength, dropFly, watchdogSpeed, timerSpeed, timerLength))
            .add(Mode.PearlExploit, new PearlExploitFlight(this, pearlSpeed, viewBobbing, toggleAura))
            .add(Mode.Fireball, new FireballFly(this))
            .add(Mode.Damage, new DamageFly(this))
            .add(Mode.OldNCP, new OldNcpFlight(ncpSpeed, ncpDecay, ncpBoostTimer, ncpBoostTicks, ncpWaitForDamage, ncpUseTimer));

    private boolean ownClass() {
        return subModes.handles(mode.getValue());
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (!e.isPre() || ownClass()) return;

        mc.timer.timerSpeed = timerAmount.getValue().floatValue();

        switch (mode.getValue()) {
            case Motion:
                MovementUtils.setSpeed(motionSpeed.getValue());
                mc.thePlayer.motionY = mc.gameSettings.keyBindJump.isKeyDown() ? 0.5
                        : mc.gameSettings.keyBindSneak.isKeyDown() ? -0.5 : 0;
                break;

            case Verus:
                if (mc.gameSettings.keyBindJump.isKeyDown()) return;

                PacketUtils.sendSilentPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, new ItemStack(Items.water_bucket), 0, 0.5f, 0));
                MovementUtils.strafe(MovementUtils.getVerusLimit(true));
                mc.thePlayer.onGround = true;
                MovementUtils.setSpeed(0.32);
                e.setOnGround(mc.thePlayer.ticksExisted % 2 == 0);
                mc.thePlayer.motionY = 0;
                e.setPosY(Math.round(mc.thePlayer.posY));
                break;
            case Vanilla:
                mc.thePlayer.capabilities.isFlying = true;
                mc.thePlayer.capabilities.setFlySpeed(vanillaSpeed.getValue().floatValue() / 10.0f);
                mc.thePlayer.motionY = mc.gameSettings.keyBindJump.isKeyDown() ? 0.15
                        : mc.gameSettings.keyBindSneak.isKeyDown() ? -0.15 : 0;
                break;
            case Miniblox:
                mc.thePlayer.motionY = 0;
                MovementUtils.strafe(0.15f);
                break;
            case Timer:
                mc.thePlayer.motionY = 0.0;

                if (MovementUtils.isMoving()) {
                    float pulse = mc.thePlayer.ticksExisted % 12 < 6 ? 1.6F : 0.75F;
                    mc.timer.timerSpeed = pulse;
                    MovementUtils.setSpeed(motionSpeed.getValue());
                } else {
                    mc.timer.timerSpeed = 1.0F;
                    mc.thePlayer.motionX = 0;
                    mc.thePlayer.motionZ = 0;
                }

                if (mc.gameSettings.keyBindJump.isKeyDown()) {
                    mc.thePlayer.motionY = 0.18;
                }

                if (mc.gameSettings.keyBindSneak.isKeyDown()) {
                    mc.thePlayer.motionY = -0.18;
                }
                break;
            case Packet:
                    mc.thePlayer.motionY = 0;
                    if(MovementUtils.isMoving() && mc.thePlayer.ticksExisted % teleportDelay.getValue().intValue() == 0) {
                        final double x = e.getPosX() + -Math.sin(Math.toRadians(mc.thePlayer.rotationYaw)) * teleportLength.getValue().intValue();
                        final double z = e.getPosZ() + Math.cos(Math.toRadians(mc.thePlayer.rotationYaw)) * teleportLength.getValue().intValue();
                        PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(x, mc.thePlayer.posY, z, false));
                        mc.thePlayer.setPosition(x, mc.thePlayer.posY, z);
                    }
                break;
        }
    };

    @EventLink
    public final Listener<BlockCollideEvent> blockCollideEventListener = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mode.getValue() != Mode.Collide) {
            return;
        }

        if (e.getBlock() instanceof BlockAir && !mc.thePlayer.isSneaking()) {
            final double x = e.getX();
            final double y = e.getY();
            final double z = e.getZ();

            if (y < mc.thePlayer.posY) {
                e.setCollisionBoundingBox(
                        AxisAlignedBB.fromBounds(-15, -1, -15, 15, 1, 15)
                                .offset(x, y, z)
                );
            }
        }
    };

    @Override
    public void onEnable() {
        subModes.enable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        mc.timer.timerSpeed = 1.0f;
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.capabilities.setFlySpeed(0.05f);

        if (fullStop.getValue()) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
    }
}
