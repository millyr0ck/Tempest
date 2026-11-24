package rip.tempest.processes;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.client.ViaMCPFixes;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

public class BackgroundProcess {
    private Timer cfgTimer = new Timer();

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (this.cfgTimer.hasTimeElapsed(30000, true)) {
            Tempest.INSTANCE.getConfigManager().saveConfigInBackground("default");
            Tempest.INSTANCE.getBindsConfig().saveToFileInBackground();
        }

        DraggingProcess.update();
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = e -> {
        ViaMCPFixes.initialized = false;
    };
}
