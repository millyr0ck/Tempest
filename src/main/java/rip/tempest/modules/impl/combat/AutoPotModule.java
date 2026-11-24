package rip.tempest.modules.impl.combat;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.player.ClutchModule;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.misc.MovementFix;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockAir;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Pot", category = ModuleCategory.COMBAT)
public final class AutoPotModule extends Module {
    private final Property<Boolean> potions = new Property<>("Potions", true);

    private final Property<Boolean> heads = new Property<>("Heads", true);

    private final NumberProperty health = new NumberProperty("Health", 12, this::usesAny, 2, 20, 1);

    private final NumberProperty slot = new NumberProperty("Slot", 7, this::usesAny, 1, 9, 1);
    private final NumberProperty throwDelay = new NumberProperty("Throw Delay", 400, this::usesAny, 0, 1000, 50);

    private final NumberProperty rotationSpeed = new NumberProperty("Rotation Speed", 1.5, potions::getValue, 0.5, 10, 0.1);

    private final Property<Boolean> distanceCheck = new Property<>("Distance Check", true, potions::getValue);
    private final NumberProperty minDistance = new NumberProperty("Min Player Distance", 1.0,
            () -> potions.getValue() && distanceCheck.getValue(), 0.1, 5.0, 0.1);

    private static final float PITCH_TOLERANCE = 8f;

    private static final float SELECT_WITHIN = 30f;

    private static final int MAX_AIM_TICKS = 20;

    private static final int MAX_THROW_TICKS = 6;

    private static AutoPotModule instance;

    private final Timer timer = new Timer();

    private enum Stage {
        IDLE,

        REFILL,

        AIM,

        THROW,

        RETURN,

        DONE
    }

    private Stage stage = Stage.IDLE;
    private int potSlot = -1, previousSlot = -1;

    private boolean aimed;
    private float aimYaw, aimPitch;

    private int stageTicks;

    private int refillIndex = -1;
    private boolean refillAim, sprintKeyWasDown;

    private float sentPitch = Float.NaN;

    public AutoPotModule() {
        instance = this;
    }

    public static boolean isPotting() {
        return instance != null && instance.isEnabled() && instance.stage != Stage.IDLE;
    }

    @EventLink(Priorities.HIGH)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        setSuffix(Integer.toString(countPotions()));

        if (stage != Stage.IDLE) {
            mc.rightClickDelayTimer = Math.max(mc.rightClickDelayTimer, 2);
            stageTicks++;
            step();
            return;
        }

        if (mc.currentScreen != null || ClutchModule.isClutching()
                || Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class).isEnabled()) return;
        if (!timer.hasTimeElapsed(throwDelay.getValue().longValue())) return;

        if (potions.getValue() && tryPotion()) return;
        if (heads.getValue()) tryHead();
    };

    private void step() {
        switch (stage) {
            case REFILL -> {
                int index = refillIndex;
                ItemStack stack = index >= 9 && index < 45 ? mc.thePlayer.inventoryContainer.getSlot(index).getStack() : null;
                if (stack == null) {
                    restoreSprintKey();
                    finish(false);
                } else if (!mc.thePlayer.serverSprintState && !mc.thePlayer.isSneaking()) {
                    restoreSprintKey();
                    begin(toHotbar(index), refillAim);
                } else if (stageTicks > 3) {
                    restoreSprintKey();
                    finish(false);
                } else {
                    holdSprint();
                }
            }
            case AIM -> {
                turnDown();
                if (hasAim(SELECT_WITHIN)) {
                    select();
                } else if (stageTicks > MAX_AIM_TICKS) {
                    finish(false);
                }
            }
            case THROW -> {
                if (mc.thePlayer.inventory.currentItem != potSlot) {
                    finish(false);
                    return;
                }

                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                if (held == null || !(held.getItem() instanceof ItemPotion || held.getItem() instanceof ItemSkull)) {
                    finish(true);
                    return;
                }

                if (aimed) {
                    turnDown();

                    if (!hasAim(PITCH_TOLERANCE)) {
                        if (stageTicks > MAX_THROW_TICKS) finish(true);
                        return;
                    }
                }

                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
                timer.reset();
                setStage(Stage.RETURN);
            }
            case RETURN -> finish(true);
            case DONE -> {
                mc.playerController.syncCurrentPlayItem();
                setStage(Stage.IDLE);
                potSlot = previousSlot = -1;
            }
            default -> {
            }
        }
    }

    private void begin(int slotToUse, boolean aim) {
        previousSlot = mc.thePlayer.inventory.currentItem;
        potSlot = slotToUse;
        aimed = aim;
        mc.rightClickDelayTimer = Math.max(mc.rightClickDelayTimer, 2);
        if (!aim) {
            select();
            return;
        }

        aimYaw = RotationProcess.lastServerRotations != null ? RotationProcess.lastServerRotations.x : mc.thePlayer.rotationYaw;
        aimPitch = 87f + (float) Math.random() * 3f;
        setStage(Stage.AIM);
        turnDown();
    }

    private void select() {
        mc.thePlayer.inventory.currentItem = potSlot;
        setStage(Stage.THROW);
    }

    private void turnDown() {
        RotationProcess.setRotations(new Vector2f(aimYaw, aimPitch), rotationSpeed.getValue(), moveFix());
    }

    private boolean hasAim(float within) {
        return !Float.isNaN(sentPitch) && sentPitch >= aimPitch - within;
    }

    private static MovementFix moveFix() {
        return AuraModule.movementFix();
    }

    private void setStage(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    private void finish(boolean restore) {
        if (restore && previousSlot >= 0 && mc.thePlayer.inventory.currentItem == potSlot) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }

        timer.reset();
        setStage(Stage.DONE);
    }

    @EventLink(Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> onPacketSend = event -> {
        if (event.getPacket() instanceof C03PacketPlayer && ((C03PacketPlayer) event.getPacket()).getRotating()) {
            sentPitch = event.isCancelled() ? Float.NaN : ((C03PacketPlayer) event.getPacket()).getPitch();
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = event -> {
        if (stage == Stage.REFILL) restoreSprintKey();
        if (mc.thePlayer != null && (stage == Stage.THROW || stage == Stage.RETURN)) finish(true);
        reset();
    };

    private boolean tryPotion() {
        float healthTarget = health.getValue().floatValue();
        for (int index = 9; index < 45; index++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(index).getStack();
            if (!isBuffSplash(stack) || !worthUsing(stack, healthTarget)) continue;

            if (overVoid()) return false;
            if (distanceCheck.getValue() && playerWithin(minDistance.getValue())) return false;

            return useFrom(index, true);
        }
        return false;
    }

    private void tryHead() {
        if (mc.thePlayer.getHealth() > health.getValue()) return;
        for (int index = 9; index < 45; index++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(index).getStack();
            if (stack == null || !(stack.getItem() instanceof ItemSkull) || !stack.getDisplayName().contains("Golden")) continue;

            useFrom(index, false);
            return;
        }
    }

    private boolean useFrom(int index, boolean aim) {
        if (index >= 36) {
            begin(index - 36, aim);
            return true;
        }
        if (mc.thePlayer.isSneaking()) return false;
        if (mc.thePlayer.serverSprintState || mc.thePlayer.isSprinting()) {
            refillIndex = index;
            refillAim = aim;
            sprintKeyWasDown = mc.gameSettings.keyBindSprint.isKeyDown();
            holdSprint();
            mc.rightClickDelayTimer = Math.max(mc.rightClickDelayTimer, 2);
            setStage(Stage.REFILL);
            return true;
        }
        begin(toHotbar(index), aim);
        return true;
    }

    private static void holdSprint() {
        mc.gameSettings.keyBindSprint.setPressed(false);
        mc.thePlayer.setSprinting(false);
    }

    private void restoreSprintKey() {
        if (sprintKeyWasDown) mc.gameSettings.keyBindSprint.setPressed(true);
        sprintKeyWasDown = false;
        refillIndex = -1;
    }

    private int toHotbar(int index) {
        if (index >= 36) return index - 36;
        int hotbar = slot.getValue().intValue() - 1;
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, index, hotbar, 2, mc.thePlayer);
        return hotbar;
    }

    private boolean worthUsing(ItemStack stack, float healthTarget) {
        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack.getMetadata());
        if (effects == null) return false;
        float current = mc.thePlayer.getHealth();
        for (PotionEffect effect : effects) {
            if (!isStrongest(stack, effect)) continue;
            int id = effect.getPotionID();
            if (id == Potion.heal.id) {
                if (current < healthTarget) return true;
            } else if (id == Potion.regeneration.id) {
                if (current < healthTarget && beatsActive(effect)) return true;
            } else if (id == Potion.moveSpeed.id) {
                if (beatsActive(effect)) return true;
            }
        }
        return false;
    }

    private static boolean beatsActive(PotionEffect effect) {
        PotionEffect active = mc.thePlayer.getActivePotionEffect(effect.getPotionID());
        return active == null || active.getAmplifier() < effect.getAmplifier();
    }

    private static boolean isStrongest(ItemStack stack, PotionEffect wanted) {
        int best = -1;
        ItemStack bestStack = null;
        for (int index = 9; index < 45; index++) {
            ItemStack other = mc.thePlayer.inventoryContainer.getSlot(index).getStack();
            if (other == null || !(other.getItem() instanceof ItemPotion)) continue;
            List<PotionEffect> effects = ((ItemPotion) other.getItem()).getEffects(other.getMetadata());
            if (effects == null) continue;
            for (PotionEffect effect : effects) {
                if (effect.getPotionID() == wanted.getPotionID() && effect.getAmplifier() > best) {
                    best = effect.getAmplifier();
                    bestStack = other;
                }
            }
        }
        return bestStack == stack;
    }

    private static boolean isBuffSplash(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemPotion) || !ItemPotion.isSplash(stack.getMetadata())) return false;
        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        if (effects == null) return false;
        for (PotionEffect effect : effects) {
            if (Potion.potionTypes[effect.getPotionID()].isBadEffect()) return false;
        }
        return true;
    }

    private int countPotions() {
        int count = 0;
        for (int index = 9; index < 45; index++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(index).getStack();
            if (!isBuffSplash(stack)) continue;
            List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack.getMetadata());
            if (effects == null) continue;
            for (PotionEffect effect : effects) {
                int id = effect.getPotionID();
                if (id == Potion.heal.id || id == Potion.regeneration.id || id == Potion.moveSpeed.id) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean overVoid() {
        for (double y = mc.thePlayer.posY; y > 0.0; y--) {
            if (!(mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ)).getBlock() instanceof BlockAir)) {
                return false;
            }
        }
        return true;
    }

    private static boolean playerWithin(double distance) {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player instanceof EntityOtherPlayerMP && !player.isSpectator()
                    && player.getDistanceSq(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ) < distance * distance) {
                return true;
            }
        }
        return false;
    }

    private boolean usesAny() {
        return potions.getValue() || heads.getValue();
    }

    private void reset() {
        sentPitch = Float.NaN;
        stage = Stage.IDLE;
        potSlot = previousSlot = -1;
        stageTicks = 0;
    }

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (stage == Stage.REFILL) restoreSprintKey();
        if (stage == Stage.THROW || stage == Stage.RETURN) finish(true);
        reset();
        super.onDisable();
    }
}
