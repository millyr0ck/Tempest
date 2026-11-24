package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.game.EntityHurtSoundEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.SoundUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

@ModuleInfo(label = "Hit Sounds", category = ModuleCategory.CLIENT)
public class HitSoundsModule extends Module {

    public static ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Skeet);

    public enum Mode {
        Skeet("Skeet"),
        Felix("Felix"),
        UltraKill("UltraKill"),
        OniChan("Oni Chan");

        public String name;
        Mode(String name) {
            this.name = name;
        }
        public String toString() {
            return name;
        }
    }

    @EventLink
    public final Listener<EntityHurtSoundEvent> entityHurtSoundEventListener = event -> {

        event.setCancelled();

        switch (mode.getValue()) {
            case Skeet:
                SoundUtils.playSound("skeet.wav");
                break;
            case Felix:
                SoundUtils.playSound("felix.wav");
                break;
            case UltraKill:
                SoundUtils.playSound("ultrakill.wav");
                break;
            case OniChan:
                SoundUtils.playSound("onichan.wav");
                break;
        }
    };

}
