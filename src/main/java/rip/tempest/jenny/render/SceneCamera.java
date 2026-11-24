package rip.tempest.jenny.render;

import rip.tempest.jenny.NpcManager;
import rip.tempest.jenny.entity.NpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public final class SceneCamera {
    private static boolean moved;
    private static float partialTicks;
    private static double posX, posY, posZ, prevX, prevY, prevZ, lastX, lastY, lastZ;

    private SceneCamera() {
    }

    public static NpcEntity activeScene() {
        for (NpcEntity npc : NpcManager.getAll()) {
            if (npc.showsPlayer()) return npc;
        }
        return null;
    }

    public static boolean isActive() {
        return activeScene() != null;
    }

    public static boolean isFirstPerson(NpcEntity npc) {
        return moved && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 && activeScene() == npc;
    }

    public static void begin(float partialTicks) {
        SceneCamera.partialTicks = partialTicks;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        NpcEntity npc = activeScene();
        if (p == null || npc == null || moved) return;

        npc.getAnimPlayer().update();
        double[] eye = BoneMath.playerEyeOffset(npc);
        if (eye == null) return;

        posX = p.posX; posY = p.posY; posZ = p.posZ;
        prevX = p.prevPosX; prevY = p.prevPosY; prevZ = p.prevPosZ;
        lastX = p.lastTickPosX; lastY = p.lastTickPosY; lastZ = p.lastTickPosZ;
        moved = true;

        double x = npc.posX + eye[0];
        double y = npc.posY + eye[1] - p.getEyeHeight();
        double z = npc.posZ + eye[2];
        p.posX = p.prevPosX = p.lastTickPosX = x;
        p.posY = p.prevPosY = p.lastTickPosY = y;
        p.posZ = p.prevPosZ = p.lastTickPosZ = z;
    }

    public static float partialTicks() {
        return partialTicks;
    }

    public static void end() {
        if (!moved) return;
        moved = false;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        p.posX = posX; p.posY = posY; p.posZ = posZ;
        p.prevPosX = prevX; p.prevPosY = prevY; p.prevPosZ = prevZ;
        p.lastTickPosX = lastX; p.lastTickPosY = lastY; p.lastTickPosZ = lastZ;
    }

    public static boolean hidesLocalPlayer() {
        return isActive();
    }
}
