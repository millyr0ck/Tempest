package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.visuals.ArmorHUDModule;
import rip.tempest.modules.impl.visuals.ArrayListModule;
import rip.tempest.modules.impl.visuals.GreetingModule;
import rip.tempest.modules.impl.visuals.InfoDisplayModule;
import rip.tempest.modules.impl.visuals.InfoModule;
import rip.tempest.modules.impl.visuals.PotionHUDModule;
import rip.tempest.modules.impl.visuals.RadarModule;
import rip.tempest.modules.impl.visuals.SessionInformationModule;
import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.replay.ReplayRecorder;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Replay", category = ModuleCategory.CLIENT, description = "Records your games as replays")
public final class ReplayModule extends Module {
    private static final Property<Boolean> multiplayer = new Property<>("Record Multiplayer", true);
    private static final Property<Boolean> singleplayer = new Property<>("Record Singleplayer", true);
    private static final Property<Boolean> indicator = new Property<>("Indicator", true);

    private static final Property<Boolean> hideArrayList = new Property<>("Hide Array List", true);
    private static final Property<Boolean> hideWatermark = new Property<>("Hide Watermark", true);
    private static final Property<Boolean> hideOtherHud = new Property<>("Hide Other HUD", false);

    public static final ModeProperty<Overlay> overlay = new ModeProperty<>("Overlay", Overlay.BAR);

    public static final Property<Boolean> followYou = new Property<>("Follow You", true);
    public static final ModeProperty<Projectiles> followProjectiles = new ModeProperty<>("Follow Projectiles", Projectiles.ALL);

    public static final NumberProperty landingPause = new NumberProperty("Landing Pause", 0.4,
            () -> followProjectiles.getValue() != Projectiles.OFF, 0, 3, 0.1);

    public static final ModeProperty<Resolution> renderSize = new ModeProperty<>("Render Size", Resolution.FULL_HD);
    public static final ModeProperty<FrameRate> renderFps = new ModeProperty<>("Render FPS", FrameRate.FPS_60);
    public static final ModeProperty<Quality> renderQuality = new ModeProperty<>("Render Quality", Quality.HIGH);
    public static final ModeProperty<Format> renderFormat = new ModeProperty<>("Render Format", Format.MP4);

    private static final List<Class<? extends Module>> OTHER_HUD = List.of(InfoDisplayModule.class, SessionInformationModule.class,
            TargetInterfaceModule.class, PotionHUDModule.class, ArmorHUDModule.class, RadarModule.class, GreetingModule.class);

    public enum Overlay {
        BAR("Playback bar"), CLEAN("No bar"), NOTHING("No HUD");

        private final String label;

        Overlay(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Projectiles {
        OFF("Off"), ARROWS("Arrows"), ALL("Arrows and throwables"), EVERYTHING("Everything (and rods)");

        private final String label;

        Projectiles(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Resolution {
        WINDOW("Window", 0, 0), HD("1280x720", 1280, 720), FULL_HD("1920x1080", 1920, 1080),
        QHD("2560x1440", 2560, 1440), UHD("3840x2160", 3840, 2160);

        private final String label;
        public final int width, height;

        Resolution(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum FrameRate {
        FPS_24(24), FPS_30(30), FPS_60(60), FPS_120(120);

        public final int fps;

        FrameRate(int fps) {
            this.fps = fps;
        }

        @Override
        public String toString() {
            return fps + " fps";
        }
    }

    public enum Quality {
        HIGH("High", 16), NORMAL("Normal", 20), SMALL("Small", 26);

        private final String label;
        public final int crf;

        Quality(String label, int crf) {
            this.label = label;
            this.crf = crf;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Format {
        MP4("MP4"), PNG("PNG frames");

        private final String label;

        Format(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static ReplayModule instance;

    public ReplayModule() {
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    public static boolean recordMultiplayer() {
        return multiplayer.getValue();
    }

    public static boolean recordSingleplayer() {
        return singleplayer.getValue();
    }

    public static boolean hidesInReplays(Module module) {
        if (module instanceof ArrayListModule) return hideArrayList.getValue();
        if (module instanceof InfoModule) return hideWatermark.getValue();
        return hideOtherHud.getValue() && OTHER_HUD.contains(module.getClass());
    }

    @Override
    public void onEnable() {
        Thread recovery = new Thread(() -> {
            int recovered = rip.tempest.replay.ReplayRecorder.recoverLeftovers();
            if (recovered > 0) mc.addScheduledTask(() -> NotificationManager.post(NotificationType.INFO, "Replay",
                    "Recovered " + recovered + " unfinished replay" + (recovered == 1 ? "" : "s")));
        }, "Replay recovery");
        recovery.setDaemon(true);
        recovery.start();
        if (mc.theWorld != null && !ReplayRecorder.recording()) {
            NotificationManager.post(NotificationType.INFO, "Replay", "Recording starts when you next join a world");
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        ReplayRecorder.stopRecording();
        super.onDisable();
    }

    @EventLink
    public final Listener<TickEvent> tickListener = e -> ReplayRecorder.onTick();

    @EventLink
    public final Listener<Render2DEvent> render2DListener = e -> {
        if (!indicator.getValue() || !ReplayRecorder.recording()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int seconds = ReplayRecorder.elapsed() / 1000;
        String text = String.format("REC %d:%02d", seconds / 60, seconds % 60);
        int width = mc.fontRendererObj.getStringWidth(text) + 9;
        int x = sr.getScaledWidth() / 2 - width / 2, y = 4;

        if (System.currentTimeMillis() % 1000 < 700) Gui.drawRect(x, y + 1, x + 5, y + 6, 0xFFE53935);
        mc.fontRendererObj.drawStringWithShadow(text, x + 9, y, 0xFFFFFFFF);
    };
}
