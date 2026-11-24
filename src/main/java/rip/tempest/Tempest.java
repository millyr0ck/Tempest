package rip.tempest;

import rip.tempest.api.commands.CommandHandler;
import rip.tempest.api.commands.impl.*;
import rip.tempest.api.config.BindsConfig;
import rip.tempest.api.config.ConfigManager;
import rip.tempest.api.events.Event;
import rip.tempest.api.events.impl.game.ClientStartupEvent;
import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.modules.ModuleManager;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.*;
import rip.tempest.utils.client.BuildType;
import de.florianmichael.viamcp.ViaMCP;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import io.github.nevalackin.homoBus.bus.impl.EventBus;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class Tempest {
    public static final Tempest INSTANCE = new Tempest();
    public static final String NAME = "Tempest";
    public static final String BUILD = BuildType.RELEASE.getName();
    public static final String VERSION = "6.7" + " " + BUILD;
    public static final String FULL = NAME + " " + VERSION;

    private EventBus<Event> eventBus;
    @Getter
    private ModuleManager moduleManager;
    @Getter
    private ConfigManager configManager;
    @Getter
    private BindsConfig bindsConfig;
    @Getter
    private CommandHandler commandHandler;
    private BackgroundProcess backgroundProcess;
    private RotationProcess rotationProcess;
    private ColorProcess colorProcess;
    private rip.tempest.interfaces.click.exhibition.ExhibitionClickInterface exhibitionClickInterface;
    private LagProcess lagProcess;
    private BlinkProcess blinkProcess;
    private BadPacketsProcess badPacketsProcess;
    private PacketDelayProcess packetDelayProcess;
    private TargetSelectionProcess targetSelectionProcess;
    @Getter
    private static long startTime;

    private Tempest() {
        getEventBus().subscribe(this);
    }

    @EventLink
    public final Listener<ClientStartupEvent> onClientStart = e -> {
        startTime = System.currentTimeMillis();
        moduleManager = new ModuleManager();
        moduleManager.postInit();
        configManager = new ConfigManager();
        getEventBus().subscribe(configManager);
        bindsConfig = new BindsConfig();
        getEventBus().subscribe(bindsConfig);
        backgroundProcess = new BackgroundProcess();
        getEventBus().subscribe(backgroundProcess);
        rotationProcess = new RotationProcess();
        getEventBus().subscribe(rotationProcess);
        colorProcess = new ColorProcess();
        getEventBus().subscribe(colorProcess);
        configManager.loadConfig("default");
        bindsConfig.loadFromFile();
        lagProcess = new LagProcess();
        getEventBus().subscribe(lagProcess);
        blinkProcess = new BlinkProcess();
        getEventBus().subscribe(blinkProcess);
        badPacketsProcess = new BadPacketsProcess();
        getEventBus().subscribe(badPacketsProcess);
        packetDelayProcess = new PacketDelayProcess();
        getEventBus().subscribe(packetDelayProcess);
        targetSelectionProcess = new TargetSelectionProcess();
        getEventBus().subscribe(targetSelectionProcess);
        getEventBus().subscribe(RotationLearnerProcess.INSTANCE);
        commandHandler = new CommandHandler();
        commandHandler.commands.addAll(Arrays.asList(
                new BindCommand(),
                new ClientNameCommand(),
                new BindsCommand(),
                new ToggleCommand(),
                new ConfigCommand(),
                new HideCommand(),
                new VClipCommand(),
                new HClipCommand(),
                new TeleportCommand(),
                new DisguiseCommand(),
                new RotationCommand(),
                new RotsCommand(),
                new HelpCommand(),
                new FriendCommand(),
                new rip.tempest.api.commands.impl.SpawnCommand()
        ));
        getEventBus().subscribe(commandHandler);

        if (moduleManager.getModule(ScaffoldModule.class).isEnabled()) {
            moduleManager.getModule(ScaffoldModule.class).setEnabled(false);
        }

        try {
            ViaMCP.create();
            ViaMCP.INSTANCE.initAsyncSlider();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    };

    @EventLink
    public final Listener<KeyPressEvent> keyPressEventListener = e -> {
        if (e.getKey() == ClientSettingsModule.menuKey.getValue().intValue()) {
            switch (ClientSettingsModule.clickInterface.getValue()) {
                case Tempest -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.tempest.TempestClickInterface());
                case Exhibition -> {
                    if (exhibitionClickInterface == null) {
                        exhibitionClickInterface = new rip.tempest.interfaces.click.exhibition.ExhibitionClickInterface();
                    }
                    Minecraft.getMinecraft().displayGuiScreen(exhibitionClickInterface);
                }
                case Bankroll -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.bankroll.BankrollClickInterface());
                case Astolfo -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.astolfo.AstolfoClickInterface());
                case Storm -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.storm.StormClickInterface());
                case Tiles -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.flat.TilesClickInterface());
                case Columns -> Minecraft.getMinecraft().displayGuiScreen(new rip.tempest.interfaces.click.flat.ColumnsClickInterface());
            }
        }
    };

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> {
        if(!Tempest.INSTANCE.getModuleManager().getModule(ClientSettingsModule.class).isEnabled()) Tempest.INSTANCE.getModuleManager().getModule(ClientSettingsModule.class).setEnabled(true);

        String currentFont = FontProcess.getCurrentFont().getNameFontTTF().toLowerCase();
        String desiredFont = ClientSettingsModule.font.getValue().toString().toLowerCase();

        if (!currentFont.equals(desiredFont)) {
            FontProcess.setCurrentFont(desiredFont);
        }
    };

    public EventBus<Event> getEventBus() {
        if (eventBus == null) {
            eventBus = new EventBus<>();
        }

        return eventBus;
    }

    public static <T> T requireNonNull(T obj) {
        if (obj == null) {
            throw new IllegalArgumentException();
        }
        return obj;
    }
}
