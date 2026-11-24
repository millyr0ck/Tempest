package rip.tempest.jenny.gui;

import rip.tempest.jenny.NpcManager;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.entity.SexScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public final class SceneHud {
    private SceneHud() {
    }

    public static NpcEntity sceneNpc() {
        for (NpcEntity npc : NpcManager.getAll()) {
            if (npc.isInScene()) return npc;
        }
        return null;
    }

    public static void draw(ScaledResolution sr) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || NpcManager.getAll().isEmpty()) return;

        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        int hintY = h - 64;
        int meterY = h - 74;
        int subtitleBottom = h - 80;

        NpcEntity npc = sceneNpc();
        if (npc == null) {
            for (NpcEntity n : NpcManager.getAll()) {
                if (n.getLine() != null) {
                    drawSubtitle(mc, n, null, w, h - 56);
                    break;
                }
            }
            return;
        }

        String status;
        SexScene scene = npc.getCurrentScene();
        if (npc.isPreluding()) {
            status = "Paying her...";
        } else if (npc.isStripping() || scene == null) {
            status = "Undressing...";
        } else {
            String stage = scene.act ? (npc.getSceneStage() == 3 ? "ending" : "on") : switch (npc.getSceneStage()) {
                case 0 -> "starting";
                case 1 -> "slow";
                case 2 -> "fast";
                default -> "finishing";
            };
            status = scene.displayName + " - " + stage;
        }

        if (scene == null || npc.isStripping()) {
            drawSubtitle(mc, npc, status, w, h - 56);
            return;
        }
        if (scene.act) {
            drawSubtitle(mc, npc, status, w, subtitleBottom);
            if (mc.currentScreen == null) {
                FontRenderer fr = mc.fontRendererObj;
                String text = "[Sneak] Stop   [Right-click] Menu";
                int tw = fr.getStringWidth(text);
                Gui.drawRect((w - tw) / 2 - 4, hintY - 2, (w + tw) / 2 + 4, hintY + 10, 0x80000000);
                fr.drawStringWithShadow(text, (w - tw) / 2f, hintY, 0xFFFFFFFF);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            return;
        }
        drawSubtitle(mc, npc, status, w, subtitleBottom);

        int barW = 160;
        int bx = (w - barW) / 2;
        float progress = npc.getCumProgress();
        Gui.drawRect(bx - 1, meterY - 1, bx + barW + 1, meterY + 5, 0xB0000000);
        int r = (int) (200 + 55 * progress);
        int g = (int) (60 * (1f - progress));
        int b = (int) (100 * (1f - progress));
        Gui.drawRect(bx, meterY, bx + (int) (barW * progress), meterY + 4, 0xFF000000 | (r << 16) | (g << 8) | b);

        if (mc.currentScreen == null) {
            StringBuilder hint = new StringBuilder();
            if (progress < 1f && scene.hasFast() && npc.getSceneStage() == 1) hint.append("[Space] Faster   ");
            if (progress < 1f && npc.getSceneStage() == 2) hint.append("[Space] Slower   ");
            if (npc.getSceneStage() != 3) hint.append(progress >= 1f ? "§d[Space/F] Finish§f   " : "[F] Finish   ");
            hint.append("[Sneak] Stop   [Right-click] Menu");
            FontRenderer fr = mc.fontRendererObj;
            String text = hint.toString();
            int tw = fr.getStringWidth(text);
            Gui.drawRect((w - tw) / 2 - 4, hintY - 2, (w + tw) / 2 + 4, hintY + 10, 0x80000000);
            fr.drawStringWithShadow(text, (w - tw) / 2f, hintY, 0xFFFFFFFF);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void drawSubtitle(Minecraft mc, NpcEntity npc, String status, int screenW, int bottom) {
        FontRenderer fr = mc.fontRendererObj;
        String line = npc.getLine();
        String text = line != null ? "§e" + npc.getLineSpeaker() + ": §f" + line : null;
        int w = 0;
        if (text != null) w = Math.max(w, fr.getStringWidth(text));
        if (status != null) w = Math.max(w, fr.getStringWidth(status));
        if (w == 0) return;

        int rows = (text != null ? 1 : 0) + (status != null ? 1 : 0);
        int top = bottom - 6 - rows * 11;
        int x0 = (screenW - w) / 2 - 6;
        Gui.drawRect(x0, top, x0 + w + 12, bottom, 0xB0000000);
        int y = top + 4;
        if (status != null) {
            fr.drawStringWithShadow(status, (screenW - fr.getStringWidth(status)) / 2f, y, 0xFFFF88CC);
            y += 11;
        }
        if (text != null) {
            fr.drawStringWithShadow(text, (screenW - fr.getStringWidth(text)) / 2f, y, 0xFFFFFFFF);
        }
    }
}
