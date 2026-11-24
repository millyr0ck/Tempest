package rip.tempest.modules.impl.movement;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.player.BlockCollideEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.SubModes;
import rip.tempest.modules.impl.movement.phase.ExhibitionHypixelPhase;
import rip.tempest.modules.impl.movement.phase.GrimPhase;
import rip.tempest.modules.impl.movement.phase.MoonWatchdogPhase;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.PlayerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockAir;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Phase", category = ModuleCategory.MOVEMENT)
public final class PhaseModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);
    private final ModeProperty<GrimPhase.Release> releaseMode = new ModeProperty<>("Release Mode", GrimPhase.Release.Simple, () -> mode.getValue() == Mode.Grim);
    private final NumberProperty semiPackets = new NumberProperty("Semi Packets", 2, () -> mode.getValue() == Mode.Grim, 1, 15, 1);

    private final SubModes<Mode> subModes = new SubModes<>(this, mode, Mode.class)
            .add(Mode.Grim, new GrimPhase(releaseMode, semiPackets, () -> setEnabled(false)))
            .add(Mode.MoonWatchdog, new MoonWatchdogPhase(false))
            .add(Mode.ExhibitionHypixel, new ExhibitionHypixelPhase());

    private enum Mode {
        Normal("Normal", false),
        Grim("Grim", false),

        MoonWatchdog("Watchdog", true),
        ExhibitionHypixel("Hypixel", false);

        private final String name;
        private final boolean noPush;

        Mode(String name, boolean noPush) {
            this.name = name;
            this.noPush = noPush;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static boolean stopsPushOut() {
        PhaseModule module = Tempest.INSTANCE.getModuleManager().getModule(PhaseModule.class);
        return module != null && module.isEnabled() && module.mode.getValue().noPush;
    }

    private boolean phasing;

    @Override
    public void onEnable() {
        subModes.enable();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        subModes.disable();
        super.onDisable();
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        setSuffix(mode.getValue().toString());
        if(!event.isPre() || mode.getValue() != Mode.Normal) return;
        this.phasing = false;

        final double rotation = Math.toRadians(mc.thePlayer.rotationYaw);

        final double x = Math.sin(rotation);
        final double z = Math.cos(rotation);

        if (mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.setPosition(mc.thePlayer.posX - x * 0.005, mc.thePlayer.posY, mc.thePlayer.posZ + z * 0.005);
            this.phasing = true;
        } else if (PlayerUtils.insideBlock()) {
            PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX - x * 1.5, mc.thePlayer.posY, mc.thePlayer.posZ + z * 1.5, false));

            mc.thePlayer.motionX *= 0.3D;
            mc.thePlayer.motionZ *= 0.3D;

            this.phasing = true;
        }
    };

    @EventLink
    public final Listener<BlockCollideEvent> blockCollideEventListener = event -> {
        if (mode.getValue() != Mode.Normal) return;
        if (event.getBlock() instanceof BlockAir && phasing) {
            final double x = event.getX(), y = event.getY(), z = event.getZ();

            if (y < mc.thePlayer.posY) {
                event.setCollisionBoundingBox(AxisAlignedBB.fromBounds(-15, -1, -15, 15, 1, 15).offset(x, y, z));
            }
        }
    };
}
