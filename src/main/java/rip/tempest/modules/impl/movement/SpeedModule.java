package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.*;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.movement.speed.AstralisLowHopSpeed;
import rip.tempest.modules.impl.movement.speed.ExhibitionHypixelLowSpeed;
import rip.tempest.modules.impl.movement.speed.ExireWatchdogSpeed;
import rip.tempest.modules.impl.movement.speed.FluxHypixelHopSpeed;
import rip.tempest.modules.impl.movement.speed.HypixelHopSpeed;
import rip.tempest.modules.impl.movement.speed.KrsHypixelLowHopSpeed;
import rip.tempest.modules.impl.movement.speed.OpenB4SevenTickSpeed;
import rip.tempest.modules.impl.movement.speed.Watchdog3Speed;
import rip.tempest.modules.impl.movement.speed.BhopSpeed;
import rip.tempest.modules.impl.movement.speed.RiseWatchdog6TickSpeed;
import rip.tempest.modules.impl.movement.speed.TenacityLowHopSpeed;
import rip.tempest.modules.impl.movement.speed.NovolineNcpSpeed;
import rip.tempest.modules.impl.movement.speed.YPortNcpSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogCustomSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogFastSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogGroundSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogLowHopSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogNewSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogNewTunedSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogRadiumSpeed;
import rip.tempest.modules.impl.movement.speed.WatchdogSpeed;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Speed", category = ModuleCategory.MOVEMENT)
public final class SpeedModule extends Module {
    private static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.LegitRotate);
    private static final NumberProperty vanillaSpeed = new NumberProperty("Vanilla Speed", 1.25, () -> mode.getValue() == Mode.Vanilla, 0.1, 3.0, 0.05);

    private final NumberProperty startingSpeed = new NumberProperty("Starting Speed", 0.6, () -> mode.getValue() == Mode.Watchdog, 0.5, 1.0, 0.05);
    private final NumberProperty damageBoost = new NumberProperty("Damage Boost", 1.0, () -> mode.getValue() == Mode.Watchdog, 0.0, 2.0, 0.05);
    private final ModeProperty<WatchdogSpeed.BoostMode> damageBoostMode = new ModeProperty<>("Damage Boost Mode", WatchdogSpeed.BoostMode.Set,
            () -> mode.getValue() == Mode.Watchdog);
    private final Property<Boolean> damageJump = new Property<>("Damage Jump", false, () -> mode.getValue() == Mode.Watchdog);

    private final Property<Boolean> strafeBypass = new Property<>("Strafe Bypass", true, () -> mode.getValue() == Mode.WatchdogCustom);
    private final NumberProperty jumpValue = new NumberProperty("Jump Y-Value", 0.42, () -> mode.getValue() == Mode.WatchdogCustom, 0.3, 0.43, 0.01);
    private final NumberProperty groundFriction = new NumberProperty("Ground Friction", 0.66, () -> mode.getValue() == Mode.WatchdogCustom, 0.5, 1.0, 0.01);
    private final NumberProperty groundBoost = new NumberProperty("Ground Boost", 2.14, () -> mode.getValue() == Mode.WatchdogCustom, 1.4, 2.14, 0.01);

    private final ModeProperty<WatchdogRadiumSpeed.FrictionMode> frictionMode = new ModeProperty<>("Friction Mode", WatchdogRadiumSpeed.FrictionMode.NCP,
            () -> mode.getValue() == Mode.Rewound);
    private final Property<Boolean> glide = new Property<>("Glide", false, () -> mode.getValue() == Mode.Rewound);
    private final Property<Boolean> explosionBoost = new Property<>("Explosion Boost", false, () -> mode.getValue() == Mode.Rewound);

    private final Property<Boolean> radiumTimer = new Property<>("Use Timer", true, () -> mode.getValue() == Mode.Rewound);

    {
        renamedSetting("Use Timer", "Timer");
    }
    private final NumberProperty onGroundTimer = new NumberProperty("OnGround Timer", 1.3,
            () -> mode.getValue() == Mode.Rewound && radiumTimer.getValue(), 1.0, 1.4, 0.01);

    private final NumberProperty hopSteps = new NumberProperty("Steps", 40.3, SpeedModule::exhibitionHop, 1.0, 180.0, 0.1);
    private final NumberProperty velBoost = new NumberProperty("Vel Boost", 0.5, SpeedModule::exhibitionHop, 0.0, 1.0, 0.01);
    private final Property<Boolean> strafeFix = new Property<>("Strafe Fix", true, SpeedModule::exhibitionHop);
    private final Property<Boolean> fastFall = new Property<>("Fast Fall", false, SpeedModule::exhibitionHop);
    private final Property<Boolean> firstSlow = new Property<>("First Slow", false, SpeedModule::exhibitionHop);

    private static boolean exhibitionHop() {
        return mode.getValue() == Mode.HypixelHop || mode.getValue() == Mode.ExhibitionHypixelLow;
    }

    private final ModeProperty<NovolineNcpSpeed.JumpMode> ncpJumpMode = new ModeProperty<>("NCP Jump Mode", NovolineNcpSpeed.JumpMode.Reduced, () -> mode.getValue() == Mode.NovolineNCP);
    private final Property<Boolean> ncpDownMotion = new Property<>("NCP Down Motion", false, () -> mode.getValue() == Mode.NovolineNCP);
    private final Property<Boolean> ncpLagbackCheck = new Property<>("NCP Lagback Check", false, () -> mode.getValue() == Mode.NovolineNCP);
    private final Property<Boolean> ncpDamageBoost = new Property<>("NCP Damage Boost", false, () -> mode.getValue() == Mode.NovolineNCP);
    private final NumberProperty ncpTimer = new NumberProperty("NCP Timer", 1.0, () -> mode.getValue() == Mode.NovolineNCP, 1.0, 2.0, 0.05);
    private final NumberProperty ncpFriction = new NumberProperty("NCP Friction", 150, () -> mode.getValue() == Mode.NovolineNCP, 60, 180, 2);

    private final NumberProperty w3DamageBooster = new NumberProperty("Damage Booster", 0.0, () -> mode.getValue() == Mode.Watchdog3, 0.0, 1.0, 0.01);
    private final NumberProperty w3MultiplyFactor = new NumberProperty("Multiply Factor", 1.0, () -> mode.getValue() == Mode.Watchdog3, 1.0, 3.0, 0.05);
    private final NumberProperty w3MultiplyTicks = new NumberProperty("Multiply Ticks", 0, () -> mode.getValue() == Mode.Watchdog3, 0, 10, 1);
    private final NumberProperty w3StrafeAmount = new NumberProperty("Strafe Amount", 0.2, () -> mode.getValue() == Mode.Watchdog3, 0.0, 1.0, 0.01);
    private final Property<Boolean> w3MultiplyTick = new Property<>("Multiply Tick", false, () -> mode.getValue() == Mode.Watchdog3);

    private final NumberProperty hopTimer = new NumberProperty("Timer", 1, SpeedModule::hopTimed, 1, 2, 0.1);

    private final NumberProperty lowHopBoost = new NumberProperty("Low Hop Boost", 1.08, () -> mode.getValue() == Mode.RewoundHop, 1.0, 1.3, 0.01);
    private final NumberProperty hopRandomTimer = new NumberProperty("Randomized Timer", 0.05,
            () -> mode.getValue() == Mode.Bhop && hopTimer.getValue() > 1.0, 0.01, 1.2, 0.01);
    private final NumberProperty hopAbove2Ticks = new NumberProperty("Above 2.0 Timer Ticks", 2, this::hopAbove2, 1, 10, 1);
    private final ModeProperty<BhopSpeed.TicksMode> hopAbove2Mode = new ModeProperty<>("A2T Ticks Mode",
            BhopSpeed.TicksMode.NotEquals0, this::hopAbove2);
    private final NumberProperty hopAbove2Start = new NumberProperty("A2TS Starting Ticks", 1,
            () -> hopAbove2() && hopAbove2Mode.getValue() == BhopSpeed.TicksMode.NotEquals0, 1, 9, 1);

    private static boolean hopTimed() {
        return mode.getValue() == Mode.Bhop || mode.getValue() == Mode.RewoundLow || mode.getValue() == Mode.RewoundHop;
    }

    private boolean hopAbove2() {
        return mode.getValue() == Mode.Bhop && hopTimer.getValue() >= 2.0;
    }

    private final NumberProperty timerBoost = new NumberProperty("Timer Boost", 0.0, 0.0, 3.0, 0.1);
    private final ModeProperty<BoostMode> boostMode = new ModeProperty<>("Boost Mode", BoostMode.Normal, () -> timerBoost.getValue() > 0.0);

    public enum BoostMode {
        Normal, Dynamic
    }

    private enum Mode {
        Legit("Legit"),
        LegitRotate("Legit Rotate"),
        LegitTimerExploit("Hylex"),
        Grim("Grim"),
        VerusGround("Verus Ground"),
        VerusLowHop("Verus Low Hop"),
        UpdatedNCP("Updated NCP"),
        DamageHop("Damage Hop"),
        NCP("NCP"),
        NovolineNCP("Novoline NCP"),
        YPortNCP("YPort NCP"),
        Strafe("Strafe"),
        GroundStrafe("Ground Strafe"),
        Intave("Intave"),
        Yport("Yport"),
        Vanilla("Vanilla"),
        CollisionExploit("Collision Exploit"),

        Watchdog("Watchdog"),
        WatchdogLowHop("Watchdog Low Hop"),
        WatchdogGround("Watchdog Ground"),
        WatchdogCustom("Watchdog Custom"),
        WatchdogFast("Watchdog Fast"),
        WatchdogNew("Watchdog New"),

        WatchdogNewPlus("Watchdog New+"),
        WatchdogNewFast("Watchdog New Fast"),
        WatchdogNewLow("Watchdog New Low"),
        Rewound("Rewound"),
        HypixelHop("Hypixel Hop"),

        ExireWatchdog("Watchdog2"),
        FluxHypixelHop("Hypixel Hop2"),
        AstralisLowHop("Watchdog Low Hop2"),
        RiseWatchdog6Tick("Watchdog 6 Tick"),
        TenacityLowHop("Watchdog Low Hop3"),
        ExhibitionHypixelLow("Hypixel Low"),
        KrsHypixelLowHop("krs Hypixel Low Hop"),
        OpenB4SevenTick("Watchdog 7 Tick"),

        Watchdog3("Watchdog3"),

        Bhop("Bhop"),
        RewoundLow("Rewound Low"),

        RewoundHop("Rewound Hop"),
        Vanilla2("Vanilla2"),
        VLAbuse("VL Abuse"),
        Zonecraft("Zonecraft"),
        Vulcan("Vulcan"),
        Ghostly("Ghostly");

        public String name;

        Mode(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    private BhopSpeed hop(BhopSpeed.Kind kind) {
        return new BhopSpeed(kind, hopTimer, hopRandomTimer, hopAbove2Ticks, hopAbove2Mode, hopAbove2Start);
    }

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.Watchdog, new WatchdogSpeed(startingSpeed, damageBoost, damageBoostMode, damageJump))
            .add(Mode.WatchdogLowHop, new WatchdogLowHopSpeed())
            .add(Mode.WatchdogGround, new WatchdogGroundSpeed())
            .add(Mode.WatchdogCustom, new WatchdogCustomSpeed(strafeBypass, jumpValue, groundFriction, groundBoost))
            .add(Mode.WatchdogFast, new WatchdogFastSpeed())
            .add(Mode.WatchdogNew, new WatchdogNewSpeed())

            .add(Mode.WatchdogNewPlus, new WatchdogNewTunedSpeed(0.68D, 0.55D, 159.9D, 0.42F, 1.0D))
            .add(Mode.WatchdogNewFast, new WatchdogNewTunedSpeed(0.75D, 0.45D, 200.0D, 0.42F, 1.03D))

            .add(Mode.WatchdogNewLow, new WatchdogNewTunedSpeed(0.75D, 0.45D, 200.0D, 0.25F, 1.03D))
            .add(Mode.Rewound, new WatchdogRadiumSpeed(frictionMode, glide, explosionBoost, radiumTimer, onGroundTimer))
            .add(Mode.HypixelHop, new HypixelHopSpeed(strafeFix, fastFall, firstSlow, hopSteps, velBoost))
            .add(Mode.ExireWatchdog, new ExireWatchdogSpeed())
            .add(Mode.FluxHypixelHop, new FluxHypixelHopSpeed())
            .add(Mode.AstralisLowHop, new AstralisLowHopSpeed())
            .add(Mode.RiseWatchdog6Tick, new RiseWatchdog6TickSpeed())
            .add(Mode.TenacityLowHop, new TenacityLowHopSpeed())
            .add(Mode.ExhibitionHypixelLow, new ExhibitionHypixelLowSpeed(strafeFix, fastFall, firstSlow, hopSteps, velBoost))
            .add(Mode.KrsHypixelLowHop, new KrsHypixelLowHopSpeed())
            .add(Mode.OpenB4SevenTick, new OpenB4SevenTickSpeed())
            .add(Mode.Bhop, hop(BhopSpeed.Kind.Bhop))
            .add(Mode.RewoundLow, hop(BhopSpeed.Kind.LowHop))
            .add(Mode.RewoundHop, hop(BhopSpeed.Kind.LowHopFast).withBoost(lowHopBoost))
            .add(Mode.Vanilla2, hop(BhopSpeed.Kind.Vanilla))
            .add(Mode.VLAbuse, hop(BhopSpeed.Kind.VLAbuse))
            .add(Mode.Zonecraft, hop(BhopSpeed.Kind.Zonecraft))
            .add(Mode.Vulcan, hop(BhopSpeed.Kind.Vulcan))
            .add(Mode.Ghostly, hop(BhopSpeed.Kind.Ghostly))
            .add(Mode.Watchdog3, new Watchdog3Speed(w3DamageBooster, w3MultiplyFactor, w3MultiplyTicks,
                    w3StrafeAmount, w3MultiplyTick))
            .add(Mode.YPortNCP, new YPortNcpSpeed())
            .add(Mode.NovolineNCP, new NovolineNcpSpeed(ncpJumpMode, ncpDownMotion, ncpLagbackCheck, ncpDamageBoost, ncpTimer, ncpFriction,
                    () -> {
                        if (isEnabled()) toggle();
                    }));

    private double speedV;
    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());

        if (mc.gameSettings.keyBindJump.isKeyDown() && !subModes.handles(mode.getValue()) && mode.getValue() != Mode.Legit && mode.getValue() != Mode.LegitRotate && mode.getValue() != Mode.Grim && mode.getValue() != Mode.LegitTimerExploit && mode.getValue() != Mode.CollisionExploit)
            mc.gameSettings.keyBindJump.setPressed(false);
        if (!e.isPre()) return;
        switch (mode.getValue()) {
            case VerusGround:
                if (mc.gameSettings.keyBindJump.isKeyDown()) return;

                PacketUtils.sendSilentPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, new ItemStack(Items.water_bucket), 0, 0.5f, 0));

                if (MovementUtils.isMoving()) {
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.motionY = 0.00001f;
                    }
                } else {
                    mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
                }

                MovementUtils.strafe(MovementUtils.getVerusLimit(true));

                if (mc.thePlayer.fallDistance > 0.2) {
                    mc.thePlayer.motionY = -0.1f;
                }
                break;
            case VerusLowHop:
                PacketUtils.sendSilentPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, new ItemStack(Items.water_bucket), 0, 0.5f, 0));
                if (mc.thePlayer.onGround && MovementUtils.isMoving()) {
                    mc.thePlayer.motionY = 0.42;
                    MovementUtils.strafe(0.48f, 0.52f, 0.6f);
                }
                if (mc.thePlayer.offGroundTicks == 1) {
                    mc.thePlayer.motionY = -0.15233518685055714;
                }
                break;
            case Strafe:
                if (MovementUtils.isMoving()) {
                    MovementUtils.strafe();
                    if (MovementUtils.isOnGround()) {
                        mc.thePlayer.jump();
                    }
                }
                break;
            case DamageHop:
                if (MovementUtils.isMoving()) {
                    if (mc.thePlayer.hurtTime > 0) {
                        MovementUtils.strafe(0.65f);
                    } else {
                        MovementUtils.strafe();
                    }

                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.jump();
                    }
                }
                break;
            case GroundStrafe:
                if (MovementUtils.isMoving()) {
                    if (MovementUtils.isOnGround()) {
                        mc.thePlayer.jump();
                        MovementUtils.strafe();
                    }
                }
                break;
            case Yport:
                if (MovementUtils.isMoving()) {
                    mc.timer.timerSpeed = mc.thePlayer.onGround ? 1.15F : 1.03F;

                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.jump();
                        MovementUtils.strafe(0.46f);
                    } else {
                        MovementUtils.strafe();
                    }
                } else {
                    mc.timer.timerSpeed = 1.0F;
                }
                break;
            case UpdatedNCP:
                if (MovementUtils.isMoving() && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    MovementUtils.strafe();
                    if (mc.thePlayer.offGroundTicks >= 5.1) {
                        mc.timer.timerSpeed = 1.2f;
                    } else
                        mc.timer.timerSpeed = 1.0f;
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.jump();
                    }
                }
                if (!MovementUtils.isMoving()) {
                    mc.timer.timerSpeed = 1.0f;
                }
                break;
            case Legit:
            case LegitRotate:
            case CollisionExploit:
                mc.gameSettings.keyBindJump.setPressed(MovementUtils.isMoving() && MovementUtils.isOnGround());
                break;
            case LegitTimerExploit:
                mc.gameSettings.keyBindJump.setPressed(MovementUtils.isMoving() && MovementUtils.isOnGround());
                mc.timer.timerSpeed = 1.0075f;
                break;
            case Grim:
                mc.gameSettings.keyBindJump.setPressed(MovementUtils.isMoving() && MovementUtils.isOnGround());
                mc.timer.timerSpeed = 1.02f;
                break;
            case Intave:
                if (MovementUtils.isOnGround() && MovementUtils.isMoving()) {
                    mc.thePlayer.jump();
                }

                switch (mc.thePlayer.offGroundTicks) {
                    case 1:
                        mc.thePlayer.motionX *= 1.005;
                        mc.thePlayer.motionZ *= 1.005;
                        break;
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                        mc.thePlayer.motionX *= 1.011;
                        mc.thePlayer.motionZ *= 1.011;
                        break;
                }

                if (mc.thePlayer.onGroundTicks == 1) {
                    mc.thePlayer.motionX *= 1.0045;
                    mc.thePlayer.motionZ *= 1.0045;
                }

                mc.timer.timerSpeed = 1.0075f;
                break;
            case NCP:
                if (MovementUtils.isMoving()) {
                    if (mc.thePlayer.fallDistance > 1.0) {
                        mc.timer.timerSpeed = 0.8f;
                    } else {
                        mc.timer.timerSpeed = 1.9f;
                    }
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.motionY = 0.42f;
                        float speed = 0.0310f;
                        if (mc.thePlayer.speedInAir < speed) {
                            if (mc.thePlayer.speedInAir < 0.025) {
                                mc.thePlayer.speedInAir = (float) (0.025f + (Math.random() / 100));
                            }
                            mc.thePlayer.speedInAir += 0.0091f;
                        } else {
                            mc.thePlayer.speedInAir = speed;
                        }
                        if (mc.thePlayer.jumpMovementFactor > 0.022) {
                            mc.thePlayer.jumpMovementFactor -= 0.002f;
                        }
                    } else {
                        mc.thePlayer.motionY *= 1.00;
                        mc.thePlayer.motionX *= 0.982f;
                        mc.thePlayer.motionZ *= 0.982f;
                        mc.thePlayer.speedInAir -= 0.00019f;
                        if (mc.thePlayer.fallDistance > 0.4 && mc.thePlayer.fallDistance < 0.41) {
                            mc.thePlayer.motionY -= 0.1f;
                        }
                        if (mc.thePlayer.hurtTime > 4) {
                            mc.thePlayer.speedInAir += 0.006f;
                        }
                    }
                }
                break;
        }
    };

    private static final int BOOST_SETBACK_TICKS = 15;

    private int boostStage, boostHops;
    private boolean boostedTimer;
    private volatile int boostPause;

    @EventLink(Priorities.VERY_LOW)
    public final Listener<MotionEvent> timerBoostListener = e -> {
        if (!e.isPre()) return;

        boolean moving = MovementUtils.isMoving();
        boolean onGround = mc.thePlayer.onGround;
        if (onGround) {
            boostStage = 2;
            boostHops = moving ? boostHops + 1 : 0;
        } else {
            boostStage++;
        }
        if (boostPause > 0) {
            boostPause--;
            boostHops = 0;
        }

        float boost = timerBoost.getValue().floatValue();
        if (boost > 0.0F && boostPause == 0 && (moving || !onGround)) {
            boostedTimer = true;
            float add = boost + boost / 10.0F * (float) Math.random();
            if (boostMode.getValue() == BoostMode.Normal) {
                mc.timer.timerSpeed = 1.0F + add;
            } else if (boostHops > 1 && onGround) {
                mc.timer.timerSpeed = 1.0F - boost / 7.0F + 0.05F * (float) Math.random();
            } else {
                mc.timer.timerSpeed = 1.0F + add / boostStage;
            }
        } else if (boostedTimer) {
            boostedTimer = false;
            mc.timer.timerSpeed = 1.0F;
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> timerBoostSetback = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            boostPause = BOOST_SETBACK_TICKS;
        }
    };

    @EventLink
    public final Listener<MovePlayerEvent> movePlayerEventListener = e -> {
        if (mode.getValue() == Mode.NCP) {
            if (mc.thePlayer.onGround) {
                e.setY(mc.thePlayer.motionY = 0.42F);
                if (speedV < 0.2805) {
                    speedV = 0.2805;
                }
                speedV *= 1.949;
            }
        } else if (mode.getValue() == Mode.Vanilla) {
            if (MovementUtils.isMoving()) {
                if (mc.thePlayer.onGround) {
                    for (int i = 0; i < 20; i++) {
                        mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                                mc.thePlayer.posX, mc.thePlayer.posY + 1.0E-9D, mc.thePlayer.posZ, mc.thePlayer.onGround));
                    }
                    MovementUtils.setSpeed(vanillaSpeed.getValue());
                    e.setY(mc.thePlayer.motionY = 0.4D);
                } else {
                    MovementUtils.setSpeed(MovementUtils.getSpeed());
                }
            } else {
                mc.thePlayer.motionX = 0.0D;
                mc.thePlayer.motionZ = 0.0D;
            }
        }
    };

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (!MovementUtils.isOnGround() && mode.getValue() == Mode.LegitRotate) {
            RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw + 45, mc.thePlayer.rotationPitch), 10, MovementFix.NORMAL);
        }
    };

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<StrafeEvent> strafe = event -> {
        if (mode.getValue() == Mode.CollisionExploit) {
            mc.theWorld.playerEntities.stream()
                    .filter(entityPlayer -> entityPlayer != mc.thePlayer &&
                            mc.thePlayer.getEntityBoundingBox().expand(1, 1, 1)
                                    .intersectsWith(entityPlayer.getEntityBoundingBox()))
                    .forEach(entityPlayer -> MovementUtils.moveFlying(0.08));
        }
    };

    @Override
    public void onEnable() {
        boostStage = 2;
        boostHops = 0;
        boostPause = 0;
        boostedTimer = false;
        subModes.enable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        mc.timer.timerSpeed = 1.0f;
        if (mode.getValue() == Mode.UpdatedNCP || mode.getValue() == Mode.NCP) {
            mc.thePlayer.speedInAir = 0.02F;
        }
        if (mode.getValue() == Mode.Legit && mc.gameSettings.keyBindJump.isPressed())
            mc.gameSettings.keyBindJump.setPressed(false);
        if (mode.getValue() == Mode.LegitRotate && mc.gameSettings.keyBindJump.isPressed())
            mc.gameSettings.keyBindJump.setPressed(false);
        if (mode.getValue() == Mode.LegitTimerExploit && mc.gameSettings.keyBindJump.isPressed())
            mc.gameSettings.keyBindJump.setPressed(false);
        if (mode.getValue() == Mode.Grim && mc.gameSettings.keyBindJump.isPressed())
            mc.gameSettings.keyBindJump.setPressed(false);
        if (mode.getValue() == Mode.CollisionExploit && mc.gameSettings.keyBindJump.isPressed())
            mc.gameSettings.keyBindJump.setPressed(false);
    }
}
