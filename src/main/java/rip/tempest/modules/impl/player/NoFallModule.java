package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.player.nofall.ExhibitionNoFall;
import rip.tempest.modules.impl.player.nofall.GrimServer19NoFall;
import rip.tempest.modules.impl.player.nofall.WatchdogNoFall;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.InventoryUtils;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "No Fall", category = ModuleCategory.PLAYER)
public class NoFallModule extends Module {
    public ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Clutch);

    private final Property<Boolean> newestGrim = new Property<>("Newest Grim (May Flag)", false, () -> mode.getValue() == Mode.GrimServer19);

    private final Property<Boolean> exhibitionHypixel = new Property<>("Hypixel (Strip Rotation)", false, () -> mode.getValue() == Mode.Exhibition);
    private final Property<Boolean> exhibitionFastFall = new Property<>("Fast Fall", false, () -> mode.getValue() == Mode.Exhibition);

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.GrimServer19, new GrimServer19NoFall(newestGrim))
            .add(Mode.Watchdog, new WatchdogNoFall())
            .add(Mode.Exhibition, new ExhibitionNoFall(exhibitionHypixel, exhibitionFastFall));

    private enum Mode {
        Vanilla,
        Vulcan,
        Clutch,
        Edit,
        Edit2,
        Round,

        GrimServer19("Grim Server 1.9+"),

        Watchdog,

        Exhibition("Rewound");

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

    public Timer timer = new Timer();
    private boolean timered = false;
    public boolean canWork = false;
    public boolean pickup = false;

    @EventLink
    private final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (mode.getValue() == Mode.Vanilla) {
            if (mc.thePlayer.fallDistance >= 3) {
                PacketUtils.sendPacket(new C03PacketPlayer(true));
            }
        }
        if (mode.getValue() == Mode.Edit) {
            if (mc.thePlayer.fallDistance >= 3) {
                e.setOnGround(false);
            }
        }
        if (mode.getValue() == Mode.Edit2) {
            if (mc.thePlayer.fallDistance >= 3) {
                e.setOnGround(true);
            }
        }
        if (mode.getValue() == Mode.Round) {
            if (mc.thePlayer.fallDistance >= 3) {
                e.setPosY(e.getPosY() * Math.round(e.getPosY()));
            }
        }
    };

    @EventLink
    private final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (mode.getValue() == Mode.Clutch) {
            if (mc.thePlayer.fallDistance > 2.9f) {
                int item = InventoryUtils.getBucketSlot();
                if (item == -1) {
                    item = InventoryUtils.getCobwebSlot();
                }
                if (item == -1) {
                    if (canWork && !pickup) {
                        RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 10, MovementFix.NORMAL);
                        canWork = false;
                        pickup = false;
                        return;
                    }
                } else {
                    mc.thePlayer.inventory.currentItem = item;
                    RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, 90.0f), 10, MovementFix.NORMAL);
                    canWork = true;
                    if (!mc.thePlayer.isInWater() && !mc.thePlayer.isInWeb && !pickup && mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 2.0, mc.thePlayer.posZ)).getBlock() != Blocks.water && mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 2.0, mc.thePlayer.posZ)).getBlock() != Blocks.air) {
                        mc.rightClickMouse();
                        pickup = true;
                        timer.reset();
                    }
                }
            } else {
                if (!canWork) {
                    return;
                }
                if (mc.thePlayer.isInWater() && pickup) {
                    mc.rightClickMouse();
                    pickup = false;
                } else {
                    RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 10, MovementFix.NORMAL);
                    canWork = false;
                    pickup = false;
                }
                if (timer.hasTimeElapsed(150.0, false)) {
                    RotationProcess.setRotations(new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 10, MovementFix.NORMAL);
                    canWork = false;
                    pickup = false;
                    return;
                }
            }
        }
    };

    @EventLink
    private final Listener<PacketSendEvent> packetSendEventListener = e -> {
        if (mode.getValue() == Mode.Vulcan) {
            if (e.getPacket() instanceof C03PacketPlayer && mc.thePlayer.fallDistance > 3) {
                C03PacketPlayer packet = (C03PacketPlayer) e.getPacket();
                packet.onGround = true;
                mc.thePlayer.fallDistance = 0;
                mc.thePlayer.setVelocity(0, 0, 0);
            }
        }
    };

    @EventLink
    private final Listener<WorldLoadEvent> worldLoadEventListener = e -> {
        if (timered) {
            timered = false;
            mc.timer.timerSpeed = 1;
        }
        if(pickup || canWork) {
            pickup = false;
            canWork = false;
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
        if (timered) {
            timered = false;
            mc.timer.timerSpeed = 1;
        }
        super.onDisable();
    }
}
