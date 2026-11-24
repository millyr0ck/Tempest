package rip.tempest.utils.mc;

import rip.tempest.utils.Util;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

public class ServerUtils extends Util {
    public static boolean isOnHypixel() {
        ServerData server = mc.getCurrentServerData();
        if (server == null || server.serverIP == null) return false;

        String ip = server.serverIP.toLowerCase();
        int port = ip.indexOf(':');
        if (port != -1) ip = ip.substring(0, port);
        return ip.equals("hypixel.net") || ip.endsWith(".hypixel.net");
    }

    public static String sidebarTitle() {
        if (mc.theWorld == null) return "";

        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        return objective == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()).trim();
    }

    public static boolean isGameStarting() {
        if (mc.theWorld == null) return false;
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) return false;

        for (Score score : scoreboard.getSortedScores(objective)) {
            String name = score.getPlayerName();
            if (name == null || name.startsWith("#")) continue;
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(name), name)).toLowerCase();
            if (plain.contains("starting in") || plain.contains("waiting..")) return true;
        }
        return false;
    }

    public static long getPing() {
        if (mc.isSingleplayer() || mc.getNetHandler() == null || mc.thePlayer == null) return 0L;

        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getGameProfile().getId());
        return info != null && info.getResponseTime() > 0 ? info.getResponseTime() : 200L;
    }
}
