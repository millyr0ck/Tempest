package rip.tempest.interfaces.minigame.cases;

import rip.tempest.utils.render.knife.KnifeSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.DoubleSupplier;

public final class CaseScreen extends GuiScreen {
    private static CaseScreen open;

    private final DoubleSupplier volume;
    private final Runnable onClosed;
    private final long startedAt = System.nanoTime();
    private CaseOpening opening;
    private CaseGraphics graphics;
    private KnifeSounds sounds;
    private boolean hideGuiBefore;
    private boolean started;

    public CaseScreen(final DoubleSupplier volume, final Runnable onClosed) {
        this.volume = volume;
        this.onClosed = onClosed;
    }

    public static boolean hidesHud() {
        return open != null;
    }

    public static File folder() {
        return new File(Minecraft.getMinecraft().mcDataDir, "Tempest/cases");
    }

    public static boolean available() {
        return new File(folder(), "cases.json").isFile();
    }

    @Override
    public void initGui() {
        if (started) return;
        started = true;
        final File root = folder();
        try {
            final List<CaseData.Container> containers = CaseData.load(new File(root, "cases.json"));
            graphics = new CaseGraphics(font(root, "stratum2-regular-webfont", "roboto"), font(root, "stratum2-medium-webfont", "robotomedium"));
            sounds = new KnifeSounds(new File(root, "sounds"));
            opening = new CaseOpening(root, containers, graphics, this::play, sounds::stop);
        } catch (final IOException | RuntimeException | java.awt.FontFormatException exception) {
            System.err.println("Case Opening: could not start: " + exception);
            mc.displayGuiScreen(null);
            return;
        }
        hideGuiBefore = mc.gameSettings.hideGUI;
        mc.gameSettings.hideGUI = true;
        open = this;
    }

    private static Font font(final File root, final String file, final String name) throws IOException, java.awt.FontFormatException {
        final File own = new File(root, "fonts/" + file + ".ttf");
        if (own.isFile()) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, own);
            } catch (final IOException | java.awt.FontFormatException exception) {
                System.err.println("Case Opening: could not read " + own + ": " + exception);
            }
        }
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("tempest/fonts/" + name + ".ttf")).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        }
    }

    private void play(final String event) {
        final float gain = (float) volume.getAsDouble() * mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        if (sounds != null && gain > 0) sounds.playSingle(event, gain);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        if (opening == null) return;
        opening.render(mc.displayWidth, mc.displayHeight, (System.nanoTime() - startedAt) / 1e9, Mouse.getX(), mc.displayHeight - Mouse.getY() - 1);
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        if (opening != null && mouseButton == 0) opening.click(Mouse.getX(), mc.displayHeight - Mouse.getY() - 1);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && opening != null) opening.scroll(wheel);
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_SPACE && opening != null) opening.skip();
        if (keyCode != Keyboard.KEY_ESCAPE) return;
        if (opening == null || !opening.escape()) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        if (open == this) {
            open = null;
            mc.gameSettings.hideGUI = hideGuiBefore;
        }
        if (sounds != null) sounds.stop();
        if (graphics != null) graphics.dispose();
        opening = null;
        graphics = null;
        onClosed.run();
    }
}
