package rip.tempest.modules.impl.player;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.movement.FlightModule;
import rip.tempest.modules.impl.movement.LongJumpModule;
import rip.tempest.modules.impl.visuals.FreecamModule;
import rip.tempest.processes.LagProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.block.BlockAir;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vector3d;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Anti Void", category = ModuleCategory.PLAYER)
public final class AntiVoidModule extends Module {
    public ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Position);
    private final NumberProperty distance = new NumberProperty("Distance", 5, () -> mode.getValue() != Mode.Blink && mode.getValue() != Mode.Exhibition
            && mode.getValue() != Mode.Radium && mode.getValue() != Mode.SetBack, 0, 10, 1);

    private final Property<Boolean> voidOnly = new Property<>("Void Only", true, () -> mode.getValue() == Mode.Exhibition || mode.getValue() == Mode.SetBack);

    private final NumberProperty setBackHeight = new NumberProperty("Height", 2, () -> mode.getValue() == Mode.SetBack && !voidOnly.getValue(), 1, 10, 1);
    private final NumberProperty fallDistance = new NumberProperty("Fall Distance", 2, () -> mode.getValue() == Mode.Exhibition, 2, 10, 0.1);

    private final ModeProperty<RadiumCatch> radiumCatch = new ModeProperty<>("Catch", RadiumCatch.Packet, () -> mode.getValue() == Mode.Radium);
    private final NumberProperty radiumDistance = new NumberProperty("Rewound Distance", 5, () -> mode.getValue() == Mode.Radium, 3, 10, 0.5, Representation.DISTANCE);

    {
        renamedSetting("Rewound Distance", "Radium Distance");
    }

    public enum RadiumCatch {
        Packet, Motion
    }

    public enum Mode {
        Position,
        Packet,
        Collision,
        Blink,

        Exhibition,

        Radium,

        SetBack;

        @Override
        public String toString() {
            return this == Radium ? "Rewound" : name();
        }
    }

    private boolean saveMe;
    private final Timer timer = new Timer();

    private double[] lastSafePos;

    @Override
    public void onEnable() {
        saveMe = false;
        lastSafePos = null;
        super.onEnable();
    }

    @EventLink
    public final Listener<rip.tempest.api.events.impl.world.WorldLoadEvent> setBackWorld = e -> lastSafePos = null;

    @EventLink
    public final Listener<MotionEvent> setBackMotion = event -> {
        if (mode.getValue() != Mode.SetBack || mc.thePlayer == null || mc.theWorld == null) return;

        if (event.isPre() && mc.thePlayer.onGround && !astolfoOverVoid() && mc.thePlayer.isCollidedVertically && !onEdge()) {
            lastSafePos = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        }

        double fall = voidOnly.getValue() ? 3.0D : setBackHeight.getValue().intValue();
        if (mc.thePlayer.fallDistance > fall
                && !Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class).isEnabled()
                && !Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class).isEnabled()
                && (!voidOnly.getValue() || astolfoOverVoid())
                && !mc.thePlayer.onGround && !mc.thePlayer.isCollidedVertically
                && lastSafePos != null) {
            mc.thePlayer.setPosition(lastSafePos[0], lastSafePos[1], lastSafePos[2]);
        }
    };

    private static boolean astolfoOverVoid() {
        for (int y = (int) mc.thePlayer.posY; y >= 0; y--) {
            if (mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ)).getBlock() != Blocks.air) return false;
        }
        return true;
    }

    private static boolean onEdge() {
        double x = mc.thePlayer.motionX, z = mc.thePlayer.motionZ;
        double startX = x, startZ = z, step = 0.05D;
        while (x != 0.0D && mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(x, -1.0D, 0.0D)).isEmpty()) {
            x = shrink(x, step);
        }
        while (z != 0.0D && mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, -1.0D, z)).isEmpty()) {
            z = shrink(z, step);
        }
        while (x != 0.0D && z != 0.0D && mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(x, -1.0D, z)).isEmpty()) {
            x = shrink(x, step);
            z = shrink(z, step);
        }
        return startX != x || startZ != z;
    }

    private static double shrink(double v, double step) {
        if (v < step && v >= -step) return 0.0D;
        return v > 0.0D ? v - step : v + step;
    }

    @EventLink
    public final Listener<MovePlayerEvent> exhibitionMove = e -> {
        if (mode.getValue() != Mode.Exhibition || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.capabilities.allowFlying) return;
        if (isBlockUnder() && !saveMe && Tempest.INSTANCE.getModuleManager().getModule(NoFallModule.class).isEnabled() || mc.thePlayer.capabilities.isFlying) return;
        if (mc.thePlayer.isCollidedVertically) {
            saveMe = false;
            return;
        }

        if (mc.thePlayer.fallDistance >= fallDistance.getValue()
                && !Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class).isEnabled()
                && !Tempest.INSTANCE.getModuleManager().getModule(LongJumpModule.class).isEnabled()
                && !Tempest.INSTANCE.getModuleManager().getModule(FreecamModule.class).isEnabled()
                && mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ)).getBlock() == Blocks.air
                && !isBlockUnder()) {
            e.setX(mc.thePlayer.motionX = 0);
            e.setY(mc.thePlayer.motionY = 0);
            e.setZ(mc.thePlayer.motionZ = 0);
            saveMe = true;
            timer.reset();
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> exhibitionSend = e -> {
        if (mode.getValue() != Mode.Exhibition || !saveMe || e.isCancelled()) return;
        if (e.getPacket() instanceof C03PacketPlayer) {
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer(false));
            timer.reset();
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> exhibitionReceive = e -> {
        if (mode.getValue() != Mode.Exhibition || mc.thePlayer == null) return;
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            if (mc.thePlayer.fallDistance > 0) mc.thePlayer.fallDistance = 0;
            mc.thePlayer.motionX = mc.thePlayer.motionZ = 0;
            saveMe = false;
        }
    };

    private boolean isBlockUnder() {
        if (!voidOnly.getValue()) return false;
        double[][] offsets = {{0, 0}, {-0.35, -0.35}, {-0.35, 0.35}, {0.35, 0.35}, {0.35, -0.35}};
        for (int y = (int) mc.thePlayer.posY; y >= 0; y--) {
            for (double[] offset : offsets) {
                if (!(mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX + offset[0], y, mc.thePlayer.posZ + offset[1])).getBlock() instanceof BlockAir)) return true;
            }
        }
        return false;
    }

    private final Timer radiumTimer = new Timer();

    @EventLink
    public final Listener<MotionEvent> radiumMotion = event -> {
        if (mode.getValue() != Mode.Radium || !event.isPre()) return;
        if (!Tempest.INSTANCE.getModuleManager().getModule(FlightModule.class).isEnabled()
                && mc.thePlayer.fallDistance > radiumDistance.getValue().floatValue()
                && !MovementUtils.isOnGround()
                && radiumTimer.hasTimeElapsed(500L)
                && overVoid()) {
            if (radiumCatch.getValue() == RadiumCatch.Packet) {
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                        event.getPosX(), event.getPosY() + 11.0D + StrictMath.random(), event.getPosZ(),
                        event.getYaw(), event.getPitch(), false));
            } else if (mc.thePlayer.motionY < 0.0D) {
                mc.thePlayer.motionY = 2.2F;
            }
            mc.thePlayer.fallDistance = 0.0F;
            radiumTimer.reset();
        }
    };

    private static boolean overVoid() {
        for (double y = mc.thePlayer.posY; y > 0.0D; y--) {
            if (!(mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ)).getBlock() instanceof BlockAir)) return false;
        }
        return true;
    }

    private Vector3d position, motion;
    private Vector2f rotation;

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (mode.getValue() == Mode.Blink) {
            if (event.isPre()) return;
            if (mc.thePlayer.ticksExisted <= 60) return;

            if (position != null && motion != null && rotation != null && !PlayerUtils.isBlockUnder(50, true)) {
                if (mc.thePlayer.fallDistance > 4) {
                    mc.thePlayer.setPosition(position.x, position.y, position.z);
                    mc.thePlayer.motionX = 0;
                    mc.thePlayer.motionY = MovementUtils.predictedMotion(motion.y);
                    mc.thePlayer.motionZ = 0;
                    mc.thePlayer.rotationYaw = rotation.x;
                    mc.thePlayer.rotationPitch = rotation.y;
                    mc.thePlayer.fallDistance = 0;
                    LagProcess.packets.removeIf(timedPacket -> !(timedPacket.getPacket() instanceof C0FPacketConfirmTransaction || timedPacket.getPacket() instanceof C00PacketKeepAlive));
                    LagProcess.disable();
                    LagProcess.dispatch();
                }
            } else {
                position = new Vector3d(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                motion = new Vector3d(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
                rotation = new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            }
        }
        if (mode.getValue() == Mode.Position) {
            if (!event.isPre()) return;
            if (mc.thePlayer.fallDistance > distance.getValue().floatValue() && !PlayerUtils.isBlockUnder()) {
                event.setPosY(event.getPosY() + mc.thePlayer.fallDistance);
            }
        }
        if (mode.getValue() == Mode.Packet) {
            if (!event.isPre()) return;
            if (mc.thePlayer.fallDistance > distance.getValue().floatValue() && !PlayerUtils.isBlockUnder()) {
                PacketUtils.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition());
            }
        }
        if (mode.getValue() == Mode.Collision) {
            if (!event.isPre()) return;
            if (mc.thePlayer.fallDistance > distance.getValue().intValue() && !PlayerUtils.isBlockUnder() && mc.thePlayer.posY + mc.thePlayer.motionY < Math.floor(mc.thePlayer.posY)) {
                mc.thePlayer.motionY = Math.floor(mc.thePlayer.posY) - mc.thePlayer.posY;
                if (mc.thePlayer.motionY == 0) {
                    mc.thePlayer.onGround = true;
                    event.setOnGround(true);
                }
            }
        }
    };
}
