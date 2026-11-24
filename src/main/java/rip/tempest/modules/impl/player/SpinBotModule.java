package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import org.lwjgl.util.vector.Vector2f;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Spin Bot", category = ModuleCategory.PLAYER)
public final class SpinBotModule extends Module {

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Fake);
    private final Property<Boolean> movementFix = new Property<>("Movement Fix", false, () -> mode.getValue() == Mode.Server);

    private int yaw;

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        yaw += 10 % 360;
        switch (mode.getValue()) {
            case Fake -> mc.thePlayer.renderYawOffset = yaw;
            case Server ->
                    RotationProcess.setRotations(new Vector2f(yaw, mc.thePlayer.rotationPitch), 10, movementFix.getValue() ? MovementFix.NORMAL : MovementFix.OFF);
            case Player -> mc.thePlayer.rotationYaw = yaw;
        }
    };

    public enum Mode {
        Fake,
        Server,
        Player
    }
}
