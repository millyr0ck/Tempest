package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.DisablerModule;
import rip.tempest.modules.impl.client.disabler.HoverDisabler;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.render.FreecamEntity;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Freecam", category = ModuleCategory.VISUALS)
public final class FreecamModule extends Module {
    private static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Normal);
    private final NumberProperty speed = new NumberProperty("Speed", 1.0, 0.1, 5.0, 0.1);

    public enum Mode { Normal, Hypixel }

    private FreecamEntity camera;

    private double frozenX, frozenY, frozenZ;

    private final List<Vec3> recordedPath = new ArrayList<>();
    private boolean wasDisablerEnabled = false;

    @Override
    public void onEnable() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            setEnabled(false);
            return;
        }

        frozenX = mc.thePlayer.posX;
        frozenY = mc.thePlayer.posY;
        frozenZ = mc.thePlayer.posZ;

        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        camera = new FreecamEntity(mc.theWorld, frozenX, eyeY, frozenZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        mc.setRenderViewEntity(camera);

        recordedPath.clear();
        wasDisablerEnabled = false;

        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null) {
            mc.setRenderViewEntity(mc.thePlayer);
        }
        camera = null;
        recordedPath.clear();
        wasDisablerEnabled = false;
        super.onDisable();
    }

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        if (isEnabled()) setEnabled(false);
    };

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = event -> {
        if (mc.thePlayer == null || camera == null) return;

        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionY = 0;
        mc.thePlayer.motionZ = 0;

        flyCamera();

        if (mode.getValue() == Mode.Hypixel) {
            handleHypixelRecording();
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre() || mc.thePlayer == null) return;

        mc.thePlayer.setPosition(frozenX, frozenY, frozenZ);
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionY = 0;
        mc.thePlayer.motionZ = 0;

        event.setPosX(frozenX);
        event.setPosY(frozenY);
        event.setPosZ(frozenZ);
        event.setOnGround(true);
    };

    private void handleHypixelRecording() {
        DisablerModule disabler = Tempest.INSTANCE.getModuleManager().getModule(DisablerModule.class);
        if (disabler == null) return;

        boolean disablerEnabled = disabler.isEnabled();

        if (disablerEnabled && HoverDisabler.freePhase) {
            double feetY = camera.posY - mc.thePlayer.getEyeHeight();
            Vec3 pos = new Vec3(camera.posX, feetY, camera.posZ);

            if (recordedPath.isEmpty() || pos.squareDistanceTo(recordedPath.get(recordedPath.size() - 1)) > 0.25) {
                recordedPath.add(pos);
            }
            setSuffix("Rec " + recordedPath.size());
        }

        if (wasDisablerEnabled && !disablerEnabled && !recordedPath.isEmpty()) {
            flushPath();
        }

        wasDisablerEnabled = disablerEnabled;
    }

    private void flushPath() {
        for (Vec3 pos : recordedPath) {
            PacketUtils.sendSilentPacket(
                    new C03PacketPlayer.C04PacketPlayerPosition(pos.xCoord, pos.yCoord, pos.zCoord, false));
        }

        Vec3 last = recordedPath.get(recordedPath.size() - 1);
        frozenX = last.xCoord;
        frozenY = last.yCoord;
        frozenZ = last.zCoord;
        mc.thePlayer.setPosition(frozenX, frozenY, frozenZ);

        recordedPath.clear();
        setSuffix("Hypixel");
    }

    private void flyCamera() {
        camera.lastTickPosX = camera.prevPosX = camera.posX;
        camera.lastTickPosY = camera.prevPosY = camera.posY;
        camera.lastTickPosZ = camera.prevPosZ = camera.posZ;

        camera.prevRotationYaw = mc.thePlayer.prevRotationYaw;
        camera.rotationYaw = mc.thePlayer.rotationYaw;
        camera.prevRotationPitch = mc.thePlayer.prevRotationPitch;
        camera.rotationPitch = mc.thePlayer.rotationPitch;

        double forward = 0.0;
        double strafe = 0.0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) forward += 1.0;
        if (mc.gameSettings.keyBindBack.isKeyDown()) forward -= 1.0;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) strafe += 1.0;
        if (mc.gameSettings.keyBindRight.isKeyDown()) strafe -= 1.0;

        double vertical = 0.0;
        if (mc.gameSettings.keyBindJump.isKeyDown()) vertical += 1.0;
        if (mc.gameSettings.keyBindSneak.isKeyDown()) vertical -= 1.0;

        double moveSpeed = speed.getValue();

        double dx = 0.0;
        double dz = 0.0;
        double magnitude = forward * forward + strafe * strafe;
        if (magnitude > 1.0E-4) {
            double normalize = 1.0 / Math.sqrt(magnitude);
            forward *= normalize;
            strafe *= normalize;

            double yawRad = Math.toRadians(mc.thePlayer.rotationYaw);
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            dx = (strafe * cos - forward * sin) * moveSpeed;
            dz = (forward * cos + strafe * sin) * moveSpeed;
        }
        double dy = vertical * moveSpeed;

        camera.setPosition(camera.posX + dx, camera.posY + dy, camera.posZ + dz);
    }
}
