package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.ClickEvent;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.ItemSlowdownEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.PostTickEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.Representation;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.client.MCFModule;
import rip.tempest.modules.impl.player.BedNukerModule;
import rip.tempest.modules.impl.player.ClutchModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.BadPacketsProcess;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Random;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Aura", category = ModuleCategory.COMBAT)
public final class AuraModule extends Module {
    public enum Mode {
        Single, Switch, Multi
    }

    public enum Sort {
        Distance, Health, HurtTime, FOV;

        @Override
        public String toString() {
            return this == HurtTime ? "Hurt Time" : name();
        }
    }

    public enum AutoBlock {
        None, Vanilla, Hypixel, Blink, Interact, Spoof, Swap, Legit, Fake, Rewound
    }

    public enum Rotations {
        None, Legit, Silent, LockView;

        @Override
        public String toString() {
            return this == LockView ? "Lock View" : name();
        }
    }

    public enum MoveFix {
        None, Silent, Strict
    }

    public enum ShowTarget {
        None, Default, HUD, Box, Ring
    }

    private static AuraModule instance;

    private static final Random RANDOM = new Random();

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Single);
    private final ModeProperty<Sort> sort = new ModeProperty<>("Sort", Sort.Distance);
    private final ModeProperty<AutoBlock> autoBlock = new ModeProperty<>("Auto Block", AutoBlock.Legit);
    private final Property<Boolean> autoBlockRequirePress = new Property<>("Auto Block Require Press", true, () -> autoBlock.getValue() != AutoBlock.None);
    private final Property<Boolean> autoBlockNoSlow = new Property<>("Auto Block No Slow", false, () -> autoBlock.getValue() != AutoBlock.None);
    private final NumberProperty autoBlockHold = new NumberProperty("Auto Block Hold", 1.5,
            () -> autoBlock.getValue() != AutoBlock.None && autoBlock.getValue() != AutoBlock.Fake && autoBlock.getValue() != AutoBlock.Rewound, 1, 20, 0.1);
    private final NumberProperty autoBlockDelay = new NumberProperty("Auto Block Delay", 0,
            () -> autoBlock.getValue() != AutoBlock.None && autoBlock.getValue() != AutoBlock.Vanilla && autoBlock.getValue() != AutoBlock.Fake && autoBlock.getValue() != AutoBlock.Rewound, 0, 20, 0.1);
    private final NumberProperty autoBlockHurtTime = new NumberProperty("Auto Block Hurt Time", 6,
            () -> autoBlock.getValue() != AutoBlock.None && autoBlock.getValue() != AutoBlock.Fake && autoBlock.getValue() != AutoBlock.Rewound, 0, 10, 1);
    private final NumberProperty autoBlockRange = new NumberProperty("Auto Block Range", 4, () -> autoBlock.getValue() != AutoBlock.None, 3, 8, 0.1);
    private final NumberProperty swingRange = new NumberProperty("Swing Range", 4, 3, 6, 0.1);
    private final NumberProperty attackRange = new NumberProperty("Attack Range", 3, 3, 6, 0.1);
    private final NumberProperty fov = new NumberProperty("FOV", 360, 30, 360, 1);
    private final NumberProperty minAps = new NumberProperty("Min APS", 14, 1, 20, 1);
    private final NumberProperty maxAps = new NumberProperty("Max APS", 14, 1, 20, 1);
    private final NumberProperty switchDelay = new NumberProperty("Switch Delay", 150, 0, 1000, 1, Representation.MILLISECONDS);

    private final NumberProperty multiTargets = new NumberProperty("Multi Targets", 3, () -> mode.getValue() == Mode.Multi, 1, 10, 1);
    private final NumberProperty multiFov = new NumberProperty("Multi FOV", 75, () -> mode.getValue() == Mode.Multi, 1, 180, 1);
    private final NumberProperty multiRange = new NumberProperty("Multi Range", 4.2, () -> mode.getValue() == Mode.Multi, 1, 7, 0.1);
    private final Property<Boolean> multiHurtCheck = new Property<>("Multi Hurt Check", true, () -> mode.getValue() == Mode.Multi);

    private final Property<Boolean> multiLookPackets = new Property<>("Multi Look Packets", true, () -> mode.getValue() == Mode.Multi);

    private final Property<Boolean> multiPingDelay = new Property<>("Multi Ping Delay", true, () -> mode.getValue() == Mode.Multi);
    private final ModeProperty<Rotations> rotations = new ModeProperty<>("Rotations", Rotations.Silent);
    private final ModeProperty<MoveFix> moveFix = new ModeProperty<>("Move Fix", MoveFix.Silent);
    private final NumberProperty smoothing = new NumberProperty("Smoothing", 0, 0, 100, 1, Representation.PERCENTAGE);
    private final NumberProperty angleStep = new NumberProperty("Angle Step", 90, 30, 180, 1);
    private final Property<Boolean> throughWalls = new Property<>("Through Walls", true);
    private final Property<Boolean> requirePress = new Property<>("Require Press", false);
    private final Property<Boolean> allowMining = new Property<>("Allow Mining", true);
    private final Property<Boolean> weaponsOnly = new Property<>("Weapons Only", true);
    private final Property<Boolean> allowTools = new Property<>("Allow Tools", false, weaponsOnly::getValue);
    private final Property<Boolean> inventoryCheck = new Property<>("Inventory Check", true);
    private final Property<Boolean> botCheck = new Property<>("Bot Check", true);
    private final Property<Boolean> players = new Property<>("Players", true);
    private final Property<Boolean> bosses = new Property<>("Bosses", false);
    private final Property<Boolean> mobs = new Property<>("Mobs", false);
    private final Property<Boolean> animals = new Property<>("Animals", false);
    private final Property<Boolean> golems = new Property<>("Golems", false);
    private final Property<Boolean> silverfish = new Property<>("Silverfish", false);
    private final Property<Boolean> teams = new Property<>("Teams", true);
    private final ModeProperty<ShowTarget> showTarget = new ModeProperty<>("Show Target", ShowTarget.None);

    private final Timer targetSwitchTimer = new Timer();
    private TargetInfo currentTarget;

    private final ArrayList<TargetInfo> sortedCandidates = new ArrayList<>();

    private float multiAimYaw = Float.NaN;

    private int moonCpsStage;
    private int moonNextCps = 1;
    private boolean hasTargetInAutoBlockRange;
    private int targetIndex;
    private boolean attackedThisUpdate;
    private boolean serverBlocking;
    private boolean autoBlockActive;
    private boolean renderBlocking;
    private boolean bufferRestartPending;
    private long attackCooldownMillis;
    private long blockHoldMillis;
    private long blockReleaseDelayMillis;
    private int autoBlockPhase;

    private boolean attackSent;

    private int remainingHurtTicks;
    private boolean healthDecreasePending;

    private boolean bufferEnabled;
    private boolean flushing;
    private final Deque<Packet<?>> queuedPackets = new ArrayDeque<>();

    private float lockLastPartialTicks = Float.NaN, lockYawDelta = Float.NaN, lockPitchDelta = Float.NaN;

    public AuraModule() {
        instance = this;

        swingRange.addValueChangeListener((old, value) -> {
            if (swingRange.getValue() < attackRange.getValue()) attackRange.setValue(swingRange.getValue());
        });
        attackRange.addValueChangeListener((old, value) -> {
            if (attackRange.getValue() > swingRange.getValue()) swingRange.setValue(attackRange.getValue());
        });
        minAps.addValueChangeListener((old, value) -> {
            if (minAps.getValue() > maxAps.getValue()) maxAps.setValue(minAps.getValue());
        });
        maxAps.addValueChangeListener((old, value) -> {
            if (minAps.getValue() > maxAps.getValue()) minAps.setValue(maxAps.getValue());
        });
    }

    private static boolean on() {
        return instance != null && instance.isEnabled() && mc.thePlayer != null;
    }

    public static boolean holdsMining() {
        return on() && (instance.autoBlockActive || instance.fightingInSwingRange());
    }

    public static boolean holdsBlock() {
        return on() && instance.autoBlockActive;
    }

    public static boolean rendersBlock() {
        return on() && instance.renderBlocking && holdingSword();
    }

    public static boolean noSlows() {
        return on() && instance.autoBlockActive && instance.autoBlockNoSlow.getValue();
    }

    public static boolean autoBlocks() {
        return on() && instance.autoBlock.getValue() != AutoBlock.None;
    }

    public static double attackRange() {
        return instance != null ? instance.attackRange.getValue() : 3.0D;
    }

    public static double swingRange() {
        return instance != null ? instance.swingRange.getValue() : 4.0D;
    }

    public static MovementFix movementFix() {
        if (instance == null) return MovementFix.NORMAL;
        return switch (instance.moveFix.getValue()) {
            case Silent -> MovementFix.NORMAL;
            case Strict -> MovementFix.TRADITIONAL;
            default -> MovementFix.OFF;
        };
    }

    public static EntityLivingBase target() {
        if (!on() || instance.currentTarget == null || !instance.isCombatInputAllowed()) return null;
        EntityLivingBase entity = instance.currentTarget.entity;
        return mc.theWorld.loadedEntityList.contains(entity) ? entity : null;
    }

    public static List<EntityLivingBase> multiTargets() {
        EntityLivingBase main = target();
        if (main == null || instance.mode.getValue() != Mode.Multi) return Collections.emptyList();
        List<EntityLivingBase> others = instance.moonMultiTargets(false);
        others.remove(main);
        return others;
    }

    private boolean moonTargetable(Entity entity) {
        return entity instanceof EntityLivingBase living && isValidTargetEntity(living)
                && mc.thePlayer.getDistanceToEntity(living) <= multiRange.getValue();
    }

    private static float[] moonRotations(Entity entity) {
        double differenceX = entity.posX - mc.thePlayer.posX;
        double differenceY = entity.posY + entity.height - mc.thePlayer.posY + mc.thePlayer.height;
        double differenceZ = entity.posZ - mc.thePlayer.posZ;
        float rotationYaw = (float) (Math.atan2(differenceZ, differenceX) * 180.0D / Math.PI) - 90.0F;
        float rotationPitch = (float) (Math.atan2(differenceY, mc.thePlayer.getDistanceToEntity(entity)) * 180.0D / Math.PI);
        float finishedYaw = mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(rotationYaw - mc.thePlayer.rotationYaw);
        float finishedPitch = mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(rotationPitch - mc.thePlayer.rotationPitch);
        return new float[]{finishedYaw, -finishedPitch};
    }

    private boolean moonWithinFov(Entity entity, float yaw) {
        float neededYaw = moonRotations(entity)[0];
        float currentYaw = yaw % 360.0F;
        float yawDifference = neededYaw - currentYaw;
        if (yawDifference > 180.0F) {
            yawDifference = -(360.0F - neededYaw + currentYaw);
        } else if (yawDifference < -180.0F) {
            yawDifference = 360.0F - currentYaw + neededYaw;
        }
        float fov = multiFov.getValue().floatValue();
        return yawDifference < fov && yawDifference > -fov;
    }

    private EntityLivingBase moonMostCrowded() {
        List<EntityLivingBase> targetable = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (moonTargetable(entity)) targetable.add((EntityLivingBase) entity);
        }
        EntityLivingBase best = null;
        int bestCount = -1;
        for (EntityLivingBase entity : targetable) {
            float yaw = moonRotations(entity)[0];
            int close = 0;
            for (EntityLivingBase other : targetable) {
                if (moonWithinFov(other, yaw)) close++;
            }
            if (close > bestCount) {
                bestCount = close;
                best = entity;
            }
        }
        return best;
    }

    private List<EntityLivingBase> moonMultiTargets(boolean hurtCheck) {
        List<EntityLivingBase> entities = new ArrayList<>();
        float yaw = Float.isNaN(multiAimYaw) ? mc.thePlayer.rotationYaw : multiAimYaw;
        int max = multiTargets.getValue().intValue();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entities.size() >= max) break;
            if (!moonTargetable(entity)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (hurtCheck && living.hurtResistantTime > 15) continue;
            if (moonWithinFov(living, yaw)) entities.add(living);
        }
        return entities;
    }

    private static int moonPingDelay() {
        long ping = mc.getCurrentServerData() == null ? 0L : Math.min(50L, Math.max(mc.getCurrentServerData().pingToServer, 110L));
        return Math.round(ping / 10.0F);
    }

    private int moonNewCps() {
        double range = 20.0D / randomLong(minAps.getValue().longValue(), maxAps.getValue().longValue());
        if (mc.thePlayer.ticksExisted % 3 != 0) range += Math.round(randomFloat(-1.15F, 1.15F));
        if (mc.thePlayer.ticksExisted % 45 == 0) range += randomLong(1, 3);
        return (int) Math.round(Math.max(range, 1.0D));
    }

    private void moonAttack(EntityLivingBase entity) {
        moonNextCps = moonNewCps();
        moonCpsStage = 0;
        if (holdingSword() && autoBlock.getValue() != AutoBlock.None) {
            mc.playerController.syncCurrentPlayItem();

            if (isBlockingSword()) releaseBlocking();
        }
        mc.thePlayer.swingItem();
        attackSent = false;
        mc.playerController.attackEntity(mc.thePlayer, entity);
        if (attackSent) attackedThisUpdate = true;
    }

    private boolean moonMulti(boolean canAttack) {
        EntityLivingBase target = currentTarget.entity;
        float[] rotation = moonRotations(target);
        Rotations rotationMode = rotations.getValue();
        if (rotationMode != Rotations.None) {
            MovementFix fix = switch (moveFix.getValue()) {
                case None -> MovementFix.OFF;
                case Silent -> MovementFix.NORMAL;
                case Strict -> MovementFix.TRADITIONAL;
            };
            if (rotationMode == Rotations.LockView) {
                fix = MovementFix.TRADITIONAL;
                requestLockView(rotation[0], rotation[1]);
            }
            RotationProcess.setRotations(new Vector2f(rotation[0], rotation[1]), 10, fix);
        }
        multiAimYaw = rotation[0];
        if (!canAttack || sentBlockAction()) return false;

        int pingDelay = moonPingDelay();
        List<EntityLivingBase> targets = moonMultiTargets(multiHurtCheck.getValue());
        int extraWait = multiPingDelay.getValue() ? (targets.size() - 1) * pingDelay : 0;
        if (moonCpsStage < moonNextCps + extraWait) return false;

        moonAttack(target);
        boolean looked = false;
        float lookYaw = 0, lookPitch = 0;
        for (EntityLivingBase other : targets) {
            if (other == target) continue;
            if (multiHurtCheck.getValue() && other.hurtResistantTime > 9 + pingDelay) continue;
            if (multiLookPackets.getValue()) {
                float[] look = moonRotations(other);
                PacketUtils.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(look[0], look[1], mc.thePlayer.onGround));
                looked = true;
                lookYaw = look[0];
                lookPitch = look[1];
            }
            moonAttack(other);
        }

        if (looked) mc.thePlayer.setPreviousRotation(lookYaw, lookPitch);
        return true;
    }

    private long nextAttackDelayMillis() {
        return 1000L / randomLong(minAps.getValue().longValue(), maxAps.getValue().longValue());
    }

    private long blockHoldDurationMillis() {
        return (long) (autoBlockHold.getValue().floatValue() * 50.0F);
    }

    private long blockReleaseDurationMillis() {
        return (long) (autoBlockDelay.getValue().floatValue() * 50.0F);
    }

    private static boolean sentBlockAction() {
        return BadPacketsProcess.bad(false, false, false, true, false);
    }

    private boolean tryAttackTarget(float yaw, float pitch) {
        if (sentBlockAction()) return false;
        if (isBlockingSword() && autoBlock.getValue() != AutoBlock.Vanilla && autoBlock.getValue() != AutoBlock.Rewound) return false;
        if (attackCooldownMillis > 0L) return false;

        attackCooldownMillis += nextAttackDelayMillis();
        mc.thePlayer.swingItem();

        boolean canAttack;
        if (rotations.getValue() == Rotations.None) {
            canAttack = isWithinAttackRange(currentTarget.distance);
        } else {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            if (throughWalls.getValue()) {
                canAttack = intersectBoundsFrom(currentTarget.bounds, eyes, yaw, pitch, attackRange.getValue()) != null;
            } else {
                canAttack = !isBoundsRayObstructed(currentTarget.bounds, eyes, yaw, pitch, attackRange.getValue());
            }
        }
        if (!canAttack) return false;

        attackSent = false;
        mc.playerController.attackEntity(mc.thePlayer, currentTarget.entity);
        if (!attackSent) return false;

        attackedThisUpdate = true;
        return true;
    }

    private void startBlockingHeldItem() {
        mc.playerController.syncCurrentPlayItem();
        startBlockingWithStack(mc.thePlayer.getHeldItem());
    }

    private void startBlockingWithStack(ItemStack stack) {
        if (stack == null) return;
        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(stack));
        mc.thePlayer.setItemInUse(stack, stack.getMaxItemUseDuration());
        serverBlocking = true;
    }

    private void rewoundBlock() {
        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        serverBlocking = true;
    }

    private void releaseBlocking() {
        PacketUtils.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        serverBlocking = false;
    }

    private void interactWithTargetAndBlock(float yaw, float pitch) {
        if (currentTarget == null) return;
        MovingObjectPosition hit = intersectBoundsFrom(currentTarget.bounds, mc.thePlayer.getPositionEyes(1.0F), yaw, pitch, 8.0);
        if (hit == null) return;

        mc.playerController.syncCurrentPlayItem();
        PacketUtils.sendPacket(new C02PacketUseEntity(currentTarget.entity,
                new Vec3(hit.hitVec.xCoord - currentTarget.x, hit.hitVec.yCoord - currentTarget.y, hit.hitVec.zCoord - currentTarget.z)));
        PacketUtils.sendPacket(new C02PacketUseEntity(currentTarget.entity, C02PacketUseEntity.Action.INTERACT));
        startBlockingWithStack(mc.thePlayer.getHeldItem());
    }

    private boolean canRunCombat() {
        if (currentTarget == null) return false;
        if (inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
        if (weaponsOnly.getValue() && !holdingWeapon() && !(allowTools.getValue() && holdingTool())) return false;
        if ((holdingConsumable() || holdingBow()) && useItemKeyDown()) return false;

        if (AutoPotModule.isPotting()) return false;
        if (BedNukerModule.holdsKillAura()) return false;
        if (Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled()) return false;
        if (ClutchModule.isClutching()) return false;

        boolean attackKeyDown = attackKeyDown();
        if (allowMining.getValue() && attackKeyDown && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return false;
        return !requirePress.getValue() || attackKeyDown;
    }

    private boolean canAutoBlock() {
        if (!holdingSword()) return false;
        AutoBlock block = autoBlock.getValue();
        if (block == AutoBlock.None) return true;
        if (block == AutoBlock.Fake) return hasTargetInAutoBlockRange;
        return !autoBlockRequirePress.getValue() && hasTargetInAutoBlockRange || useItemKeyDown();
    }

    private boolean isValidTargetEntity(EntityLivingBase candidate) {
        Entity view = mc.getRenderViewEntity();
        if (candidate == mc.thePlayer || candidate == mc.thePlayer.ridingEntity) return false;
        if (view != null && (candidate == view || candidate == view.ridingEntity)) return false;
        return passesEntityCategoryFilters(candidate);
    }

    private boolean passesEntityCategoryFilters(EntityLivingBase candidate) {
        if (candidate.deathTime > 0) return false;

        if (candidate instanceof EntityOtherPlayerMP player) {
            if (!players.getValue()) return false;
            if (MCFModule.isExcluded(player)) return false;
            if (teams.getValue() && isTeammate(player)) return false;
            return !botCheck.getValue() || !isBot(player);
        }
        if (candidate instanceof EntityDragon || candidate instanceof EntityWither) return bosses.getValue();
        if (candidate instanceof EntityMob || candidate instanceof EntitySlime) {
            if (!(candidate instanceof EntitySilverfish)) return mobs.getValue();
            return silverfish.getValue() && (!teams.getValue() || !hasTeamArmorStand(candidate));
        }
        if (candidate instanceof EntityAnimal || candidate instanceof EntityBat || candidate instanceof EntitySquid || candidate instanceof EntityVillager) {
            return animals.getValue();
        }
        if (candidate instanceof EntityIronGolem) {
            return golems.getValue() && (!teams.getValue() || !hasTeamArmorStand(candidate));
        }
        return false;
    }

    private boolean isWithinAnyCombatRange(double distance) {
        return isWithinAutoBlockRange(distance) || isWithinSwingRange(distance) || isWithinAttackRange(distance);
    }

    private boolean isWithinAutoBlockRange(double distance) {
        return autoBlock.getValue() != AutoBlock.None && distance <= autoBlockRange.getValue();
    }

    private boolean isWithinSwingRange(double distance) {
        return distance <= swingRange.getValue();
    }

    private boolean isWithinAttackRange(double distance) {
        return distance <= attackRange.getValue();
    }

    private boolean fightingInSwingRange() {
        return canRunCombat() && currentTarget != null && isWithinSwingRange(currentTarget.distance);
    }

    private int findSwapSlot(int excludedSlot) {
        for (int slot = 0; slot < 9; slot++) {
            if (slot != excludedSlot && mc.thePlayer.inventory.getStackInSlot(slot) == null) return slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (slot != excludedSlot && stack != null && !stack.hasDisplayName()) return slot;
        }
        return Math.floorMod(excludedSlot - 1, 9);
    }

    private int findAlternateSwordSlot(int excludedSlot) {
        for (int slot = 0; slot < 9; slot++) {
            if (slot == excludedSlot) continue;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemSword) return slot;
        }
        return -1;
    }

    private boolean isCombatInputAllowed() {
        if (inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
        if (mc.playerController.getIsHittingBlock()) return false;
        if (Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled()) return false;
        if (weaponsOnly.getValue() && !holdingWeapon() && !(allowTools.getValue() && holdingTool())) return false;
        return !requirePress.getValue() || attackKeyDown();
    }

    private boolean isBlockingSword() {
        return (mc.thePlayer.isUsingItem() || serverBlocking) && holdingSword();
    }

    private float[] calculateCombatRotation(float previousYaw, float previousPitch) {
        float yawDelta = MathHelper.wrapAngleTo180_float(currentTarget.yaw - previousYaw);
        float pitchDelta = currentTarget.pitch - previousPitch;
        if (Math.abs(yawDelta) < 0.5F) yawDelta = 0.0F;
        if (Math.abs(pitchDelta) < 0.5F) pitchDelta = 0.0F;

        yawDelta = clampAngleChange(yawDelta, angleStep.getValue().intValue() * randomFloat(0.75F, 1.0F));
        pitchDelta = clampAngleChange(pitchDelta, angleStep.getValue().intValue() * randomFloat(0.75F, 1.0F) * 0.5F);
        yawDelta = scaleAngleChange(yawDelta, smoothing.getValue().intValue() / 100.0F + randomFloat(-0.05F, 0.05F));
        pitchDelta = scaleAngleChange(pitchDelta, smoothing.getValue().intValue() / 100.0F + randomFloat(-0.05F, 0.05F));

        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float mouseStep = sensitivity * sensitivity * sensitivity * 8.0F;
        if (yawDelta == 0.0F && pitchDelta != 0.0F) yawDelta += mouseStep * 0.15F * (RANDOM.nextBoolean() ? 1.0F : -1.0F);
        if (yawDelta != 0.0F && pitchDelta == 0.0F) pitchDelta += mouseStep * 0.15F * (RANDOM.nextBoolean() ? 1.0F : -1.0F);

        float yaw = snapToMouseSensitivity(previousYaw + yawDelta, previousYaw);
        float pitch = snapToMouseSensitivity(previousPitch + pitchDelta, previousPitch);
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    @EventLink(value = Priorities.LOW)
    public final Listener<PreUpdateEvent> onRotationUpdate = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        moonCpsStage++;

        int controllerSlot = mc.playerController.getCurrentPlayerItem();
        boolean combatEligible = canRunCombat();
        boolean canAttackThisTick = combatEligible;
        boolean canBlockThisTick = combatEligible && canAutoBlock();
        AutoBlock block = autoBlock.getValue();
        boolean blockSuppressed = block != AutoBlock.None && block != AutoBlock.Fake && block != AutoBlock.Rewound && canBlockThisTick
                && (remainingHurtTicks > autoBlockHurtTime.getValue().intValue() || blockReleaseDelayMillis > 0L);

        if (attackCooldownMillis > 0L) attackCooldownMillis -= 50L;
        if (blockHoldMillis > 0L) blockHoldMillis -= 50L;
        if (blockReleaseDelayMillis > 0L) blockReleaseDelayMillis -= 50L;

        if (!canBlockThisTick || blockSuppressed) {
            if (autoBlockActive && isBlockingSword() && !sentBlockAction()) {
                if (block != AutoBlock.Interact && block != AutoBlock.Spoof) {
                    releaseBlocking();
                } else {
                    int swapSlot = findSwapSlot(controllerSlot);
                    PacketUtils.sendPacket(new C09PacketHeldItemChange(swapSlot));
                    mc.playerController.setCurrentPlayerItem(swapSlot);
                }
                canAttackThisTick = false;
            }
            setBuffering(false);
            autoBlockActive = false;
            renderBlocking = false;
            if (blockHoldMillis > 0L) blockHoldMillis = 0L;
            autoBlockPhase = 0;
        }

        if (!combatEligible) return;

        if (blockSuppressed) {
            canBlockThisTick = false;
            autoBlockActive = true;
            renderBlocking = true;
        }

        boolean shouldStartBlocking = false;
        if (canBlockThisTick) {
            switch (block) {
                case None -> {
                    setBuffering(false);
                    autoBlockActive = false;
                    renderBlocking = false;
                    if (useItemKeyDown() && !isBlockingSword() && !sentBlockAction()) shouldStartBlocking = true;
                }
                case Vanilla -> {
                    setBuffering(false);
                    autoBlockActive = true;
                    renderBlocking = false;
                    if (!isBlockingSword() && !sentBlockAction()) shouldStartBlocking = true;
                }
                case Hypixel -> {
                    autoBlockActive = true;
                    renderBlocking = true;
                    if (!sentBlockAction()) {
                        switch (autoBlockPhase) {
                            case 0 -> {
                                if (!isBlockingSword()) {
                                    shouldStartBlocking = true;
                                    blockHoldMillis += blockHoldDurationMillis();
                                }
                                bufferRestartPending = true;
                                autoBlockPhase = 1;
                            }
                            case 1 -> {
                                if (blockHoldMillis <= 0L) {
                                    if (isBlockingSword()) {
                                        releaseBlocking();
                                        canAttackThisTick = false;
                                    }
                                    blockReleaseDelayMillis += blockReleaseDurationMillis();
                                    autoBlockPhase = 0;
                                } else {
                                    bufferRestartPending = true;
                                }
                            }
                            default -> autoBlockPhase = 0;
                        }
                    }
                }
                case Blink -> {
                    autoBlockActive = true;
                    renderBlocking = true;
                    if (!sentBlockAction()) {
                        switch (autoBlockPhase) {
                            case 0 -> {
                                if (!isBlockingSword()) {
                                    shouldStartBlocking = true;
                                    blockHoldMillis += blockHoldDurationMillis();
                                }
                                bufferRestartPending = true;
                                autoBlockPhase = 1;
                            }
                            case 1 -> {
                                if (isBlockingSword()) {
                                    releaseBlocking();
                                    canAttackThisTick = false;
                                }
                                if (blockHoldMillis <= 0L) {
                                    blockReleaseDelayMillis += blockReleaseDurationMillis();
                                    autoBlockPhase = 0;
                                }
                            }
                            default -> autoBlockPhase = 0;
                        }
                    }
                }
                case Interact -> {
                    autoBlockActive = true;
                    renderBlocking = true;
                    if (mc.thePlayer.inventory.currentItem == controllerSlot && !sentBlockAction()) {
                        switch (autoBlockPhase) {
                            case 0 -> {
                                if (!isBlockingSword()) {
                                    shouldStartBlocking = true;
                                    blockHoldMillis += blockHoldDurationMillis();
                                }
                                bufferRestartPending = true;
                                autoBlockPhase = 1;
                            }
                            case 1 -> {
                                if (isBlockingSword()) {
                                    int swapSlot = findSwapSlot(controllerSlot);
                                    PacketUtils.sendPacket(new C09PacketHeldItemChange(swapSlot));
                                    mc.playerController.setCurrentPlayerItem(swapSlot);
                                    canAttackThisTick = false;
                                }
                                if (blockHoldMillis <= 0L) {
                                    blockReleaseDelayMillis += blockReleaseDurationMillis();
                                    autoBlockPhase = 0;
                                }
                            }
                            default -> autoBlockPhase = 0;
                        }
                    }
                }
                case Spoof -> {
                    setBuffering(false);
                    autoBlockActive = true;
                    renderBlocking = false;
                    if (!sentBlockAction() && mc.thePlayer.inventory.currentItem == controllerSlot
                            && (!isBlockingSword() || blockHoldMillis <= 0L)) {
                        int swapSlot = findSwapSlot(controllerSlot);
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(swapSlot));
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(controllerSlot));
                        shouldStartBlocking = true;
                        blockHoldMillis += blockHoldDurationMillis();
                    }
                }
                case Swap -> {
                    setBuffering(false);
                    autoBlockActive = true;
                    renderBlocking = true;
                    if (mc.thePlayer.inventory.currentItem == controllerSlot && !sentBlockAction()) {
                        switch (autoBlockPhase) {
                            case 0 -> {
                                if (!isBlockingSword()) {
                                    shouldStartBlocking = true;
                                    blockHoldMillis += blockHoldDurationMillis();
                                }
                                autoBlockPhase = 1;
                            }
                            case 1 -> {
                                if (blockHoldMillis <= 0L) {
                                    if (isBlockingSword()) {
                                        int swordSlot = findAlternateSwordSlot(controllerSlot);
                                        if (swordSlot != -1) {
                                            PacketUtils.sendPacket(new C09PacketHeldItemChange(swordSlot));
                                            mc.playerController.setCurrentPlayerItem(swordSlot);
                                            startBlockingWithStack(mc.thePlayer.inventory.getStackInSlot(swordSlot));
                                        } else {
                                            releaseBlocking();
                                        }
                                        canAttackThisTick = false;
                                    }
                                    blockReleaseDelayMillis += blockReleaseDurationMillis();
                                    autoBlockPhase = 0;
                                }
                            }
                            default -> autoBlockPhase = 0;
                        }
                    }
                }
                case Legit -> {
                    setBuffering(false);
                    autoBlockActive = true;
                    renderBlocking = true;
                    if (!sentBlockAction()) {
                        switch (autoBlockPhase) {
                            case 0 -> {
                                if (!isBlockingSword()) {
                                    shouldStartBlocking = true;
                                    blockHoldMillis += blockHoldDurationMillis();
                                }
                                autoBlockPhase = 1;
                            }
                            case 1 -> {
                                if (blockHoldMillis <= 0L) {
                                    if (isBlockingSword()) {
                                        releaseBlocking();
                                        canAttackThisTick = false;
                                    }
                                    blockReleaseDelayMillis += blockReleaseDurationMillis();
                                    autoBlockPhase = 0;
                                }
                            }
                            default -> autoBlockPhase = 0;
                        }
                    }
                }
                case Rewound -> {
                    setBuffering(false);
                    autoBlockActive = true;
                    renderBlocking = true;
                }
                case Fake -> {
                    setBuffering(false);
                    autoBlockActive = false;
                    renderBlocking = true;
                    if (useItemKeyDown() && !isBlockingSword() && !sentBlockAction()) shouldStartBlocking = true;
                }
            }
        }

        float yaw = mc.thePlayer.rotationYaw, pitch = mc.thePlayer.rotationPitch;
        if (RotationProcess.isActive() && RotationProcess.rotations != null) {
            yaw = RotationProcess.rotations.x;
            pitch = RotationProcess.rotations.y;
        }

        boolean attacked = false;
        if (mode.getValue() == Mode.Multi) {
            attacked = moonMulti(canAttackThisTick);
            if (attacked && canBlockThisTick && block == AutoBlock.Rewound) rewoundBlock();
            if (RotationProcess.isActive() && RotationProcess.rotations != null) {
                yaw = RotationProcess.rotations.x;
                pitch = RotationProcess.rotations.y;
            }

            if (shouldStartBlocking) startBlockingHeldItem();
            return;
        }
        if (currentTarget.hasUsableAim && isWithinSwingRange(currentTarget.distance)) {
            Rotations rotationMode = rotations.getValue();
            if (rotationMode == Rotations.Silent || rotationMode == Rotations.LockView) {
                Vector2f previous = mc.thePlayer.getPreviousRotation();
                float[] rotation = calculateCombatRotation(previous.x, previous.y);

                MovementFix fix = switch (moveFix.getValue()) {
                    case None -> MovementFix.OFF;
                    case Silent -> MovementFix.NORMAL;
                    case Strict -> MovementFix.TRADITIONAL;
                };

                if (rotationMode == Rotations.LockView) {
                    fix = MovementFix.TRADITIONAL;
                    requestLockView(rotation[0], rotation[1]);
                }
                RotationProcess.setRotations(new Vector2f(rotation[0], rotation[1]), 10, fix);

                yaw = RotationProcess.rotations.x;
                pitch = RotationProcess.rotations.y;
            }

            if (canAttackThisTick) attacked = tryAttackTarget(yaw, pitch);
        }

        if (attacked && canBlockThisTick && block == AutoBlock.Rewound) rewoundBlock();

        if (shouldStartBlocking) {
            if (attacked) {
                interactWithTargetAndBlock(yaw, pitch);
            } else {
                startBlockingHeldItem();
            }
        }
    };

    @EventLink(value = Priorities.LOW)
    public final Listener<MotionEvent> onPostPlayerUpdate = event -> {
        if (event.isPre() || !bufferRestartPending) return;
        bufferRestartPending = false;
        setBuffering(false);
        setBuffering(true);
    };

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<TickEvent> onEarlyTick = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        applyLockView(1.0F);
        lockLastPartialTicks = lockYawDelta = lockPitchDelta = Float.NaN;

        if (remainingHurtTicks > 0) remainingHurtTicks--;
        if (healthDecreasePending) {
            healthDecreasePending = false;
            remainingHurtTicks = mc.thePlayer.hurtTime;
        }
    };

    @EventLink
    public final Listener<TickEvent> onPreTick = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        setSuffix(mode.getValue().name());

        if (mode.getValue() == Mode.Multi) {
            sortedCandidates.clear();
            EntityLivingBase crowded = moonMostCrowded();
            hasTargetInAutoBlockRange = collectTargetCandidates().stream().anyMatch(c -> isWithinAutoBlockRange(c.distance));
            currentTarget = crowded == null ? null : targetInfo(crowded, mc.thePlayer.getPositionEyes(1.0F));
            return;
        }

        ArrayList<TargetInfo> candidates = collectTargetCandidates();

        candidates.removeIf(c -> rip.tempest.modules.impl.visuals.PitModule.belowHuntedBounty(c.entity));
        sortedCandidates.clear();
        if (candidates.isEmpty()) {
            currentTarget = null;
            hasTargetInAutoBlockRange = false;
            return;
        }

        hasTargetInAutoBlockRange = candidates.stream().anyMatch(c -> isWithinAutoBlockRange(c.distance));
        if (candidates.stream().anyMatch(c -> c.hasUsableAim)) candidates.removeIf(c -> !c.hasUsableAim);
        if (candidates.stream().anyMatch(c -> isWithinSwingRange(c.distance))) candidates.removeIf(c -> !isWithinSwingRange(c.distance));
        if (candidates.stream().anyMatch(c -> isWithinAttackRange(c.distance))) candidates.removeIf(c -> !isWithinAttackRange(c.distance));

        if (candidates.isEmpty()) {
            currentTarget = null;
            return;
        }

        if (currentTarget != null && !targetSwitchTimer.hasTimeElapsed(switchDelay.getValue().intValue())) {
            Optional<TargetInfo> existing = candidates.stream().filter(c -> c.entity == currentTarget.entity).findFirst();
            if (existing.isPresent()) {
                currentTarget = existing.get();
                candidates.sort(this::compareTargetCandidates);
                sortedCandidates.clear();
                sortedCandidates.addAll(candidates);
                return;
            }
        }

        candidates.sort(this::compareTargetCandidates);
        sortedCandidates.clear();
        sortedCandidates.addAll(candidates);
        if (mode.getValue() == Mode.Switch && attackedThisUpdate) {
            attackedThisUpdate = false;
            targetIndex++;
        }
        if (mode.getValue() != Mode.Switch || targetIndex >= candidates.size()) targetIndex = 0;

        currentTarget = candidates.get(targetIndex);
        targetSwitchTimer.reset();
    };

    private ArrayList<TargetInfo> collectTargetCandidates() {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        ArrayList<TargetInfo> candidates = new ArrayList<>();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase living) || !isValidTargetEntity(living)) continue;

            double border = living.getCollisionBorderSize();
            AxisAlignedBB bounds = living.getEntityBoundingBox().expand(border, border, border);
            double distance = distanceToBoundsFrom(bounds, eyes);
            if (!isWithinAnyCombatRange(distance)) continue;

            float angularDistance = boundsAimError(bounds, eyes);
            if (angularDistance > fov.getValue().intValue()) continue;

            candidates.add(targetInfo(living, eyes));
        }
        return candidates;
    }

    private TargetInfo targetInfo(EntityLivingBase living, Vec3 eyes) {
        double border = living.getCollisionBorderSize();
        AxisAlignedBB bounds = living.getEntityBoundingBox().expand(border, border, border);
        double distance = distanceToBoundsFrom(bounds, eyes);
        float angularDistance = boundsAimError(bounds, eyes);

        float[] aim = aimAtBounds(bounds);
        float yaw = aim[0], pitch = aim[1];
        boolean usableAim = true;
        if (!throughWalls.getValue() && isBoundsRayObstructed(bounds, eyes, yaw, pitch, 8.0)) {
            if (isBoundsRayObstructed(bounds, eyes, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 8.0)) {
                usableAim = false;
            } else {
                yaw = mc.thePlayer.rotationYaw;
                pitch = mc.thePlayer.rotationPitch;
            }
        }

        return new TargetInfo(living, bounds, living.posX, living.posY, living.posZ, yaw, pitch, distance, angularDistance, usableAim);
    }

    @EventLink
    public final Listener<PostTickEvent> onPostTick = event -> {
        if (mc.thePlayer == null) return;
        if (isBlockingSword() && !mc.thePlayer.isBlocking()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held != null) mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
        }
        if (mc.thePlayer.isDead) setBuffering(false);
    };

    @EventLink(value = Priorities.LOW)
    public final Listener<PacketSendEvent> onAttackSent = event -> {
        if (!event.isCancelled() && event.getPacket() instanceof C02PacketUseEntity use && use.getAction() == C02PacketUseEntity.Action.ATTACK) {
            attackSent = true;
        }
    };

    @EventLink(value = Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (event.isCancelled() || mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();

        if (packet instanceof C07PacketPlayerDigging digging) {
            if (digging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) serverBlocking = false;
        } else if (packet instanceof C09PacketHeldItemChange) {
            serverBlocking = false;
            if (autoBlockActive) mc.thePlayer.stopUsingItem();
        }

        if (packet instanceof C00Handshake || packet instanceof C00PacketLoginStart || packet instanceof C00PacketServerQuery
                || packet instanceof C01PacketPing || packet instanceof C01PacketEncryptionResponse) {
            setBuffering(false);
            return;
        }

        if (!flushing && buffer(packet)) event.setCancelled();
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onPacketReceive = event -> {
        if (event.isCancelled() || mc.thePlayer == null) return;

        if (event.getPacket() instanceof S06PacketUpdateHealth health) {
            if (health.getHealth() < mc.thePlayer.getHealth()) healthDecreasePending = true;
        } else if (event.getPacket() instanceof S1CPacketEntityMetadata metadata && metadata.getEntityId() == mc.thePlayer.getEntityId()
                && metadata.func_149376_c() != null) {
            boolean dropped = false;
            for (DataWatcher.WatchableObject watched : metadata.func_149376_c()) {
                if (watched.getDataValueId() != 6) continue;
                float health = (Float) watched.getObject();
                if (!dropped && health < mc.thePlayer.getHealth()) {
                    healthDecreasePending = true;
                    dropped = true;
                }
            }
        }
    };

    @EventLink
    public final Listener<Render3DEvent> onRenderWorld = event -> {
        if (mc.thePlayer == null) return;
        applyLockView(mc.timer.renderPartialTicks);

        if (showTarget.getValue() == ShowTarget.None || currentTarget == null
                || !mc.theWorld.loadedEntityList.contains(currentTarget.entity) || !isCombatInputAllowed()) return;

        for (EntityLivingBase entity : multiTargets()) showTarget(entity);
        showTarget(currentTarget.entity);
    };

    private void showTarget(EntityLivingBase entity) {
        if (showTarget.getValue() == ShowTarget.Box || showTarget.getValue() == ShowTarget.Ring) {
            Color client = ColorProcess.getColor();
            float red = client.getRed() / 255.0F, green = client.getGreen() / 255.0F, blue = client.getBlue() / 255.0F;
            if (showTarget.getValue() == ShowTarget.Box) {
                rip.tempest.utils.render.TargetMarks.renderTargetBox(entity, red, green, blue, 1.0F);
            } else {
                rip.tempest.utils.render.TargetMarks.renderTargetRing(entity, red, green, blue);
            }
            return;
        }

        Color color = Color.WHITE;
        if (showTarget.getValue() == ShowTarget.Default) {
            color = entity.hurtTime > 0 ? new Color(16733525) : new Color(5635925);
        } else if (showTarget.getValue() == ShowTarget.HUD) {
            color = ColorProcess.getColor();
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        fillEntityBounds(entity, color.getRed(), color.getGreen(), color.getBlue(), 63, 0.1);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @EventLink
    public final Listener<ClickEvent> onLeftClick = event -> {
        if (autoBlockActive || fightingInSwingRange()) event.setCancelled();
    };

    @EventLink
    public final Listener<RightClickEvent> onRightClick = event -> {
        if (autoBlockActive || fightingInSwingRange()) event.setCancelled();
    };

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<ItemSlowdownEvent> onItemSlowdown = event -> {
        if (autoBlockActive && autoBlockNoSlow.getValue()) event.setCancelled();
    };

    @Override
    public void onEnable() {
        currentTarget = null;
        sortedCandidates.clear();
        multiAimYaw = Float.NaN;
        hasTargetInAutoBlockRange = false;
        targetIndex = 0;
        attackedThisUpdate = false;
        attackCooldownMillis = 0L;
        blockHoldMillis = 0L;
        blockReleaseDelayMillis = 0L;
        autoBlockPhase = 0;
    }

    @Override
    public void onDisable() {
        setBuffering(false);
        if (serverBlocking && mc.thePlayer != null) mc.thePlayer.stopUsingItem();

        currentTarget = null;
        sortedCandidates.clear();
        multiAimYaw = Float.NaN;
        hasTargetInAutoBlockRange = false;
        bufferRestartPending = false;
        serverBlocking = false;
        autoBlockActive = false;
        renderBlocking = false;
        autoBlockPhase = 0;
        blockHoldMillis = 0L;
        blockReleaseDelayMillis = 0L;
        lockLastPartialTicks = lockYawDelta = lockPitchDelta = Float.NaN;
    }

    private int compareTargetCandidates(TargetInfo first, TargetInfo second) {
        if (rip.tempest.modules.impl.visuals.PitModule.huntsBounties()) {
            int bounty = Integer.compare(rip.tempest.modules.impl.visuals.PitModule.bountyOf(second.entity),
                    rip.tempest.modules.impl.visuals.PitModule.bountyOf(first.entity));
            if (bounty != 0) return bounty;
        }
        int comparison = switch (sort.getValue()) {
            case Health -> Float.compare(armorAdjustedHealth(first.entity), armorAdjustedHealth(second.entity));
            case HurtTime -> Integer.compare(first.entity.hurtResistantTime, second.entity.hurtResistantTime);
            case FOV -> Float.compare(first.angularDistance, second.angularDistance);
            default -> Double.compare(first.distance, second.distance);
        };
        return comparison != 0 ? comparison : Double.compare(first.distance, second.distance);
    }

    private boolean buffer(Packet<?> packet) {
        if (!bufferEnabled) return false;
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) return false;
        if (queuedPackets.isEmpty() && packet instanceof C0FPacketConfirmTransaction) return false;
        queuedPackets.offer(packet);
        return true;
    }

    private void setBuffering(boolean enabled) {
        if (enabled) {
            bufferEnabled = true;
            return;
        }
        bufferEnabled = false;
        if (mc.getNetHandler() == null) {
            queuedPackets.clear();
            return;
        }
        flushing = true;
        while (!queuedPackets.isEmpty()) PacketUtils.sendPacketNoEvent(queuedPackets.poll());
        flushing = false;
    }

    private void requestLockView(float yaw, float pitch) {
        lockYawDelta = MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        lockPitchDelta = pitch - mc.thePlayer.rotationPitch;
        lockLastPartialTicks = 0.0F;
    }

    private void applyLockView(float partialTicks) {
        if (mc.thePlayer == null || Float.isNaN(lockYawDelta) || Float.isNaN(lockPitchDelta) || Float.isNaN(lockLastPartialTicks)) return;

        float partialDelta = partialTicks - lockLastPartialTicks;
        float yawStep = lockYawDelta * partialDelta;
        if (yawStep != 0.0F) {
            mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
            mc.thePlayer.rotationYaw += yawStep;
        }
        float pitchStep = lockPitchDelta * partialDelta;
        if (pitchStep != 0.0F) {
            mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + pitchStep, -90.0F, 90.0F);
        }
        lockLastPartialTicks = partialTicks;
    }

    private static float clampAngleChange(float change, float maximumChange) {
        maximumChange = MathHelper.clamp_float(maximumChange, 0.0F, 180.0F);
        if (change > maximumChange) return maximumChange;
        return Math.max(change, -maximumChange);
    }

    private static float scaleAngleChange(float change, float factor) {
        return change * (0.5F + 0.5F * (1.0F - MathHelper.clamp_float(factor, 0.0F, 1.0F)));
    }

    private static float snapToMouseSensitivity(float targetAngle, float referenceAngle) {
        float change = MathHelper.wrapAngleTo180_float(targetAngle - referenceAngle);
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float increment = sensitivity * sensitivity * sensitivity * 8.0F;
        double roundedChange = (float) Math.round(change / (increment * 0.15)) * increment * 0.15;
        return (float) (referenceAngle + roundedChange);
    }

    private static float[] aimAtBounds(AxisAlignedBB bounds) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 center = new Vec3((bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0, (bounds.minZ + bounds.maxZ) / 2.0);
        Vec3 closest = closestPoint(eyes, bounds);
        double deltaX = center.xCoord + (closest.xCoord - center.xCoord) * 0.875 - eyes.xCoord;
        double deltaY = closest.yCoord - eyes.yCoord;
        double deltaZ = center.zCoord + (closest.zCoord - center.zCoord) * 0.875 - eyes.zCoord;

        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(deltaY, horizontal) * 180.0 / Math.PI);
        pitch += 10.0F * MathHelper.clamp_float((float) ((eyes.yCoord - center.yCoord) / (center.yCoord - bounds.minY)), -0.5F, 0.5F);
        return new float[]{yaw, pitch};
    }

    private static Vec3 closestPoint(Vec3 position, AxisAlignedBB bounds) {
        return new Vec3(
                Math.min(Math.max(position.xCoord, bounds.minX), bounds.maxX),
                Math.min(Math.max(position.yCoord, bounds.minY), bounds.maxY),
                Math.min(Math.max(position.zCoord, bounds.minZ), bounds.maxZ));
    }

    private static double distanceToBoundsFrom(AxisAlignedBB bounds, Vec3 position) {
        if (bounds.isVecInside(position)) return 0.0;
        Vec3 closest = closestPoint(position, bounds);
        double x = closest.xCoord - position.xCoord, y = closest.yCoord - position.yCoord, z = closest.zCoord - position.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static float boundsAimError(AxisAlignedBB bounds, Vec3 position) {
        if (bounds.isVecInside(position)) return 0.0F;
        double traceDistance = 30.0 + bounds.maxX - bounds.minX + bounds.maxZ - bounds.minZ + bounds.maxY - bounds.minY;
        if (intersectBoundsFrom(bounds, position, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, traceDistance) != null) return 0.0F;

        float[] rotation = aimAtBounds(bounds);
        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(rotation[0] - mc.thePlayer.rotationYaw));
        float pitchError = Math.abs(rotation[1] - mc.thePlayer.rotationPitch);
        return Math.max(yawError * 2.0F, pitchError * 2.0F);
    }

    private static Vec3 lookVector(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private static MovingObjectPosition intersectBoundsFrom(AxisAlignedBB bounds, Vec3 position, float yaw, float pitch, double distance) {
        Vec3 direction = lookVector(pitch, yaw);
        Vec3 end = position.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        return bounds.calculateIntercept(position, end);
    }

    private static boolean isBoundsRayObstructed(AxisAlignedBB bounds, Vec3 position, float yaw, float pitch, double distance) {
        if (bounds.isVecInside(position)) return false;
        MovingObjectPosition boxHit = intersectBoundsFrom(bounds, position, yaw, pitch, distance);
        if (boxHit == null) return true;

        Vec3 direction = lookVector(pitch, yaw);
        Vec3 end = position.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(position, end);
        return blockHit != null && position.distanceTo(boxHit.hitVec) > position.distanceTo(blockHit.hitVec);
    }

    private static void fillEntityBounds(Entity entity, int red, int green, int blue, int alpha, double expansion) {
        float partialTicks = mc.timer.renderPartialTicks;
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB b = entity.getEntityBoundingBox()
                .expand(expansion, expansion, expansion)
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ)
                .offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer r = tessellator.getWorldRenderer();
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[][] faces = {
                {b.minX, b.minY, b.minZ}, {b.minX, b.minY, b.maxZ}, {b.maxX, b.minY, b.maxZ}, {b.maxX, b.minY, b.minZ},
                {b.minX, b.maxY, b.minZ}, {b.minX, b.maxY, b.maxZ}, {b.maxX, b.maxY, b.maxZ}, {b.maxX, b.maxY, b.minZ},
                {b.minX, b.minY, b.minZ}, {b.minX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.minY, b.minZ},
                {b.minX, b.minY, b.maxZ}, {b.minX, b.maxY, b.maxZ}, {b.maxX, b.maxY, b.maxZ}, {b.maxX, b.minY, b.maxZ},
                {b.minX, b.minY, b.minZ}, {b.minX, b.maxY, b.minZ}, {b.minX, b.maxY, b.maxZ}, {b.minX, b.minY, b.maxZ},
                {b.maxX, b.minY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.maxZ}, {b.maxX, b.minY, b.maxZ}
        };
        for (double[] v : faces) r.pos(v[0], v[1], v[2]).color(red, green, blue, alpha).endVertex();
        tessellator.draw();
    }

    private static boolean holdingWeapon() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;

        if (held.hasTagCompound()) {
            NBTTagCompound tags = held.getTagCompound();
            if (tags.hasKey("ExtraAttributes")) {
                NBTTagCompound extra = tags.getCompoundTag("ExtraAttributes");
                if (extra.hasKey("UHCid")) {
                    long itemId = extra.getLong("UHCid");
                    if (itemId == 50006L || itemId == 50009L) return true;
                }
            }
            if (tags.hasKey("HideFlags") && held.getItem() instanceof ItemSpade spade && spade.getToolMaterial() == Item.ToolMaterial.EMERALD) {
                return true;
            }
            if (tags.hasKey("ItemModel") && "minecraft:mace".equals(tags.getString("ItemModel"))) return true;
        }

        if (held.getItem() instanceof ItemEnchantedBook) return false;

        return EnchantmentHelper.getEnchantments(held).containsKey(19) || held.getItem() instanceof ItemSword;
    }

    private static boolean holdingSword() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    private static boolean holdingTool() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemTool;
    }

    private static boolean holdingConsumable() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        if (ItemPotion.isSplash(held.getMetadata())) return false;
        EnumAction action = held.getItemUseAction();
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }

    private static boolean holdingBow() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBow;
    }

    private static boolean attackKeyDown() {
        return mc.currentScreen == null && GameSettings.isKeyDown(mc.gameSettings.keyBindAttack);
    }

    private static boolean useItemKeyDown() {
        return mc.currentScreen == null && GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem);
    }

    private static float armorAdjustedHealth(EntityLivingBase entity) {
        return entity.getHealth() * (20.0F / entity.getTotalArmorValue());
    }

    private static boolean isBot(EntityPlayer player) {
        if (player == mc.thePlayer) return false;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getName());
        if (info == null) return true;
        if (!isHypixelScoreboard()) return false;
        if (player.getName().startsWith("§k")) return player.isInvisible();
        if (info.getResponseTime() < 1) return true;

        ScorePlayerTeam team = info.getPlayerTeam();
        if (team != null) {
            if (team.getColorSuffix().isEmpty() && team.getColorPrefix().equals("§c")) return true;
            if (!team.getColorPrefix().isEmpty()) return false;
        }
        return info.getGameProfile().getId().version() == 2;
    }

    private static boolean isTeammate(EntityPlayer player) {
        if (player == mc.thePlayer) return true;
        NetworkPlayerInfo local = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (local == null || local.getPlayerTeam() == null) return false;
        NetworkPlayerInfo other = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        return other != null && other.getPlayerTeam() != null
                && local.getPlayerTeam().getColorPrefix().equals(other.getPlayerTeam().getColorPrefix());
    }

    private static boolean hasTeamArmorStand(EntityLivingBase entity) {
        if (entity == mc.thePlayer) return true;
        NetworkPlayerInfo local = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (local == null) return false;
        ScorePlayerTeam team = local.getPlayerTeam();
        if (team == null || team.getColorPrefix().length() < 2) return false;
        Entity stand = mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        return stand != null && stand.getName().contains(team.getColorPrefix().substring(0, 2));
    }

    private static boolean isHypixelScoreboard() {
        if (mc.theWorld == null) return false;
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) return false;
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        Iterator<Score> it = scores.iterator();
        if (!it.hasNext()) return false;
        Score first = it.next();
        String line = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(first.getPlayerName()), first.getPlayerName());
        return line.equals("§ewww.hypixel.ne🎂§et") || line.equals("§ewww.hypixel.ne§g§et");
    }

    private static long randomLong(long minInclusive, long maxInclusive) {
        return (long) (RANDOM.nextDouble() * (maxInclusive + 1L - minInclusive) + minInclusive);
    }

    private static float randomFloat(float min, float max) {
        return RANDOM.nextFloat() * (max - min) + min;
    }

    private static final class TargetInfo {
        final EntityLivingBase entity;
        final AxisAlignedBB bounds;
        final double x, y, z;
        final float yaw, pitch;
        final double distance;
        final float angularDistance;
        final boolean hasUsableAim;

        TargetInfo(EntityLivingBase entity, AxisAlignedBB bounds, double x, double y, double z, float yaw, float pitch,
                   double distance, float angularDistance, boolean hasUsableAim) {
            this.entity = entity;
            this.bounds = bounds;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.angularDistance = angularDistance;
            this.hasUsableAim = hasUsableAim;
        }
    }
}
