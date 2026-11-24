package rip.tempest.modules.impl.visuals;

import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.Glyphs;
import net.minecraft.client.Minecraft;

import java.awt.Color;

@ModuleInfo(label = "Streamer Mode", category = ModuleCategory.VISUALS)
public class NickHiderModule extends Module {
    public static final char GRADIENT_START = 'z', GRADIENT_END = 'y';
    private static final String START = "§" + GRADIENT_START, END = "§" + GRADIENT_END;

    private final Property<String> fakeName = new Property<>("Fake Name", "x0lumie");
    private final Property<Boolean> clientColors = new Property<>("Client Colors", true);

    private static NickHiderModule instance;
    private static volatile boolean active;

    public NickHiderModule() {
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        super.onDisable();
    }

    private static String realName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.getSession() != null ? mc.getSession().getUsername() : null;
    }

    private String fake() {
        String name = fakeName.getValue();
        return name == null || name.isEmpty() ? "Player" : name;
    }

    public static String displayName() {
        String real = realName();
        if (active && instance != null) return instance.fake();
        return real != null ? real : "";
    }

    public static boolean hides(String name) {
        return active && instance != null && name != null && name.equals(realName());
    }

    public static String replace(String text) {
        if (!active || instance == null || text == null || text.isEmpty()) return text;
        String real = realName();
        if (real == null || real.isEmpty() || text.indexOf(real) < 0) return text;
        String fake = instance.fake();
        return text.replace(real, instance.clientColors.getValue() ? START + fake + END : fake);
    }

    public static int gradientColor(int index, int length) {
        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        float position = length <= 1 ? 0f : index / (float) (length - 1);
        double phase = (System.currentTimeMillis() % 1_000_000L) / 260.0;
        return Glyphs.waveColor(first, second, position, phase).getRGB() & 0xFFFFFF;
    }
}
