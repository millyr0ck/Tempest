package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.utils.render.shaders.RoundedEffects;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.ClientSettingsModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.Themes;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.ArrayPattern;
import rip.tempest.utils.render.PatternWorker;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL12;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.shaders.StencilUtils;
import net.minecraft.client.renderer.texture.DynamicTexture;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.Translate;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import java.awt.*;
import java.util.*;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Array List", category = ModuleCategory.VISUALS)
public final class ArrayListModule extends Module {
    private final Property<Boolean> bg = new Property<>("Background", true);
    private final ModeProperty<BGColorMode> bgColor = new ModeProperty<>("Background Color", BGColorMode.Normal, bg::getValue);
    private final NumberProperty bgAlpha = new NumberProperty("Background Opacity", 100, bg::getValue, 1, 255, 1);
    private final ModeProperty<LineMode> line = new ModeProperty<>("Line", LineMode.Outline);
    private final NumberProperty lineWidth = new NumberProperty("Line Width", 0.5, () -> line.getValue().wraps(), 0.5, 2, 0.5);
    private final Property<Boolean> hideVisuals = new Property<>("Hide Visuals", false);
    private final Property<Boolean> hideMost = new Property<>("Hide Most", false);

    private static final java.util.Set<String> KEPT_BY_HIDE_MOST = java.util.Set.of("aura", "scaffold", "speed", "velocity");

    private boolean hiddenByHideMost(Module module) {
        return hideMost.getValue() && !KEPT_BY_HIDE_MOST.contains(module.getLabel().toLowerCase());
    }
    private static final Property<Boolean> useMcFont = new Property<>("Use MC Font", false);
    private final Property<Boolean> noSpaces = new Property<>("No Spaces", false);
    private final Property<Boolean> showSuffix = new Property<>("Show Suffix", true);
    private final ModeProperty<SuffixMode> suffixMode = new ModeProperty<>("Suffix Mode", SuffixMode.Space, showSuffix::getValue);

    private final ModeProperty<SuffixColor> suffixColor = new ModeProperty<>("Suffix Color", SuffixColor.Normal, showSuffix::getValue);

    public enum SuffixColor {
        Normal, White, Client
    }
    private final Property<Boolean> lowercase = new Property<>("Lowercase", true);
    private final ModeProperty<ColorMode> colorMode = new ModeProperty<>("Color Mode", ColorMode.Fade);

    public final ModeProperty<ArrayPattern> pattern = new ModeProperty<>("Pattern", ArrayPattern.Off);

    private final Property<Boolean> patternMotion = new Property<>("Pattern Motion", true, () -> pattern.getValue() != ArrayPattern.Off);
    public final NumberProperty patternSpeed = new NumberProperty("Pattern Speed", 1, () -> pattern.getValue().animated(patternMotion.getValue()), 0.1, 3, 0.1);

    private final Property<Boolean> patternBackground = new Property<>("Pattern Background", false, () -> pattern.getValue() != ArrayPattern.Off && bg.getValue());
    private final NumberProperty offsetX = new NumberProperty("Offset X", 0, -50, 50, 1);
    private final NumberProperty offsetY = new NumberProperty("Offset Y", 0, -50, 50, 1);

    private final NumberProperty spacingProp = new NumberProperty("Spacing", 0, -4, 10, 0.5);

    private final NumberProperty scale = new NumberProperty("Scale", 1, 0.5, 2, 0.05);

    private float widestRow = 1;

    private final Property<Boolean> blur = new Property<>("Blur", true);
    private final NumberProperty blurRadius = new NumberProperty("Blur Radius", 7, blur::getValue, 1, 25, 0.5);
    private final NumberProperty blurStrength = new NumberProperty("Blur Strength", 0.35, blur::getValue, 0, 1, 0.05);
    private final Property<Boolean> shadow = new Property<>("Shadow", true);
    private final NumberProperty shadowOpacity = new NumberProperty("Shadow Opacity", 120, shadow::getValue, 0, 255, 1);
    private final NumberProperty shadowRadius = new NumberProperty("Shadow Radius", 10, shadow::getValue, 1, 30, 0.5);
    private final Property<Boolean> bloom = new Property<>("Bloom", false);

    private final ModeProperty<BloomMode> bloomMode = new ModeProperty<>("Bloom Mode", BloomMode.Tenacity, bloom::getValue);
    private final NumberProperty bloomRadius = new NumberProperty("Bloom Radius", 12, this::softBloom, 1, 40, 0.5);
    private final NumberProperty bloomStrength = new NumberProperty("Bloom Strength", 130, this::softBloom, 0, 255, 1);
    private final NumberProperty bloomSpread = new NumberProperty("Bloom Spread", 2, this::softBloom, 0, 10, 0.5);

    private final NumberProperty bloomIterations = new NumberProperty("Bloom Iterations", 3, this::tenacityBloom, 1, 8, 1);
    private final NumberProperty bloomOffset = new NumberProperty("Bloom Offset", 1, this::tenacityBloom, 1, 10, 1);

    private net.minecraft.client.shader.Framebuffer bloomMask = new net.minecraft.client.shader.Framebuffer(1, 1, false);

    public enum BloomMode {
        Tenacity,
        Soft
    }

    private boolean softBloom() {
        return bloom.getValue() && bloomMode.getValue() == BloomMode.Soft;
    }

    private boolean tenacityBloom() {
        return bloom.getValue() && bloomMode.getValue() == BloomMode.Tenacity;
    }

    private final Property<Boolean> glassPreset = new Property<>("Glow Preset", false);

    private final ModeProperty<Style> style = new ModeProperty<>("Style", Style.Normal);

    public enum Style {
        Normal,
        Step {
            @Override
            public String toString() {
                return "Sigma";
            }
        }
    }

    private static final class ClassicRow {
        float grow;
    }

    private final Map<Module, ClassicRow> classicRows = new HashMap<>();
    private long classicLastFrame;

    private static final class WrapRow {
        final float far, top, bottom;
        final int color;

        WrapRow(float far, float top, float bottom, int color) {
            this.far = far;
            this.top = top;
            this.bottom = bottom;
            this.color = color;
        }
    }

    private final List<WrapRow> wrapRows = new ArrayList<>();

    private final List<RowRect> rowRects = new ArrayList<>();
    private int rowRectCount;

    private static ArrayListModule instance;

    public ArrayListModule() {
        instance = this;
        glassPreset.addValueChangeListener((oldValue, value) -> {
            if (!value) return;
            applyGlassPreset();
            glassPreset.setValue(false);
        });
    }

    private void applyGlassPreset() {
        blur.setValue(true);
        blurRadius.setValue(12.0);
        blurStrength.setValue(0.3);

        shadow.setValue(false);

        bloom.setValue(true);
        bloomMode.setValue(BloomMode.Soft);
        bloomRadius.setValue(12.0);
        bloomStrength.setValue(41.0);
        bloomSpread.setValue(1.5);
    }

    private static final Map<Module, String> displayLabelCache = new HashMap<>();
    private static List<Module> moduleCache;
    private int visiblePickerModuleCount = 1;

    private static final String DRAG_KEY = "Arraylist";
    private boolean rightSide = true;
    private boolean bottomSide = false;
    private boolean positionsSeeded = false;

    private float lastAlignX = Float.NaN, lastAnchorY = Float.NaN;

    private float alignX;

    private float anchorY;

    private void updateDragBounds(ScaledResolution sr, float listW, float listH) {
        DraggingProcess.DraggableComponent c = DraggingProcess.components.get(DRAG_KEY);
        if (c == null) {
            c = new DraggingProcess.DraggableComponent(sr.getScaledWidth() - listW - 2, 2);
            DraggingProcess.components.put(DRAG_KEY, c);
        }

        double oldW = c.getWidth(), oldH = c.getHeight();
        rightSide = c.getX() + oldW / 2.0 > sr.getScaledWidth() / 2.0;
        bottomSide = c.getY() + oldH / 2.0 > sr.getScaledHeight() / 2.0;

        if (rightSide && oldW > 0) c.setX(c.getX() + (oldW - listW));
        if (bottomSide && oldH > 0) c.setY(c.getY() + (oldH - listH));
        c.setWidth(listW);
        c.setHeight(listH);

        float ox = offsetX.getValue().floatValue();
        float oy = offsetY.getValue().floatValue();

        c.setSlack(rightSide ? 0 : ox, bottomSide ? 0 : oy, rightSide ? ox : 0, bottomSide ? oy : 0);
        alignX = (float) (rightSide ? c.getX() + listW - ox : c.getX() + ox);
        anchorY = (float) (bottomSide ? c.getY() + listH - oy : c.getY() + oy);

        drawnTopAnchored = !bottomSide;
        drawnBounds[0] = rightSide ? alignX - listW : alignX;
        drawnBounds[2] = rightSide ? alignX : alignX + listW;
        drawnBounds[1] = bottomSide ? anchorY - listH : anchorY;
        drawnBounds[3] = bottomSide ? anchorY : anchorY + listH;
    }

    private static final float[] drawnBounds = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private static boolean drawnTopAnchored;

    public static float[] listBounds() {
        ArrayListModule module = rip.tempest.Tempest.INSTANCE.getModuleManager().getModule(ArrayListModule.class);
        if (module == null || !module.isEnabled() || !drawnTopAnchored || Float.isNaN(drawnBounds[0])) return null;
        return drawnBounds.clone();
    }

    private double farEdge(double translateX, float moduleWidth, float inset) {
        return rightSide ? translateX - inset : translateX + moduleWidth + inset;
    }

    private float stepEdge(float w) {
        return rightSide ? alignX - w - 3 : alignX + w + 3;
    }

    public enum BGColorMode {
        Normal, Theme, White
    }

    public enum ColorMode {
        Static, Fade
    }

    private float patternX0, patternY0, patternW = 1, patternH = 1;

    private float patternCx = 0.5f, patternSpan = 1;

    private double patternTime;
    private long patternClockAt;

    private DynamicTexture patternTexture;
    private int patternTexW, patternTexH;
    private final PatternWorker patternWorker = new PatternWorker();
    private ArrayPattern requestedPattern;
    private boolean requestedMotion;
    private float requestedW, requestedH, requestedCx, requestedSpan;
    private long patternRequestedAt;

    private boolean patternReady, patternDrawing;

    private boolean patternOn() {
        return pattern.getValue() != ArrayPattern.Off;
    }

    public boolean motion() {
        return patternMotion.getValue();
    }

    public double patternTime() {
        return patternTime;
    }

    private void tickPatternClock() {
        long now = System.currentTimeMillis();
        if (patternClockAt != 0) patternTime += (now - patternClockAt) / 1000.0 * Math.max(0.1, patternSpeed.getValue());
        patternClockAt = now;
    }

    private int patternColorAt(double x, double y) {
        return pattern.getValue().color(x - patternX0, y - patternY0, patternW, patternH, patternCx, patternSpan, patternTime, patternMotion.getValue());
    }

    private static void stencilLetters(CustomFontRenderer fr, String text, float x, float y) {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.enableAlpha();

        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.35f);
        for (int i = 0; i < text.length(); i++) {
            String letter = String.valueOf(text.charAt(i));
            fr.drawString(letter, x, y, 0xFFFFFFFF);
            x += fr.getStringWidth(letter);
        }
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.colorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private void beginPatternStencil() {
        StencilUtils.checkSetupFBO(mc.getFramebuffer());
        GL11.glStencilMask(0xFF);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
    }

    private void preparePatternTexture(float size) {
        int tw = Math.max(1, Math.min(1024, (int) Math.ceil(patternW * size)));
        int th = Math.max(1, Math.min(1024, (int) Math.ceil(patternH * size)));
        if (patternTexture == null || tw != patternTexW || th != patternTexH) {
            if (patternTexture != null) patternTexture.deleteGlTexture();
            patternTexture = new DynamicTexture(tw, th);
            patternTexW = tw;
            patternTexH = th;
            requestedPattern = null;
            patternReady = false;
        }

        ArrayPattern current = pattern.getValue();
        boolean motionOn = patternMotion.getValue();
        long now = System.currentTimeMillis();
        boolean stale = current != requestedPattern || motionOn != requestedMotion || patternW != requestedW || patternH != requestedH
                || patternCx != requestedCx || patternSpan != requestedSpan;
        if (stale || current.animated(motionOn) && now - patternRequestedAt >= 33) {
            int[] base = current.shape() ? ArrayPattern.baseTable(patternTime) : null;
            patternWorker.request(current, tw, th, patternW, patternH, patternCx, patternSpan, patternTime, motionOn, base);
            requestedPattern = current;
            requestedMotion = motionOn;
            requestedW = patternW;
            requestedH = patternH;
            requestedCx = patternCx;
            requestedSpan = patternSpan;
            patternRequestedAt = now;
        }

        PatternWorker.Frame frame = patternWorker.take();
        if (frame != null && frame.width == patternTexW && frame.height == patternTexH) {
            TextureUtil.uploadTexture(patternTexture.getGlTextureId(), frame.pixels, frame.width, frame.height);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            patternReady = true;
        }
    }

    private void drawPatternBackground(double x0, double y0, double x1, double y1, int alpha) {
        double left = Math.min(x0, x1), right = Math.max(x0, x1);
        double u0 = clamp01((left - patternX0) / patternW), u1 = clamp01((right - patternX0) / patternW);
        double v0 = clamp01((y0 - patternY0) / patternH), v1 = clamp01((y1 - patternY0) / patternH);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(0.45f, 0.45f, 0.45f, alpha / 255f);
        GlStateManager.bindTexture(patternTexture.getGlTextureId());
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(left, y1, 0).tex(u0, v1).endVertex();
        buffer.pos(right, y1, 0).tex(u1, v1).endVertex();
        buffer.pos(right, y0, 0).tex(u1, v0).endVertex();
        buffer.pos(left, y0, 0).tex(u0, v0).endVertex();
        tessellator.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void drawPatternOverlay() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.bindTexture(patternTexture.getGlTextureId());
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        double x0 = patternX0, y0 = patternY0, x1 = patternX0 + patternW, y1 = patternY0 + patternH;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x0, y1, 0).tex(0, 1).endVertex();
        buffer.pos(x1, y1, 0).tex(1, 1).endVertex();
        buffer.pos(x1, y0, 0).tex(1, 0).endVertex();
        buffer.pos(x0, y0, 0).tex(0, 0).endVertex();
        tessellator.draw();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private boolean themeAcross() {
        return Themes.active() && Themes.horizontal() && colorMode.getValue() != ColorMode.Static;
    }

    private float textY(CustomFontRenderer fr, double translateY) {
        return (float) translateY - (fr == FontProcess.getFont("mc") || useMcFont.getValue() ? 0 : 1);
    }

    private float farPadding() {
        return line.getValue() == LineMode.Flux ? 4 * fluxUnit() : 1f;
    }

    private float fluxUnit() {
        return rowHeight() / 20f;
    }

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(colorMode.getValue().toString());
    };

    private enum LineMode {
        Off("Off"),

        Outline("Outline"),
        Rise("Rise"),
        Top("Top"),
        Right("Right"),
        RightTop("Right Top"),
        Left("Left"),
        Bottom("Bottom"),

        Wrap("Wrap"),

        WrapOpen("Wrap Open"),

        Flux("Flux"),

        EdgeEmber("Edge Ember");

        boolean wraps() {
            return this == Wrap || this == WrapOpen;
        }

        public String name;

        LineMode(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    public enum SuffixMode {
        Space("Space"),
        Dash("Dash"),
        Brackets("Brackets"),
        Pipe("Pipe");

        public String name;

        SuffixMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public void onEnable() {
        moduleCache = null;
        positionsSeeded = false;
        super.onEnable();
    }

    private static final Map<ModuleCategory, Color> CATEGORY_COLORS = new HashMap<>();
    static {
        CATEGORY_COLORS.put(ModuleCategory.COMBAT,   new Color(219, 120,  163));
        CATEGORY_COLORS.put(ModuleCategory.MOVEMENT, new Color(91,  153, 204));
        CATEGORY_COLORS.put(ModuleCategory.PLAYER,   new Color(224,  197, 242));
        CATEGORY_COLORS.put(ModuleCategory.CLIENT,   new Color(196, 224, 249));
        CATEGORY_COLORS.put(ModuleCategory.VISUALS,  new Color(255, 187,  145));
        CATEGORY_COLORS.put(ModuleCategory.CONFIGS,  new Color(180, 180, 180));
    }

    private static final Map<ModuleCategory, Color> VIRTUE_CATEGORY_COLORS = new HashMap<>();
    static {
        VIRTUE_CATEGORY_COLORS.put(ModuleCategory.COMBAT,   new Color(194, 82, 76));
        VIRTUE_CATEGORY_COLORS.put(ModuleCategory.VISUALS,  new Color(228, 208, 141));
        VIRTUE_CATEGORY_COLORS.put(ModuleCategory.MOVEMENT, new Color(180, 207, 236));
        VIRTUE_CATEGORY_COLORS.put(ModuleCategory.PLAYER,   new Color(119, 221, 119));
        VIRTUE_CATEGORY_COLORS.put(ModuleCategory.CLIENT,   new Color(119, 221, 119));
    }

    @EventLink
    public Listener<PreUpdateEvent> preUpdateEventListener = e -> {
        if (moduleCache != null) {
            for (Module module : moduleCache)
                displayLabelCache.put(module, getDisplayLabel(module));

            moduleCache.sort(new LengthComparator());
        }
    };

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> renderArrayList();

    private void renderArrayList() {
        if (style.getValue() == Style.Step) {
            renderStep();
            return;
        }
        CustomFontRenderer fr = FontProcess.getCurrentFont();
        ScaledResolution sr = new ScaledResolution(mc);

        if (useMcFont.getValue()) fr = FontProcess.getFont("mc");

        float spacing = 0;
        float size = scale.getValue().floatValue();

        if (moduleCache == null)
            moduleCache = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModules());

        List<Module> filteredModules = new ArrayList<>();
        for (Module module : moduleCache) {
            if (hiddenByHideMost(module)) continue;
            if (hideVisuals.getValue() && module.getCategory() == ModuleCategory.VISUALS) {
                continue;
            }
            filteredModules.add(module);
        }

        float listW = 0, listH = 0;
        List<Float> rowWidths = new ArrayList<>();
        for (Module module : filteredModules) {
            if (!module.isVisible()) continue;
            String n = displayLabelCache.get(module);
            if (n == null) continue;
            float w = fr.getStringWidth(n) + 3;
            rowWidths.add(w);
            listW = Math.max(listW, w);
            listH += rowHeight() + spacing;
        }

        if (listH <= 0) {
            listW = 60;
            listH = rowHeight();
        }

        updateDragBounds(sr, listW * size, listH * size);
        widestRow = Math.max(1f, listW - 3);
        patternX0 = rightSide ? alignX - listW : alignX;
        patternY0 = bottomSide ? anchorY - listH : anchorY;
        patternW = Math.max(1, listW);
        patternH = Math.max(1, listH);

        Collections.sort(rowWidths);
        patternSpan = rowWidths.isEmpty() ? patternW : Math.min(patternW, rowWidths.get(rowWidths.size() / 2));
        patternCx = rightSide ? patternW - patternSpan / 2 : patternSpan / 2;
        tickPatternClock();

        GlStateManager.pushMatrix();
        try {
            if (size != 1f) {
                GlStateManager.translate(alignX, anchorY, 0);
                GlStateManager.scale(size, size, 1);
                GlStateManager.translate(-alignX, -anchorY, 0);
            }

            final float dir = bottomSide ? -1f : 1f;
            final float startY = bottomSide ? anchorY - rowHeight() + 2 : anchorY + 2;

            final float capY = bottomSide ? startY + rowHeight() - 2 : startY - 2;

            final float parkY = anchorY + ((bottomSide ? sr.getScaledHeight() + 20 : -20) - anchorY) / size;

            if (!positionsSeeded) {
                updateModulePositions(fr, filteredModules, startY, dir, spacing, parkY);
                positionsSeeded = true;
            } else if (!Float.isNaN(lastAlignX) && (alignX != lastAlignX || anchorY != lastAnchorY)) {
                float dx = alignX - lastAlignX, dy = anchorY - lastAnchorY;
                for (Module module : filteredModules) {
                    module.getTranslate().setX((float) (module.getTranslate().getX() + dx));
                    module.getTranslate().setY((float) (module.getTranslate().getY() + dy));
                }
            }
            lastAlignX = alignX;
            lastAnchorY = anchorY;

            visiblePickerModuleCount = 0;
            for (Module module : filteredModules) {
                if (module.isVisible()) {
                    visiblePickerModuleCount++;
                }
            }
            if (visiblePickerModuleCount < 1) {
                visiblePickerModuleCount = 1;
            }

            layoutRows(fr, filteredModules, startY, dir, spacing, parkY);
            renderRowEffects(fr, filteredModules);

            float previousModuleWidth = -1;
            wrapRows.clear();
            patternDrawing = false;
            if (patternOn()) {
                preparePatternTexture(size);

                patternDrawing = patternReady;
                if (patternDrawing) beginPatternStencil();
            }

            final int moduleCacheSize = filteredModules.size();
            int lastVisibleModuleIndex = moduleCacheSize - 1;

            for (; lastVisibleModuleIndex > 0; lastVisibleModuleIndex--) {
                if (filteredModules.get(lastVisibleModuleIndex).isVisible())
                    break;
            }

            int firstVisibleModuleIndex = -1;

            int visibleModuleCount = 0;

            for (int i = 0; i < moduleCacheSize; i++) {
                final Module module = filteredModules.get(i);
                final Translate translate = module.getTranslate();
                final String name = displayLabelCache.get(module);
                if (name == null) continue;
                final float moduleWidth = fr.getStringWidth(name);
                final boolean visible = module.isVisible();

                if (visible && firstVisibleModuleIndex == -1)
                    firstVisibleModuleIndex = i;

                double translateX = translate.getX();
                double translateY = translate.getY();

                if (visible || leaving(module, translateX, moduleWidth)) {
                    int visibleModuleIndex = visibleModuleCount;
                    int aColor = getColorForModule(visibleModuleIndex, translateY, translateX, alignX, module);
                    final String shownName = name;
                    final float textX = (float) translateX;

                    double top = translateY - 2;

                    final double farX = farEdge(translateX, moduleWidth, 2);
                    final double bgFar = farEdge(translateX, moduleWidth, farPadding());
                    final float edgeA = rightSide ? alignX - 1 : alignX + 1;

                    if (bg.getValue() && line.getValue() == LineMode.Flux) {
                        drawFluxBackground(bgFar, translateY, moduleWidth, aColor);
                    } else if (bg.getValue()) {
                        if (patternDrawing && patternBackground.getValue()) {
                            drawPatternBackground(bgFar, translateY - 2, alignX, translateY + rowHeight() - 2, getColorForBG().getAlpha());
                        } else {
                            Gui.drawRect(bgFar,
                                    translateY - 2,
                                    alignX,
                                    translateY + rowHeight() - 2,
                                    getColorForBG().getRGB());
                        }
                    }
                    if (line.getValue() == LineMode.EdgeEmber) {
                        drawEmberBleed(bgFar, translateY - 2, translateY + rowHeight() - 2, aColor);
                    }

                    if (patternOn() || themeAcross() || !Themes.active() && isSpatial(ColorProcess.mode())) {
                        int colorCodeIndex = shownName.indexOf("§7");

                        boolean suffixAsName = suffixColor.getValue() == SuffixColor.Client;
                        String modulePart = colorCodeIndex == -1 ? shownName
                                : suffixAsName ? shownName.substring(0, colorCodeIndex) + shownName.substring(colorCodeIndex + 2)
                                : shownName.substring(0, colorCodeIndex);
                        String suffixPart = colorCodeIndex != -1 && !suffixAsName ? shownName.substring(colorCodeIndex + 2) : "";

                        float charX = textX;
                        float yOff = textY(fr, translateY);

                        if (fr.canDrawLetters()) {
                            final float rowY = yOff;
                            charX = fr.drawLettersWithShadow(modulePart, textX, yOff, (letterX, charWidth) ->
                                    patternOn() ? patternColorAt(letterX + charWidth / 2f, rowY + 4)
                                            : themeAcross() ? Themes.atX(letterX + charWidth / 2f)
                                            : spatialColor(letterX, rowY));
                        } else {
                            for (char c : modulePart.toCharArray()) {
                                float charWidth = fr.getStringWidth(String.valueOf(c));
                                int charColor = patternOn() ? patternColorAt(charX + charWidth / 2f, yOff + 4)
                                        : themeAcross() ? Themes.atX(charX + charWidth / 2f)
                                        : spatialColor(charX, yOff);
                                fr.drawStringWithShadow(String.valueOf(c), charX, yOff, charColor);
                                charX += charWidth;
                            }
                        }
                        if (patternDrawing) stencilLetters(fr, modulePart, textX, yOff);

                        if (!suffixPart.isEmpty()) {
                            fr.drawStringWithShadow(suffixPart, charX, yOff, suffixColor.getValue() == SuffixColor.White ? 0xFFFFFFFF : new Color(180, 180, 180).getRGB());
                        }
                    } else {
                        fr.drawStringWithShadow(
                                colouredSuffix(shownName),
                                textX,
                                textY(fr, translateY),
                                line.getValue() == LineMode.EdgeEmber ? 0xFFF2F3F5 : aColor);
                    }

                    if (line.getValue() == LineMode.Outline) {
                        Gui.drawRect(farX,
                                translateY - 2,
                                farEdge(translateX, moduleWidth, 1),
                                translateY + rowHeight() - 2,
                                aColor);

                        double outlineTop = top - 1;
                        double outlineBottom = translateY + rowHeight() - 2;

                        if (i != firstVisibleModuleIndex && moduleWidth - previousModuleWidth > 0) {
                            Gui.drawRect(farX,
                                    outlineTop,
                                    stepEdge(previousModuleWidth),
                                    outlineTop + 1,
                                    aColor);
                        }

                        if (i != lastVisibleModuleIndex) {
                            Module nextModule = null;
                            int indexOffset = 1;

                            while (i + indexOffset <= lastVisibleModuleIndex) {
                                nextModule = filteredModules.get(i + indexOffset);
                                if (nextModule.isVisible())
                                    break;
                                nextModule = null;
                                indexOffset++;
                            }

                            if (nextModule != null) {
                                String nextModuleName = displayLabelCache.get(nextModule);
                                float nextModuleWidth = fr.getStringWidth(nextModuleName);

                                if (moduleWidth - nextModuleWidth > 0.5)
                                    Gui.drawRect(farX,
                                            outlineBottom,
                                            stepEdge(nextModuleWidth),
                                            outlineBottom + 1,
                                            aColor);
                            }
                        } else {
                            Gui.drawRect(farX,
                                    outlineBottom,
                                    alignX,
                                    outlineBottom + 1,
                                    aColor);
                        }
                        if (i == firstVisibleModuleIndex) {
                            Gui.drawRect(edgeA,
                                    capY,
                                    alignX,
                                    translateY + rowHeight() - 2 + spacing,
                                    aColor);
                        } else {
                            Module prevModule = null;
                            for (int j = i - 1; j >= 0; j--) {
                                if (filteredModules.get(j).isVisible()) {
                                    prevModule = filteredModules.get(j);
                                    break;
                                }
                            }
                            if (prevModule != null) {
                                double prevY = prevModule.getTranslate().getY();
                                Gui.drawRect(edgeA,
                                        prevY + rowHeight() - 2,
                                        alignX,
                                        translateY + rowHeight() - 2 + spacing,
                                        aColor);
                            }
                        }
                        if (i == firstVisibleModuleIndex) {
                            Gui.drawRect(farX,
                                    capY,
                                    alignX,
                                    capY + 1,
                                    aColor);
                        }
                    }

                    if (line.getValue() != LineMode.Off && line.getValue() != LineMode.Outline) {
                        if (line.getValue() == LineMode.Rise) {
                            Gui.drawRect(edgeA,
                                    translateY - 2,
                                    alignX,
                                    translateY + rowHeight() - 4,
                                    aColor);
                        } else if (line.getValue() == LineMode.Right || line.getValue() == LineMode.RightTop) {
                            if (i == firstVisibleModuleIndex) {
                                Gui.drawRect(edgeA,
                                        capY,
                                        alignX,
                                        translateY + rowHeight() - 2 + spacing,
                                        aColor);
                            } else {
                                Module prevModule = null;
                                for (int j = i - 1; j >= 0; j--) {
                                    if (filteredModules.get(j).isVisible()) {
                                        prevModule = filteredModules.get(j);
                                        break;
                                    }
                                }
                                if (prevModule != null) {
                                    double prevY = prevModule.getTranslate().getY();
                                    Gui.drawRect(edgeA,
                                            prevY + rowHeight() - 2,
                                            alignX,
                                            translateY + rowHeight() - 2 + spacing,
                                            aColor);
                                }
                            }
                            if (line.getValue() == LineMode.RightTop && i == firstVisibleModuleIndex) {
                                Gui.drawRect(farX,
                                        capY,
                                        alignX,
                                        capY + 1,
                                        aColor);
                            }
                        } else if (line.getValue() == LineMode.Left) {
                            Gui.drawRect(farX,
                                    translateY - 2,
                                    farEdge(translateX, moduleWidth, 1),
                                    translateY + rowHeight() - 2,
                                    aColor);
                        } else if (line.getValue() == LineMode.Bottom) {
                            if (i == lastVisibleModuleIndex) {
                                Gui.drawRect(bgFar,
                                        translateY + rowHeight() - 2,
                                        alignX,
                                        translateY + rowHeight() - 1,
                                        aColor);
                            }
                        } else if (line.getValue() == LineMode.Top) {
                            if (i == firstVisibleModuleIndex) {
                                Gui.drawRect(farX,
                                        capY,
                                        alignX,
                                        capY + 1,
                                        aColor);
                            }
                        } else if (line.getValue() == LineMode.EdgeEmber) {
                            drawEmberEdge(translateY - 2, translateY + rowHeight() - 2, aColor);
                        } else if (line.getValue() == LineMode.Flux) {
                            int above = visibleModuleIndex > 0
                                    ? getColorForModule(visibleModuleIndex - 1, translateY, translateX, alignX, module) : aColor;
                            drawFluxBar(translateY, above, aColor);
                        }
                    }
                    if (line.getValue().wraps() && visible) {
                        wrapRows.add(new WrapRow((float) bgFar, (float) top, (float) (translateY + rowHeight() - 2), aColor));
                    }

                    previousModuleWidth = moduleWidth;
                    visibleModuleCount++;
                }
            }

            if (line.getValue().wraps()) drawWrapOutline(wrapRows, line.getValue() == LineMode.Wrap);
            if (patternDrawing) drawPatternOverlay();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawWrapOutline(List<WrapRow> rows, boolean edge) {
        if (rows.isEmpty()) return;
        rows.sort(Comparator.comparingDouble(r -> r.top));

        float t = lineWidth.getValue().floatValue();

        float out = rightSide ? -1f : 1f;
        int n = rows.size();
        WrapRow first = rows.get(0), last = rows.get(n - 1);

        wrapBar(first.far + out * t, alignX, first.top - t, first.top, first.color);
        wrapBar(last.far + out * t, alignX, last.bottom, last.bottom + t, last.color);

        float above = first.top;
        for (int i = 0; i < n; i++) {
            WrapRow row = rows.get(i);
            int color = row.color;
            float bottom = row.bottom;

            wrapBar(row.far, row.far + out * t, above, bottom, color);

            if (edge) wrapBar(alignX, alignX + out * t, i == 0 ? above - t : above, i == n - 1 ? bottom + t : bottom, color);

            if (i < n - 1) {
                WrapRow next = rows.get(i + 1);
                float step = (row.far - next.far) * -out;
                if (step > 0.25f) {
                    wrapBar(row.far + out * t, next.far + out * t, bottom, bottom + t, color);
                } else if (step < -0.25f) {
                    wrapBar(next.far + out * t, row.far + out * t, bottom - t, bottom, next.color);
                }
                above = bottom;
            }
        }
    }

    private static void wrapBar(float x1, float x2, float y1, float y2, int color) {
        Glyphs.bar(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1), new Color(color, true));
    }

    private void layoutRows(CustomFontRenderer fr, List<Module> filteredModules,
                            float startY, float dir, float spacing, float parkY) {
        float y = startY;
        for (Module module : filteredModules) {
            String name = displayLabelCache.get(module);
            if (name == null) continue;
            float moduleWidth = fr.getStringWidth(name);
            if (module.isVisible()) {
                module.getTranslate().animate(restX(moduleWidth), y);
                y += dir * (rowHeight() + spacing);
            } else {
                module.getTranslate().animate(parkX(moduleWidth), y);
            }
        }
    }

    private boolean leaving(Module module, double translateX, float moduleWidth) {
        return isSliding(translateX, moduleWidth);
    }

    private void drawFluxBackground(double far, double translateY, float moduleWidth, int rowColor) {
        Color base = getColorForBG();
        Color row = new Color(rowColor);
        Color tint = new Color((int) (row.getRed() * 0.4f), (int) (row.getGreen() * 0.4f), (int) (row.getBlue() * 0.4f), base.getAlpha());
        float ratio = Math.min(1f, moduleWidth / widestRow);
        Color farColor = RenderUtils.interpolateColorC(tint, base, ratio);
        float bar = 3 * fluxUnit();
        float edge = rightSide ? alignX - bar : alignX + bar;
        float x0 = (float) Math.min(far, edge), x1 = (float) Math.max(far, edge);
        float y0 = (float) translateY - 2, h = rowHeight();
        if (rightSide) Glyphs.gradient(x0, y0, x1 - x0, h, farColor, tint);
        else Glyphs.gradient(x0, y0, x1 - x0, h, tint, farColor);
    }

    private void drawFluxBar(double translateY, int topColor, int bottomColor) {
        float bar = 3 * fluxUnit();
        float x0 = rightSide ? alignX - bar : alignX, x1 = rightSide ? alignX : alignX + bar;
        float y0 = (float) translateY - 2, y1 = y0 + rowHeight();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x0, y1, 0).color(bottomColor >> 16 & 255, bottomColor >> 8 & 255, bottomColor & 255, bottomColor >>> 24).endVertex();
        wr.pos(x1, y1, 0).color(bottomColor >> 16 & 255, bottomColor >> 8 & 255, bottomColor & 255, bottomColor >>> 24).endVertex();
        wr.pos(x1, y0, 0).color(topColor >> 16 & 255, topColor >> 8 & 255, topColor & 255, topColor >>> 24).endVertex();
        wr.pos(x0, y0, 0).color(topColor >> 16 & 255, topColor >> 8 & 255, topColor & 255, topColor >>> 24).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
    }

    private double restX(float moduleWidth) {
        if (line.getValue() == LineMode.Flux) {
            float fluxInset = 8 * fluxUnit();
            return rightSide ? alignX - moduleWidth - fluxInset : alignX + fluxInset;
        }
        float inset = (line.getValue() != LineMode.Off ? 1 : 0) + 1;
        return rightSide ? alignX - moduleWidth - inset : alignX + inset;
    }

    private double parkX(float moduleWidth) {
        return rightSide ? alignX : alignX - moduleWidth;
    }

    private boolean isSliding(double translateX, float moduleWidth) {
        return rightSide ? translateX < alignX - 0.5 : translateX > alignX - moduleWidth + 0.5;
    }

    private float slideVisibility(double translateX, float moduleWidth) {
        double rest = restX(moduleWidth);
        double travel = Math.abs(parkX(moduleWidth) - rest);
        if (travel < 0.01) return 1f;
        return (float) Math.max(0, Math.min(1, 1 - Math.abs(translateX - rest) / travel));
    }

    private void renderRowEffects(CustomFontRenderer fr, List<Module> filteredModules) {
        boolean blurOn = blur.getValue();
        boolean shadowOn = shadow.getValue();
        boolean bloomOn = bloom.getValue();
        if (!blurOn && !shadowOn && !bloomOn) return;

        rowRectCount = 0;
        int visibleIndex = 0;
        for (Module module : filteredModules) {
            String name = displayLabelCache.get(module);
            if (name == null) continue;
            float moduleWidth = fr.getStringWidth(name);
            double translateX = module.getTranslate().getX();
            double translateY = module.getTranslate().getY();
            if (!module.isVisible() && !isSliding(translateX, moduleWidth)) continue;

            if (rowRectCount == rowRects.size())
                rowRects.add(new RowRect());
            RowRect rect = rowRects.get(rowRectCount++);

            double bgFar = farEdge(translateX, moduleWidth, farPadding());
            rect.x = (float) Math.min(bgFar, alignX);
            rect.width = (float) Math.abs(alignX - bgFar);
            rect.y = (float) translateY - 2;
            rect.height = rowHeight();
            rect.alpha = slideVisibility(translateX, moduleWidth);
            rect.color = getColorForModule(visibleIndex, translateY, translateX, alignX, module);
            visibleIndex++;
        }

        float radius = 0f;

        if (blurOn && RoundedEffects.prepareBlur(blurRadius.getValue().floatValue())) {
            float strength = blurStrength.getValue().floatValue();
            for (int i = 0; i < rowRectCount; i++) {
                RowRect rect = rowRects.get(i);
                RoundedEffects.drawBlur(rect.x, rect.y, rect.width, rect.height, radius, rect.alpha * strength);
            }
        }

        if (bloomOn && bloomMode.getValue() == BloomMode.Tenacity) {
            renderTenacityBloom(() -> {
                for (int i = 0; i < rowRectCount; i++) {
                    RowRect rect = rowRects.get(i);
                    drawBloomShape(rect.x, rect.y, rect.width, rect.height, radius, rect.color, rect.alpha);
                }
            });

            if (bg.getValue()) {
                for (int i = 0; i < rowRectCount; i++) {
                    RowRect rect = rowRects.get(i);
                    drawBloomShape(rect.x, rect.y, rect.width, rect.height, radius, rect.color, rect.alpha * TENACITY_ROW_FILL);
                }
            }
        }

        for (int i = 0; i < rowRectCount; i++) {
            RowRect rect = rowRects.get(i);

            if (bloomOn && bloomMode.getValue() == BloomMode.Soft) {
                int glowAlpha = (int) (bloomStrength.getValue() * rect.alpha);
                Color glow = new Color((rect.color >> 16) & 0xFF, (rect.color >> 8) & 0xFF, rect.color & 0xFF, glowAlpha);
                RoundedEffects.drawShadow(rect.x, rect.y, rect.width, rect.height, radius,
                        bloomRadius.getValue().floatValue(), bloomSpread.getValue().floatValue(), 0f, 0f, glow);
            }

            if (shadowOn) {
                int shadowAlpha = (int) (shadowOpacity.getValue() * rect.alpha);
                RoundedEffects.drawShadow(rect.x, rect.y, rect.width, rect.height, radius,
                        shadowRadius.getValue().floatValue(), 2f, 0f, 3f, new Color(0, 0, 0, shadowAlpha));
            }
        }

        RenderUtils.resetColor();
    }

    private static final float TENACITY_ROW_FILL = 0.35f;

    private static void drawBloomShape(float x, float y, float width, float height, float radius, int rgb, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0 || width <= 0f || height <= 0f) return;
        Color color = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
        if (radius > 0f) RenderUtils.drawRoundedRect(x, y, width, height, radius, color);
        else Glyphs.bar(x, y, width, height, color);
    }

    private void renderTenacityBloom(Runnable mask) {
        bloomMask = RenderUtils.createFrameBuffer(bloomMask);
        bloomMask.framebufferClear();
        bloomMask.bindFramebuffer(false);
        mask.run();
        mc.getFramebuffer().bindFramebuffer(false);

        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0f, 0f, -2000f);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        rip.tempest.utils.kit.render.shader.impl.KawaseBloom.renderBlur(bloomMask.framebufferTexture,
                bloomIterations.getValue().intValue(), bloomOffset.getValue().intValue());
        GlStateManager.popMatrix();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.alphaFunc(516, 0.1f);
        GlStateManager.bindTexture(0);
        RenderUtils.resetColor();
    }

    private static final class RowRect {
        float x, y, width, height, alpha;
        int color;
    }

    private boolean isSpatial(ClientSettingsModule.Color c) {
        return c == ClientSettingsModule.Color.Horizontal
                || c == ClientSettingsModule.Color.Vertical
                || c == ClientSettingsModule.Color.Diagonal
                || c == ClientSettingsModule.Color.Radial
                || c == ClientSettingsModule.Color.Spiral;
    }

    private int spatialColor(double x, double y) {
        ScaledResolution sr = new ScaledResolution(mc);
        double w = sr.getScaledWidth(), h = sr.getScaledHeight();
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        double dx = x - w / 2.0, dy = y - h / 2.0;
        double maxR = Math.sqrt(w * w + h * h) / 2.0;
        double phase;
        switch (ColorProcess.mode()) {
            case Vertical -> phase = y / h;
            case Diagonal -> phase = (x + y) / (w + h);
            case Radial -> phase = Math.sqrt(dx * dx + dy * dy) / maxR;
            case Spiral -> phase = (Math.atan2(dy, dx) + Math.PI) / (2 * Math.PI) + Math.sqrt(dx * dx + dy * dy) / maxR * 0.5;
            default -> phase = x / w;
        }
        float hue = (float) (((phase - time) % 1.0 + 1.0) % 1.0);
        return Color.HSBtoRGB(hue, 0.85f, 1.0f);
    }

    private int brightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final float EMBER_BLEED = 52f;

    private void drawEmberBleed(double far, double top, double bottom, int color) {
        float width = (float) Math.min(EMBER_BLEED, Math.abs(alignX - far));
        if (width <= 0) return;
        Color glow = new Color(color >> 16 & 255, color >> 8 & 255, color & 255, 56);
        Color clear = new Color(color >> 16 & 255, color >> 8 & 255, color & 255, 0);
        float h = (float) (bottom - top);
        if (rightSide) Glyphs.gradient(alignX - width, (float) top, width, h, clear, glow);
        else Glyphs.gradient(alignX, (float) top, width, h, glow, clear);
    }

    private void drawEmberEdge(double top, double bottom, int color) {
        float x0 = rightSide ? alignX - 2 : alignX, x1 = x0 + 2;
        int rgb = color & 0xFFFFFF;
        for (int k = 5; k >= 1; k--) {
            float spread = k * 1.4f;
            int alpha = Math.round(34 * (1f - k / 6f));
            Gui.drawRect(x0 - spread, top, x1 + spread, bottom, alpha << 24 | rgb);
        }
        Gui.drawRect(x0, top, x1, bottom, 0xFF000000 | rgb);
    }

    private float rowHeight() {
        return 12f + spacingProp.getValue().floatValue();
    }

    private static final float CLASSIC_ROW = 10f;

    private void renderStep() {
        ScaledResolution sr = new ScaledResolution(mc);
        boolean mcFont = useMcFont.getValue();
        CustomFontRenderer fr = mcFont ? FontProcess.getFont("mc")
                : FontProcess.getStyledFont("sfdisplaybold", 16, Font.PLAIN);
        float size = scale.getValue().floatValue();

        long now = System.currentTimeMillis();
        float dt = classicLastFrame == 0 ? 0 : Math.min(100, now - classicLastFrame);
        classicLastFrame = now;

        if (moduleCache == null)
            moduleCache = new ArrayList<>(Tempest.INSTANCE.getModuleManager().getModules());

        List<Module> rows = new ArrayList<>();
        for (Module module : moduleCache) {
            if (hiddenByHideMost(module)) continue;
            if (hideVisuals.getValue() && module.getCategory() == ModuleCategory.VISUALS) continue;
            ClassicRow row = classicRows.computeIfAbsent(module, m -> new ClassicRow());
            boolean on = module.isVisible();
            row.grow += ((on ? 1f : 0f) - row.grow) * (1f - (float) Math.exp(-dt / 70f));
            if (!on && row.grow < 0.01f) row.grow = 0f;
            if (on && row.grow > 0.995f) row.grow = 1f;
            if (row.grow > 0) rows.add(module);
        }

        Map<Module, String> labels = new HashMap<>();
        Map<Module, Float> widths = new HashMap<>();
        for (Module module : rows) {
            String label = displayLabelCache.get(module);
            if (label == null) label = getDisplayLabel(module);
            labels.put(module, label);
            widths.put(module, (float) fr.getStringWidth(label));
        }
        rows.sort((a, b) -> Float.compare(widths.get(b), widths.get(a)));

        float listW = 60, listH = 0;
        for (Module module : rows) {
            listW = Math.max(listW, widths.get(module) + 2);
            listH += CLASSIC_ROW * classicRows.get(module).grow;
        }
        if (listH <= 0) listH = CLASSIC_ROW;
        updateDragBounds(sr, listW * size, listH * size);

        float edge = alignX;
        float top = bottomSide ? anchorY - listH : anchorY;
        float out = rightSide ? -1f : 1f;
        int bgColor = new Color(0, 0, 0, 160).getRGB();
        int suffixRgb = 0xFF969696;

        GlStateManager.pushMatrix();
        try {
            if (size != 1f) {
                GlStateManager.translate(alignX, anchorY, 0);
                GlStateManager.scale(size, size, 1);
                GlStateManager.translate(-alignX, -anchorY, 0);
            }

            float y = top;
            float prevFar = Float.NaN;
            for (int i = 0; i < rows.size(); i++) {
                Module module = rows.get(i);
                ClassicRow row = classicRows.get(module);
                String label = labels.get(module);
                float w = widths.get(module);
                float x = rightSide ? edge - w : edge;
                float far = rightSide ? x - 1 : x + w + 1;
                float h = CLASSIC_ROW * row.grow;
                int color = getColorForModule(i, y, x, alignX, module);

                boolean clip = row.grow < 1f;
                if (clip) {
                    float sy = anchorY + (y - anchorY) * size;
                    RenderUtils.startScissor(0, sy, sr.getScaledWidth(), Math.max(0, h * size));
                }

                if (bg.getValue()) drawRectF(far, y, edge, y + CLASSIC_ROW, bgColor);

                drawRectF(far, y, far + out, y + CLASSIC_ROW + 1, color);
                if (i > 0 && !Float.isNaN(prevFar)) drawRectF(far + out, y, prevFar, y + 1, color);
                if (i == rows.size() - 1) drawRectF(far + out, y + CLASSIC_ROW - 0.2f, edge, y + CLASSIC_ROW + 1, color);

                int split = label.indexOf("§7");
                String name = split == -1 ? label : label.substring(0, split);
                String suffix = split == -1 ? "" : label.substring(split + 2);
                float textY = mcFont ? y + (CLASSIC_ROW - 8) / 2f + 1 : y + (CLASSIC_ROW - fr.getHeight()) / 2f;
                fr.drawStringWithShadow(name, x, textY, color);
                if (!suffix.isEmpty()) fr.drawStringWithShadow(suffix, x + fr.getStringWidth(name), textY, suffixRgb);

                if (clip) RenderUtils.endScissor();

                prevFar = far;
                y += h;
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static void drawRectF(float x0, float y0, float x1, float y1, int color) {
        Gui.drawRect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), color);
    }

    private Color getColorForBG() {
        int alpha = bgAlpha.getValue().intValue();

        return switch (bgColor.getValue()) {
            case Normal -> new Color(0, 0, 0, alpha);
            case Theme ->
                    new Color(ColorProcess.getColor().getRed(), ColorProcess.getColor().getGreen(), ColorProcess.getColor().getBlue(), alpha);
            case White -> new Color(255, 255, 255, alpha);
        };
    }

    private int getColorForModule(int visibleModuleIndex, double translateY, double translateX, float screenX, Module module) {
        if (patternOn()) return patternColorAt(rightSide ? screenX - 1 : screenX + 1, translateY + rowHeight() / 2 - 2);
        if (Themes.active()) {
            if (colorMode.getValue() == ColorMode.Static) return Themes.color();
            return Themes.horizontal() ? Themes.atX(screenX) : Themes.row(visibleModuleIndex);
        }
        int offset = colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex * 75 : 75;

        float fluxY = (colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex : 0) * 20f;
        switch (ColorProcess.mode()) {
            case FluxTwoColor -> {
                return ColorProcess.fluxTwoColor(0).getRGB();
            }
            case FluxScroll -> {
                return ColorProcess.fluxTwoColor(fluxY).getRGB();
            }
            case FluxRainbow -> {
                return ColorProcess.fluxRainbow(fluxY).getRGB();
            }
            case FluxCategory -> {
                return ColorProcess.fluxCategory(module.getCategory()).getRGB();
            }
            case FluxModule -> {
                return ColorProcess.fluxModule(module).getRGB();
            }
            default -> {
            }
        }

        if (ColorProcess.mode() == ClientSettingsModule.Color.Picker) {
            Color color1 = new Color(ClientSettingsModule.getPickerColor1(), true);
            Color color2 = new Color(ClientSettingsModule.getPickerColor2(), true);

            long time = System.currentTimeMillis();
            float waveSpeed = 2000f;
            float wavePosition = (time % (long)waveSpeed) / waveSpeed;
            float modulePosition = visibleModuleIndex / (float) Math.max(1, visiblePickerModuleCount - 1);

            float wave = (float) Math.sin((wavePosition - modulePosition) * Math.PI * 2);

            float ratio = (wave + 1f) / 2f;
            return RenderUtils.interpolateColorC(color1, color2, ratio).getRGB();
        }

        if (ColorProcess.mode() == ClientSettingsModule.Color.Astolfo) {
            int index = colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex : 0;
            return RenderUtils.astolfoColors(0, index * 10).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.Rainbow) {
            return RenderUtils.rainbowColors(offset / 2, offset).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.ClassicRainbow) {
            return ColorProcess.classicRainbow(colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex : 0).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.SoftRainbow) {
            return ColorProcess.softRainbow(colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex : 0).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.ExhibitionRainbow) {
            return ColorProcess.exhibitionRainbow(colorMode.getValue() == ColorMode.Fade ? visibleModuleIndex : 0).getRGB();
        }

        if (ColorProcess.mode() == ClientSettingsModule.Color.NewVirtue) {
            return VIRTUE_CATEGORY_COLORS.getOrDefault(module.getCategory(), Color.WHITE).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.Indigo) {
            return ColorProcess.getColor().getRGB();
        }

        if (ColorProcess.mode() == ClientSettingsModule.Color.Category) {
            return CATEGORY_COLORS.getOrDefault(module.getCategory(), Color.WHITE).getRGB();
        }

        if (isSpatial(ColorProcess.mode())) {
            return spatialColor(translateX, translateY);
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.Exhibition) {
            float hue = (System.currentTimeMillis() % 3000) / 3000f;
            if (colorMode.getValue() == ColorMode.Fade) {
                hue += visibleModuleIndex * 0.035f;
            }
            if (hue > 1.0f) hue %= 1.0f;
            return Color.getHSBColor(hue, 0.55f, 0.9f).getRGB();
        }
        if (ColorProcess.mode() == ClientSettingsModule.Color.Category) {
            return CATEGORY_COLORS.getOrDefault(module.getCategory(), java.awt.Color.WHITE).getRGB();
        }
        return RenderUtils.interpolateColorsBackAndForth(15, offset, ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), false).getRGB();
    }

    private String colouredSuffix(String label) {
        return switch (suffixColor.getValue()) {
            case White -> label.replace("§7", "§f");
            case Client -> label.replace("§7", "");
            default -> label;
        };
    }

    private String getDisplayLabel(Module m) {
        String label = m.getLabel();
        String suffix = m.getSuffix();

        if (noSpaces.getValue()) {
            label = label.replace(" ", "");
            if (suffix != null) {
                suffix = suffix.replace(" ", "");
            }
        }

        if (lowercase.getValue()) {
            label = label.toLowerCase();
            if (suffix != null) {
                suffix = suffix.toLowerCase();
            }
        }

        if (suffix != null && showSuffix.getValue()) {
            return switch (suffixMode.getValue()) {
                case Space -> label + " \2477" + suffix;
                case Dash -> label + " \2477- " + suffix;
                case Brackets -> label + " \2477[" + suffix + "]";
                case Pipe -> label + " \2477| " + suffix;
            };
        } else {
            return label;
        }
    }

    private void updateModulePositions(CustomFontRenderer fr, List<Module> filteredModules, float startY, float dir, float spacing, float parkY) {
        float y = startY;

        for (Module module : filteredModules) {
            float w = fr.getStringWidth(getDisplayLabel(module));
            if (module.isVisible()) {
                module.getTranslate().setX(rightSide ? alignX - w - 1 : alignX + 1);
                module.getTranslate().setY(y);
                y += dir * (rowHeight() + spacing);
            } else {
                module.getTranslate().setX(rightSide ? alignX : alignX - w);
                module.getTranslate().setY(parkY);
            }
        }
    }

    private abstract static class ModuleComparator implements Comparator<Module> {
        protected CustomFontRenderer fontRenderer;

        @Override
        public abstract int compare(Module o1, Module o2);

        public CustomFontRenderer getFontRenderer() {
            return fontRenderer;
        }

        public void setFontRenderer(CustomFontRenderer fontRenderer) {
            this.fontRenderer = fontRenderer;
        }
    }

    private static class LengthComparator extends ModuleComparator {
        @Override
        public int compare(Module o1, Module o2) {
            CustomFontRenderer fr = FontProcess.getCurrentFont();
            if (useMcFont.getValue()) fr = FontProcess.getFont("mc");
            return Float.compare(
                    fr.getStringWidth(displayLabelCache.get(o2)),
                    fr.getStringWidth(displayLabelCache.get(o1)));
        }
    }
}
