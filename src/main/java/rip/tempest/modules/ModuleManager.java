package rip.tempest.modules;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.modules.impl.client.*;
import rip.tempest.modules.impl.combat.*;
import rip.tempest.modules.impl.movement.*;
import rip.tempest.modules.impl.player.*;
import rip.tempest.modules.impl.visuals.*;
import com.google.common.collect.ImmutableClassToInstanceMap;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class ModuleManager {
    private final ImmutableClassToInstanceMap<Module> instanceMap;

    public ModuleManager() {
        instanceMap = putInInstanceMap(

                new AuraModule(),
                new DisplaceModule(),
                new TPAuraModule(),
                new SnapModule(),
                new TargetStrafeModule(),
                new CriticalsModule(),
                new TickBaseModule(),
                new TimerRangeModule(),
                new LagRangeModule(),
                new VelocityModule(),
                new BackTrackModule(),
                new WTapModule(),
                new HitSelectionModule(),
                new AutoClickerModule(),
                new FastBowModule(),
                new KeepSprintModule(),
                new AutoPotModule(),
                new AutoHeadModule(),
                new NoHitDelayModule(),

                new SprintModule(),
                new SpeedModule(),
                new FlightModule(),
                new LongJumpModule(),
                new NoSlowModule(),
                new NoWebModule(),
                new SafeWalkModule(),
                new StepModule(),
                new JesusModule(),
                new LiquidModule(),
                new NoJumpDelayModule(),
                new PhaseModule(),
                new SpiderModule(),

                new ScaffoldModule(),
                new AutoAuthenticateModule(),
                new NoFallModule(),
                new AntiVoidModule(),
                new ClutchModule(),
                new WaterBucketModule(),
                new AutoToolModule(),
                new FastUseModule(),
                new RegenModule(),
                new FastPlaceModule(),
                new StealerModule(),
                new NotebotModule(),
                new ManagerModule(),
                new AutoArmorModule(),
                new InvMoveModule(),
                new BedNukerModule(),
                new BridgeAssistModule(),
                new NoFireballModule(),
                new PingSpoofModule(),

                new JennyModule(),
                new ClientSettingsModule(),
                new AntiBotModule(),
                new MCFModule(),
                new ReplayModule(),
                new DisablerModule(),
                new FakeLagModule(),
                new BypassModule(),
                new NoC03Module(),
                new SpooferModule(),
                new AutoGGModule(),
                new AutoPlayModule(),
                new TimerModule(),
                new BlinkModule(),
                new NoRotateModule(),
                new MurderDetectorModule(),
                new BasketballModule(),
                new PoolModule(),
                new EmoteModule(),

                new RadarModule(),
                new ArrayListModule(),
                new InfoModule(),
                new InfoDisplayModule(),
                new NotificationsModule(),
                new CustomCapeModule(),
                new CosmeticsModule(),
                new ItemPhysicsModule(),
                new GlintModule(),
                new DamageParticles(),
                new BedPlatesModule(),
                new PlayerModelModule(),
                new PlayerlistModule(),
                new AnimationsModule(),
                new InspectModule(),
                new TracersModule(),
                new AmbienceModule(),
                new ESPModule(),
                new ChamsModule(),
                new ParticlesModule(),
                new KillEffectModule(),
                new HitmarkersModule(),
                new BedWarsStatsModule(),
                new PitModule(),
                new TargetInterfaceModule(),
                new ScoreboardModule(),
                new PostProcessingModule(),
                new GlowESPModule(),
                new NickHiderModule(),
                new NameTagsModule(),
                new StorageESPModule(),
                new ChinaHatModule(),
                new TrajectoriesModule(),
                new FullBrightModule(),
                new FreeLookModule(),
                new FreecamModule(),
                new ItemESPModule(),
                new ArmorHUDModule(),
                new PotionHUDModule(),
                new CaseOpeningModule(),
                new BlockInModule()
        );
        getModules().forEach(Module::reflectProperties);

        getModules().forEach(Module::resetPropertyValues);

        Tempest.INSTANCE.getEventBus().subscribe(this);
    }

    @EventLink
    public final Listener<KeyPressEvent> onKeyPress = event -> {
        final int keyPressed = event.getKey();
        for (final Module module : this.getModules()) {
            final int moduleBind = module.getKey();
            if (moduleBind == keyPressed && !rip.tempest.replay.ReplayViewer.blocks(module)) {
                module.toggle();
            }
        }
    };

    public void postInit() {
        getModules().forEach(Module::resetPropertyValues);
    }

    private ImmutableClassToInstanceMap<Module> putInInstanceMap(Module... modules) {
        ImmutableClassToInstanceMap.Builder<Module> modulesBuilder = ImmutableClassToInstanceMap.builder();
        Arrays.stream(modules).forEach(module -> modulesBuilder.put((Class<Module>) module.getClass(), module));
        return modulesBuilder.build();
    }

    public Collection<Module> getModules() {
        return instanceMap.values();
    }

    public <T extends Module> T getModule(Class<T> moduleClass) {
        return instanceMap.getInstance(moduleClass);
    }

    public Module getModule(String label) {
        return getModules().stream().filter(module -> module.getLabel().replaceAll(" ", "").equalsIgnoreCase(label)).findFirst().orElse(null);
    }

    public static <T extends Module> T getInstance(Class<T> clazz) {
        return Tempest.INSTANCE.getModuleManager().getModule(clazz);
    }

    private final java.util.Map<ModuleCategory, List<Module>> categoryCache = new java.util.EnumMap<>(ModuleCategory.class);

    public List<Module> getModulesForCategory(ModuleCategory category) {
        if (category == null) {
            return getModules().stream().filter(module -> module.getCategory() == null).collect(Collectors.toList());
        }
        List<Module> cached = categoryCache.get(category);
        if (cached == null) {
            cached = getModules().stream()
                    .filter(module -> module.getCategory() == category)
                    .collect(Collectors.toList());
            categoryCache.put(category, cached);
        }
        return new java.util.ArrayList<>(cached);
    }
}
