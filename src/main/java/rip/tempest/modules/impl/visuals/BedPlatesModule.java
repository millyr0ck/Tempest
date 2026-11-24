package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Bed Plates", category = ModuleCategory.VISUALS)
public final class BedPlatesModule extends Module {
    private final ModeProperty<ColorMode> colorMode = new ModeProperty<>("Color", ColorMode.Static);
    private final NumberProperty range = new NumberProperty("Range", 20, 2, 100, 2);
    private final Property<Boolean> firstBed = new Property<>("Only First Bed", false);
    private final Property<Boolean> renderFullBlock = new Property<>("Full Block", false);
    private final Property<Boolean> showExposedOutline = new Property<>("Exposed Outline", true);
    private final Property<Boolean> showDefenseLayers = new Property<>("Defense Layers", false);
    private final Property<Boolean> showDefenseTools = new Property<>("Break Tools", false, showDefenseLayers::getValue);
    private final Property<Boolean> showDefenseCounts = new Property<>("Defense Counts", true,
            () -> showDefenseLayers.getValue() && !showDefenseTools.getValue());
    private final NumberProperty defenseHeight = new NumberProperty("Defense Height", 0.6, showDefenseLayers::getValue, 0.1, 3.0, 0.1);
    private final NumberProperty defenseScale = new NumberProperty("Defense Scale", 1.0, showDefenseLayers::getValue, 0.1, 2.0, 0.05);
    private final Property<Boolean> defenseAutoScale = new Property<>("Auto Scale", false, showDefenseLayers::getValue);

    private enum ColorMode { Static, Gradient, Rainbow }

    private static final int DEFENSE_ICON_SIZE = 16;
    private static final int DEFENSE_ICON_SPACING = 18;
    private static final int DEFENSE_PADDING = 3;
    private static final int DEFENSE_MAX_LAYERS = 5;
    private static final float DEFENSE_AIR_RATIO_THRESHOLD = 0.2F;
    private static final float DEFENSE_BLOCK_RATIO_THRESHOLD = 0.2F;
    private static final float DEFENSE_AUTO_SCALE_THRESHOLD = 8.0F;
    private static final int MAX_SCAN_RADIUS = 64;
    private static final int RESCAN_INTERVAL = 20;
    private static final EnumMap<EnumFacing, LayerOffsets[]> DEFENSE_OFFSETS = buildLayerOffsetsByFacing();

    private final List<BlockPos[]> activeBedPairs = new ArrayList<>();
    private final Map<Long, DefenseOverlaySnapshot> defenseSnapshots = new HashMap<>();
    private int tickCounter;

    @Override
    public void onEnable() {
        super.onEnable();
        rescan();
    }

    @Override
    public void onDisable() {
        activeBedPairs.clear();
        defenseSnapshots.clear();
        super.onDisable();
    }

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) {
            activeBedPairs.clear();
            defenseSnapshots.clear();
            return;
        }
        if (++tickCounter % RESCAN_INTERVAL == 0) {
            rescan();
        }
    };

    @EventLink
    public final Listener<Render3DEvent> render3DEventListener = event -> {
        if (mc.theWorld == null || mc.thePlayer == null || activeBedPairs.isEmpty()) return;

        final float height = renderFullBlock.getValue() ? 1.0f : 0.5625f;
        final Color themeColor = getCurrentColor();
        final float r = themeColor.getRed() / 255f;
        final float g = themeColor.getGreen() / 255f;
        final float b = themeColor.getBlue() / 255f;

        for (BlockPos[] pair : new ArrayList<>(activeBedPairs)) {
            if (!isLiveBedPair(pair)) continue;
            renderBed(pair, height, r, g, b);
            if (showDefenseLayers.getValue()) {
                renderDefenseOverlay(pair, height);
            }
        }
    };

    private void rescan() {
        activeBedPairs.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            defenseSnapshots.clear();
            return;
        }

        final double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        final double rangeSq = range.getValue() * range.getValue();
        final int r = Math.min((int) range.getValue().doubleValue(), MAX_SCAN_RADIUS);
        final int cx = (int) px, cy = (int) py, cz = (int) pz;
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = Math.max(0, cy - r); y <= Math.min(255, cy + r); y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    m.set(x, y, z);
                    final IBlockState st = mc.theWorld.getBlockState(m);
                    if (!isBedFoot(st)) continue;

                    final double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

                    final BlockPos foot = new BlockPos(x, y, z);
                    final EnumFacing facing = (EnumFacing) st.getValue((IProperty) BlockBed.FACING);
                    activeBedPairs.add(new BlockPos[]{foot, foot.offset(facing)});
                }
            }
        }

        if (firstBed.getValue() && activeBedPairs.size() > 1) {
            BlockPos[] nearest = null;
            double best = Double.MAX_VALUE;
            for (BlockPos[] pair : activeBedPairs) {
                final double dx = pair[0].getX() + 0.5 - px, dy = pair[0].getY() + 0.5 - py, dz = pair[0].getZ() + 0.5 - pz;
                final double d = dx * dx + dy * dy + dz * dz;
                if (d < best) {
                    best = d;
                    nearest = pair;
                }
            }
            activeBedPairs.clear();
            if (nearest != null) activeBedPairs.add(nearest);
        }

        final Set<Long> activeFeet = new HashSet<>();
        if (showDefenseLayers.getValue()) {
            for (BlockPos[] pair : activeBedPairs) {
                final long key = pair[0].toLong();
                activeFeet.add(key);
                defenseSnapshots.put(key, computeDefenseSnapshot(pair[0], pair[1]));
            }
        }
        defenseSnapshots.keySet().retainAll(activeFeet);
    }

    private static boolean isBedFoot(IBlockState st) {
        return st != null && st.getBlock() instanceof BlockBed
                && st.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT;
    }

    private boolean isLiveBedPair(BlockPos[] pair) {
        if (pair == null || pair.length < 2 || mc.theWorld == null) return false;
        final IBlockState footState = mc.theWorld.getBlockState(pair[0]);
        final IBlockState headState = mc.theWorld.getBlockState(pair[1]);
        return isBedFoot(footState) && headState != null && headState.getBlock() instanceof BlockBed;
    }

    private void renderBed(BlockPos[] blocks, float height, float r, float g, float b) {
        final boolean exposed = showExposedOutline.getValue() && isBedExposed(blocks);
        final double x = blocks[0].getX() - mc.getRenderManager().viewerPosX;
        final double y = blocks[0].getY() - mc.getRenderManager().viewerPosY;
        final double z = blocks[0].getZ() - mc.getRenderManager().viewerPosZ;

        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GlStateManager.disableCull();
        GL11.glDepthMask(false);

        final AxisAlignedBB box = bedRenderBounds(blocks[0], blocks[1], x, y, z, height);

        GlStateManager.color(r, g, b, 0.3f);
        RenderUtils.drawBoundingBox(box);
        GlStateManager.color(r, g, b, 1.0f);
        RenderUtils.drawOutlinedBoundingBox(box);

        if (exposed) {
            GL11.glLineWidth(3.0f);
            GlStateManager.color(1.0f, 0.86f, 0.25f, 1.0f);
            RenderGlobal.drawSelectionBoundingBox(box);
            GL11.glLineWidth(2.0f);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableCull();
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
    }

    private static AxisAlignedBB bedRenderBounds(BlockPos foot, BlockPos head, double x, double y, double z, float height) {
        if (foot.getX() != head.getX()) {
            if (foot.getX() > head.getX()) {
                return new AxisAlignedBB(x - 1.0, y, z, x + 1.0, y + height, z + 1.0);
            }
            return new AxisAlignedBB(x, y, z, x + 2.0, y + height, z + 1.0);
        }
        if (foot.getZ() > head.getZ()) {
            return new AxisAlignedBB(x, y, z - 1.0, x + 1.0, y + height, z + 1.0);
        }
        return new AxisAlignedBB(x, y, z, x + 1.0, y + height, z + 2.0);
    }

    private static AxisAlignedBB bedWorldBounds(BlockPos foot, BlockPos head, float height) {
        final int fx = foot.getX(), fy = foot.getY(), fz = foot.getZ();
        final double h = fy + height;
        if (foot.getX() != head.getX()) {
            if (foot.getX() > head.getX()) {
                return new AxisAlignedBB(fx - 1.0, fy, fz, fx + 1.0, h, fz + 1.0);
            }
            return new AxisAlignedBB(fx, fy, fz, fx + 2.0, h, fz + 1.0);
        }
        if (foot.getZ() > head.getZ()) {
            return new AxisAlignedBB(fx, fy, fz - 1.0, fx + 1.0, h, fz + 1.0);
        }
        return new AxisAlignedBB(fx, fy, fz, fx + 1.0, h, fz + 2.0);
    }

    private boolean isBedExposed(BlockPos[] pair) {
        if (pair == null || pair.length < 2 || mc.theWorld == null) return false;
        for (BlockPos bedPart : pair) {
            for (EnumFacing side : EnumFacing.values()) {
                if (mc.theWorld.getBlockState(bedPart.offset(side)).getBlock() == Blocks.air) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderDefenseOverlay(BlockPos[] blocks, float blockHeight) {
        final DefenseOverlaySnapshot snapshot = defenseSnapshots.get(blocks[0].toLong());
        if (snapshot == null || !snapshot.matches(blocks[1]) || snapshot.entries.isEmpty()) return;

        final RenderManager renderManager = mc.getRenderManager();
        final FontRenderer fontRenderer = mc.fontRendererObj;
        if (renderManager == null || fontRenderer == null) return;

        final AxisAlignedBB bounds = bedWorldBounds(blocks[0], blocks[1], blockHeight);
        final double x = (bounds.minX + bounds.maxX) * 0.5 - renderManager.viewerPosX;
        final double y = bounds.maxY + defenseHeight.getValue() - renderManager.viewerPosY;
        final double z = (bounds.minZ + bounds.maxZ) * 0.5 - renderManager.viewerPosZ;

        float scale = (float) defenseScale.getValue().doubleValue() * 0.02F;
        if (defenseAutoScale.getValue()) {
            final float distance = (float) Math.sqrt(x * x + y * y + z * z);
            final float effective = Math.max(1.0F, distance);
            scale = Math.max(scale, scale * (effective / DEFENSE_AUTO_SCALE_THRESHOLD));
        }

        final List<DefenseOverlayEntry> stacks = snapshot.entries;
        final int contentWidth = stacks.size() * DEFENSE_ICON_SPACING - (DEFENSE_ICON_SPACING - DEFENSE_ICON_SIZE);
        final int left = -contentWidth / 2;
        final int iconY = -8;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);

            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

            drawOverlayBackground(left - DEFENSE_PADDING, iconY - DEFENSE_PADDING,
                    left + contentWidth + DEFENSE_PADDING, iconY + DEFENSE_ICON_SIZE + DEFENSE_PADDING);

            for (int i = 0; i < stacks.size(); i++) {
                final DefenseOverlayEntry entry = stacks.get(i);
                final int iconX = left + i * DEFENSE_ICON_SPACING;
                if (entry.renderStack != null && entry.renderStack.getItem() != null) {
                    RenderHelper.enableGUIStandardItemLighting();
                    mc.getRenderItem().renderItemAndEffectIntoGUI(entry.renderStack, iconX, iconY);
                    RenderHelper.disableStandardItemLighting();
                }

                if (!showDefenseTools.getValue() && showDefenseCounts.getValue() && entry.count > 1) {
                    final String countText = String.valueOf(entry.count);
                    GlStateManager.disableLighting();
                    GlStateManager.disableDepth();
                    GlStateManager.enableTexture2D();
                    fontRenderer.drawStringWithShadow(countText, iconX + 17 - fontRenderer.getStringWidth(countText), iconY + 9, 0xFFFFFF);
                }
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static void drawOverlayBackground(int left, int top, int right, int bottom) {
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.45F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(left, bottom);
        GL11.glVertex2d(right, bottom);
        GL11.glVertex2d(right, top);
        GL11.glVertex2d(left, top);
        GL11.glEnd();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.6F);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(left, bottom);
        GL11.glVertex2d(right, bottom);
        GL11.glVertex2d(right, top);
        GL11.glVertex2d(left, top);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private DefenseOverlaySnapshot computeDefenseSnapshot(BlockPos foot, BlockPos head) {
        final EnumFacing facing = getBedFacing(foot, head);
        final LayerOffsets[] layers = facing == null ? null : DEFENSE_OFFSETS.get(facing);
        if (layers == null) {
            return new DefenseOverlaySnapshot(head, Collections.<DefenseOverlayEntry>emptyList());
        }

        final Map<DefenseBlockKey, Integer> finalCounts = new HashMap<>();
        final Map<Integer, DefenseBlockKey> resolvedBlockKeys = new HashMap<>();
        int sparseLayerCount = 0;
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (LayerOffsets layerOffsets : layers) {
            final Map<DefenseBlockKey, Integer> layerCounts = new HashMap<>();
            int layerTotalBlocks = 0;
            int layerAirBlocks = 0;

            for (RelativeOffset offset : layerOffsets.positions) {
                mutablePos.set(foot.getX() + offset.x, foot.getY() + offset.y, foot.getZ() + offset.z);
                if (accumulateDefenseBlock(mutablePos, layerCounts, resolvedBlockKeys)) {
                    layerAirBlocks++;
                }
                layerTotalBlocks++;
            }

            if (layerTotalBlocks == 0 || (float) layerAirBlocks / (float) layerTotalBlocks > DEFENSE_AIR_RATIO_THRESHOLD) {
                if (++sparseLayerCount >= 2) break;
                continue;
            }

            sparseLayerCount = 0;
            for (Map.Entry<DefenseBlockKey, Integer> countedBlock : layerCounts.entrySet()) {
                final int count = countedBlock.getValue();
                if ((float) count / (float) layerTotalBlocks >= DEFENSE_BLOCK_RATIO_THRESHOLD) {
                    finalCounts.put(countedBlock.getKey(), finalCounts.getOrDefault(countedBlock.getKey(), 0) + count);
                }
            }
        }

        final List<DefenseOverlayEntry> entries;
        if (showDefenseTools.getValue()) {
            final Set<ToolOverlayType> toolTypes = new HashSet<>();
            for (DefenseBlockKey countedBlock : finalCounts.keySet()) {
                final ToolOverlayType toolType = ToolOverlayType.fromState(countedBlock.state);
                if (toolType != null) toolTypes.add(toolType);
            }
            if (toolTypes.contains(ToolOverlayType.DIAMOND_PICKAXE)) {
                toolTypes.remove(ToolOverlayType.IRON_PICKAXE);
            }
            entries = new ArrayList<>(toolTypes.size());
            for (ToolOverlayType toolType : toolTypes) {
                entries.add(new DefenseOverlayEntry(toolType.createRenderStack(), 1, toolType.sortName));
            }
            entries.sort((a, b) -> a.sortName.compareToIgnoreCase(b.sortName));
        } else {
            entries = new ArrayList<>();
            for (Map.Entry<DefenseBlockKey, Integer> countedBlock : finalCounts.entrySet()) {
                entries.add(new DefenseOverlayEntry(countedBlock.getKey().createRenderStack(),
                        countedBlock.getValue(), countedBlock.getKey().sortName));
            }
            entries.sort((a, b) -> {
                final int countCompare = Integer.compare(b.count, a.count);
                return countCompare != 0 ? countCompare : a.sortName.compareToIgnoreCase(b.sortName);
            });
        }

        return new DefenseOverlaySnapshot(head, Collections.unmodifiableList(entries));
    }

    private boolean accumulateDefenseBlock(BlockPos pos, Map<DefenseBlockKey, Integer> layerCounts, Map<Integer, DefenseBlockKey> resolvedBlockKeys) {
        final IBlockState state = mc.theWorld.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.air) {
            return true;
        }
        final IBlockState normalizedState = normalizeDefenseState(state);
        final int stateId = Block.getStateId(normalizedState);
        DefenseBlockKey key = resolvedBlockKeys.get(stateId);
        if (key == null) {
            key = DefenseBlockKey.from(normalizedState, pos, mc.theWorld);
            resolvedBlockKeys.put(stateId, key);
        }
        layerCounts.put(key, layerCounts.getOrDefault(key, 0) + 1);
        return false;
    }

    private static EnumFacing getBedFacing(BlockPos foot, BlockPos head) {
        final int dx = head.getX() - foot.getX();
        final int dz = head.getZ() - foot.getZ();
        for (EnumFacing facing : EnumFacing.values()) {
            if (facing.getFrontOffsetY() == 0 && facing.getFrontOffsetX() == dx && facing.getFrontOffsetZ() == dz) {
                return facing;
            }
        }
        return null;
    }

    private static IBlockState normalizeDefenseState(IBlockState state) {
        if (state == null) return null;
        final Block block = state.getBlock();
        if (block == Blocks.water || block == Blocks.flowing_water) return Blocks.water.getDefaultState();
        if (block == Blocks.lava || block == Blocks.flowing_lava) return Blocks.lava.getDefaultState();
        if (block == Blocks.fire) return Blocks.fire.getDefaultState();
        return state;
    }

    private static EnumMap<EnumFacing, LayerOffsets[]> buildLayerOffsetsByFacing() {
        final EnumMap<EnumFacing, LayerOffsets[]> offsets = new EnumMap<>(EnumFacing.class);
        offsets.put(EnumFacing.EAST, buildLayerOffsets(EnumFacing.EAST));
        offsets.put(EnumFacing.WEST, buildLayerOffsets(EnumFacing.WEST));
        offsets.put(EnumFacing.SOUTH, buildLayerOffsets(EnumFacing.SOUTH));
        offsets.put(EnumFacing.NORTH, buildLayerOffsets(EnumFacing.NORTH));
        return offsets;
    }

    private static LayerOffsets[] buildLayerOffsets(EnumFacing canonicalFacing) {
        final BlockPos foot = BlockPos.ORIGIN;
        final BlockPos head = foot.offset(canonicalFacing);
        final boolean facingZ = canonicalFacing.getAxis() == EnumFacing.Axis.Z;
        final BlockPos firstBedPart = facingZ
                ? (head.getZ() > foot.getZ() ? head : foot)
                : (head.getX() > foot.getX() ? head : foot);
        final BlockPos secondBedPart = firstBedPart.equals(foot) ? head : foot;
        final BlockPos[] bedParts = {firstBedPart, secondBedPart};
        final LayerOffsets[] layers = new LayerOffsets[DEFENSE_MAX_LAYERS];
        final Set<Long> seenAcrossLayers = new HashSet<>();

        for (int layer = 1; layer <= DEFENSE_MAX_LAYERS; layer++) {
            final List<RelativeOffset> offsets = new ArrayList<>();

            for (int bedIndex = 0; bedIndex < bedParts.length; bedIndex++) {
                final BlockPos bed = bedParts[bedIndex];
                final int outwardOffset = bedIndex == 0 ? layer : -layer;
                final int startX = facingZ ? bed.getX() : bed.getX() + outwardOffset;
                final int startZ = facingZ ? bed.getZ() + outwardOffset : bed.getZ();

                for (int advance = 0; advance <= layer; advance++) {
                    int yOffset = 0;
                    for (int breadth = advance; breadth >= 0; breadth--) {
                        int firstX, firstZ, secondX, secondZ;
                        final int firstY = bed.getY() + yOffset;
                        final int secondY = firstY;

                        if (facingZ) {
                            final int zz = startZ - (bedIndex == 0 ? advance : -advance);
                            firstX = startX - breadth;
                            firstZ = zz;
                            secondX = startX + breadth;
                            secondZ = zz;
                        } else {
                            final int xx = startX - (bedIndex == 0 ? advance : -advance);
                            firstX = xx;
                            firstZ = startZ - breadth;
                            secondX = xx;
                            secondZ = startZ + breadth;
                        }

                        addOffset(offsets, seenAcrossLayers, firstX, firstY, firstZ);
                        addOffset(offsets, seenAcrossLayers, secondX, secondY, secondZ);

                        if (breadth > 0) yOffset++;
                    }
                }
            }

            layers[layer - 1] = new LayerOffsets(Collections.unmodifiableList(offsets));
        }

        return layers;
    }

    private static void addOffset(List<RelativeOffset> offsets, Set<Long> seen, int x, int y, int z) {
        final long key = new BlockPos(x, y, z).toLong();
        if (seen.add(key)) {
            offsets.add(new RelativeOffset(x, y, z));
        }
    }

    private Color getCurrentColor() {
        switch (colorMode.getValue()) {
            case Gradient: {
                final Color c1 = ColorProcess.getColors().getFirst();
                final Color c2 = ColorProcess.getColors().getSecond();
                final double pct = Math.sin(System.currentTimeMillis() / 1000.0) * 0.5 + 0.5;
                return new Color(
                        (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * pct),
                        (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * pct),
                        (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * pct));
            }
            case Rainbow: {
                final float hue = (System.currentTimeMillis() % 2000) / 2000f;
                return Color.getHSBColor(hue, 0.8f, 1.0f);
            }
            case Static:
            default:
                return ColorProcess.getColor();
        }
    }

    private static final class DefenseOverlaySnapshot {
        private final long headKey;
        private final List<DefenseOverlayEntry> entries;

        private DefenseOverlaySnapshot(BlockPos head, List<DefenseOverlayEntry> entries) {
            this.headKey = head.toLong();
            this.entries = entries;
        }

        private boolean matches(BlockPos otherHead) {
            return otherHead != null && headKey == otherHead.toLong();
        }
    }

    private static final class DefenseOverlayEntry {
        private final ItemStack renderStack;
        private final int count;
        private final String sortName;

        private DefenseOverlayEntry(ItemStack renderStack, int count, String sortName) {
            this.renderStack = renderStack;
            this.count = count;
            this.sortName = sortName;
        }
    }

    private static final class DefenseBlockKey {
        private final IBlockState state;
        private final String identityKey;
        private final String sortName;
        private final int hashCode;
        private final ItemStack renderStack;

        private DefenseBlockKey(IBlockState state, String identityKey, String sortName, ItemStack renderStack) {
            this.state = state;
            this.identityKey = identityKey;
            this.sortName = sortName;
            this.renderStack = renderStack;
            this.hashCode = identityKey.hashCode();
        }

        private static DefenseBlockKey from(IBlockState state, BlockPos pos, World world) {
            final String fallbackName = getFallbackStateName(state);
            ItemStack stack = resolveBlockItemStack(state, pos, world);
            if (stack == null || stack.getItem() == null) {
                stack = fallbackRenderStack(state.getBlock());
            }
            final String identityKey = resolveIdentityKey(state, stack);
            final String sortName = stack != null && stack.getItem() != null ? getSafeDisplayName(stack, fallbackName) : fallbackName;
            return new DefenseBlockKey(state, identityKey, sortName, stack);
        }

        private ItemStack createRenderStack() {
            return renderStack == null ? null : renderStack.copy();
        }

        private static String resolveIdentityKey(IBlockState state, ItemStack stack) {
            if (stack != null && stack.getItem() != null) {
                return Item.getIdFromItem(stack.getItem()) + ":" + stack.getMetadata();
            }
            final Object registryName = Block.blockRegistry.getNameForObject(state.getBlock());
            return registryName != null ? registryName.toString() : Integer.toString(Block.getIdFromBlock(state.getBlock()));
        }

        private static String getSafeDisplayName(ItemStack stack, String fallback) {
            if (stack == null || stack.getItem() == null) return fallback;
            try {
                final String displayName = stack.getDisplayName();
                return displayName != null && !displayName.isEmpty() ? displayName : fallback;
            } catch (Exception ignored) {
                return fallback;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DefenseBlockKey)) return false;
            return identityKey.equals(((DefenseBlockKey) obj).identityKey);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private enum ToolOverlayType {
        DIAMOND_PICKAXE(Items.diamond_pickaxe, "Diamond Pickaxe"),
        IRON_AXE(Items.iron_axe, "Iron Axe"),
        IRON_HOE(Items.iron_hoe, "Iron Hoe"),
        IRON_PICKAXE(Items.iron_pickaxe, "Iron Pickaxe"),
        SHEARS(Items.shears, "Shears"),
        IRON_SHOVEL(Items.iron_shovel, "Iron Shovel"),
        IRON_SWORD(Items.iron_sword, "Iron Sword");

        private final String sortName;
        private final ItemStack renderStack;

        ToolOverlayType(Item item, String sortName) {
            this.sortName = sortName;
            this.renderStack = new ItemStack(item);
        }

        private ItemStack createRenderStack() {
            return renderStack.copy();
        }

        private static ToolOverlayType fromState(IBlockState state) {
            if (state == null) return null;
            final Block block = state.getBlock();
            if (block == Blocks.obsidian) return DIAMOND_PICKAXE;

            ToolOverlayType bestTool = IRON_PICKAXE;
            float bestEfficiency = 1.0F;
            for (ToolOverlayType toolType : values()) {
                if (toolType == DIAMOND_PICKAXE) continue;
                final float efficiency = toolType.renderStack.getStrVsBlock(block);
                if (efficiency > bestEfficiency) {
                    bestEfficiency = efficiency;
                    bestTool = toolType;
                }
            }
            return bestTool;
        }
    }

    private static ItemStack resolveBlockItemStack(IBlockState state, BlockPos pos, World world) {
        final Block block = state.getBlock();
        try {
            final Item item = block.getItem(world, pos);
            if (item == null) return null;
            final int meta = item.getHasSubtypes() ? block.getDamageValue(world, pos) : 0;
            return new ItemStack(item, 1, meta);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getFallbackStateName(IBlockState state) {
        final String localizedName = state.getBlock().getLocalizedName();
        if (localizedName != null && !localizedName.isEmpty()) return localizedName;
        final Object registryName = Block.blockRegistry.getNameForObject(state.getBlock());
        if (registryName != null) {
            final int meta = state.getBlock().getMetaFromState(state);
            return meta != 0 ? registryName + ":" + meta : registryName.toString();
        }
        return "unknown";
    }

    private static ItemStack fallbackRenderStack(Block block) {
        if (block == Blocks.bed) return new ItemStack(Items.bed);
        try {
            final Item item = Item.getItemFromBlock(block);
            if (item != null) {
                final int meta = block.getMetaFromState(block.getDefaultState());
                return new ItemStack(item, 1, meta);
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Blocks.barrier);
    }

    private static final class LayerOffsets {
        private final List<RelativeOffset> positions;

        private LayerOffsets(List<RelativeOffset> positions) {
            this.positions = positions;
        }
    }

    private static final class RelativeOffset {
        private final int x;
        private final int y;
        private final int z;

        private RelativeOffset(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
