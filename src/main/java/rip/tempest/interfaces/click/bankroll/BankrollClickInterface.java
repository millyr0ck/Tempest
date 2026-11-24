package rip.tempest.interfaces.click.bankroll;

import rip.tempest.modules.impl.client.ClientSettingsModule;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.InputStream;
import java.util.ArrayDeque;

public final class BankrollClickInterface extends GuiScreen {
    private static BankrollFont font;
    private static BankrollUi ui;
    private static boolean fontFailed;
    private static final BankrollMenu menu = new BankrollMenu();
    private static final BankrollGLCanvas canvas = new BankrollGLCanvas();

    private final ArrayDeque<int[]> buttonEvents = new ArrayDeque<>();
    private boolean leftDown;
    private float wheel;

    private static boolean load() {
        if (ui != null) return true;
        if (fontFailed) return false;
        try (InputStream png = mc().getResourceManager().getResource(new ResourceLocation("tempest/fonts/bankroll/glyphs.png")).getInputStream();
             InputStream table = mc().getResourceManager().getResource(new ResourceLocation("tempest/fonts/bankroll/glyphs.txt")).getInputStream()) {
            font = BankrollFont.load(png, table);
            ui = new BankrollUi(font);
            return true;
        } catch (Exception e) {
            fontFailed = true;
            e.printStackTrace();
            return false;
        }
    }

    private static net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getMinecraft();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonEvents.clear();
        leftDown = Mouse.isButtonDown(0);
        wheel = 0;
        menu.onOpen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!load()) {
            mc.displayGuiScreen(null);
            return;
        }
        int width = mc.displayWidth, height = mc.displayHeight;

        if (mc.theWorld == null) rip.tempest.interfaces.menu.MenuBackdrop.draw(this.width, this.height);

        float mx = Mouse.getX(), my = height - Mouse.getY() - 1;
        boolean clicked = false, released = false;
        int[] event = buttonEvents.poll();
        if (event != null) {
            if (event[0] == 1) {
                clicked = !leftDown;
                leftDown = true;
            } else {
                released = leftDown;
                leftDown = false;
            }
            mx = event[1];
            my = height - event[2] - 1;
        }

        ui.newFrame(width, height, mx, my, leftDown, clicked, released, wheel);
        wheel = 0;
        menu.draw(ui);
        ui.endFrame();

        GlStateManager.pushMatrix();
        double unit = 1.0 / new ScaledResolution(mc).getScaleFactor();
        GlStateManager.scale(unit, unit, 1.0);
        canvas.begin(font, height);
        ui.render(canvas);
        canvas.end();
        GlStateManager.popMatrix();
    }

    @Override
    public void handleMouseInput() {
        if (Mouse.getEventButton() == 0) {
            buttonEvents.add(new int[]{Mouse.getEventButtonState() ? 1 : 0, Mouse.getEventX(), Mouse.getEventY()});
        }
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) wheel += Math.abs(dWheel) >= 120 ? dWheel / 120.0f : Math.signum(dWheel);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (ui == null) return;

        if (menu.listening()) {
            if (keyCode > 0) menu.bind(keyCode);
            return;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl && keyCode == Keyboard.KEY_F) {
            ui.focusField("##search");
            return;
        }
        if (ui.typing()) {
            if (keyCode == Keyboard.KEY_ESCAPE && ui.typingIn("##search")) {
                ui.stopTyping(false);
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                ui.stopTyping(true);
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                ui.stopTyping(false);
            } else if (keyCode == Keyboard.KEY_BACK) {
                ui.backspace();
            } else if (keyCode == Keyboard.KEY_DELETE) {
                ui.delete();
            } else if (keyCode == Keyboard.KEY_LEFT) {
                ui.moveCaret(-1, false);
            } else if (keyCode == Keyboard.KEY_RIGHT) {
                ui.moveCaret(1, false);
            } else if (keyCode == Keyboard.KEY_HOME) {
                ui.moveCaret(-1, true);
            } else if (keyCode == Keyboard.KEY_END) {
                ui.moveCaret(1, true);
            } else if (ctrl && keyCode == Keyboard.KEY_V) {
                ui.paste(getClipboardString());
            } else if (!ctrl && typedChar >= 32 && typedChar != 127) {
                ui.typeChar(typedChar);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (ui.popupOpen()) {
                ui.closePopup();
            } else {
                mc.displayGuiScreen(null);
            }
        } else if (keyCode > 0 && keyCode == ClientSettingsModule.menuKey.getValue().intValue()) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (ui != null) {
            ui.stopTyping(false);
            ui.closePopup();
        }
        menu.stopListening();
        buttonEvents.clear();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
