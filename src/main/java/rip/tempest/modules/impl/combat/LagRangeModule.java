package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.PostTickEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.modules.impl.client.MCFModule;
import rip.tempest.modules.impl.player.BedNukerModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.processes.LagProcess;
import rip.tempest.processes.PacketDelayProcess;
import rip.tempest.processes.TargetSelectionProcess;
import rip.tempest.utils.client.MathUtils;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.render.RenderUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Lag Range", category = ModuleCategory.COMBAT)
public final class LagRangeModule extends Module {
    private enum Mode {
        Tempest, Myau
    }

    private enum ShowPosition {
        None, Default, HUD
    }

    private static final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Tempest);
    public static NumberProperty rangeProperty = new NumberProperty("Range", 3.5, () -> mode.getValue() == Mode.Tempest, 0.1, 6, 0.05);
    public static NumberProperty minDelayProperty = new NumberProperty("Min Delay", 50.0, () -> mode.getValue() == Mode.Tempest, 0.0, 5000.0, 10.0);
    public static NumberProperty maxDelayProperty = new NumberProperty("Max Delay", 200.0, () -> mode.getValue() == Mode.Tempest, 0.0, 5000.0, 10.0);
    public static final Property<Boolean> displayProperty = new Property<>("Display", true, () -> mode.getValue() == Mode.Tempest);
    public static final Property<Boolean> onlyWithKillaura = new Property<>("Only With Killaura", true, () -> mode.getValue() == Mode.Tempest);

    private final NumberProperty myauDelay = new NumberProperty("Delay", 100, () -> mode.getValue() == Mode.Myau, 0, 1000, 10);
    private final NumberProperty myauRange = new NumberProperty("Target Range", 10.0, () -> mode.getValue() == Mode.Myau, 3.0, 100.0, 0.5);
    private final Property<Boolean> weaponsOnly = new Property<>("Weapons Only", true, () -> mode.getValue() == Mode.Myau);
    private final Property<Boolean> allowTools = new Property<>("Allow Tools", false, () -> mode.getValue() == Mode.Myau && weaponsOnly.getValue());
    private final Property<Boolean> botCheck = new Property<>("Bot Check", true, () -> mode.getValue() == Mode.Myau);
    private final Property<Boolean> teams = new Property<>("Teams", true, () -> mode.getValue() == Mode.Myau);
    private final ModeProperty<ShowPosition> showPosition = new ModeProperty<>("Show Position", ShowPosition.None, () -> mode.getValue() == Mode.Myau);

    private boolean lagging = false;
    private int lagAmount = 69;

    private int delayTicks = -1;
    private long remainingDelayMillis;
    private boolean delaying;
    private Vec3 previousPosition;
    private Vec3 currentPosition;

    @EventLink
    public Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        if (lagging) {
            disableLag();
        }
    };

    @EventLink
    public Listener<MotionEvent> motionEventListener = event -> {
        if (mode.getValue() != Mode.Tempest) {
            if (lagging) disableLag();
            return;
        }

        setSuffix(rangeProperty.getValue().toString());
        if (event.isPre()) return;

        if (onlyWithKillaura.getValue() && !Tempest.INSTANCE.getModuleManager().getModule(AuraModule.class).isEnabled() && AuraModule.target() == null) {
            if (lagging) disableLag();
            return;
        }

        if (lagging) lagAmount = (int) MathUtils.getRandom(minDelayProperty.getValue().intValue(), maxDelayProperty.getValue().intValue());

        if (!lagging) lagAmount = 69;

        setSuffix(String.format("%.1f | %dms", rangeProperty.getValue(), lagAmount));

        if (TargetSelectionProcess.getTarget() == null) {
            if (lagging) disableLag();
            return;
        }

        if (!MovementUtils.isMoving()) {
            if (lagging) disableLag();
            return;
        }

        double distance = mc.thePlayer.getDistanceToEntity(TargetSelectionProcess.getTarget());

        if (distance <= AuraModule.swingRange() && distance > rangeProperty.getValue()) {
            if (!lagging) {
                enableLag();
            }
        } else {
            if (lagging) {
                disableLag();
            }
        }
    };

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = event -> {
        if (mode.getValue() != Mode.Tempest || !displayProperty.getValue() || !lagging) return;

        CustomFontRenderer fr = FontProcess.getCurrentFont();
        ScaledResolution sr = new ScaledResolution(mc);
        String text = String.format("Lagging %dms", lagAmount);
        fr.drawStringWithShadow(text,
                (float) sr.getScaledWidth() / 2 - (float) fr.getStringWidth(text) / 2,
                sr.getScaledHeight() / 10f - fr.FONT_HEIGHT,
                Color.YELLOW.getRGB());
    };

    @EventLink(value = Priorities.LOW)
    public final Listener<TickEvent> myauTickListener = event -> {
        if (mode.getValue() != Mode.Myau) return;
        setSuffix(myauDelay.getValue().intValue() + "ms");
        PacketDelayProcess.setDelayTicks(0);
        delaying = false;

        if (BedNukerModule.holdsKillAura()) {
            delayTicks = -1;
        } else if (!mc.playerController.getIsHittingBlock() && (!mc.thePlayer.isUsingItem() || mc.thePlayer.isBlocking()) && holdingAllowedItem()) {
            List<EntityPlayer> targets = mc.theWorld.loadedEntityList.stream()
                    .filter(entity -> entity instanceof EntityPlayer)
                    .map(EntityPlayer.class::cast)
                    .filter(this::isTarget)
                    .collect(Collectors.toList());
            if (targets.isEmpty()) {
                delayTicks = -1;
            } else {
                double eyeHeight = mc.thePlayer.getEyeHeight();
                Vec3 bufferedEyes = PacketDelayProcess.lastReleasedPosition().addVector(0.0, eyeHeight, 0.0);
                Vec3 previousEyes = new Vec3(mc.thePlayer.lastTickPosX, mc.thePlayer.lastTickPosY + eyeHeight, mc.thePlayer.lastTickPosZ);
                Vec3 currentEyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + eyeHeight, mc.thePlayer.posZ);

                for (EntityPlayer target : targets) {
                    double currentDistance = distanceTo(target, currentEyes);
                    if (currentDistance > myauRange.getValue()) continue;
                    double previousDistance = distanceTo(target, previousEyes);
                    double bufferedDistance = distanceTo(target, bufferedEyes);
                    if (currentDistance < previousDistance || currentDistance < bufferedDistance) {
                        delayTicks = 0;
                        for (remainingDelayMillis += myauDelay.getValue().intValue(); remainingDelayMillis > 0L; remainingDelayMillis -= 50L) {
                            delayTicks++;
                        }
                        PacketDelayProcess.setDelayTicks(delayTicks);
                        delaying = true;
                        break;
                    }
                }
            }
        } else {
            delayTicks = -1;
        }
    };

    @EventLink(value = Priorities.LOW)
    public final Listener<PostTickEvent> myauPostTickListener = event -> {
        if (mode.getValue() != Mode.Myau) {
            if (delayTicks != -1 || delaying) resetMyau();
            return;
        }
        Vec3 position = PacketDelayProcess.lastReleasedPosition();
        previousPosition = currentPosition == null ? position : currentPosition;
        currentPosition = position;
    };

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> myauPacketSendListener = event -> {
        if (mode.getValue() != Mode.Myau || event.isCancelled()) return;
        if (shouldFlush(event.getPacket())) {
            PacketDelayProcess.setDelayTicks(0);
            delayTicks = -1;
        }
    };

    @EventLink(value = Priorities.HIGH)
    public final Listener<Render3DEvent> myauRenderListener = event -> {
        if (mode.getValue() != Mode.Myau || showPosition.getValue() == ShowPosition.None || mc.gameSettings.thirdPersonView == 0) return;
        if (!delaying || previousPosition == null || currentPosition == null) return;

        Color color = showPosition.getValue() == ShowPosition.Default ? teamColor(mc.thePlayer) : ColorProcess.getColor();
        float partialTicks = mc.timer.renderPartialTicks;
        double x = previousPosition.xCoord + (currentPosition.xCoord - previousPosition.xCoord) * partialTicks;
        double y = previousPosition.yCoord + (currentPosition.yCoord - previousPosition.yCoord) * partialTicks;
        double z = previousPosition.zCoord + (currentPosition.zCoord - previousPosition.zCoord) * partialTicks;
        float border = mc.thePlayer.getCollisionBorderSize();
        double halfWidth = mc.thePlayer.width / 2.0;
        AxisAlignedBB box = new AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + mc.thePlayer.height, z + halfWidth)
                .expand(border, border, border)
                .offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 63 / 255.0F);
        RenderUtils.drawBoundingBox(box);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.resetColor();
    };

    private static boolean shouldFlush(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) return true;
        if (packet instanceof C07PacketPlayerDigging digging) return digging.getStatus() != C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
        if (!(packet instanceof C08PacketPlayerBlockPlacement placement)) return false;
        ItemStack stack = placement.getStack();
        return stack == null || !(stack.getItem() instanceof ItemSword);
    }

    private boolean isTarget(EntityPlayer player) {
        Entity view = mc.getRenderViewEntity();
        if (player == mc.thePlayer || player == mc.thePlayer.ridingEntity || player == view || view != null && player == view.ridingEntity) return false;
        if (player.deathTime > 0 || MCFModule.isExcluded(player)) return false;
        if (teams.getValue() && isTeammate(player)) return false;
        return !botCheck.getValue() || !isBot(player);
    }

    private static boolean isTeammate(EntityPlayer player) {
        NetworkPlayerInfo localInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (localInfo == null || localInfo.getPlayerTeam() == null) return false;
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        return targetInfo != null && targetInfo.getPlayerTeam() != null
                && localInfo.getPlayerTeam().getColorPrefix().equals(targetInfo.getPlayerTeam().getColorPrefix());
    }

    private static boolean isBot(EntityPlayer player) {
        return mc.getNetHandler().getPlayerInfo(player.getName()) == null || AntiBotModule.botList.contains(player);
    }

    private static Color teamColor(EntityPlayer player) {
        if (!(player.getTeam() instanceof ScorePlayerTeam team)) return Color.WHITE;
        String formatting = FontRenderer.getFormatFromString(team.getColorPrefix());
        return formatting.length() < 2 ? Color.WHITE : new Color(mc.fontRendererObj.getColorCode(formatting.charAt(1)));
    }

    private boolean holdingAllowedItem() {
        if (!weaponsOnly.getValue() || isHoldingWeapon()) return true;
        ItemStack held = mc.thePlayer.getHeldItem();
        return allowTools.getValue() && held != null && held.getItem() instanceof ItemTool;
    }

    private static boolean isHoldingWeapon() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        if (held.hasTagCompound()) {
            NBTTagCompound tags = held.getTagCompound();
            if (tags.hasKey("ExtraAttributes")) {
                NBTTagCompound extraAttributes = tags.getCompoundTag("ExtraAttributes");
                if (extraAttributes.hasKey("UHCid")) {
                    long itemId = extraAttributes.getLong("UHCid");
                    if (itemId == 50006L || itemId == 50009L) return true;
                }
            }
            if (tags.hasKey("HideFlags") && held.getItem() instanceof ItemSpade spade && spade.getToolMaterial() == Item.ToolMaterial.EMERALD) return true;
            if (tags.hasKey("ItemModel") && "minecraft:mace".equals(tags.getString("ItemModel"))) return true;
        }
        if (held.getItem() instanceof ItemEnchantedBook) return false;
        return EnchantmentHelper.getEnchantments(held).containsKey(19) || held.getItem() instanceof ItemSword;
    }

    private static double distanceTo(Entity entity, Vec3 position) {
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        if (box.isVecInside(position)) return 0.0;
        double x = MathHelper.clamp_double(position.xCoord, box.minX, box.maxX) - position.xCoord;
        double y = MathHelper.clamp_double(position.yCoord, box.minY, box.maxY) - position.yCoord;
        double z = MathHelper.clamp_double(position.zCoord, box.minZ, box.maxZ) - position.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void resetMyau() {
        PacketDelayProcess.setDelayTicks(0);
        delayTicks = -1;
        remainingDelayMillis = 0L;
        delaying = false;
        previousPosition = null;
        currentPosition = null;
    }

    private void enableLag() {
        LagProcess.spoof(lagAmount, true, true, false, true);
        lagging = true;
    }

    private void disableLag() {
        LagProcess.dispatch();
        LagProcess.disable();
        lagging = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (lagging) {
            disableLag();
        }
        resetMyau();
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }
}
