package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.Themes;
import rip.tempest.utils.render.shaders.LiquidGlass;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@ModuleInfo(label = "Scoreboard", category = ModuleCategory.VISUALS)
public final class ScoreboardModule extends Module {
    public enum Style {
        Gradient,

        Solid
    }

    public enum ServerColor {
        Client,

        Yellow
    }

    private static final int YELLOW = 0xFFFFFF55;

    public static final Property<String> serverText = new Property<>("Server Text", "tempest.wtf");
    public static final ModeProperty<ServerColor> serverColor = new ModeProperty<>("Server Color", ServerColor.Client);

    public static final Property<Boolean> customTitle = new Property<>("Custom Title", false);
    public static final Property<String> titleText = new Property<>("Title Text", "&e&lTEMPEST", () -> customTitle.getValue());
    public static final ModeProperty<Style> style = new ModeProperty<>("Style", Style.Gradient);
    public static final NumberProperty backgroundOpacity = new NumberProperty("Background Opacity", 140, () -> !glass(), 0, 255, 5);

    public static final Property<Boolean> liquidGlass = new Property<>("Liquid Glass", false);

    private static final String DRAG_KEY = "Scoreboard";

    private static int lastW = -1, lastH = -1;

    private static long drawnAt;

    private static final double LIST_GAP = 4, STICK_RANGE = 14;

    private static boolean stuckToList, wasDragging;

    private static boolean glass() {
        return liquidGlass.getValue() || Themes.liquidGlass();
    }

    @EventLink
    public final Listener<Render2DEvent> render2DListener = e -> {
        if (System.currentTimeMillis() - drawnAt > 500) releaseDragBox();
    };

    @Override
    public void onDisable() {
        releaseDragBox();
        super.onDisable();
    }

    private static void releaseDragBox() {
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d != null) {
            d.setWidth(0);
            d.setHeight(0);
        }
        lastW = lastH = -1;
    }

    private static void stickUnderList(DraggingProcess.DraggableComponent d, int width, int height) {
        boolean dragging = DraggingProcess.isDragging(DRAG_KEY);
        float[] list = ArrayListModule.listBounds();
        boolean released = wasDragging && !dragging;
        wasDragging = dragging;
        if (dragging || list == null) {
            stuckToList = false;
            return;
        }
        boolean sameColumn = d.getX() < list[2] && d.getX() + width > list[0];
        boolean below = d.getY() + height / 2.0 > (list[1] + list[3]) / 2.0;
        if (!sameColumn || !below) {
            stuckToList = false;
            return;
        }
        double want = list[3] + LIST_GAP;
        if (stuckToList || d.getY() < want || (released && d.getY() < want + STICK_RANGE)) {
            d.setY(want);
            stuckToList = true;
        }
    }

    private static DraggingProcess.DraggableComponent place(ScaledResolution resolution, int width, int height) {
        int screenW = resolution.getScaledWidth(), screenH = resolution.getScaledHeight();
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d == null) {
            d = new DraggingProcess.DraggableComponent(screenW - width - MARGIN, (screenH - height) / 2.0);
            DraggingProcess.components.put(DRAG_KEY, d);
        } else if (lastW >= 0 && (lastW != width || lastH != height)) {
            if (d.getX() + lastW / 2.0 > screenW / 2.0) d.setX(d.getX() + lastW - width);
            double middle = d.getY() + lastH / 2.0;

            if (!stuckToList) {
                if (middle > screenH * 2 / 3.0) d.setY(d.getY() + lastH - height);
                else if (middle >= screenH / 3.0) d.setY(d.getY() + (lastH - height) / 2.0);
            }
        }
        d.setX(Math.max(0, Math.min(screenW - width, d.getX())));
        stickUnderList(d, width, height);
        d.setY(Math.max(0, Math.min(screenH - height, d.getY())));
        d.setWidth(width);
        d.setHeight(height);
        lastW = width;
        lastH = height;
        drawnAt = System.currentTimeMillis();
        return d;
    }

    private static final int PAD = 5, LINE_GAP = 2, MARGIN = 4;

    private static final Pattern ADDRESS = Pattern.compile("(?i)(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,24}");

    private static ScoreboardModule instance;

    private static boolean active() {
        if (instance == null) instance = Tempest.INSTANCE.getModuleManager().getModule(ScoreboardModule.class);
        return instance != null && instance.isEnabled();
    }

    public static boolean replaces(String line) {
        if (!active() || line == null) return false;

        Boolean known = REPLACES_CACHE.get(line);
        if (known != null) return known;
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(line);
        boolean result = plain != null && ADDRESS.matcher(plain.trim()).matches();
        if (REPLACES_CACHE.size() > 512) REPLACES_CACHE.clear();
        REPLACES_CACHE.put(line, result);
        return result;
    }

    private static final java.util.Map<String, Boolean> REPLACES_CACHE = new java.util.HashMap<>();

    public static String text() {
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(serverText.getValue());
        return text == null ? "" : text;
    }

    private static String title(ScoreObjective objective) {
        if (!customTitle.getValue()) return objective.getDisplayName();
        String text = titleText.getValue();
        if (text == null || text.trim().isEmpty()) return objective.getDisplayName();
        return text.replace('&', '§');
    }

    public static void draw(FontRenderer font, int x, int y) {
        String text = text();
        if (serverColor.getValue() == ServerColor.Yellow) {
            font.drawString(text, x, y, YELLOW);
            return;
        }
        if (style.getValue() == Style.Solid) {
            font.drawString(text, x, y, ColorProcess.getColor().getRGB());
            return;
        }

        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        double phase = System.currentTimeMillis() / InfoModule.GRADIENT_WAVE_SPEED;
        float total = Math.max(1, font.getStringWidth(text));
        for (int i = 0; i < text.length(); i++) {
            int offset = font.getStringWidth(text.substring(0, i));
            int color = Glyphs.waveColor(first, second, offset / total, phase).getRGB();
            font.drawString(String.valueOf(text.charAt(i)), x + offset, y, color);
        }
    }

    public static boolean render(ScoreObjective objective, ScaledResolution resolution) {
        if (!active()) return false;
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        Scoreboard scoreboard = objective.getScoreboard();

        List<Score> scores = new ArrayList<>();
        Collection<Score> sorted = scoreboard.getSortedScores(objective);
        for (Score score : sorted) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) scores.add(score);
        }
        if (scores.size() > 15) scores = new ArrayList<>(scores.subList(scores.size() - 15, scores.size()));

        List<String> lines = new ArrayList<>();
        for (int i = scores.size() - 1; i >= 0; i--) {
            Score score = scores.get(i);
            lines.add(ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName()));
        }

        String title = title(objective);
        int width = font.getStringWidth(title);
        for (String line : lines) width = Math.max(width, font.getStringWidth(replaces(line) ? text() : line));

        int lineHeight = font.FONT_HEIGHT + LINE_GAP;
        int titleHeight = font.FONT_HEIGHT + PAD + 2;
        int panelWidth = width + PAD * 2;
        int panelHeight = titleHeight + PAD - 1 + lines.size() * lineHeight;
        DraggingProcess.DraggableComponent d = place(resolution, panelWidth, panelHeight);
        int x = (int) Math.round(d.getX());
        int y = (int) Math.round(d.getY());

        if (glass() && LiquidGlass.prepare()) {
            LiquidGlass.drawWithShadow(x, y, panelWidth, panelHeight, 6, 1f);
        } else {
            RenderUtils.drawRoundedRect(x, y, panelWidth, panelHeight, 4, new Color(12, 12, 14, backgroundOpacity.getValue().intValue()));
        }
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        font.drawString(title, x + (panelWidth - font.getStringWidth(title)) / 2f, y + PAD, 0xFFFFFFFF, glass());
        float lineY = y + titleHeight;
        if (style.getValue() == Style.Gradient) {
            Glyphs.waveGradient(x + PAD, lineY, panelWidth - PAD * 2, 1, ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(),
                    System.currentTimeMillis() / InfoModule.GRADIENT_WAVE_SPEED);
        } else {
            Glyphs.bar(x + PAD, lineY, panelWidth - PAD * 2, 1, ColorProcess.getColor());
        }

        GlStateManager.enableTexture2D();
        int textY = y + titleHeight + PAD - 1;
        for (String line : lines) {
            if (replaces(line)) {
                draw(font, x + PAD, textY);
            } else {
                font.drawString(line, x + PAD, textY, 0xFFFFFFFF, glass());
            }
            textY += lineHeight;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }
}
