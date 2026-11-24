package rip.tempest.interfaces.click;

import net.minecraft.client.gui.GuiScreen;

public final class ColorClipboard {
    private ColorClipboard() {
    }

    public static void copy(int argb) {
        GuiScreen.setClipboardString(String.format("#%08X", argb));
    }

    public static Integer paste() {
        String text = GuiScreen.getClipboardString();
        if (text == null) return null;
        text = text.trim().replace("#", "").replace("0x", "");
        if (text.length() != 6 && text.length() != 8) return null;
        try {
            int value = (int) Long.parseLong(text, 16);
            return text.length() == 6 ? value | 0xFF000000 : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
