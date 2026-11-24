package rip.tempest.modules.impl.player;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.movement.SpeedModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.processes.SlotProcess;
import rip.tempest.utils.client.EnumFacingOffset;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import rip.tempest.utils.mc.RayCastUtils;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.mc.ScaffoldUtils;
import rip.tempest.utils.misc.MovementFix;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import rip.tempest.api.events.impl.player.PostStrafeEvent;
import rip.tempest.api.events.impl.player.TeleportEvent;
import rip.tempest.utils.mc.ExireRotations;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.awt.Font;

import static rip.tempest.utils.Util.mc;

/**
 * Builds under you as you walk. The bridging itself (finding where to place, the modes, the
 * rotations, sprint and tower handling, and the slot swap) is Yuri's Scaffold (AGPL-3.0),
 * ported onto RotationProcess and Tempest's settings. Kept from Tempest's own Scaffold: the
 * block counters, the outline on the block being placed against, and the four telly variants
 * (Slow, Fast, Lock and Rapid) as extra modes. Three of the block counters follow other clients'
 * (Badge: OpenAstralis, Alert: Open B4, Outline: Yuri). The Experiment rotations (see
 * RotationExperiment) are for testing: random rotation profiles, changed while bridging. Telly
 * Plus is Coolware's telly bridging (its phases, presets and duplicate-turn guard).
 */
@ModuleInfo(label = "Scaffold", category = ModuleCategory.PLAYER)
public final class ScaffoldModule extends Module {
    public static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);
    private final ModeProperty<ExireRotations.Mode> rewoundRotation = new ModeProperty<>("Rewound Rotation", ExireRotations.Mode.Normal, () -> mode.getValue() == Mode.Rewound);

    private final Property<Boolean> hypixelPose = new Property<>("Hypixel Larp Mode", true, () -> mode.getValue() == Mode.Hypixel);
    private final Property<Boolean> hypixelTelly = new Property<>("Hypixel Telly", false, () -> mode.getValue() == Mode.Telly);

    private final NumberProperty tellyStraightTicks = new NumberProperty("Telly Straight Ticks", 6, () -> mode.getValue() == Mode.Telly && !hypixelTelly.getValue(), 0, 8, 1);
    private final NumberProperty tellyDiagonalTicks = new NumberProperty("Telly Diagonal Ticks", 4, () -> mode.getValue() == Mode.Telly && !hypixelTelly.getValue(), 0, 8, 1);
    private final NumberProperty tellyJumpDownTicks = new NumberProperty("Telly Jump Down Ticks", 1, () -> mode.getValue() == Mode.Telly && !hypixelTelly.getValue(), 0, 8, 1);

    private final ModeProperty<TellyPreset> tellyPreset = new ModeProperty<>("Telly Preset", TellyPreset.Grim, () -> mode.getValue() == Mode.TellyPlus);
    private final Property<Boolean> tellySmooth = new Property<>("Smooth Rotation", true, () -> mode.getValue() == Mode.TellyPlus && tellyPreset.getValue() == TellyPreset.Custom);
    private final ModeProperty<Easing> tellyEasing = new ModeProperty<>("Easing", Easing.EaseOutExpo, () -> mode.getValue() == Mode.TellyPlus && tellyPreset.getValue() == TellyPreset.Custom && tellySmooth.getValue());
    private final NumberProperty tellyGroundTicks = new NumberProperty("Telly Ticks", 1, () -> mode.getValue() == Mode.TellyPlus && tellyPreset.getValue() == TellyPreset.Custom, 1, 5, 1);

    private final Property<Boolean> noDuplicateRot = new Property<>("No Duplicate Rot", true, () -> mode.getValue() == Mode.TellyPlus);
    public static final ModeProperty<Rotations> rotations = new ModeProperty<>("Rotations", Rotations.Normal);
    private final NumberProperty randomizedSpeedMin = new NumberProperty("Randomized Speed Min", 3, () -> rotations.getValue() == Rotations.Randomized, 0, 10, 0.5);
    private final NumberProperty randomizedSpeedMax = new NumberProperty("Randomized Speed Max", 7, () -> rotations.getValue() == Rotations.Randomized, 0, 10, 0.5);
    private final NumberProperty minRotationSpeed = new NumberProperty("Min Rotation Speed", 3, 0, 10, 0.5);
    private final NumberProperty maxRotationSpeed = new NumberProperty("Max Rotation Speed", 7, 0, 10, 0.5);

    private final Property<String> experimentAnticheat = new Property<>("Anticheat", "", () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentFaster = new Property<>("Faster", false, () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentSlower = new Property<>("Slower", false, () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentNew = new Property<>("New Random", false, () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentWorks = new Property<>("Mark Working", false, () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentFlagged = new Property<>("Mark Flagged", false, () -> rotations.getValue() == Rotations.Experiment);
    private final Property<Boolean> experimentPanel = new Property<>("Show Experiment", true, () -> rotations.getValue() == Rotations.Experiment);
    private final NumberProperty placeDelay = new NumberProperty("Place Delay", 0, 0, 10, 1);
    private final ModeProperty<RayCast> rayCast = new ModeProperty<>("Ray Cast", RayCast.Normal);

    private final ModeProperty<SwapMode> swapMode = new ModeProperty<>("Swap Mode", SwapMode.Client);
    private final ModeProperty<SprintMode> sprintMode = new ModeProperty<>("Sprint Mode", SprintMode.None);
    private final ModeProperty<TowerMode> towerMode = new ModeProperty<>("Tower Mode", TowerMode.None);
    private final Property<Boolean> towerMove = new Property<>("Tower Move", true, () -> towerMode.getValue() != TowerMode.None);
    public final Property<Boolean> moveFix = new Property<>("Move Fix", true);
    public final Property<Boolean> autoJump = new Property<>("Auto Jump", false);
    public final Property<Boolean> keepY = new Property<>("Keep Y", false);

    private final ModeProperty<KeepYMode> keepYMode = new ModeProperty<>("Keep Y Mode", KeepYMode.Off);
    private final Property<Boolean> sneak = new Property<>("Sneak", false);
    private final NumberProperty sneakEvery = new NumberProperty("Sneak Every", 1, sneak::getValue, 0, 10, 1);
    private final Property<Boolean> safeWalk = new Property<>("Safe Walk", false);
    private final NumberProperty expand = new NumberProperty("Expand", 0, 0, 4, 1);

    private final Property<Boolean> timer = new Property<>("Timer", false);
    private final NumberProperty timerSpeed = new NumberProperty("Timer Speed", 1.0, timer::getValue, 0.1, 3.0, 0.01);
    private final ModeProperty<BlockCounter> blockCounter = new ModeProperty<>("Block Counter", BlockCounter.None);
    private final Property<Boolean> render = new Property<>("Render Block Selection", true);

    private final Property<Boolean> autoDisable = new Property<>("Auto Disable", false);

    public enum Mode {
        Normal("Normal"),
        Telly("Telly"),
        Hypixel("Hypixel"),
        Breezily("Breezily"),
        GodBridge("God Bridge"),

        SlowTelly("Slow Telly"),
        FastTelly("Fast Telly"),
        TellyLock("Telly Lock"),
        TellyRapid("Telly Rapid"),

        TellyPlus("Telly Plus"),

        NoRotate("No Rotate"),

        Rewound("Rewound");

        private final String display;

        Mode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }

        boolean telly() {
            return this == Telly || this == SlowTelly || this == FastTelly || this == TellyLock || this == TellyRapid;
        }
    }

    public enum Rotations { Normal, Randomized, Old, Experiment, Grim2, Grim3, Grim4, ExhibitionWatchdog }

    public enum SprintMode { Vanilla, Universal, NCP, Legit, None }

    public enum KeepYMode { Off, Vanilla, Telly }

    public enum TowerMode {
        Vanilla("Vanilla"),
        Polar("Polar"),
        Hypixel("Hypixel"),
        NCP("NCP"),

        UpdatedNCP("Updated NCP"),
        None("None");

        private final String display;

        TowerMode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum RayCast { None, Normal, Strict }

    public enum SwapMode { Client, Server }

    public enum TellyPreset {
        Hypixel(true, Easing.EaseOutQuad, 1),
        Grim(false, Easing.Normal, 1),
        Custom(true, Easing.EaseOutExpo, 1);

        final boolean smooth;
        final Easing easing;
        final int groundTicks;

        TellyPreset(boolean smooth, Easing easing, int groundTicks) {
            this.smooth = smooth;
            this.easing = easing;
            this.groundTicks = groundTicks;
        }
    }

    public enum Easing {
        Normal("Linear") {
            double ease(double x) { return x; }
        },
        EaseOutSine("Ease Out Sine") {
            double ease(double x) { return Math.sin(x * Math.PI / 2); }
        },
        EaseOutQuad("Ease Out Quad") {
            double ease(double x) { return 1 - (1 - x) * (1 - x); }
        },
        EaseOutCubic("Ease Out Cubic") {
            double ease(double x) { return 1 - Math.pow(1 - x, 3); }
        },
        EaseOutQuart("Ease Out Quart") {
            double ease(double x) { return 1 - Math.pow(1 - x, 4); }
        },
        EaseOutExpo("Ease Out Expo") {
            double ease(double x) { return x == 1 ? 1 : 1 - Math.pow(2, -10 * x); }
        },
        EaseInOutSine("Ease In Out Sine") {
            double ease(double x) { return -(Math.cos(Math.PI * x) - 1) / 2; }
        },
        EaseInOutQuad("Ease In Out Quad") {
            double ease(double x) { return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2; }
        };

        private final String display;

        Easing(String display) {
            this.display = display;
        }

        abstract double ease(double x);

        @Override
        public String toString() {
            return display;
        }
    }

    public enum BlockCounter { Clean, Badge, Alert, Outline, None }

    private final Timer delayTimer = new Timer();
    private final Timer tellySafeTimer = new Timer();
    private Vec3 targetBlock;
    private EnumFacingOffset enumFacing;
    public Vec3i offset = new Vec3i(0, 0, 0);
    private BlockPos blockFace;
    private float targetYaw, targetPitch;
    private int ticksOnAir;
    public int recursions, recursion;
    public double startY;
    private boolean canPlace;
    private int directionalChange;
    private float rotSpeed;

    private float baseRotSpeed;

    private float exhiLastYaw = Float.NaN;

    private boolean ncpTowered;
    private int ncpAirTicks;
    private double ncpMotionRemoval;
    private int ticksSinceTeleport = 100;
    private final RotationExperiment experiment = new RotationExperiment(experimentAnticheat::getValue);

    private final RotationExperiment grim2 = RotationExperiment.fixed(RotationExperiment.Profile.of(28,
            8.51f, 0.44f, 0.96f, 0.95f, 1.03f, 3.75f, 0.01f, 3.95f, 0, false));

    private final RotationExperiment grim3 = RotationExperiment.fixed(RotationExperiment.Profile.of(169,
            5.13f, 0.58f, 0.97f, 3.83f, 0.24f, 2.73f, 0.05f, 5.91f, 0, false));

    private final RotationExperiment grim4 = RotationExperiment.fixed(RotationExperiment.Profile.of(195,
            4.25f, 0.05f, 0.90f, 3.09f, 1.45f, 3.87f, 0.05f, 1.29f, 0, true));
    private boolean stop;
    private int blocksPlaced;

    private int keepYState, keepYHoldTicks;
    private boolean keepYTellyActive, keepYTakeoffTick;
    private boolean tellyNoPlace;

    private int tellyGroundPlaced;
    private float lastPlacedYawDelta = -1f;

    private boolean timerApplied;
    private int placeTimer;

    private final CounterOverlay counterOverlay = new CounterOverlay();
    private boolean counterSubscribed;

    private float counterShow;
    private long counterLastMs;
    private float counterDt;

    private int counterBlocks, counterHeld;

    private int blockMax;

    private final Smooth barFill = new Smooth();
    private final Smooth alertLevel = new Smooth(), badgeWidth = new Smooth(), rollWidth = new Smooth(), pillWidth = new Smooth();
    private static final int ROLL_DIGITS = 6, ROLL_MS = 220;
    private final char[] rollFrom = new char[ROLL_DIGITS], rollTo = new char[ROLL_DIGITS];
    private final long[] rollAt = new long[ROLL_DIGITS];
    private final boolean[] rollDown = new boolean[ROLL_DIGITS];
    private int rollShown = -1;
    private static final int SEGMENTS = 10, SEGMENT_FLASH_MS = 350;
    private final long[] segEmptiedAt = new long[SEGMENTS];
    private int segLit = -1;

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        ticksSinceTeleport++;
        applyTimer();
        resetBinds(false, false, true, true, false, false);

        if (placeTimer > 0) placeTimer--;

        if (autoDisable.getValue()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityFireball && entity.getDistanceToEntity(mc.thePlayer) < 6) {
                    RotationProcess.setRotations(RotationUtils.calculate(entity), 10, MovementFix.NORMAL);
                    if (entity.getDistanceToEntity(mc.thePlayer) <= 5) {
                        NotificationManager.post(NotificationType.WARNING, "Scaffold", "Disabled, fireball detected.");
                        this.toggle();
                    }
                    break;
                }
            }
            if (!isEnabled()) return;
        }

        if (mc.gameSettings.keyBindAttack.isPressed()) {
            mc.gameSettings.keyBindAttack.setPressed(false);
        }

        setSuffix(mode.getValue().toString());

        if (safeWalk.getValue()) mc.thePlayer.safeWalk = true;

        updateKeepYMode();

        sprint();
        sneak();
        tower();

        if (mode.getValue() == Mode.Telly) tellyLogic();

        if (mode.getValue() == Mode.Hypixel) {
            float moveYaw = RotationUtils.getMovementYaw();
            float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(moveYaw + 50.0f - targetYaw));
            if (placeTimer == (yawDelta > 60.0f ? 8 : 9) && !mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.onGround) {
                targetYaw = moveYaw + 50.0f;
            }
            if (mc.thePlayer.onGround) startY = Math.floor(mc.thePlayer.posY);
        }

        if (mc.gameSettings.keyBindJump.isKeyDown()
                || (mc.thePlayer.onGround && mc.thePlayer.posY < startY)
                || (mc.thePlayer.onGround && mc.thePlayer.posY > startY && mc.thePlayer.onGroundTicks <= 1)
                || (mc.thePlayer.onGround && Math.abs(mc.thePlayer.posY - startY) > 0.5 && !MovementUtils.isMoving())) {
            startY = Math.floor(mc.thePlayer.posY);
        }

        for (recursion = 0; recursion <= recursions; recursion++) {
            rotSpeed = (float) MathUtils.getRandom(minRotationSpeed.getValue(), maxRotationSpeed.getValue());
            baseRotSpeed = rotSpeed;

            if (expand.getValue().intValue() != 0) {
                double direction = MovementUtils.direction(mc.thePlayer.rotationYaw,
                        mc.gameSettings.keyBindForward.isKeyDown() ? 1 : mc.gameSettings.keyBindBack.isKeyDown() ? -1 : 0,
                        mc.gameSettings.keyBindRight.isKeyDown() ? -1 : mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0);

                for (int range = 0; range <= expand.getValue().intValue(); range++) {
                    if (PlayerUtils.blockAheadOfPlayer(range, offset.getY() - 0.5) instanceof BlockAir) {
                        offset = offset.add(new Vec3i((int) (-Math.sin(direction) * (range + 1)), 0, (int) (Math.cos(direction) * (range + 1))));
                        break;
                    }
                }
            }

            final boolean sameY = ((((keepY.getValue() || mode.getValue() == Mode.TellyPlus || mode.getValue() == Mode.NoRotate
                    || keepYMode.getValue() != KeepYMode.Off)
                    && !mc.gameSettings.keyBindJump.isKeyDown())
                    || Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class).isEnabled() && !mc.gameSettings.keyBindJump.isKeyDown())
                    && MovementUtils.isMoving()) && Math.abs(mc.thePlayer.posY - startY) <= 3.0;

            final int blockSlot = ScaffoldUtils.getBlockSlot();
            if (blockSlot == -1) {
                NotificationManager.post(NotificationType.DISABLE, "Scaffold", "Disabled, no blocks found.");
                this.toggle();
                return;
            }

            final ItemStack blockStack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
            if (blockStack == null || !(blockStack.getItem() instanceof ItemBlock)) return;

            SlotProcess.swap(blockSlot, rayCast.getValue() != RayCast.Strict && swapMode.getValue() == SwapMode.Server);

            if (ScaffoldUtils.doesNotContainBlock(offset, 1) && (!sameY
                    || (ScaffoldUtils.doesNotContainBlock(offset, 2) && ScaffoldUtils.doesNotContainBlock(offset, 3) && ScaffoldUtils.doesNotContainBlock(offset, 4)))) {
                ticksOnAir++;
            } else {
                ticksOnAir = 0;
            }

            canPlace = ticksOnAir > 0;
            if (keepYHoldTicks > 0) canPlace = false;

            if (mode.getValue() == Mode.Telly && tellyNoPlace) canPlace = false;
            if (mode.getValue() == Mode.Telly && !tellySafeTimer.hasTimeElapsed(700)) {
                canPlace = true;
                tellyNoPlace = false;
            }

            final boolean downwards = offset.getY() < 0;

            targetBlock = ScaffoldUtils.getPlacePossibility(offset.getX(), offset.getY(), offset.getZ(),
                    sameY ? (int) Math.floor(startY) : null, downwards, true);
            if (targetBlock == null) return;

            enumFacing = ScaffoldUtils.getEnumFacing(targetBlock, downwards, true);
            if (enumFacing == null) return;

            final BlockPos position = new BlockPos(targetBlock.xCoord, targetBlock.yCoord, targetBlock.zCoord);
            blockFace = position.add(enumFacing.getOffset().xCoord, enumFacing.getOffset().yCoord, enumFacing.getOffset().zCoord);
            if (enumFacing.getEnumFacing() == null) return;

            if (sameY && startY - 1 != Math.floor(targetBlock.yCoord)) return;

            doRotations();

            if (canPlace && (mode.getValue() == Mode.NoRotate
                    || RayCastUtils.overBlock(enumFacing.getEnumFacing(), blockFace, rayCast.getValue() == RayCast.Strict)
                    || rayCast.getValue() == RayCast.None)) {
                place(blockStack);
                ticksOnAir = 0;
            }
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre()) return;
        offset = new Vec3i(0, 0, 0);
        if (ncpTowered && event.getPosY() % 1.0D == 0.0D && ncpAirTicks == 3) event.setOnGround(true);

        boolean pose = mode.getValue() == Mode.Hypixel && hypixelPose.getValue();
        RotationProcess.showRotations = !pose;
        if (pose) {
            mc.thePlayer.rotationYawHead = mc.thePlayer.renderYawOffset = mc.thePlayer.rotationYaw + 125;
            mc.thePlayer.renderPitchHead = targetPitch;
        }
    };

    @EventLink
    public final Listener<StrafeEvent> onStrafe = event -> {
        if (!mc.gameSettings.keyBindJump.isPressed()) {
            jump();
            if (mode.getValue() == Mode.GodBridge && !autoJump.getValue() && blocksPlaced >= 8) {
                if (mc.thePlayer.onGround) mc.thePlayer.jump();
                blocksPlaced = 0;
            }
        }
    };

    @EventLink
    public final Listener<MoveEvent> moveEventListener = event -> {
        if (keepYMode.getValue() != KeepYMode.Off && mc.thePlayer.onGround && keepYState > 0 && MovementUtils.isMoving()) event.setJump(true);

        if (mc.gameSettings.keyBindJump.isKeyDown() && updatedNcpCanTower()) event.setJump(false);
        if (stop) {
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onPacketReceiveEvent = event -> {
        PacketUtils.correctBlockCount(event);
        if (event.getPacket() instanceof S12PacketEntityVelocity velocity && mc.thePlayer != null
                && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            ncpTowered = false;
        }

        if (autoJump.getValue() && event.getPacket() instanceof S02PacketChat
                && ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText().contains("You are not allowed to place blocks here!")) {
            NotificationManager.post(NotificationType.DISABLE, "Scaffold", "Disabled, the server won't let you place here.");
            this.toggle();
        }
    };

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = event -> {
        if (render.getValue()) {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2848);
            GL11.glDisable(2929);
            GL11.glDisable(3553);
            GlStateManager.disableCull();
            GL11.glDepthMask(false);
            final float red = ColorProcess.getColor().getRed();
            final float green = ColorProcess.getColor().getGreen();
            final float blue = ColorProcess.getColor().getBlue();
            float lineWidth = 0.0f;
            if (this.blockFace != null) {
                if (mc.thePlayer.getDistance(this.blockFace.getX(), this.blockFace.getY(), this.blockFace.getZ()) > 1.0) {
                    double d0 = 1.0 - mc.thePlayer.getDistance(this.blockFace.getX(), this.blockFace.getY(), this.blockFace.getZ()) / 20.0;
                    if (d0 < 0.3) {
                        d0 = 0.3;
                    }
                    lineWidth *= (float) d0;
                }
                RenderUtils.drawBlockESP(this.blockFace, red, green, blue, 0.3137255f, 1.0f, lineWidth);
            }
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glDepthMask(true);
            GlStateManager.enableCull();
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDisable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(2848);
        }
    };

    @EventLink
    public final Listener<KeyPressEvent> onKeyPress = event -> {
        if (rotations.getValue() != Rotations.Experiment) return;
        int key = event.getKey();
        if (key == Keyboard.KEY_RBRACKET) experiment.faster();
        else if (key == Keyboard.KEY_LBRACKET) experiment.slower();
        else if (key == Keyboard.KEY_BACKSLASH) experiment.reroll();
        else if (key == Keyboard.KEY_APOSTROPHE) experiment.markWorking();
        else if (key == Keyboard.KEY_SEMICOLON) experiment.markFlagged();
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        if (rotations.getValue() == Rotations.Experiment && experimentPanel.getValue()) experiment.drawPanel();
    };

    public ScaffoldModule() {
        button(experimentFaster, experiment::faster);
        button(experimentSlower, experiment::slower);
        button(experimentNew, experiment::reroll);
        button(experimentWorks, experiment::markWorking);
        button(experimentFlagged, experiment::markFlagged);
    }

    private static void button(Property<Boolean> property, Runnable action) {
        property.addValueChangeListener((was, now) -> {
            if (now) {
                property.setValue(false);
                action.run();
            }
        });
    }

    private void applyTimer() {
        if (timer.getValue()) {
            mc.timer.timerSpeed = timerSpeed.getValue().floatValue();
            timerApplied = true;
        } else if (timerApplied) {
            mc.timer.timerSpeed = 1.0f;
            timerApplied = false;
        }
    }

    private void tellyLogic() {
        if (!tellySafeTimer.hasTimeElapsed(400)) {
            tellyNoPlace = false;
            return;
        }

        if (mc.thePlayer.offGroundTicks == 0) {
            if (mc.thePlayer.onGroundTicks == 0) tellyNoPlace = true;
        } else if (!hypixelTelly.getValue()) {
            if (mc.gameSettings.keyBindJump.isKeyDown()) {
                if (mc.thePlayer.offGroundTicks >= tellyJumpDownTicks.getValue().intValue()) tellyNoPlace = false;
            } else if (isDiagonal()) {
                if (mc.thePlayer.offGroundTicks == tellyDiagonalTicks.getValue().intValue()) tellyNoPlace = false;
            } else if (mc.thePlayer.offGroundTicks == tellyStraightTicks.getValue().intValue()) {
                tellyNoPlace = false;
            }
        } else if (isDiagonal() ? mc.thePlayer.offGroundTicks <= 4 : mc.thePlayer.offGroundTicks <= 5) {
            tellyNoPlace = false;
        }
    }

    private boolean isDiagonal() {
        float delta = mc.thePlayer.rotationYaw % 90;
        if (delta < 0) delta += 90;
        return delta > 20 && delta < 70 || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
    }

    public void resetBinds() {
        resetBinds(true, true, true, true, true, true);
    }

    public void resetBinds(boolean sneak, boolean jump, boolean right, boolean left, boolean forward, boolean back) {
        if (sneak) mc.gameSettings.keyBindSneak.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
        if (jump) mc.gameSettings.keyBindJump.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
        if (right) mc.gameSettings.keyBindRight.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
        if (left) mc.gameSettings.keyBindLeft.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
        if (forward) mc.gameSettings.keyBindForward.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
        if (back) mc.gameSettings.keyBindBack.setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
    }

    private boolean lookingAtFace() {
        MovingObjectPosition over = mc.objectMouseOver;
        return over != null && over.sideHit == enumFacing.getEnumFacing() && blockFace.equals(over.getBlockPos());
    }

    private void tellyPlusRotations(float[] target) {
        TellyPreset preset = tellyPreset.getValue();
        boolean custom = preset == TellyPreset.Custom;
        boolean smooth = custom ? tellySmooth.getValue() : preset.smooth;
        Easing easing = custom ? tellyEasing.getValue() : preset.easing;
        int groundPhase = Math.max(1, custom ? tellyGroundTicks.getValue().intValue() : preset.groundTicks);
        Vector2f from = RotationProcess.lastRotations != null ? RotationProcess.lastRotations
                : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

        rotSpeed = 20.0f;

        int ground = mc.thePlayer.onGround ? mc.thePlayer.onGroundTicks : 0;
        if (ground >= 1 && ground <= groundPhase) {
            Vector2f forward = new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            aim(target, smooth ? ease(from, forward, easing, Math.min(1.0, ground / (double) groundPhase)) : forward);
            return;
        }

        if (ground == 0) {
            Vector2f found = faceRotation();
            if (found != null) {
                aim(target, smooth ? ease(from, found, easing, 0.5) : found);
                avoidDuplicateTurn(target, from);
                return;
            }
        }

        if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
            ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
        }
        avoidDuplicateTurn(target, from);
    }

    private Vector2f faceRotation() {
        EnumFacing face = enumFacing.getEnumFacing();
        boolean strict = rayCast.getValue() == RayCast.Strict;
        Vec3 hit = new Vec3(blockFace.getX() + 0.5 + face.getFrontOffsetX() * 0.45,
                blockFace.getY() + 0.5 + face.getFrontOffsetY() * 0.45,
                blockFace.getZ() + 0.5 + face.getFrontOffsetZ() * 0.45);
        Vector2f direct = RotationUtils.calculate(hit);
        if (RayCastUtils.overBlock(direct, face, blockFace, strict)) return direct;
        for (float pitch = 90; pitch >= 0; pitch--) {
            Vector2f candidate = new Vector2f(direct.x, pitch);
            if (RayCastUtils.overBlock(candidate, face, blockFace, strict)) return candidate;
        }
        return null;
    }

    private static void aim(float[] target, Vector2f rotation) {
        target[0] = rotation.x;
        target[1] = rotation.y;
    }

    private static Vector2f ease(Vector2f from, Vector2f to, Easing easing, double progress) {
        double amount = easing.ease(Math.max(0.0, Math.min(1.0, progress)));
        float yaw = from.x + (float) (MathHelper.wrapAngleTo180_float(to.x - from.x) * amount);
        float pitch = from.y + (float) ((to.y - from.y) * amount);
        return new Vector2f(yaw, pitch);
    }

    private void avoidDuplicateTurn(float[] target, Vector2f from) {
        if (!noDuplicateRot.getValue() || lastPlacedYawDelta < 0f || blockFace == null || enumFacing == null) return;
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        double step = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
        double turn = Math.abs(Math.round(MathHelper.wrapAngleTo180_float(target[0] - from.x) / step) * step);
        if (turn <= 2.0 || Math.abs(turn - lastPlacedYawDelta) >= 1.0E-4) return;

        boolean strict = rayCast.getValue() == RayCast.Strict;
        for (double shift : new double[]{step, -step}) {
            float yaw = (float) (target[0] + shift);
            if (RayCastUtils.overBlock(new Vector2f(yaw, target[1]), enumFacing.getEnumFacing(), blockFace, strict)) {
                target[0] = yaw;
                return;
            }
        }
    }

    private boolean nearEdge() {
        double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX, dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
        double distance = Math.hypot(dx, dz);
        double aheadX, aheadZ;
        if (distance > 0.01) {
            aheadX = dx / distance;
            aheadZ = dz / distance;
        } else {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            aheadX = -Math.sin(yaw);
            aheadZ = Math.cos(yaw);
        }
        BlockPos below = new BlockPos(mc.thePlayer.posX + aheadX * 1.2, mc.thePlayer.posY - 1, mc.thePlayer.posZ + aheadZ * 1.2);
        return mc.theWorld.getBlockState(below).getBlock() instanceof BlockAir;
    }

    public void doRotations() {
        if (mode.getValue() == Mode.NoRotate) return;

        if (keepYTakeoffTick) return;
        MovementFix movementFix = moveFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF;
        float[] target = {targetYaw, targetPitch};

        switch (mode.getValue()) {
            case Normal, GodBridge -> {
                mc.entityRenderer.getMouseOver(1);
                if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
                    ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
                }
                if (mode.getValue() == Mode.GodBridge) {
                    directionalChange++;
                    float lastYaw = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations.getX() : mc.thePlayer.rotationYaw;
                    if (Math.abs(MathHelper.wrapAngleTo180_double(target[0] - lastYaw)) > 10) {
                        directionalChange = (int) (Math.random() * 4);
                    }
                    mc.gameSettings.keyBindSneak.setPressed(directionalChange <= 10);
                }
            }
            case Rewound -> {
                mc.entityRenderer.getMouseOver(1);
                if (!mc.gameSettings.keyBindPickBlock.isKeyDown()) {
                    boolean hasBlock = canPlace && blockFace != null && enumFacing != null;
                    ExireRotations.compute(rewoundRotation.getValue(), hasBlock ? blockFace : null,
                            hasBlock ? enumFacing.getEnumFacing() : null, target);
                }
            }
            case Breezily -> {
                if (canPlace) {
                    if (enumFacing.getEnumFacing() == EnumFacing.UP) {
                        target[1] = 90;
                    } else {
                        target[0] = (float) ((Math.toDegrees(Math.atan2(enumFacing.getOffset().zCoord, enumFacing.getOffset().xCoord)) % 360) - 90);
                        target[1] = 80;
                    }
                }

                if (mc.gameSettings.keyBindForward.isKeyDown() && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    double off = 0, speed = 0;
                    switch (mc.thePlayer.getHorizontalFacing()) {
                        case NORTH -> {
                            off = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
                            speed = mc.thePlayer.motionZ;
                        }
                        case EAST -> {
                            off = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
                            speed = mc.thePlayer.motionX;
                        }
                        case SOUTH -> {
                            off = 1 - (mc.thePlayer.posX - Math.floor(mc.thePlayer.posX));
                            speed = mc.thePlayer.motionZ;
                        }
                        case WEST -> {
                            off = 1 - (mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ));
                            speed = mc.thePlayer.motionX;
                        }
                        default -> {
                        }
                    }
                    speed = Math.abs(speed);

                    if (!(speed < 0.086 && Math.abs(off - 0.5) < 0.4 && placeDelay.getValue().intValue() <= 1)) {
                        boolean right = off < 0.5 + ((Math.random() - 0.5) / 10);
                        mc.gameSettings.keyBindLeft.setPressed(!right);
                        mc.gameSettings.keyBindRight.setPressed(right);
                    }
                }
            }
            case Telly -> {
                if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
                    ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
                }

                if (!tellySafeTimer.hasTimeElapsed(400)) rotSpeed = 7.0f;

                mc.entityRenderer.getMouseOver(1);
                if (mc.thePlayer.onGround && (!mc.thePlayer.isSprinting() || mc.gameSettings.keyBindJump.isKeyDown())
                        && MovementUtils.isMoving() && (mc.gameSettings.keyBindJump.isKeyDown() || !mc.thePlayer.isSprinting())) {
                    rotSpeed = hypixelTelly.getValue() ? 10.0f : 20.0f;
                    if (tellySafeTimer.hasTimeElapsed(400)) target[0] = mc.thePlayer.rotationYaw;
                } else if (hypixelTelly.getValue()) {
                    rotSpeed = isDiagonal() ? 3.6f : mc.gameSettings.keyBindJump.isKeyDown() ? 3.45f : 2.8f;
                }
            }
            case Hypixel -> {
                rotSpeed = mc.thePlayer.isPotionActive(Potion.moveSpeed)
                        ? isDiagonal() ? 10.0f : 7.0f
                        : isDiagonal() || mc.gameSettings.keyBindJump.isKeyDown() ? 6.75f : 4.8f;
                if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown()) {
                    ScaffoldUtils.computeWatchdogRotations(blockFace, enumFacing, target);
                }
            }

            case SlowTelly -> {
                if (recursion == 0) {
                    if (mc.thePlayer.offGroundTicks <= 9 && mc.thePlayer.offGroundTicks > 3) {
                        mc.entityRenderer.getMouseOver(1);
                        if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
                            ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
                        }
                    } else {
                        target[0] = mc.thePlayer.rotationYaw;
                        target[1] = mc.thePlayer.rotationPitch;
                        canPlace = false;
                    }
                }
            }
            case FastTelly, TellyRapid -> {
                if (recursion == 0) {
                    mc.entityRenderer.getMouseOver(1);
                    if (mc.thePlayer.hurtTime == 0 && mc.thePlayer.onGround) {
                        target[0] = (float) Math.toDegrees(MovementUtils.direction());
                    }
                    if (mc.thePlayer.onGround && MovementUtils.isMoving()) {
                        target[0] = mc.thePlayer.rotationYaw;
                    } else if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
                        if (mode.getValue() == Mode.TellyRapid) rotSpeed = 3f;
                        ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
                    }
                }
            }
            case TellyPlus -> {
                if (recursion == 0) {
                    mc.entityRenderer.getMouseOver(1);
                    tellyPlusRotations(target);
                }
            }
            case TellyLock -> {
                if (recursion == 0) {
                    mc.entityRenderer.getMouseOver(1);
                    if (mc.thePlayer.onGround && MovementUtils.isMoving()) {
                        target[0] = (float) Math.toDegrees(MovementUtils.direction());
                    } else if (canPlace && !mc.gameSettings.keyBindPickBlock.isKeyDown() && !lookingAtFace()) {
                        ScaffoldUtils.computeNormalRotations(blockFace, enumFacing, target);
                    }
                }
            }
        }

        if (sprintMode.getValue() == SprintMode.Universal && blocksPlaced >= 3) {
            target[0] = mc.thePlayer.rotationYaw;
            blocksPlaced = 0;
        }

        if (rotations.getValue() == Rotations.Randomized && MovementUtils.isMoving() && blockFace != null && enumFacing != null) {
            float amount = (float) MathUtils.getRandom(randomizedSpeedMin.getValue().floatValue(), randomizedSpeedMax.getValue().floatValue());
            float candidateYaw = target[0] + (float) ((Math.random() - 0.5) * 2 * amount);
            float candidatePitch = target[1] + (float) ((Math.random() - 0.5) * 0.5 * amount);

            if (RayCastUtils.overBlock(new Vector2f(candidateYaw, candidatePitch), enumFacing.getEnumFacing(), blockFace, rayCast.getValue() == RayCast.Strict)) {
                target[0] = candidateYaw;
                target[1] = candidatePitch;
            }
        }

        targetYaw = target[0];
        targetPitch = target[1];

        if (keepYTellyActive && mc.thePlayer.motionY > 0.0 && keepYHoldTicks <= 1) {
            rotSpeed = Math.min(rotSpeed, (float) MathUtils.getRandom(30.0, 35.0) / 36.0f);
        }

        if (rotSpeed != 0 && blockFace != null && enumFacing != null) {
            RotationExperiment profile = rotations.getValue() == Rotations.Experiment ? experiment
                    : rotations.getValue() == Rotations.Grim2 ? grim2
                    : rotations.getValue() == Rotations.Grim3 ? grim3
                    : rotations.getValue() == Rotations.Grim4 ? grim4 : null;
            if (profile != null) {
                boolean strict = rayCast.getValue() == RayCast.Strict;
                EnumFacing face = enumFacing.getEnumFacing();
                Vector2f aim = new Vector2f(targetYaw, targetPitch);
                float speed = profile.apply(aim, rotSpeed, rotSpeed != baseRotSpeed, blockFace, face, strict);
                RotationProcess.setRotations(aim, speed, movementFix, profile.wander(blockFace, face, strict));
            } else if (rotations.getValue() == Rotations.ExhibitionWatchdog) {
                if (Float.isNaN(exhiLastYaw)) exhiLastYaw = mc.thePlayer.rotationYaw;
                float maxTurn = 35f + (float) (Math.random() * 10);
                exhiLastYaw += MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(targetYaw - exhiLastYaw), -maxTurn, maxTurn);
                float exhiPitch = Math.min(targetPitch, mc.gameSettings.keyBindJump.isKeyDown() ? 90f : 70f);
                RotationProcess.setRotations(new Vector2f(exhiLastYaw, exhiPitch), 360, movementFix);
            } else {
                RotationProcess.setRotations(new Vector2f(targetYaw, targetPitch), rotSpeed, movementFix);
            }
        }
    }

    private void place(ItemStack blockStack) {
        if (!canPlace || !delayTimer.hasTimeElapsed(placeDelay.getValue().longValue() * 20)) return;

        if (rayCast.getValue() == RayCast.Strict && mode.getValue() != Mode.NoRotate) {
            MovingObjectPosition over = mc.objectMouseOver;
            if (over != null && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, blockStack, over.getBlockPos(), over.sideHit, over.hitVec)) {
                PacketUtils.sendPacket(new C0APacketAnimation());
                updatedNcpPlaced(over.getBlockPos().offset(over.sideHit));
            }
        } else {
            Vec3 hitVec = ScaffoldUtils.computeHitVec(blockFace, enumFacing);
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, blockStack, blockFace, enumFacing.getEnumFacing(), hitVec)) {
                PacketUtils.sendPacket(new C0APacketAnimation());
                updatedNcpPlaced(blockFace.offset(enumFacing.getEnumFacing()));
            }
        }
        blocksPlaced++;
        if (mode.getValue() == Mode.TellyPlus) {
            if (mc.thePlayer.onGround) tellyGroundPlaced++;
            if (RotationProcess.rotations != null && RotationProcess.lastRotations != null) {
                lastPlacedYawDelta = Math.abs(MathHelper.wrapAngleTo180_float(RotationProcess.rotations.x - RotationProcess.lastRotations.x));
            }
        }
        placeTimer = 10;
        delayTimer.reset();
    }

    public void jump() {
        if (mc.gameSettings.keyBindJump.isKeyDown() || Tempest.INSTANCE.getModuleManager().getModule(SpeedModule.class).isEnabled()) return;

        if (mode.getValue() == Mode.TellyPlus) {
            if (mc.thePlayer.onGround && MovementUtils.isMoving() && mc.thePlayer.onGroundTicks >= 1 && (nearEdge() || tellyGroundPlaced >= 2)) {
                mc.thePlayer.jump();
                tellyGroundPlaced = 0;
            }
            return;
        }

        if (mode.getValue().telly() && !autoJump.getValue()) autoJump.setValue(true);

        if (autoJump.getValue() && mc.thePlayer.onGround && MovementUtils.isMoving()) mc.thePlayer.jump();
    }

    private void updateKeepYMode() {
        keepYTakeoffTick = false;
        if (keepYHoldTicks > 0) keepYHoldTicks--;
        if (keepYMode.getValue() == KeepYMode.Off) {
            keepYState = 0;
            keepYTellyActive = false;
            return;
        }
        if (mc.thePlayer.onGround) {
            if (keepYState > 0) keepYState--;
            if (keepYState == 0 && !mc.gameSettings.keyBindJump.isKeyDown() && !mc.thePlayer.isPotionActive(Potion.jump)) keepYState = 1;
            keepYTellyActive = false;
        }
        if (keepYTellyTakeoff()) {
            Vector2f last = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations
                    : new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            float turn = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - last.x);
            float yaw = snapToMouse(last.x + turn * (float) MathUtils.getRandom(0.98, 0.99), last.x);
            float pitch = snapToMouse((float) MathUtils.getRandom(30.0, 80.0), last.y);
            RotationProcess.setRotations(new Vector2f(yaw, pitch), 10, moveFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
            keepYHoldTicks = 3;
            keepYTellyActive = true;
            keepYTakeoffTick = true;
        }
    }

    private boolean keepYTellyTakeoff() {
        if (keepYMode.getValue() != KeepYMode.Telly || keepYState <= 0 || !mc.thePlayer.onGround || !MovementUtils.isMoving()) return false;
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0, 1.0, 0.0)).isEmpty();
    }

    private static float snapToMouse(float target, float reference) {
        float change = MathHelper.wrapAngleTo180_float(target - reference);
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float step = sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;
        return reference + Math.round(change / step) * step;
    }

    private void sprint() {
        if (keepYMode.getValue() == KeepYMode.Vanilla && keepYState > 0 || keepYTellyTakeoff()) return;

        if (mode.getValue() == Mode.NoRotate) {
            if (mc.gameSettings.keyBindForward.isKeyDown() && !mc.thePlayer.isSneaking()
                    && (mc.thePlayer.getFoodStats().getFoodLevel() > 6 || mc.thePlayer.capabilities.allowFlying)) {
                mc.thePlayer.setSprinting(true);
            }
            return;
        }
        if (mode.getValue() == Mode.Hypixel && sprintMode.getValue() != SprintMode.Legit) sprintMode.setValue(SprintMode.Legit);

        switch (sprintMode.getValue()) {
            case Vanilla -> mc.thePlayer.setSprinting(MovementUtils.isMoving());
            case NCP -> {
                mc.thePlayer.setSprinting(false);
                MovementUtils.strafe(MovementUtils.getBaseMoveSpeed() / 1.84f);
            }
            case None -> {
                mc.gameSettings.keyBindSprint.setPressed(false);
                mc.thePlayer.setSprinting(false);
            }
            case Universal, Legit -> {
                if (!moveFix.getValue() && RotationProcess.rotations != null
                        && Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - MathHelper.wrapAngleTo180_float(RotationProcess.rotations.x)) > 90) {
                    mc.gameSettings.keyBindSprint.setPressed(false);
                    mc.thePlayer.setSprinting(false);
                }
            }
        }
    }

    private void sneak() {
        if (!sneak.getValue()) return;
        if (blocksPlaced >= sneakEvery.getValue().intValue() && !mc.gameSettings.keyBindSneak.isPressed()) {
            mc.gameSettings.keyBindSneak.setPressed(true);
            blocksPlaced = 0;
        } else {
            mc.gameSettings.keyBindSneak.setPressed(false);
        }
    }

    private boolean updatedNcpCanTower() {
        return towerMode.getValue() == TowerMode.UpdatedNCP
                && (towerMove.getValue() || !MovementUtils.isMoving())
                && totalBlocks() > 0 && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isPotionActive(Potion.jump) && ticksSinceTeleport > 60;
    }

    @EventLink
    public final Listener<PostStrafeEvent> onPostStrafe = event -> {
        if (towerMode.getValue() != TowerMode.UpdatedNCP) {
            ncpTowered = false;
            return;
        }
        ncpAirTicks = mc.thePlayer.onGround ? 0 : ncpAirTicks + 1;
        if (!updatedNcpCanTower()) {
            ncpTowered = false;
            return;
        }

        boolean holding = mc.gameSettings.keyBindJump.isKeyDown();
        if (ncpTowered) {
            if (!holding || mc.thePlayer.onGround || mc.thePlayer.isCollidedVertically) {
                ncpTowered = false;
            } else if (ncpAirTicks == 1) {
                mc.thePlayer.motionY -= ncpMotionRemoval;
            } else if (ncpAirTicks == 2) {
                double above = mc.thePlayer.posY - Math.floor(mc.thePlayer.posY);
                double toNextBlock = 1.0D - above;
                if (above > toNextBlock) mc.thePlayer.motionY = toNextBlock;
            }
        }
        if (!ncpTowered && mc.thePlayer.onGround && holding) {
            mc.thePlayer.motionY = 0.41999998688697815D;
            ncpTowered = true;
        }
        if (ncpTowered) {
            if (!MovementUtils.isMoving()) {
                MovementUtils.setSpeed(0.0D);
            } else if (ncpAirTicks <= 1) {
                MovementUtils.setSpeed(MovementUtils.getBaseMoveSpeed(0.2872D));
            }
        }
    };

    @EventLink
    public final Listener<TeleportEvent> onTeleport = event -> ticksSinceTeleport = 0;

    private void updatedNcpPlaced(BlockPos placed) {
        if (!ncpTowered || !mc.gameSettings.keyBindJump.isKeyDown() || !updatedNcpCanTower()) return;
        double above = mc.thePlayer.posY - Math.floor(mc.thePlayer.posY);
        if (placed.getY() == (int) Math.floor(mc.thePlayer.posY) - 1 && above <= 0.001335979112141672D
                && mc.thePlayer.getEntityBoundingBox().addCoord(0.0D, -0.0625D, 0.0D)
                .intersectsWith(new AxisAlignedBB(placed, placed.add(1, 1, 1)))) {
            mc.thePlayer.motionY = -0.0784000015258789D;
            mc.thePlayer.onGround = true;
            ncpAirTicks = 0;
            ncpTowered = false;
        }
    }

    public void tower() {
        if (towerMode.getValue() == TowerMode.None || !mc.gameSettings.keyBindJump.isKeyDown() || !PlayerUtils.isBlockUnder(2, true)) return;
        if (!towerMove.getValue() && MovementUtils.isMoving()) return;

        switch (towerMode.getValue()) {
            case NCP -> {
                if (mc.thePlayer.posY % 1.0D <= 0.00153598D) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ);
                    mc.thePlayer.motionY = 0.41998D;
                } else if (mc.thePlayer.posY % 1.0D < 0.1D && mc.thePlayer.onGround) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ);
                }
            }
            case Vanilla -> mc.thePlayer.motionY = 0.42;
            case Hypixel -> {
                Block below = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.getPositionVector().subtract(0, 1, 0))).getBlock();
                if (mc.thePlayer.isPotionActive(Potion.jump) || (below != null && !below.isFullBlock())) return;

                boolean sprintInput = Math.abs(mc.thePlayer.moveForward) >= 0.8F || Math.abs(mc.thePlayer.moveStrafing) >= 0.8F;
                if (mc.thePlayer.onGround && !sprintInput) mc.thePlayer.jump();

                boolean still = !MovementUtils.isMoving() && Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ) == 0.0;
                if (mc.thePlayer.offGroundTicks == 4 && still) mc.thePlayer.motionY -= 0.03;
                if (mc.thePlayer.offGroundTicks == 5 && still) mc.thePlayer.motionY -= 0.5;
            }
            case Polar -> {
                double ground = mc.thePlayer.posY - groundBelow(mc.thePlayer);
                boolean moved = Math.hypot(mc.thePlayer.posX - mc.thePlayer.prevPosX, mc.thePlayer.posZ - mc.thePlayer.prevPosZ) != 0;
                if (!moved && mc.thePlayer.motionY < 0 && ground < 1.26) mc.thePlayer.motionY -= 0.091F;
            }
            default -> {
            }
        }
    }

    private static double groundBelow(EntityPlayer player) {
        for (int i = 0; i < 50; i++) {
            BlockPos pos = new BlockPos(player.getPositionVector().subtract(0, i, 0));
            if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockAir)) return pos.getY();
        }
        return 0;
    }

    private int totalBlocks() {
        if (mc.thePlayer == null) return 0;
        int total = 0;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s != null && s.getItem() instanceof ItemBlock) total += s.stackSize;
        }
        return total;
    }

    private float blockFraction(int count) {
        if (count > blockMax) blockMax = count;
        return blockMax <= 0 ? 0f : Math.max(0f, Math.min(1f, count / (float) blockMax));
    }

    private final class CounterOverlay {
        @EventLink
        public final Listener<Render2DEvent> onRender2D = event -> {
            updateBlockCounter();
            renderBlockCounter(true);
        };

        @EventLink
        public final Listener<ShaderEvent> onShader = event -> renderBlockCounter(false);
    }

    private static final class Smooth {
        private float value = Float.NaN;

        float to(float target, float rate, float dt) {
            value = Float.isNaN(value) ? target : value + (target - value) * Math.min(1f, dt * rate);
            return value;
        }

        float get() {
            return Float.isNaN(value) ? 0f : value;
        }

        void reset() {
            value = Float.NaN;
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    private static boolean visible(float alpha) {
        return alpha * 255f >= 5f;
    }

    private static void drawFaded(CustomFontRenderer font, String text, float x, float y, int rgb, float alpha) {
        if (!visible(alpha)) return;
        float shadowAlpha = alpha * (200f / 255f);
        if (visible(shadowAlpha)) font.drawString(text, x + 1f, y + 1f, argb((rgb & 0xFCFCFC) >> 2, shadowAlpha));
        font.drawString(text, x, y, argb(rgb, alpha));
    }

    private void updateBlockCounter() {
        long now = System.currentTimeMillis();
        counterDt = counterLastMs == 0L ? 0f : Math.min(0.1f, (now - counterLastMs) / 1000f);
        counterLastMs = now;

        boolean on = isEnabled() && blockCounter.getValue() != BlockCounter.None && mc.thePlayer != null;
        counterShow = Math.max(0f, Math.min(1f, counterShow + (on ? counterDt : -counterDt) / 0.25f));
        if (counterShow <= 0f) {
            barFill.reset();
            alertLevel.reset();
            badgeWidth.reset();
            rollWidth.reset();
            pillWidth.reset();
            rollShown = -1;
            segLit = -1;
            return;
        }

        if (isEnabled() && mc.thePlayer != null) {
            counterBlocks = totalBlocks();
            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            counterHeld = held != null && held.getItem() instanceof ItemBlock ? held.stackSize : 0;
        }

        float target = blockFraction(counterBlocks);
        barFill.to(target, 8f, counterDt);

        if (counterBlocks != rollShown) {
            String digits = Integer.toString(counterBlocks);
            boolean down = counterBlocks < rollShown;
            for (int i = 0; i < ROLL_DIGITS; i++) {
                char next = i < digits.length() ? digits.charAt(digits.length() - 1 - i) : ' ';
                if (rollShown < 0) {
                    rollTo[i] = next;
                    rollAt[i] = 0L;
                } else if (next != rollTo[i]) {
                    rollFrom[i] = rollTo[i];
                    rollTo[i] = next;
                    rollAt[i] = now;
                    rollDown[i] = down;
                }
            }
            rollShown = counterBlocks;
        }

        int lit = (int) Math.ceil(target * SEGMENTS - 1e-4f);
        if (segLit >= 0) {
            for (int i = lit; i < segLit; i++) segEmptiedAt[i] = now;
        }
        segLit = lit;
    }

    private void renderBlockCounter(boolean live) {
        if (counterShow <= 0f || mc.thePlayer == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f, cy = sr.getScaledHeight() / 2f;
        float show = easeOut(counterShow);
        float dt = live ? counterDt : 0f;
        Color accent = ColorProcess.getColor();

        switch (blockCounter.getValue()) {
            case Clean -> renderClean(cx, cy + 10f, show, accent);
            case Badge -> renderBadge(cx, cy + 8f, show, accent, dt);
            case Alert -> renderAlert(cx, cy, show, dt);
            case Outline -> renderOutline(cx, cy + 13f, show, accent);
            default -> {
            }
        }
    }

    private void renderClean(float cx, float y, float scale, Color accent) {
        CustomFontRenderer font = FontProcess.getFont("bold");
        String num = String.valueOf(counterHeld);
        String suffix = " block" + (counterHeld != 1 ? "s" : "");
        float numW = font.getStringWidth(num);
        float w = numW + font.getStringWidth(suffix);
        float fh = font.getHeight();
        float x = cx - w / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y + fh / 2f, 0f);
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.translate(-cx, -(y + fh / 2f), 0f);
        font.drawStringWithShadow(num, x, y, accent.getRGB());
        font.drawStringWithShadow(suffix, x + numW, y, new Color(205, 205, 205).getRGB());
        GlStateManager.popMatrix();
    }

    private void renderBadge(float cx, float y, float show, Color accent, float dt) {
        CustomFontRenderer bold = FontProcess.getFont("bold"), regular = FontProcess.getFont("tempest");
        String num = String.valueOf(counterBlocks);
        float numW = bold.getStringWidth(num), labelW = regular.getStringWidth("Blocks");
        float textW = numW + 2f + labelW;
        float w = badgeWidth.to(textW + 6f, 14f, dt), h = 15f;
        float top = y + (1f - show) * 5f;

        RenderUtils.drawRoundedRect(cx - w / 2f, top, w, h, 3f, new Color(15, 15, 18, Math.round(170 * show)));
        if (!visible(show)) return;
        float tx = cx - textW / 2f, ty = top + h / 2f - bold.getHeight() / 2f + 0.5f;
        drawFaded(bold, num, tx, ty, accent.getRGB(), show);
        drawFaded(regular, "Blocks", tx + numW + 2f, ty, 0xFFFFFF, show);
    }

    private static final int[] ALERT_COLORS = {0xFFFFFF, 0xFFFF55, 0xFFAA00, 0xFF5555};

    private void renderAlert(float cx, float cy, float show, float dt) {
        if (!visible(show)) return;
        CustomFontRenderer bold = FontProcess.getFont("bold"), regular = FontProcess.getFont("tempest");
        int n = counterBlocks;
        float danger = n <= 5 ? 3f : n <= 15 ? 2f : n <= 25 ? 1f : 0f;
        float level = Math.max(0f, Math.min(3f, alertLevel.to(danger, 6f, dt)));
        int step = Math.min(2, (int) level);
        Color from = new Color(ALERT_COLORS[step]), to = new Color(ALERT_COLORS[step + 1]);
        int numColor = RenderUtils.interpolateColorC(from, to, level - step).getRGB();

        String num = String.valueOf(n);
        float x = cx + 11f - (1f - show) * 4f;
        float y = cy - bold.getHeight() / 2f + 0.5f;
        drawFaded(bold, num, x, y, numColor, show);
        drawFaded(regular, n == 1 ? " block" : " blocks", x + bold.getStringWidth(num), y, 0xFFFFFF, show);
    }

    // Outline: Yuri's bar (AGPL-3.0) — a thin rounded track edged in a deep shade of your colour,
    // with a slim fill that drains as you place. Fades in rather than scaling, since the outline
    // shader works in screen space.
    private void renderOutline(float cx, float y, float show, Color accent) {
        float width = 80f, height = 4f;
        float x = cx - width / 2f, top = y + (1f - show) * 4f;
        Color edge = RenderUtils.applyOpacity(accent.darker().darker(), show);

        RenderUtils.drawRoundOutline(x, top, width, height, 1.25f, -0.4f, new Color(0, 0, 0, Math.round(130 * show)), edge);
        float fw = (width - 2f) * Math.max(0f, Math.min(1f, barFill.get()));
        if (fw > 0.25f) Gui.drawRect(x + 1f, top + 1.25f, x + 1f + fw, top + 2.75f, argb(accent.getRGB(), show));
    }

    private static void drawDigit(CustomFontRenderer font, char digit, float cellX, float cell, float y, float alpha) {
        if (!visible(alpha)) return;
        String s = String.valueOf(digit);
        drawFaded(font, s, cellX + (cell - font.getStringWidth(s)) / 2f, y, 0xFFFFFF, alpha);
    }

    @Override
    public void onEnable() {
        if (!counterSubscribed) {
            Tempest.INSTANCE.getEventBus().subscribe(counterOverlay);
            counterSubscribed = true;
        }
        experiment.resetTickState();
        grim2.resetTickState();
        grim3.resetTickState();
        grim4.resetTickState();
        exhiLastYaw = Float.NaN;
        ncpTowered = false;
        ncpAirTicks = 0;
        ncpMotionRemoval = 7.5E-4D + Math.random() * 5.0E-4D;
        ticksSinceTeleport = 100;
        if (mc.thePlayer != null) {
            targetYaw = mc.thePlayer.rotationYaw - 180;
            targetPitch = 90;
            startY = Math.floor(mc.thePlayer.posY);
            targetBlock = null;
            tellyNoPlace = false;
            if (hypixelTelly.getValue() && mode.getValue() == Mode.Telly) rotSpeed = 7.0f;
            blockMax = 0;
        }
        tellySafeTimer.reset();
        recursions = 0;
        placeTimer = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.safeWalk = false;
            mc.timer.timerSpeed = 1.0f;
            timerApplied = false;
            SlotProcess.swapBack();
            stop = false;
            tellySafeTimer.reset();
            blocksPlaced = 0;
            tellyNoPlace = false;
            placeTimer = 0;
        }
        RotationProcess.showRotations = true;
        ncpTowered = false;
        resetBinds();
        super.onDisable();
    }
}
