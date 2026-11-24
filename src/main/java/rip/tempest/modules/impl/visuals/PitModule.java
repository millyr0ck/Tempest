package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Pit Tracker", category = ModuleCategory.VISUALS, description = "Bounties, streaks and your session in the Pit")
public final class PitModule extends Module {
    private static final String DRAG_KEY = "PitTracker";
    private static PitModule instance;

    public enum Hunter {
        Off, Prefer, Only
    }

    private final Property<Boolean> onlyInPit = new Property<>("Only In Pit", true);
    private final Property<Boolean> showSession = new Property<>("Show Session", true);
    private final Property<Boolean> showBounties = new Property<>("Show Bounties", true);
    private final NumberProperty maxBounties = new NumberProperty("Bounties Shown", 5, showBounties::getValue, 1, 10, 1);
    private final Property<Boolean> showStreaks = new Property<>("Show Streaks", true);
    private final NumberProperty minBounty = new NumberProperty("Min Bounty", 500, 0, 10000, 50);
    private final Property<Boolean> bountyTags = new Property<>("Bounty Tags", true);
    private final Property<Boolean> bountyAlerts = new Property<>("Bounty Alerts", true);
    private final Property<Boolean> claimAlerts = new Property<>("Claim Alerts", true);
    private final ModeProperty<Hunter> hunter = new ModeProperty<>("Bounty Hunter", Hunter.Off);

    public PitModule() {
        instance = this;
    }

    private static final Pattern KILL = Pattern.compile("^(?:[A-Z]+ )?KILL!(?: \\(\\d+\\))? on \\[([^\\]]+)\\] (\\S+) \\+([\\d,]+)XP \\+([\\d,.]+)g");
    private static final Pattern ASSIST = Pattern.compile("^ASSIST! \\d+% on \\[([^\\]]+)\\] (\\S+) \\+([\\d,]+)XP \\+([\\d,.]+)g");
    private static final Pattern DEATH = Pattern.compile("^DEATH!");
    private static final Pattern CLAIMED = Pattern.compile("^BOUNTY CLAIMED! \\[([^\\]]+)\\] (\\S+) killed \\[([^\\]]+)\\] (\\S+) for ([\\d,]+)g");
    private static final Pattern STREAK = Pattern.compile("^STREAK! of (\\d+) kills by \\[([^\\]]+)\\] (\\S+)");

    private static final Pattern TAB_GOLD = Pattern.compile("(?<![\\w.])(\\d[\\d,]*)g(?!\\w)");
    private static final Pattern TAB_LEVEL = Pattern.compile("\\[(\\d+)]");

    private static final class Bounty {
        String level = "?";
        int gold;
    }

    private final Map<String, Bounty> bounties = new HashMap<>();
    private final Map<String, Integer> streaks = new HashMap<>();
    private final Map<String, String> levels = new HashMap<>();

    private long sessionStart;
    private int kills, assists, deaths, streak, bestStreak, claimed;
    private long xp;
    private double claimedGold;

    @Override
    public void onEnable() {
        sessionStart = System.currentTimeMillis();
        kills = assists = deaths = streak = bestStreak = claimed = 0;
        xp = 0;
        claimedGold = 0;
        super.onEnable();
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> {
        bounties.clear();
        streaks.clear();
        levels.clear();
    };

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;
        Map<String, Bounty> now = new HashMap<>();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            if (name == null) continue;
            String entry = info.getDisplayName() != null ? info.getDisplayName().getFormattedText()
                    : ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(), name);
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(entry);

            String rest = plain.replace(name, " ");
            Matcher gold = TAB_GOLD.matcher(rest);
            int amount = -1;
            while (gold.find()) amount = (int) number(gold.group(1));
            if (amount <= 0) continue;

            Bounty bounty = new Bounty();
            bounty.gold = amount;
            Matcher level = TAB_LEVEL.matcher(plain);
            if (level.find()) bounty.level = level.group(1);
            else bounty.level = levels.getOrDefault(name, "?");
            now.put(name, bounty);

            Bounty before = bounties.get(name);
            int min = minBounty.getValue().intValue();
            if (bountyAlerts.getValue() && amount >= min && (before == null || before.gold < min) && !name.equals(mc.thePlayer.getName())) {
                NotificationManager.post(NotificationType.WARNING, "Bounty", amount + "g on [" + bounty.level + "] " + name);
            }
        }
        bounties.clear();
        bounties.putAll(now);
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (!(e.getPacket() instanceof S02PacketChat chat) || chat.getType() == 2 || chat.getChatComponent() == null || mc.thePlayer == null) return;
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(chat.getChatComponent().getUnformattedText()).trim();
        String me = mc.thePlayer.getName();
        Matcher m;

        if ((m = KILL.matcher(text)).find()) {
            kills++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            xp += (long) number(m.group(3));
            levels.put(m.group(2), m.group(1));
        } else if ((m = ASSIST.matcher(text)).find()) {
            assists++;
            xp += (long) number(m.group(3));
        } else if (DEATH.matcher(text).find()) {
            deaths++;
            streak = 0;
        } else if ((m = CLAIMED.matcher(text)).find()) {
            String killer = m.group(2), target = m.group(4);
            streaks.remove(target);
            if (killer.equalsIgnoreCase(me)) {
                double amount = number(m.group(5));
                claimed++;
                claimedGold += amount;
                if (claimAlerts.getValue()) {
                    NotificationManager.post(NotificationType.SUCCESS, "Bounty Claimed", "+" + compact(amount) + "g from [" + m.group(3) + "] " + target);
                }
            }
        } else if ((m = STREAK.matcher(text)).find()) {
            streaks.put(m.group(3), Integer.parseInt(m.group(1)));
            levels.put(m.group(3), m.group(2));
        }
    };

    private static double number(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean on() {
        return instance != null && instance.isEnabled();
    }

    public static int bountyOf(Entity entity) {
        if (!on() || !(entity instanceof EntityPlayer)) return 0;
        Bounty bounty = instance.bounties.get(entity.getName());
        return bounty == null ? 0 : bounty.gold;
    }

    public static boolean huntsBounties() {
        return on() && instance.hunter.getValue() != Hunter.Off;
    }

    public static boolean belowHuntedBounty(Entity entity) {
        return on() && instance.hunter.getValue() == Hunter.Only && bountyOf(entity) < instance.minBounty.getValue();
    }

    private static final int GOLD = 0xFFFFAA00, SOFT_GOLD = 0xFFE0C080, GREY = 0xFFAAAAAA, WHITE = 0xFFFFFFFF,
            GREEN = 0xFF55FF55, RED = 0xFFFF5555, AQUA = 0xFF55FFFF;

    private static final class Row {
        final List<String> parts = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        String right;
        int rightColor;
        boolean header;

        Row add(String part, int color) {
            parts.add(part);
            colors.add(color);
            return this;
        }

        Row right(String text, int color) {
            right = text;
            rightColor = color;
            return this;
        }
    }

    private boolean inPit() {
        if (!onlyInPit.getValue()) return true;
        if (mc.theWorld == null) return false;
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) return false;
        String title = EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()).toUpperCase();
        if (title.contains("PIT")) return true;
        for (net.minecraft.scoreboard.Score score : mc.theWorld.getScoreboard().getSortedScores(objective)) {
            String name = score.getPlayerName();
            if (name == null) continue;
            String line = EnumChatFormatting.getTextWithoutFormattingCodes(
                    ScorePlayerTeam.formatPlayerName(mc.theWorld.getScoreboard().getPlayersTeam(name), name));
            if (line.startsWith("Needed XP")) return true;
        }
        return false;
    }

    @EventLink
    public final Listener<Render2DEvent> onRender = e -> {
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;
        if (!inPit() && !(mc.currentScreen instanceof GuiChat)) return;

        int accent = ColorProcess.getColor().getRGB();
        List<Row> rows = new ArrayList<>();
        Row title = new Row().add("Pit", accent).right(clock(System.currentTimeMillis() - sessionStart), GREY);
        title.header = true;
        rows.add(title);

        if (showSession.getValue()) {
            rows.add(new Row().add("K ", GREY).add(String.valueOf(kills), GREEN).add("  A ", GREY).add(String.valueOf(assists), WHITE)
                    .add("  D ", GREY).add(String.valueOf(deaths), RED).right(String.format("%.1f K/D", kills / (double) Math.max(1, deaths)), GREY));
            rows.add(new Row().add("Streak ", GREY).add(String.valueOf(streak), streak >= 5 ? GOLD : WHITE)
                    .add("  best ", GREY).add(String.valueOf(bestStreak), WHITE).right("+" + compact(xp) + " XP", AQUA));
            rows.add(new Row().add("Claimed ", GREY).add(String.valueOf(claimed), claimed > 0 ? GOLD : WHITE)
                    .right(compact(claimedGold) + "g", claimed > 0 ? GOLD : GREY));
        }

        if (showBounties.getValue()) {
            List<Map.Entry<String, Bounty>> list = new ArrayList<>(bounties.entrySet());
            list.removeIf(entry -> entry.getKey().equals(mc.thePlayer.getName()));
            list.sort((a, b) -> Integer.compare(b.getValue().gold, a.getValue().gold));
            Row header = new Row().add("Bounties", accent).right(list.isEmpty() ? "none" : String.valueOf(list.size()), GREY);
            header.header = true;
            rows.add(header);
            for (int i = 0; i < Math.min(list.size(), maxBounties.getValue().intValue()); i++) {
                String name = list.get(i).getKey();
                Bounty bounty = list.get(i).getValue();
                EntityPlayer player = mc.theWorld.getPlayerEntityByName(name);
                Row row = new Row().add("[" + bounty.level + "] ", GREY).add(name, player != null ? GREEN : WHITE);
                if (player != null) row.add(" " + Math.round(mc.thePlayer.getDistanceToEntity(player)) + "m", GREY);
                row.right(compact(bounty.gold) + "g", bounty.gold >= minBounty.getValue() ? GOLD : SOFT_GOLD);
                rows.add(row);
            }
        }

        if (showStreaks.getValue() && !streaks.isEmpty()) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(streaks.entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            Row header = new Row().add("Streaks", accent);
            header.header = true;
            rows.add(header);
            for (int i = 0; i < Math.min(list.size(), 3); i++) {
                String name = list.get(i).getKey();
                rows.add(new Row().add("[" + levels.getOrDefault(name, "?") + "] ", GREY).add(name, WHITE).right(list.get(i).getValue() + " kills", RED));
            }
        }

        draw(rows);
    };

    private void draw(List<Row> rows) {
        CustomFontRenderer font = FontProcess.getCurrentFont();
        float pad = 4, lineH = 11, gap = 10;
        float width = 120;
        for (Row row : rows) {
            float w = 0;
            for (String part : row.parts) w += font.getStringWidth(part);
            if (row.right != null) w += gap + font.getStringWidth(row.right);
            width = Math.max(width, w + pad * 2);
        }
        float height = pad * 2 + rows.size() * lineH;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d == null) {
            d = new DraggingProcess.DraggableComponent(4, 60);
            DraggingProcess.components.put(DRAG_KEY, d);
        }
        d.setWidth(width);
        d.setHeight(height);
        float x = (float) d.getX(), y = (float) d.getY();

        Glyphs.bar(x, y, width, height, new Color(12, 12, 16, 170));
        Glyphs.bar(x, y, width, 1, ColorProcess.getColor());

        GlStateManager.enableBlend();
        float ty = y + pad;
        for (Row row : rows) {
            if (row.header && ty > y + pad) Glyphs.bar(x + pad, ty - 1, width - pad * 2, 0.5f, new Color(255, 255, 255, 30));
            float tx = x + pad;
            float textY = ty + (lineH - font.getHeight()) / 2f;
            for (int i = 0; i < row.parts.size(); i++) {
                font.drawStringWithShadow(row.parts.get(i), tx, textY, row.colors.get(i));
                tx += font.getStringWidth(row.parts.get(i));
            }
            if (row.right != null) font.drawStringWithShadow(row.right, x + width - pad - font.getStringWidth(row.right), textY, row.rightColor);
            ty += lineH;
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static String compact(double v) {
        if (v >= 1_000_000) return String.format("%.1fm", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fk", v / 1000);
        return String.format("%,.0f", v);
    }

    @EventLink
    public final Listener<Render3DEvent> onRender3D = e -> {
        if (!bountyTags.getValue() || mc.theWorld == null || bounties.isEmpty()) return;
        float partial = mc.timer.renderPartialTicks;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            Bounty bounty = bounties.get(player.getName());
            if (bounty == null) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial - mc.getRenderManager().viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial - mc.getRenderManager().viewerPosY + player.height + 0.85;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial - mc.getRenderManager().viewerPosZ;
            String text = compact(bounty.gold) + "g";
            int color = bounty.gold >= minBounty.getValue() ? GOLD : SOFT_GOLD;

            float scale = 0.027f * (float) Math.max(1, mc.thePlayer.getDistanceToEntity(player) / 8);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0, 1, 0);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            int w = mc.fontRendererObj.getStringWidth(text);
            Glyphs.bar(-w / 2f - 2, -2, w + 4, 11, new Color(0, 0, 0, 120));
            GlStateManager.enableTexture2D();
            mc.fontRendererObj.drawStringWithShadow(text, -w / 2f, 0, color);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1, 1, 1, 1);
    };
}
