package rip.tempest.jenny.gui;

import rip.tempest.jenny.anim.NpcAnimation;
import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.entity.Outfit;
import rip.tempest.jenny.entity.SexScene;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DialogueGui extends GuiScreen {
    private static final int BTN_TALK      = 0;
    private static final int BTN_LEAVE     = 1;
    private static final int BTN_UNDRESS   = 2;
    private static final int BTN_FASTER    = 3;
    private static final int BTN_CUM       = 4;
    private static final int BTN_STOP      = 5;
    private static final int BTN_OUTFIT    = 6;

    private static final int BTN_ACT_BASE  = 10;

    private static final int BTN_IDLE_BASE = 100;

    private final NpcEntity npc;
    private String currentLine;
    private List<SexScene> availableScenes;

    private List<SexScene> acts;
    private static final int BTN_SCENE_ACT_BASE = 200;

    public DialogueGui(NpcEntity npc) {
        this.npc = npc;
        this.currentLine = npc.nextDialogue();
        this.availableScenes = SexScene.forCharacter(npc.getCharacterType());
        this.acts = SexScene.actsFor(npc.getCharacterType());
    }

    @Override
    public void initGui() {
        buildButtons();
    }

    private String shownState = "";

    @Override
    public void updateScreen() {
        String state = npc.isStripping() + "/" + npc.isPreluding() + "/" + npc.getCurrentScene() + "/" + npc.getSceneStage() + "/" + npc.isNaked();
        if (!state.equals(shownState)) {
            shownState = state;
            buildButtons();
        }
    }

    private void buildButtons() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        if (npc.isStripping() || npc.isPreluding()) {
            buttonList.add(new GuiButton(BTN_STOP, cx - 50, height - 52, 100, 20, "§cStop"));
        } else if (npc.isInScene()) {
            buildSceneButtons(cx, height - 52);
        } else {
            buildDialogueButtons(cx, cy);
        }
    }

    private void buildDialogueButtons(int cx, int cy) {
        CharacterType type = npc.getCharacterType();

        buttonList.add(new GuiButton(BTN_TALK,  cx - 210, cy + 20, 100, 20, "Talk more"));
        if (type.canUndress()) {
            String undressLabel = npc.isNaked() ? "§bDress~" : "§dUndress~";
            buttonList.add(new GuiButton(BTN_UNDRESS, cx - 210, cy + 44, 100, 20, undressLabel));
        }
        buttonList.add(new GuiButton(BTN_LEAVE, cx - 210, cy + 68, 100, 20, "Leave"));

        for (int i = 0; i < type.idleActNames.length; i++) {
            buttonList.add(new GuiButton(BTN_IDLE_BASE + i, cx - 210, cy + 92 + i * 24, 100, 20, type.idleActNames[i]));
        }
        int row = type.idleActNames.length;
        for (int i = 0; i < acts.size(); i++, row++) {
            buttonList.add(new GuiButton(BTN_SCENE_ACT_BASE + i, cx - 210, cy + 92 + row * 24, 100, 20, acts.get(i).displayName));
        }
        boolean hasArmor = false;
        for (Outfit.Slot slot : Outfit.Slot.values()) hasArmor |= Outfit.supports(type, slot);
        if (hasArmor) {
            buttonList.add(new GuiButton(BTN_OUTFIT, cx - 210, cy + 92 + row * 24, 100, 20, "Outfit"));
        }

        int bx = cx + 10;
        int by = cy + 20;
        for (int i = 0; i < availableScenes.size(); i++) {
            buttonList.add(new GuiButton(BTN_ACT_BASE + i, bx, by + i * 24, 100, 20, availableScenes.get(i).displayName));
        }
    }

    private void buildSceneButtons(int cx, int y) {
        int stage = npc.getSceneStage();
        SexScene scene = npc.getCurrentScene();

        if (scene != null && scene.act) {
            buttonList.add(new GuiButton(BTN_STOP, cx - 50, y, 100, 20, "Stop"));
            return;
        }
        if (stage < 3) {
            if (scene != null && scene.hasFast() && stage < 2) {
                buttonList.add(new GuiButton(BTN_FASTER, cx - 155, y, 100, 20, "§aFaster~"));
            }
            buttonList.add(new GuiButton(BTN_CUM,  cx - 50, y, 100, 20, "§dFinish~"));
            buttonList.add(new GuiButton(BTN_STOP, cx + 55, y, 100, 20, "§cStop"));
        } else {
            buttonList.add(new GuiButton(BTN_STOP, cx - 50, y, 100, 20, "§cStop"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_TALK) {
            currentLine = npc.nextDialogue();
            return;
        }
        if (button.id == BTN_LEAVE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BTN_OUTFIT) {
            mc.displayGuiScreen(new OutfitGui(npc, this));
            return;
        }
        if (button.id == BTN_UNDRESS) {
            boolean undressing = !npc.isNaked();
            npc.toggleNaked();
            npc.say(undressing ? "*" + npc.getName() + " slips off her clothes~*"
                               : "*" + npc.getName() + " gets dressed again*");

            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BTN_FASTER) {
            npc.goFaster();
            npc.say(sceneDialogue(npc.getCurrentScene(), 2));
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BTN_CUM) {
            npc.triggerCum();
            npc.say(sceneDialogue(npc.getCurrentScene(), 3));
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BTN_STOP) {
            SexScene scene = npc.getCurrentScene();
            if (scene != null && scene.act && npc.getSceneStage() != 3 && scene.cumAnim != null) {
                npc.triggerCum();
                mc.displayGuiScreen(null);
                return;
            }
            npc.stopScene();
            currentLine = npc.nextDialogue();
            npc.say(currentLine);
            buildButtons();
            return;
        }

        int actIdx = button.id - BTN_ACT_BASE;
        if (actIdx >= 0 && actIdx < availableScenes.size()) {
            SexScene scene = availableScenes.get(actIdx);
            npc.startScene(scene);
            npc.say(sceneDialogue(scene, 0));

            mc.displayGuiScreen(null);
            return;
        }

        int sceneActIdx = button.id - BTN_SCENE_ACT_BASE;
        if (sceneActIdx >= 0 && sceneActIdx < acts.size()) {
            SexScene act = acts.get(sceneActIdx);
            npc.startScene(act);
            npc.say("*" + npc.getName() + ": " + act.displayName.toLowerCase() + "*");
            mc.displayGuiScreen(null);
            return;
        }

        int idleIdx = button.id - BTN_IDLE_BASE;
        CharacterType type = npc.getCharacterType();
        if (idleIdx >= 0 && idleIdx < type.idleActNames.length) {
            playIdleAct(type, idleIdx);
            npc.say("*" + npc.getName() + " " + type.idleActNames[idleIdx].toLowerCase() + "s~*");
            mc.displayGuiScreen(null);
        }
    }

    private void playIdleAct(CharacterType type, int idx) {
        Map<String, NpcAnimation> anims = type.getAnimations();
        if (anims == null || idx >= type.idleActAnimNames.length) return;
        NpcAnimation anim = anims.get(type.idleActAnimNames[idx]);
        npc.playAct(anim);
    }

    public static String sceneDialogue(SexScene scene, int stage) {
        if (scene == null) return "...";
        return switch (scene) {
            case JENNY_BLOWJOB, ALLIE_DEEPTHROAT, SLIME_BLOWJOB, KOBOLD_BLOWJOB -> switch (stage) {
                case 0 -> "*" + scene.character.id + " takes you in her mouth~*";
                case 2 -> "Mmph~! *gags softly*";
                case 3 -> "Mmm~ *swallows*";
                default -> "Mmmm~";
            };
            case JENNY_PAIZURI -> switch (stage) {
                case 0 -> "*sandwiches you between her chest~*";
                case 2 -> "Ooh~ you're so hard~";
                case 3 -> "Ahh~! Cum for me~";
                default -> "Feel good~?";
            };
            case JENNY_DOGGY, BIA_PRONE_DOGGY, SLIME_DOGGY -> switch (stage) {
                case 0 -> "*gets down for you~*";
                case 2 -> "H-harder~!";
                case 3 -> "*moans loudly*";
                default -> "Mmm~ yes~";
            };
            case ELLIE_COWGIRL, ALLIE_REVERSE_COWGIRL, KOBOLD_MATING_PRESS -> switch (stage) {
                case 0 -> "*climbs on top of you~*";
                case 2 -> "I'm going faster~ don't stop~!";
                case 3 -> "I'm c-cumming~!";
                default -> "You feel so good inside~";
            };
            case ELLIE_MISSIONARY, ELLIE_CARRY -> switch (stage) {
                case 0 -> "*pulls you close~*";
                case 2 -> "Yes~ right there~!";
                case 3 -> "D-don't stop~!";
                default -> "Deeper~";
            };
            case BIA_ANAL, KOBOLD_ANAL -> switch (stage) {
                case 0 -> "*braces herself~*";
                case 2 -> "A-ahh~ it's so deep~!";
                case 3 -> "*gasps*";
                default -> "Ngh~ careful~";
            };
            case GALATH_LICK, GALATH_MASTURBATE -> switch (stage) {
                case 0 -> "...I will allow this.";
                case 2 -> "...keep going.";
                case 3 -> "*shudders*";
                default -> "...";
            };
            default -> switch (stage) {
                case 0 -> "*starts*";
                case 2 -> "Faster~";
                case 3 -> "*finishes*";
                default -> "Mmm~";
            };
        };
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        if (npc.isInScene()) {
            super.drawScreen(mx, my, pt);
            return;
        }
        drawDefaultBackground();

        String name = npc.getName();
        int cx = width / 2;
        int cy = height / 2;

        int boxW = 220;
        drawRect(cx - boxW / 2, cy - 55, cx + boxW / 2, cy + 15, 0xCC000000);
        drawCenteredString(fontRendererObj, "§e§l" + name, cx, cy - 50, 0xFFFFFF);

        if (npc.isStripping()) {
            drawCenteredString(fontRendererObj, "§d[Undressing...]", cx, cy - 38, 0xFFFFFF);
        } else if (npc.isInScene()) {
            SexScene sc = npc.getCurrentScene();
            String stageLabel = switch (npc.getSceneStage()) {
                case 0 -> "intro";
                case 1 -> "loop";
                case 2 -> "fast";
                case 3 -> "finishing...";
                default -> "";
            };
            drawCenteredString(fontRendererObj, "§b[" + sc.displayName + " – " + stageLabel + "]", cx, cy - 38, 0xFFFFFF);
        }
        drawCenteredString(fontRendererObj, "§f" + currentLine, cx, cy - 22, 0xFFFFFF);

        if (!npc.isInScene() && !npc.isStripping() && !availableScenes.isEmpty()) {
            drawString(fontRendererObj, "§7Actions:", cx - 210, cy + 8, 0xFFFFFF);
            drawString(fontRendererObj, "§7Sex acts:", cx + 10, cy + 8, 0xFFFFFF);
        }

        if (npc.isNaked()) {
            drawCenteredString(fontRendererObj, "§d♥ naked ♥", cx, cy - 6, 0xFFCCCC);
        }

        super.drawScreen(mx, my, pt);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
