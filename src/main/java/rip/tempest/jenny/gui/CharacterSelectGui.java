package rip.tempest.jenny.gui;

import rip.tempest.jenny.NpcManager;
import rip.tempest.jenny.entity.CharacterType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

public class CharacterSelectGui extends GuiScreen {
    private static final int BTN_PREV     = 0;
    private static final int BTN_NEXT     = 1;
    private static final int BTN_SPAWN    = 2;
    private static final int BTN_DESPAWN  = 3;
    private static final int BTN_CLOSE    = 4;

    private final CharacterType[] types = CharacterType.values();
    private int selected = 0;

    @Override
    public void initGui() {
        buildButtons();
    }

    private void buildButtons() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        buttonList.add(new GuiButton(BTN_PREV,    cx - 130, cy,      40, 20, "<"));
        buttonList.add(new GuiButton(BTN_NEXT,    cx +  90, cy,      40, 20, ">"));
        buttonList.add(new GuiButton(BTN_SPAWN,   cx -  50, cy,     100, 20, "Spawn"));
        buttonList.add(new GuiButton(BTN_DESPAWN, cx -  50, cy + 28, 100, 20, "Despawn All"));
        buttonList.add(new GuiButton(BTN_CLOSE,   cx -  50, cy + 56, 100, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BTN_PREV -> { selected = (selected - 1 + types.length) % types.length; }
            case BTN_NEXT -> { selected = (selected + 1) % types.length; }
            case BTN_SPAWN -> {
                CharacterType type = types[selected];
                NpcManager.despawnAll();
                NpcManager.spawn(type);
                mc.displayGuiScreen(null);
            }
            case BTN_DESPAWN -> {
                NpcManager.despawnAll();
                mc.displayGuiScreen(null);
            }
            case BTN_CLOSE -> mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();

        CharacterType type = types[selected];
        int cx = width / 2;
        int cy = height / 2;

        drawRect(cx - 140, cy - 60, cx + 140, cy + 90, 0xCC000000);

        drawCenteredString(fontRendererObj, "§e§lCharacter Select", cx, cy - 54, 0xFFFFFF);

        String displayName = type.id.substring(0, 1).toUpperCase() + type.id.substring(1);
        drawCenteredString(fontRendererObj, "§f§l" + displayName, cx, cy - 30, 0xFFFFFF);

        drawCenteredString(fontRendererObj, "§7(" + (selected + 1) + "/" + types.length + ")", cx, cy - 14, 0xAAAAAA);

        boolean spawned = NpcManager.getAll().stream()
                .anyMatch(n -> n.getCharacterType() == type);
        if (spawned) {
            drawCenteredString(fontRendererObj, "§a● Spawned", cx, cy - 2, 0xFFFFFF);
        }

        super.drawScreen(mx, my, pt);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
