package rip.tempest.jenny;

import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.jenny.entity.NpcEntity;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class NpcManager {
    private static final List<NpcEntity> npcs = new ArrayList<>();
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void spawn(CharacterType type) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double x = mc.thePlayer.posX - Math.sin(yaw) * 2.0;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ + Math.cos(yaw) * 2.0;
        NpcEntity npc = new NpcEntity(mc.theWorld, type, x, y, z);
        npc.rotationYaw = mc.thePlayer.rotationYaw + 180f;
        npcs.add(npc);
    }

    public static void despawn(CharacterType type) {
        npcs.removeIf(npc -> npc.getCharacterType() == type);
    }

    public static void despawnAll() {
        npcs.clear();
    }

    public static void onWorldLoad() {
        npcs.clear();
    }

    public static List<NpcEntity> getAll() {
        return npcs;
    }
}
