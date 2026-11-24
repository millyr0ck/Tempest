package rip.tempest.jenny.gui;

import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.entity.Outfit;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class OutfitGui extends GuiScreen {
    private static final int BTN_BACK = 0;
    private static final int BTN_CLEAR = 1;
    private static final int BTN_COLOR = 2;
    private static final int BTN_KOBOLD_BODY = 3;
    private static final int BTN_KOBOLD_EYES = 4;
    private static final int BTN_SLOT = 10;

    private final NpcEntity npc;
    private final GuiScreen parent;

    public OutfitGui(NpcEntity npc, GuiScreen parent) {
        this.npc = npc;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buildButtons();
    }

    private void buildButtons() {
        buttonList.clear();
        Outfit outfit = npc.getOutfit();
        int x = width - 130;
        int y = height / 2 - 70;
        for (Outfit.Slot slot : Outfit.Slot.values()) {
            if (!Outfit.supports(npc.getCharacterType(), slot)) continue;
            buttonList.add(new GuiButton(BTN_SLOT + slot.ordinal(), x, y, 120, 20,
                    title(slot) + ": " + outfit.get(slot).label));
            y += 24;
        }
        if (outfit.wearsLeather()) {
            buttonList.add(new GuiButton(BTN_COLOR, x, y, 120, 20,
                    "Leather: " + Outfit.LEATHER_COLOR_NAMES[outfit.leatherColorIndex()]));
            y += 24;
        }
        if (npc.getCharacterType() == CharacterType.KOBOLD) {
            buttonList.add(new GuiButton(BTN_KOBOLD_BODY, x, y, 120, 20, "Body: " + npc.getKoboldBody().label));
            y += 24;
            buttonList.add(new GuiButton(BTN_KOBOLD_EYES, x, y, 120, 20, "Eyes: " + npc.getKoboldEyes().label));
            y += 24;
        }
        buttonList.add(new GuiButton(BTN_CLEAR, x, y + 6, 120, 20, "Remove all"));
        buttonList.add(new GuiButton(BTN_BACK, x, y + 30, 120, 20, "Back"));
    }

    private static String title(Outfit.Slot slot) {
        switch (slot) {
            case HELMET: return "Helmet";
            case CHEST: return "Chestplate";
            case LEGS: return "Leggings";
            default: return "Boots";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Outfit outfit = npc.getOutfit();
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == BTN_CLEAR) {
            outfit.clear();
        } else if (button.id == BTN_KOBOLD_BODY) {
            npc.cycleKoboldBody();
        } else if (button.id == BTN_KOBOLD_EYES) {
            npc.cycleKoboldEyes();
        } else if (button.id == BTN_COLOR) {
            outfit.cycleLeatherColor();
        } else if (button.id >= BTN_SLOT && button.id < BTN_SLOT + Outfit.Slot.values().length) {
            outfit.cycle(Outfit.Slot.values()[button.id - BTN_SLOT]);
        }
        buildButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int x = width - 130;
        drawRect(x - 6, height / 2 - 88, width - 4, height / 2 + 140, 0x90000000);
        drawString(fontRendererObj, "§e" + npc.getName() + "'s outfit", x, height / 2 - 82, 0xFFFFFF);
        if (npc.isNaked()) {
            drawString(fontRendererObj, "§7Shows once she is dressed", x, height / 2 + 78, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
