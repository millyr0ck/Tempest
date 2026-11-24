package rip.tempest.modules.impl.client;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

@ModuleInfo(label = "Discord RPC", category = ModuleCategory.CLIENT)
public class DiscordRPCModule extends Module {
    private static final String APPLICATION_ID = "1140815918478409770";

    @Override
    public void onEnable() {
        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder()
                .setReadyEventHandler(user -> System.out.println("[Discord RPC] Logged in as: " + user.username + " " + user.userId))
                .build();

        DiscordRPC.discordInitialize(APPLICATION_ID, handlers, true);

        Runtime.getRuntime().addShutdownHook(new Thread(DiscordRPC::discordShutdown));
    }
    
    @Override
    public void onDisable() {
        DiscordRPC.discordClearPresence();
    }

    @EventLink
    public final Listener<TickEvent> onTick = event -> {
        DiscordRichPresence presence = new DiscordRichPresence
                .Builder("making kids tempest since 2025..")
                .setStartTimestamps(Tempest.getStartTime())
                .setDetails("Tempest [ " + Tempest.VERSION + " ]")
                .setBigImage("icon", "https://github.com/x0lumie/Simp")
                .build();

        DiscordRPC.discordUpdatePresence(presence);
    };

}
