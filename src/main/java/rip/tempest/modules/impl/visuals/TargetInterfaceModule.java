package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.modules.impl.visuals.targethud.TargetHudMode;
import rip.tempest.modules.impl.visuals.targethud.MoonHud;
import rip.tempest.modules.impl.visuals.targethud.OpalHud;
import rip.tempest.modules.impl.visuals.targethud.PulsiveHud;
import rip.tempest.modules.impl.visuals.targethud.StripHud;
import rip.tempest.modules.impl.visuals.targethud.RavenHuds;
import rip.tempest.modules.impl.visuals.targethud.TempestHuds;
import rip.tempest.modules.impl.visuals.targethud.TargetHuds;
import rip.tempest.modules.impl.visuals.targethud.impl.*;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.interfaces.click.exhibition.util.Colors;
import rip.tempest.interfaces.click.exhibition.util.ExhibitionFonts;
import rip.tempest.interfaces.click.exhibition.util.RenderingUtil;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.kit.render.ColorUtil;
import rip.tempest.utils.kit.render.RoundedUtil;
import rip.tempest.utils.kit.render.font.CustomFont;
import rip.tempest.utils.kit.render.font.FontUtil;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Target HUD", category = ModuleCategory.VISUALS)
public final class TargetInterfaceModule extends Module {
    public static ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Myau2);

    private enum Mode {
        Astolfo("Astolfo"),
        Myau2("Myau2"),
        Astralis("Astralis"),
        Rise("Rise"),
        Novoline("Novoline"),
        Adjust("Adjust"),
        Sleek("Sleek"),
        Aura("Aura"),
        Euphoria("Euphoria"),
        Tenacity("Tenacity"),
        ExhibitionOriginal("Exhibition (Original)"),

        RoundedCompact("Rounded Compact", true),
        RoundedMinimal("Rounded Minimal", true),

        TempestModern("Tempest Modern"),
        TempestLegacy("Tempest Legacy"),
        TempestSlim("Tempest Slim"),
        TempestArmor("Tempest Armor"),

        TempestSegments("Tempest Segments"),

        RavenModern("Raven Modern"),
        RavenLegacy("Raven Legacy"),

        Opal("Opal"),

        MoonMoon("Moon"),
        MoonNovolineO("Moon NovolineO"),
        MoonCustom("Moon Custom"),
        MoonNostalgia("Moon Nostalgia"),

        Pulsive("Pulsive"),
        Pulse("Pulse"),
        PulseSlim("Pulse Slim"),

        StripLine("Line"),
        StripStack("Stack"),

        StripSimple("Simple"),
        StripModern("Modern"),
        StripBadge("Badge");

        public String name;
        private final boolean rounded;
        Mode(String name) {
            this(name, false);
        }
        Mode(String name, boolean rounded) {
            this.name = name;
            this.rounded = rounded;
        }
        public String toString() {
            return name;
        }

        MoonHud.Style moonStyle() {
            return switch (this) {
                case MoonMoon -> MoonHud.Style.Moon;
                case MoonNovolineO -> MoonHud.Style.NovolineO;
                case MoonCustom -> MoonHud.Style.Custom;
                case MoonNostalgia -> MoonHud.Style.Nostalgia;
                default -> null;
            };
        }

        PulsiveHud.Style pulseStyle() {
            return switch (this) {
                case Pulsive -> PulsiveHud.Style.Pulsive;
                case Pulse -> PulsiveHud.Style.Pulse;
                case PulseSlim -> PulsiveHud.Style.PulseSlim;
                default -> null;
            };
        }

        StripHud.Style stripStyle() {
            return switch (this) {
                case StripLine -> StripHud.Style.Line;
                case StripStack -> StripHud.Style.Stack;
                case StripSimple -> StripHud.Style.Simple;
                case StripModern -> StripHud.Style.Modern;
                case StripBadge -> StripHud.Style.Badge;
                default -> null;
            };
        }

        RavenHuds.Style ravenStyle() {
            return switch (this) {
                case RavenModern -> RavenHuds.Style.Modern;
                case RavenLegacy -> RavenHuds.Style.Legacy;
                default -> null;
            };
        }

        TempestHuds.Style tempestStyle() {
            return switch (this) {
                case TempestModern -> TempestHuds.Style.Modern;
                case TempestLegacy -> TempestHuds.Style.Legacy;
                case TempestSlim -> TempestHuds.Style.Slim;
                case TempestArmor -> TempestHuds.Style.Armor;
                case TempestSegments -> TempestHuds.Style.Segments;
                default -> null;
            };
        }
    }

    public enum MultiTargets {
        Off, Stacked, List
    }

    private static final ModeProperty<MultiTargets> multiTargets = new ModeProperty<>("Multi Targets", MultiTargets.Off);
    private static final NumberProperty multiTargetCount = new NumberProperty("Multi Target Count", 2,
            () -> multiTargets.getValue() != MultiTargets.Off, 1, 9, 1);

    private static final NumberProperty tenacityOpacity = new NumberProperty("Opacity", 0.3, () -> mode.getValue() == Mode.Tenacity, 0, 1, 0.1);

    private static final Property<Boolean> grid = new Property<>("Grid", true, () -> mode.getValue().rounded);
    private static final Property<Boolean> customFont = new Property<>("Custom Font", true, () -> mode.getValue().rounded);
    private static final Property<Boolean> follow = new Property<>("Follow Target", false, () -> mode.getValue().rounded);
    private static final NumberProperty followOffset = new NumberProperty("Follow Offset", 16, () -> mode.getValue().rounded && follow.getValue(), -50, 100, 1);

    private static final NumberProperty tempestSize = new NumberProperty("Size", 0.85, () -> mode.getValue().tempestStyle() != null, 0.5, 1.5, 0.05);

    private final TempestHuds tempestHuds = new TempestHuds();

    private static final Property<Boolean> ravenHealth = new Property<>("Show Health", true, () -> mode.getValue().ravenStyle() != null);
    private static final Property<Boolean> ravenDifference = new Property<>("Show Difference", false, () -> mode.getValue().ravenStyle() != null);
    private static final Property<Boolean> ravenWinLoss = new Property<>("Show Win/Loss", true, () -> mode.getValue().ravenStyle() != null);

    private static final Property<Boolean> ravenTraditional = new Property<>("Traditional Health Color", false, () -> mode.getValue().ravenStyle() != null);

    private final RavenHuds ravenHuds = new RavenHuds();

    private static final NumberProperty opalScale = new NumberProperty("Opal Scale", 1.0, () -> mode.getValue() == Mode.Opal, 0.5, 3.0, 0.05);

    private final OpalHud opalHud = new OpalHud();
    private final PulsiveHud pulsiveHud = new PulsiveHud();
    private final StripHud stripHud = new StripHud();
    private final MoonHud moonHud = new MoonHud();

    private final TargetHuds roundedHuds = new TargetHuds(grid, customFont, follow, followOffset);
    private final Map<Mode, TargetHudMode> roundedModes = new EnumMap<>(Mode.class);

    {
        roundedModes.put(Mode.RoundedCompact, new RoundedCompactMode(roundedHuds));
        roundedModes.put(Mode.RoundedMinimal, new RoundedMinimalMode(roundedHuds));
    }

    private static final Color TENACITY_TINT = new Color(19, 19, 19);
    private static final float TENACITY_WIDTH = 75f;
    private static final float TENACITY_HEIGHT = 30f;
    private static final DecimalFormat TENACITY_HEALTH_FORMAT = new DecimalFormat("##.#");

    private static boolean positionInitialized = false;
    public static EntityLivingBase target;

    public static String disguiseName(EntityLivingBase t) {
        if (t == null) return "";
        if (t == mc.thePlayer && NickHiderModule.hides(t.getName())) return NickHiderModule.displayName();
        String d = rip.tempest.utils.misc.DisguiseManager.getDisguiseName(t.getName());
        return d != null ? d : t.getName();
    }

    private float myau2OldHealth = 0f;
    private float myau2NewHealth = 0f;
    private float myau2MaxHealth = 0f;
    private final Timer myau2AnimTimer = new Timer();

    private static final int HUD_WIDTH = 150;
    private static final int HUD_HEIGHT = 40;
    private static final int VANILLA_BAR_HEIGHT = 45;

    private float astralisHealthAnimation = 0.0F;

    private float smoothHealth = 0f;
    private long smoothHealthTime = 0L;

    private int getWaveColor(float x, int color1, int color2) {
        double time = System.currentTimeMillis() / 1000.0D;
        double waveOffset = x / 100.0D;
        double factor = (Math.sin(time * 3.0D + waveOffset) + 1.0D) / 2.0D;

        Color c1 = new Color(color1, true);
        Color c2 = new Color(color2, true);

        int r = (int) (c1.getRed() * factor + c2.getRed() * (1.0D - factor));
        int g = (int) (c1.getGreen() * factor + c2.getGreen() * (1.0D - factor));
        int b = (int) (c1.getBlue() * factor + c2.getBlue() * (1.0D - factor));
        int a = (int) (c1.getAlpha() * factor + c2.getAlpha() * (1.0D - factor));

        return new Color(r, g, b, a).getRGB();
    }

    private static TargetInterfaceModule instance;

    {
        if (instance == null) instance = this;
    }

    private static boolean overriding;
    private static EntityLivingBase overrideTarget;

    private final Map<Integer, TargetInterfaceModule> extraHuds = new java.util.HashMap<>();

    private final Map<Integer, Float> listHealth = new java.util.HashMap<>();
    private final RiseTargetHUD riseHud = new RiseTargetHUD();

    public static EntityLivingBase auraTarget() {
        if (overriding) return overrideTarget;
        return AuraModule.target();
    }

    public static boolean multiStacked() {
        return multiTargets.getValue() == MultiTargets.Stacked;
    }

    public static List<EntityLivingBase> extraTargets() {
        if (multiTargets.getValue() == MultiTargets.Off || mc.thePlayer == null) return java.util.Collections.emptyList();
        int count = multiTargetCount.getValue().intValue();
        List<EntityLivingBase> extras = new ArrayList<>();
        if (previewing()) {
            for (int i = 0; i < count; i++) extras.add(mc.thePlayer);
            return extras;
        }
        for (EntityLivingBase entity : AuraModule.multiTargets()) {
            if (extras.size() >= count) break;
            extras.add(entity);
        }
        return extras;
    }

    public static boolean previewing() {
        return mc.currentScreen instanceof GuiChat || clickGuiOpen();
    }

    public static boolean clickGuiOpen() {
        return mc.currentScreen != null && mc.currentScreen.getClass().getName().startsWith("rip.tempest.interfaces.click.");
    }

    public static void drawOverClickGui() {
        TargetInterfaceModule module = instance;
        if (module == null || !module.isEnabled() || mc.thePlayer == null || mc.theWorld == null || !clickGuiOpen()) return;
        GlStateManager.pushMatrix();
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        module.drawHud();
        module.drawExtras(null);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (!clickGuiOpen()) {
            drawHud();
            drawExtras(null);
        }
    };

    private void drawExtras(ShaderEvent shader) {
        if (instance != this) return;
        MultiTargets style = multiTargets.getValue();
        List<EntityLivingBase> extras = extraTargets();
        if (style != MultiTargets.Stacked || extras.isEmpty()) {
            if (shader == null) extraHuds.clear();
        }
        if (style != MultiTargets.List || extras.isEmpty()) {
            if (shader == null) listHealth.clear();
        }
        if (extras.isEmpty()) return;

        if (style == MultiTargets.Stacked && mode.getValue().rounded) return;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        if (d == null || d.getHeight() <= 1) return;
        double x = d.getX(), y = d.getY(), width = d.getWidth(), height = d.getHeight();
        boolean upward = y + height / 2.0 > new ScaledResolution(mc).getScaledHeight() / 2.0;

        if (style == MultiTargets.List) {
            drawTargetList(extras, x, y, width, height, upward, shader != null);
            return;
        }

        EntityLivingBase savedTarget = target;
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        try {
            for (int i = 0; i < extras.size(); i++) {
                EntityLivingBase entity = extras.get(i);
                int key = previewing() ? -1 - i : entity.getEntityId();
                shown.add(key);
                TargetInterfaceModule copy = extraHuds.computeIfAbsent(key, k -> new TargetInterfaceModule());
                double step = (i + 1) * (height + 4);
                d.setX(x);
                d.setY(upward ? y - step : y + step);
                overriding = true;
                overrideTarget = entity;
                if (shader == null) copy.drawHud();
                else copy.drawShaderPass(shader);
                overriding = false;
                overrideTarget = null;
            }
        } finally {
            overriding = false;
            overrideTarget = null;
            d.setX(x);
            d.setY(y);
            d.setWidth(width);
            d.setHeight(height);
            target = savedTarget;
        }
        if (shader == null) extraHuds.keySet().retainAll(shown);
    }

    private void drawTargetList(List<EntityLivingBase> extras, double x, double y, double hudWidth, double hudHeight,
                                boolean upward, boolean shapeOnly) {
        float width = (float) Math.max(90, Math.min(170, hudWidth));
        float rowHeight = 14, gap = 2;
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        for (int i = 0; i < extras.size(); i++) {
            EntityLivingBase entity = extras.get(i);
            float rowY = (float) (upward ? y - (i + 1) * (rowHeight + gap) : y + hudHeight + gap + i * (rowHeight + gap));
            float rowX = (float) x;
            if (shapeOnly) {
                Gui.drawRect((int) rowX, (int) rowY, (int) (rowX + width), (int) (rowY + rowHeight), 0xFF000000);
                continue;
            }
            int key = previewing() ? -1 - i : entity.getEntityId();
            shown.add(key);
            float max = Math.max(1f, entity.getMaxHealth());
            float ratio = Math.max(0f, Math.min(1f, entity.getHealth() / max));
            float shownRatio = listHealth.getOrDefault(key, ratio);
            shownRatio += (ratio - shownRatio) * 0.15f;
            listHealth.put(key, shownRatio);

            Color health = getHealthBlend(ratio);
            Gui.drawRect((int) rowX, (int) rowY, (int) (rowX + width), (int) (rowY + rowHeight), 0x96000000);
            Gui.drawRect((int) rowX, (int) (rowY + rowHeight - 2), (int) (rowX + width), (int) (rowY + rowHeight), 0x50000000);
            Gui.drawRect((int) rowX, (int) (rowY + rowHeight - 2), (int) (rowX + width * shownRatio), (int) (rowY + rowHeight), health.getRGB());

            String hp = String.format("%.1f", entity.getHealth());
            int hpWidth = mc.fontRendererObj.getStringWidth(hp);
            String name = disguiseName(entity);
            int room = (int) width - hpWidth - 10;
            if (mc.fontRendererObj.getStringWidth(name) > room) name = mc.fontRendererObj.trimStringToWidth(name, room - 6) + "..";
            mc.fontRendererObj.drawStringWithShadow(name, rowX + 3, rowY + 2.5f, 0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow(hp, rowX + width - hpWidth - 3, rowY + 2.5f, health.getRGB());
        }
        if (!shapeOnly) listHealth.keySet().retainAll(shown);
    }

    private void drawHud() {
        switch (mode.getValue()) {
            case Astolfo -> drawAstolfoTargetInterface();
            case Astralis -> drawAstralisTargetInterface();
            case Myau2 -> drawMyau2TargetInterface();
            case Rise -> drawRiseTargetInterface();
            case Novoline -> drawNovolineTargetInterface();
            case Adjust -> drawAdjustTargetInterface();
            case Sleek -> drawSleekTargetInterface();
            case Aura -> drawAuraTargetInterface();
            case Euphoria -> drawEuphoriaTargetInterface();
            case Tenacity -> drawTenacityTargetInterface();
            case ExhibitionOriginal -> drawExhibitionOriginalTargetInterface();
            case RoundedCompact, RoundedMinimal -> roundedHuds.render2D(roundedModes.get(mode.getValue()));
            case TempestModern, TempestLegacy, TempestSlim, TempestArmor,
                 TempestSegments -> drawTempest(false);
            case RavenModern, RavenLegacy -> drawRaven(false);
            case Opal -> drawOpal(false);
            case MoonMoon, MoonNovolineO, MoonCustom,
                 MoonNostalgia -> drawMoon(false);
            case Pulsive, Pulse, PulseSlim -> drawPulsive(false);
            case StripLine, StripStack, StripSimple, StripModern, StripBadge -> drawStrip(false);
        }
    }

    @EventLink
    public Listener<Render3DEvent> render3DEventListener = e -> {
        if (mode.getValue().rounded) roundedHuds.render3D();
    };

    @EventLink
    public Listener<WorldLoadEvent> worldLoadEventListener = e -> roundedHuds.reset();

    @EventLink
    public Listener<ShaderEvent> shaderEventListener = e -> {
        if (clickGuiOpen()) return;
        drawShaderPass(e);
        drawExtras(e);
    };

    private void drawShaderPass(ShaderEvent e) {
        switch (mode.getValue()) {
            case Astolfo -> drawAstolfoTargetInterface();
            case Astralis -> drawAstralisTargetInterface();
            case Myau2 -> drawMyau2TargetInterface();
            case Rise -> drawRiseTargetInterface();
            case Novoline -> drawNovolineTargetInterface();
            case Adjust -> drawAdjustTargetInterface();
            case Sleek -> drawSleekTargetInterface();
            case Aura -> drawAuraTargetInterface();
            case Euphoria -> drawEuphoriaTargetInterface();

            case Tenacity -> drawTenacityTargetBackground();
            case ExhibitionOriginal -> drawExhibitionOriginalBackground();

            case RoundedCompact, RoundedMinimal -> roundedHuds.renderShader(e, roundedModes.get(mode.getValue()));

            case TempestModern, TempestLegacy, TempestSlim, TempestArmor,
                 TempestSegments -> drawTempest(true);
            case RavenModern, RavenLegacy -> drawRaven(true);

            case Opal -> drawOpal(true);

            case MoonMoon, MoonCustom -> drawMoon(true);

            case Pulsive, Pulse, PulseSlim -> drawPulsive(true);

            case StripLine, StripStack, StripSimple, StripModern, StripBadge -> drawStrip(true);
        }
    }

    private void drawRaven(boolean shapeOnly) {
        RavenHuds.Style style = mode.getValue().ravenStyle();
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");

        if (!shapeOnly) {
            ravenHuds.update(previewing() ? mc.thePlayer : auraTarget());
            ravenHuds.animate(style);
        }
        String text = ravenHuds.text(ravenHealth.getValue(), ravenDifference.getValue(), ravenWinLoss.getValue());
        float[] size = ravenHuds.size(text);
        d.setWidth(size[0]);
        d.setHeight(size[1]);
        ravenHuds.render(style, text, (float) d.getX(), (float) d.getY(), ravenTraditional.getValue(), shapeOnly);
    }

    private void drawOpal(boolean shapeOnly) {
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");

        if (!shapeOnly) opalHud.update(previewing() ? mc.thePlayer : auraTarget());
        float scale = opalScale.getValue().floatValue();
        float[] size = opalHud.size();
        d.setWidth(size[0] * scale);
        d.setHeight(size[1] * scale);
        opalHud.render((float) d.getX(), (float) d.getY(), scale, shapeOnly);
    }

    private void drawPulsive(boolean shapeOnly) {
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");

        if (!shapeOnly) pulsiveHud.update(previewing() ? mc.thePlayer : auraTarget());
        PulsiveHud.Style style = mode.getValue().pulseStyle();
        float[] size = pulsiveHud.size(style);
        d.setWidth(size[0]);
        d.setHeight(size[1]);
        pulsiveHud.render(style, (float) d.getX(), (float) d.getY(), shapeOnly);
    }

    private void drawStrip(boolean shapeOnly) {
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");

        if (!shapeOnly) stripHud.update(previewing() ? mc.thePlayer : auraTarget());
        StripHud.Style style = mode.getValue().stripStyle();
        float[] size = stripHud.size(style);
        d.setWidth(size[0]);
        d.setHeight(size[1]);
        stripHud.render(style, (float) d.getX(), (float) d.getY(), shapeOnly);
    }

    private void drawMoon(boolean shapeOnly) {
        MoonHud.Style style = mode.getValue().moonStyle();
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");

        if (!shapeOnly) moonHud.update(style, previewing() ? mc.thePlayer : auraTarget());
        float[] size = moonHud.size(style);
        d.setWidth(size[0]);
        d.setHeight(size[1]);
        moonHud.render(style, (float) d.getX(), (float) d.getY(), shapeOnly);
    }

    private void drawTempest(boolean shapeOnly) {
        TempestHuds.Style style = mode.getValue().tempestStyle();
        initializePosition(new ScaledResolution(mc));
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        float scale = tempestSize.getValue().floatValue();
        d.setWidth(style.width * scale);
        d.setHeight(style.height * scale);

        if (!shapeOnly) tempestHuds.update(previewing() ? mc.thePlayer : auraTarget());
        tempestHuds.render(style, (float) d.getX(), (float) d.getY(), scale, shapeOnly);
    }

    private void initializePosition(ScaledResolution sr) {
        if (!positionInitialized && !DraggingProcess.components.containsKey("TargetInterface")) {
            DraggingProcess.components.put("TargetInterface", new DraggingProcess.DraggableComponent((sr.getScaledWidth() - HUD_WIDTH) / 2.0,
                    sr.getScaledHeight() - HUD_HEIGHT - VANILLA_BAR_HEIGHT - 5));
            positionInitialized = true;
        }
    }

    private void drawAstolfoTargetInterface() {
        target = auraTarget();

        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        DraggingProcess.DraggableComponent draggableComponent = DraggingProcess.components.get("TargetInterface");
        draggableComponent.setHeight(HUD_HEIGHT);
        draggableComponent.setWidth(HUD_WIDTH);

        GlStateManager.pushMatrix();
        GlStateManager.translate(draggableComponent.getX(), draggableComponent.getY(), 0);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float healthPercentage = health / maxHealth;

        Color color = ColorProcess.getColor();

        Gui.drawRect(0, 0, 125, 36, new Color(0, 0, 0, 150).getRGB());

        Gui.drawRect(37, 26, 89, 32, new Color(0, 0, 0, 255).getRGB());

        int healthWidth = (int) (52 * healthPercentage);
        Gui.drawRect(37, 26, 37 + healthWidth, 32, color.getRGB());

        RenderUtils.resetColor();
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        GuiInventory.drawEntityOnScreen(15, 32, 16, -target.rotationYaw, target.rotationPitch, target);

        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.popMatrix();

        mc.fontRendererObj.drawString(disguiseName(target), 38, 2, -1, true);

        GlStateManager.pushMatrix();
        GlStateManager.scale(2, 2, 2);
        mc.fontRendererObj.drawStringWithShadow((Math.round(target.getHealth() * 10f) / 10f + "❤").replace(".0", ""), 19,
                5, color.getRGB());
        GlStateManager.popMatrix();

        GlStateManager.popMatrix();
    }

    private void drawTenacityTargetInterface() {
        if (!drawTenacityTargetBackground()) return;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        float x = (float) d.getX();
        float y = (float) d.getY();

        Color barStart = ColorProcess.colors.getFirst();
        Color barEnd = ColorProcess.colors.getSecond();

        CustomFont name = FontUtil.getFont("OpenSans-Medium", 22);
        CustomFont health = FontUtil.getFont("OpenSans-Medium", 16);
        float healthPercentage = (target.getHealth() + target.getAbsorptionAmount()) / (target.getMaxHealth() + target.getAbsorptionAmount());

        String displayName = target instanceof EntityPlayer ? disguiseName(target) : target.getDisplayName().getUnformattedText();
        name.drawString(displayName, x + 2.5f, y + 7.5f, -1);
        health.drawString(TENACITY_HEALTH_FORMAT.format(target.getHealth()), x + 60f, y + 10f, -1);
        RoundedUtil.drawRound(x + 2.5f, y + 22.5f, 70f * healthPercentage, 5f, 2,
                ColorUtil.interpolateColorsBackAndForth(5, 1, barStart, barEnd, false));
    }

    private boolean drawTenacityTargetBackground() {
        target = previewing() ? mc.thePlayer : auraTarget();
        initializePosition(new ScaledResolution(mc));
        if (target == null) return false;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) TENACITY_WIDTH);
        d.setHeight((int) TENACITY_HEIGHT);

        RoundedUtil.drawRound((float) d.getX(), (float) d.getY(), TENACITY_WIDTH, TENACITY_HEIGHT, 8,
                ColorUtil.applyOpacity(TENACITY_TINT, tenacityOpacity.getValue().floatValue()));
        return true;
    }

    private void drawExhibitionOriginalTargetInterface() {
        if (!drawExhibitionOriginalBackground()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        double x = d.getX() + 2.5, y = d.getY() + 2.5;
        EntityLivingBase player = target;
        String name = disguiseName(player);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);

        RenderingUtil.rectangleBordered(2, 2, 38, 38, 0.5, Colors.getColor(0, 0), Colors.getColor(10));
        RenderingUtil.rectangleBordered(2.5, 2.5, 38 - 0.5, 38 - 0.5, 0.5, Colors.getColor(17), Colors.getColor(48));

        GlStateManager.pushMatrix();
        int factor = sr.getScaleFactor();
        GL11.glScissor((int) ((x + 3) * factor), (int) ((sr.getScaledHeight() - (y + 37)) * factor), (int) ((37 - 3) * factor), (int) ((37 - 3) * factor));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RendererLivingEntity.hideNameTags = true;
        drawExhibitionEntity(player);
        RendererLivingEntity.hideNameTags = false;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();

        GlStateManager.translate(2, 0, 0);

        ExhibitionFonts.calibri18.drawStringWithShadow(name, 37, 3, -1);

        float health = player.getHealth();
        float[] fractions = new float[]{0f, 0.5f, 1f};
        Color[] colors = new Color[]{Color.RED, Color.YELLOW, Color.GREEN};

        float absorption = player.getAbsorptionAmount();
        float progress = health / (player.getMaxHealth() + absorption);
        float realHealthProgress = (health / player.getMaxHealth());

        Color customColor = health >= 0 ? blendColors(fractions, colors, realHealthProgress).brighter() : Color.RED;
        double width = Math.min(mc.fontRendererObj.getStringWidth(name), 60);

        width = rip.tempest.interfaces.click.exhibition.util.MathUtils.getIncremental(width, 10);
        if (width < 60) {
            width = 60;
        }
        double healthLocation = width * progress;

        RenderingUtil.rectangleBordered(37, 12, 39 + width, 16, 0.5, Colors.getColor(0, 0), Colors.getColor(0));
        RenderingUtil.rectangle(38 + healthLocation + 0.5, 12.5, 38 + width + 0.5, 15.5, Colors.getColorOpacity(customColor.getRGB(), 35));
        RenderingUtil.rectangle(37.5, 12.5, 38 + healthLocation + 0.5, 15.5, customColor.getRGB());

        if (absorption > 0) {
            double absorptionDifferent = width * (absorption / (player.getMaxHealth() + absorption));
            RenderingUtil.rectangle(38 + healthLocation + 0.5, 12.5, 38 + healthLocation + 0.5 + absorptionDifferent, 15.5, 0x80FFAA00);
        }

        for (int i = 1; i < 10; i++) {
            double dThing = (width / 10) * i;
            RenderingUtil.rectangle(38 + dThing, 12, 38 + dThing + 0.5, 16, Colors.getColor(0));
        }

        String str = "HP: " + (int) health + " | Dist: " + (int) mc.thePlayer.getDistanceToEntity(player);
        ExhibitionFonts.calibri10.drawStringWithShadow(str, 37, 18, -1);

        if (player instanceof EntityPlayer) {
            drawExhibitionEquipment((EntityPlayer) player);
        }

        GlStateManager.popMatrix();
    }

    private boolean drawExhibitionOriginalBackground() {
        target = previewing() ? mc.thePlayer : auraTarget();
        initializePosition(new ScaledResolution(mc));
        if (target == null) return false;
        ExhibitionFonts.load();

        double renderWidth = Math.max(40 + mc.fontRendererObj.getStringWidth(disguiseName(target)), 120);
        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) Math.ceil(renderWidth + 5));
        d.setHeight(45);

        GlStateManager.pushMatrix();
        GlStateManager.translate(d.getX() + 2.5, d.getY() + 2.5, 0);
        RenderingUtil.rectangleBordered(-2.5, -2.5, renderWidth + 2.5, 40 + 2.5, 0.5, Colors.getColor(60), Colors.getColor(10));
        RenderingUtil.rectangleBordered(-1.5, -1.5, renderWidth + 1.5, 40 + 1.5, 1.5, Colors.getColor(60), Colors.getColor(40));
        RenderingUtil.rectangleBordered(0, 0, renderWidth, 40, 0.5, Colors.getColor(22), Colors.getColor(60));
        GlStateManager.popMatrix();
        return true;
    }

    private void drawExhibitionEquipment(EntityPlayer target) {
        GL11.glPushMatrix();
        final List<ItemStack> items = new ArrayList<>();
        int split = 20;
        for (int index = 3; index >= 0; --index) {
            final ItemStack armor = target.inventory.armorInventory[index];
            if (armor != null) {
                items.add(armor);
            }
        }
        int yOffset = 23;
        if (target.getCurrentEquippedItem() != null) {
            items.add(target.getCurrentEquippedItem());
        }
        RenderHelper.enableGUIStandardItemLighting();
        for (final ItemStack itemStack : items) {
            if (mc.theWorld != null) {
                split += 16;
            }
            GlStateManager.pushMatrix();
            GlStateManager.disableAlpha();
            GlStateManager.clear(256);
            mc.getRenderItem().zLevel = -150.0f;
            mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, split, yOffset);
            mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, itemStack, split, yOffset);
            mc.getRenderItem().zLevel = 0.0f;

            int renderY = yOffset;
            if (itemStack.getItem() instanceof ItemSword) {
                int sLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack);
                int fLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack);
                int kLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, itemStack);
                int uLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, itemStack);
                if (sLevel > 0) {
                    drawEnchantTag("S" + enchantColor(sLevel) + sLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (fLevel > 0) {
                    drawEnchantTag("F" + enchantColor(fLevel) + fLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (kLevel > 0) {
                    drawEnchantTag("K" + enchantColor(kLevel) + kLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (uLevel > 0) {
                    drawEnchantTag("U" + enchantColor(uLevel) + uLevel, split, renderY);
                }
            } else if (itemStack.getItem() instanceof ItemArmor) {
                int pLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, itemStack);
                int tLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, itemStack);
                int uLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, itemStack);
                if (pLevel > 0) {
                    drawEnchantTag("P" + enchantColor(pLevel) + pLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (tLevel > 0) {
                    drawEnchantTag("T" + enchantColor(tLevel) + tLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (uLevel > 0) {
                    drawEnchantTag("U" + enchantColor(uLevel) + uLevel, split, renderY);
                }
            } else if (itemStack.getItem() instanceof ItemBow) {
                int powLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack);
                int punLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, itemStack);
                int fireLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemStack);
                if (powLevel > 0) {
                    drawEnchantTag("Pow" + enchantColor(powLevel) + powLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (punLevel > 0) {
                    drawEnchantTag("Pun" + enchantColor(punLevel) + punLevel, split, renderY);
                    renderY += 4.5F;
                }
                if (fireLevel > 0) {
                    drawEnchantTag("F" + enchantColor(fireLevel) + fireLevel, split, renderY);
                }
            } else if (itemStack.getRarity() == EnumRarity.EPIC) {
                drawEnchantTag("\247e\247lGod", split, renderY);
            }

            GlStateManager.disableBlend();
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glPopMatrix();
    }

    private static void drawEnchantTag(String text, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        ExhibitionFonts.calibri10.drawBorderedString(text, x, y, -1, Colors.getColor(0, 200));
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static String enchantColor(int level) {
        switch (level) {
            case 2:
                return "\247a";
            case 3:
                return "\2473";
            case 4:
                return "\2474";
            case 5:
                return "\2476";
            default:
                return "\247f";
        }
    }

    private void drawExhibitionEntity(EntityLivingBase ent) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) 20, (float) 36, 50.0F);

        float largestSize = Math.max(ent.height, ent.width);
        float relativeScale = Math.max(largestSize / 1.8F, 1);

        GlStateManager.scale((float) -16 / relativeScale, (float) 16 / relativeScale, (float) 16 / relativeScale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-((float) Math.atan((double) ((float) 17 / 40.0F))) * 20.0F, 1.0F, 0.0F, 0.0F);
        RenderManager renderManager = mc.getRenderManager();
        renderManager.setPlayerViewY(180.0F);
        renderManager.setRenderShadow(false);
        renderManager.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        renderManager.setRenderShadow(true);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static Color blendColors(float[] fractions, Color[] colors, float progress) {
        if (fractions == null || colors == null || fractions.length != colors.length) return Color.RED;
        int startPoint = 0;
        while (startPoint < fractions.length && fractions[startPoint] <= progress) {
            startPoint++;
        }
        if (startPoint >= fractions.length) {
            startPoint = fractions.length - 1;
        }
        int from = Math.max(0, startPoint - 1);
        float max = fractions[startPoint] - fractions[from];
        float weight = max == 0 ? 1 : (progress - fractions[from]) / max;
        float r = 1f - weight, ir = 1f - r;

        float[] rgb1 = colors[from].getColorComponents(null);
        float[] rgb2 = colors[startPoint].getColorComponents(null);
        try {
            return new Color(clamp01(rgb1[0] * r + rgb2[0] * ir), clamp01(rgb1[1] * r + rgb2[1] * ir), clamp01(rgb1[2] * r + rgb2[2] * ir));
        } catch (IllegalArgumentException e) {
            return Color.RED;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private Color getHealthBlend(float percent) {
        Color red    = new Color(255, 0,   0);
        Color yellow = new Color(255, 255, 0);
        Color green  = new Color(0,   255, 0);
        if (percent >= 0.9f) return green;
        if (percent >= 0.55f) return interpolateColor((percent - 0.55f) / 0.35f, yellow, green);
        if (percent >= 0.45f) return yellow;
        if (percent >= 0.1f)  return interpolateColor((percent - 0.1f)  / 0.35f, red,    yellow);
        return red;
    }

    private Color interpolateColor(float t, Color a, Color b) {
        t = Math.min(1, Math.max(0, t));
        return new Color(
                (int)(a.getRed()   + t * (b.getRed()   - a.getRed())),
                (int)(a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int)(a.getBlue()  + t * (b.getBlue()  - a.getBlue()))
        );
    }

    private Color darkerColor(Color color, float factor) {
        return new Color(
                Math.min(255, Math.max(0, (int)(color.getRed()   * factor))),
                Math.min(255, Math.max(0, (int)(color.getGreen() * factor))),
                Math.min(255, Math.max(0, (int)(color.getBlue()  * factor))),
                color.getAlpha()
        );
    }

    private void drawMyau2TargetInterface() {
        target = auraTarget();
        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        float health = target.getHealth() / 2.0f;
        float abs    = target.getAbsorptionAmount() / 2.0f;
        float heal   = health + abs;
        float myHeal = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0f;

        if (!myau2AnimTimer.hasTimeElapsed(150, false)) {
        } else {
            myau2OldHealth = myau2NewHealth;
            myau2NewHealth = heal;
            myau2MaxHealth = target.getMaxHealth() / 2.0f;
            if (myau2OldHealth != myau2NewHealth) {
                myau2AnimTimer.reset();
            }
        }

        float elapsed  = Math.min(Math.max(myau2AnimTimer.getTime(), 0), 150);
        float lerped   = myau2OldHealth + (myau2NewHealth - myau2OldHealth) * (elapsed / 150f);
        float lerpedHealthRatio = Math.min(1, Math.max(0, myau2MaxHealth > 0 ? lerped / myau2MaxHealth : 0));

        Color accentColor     = ColorProcess.getColor();
        Color healthBarColor  = getHealthBlend(lerpedHealthRatio);
        float healthDeltaRatio = Math.min(1, Math.max(0, (myHeal - heal + 1f) / 2f));
        Color healthDeltaColor = getHealthBlend(healthDeltaRatio);

        String targetName  = disguiseName(target);
        int    nameWidth   = mc.fontRendererObj.getStringWidth(targetName);

        String healthText  = String.format("%.1f%s\u2764", heal, abs > 0 ? "" : "");
        int    healthWidth = mc.fontRendererObj.getStringWidth(healthText);
        int    healthColor = abs > 0 ? new Color(255, 170, 0).getRGB() : new Color(255, 85, 85).getRGB();

        String statusText  = heal == myHeal ? "D" : (heal < myHeal ? "W" : "L");
        int    statusWidth = mc.fontRendererObj.getStringWidth(statusText);

        String diffText    = heal == myHeal ? "0.0" : String.format("%+.1f", myHeal - heal);
        int    diffWidth   = mc.fontRendererObj.getStringWidth(diffText);

        float barContentWidth = Math.max(
                nameWidth   + 2f + statusWidth + 2f,
                healthWidth + 2f + diffWidth   + 2f
        );

        float headOffset   = 25.0f;
        float barTotalWidth = Math.max(headOffset + 70f, headOffset + 2f + barContentWidth + 2f);

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) barTotalWidth);
        d.setHeight(27);

        float x = (float) d.getX();
        float y = (float) d.getY();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);

        RenderUtils.drawRoundedRect(0, 0, barTotalWidth, 27, 3, new Color(0, 0, 0, 180));
        RenderUtils.drawRoundOutline(0, 0, barTotalWidth, 27, 3, 1f,
                new Color(0, 0, 0, 180), accentColor);

        Gui.drawRect((int)(headOffset + 2), 22, (int)(barTotalWidth - 2), 25,
                darkerColor(healthBarColor, 0.2f).getRGB());

        float fillWidth = lerpedHealthRatio * (barTotalWidth - 2f - headOffset - 2f);
        Gui.drawRect((int)(headOffset + 2), 22, (int)(headOffset + 2 + fillWidth), 25,
                healthBarColor.getRGB());

        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        mc.fontRendererObj.drawString(targetName, headOffset + 2, 2, Color.WHITE.getRGB(), true);
        mc.fontRendererObj.drawString(healthText,  headOffset + 2, 12, healthColor, true);

        mc.fontRendererObj.drawString(statusText, barTotalWidth - 2 - statusWidth, 2,
                healthDeltaColor.getRGB(), true);
        mc.fontRendererObj.drawString(diffText, barTotalWidth - 2 - diffWidth, 12,
                darkerColor(healthDeltaColor, 0.8f).getRGB(), true);

        if (target instanceof net.minecraft.entity.player.EntityPlayer) {
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(
                    ((net.minecraft.client.entity.AbstractClientPlayer) target).getLocationSkin());
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                    2, 2, 8, 8, 8, 8, 23, 23, 64, 64);
            if (((net.minecraft.entity.player.EntityPlayer) target)
                    .func_175148_a(net.minecraft.entity.player.EnumPlayerModelParts.HAT)) {
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                        2, 2, 40, 8, 8, 8, 23, 23, 64, 64);
            }
            GlStateManager.color(1, 1, 1, 1);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private float smoothHealthRatio(EntityLivingBase t) {
        float goal = Math.min(1f, Math.max(0f, t.getHealth() / Math.max(1f, t.getMaxHealth())));
        long now = System.currentTimeMillis();
        float dt = smoothHealthTime == 0 ? 1f : (now - smoothHealthTime) / 1000f;
        smoothHealthTime = now;
        float alpha = 1f - (float) Math.exp(-dt / 0.12f);
        smoothHealth += (goal - smoothHealth) * alpha;
        return Math.min(1f, Math.max(0f, smoothHealth));
    }

    private void drawTargetHead(float x, float y, float size) {
        if (!(target instanceof net.minecraft.entity.player.EntityPlayer)) return;
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(((net.minecraft.client.entity.AbstractClientPlayer) target).getLocationSkin());
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8, 8, 8, 8, (int) size, (int) size, 64, 64);
        if (((net.minecraft.entity.player.EntityPlayer) target).func_175148_a(net.minecraft.entity.player.EnumPlayerModelParts.HAT)) {
            Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 40, 8, 8, 8, (int) size, (int) size, 64, 64);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawSleekTargetInterface() {
        target = previewing() ? mc.thePlayer : auraTarget();
        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);
        if (target == null) return;

        var fr = FontProcess.getFont("tempest");
        float hp = target.getHealth() / 2f;
        float myHp = mc.thePlayer.getHealth() / 2f;
        float ratio = smoothHealthRatio(target);
        Color healthColor = getHealthBlend(ratio);

        String name = disguiseName(target);
        String hpText = (Math.round(hp * 10f) / 10f + "❤").replace(".0", "");
        String status = hp == myHp ? "D" : (hp < myHp ? "W" : "L");
        String diff = hp == myHp ? "0.0" : String.format("%+.1f", myHp - hp);

        float head = 21f;
        float contentW = Math.max(fr.getStringWidth(name) + 6 + fr.getStringWidth(status),
                fr.getStringWidth(hpText) + 6 + fr.getStringWidth(diff));
        float width = 6 + head + 8 + contentW + 8;
        float height = 33;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) width);
        d.setHeight((int) height);
        float x = (float) d.getX(), y = (float) d.getY();

        RenderUtils.drawRoundedRect(x, y, width, height, 5f, new Color(16, 17, 22, 210));
        RenderUtils.drawRoundOutline(x, y, width, height, 5f, 0.5f, new Color(0, 0, 0, 0), new Color(255, 255, 255, 22));

        GlStateManager.enableBlend();
        drawTargetHead(x + 6, y + 6, head);
        GlStateManager.disableBlend();

        float textX = x + 6 + head + 8;
        fr.drawStringWithShadow(name, textX, y + 5, Color.WHITE.getRGB());
        fr.drawStringWithShadow(hpText, textX, y + 15, healthColor.getRGB());
        fr.drawStringWithShadow(status, x + width - 8 - fr.getStringWidth(status), y + 5, healthColor.getRGB());
        fr.drawStringWithShadow(diff, x + width - 8 - fr.getStringWidth(diff), y + 15, new Color(160, 162, 170).getRGB());

        float barX = textX, barY = y + height - 6, barW = width - (textX - x) - 8, barH = 3.5f;
        RenderUtils.drawRoundedRect(barX, barY, barW, barH, barH / 2f, new Color(255, 255, 255, 28));
        RenderUtils.drawRoundedRect(barX, barY, barW * ratio, barH, barH / 2f, healthColor);
    }

    private void drawEuphoriaTargetInterface() {
        target = previewing() ? mc.thePlayer : auraTarget();
        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);
        if (target == null) return;

        var fr = FontProcess.getFont("tempest");
        float ratio = smoothHealthRatio(target);
        Color accent = ColorProcess.getColor();
        String name = disguiseName(target);

        List<ItemStack> items = new ArrayList<>();
        if (target.getHeldItem() != null) items.add(target.getHeldItem());
        for (int i = 3; i >= 0; i--) {
            if (target.getCurrentArmor(i) != null) items.add(target.getCurrentArmor(i));
        }

        final float pad = 3f, head = 16f, iconSize = 9f, iconGap = 1f, barH = 4f;
        float iconsW = items.isEmpty() ? 0 : items.size() * (iconSize + iconGap) - iconGap;
        float contentW = Math.max(fr.getStringWidth(name), iconsW);

        float width = Math.max(100f, pad + head + 4f + contentW + 10f);

        float height = pad + head + pad + barH;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) width);
        d.setHeight((int) height);
        float x = (float) d.getX(), y = (float) d.getY();

        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, new Color(0, 0, 0, 180).getRGB());
        Gui.drawRect(x, y, x + width, y + height, new Color(20, 18, 26, 235).getRGB());

        GlStateManager.enableBlend();
        drawTargetHead(x + pad, y + pad, head);
        GlStateManager.disableBlend();

        float contentX = x + pad + head + 4f;
        fr.drawStringWithShadow(name, contentX, y + pad, Color.WHITE.getRGB());

        if (!items.isEmpty()) {
            float scale = iconSize / 16f;
            float iconY = y + pad + fr.getHeight() + 1f;
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, scale);
            for (int i = 0; i < items.size(); i++) {
                float ix = contentX + i * (iconSize + iconGap);
                mc.getRenderItem().renderItemAndEffectIntoGUI(items.get(i), (int) (ix / scale), (int) (iconY / scale));
            }
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
        }

        float barY = y + height - barH;
        Gui.drawRect(x, barY, x + width, barY + barH, new Color(0, 0, 0, 190).getRGB());
        Gui.drawRect(x + 1, barY + 1, x + width - 1, barY + barH - 1, new Color(255, 255, 255, 22).getRGB());
        float fill = (width - 2f) * ratio;
        if (fill > 0.5f) {
            Gui.drawRect(x + 1, barY + 1, x + 1 + fill, barY + barH - 1, accent.getRGB());
        }
    }

    private void drawAuraTargetInterface() {
        target = previewing() ? mc.thePlayer : auraTarget();
        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);
        if (target == null) return;

        var fr = FontProcess.getFont("tempest");
        float hp = target.getHealth() / 2f;
        float ratio = smoothHealthRatio(target);
        float dist = mc.thePlayer.getDistanceToEntity(target);
        Color accent = ColorProcess.getColor();

        String name = disguiseName(target);
        String hpText = String.format("%.1f hp", hp);
        String distText = String.format("%.1f m", dist);

        float pad = 8;
        float contentW = Math.max(fr.getStringWidth(name), fr.getStringWidth(hpText) + 8 + fr.getStringWidth(distText));
        float width = pad * 2 + Math.max(118, contentW);
        float height = 32;

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get("TargetInterface");
        d.setWidth((int) width);
        d.setHeight((int) height);
        float x = (float) d.getX(), y = (float) d.getY();

        RenderUtils.drawRoundedRect(x, y, width, height, 5f, new Color(14, 15, 20, 205));

        fr.drawStringWithShadow(name, x + pad, y + 5, Color.WHITE.getRGB());
        fr.drawStringWithShadow(hpText, x + pad, y + 15, accent.getRGB());
        fr.drawStringWithShadow(distText, x + width - pad - fr.getStringWidth(distText), y + 15, new Color(150, 152, 160).getRGB());

        float barX = x + pad, barY = y + height - 6, barW = width - pad * 2, barH = 3.5f;
        RenderUtils.drawRoundedRect(barX, barY, barW, barH, barH / 2f, new Color(255, 255, 255, 28));
        RenderUtils.drawRoundedRect(barX, barY, barW * ratio, barH, barH / 2f, accent);
    }

    private void drawAstralisTargetInterface() {
        target = auraTarget();

        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        DraggingProcess.DraggableComponent draggableComponent = DraggingProcess.components.get("TargetInterface");
        draggableComponent.setHeight(40);
        draggableComponent.setWidth(140);

        float x = (float) draggableComponent.getX();
        float y = (float) draggableComponent.getY();

        float width = 140;
        float height = 40;
        float padding = 6;
        float faceSize = height - padding * 2;

        float health = Math.max(0.0F, target.getHealth());
        float maxHealth = Math.max(1.0F, target.getMaxHealth());
        float healthPercent = Math.min(health / maxHealth, 1.0F);

        astralisHealthAnimation += (healthPercent - astralisHealthAnimation) * 0.18F;

        Color accent = ColorProcess.getColor();
        Color background = new Color(5, 5, 10, 145);
        Color panel = new Color(30, 30, 35, 150);

        RenderUtils.drawRoundedRect(x, y, width, height, 8, background);
        RenderUtils.drawRoundOutline(x, y, width, height, 8, 0.5f, new Color(0, 0, 0, 0), new Color(255, 255, 255, 25));

        if (target instanceof AbstractClientPlayer) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());

            Gui.drawScaledCustomSizeModalRect(
                    (int) (x + padding),
                    (int) (y + padding),
                    8,
                    8,
                    8,
                    8,
                    (int) faceSize,
                    (int) faceSize,
                    64,
                    64
            );

            if (((EntityPlayer) target).func_175148_a(EnumPlayerModelParts.HAT)) {
                Gui.drawScaledCustomSizeModalRect(
                        (int) (x + padding),
                        (int) (y + padding),
                        40,
                        8,
                        8,
                        8,
                        (int) faceSize,
                        (int) faceSize,
                        64,
                        64
                );
            }

            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) mc.thePlayer).getLocationSkin());

            Gui.drawScaledCustomSizeModalRect(
                    (int) (x + padding),
                    (int) (y + padding),
                    8,
                    8,
                    8,
                    8,
                    (int) faceSize,
                    (int) faceSize,
                    64,
                    64
            );

            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }

        float textX = x + faceSize + padding * 2;
        float contentWidth = width - faceSize - padding * 3;

        String targetName = disguiseName(target);

        if (FontProcess.getFont("bold").getStringWidth(targetName) > contentWidth) {
            targetName = targetName.substring(0, Math.min(targetName.length(), 12)) + "...";
        }

        FontProcess.getFont("bold").drawString(targetName, textX, y + padding, Color.WHITE.getRGB());

        String healthText = String.format("%.1f", health);
        FontProcess.getFont("tempest").drawString(healthText, textX, y + padding + 12, new Color(220, 220, 230).getRGB());

        float barHeight = 6;
        float barY = y + height - padding - barHeight;

        RenderUtils.drawRoundedRect(textX, barY, contentWidth, barHeight, barHeight / 2.0F, panel);

        float barWidth = contentWidth * Math.min(Math.max(astralisHealthAnimation, 0.0F), 1.0F);

        if (barWidth > 0.0F) {
            RenderUtils.drawRoundedRect(textX, barY, barWidth, barHeight, 3, accent);
        }

        if (target.hurtTime > 0) {
            RenderUtils.drawRoundedRect(x, y, width, height, 8,
                    new Color(255, 60, 60, target.hurtTime * 8));
        }

        draggableComponent.setWidth((int) width);
        draggableComponent.setHeight((int) height);
    }

    private Color interpolateHealthColor(float healthRatio) {
        if (healthRatio > 0.5F) {
            return RenderUtils.interpolateColorC(new Color(255, 255, 85), new Color(85, 255, 85), (healthRatio - 0.5F) / 0.5F);
        } else {
            return RenderUtils.interpolateColorC(new Color(255, 85, 85), new Color(255, 255, 85), healthRatio * 2);
        }
    }

    private void drawRiseTargetInterface() {
        target = auraTarget();

        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        DraggingProcess.DraggableComponent draggableComponent = DraggingProcess.components.get("TargetInterface");
        draggableComponent.setHeight(50);
        draggableComponent.setWidth(150);

        riseHud.render(draggableComponent.getX(), draggableComponent.getY(), target);
    }

    public static class RiseTargetHUD {
        private static final int MIN_WIDTH = 128;
        private static final int HEIGHT = 50;

        private boolean sentParticles = false;
        private final List<Particle> particles = new ArrayList<>();
        private long lastUpdate = System.currentTimeMillis();
        private float animatedHealthBar = 0;

        public void render(double x, double y, EntityLivingBase target) {
            if (target == null) return;

            String name = disguiseName(target);
            int width = Math.max(MIN_WIDTH, mc.fontRendererObj.getStringWidth("Name: " + name) + 60);

            Color backgroundColor = new Color(0, 0, 0, 110);
            int textColor = Color.WHITE.getRGB();

            RenderUtils.drawRoundedRect(x, y, width, HEIGHT, 6, backgroundColor);

            final int scaleOffset = (int) (target.hurtTime * 0.35f);

            float health = target.getHealth() + target.getAbsorptionAmount();
            float maxHealth = target.getMaxHealth() + target.getAbsorptionAmount();
            float healthPercent = health / maxHealth;
            float targetHealthBar = (width - 28) * healthPercent;

            animatedHealthBar = lerp(animatedHealthBar, targetHealthBar, 0.18f);

            GlStateManager.pushMatrix();
            Color color1 = ColorProcess.getColor();
            Color color2 = ColorProcess.getColor();
            drawGradientRect((int) (x + 5), (int) (y + 40), (int) (x + 5 + animatedHealthBar), (int) (y + 45), color1.getRGB(), color2.getRGB());
            GlStateManager.popMatrix();

            for (Particle p : particles) {
                p.x = (float) (x + 20);
                p.y = (float) (y + 20);
                if (p.opacity > 4) p.render2D();
            }

            if (target instanceof AbstractClientPlayer) {
                final double offset = -(target.hurtTime * 23);
                Color playerTint = new Color(255, (int) (255 + offset), (int) (255 + offset));
                GlStateManager.color(playerTint.getRed() / 255f, playerTint.getGreen() / 255f, playerTint.getBlue() / 255f, 1f);

                mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());
                Gui.drawScaledCustomSizeModalRect((int) (x + 5 + scaleOffset / 2f), (int) (y + 5 + scaleOffset / 2f),
                        8, 8, 8, 8, (int) (30 - scaleOffset), (int) (30 - scaleOffset), 64, 64);

                GlStateManager.color(1, 1, 1, 1);
            } else {
                final double offset = -(target.hurtTime * 23);
                Color playerTint = new Color(255, (int) (255 + offset), (int) (255 + offset));
                GlStateManager.color(playerTint.getRed() / 255f, playerTint.getGreen() / 255f, playerTint.getBlue() / 255f, 1f);

                mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
                Gui.drawScaledCustomSizeModalRect((int) (x + 5 + scaleOffset / 2f), (int) (y + 5 + scaleOffset / 2f),
                        8, 8, 8, 8, (int) (30 - scaleOffset), (int) (30 - scaleOffset), 64, 64);

                GlStateManager.color(1, 1, 1, 1);
            }

            if (System.currentTimeMillis() - lastUpdate >= 16) {
                for (int i = particles.size() - 1; i >= 0; i--) {
                    Particle p = particles.get(i);
                    p.updatePosition();
                    if (p.opacity < 1) particles.remove(i);
                }
                lastUpdate = System.currentTimeMillis();
            }

            double healthNum = Math.round(health * 10.0) / 10.0;
            FontProcess.getFont("tempest").drawString(String.valueOf(healthNum), (float) (x + animatedHealthBar + 8), (float) (y + 38), textColor);

            FontProcess.getFont("bold").drawString("Name: " + name, (float) (x + 40), (float) (y + 10), textColor);

            float distance = mc.thePlayer.getDistanceToEntity(target);
            String statsText = String.format("Distance: %.1f Hurt: %d", distance, target.hurtTime);
            FontProcess.getFont("tempest").drawString(statsText, (float) (x + 40), (float) (y + 22), textColor);

            if (target.hurtTime == 9 && !sentParticles) {
                for (int i = 0; i <= 15; i++) {
                    Particle particle = new Particle();
                    particle.init((float) (x + 20), (float) (y + 20),
                            (float) (((Math.random() - 0.5) * 2) * 1.4),
                            (float) (((Math.random() - 0.5) * 2) * 1.4),
                            (float) (Math.random() * 4),
                            i % 2 == 0 ? color1 : color2);
                    particles.add(particle);
                }
                sentParticles = true;
            }
            if (target.hurtTime == 8) sentParticles = false;
        }

        private static float lerp(float current, float target, float speed) {
            return current + (target - current) * speed;
        }

        private static void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
            Gui.drawRect(left, top, right, bottom, startColor);
        }

        public static class Particle {
            public float x, y, adjustedX, adjustedY, deltaX, deltaY, size, opacity;
            public Color color;

            public void render2D() {
                Color renderColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (opacity));
                RenderUtils.drawRoundedRect(x + adjustedX, y + adjustedY, size, size, (size / 2f) - 0.5f, renderColor);
            }

            public void updatePosition() {
                for (int i = 1; i <= 2; i++) {
                    adjustedX += deltaX;
                    adjustedY += deltaY;
                    deltaY *= 0.97;
                    deltaX *= 0.97;
                    opacity -= 1f;
                    if (opacity < 1) opacity = 1;
                }
            }

            public void init(float x, float y, float deltaX, float deltaY, float size, Color color) {
                this.x = x;
                this.y = y;
                this.deltaX = deltaX;
                this.deltaY = deltaY;
                this.size = size;
                this.opacity = 254;
                this.color = color;
            }
        }
    }

    private void renderPlayerSkin(EntityLivingBase player, int x, int y) {
        List<NetworkPlayerInfo> playerInfoList = GuiPlayerTabOverlay.field_175252_a.sortedCopy(mc.thePlayer.sendQueue.getPlayerInfoMap());
        for (NetworkPlayerInfo info : playerInfoList) {
            if (mc.theWorld.getPlayerEntityByUUID(info.getGameProfile().getId()) == player) {
                mc.getTextureManager().bindTexture(info.getLocationSkin());

                Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, 32, 32, 64, 64);

                if (player.func_175148_a(EnumPlayerModelParts.HAT)) {
                    Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, 32, 32, 64, 64);
                }

                break;
            }
        }
    }

    private void drawNovolineTargetInterface() {
        target = auraTarget();

        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        DraggingProcess.DraggableComponent draggableComponent = DraggingProcess.components.get("TargetInterface");
        draggableComponent.setHeight(40);
        draggableComponent.setWidth(150);

        float x = (float) draggableComponent.getX();
        float y = (float) draggableComponent.getY();

        int headSize = 24;
        int maxL = 100;

        String name = disguiseName(target);

        Color backgroundColor = new Color(45, 45, 45);
        Color backgroundColor2 = new Color(21, 21, 21);

        Gui.drawRect((int) (x - 1), (int) (y - 1), (int) (x + 2 + headSize + maxL + 1), (int) (y + 2 + headSize + 2 + 1), backgroundColor2.getRGB());

        Gui.drawRect((int) x, (int) y, (int) (x + 2 + headSize + maxL), (int) (y + 2 + headSize + 2), backgroundColor.getRGB());

        if (target instanceof AbstractClientPlayer) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());
            Gui.drawScaledCustomSizeModalRect((int) (x + 2), (int) (y + 2), 8, 8, 8, 8, headSize, headSize, 64, 64);

            if (((EntityPlayer) target).func_175148_a(EnumPlayerModelParts.HAT)) {
                Gui.drawScaledCustomSizeModalRect((int) (x + 2), (int) (y + 2), 40, 8, 8, 8, headSize, headSize, 64, 64);
            }

            GlStateManager.popMatrix();
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) mc.thePlayer).getLocationSkin());
            Gui.drawScaledCustomSizeModalRect((int) (x + 2), (int) (y + 2), 8, 8, 8, 8, headSize, headSize, 64, 64);

            if (((EntityPlayer) mc.thePlayer).func_175148_a(EnumPlayerModelParts.HAT)) {
                Gui.drawScaledCustomSizeModalRect((int) (x + 2), (int) (y + 2), 40, 8, 8, 8, headSize, headSize, 64, 64);
            }

            GlStateManager.popMatrix();
        }

        mc.fontRendererObj.drawString(name, (int) (x + 2 + headSize + 2), (int) (y + 4), -1, true);

        int healthBarStartX = (int) (x + 2 + headSize + 2);
        int healthBarStartY = (int) (y + 4 + mc.fontRendererObj.FONT_HEIGHT + 2);
        int healthBarEndX = (int) (x + 2 + headSize + maxL - 2);
        int healthBarEndY = healthBarStartY + mc.fontRendererObj.FONT_HEIGHT + 2;

        double health = target.getHealth();
        double maxHealth = target.getMaxHealth();
        double healthPercentage = health / maxHealth;

        String healthPercentageText = String.format("%.1f%%", healthPercentage * 100);

        Gui.drawRect(healthBarStartX, healthBarStartY, healthBarEndX, healthBarEndY, backgroundColor.darker().getRGB());

        int filledHealthBarEndX = (int) (healthBarStartX + (healthBarEndX - healthBarStartX) * healthPercentage);

        Gui.drawRect(healthBarStartX, healthBarStartY, filledHealthBarEndX, healthBarEndY, ColorProcess.getColor().getRGB());

        int textWidth = mc.fontRendererObj.getStringWidth(healthPercentageText);
        int textHeight = mc.fontRendererObj.FONT_HEIGHT;

        double rectCenterX = (healthBarStartX + healthBarEndX) / 2.0;
        double rectCenterY = (healthBarStartY + healthBarEndY) / 2.0;

        double textX = rectCenterX - textWidth / 2.0;
        double textY = rectCenterY - textHeight / 2.0;

        mc.fontRendererObj.drawString(healthPercentageText, (int) textX, (int) textY + 1, 0xFFFFFFFF, true);

        draggableComponent.setWidth((int) (2 + headSize + maxL));
        draggableComponent.setHeight((int) (2 + headSize + 2));
    }

    private void drawAdjustTargetInterface() {
        target = auraTarget();

        ScaledResolution sr = new ScaledResolution(mc);
        initializePosition(sr);

        if (previewing()) {
            target = mc.thePlayer;
        } else {
            if (target == null) return;
        }

        DraggingProcess.DraggableComponent draggableComponent = DraggingProcess.components.get("TargetInterface");
        draggableComponent.setHeight(40);
        draggableComponent.setWidth(150);

        float x = (float) draggableComponent.getX();
        float y = (float) draggableComponent.getY();

        String name = disguiseName(target);

        int maxL = 100;
        int totalWidth = 3 + 24 + 3 + maxL + 3;
        int totalHeight = 3 + 24 + 8 + 3;

        Gui.drawRect((int) x, (int) y, (int) (x + totalWidth), (int) (y + totalHeight), new Color(43, 43, 43, 200).getRGB());

        if (target instanceof AbstractClientPlayer) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) target).getLocationSkin());
            Gui.drawScaledCustomSizeModalRect((int) (x + 3), (int) (y + 3), 8, 8, 8, 8, 24, 24, 64, 64);

            if (((EntityPlayer) target).func_175148_a(EnumPlayerModelParts.HAT)) {
                Gui.drawScaledCustomSizeModalRect((int) (x + 3), (int) (y + 3), 40, 8, 8, 8, 24, 24, 64, 64);
            }

            GlStateManager.popMatrix();
        } else {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);

            mc.getTextureManager().bindTexture(((AbstractClientPlayer) mc.thePlayer).getLocationSkin());
            Gui.drawScaledCustomSizeModalRect((int) (x + 3), (int) (y + 3), 8, 8, 8, 8, 24, 24, 64, 64);

            if (((EntityPlayer) mc.thePlayer).func_175148_a(EnumPlayerModelParts.HAT)) {
                Gui.drawScaledCustomSizeModalRect((int) (x + 3), (int) (y + 3), 40, 8, 8, 8, 24, 24, 64, 64);
            }

            GlStateManager.popMatrix();
        }

        Color gray = Color.WHITE.darker();
        FontProcess.getFont("bold").drawString(name, (float) (x + 3 + 24 + 3), (float) (y + 2), gray.getRGB());

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i <= 3; i++) {
            if (target.getCurrentArmor(i) != null) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(target.getCurrentArmor(i),
                        (int) (x + 3 + 24 + 3 + (i * 16)), (int) (y + 3 + mc.fontRendererObj.FONT_HEIGHT + 2));
            }
        }
        RenderHelper.disableStandardItemLighting();

        int healthBarStartX = (int) (x + 3);
        int healthBarStartY = (int) (y + 3 + 24 + 3);
        int healthBarEndX = (int) (x + 3 + 24 + 3 + maxL);
        int healthBarEndY = (int) (y + 3 + 24 + 8);

        Gui.drawRect(healthBarStartX, healthBarStartY, healthBarEndX, healthBarEndY, new Color(0, 0, 0, 80).getRGB());

        double health = target.getHealth();
        double maxHealth = target.getMaxHealth();
        double healthPercentage = health / maxHealth;

        int filledHealthBarEndX = (int) (healthBarStartX + (healthBarEndX - healthBarStartX) * healthPercentage);

        Gui.drawRect(healthBarStartX, healthBarStartY, filledHealthBarEndX, healthBarEndY, ColorProcess.getColor().getRGB());

        RenderUtils.drawOutline(healthBarStartX, healthBarStartY, filledHealthBarEndX - healthBarStartX,
                healthBarEndY - healthBarStartY, 0.5f, Color.DARK_GRAY.getRGB());

        String healthDiffStr = String.format("%.1f", Math.abs(mc.thePlayer.getHealth() - target.getHealth()));
        String healthDiff = mc.thePlayer.getHealth() < target.getHealth() ? "-" + healthDiffStr : "+" + healthDiffStr;

        int healthDiffWidth = FontProcess.getFont("tempest").getStringWidth(healthDiff);
        int healthDiffHeight = FontProcess.getFont("tempest").getHeight();

        FontProcess.getFont("tempest").drawString(healthDiff,
                (float) (x + 3 + 24 + 3 + maxL - healthDiffWidth),
                (float) (y + 3 + 24 - 9 - healthDiffHeight),
                gray.getRGB());

        draggableComponent.setWidth(totalWidth);
        draggableComponent.setHeight(totalHeight);
    }
}
