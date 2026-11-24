package rip.tempest.modules.impl.movement;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.StrafeEvent;
import rip.tempest.api.events.impl.player.TeleportEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.TargetSelectionProcess;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.BlockAir;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Long Jump", category = ModuleCategory.MOVEMENT)
public final class LongJumpModule extends Module {
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.Vanilla);
    private final NumberProperty height = new NumberProperty("Height", 0.5, () -> mode.getValue() == Mode.Vanilla, 0.1, 1, 0.01);
    private final NumberProperty speed = new NumberProperty("Speed", 1, () -> mode.getValue() == Mode.Vanilla, 0.1, 9.5, 0.1);
    private final NumberProperty groundSpeed = new NumberProperty("Ground Speed", 0.4, () -> mode.getValue() == Mode.NCP, 0.1, 3, 0.1);
    private final NumberProperty jumpSpeed = new NumberProperty("Jump Speed", 1.4, () -> mode.getValue() == Mode.NCP, 0, 3, 0.1);
    private final NumberProperty glide = new NumberProperty("Glide", 0, () -> mode.getValue() == Mode.NCP, 0, 3, 0.5);
    private final NumberProperty timer = new NumberProperty("Timer", 1, () -> mode.getValue() == Mode.NCP, 0.1, 10, 0.1);

    private final Property<Boolean> pearlToPlayer = new Property<>("To Closest Player", false, () -> mode.getValue() == Mode.Pearl);
    private final NumberProperty pearlSpeed = new NumberProperty("Pearl Speed", 3.0, () -> mode.getValue() == Mode.Pearl && !pearlToPlayer.getValue(), 0.5, 9.5, 0.1);

    private final NumberProperty pearlHeight = new NumberProperty("Pearl Height", 10, () -> mode.getValue() == Mode.Pearl && !pearlToPlayer.getValue(), 1, 30, 0.5);

    private final NumberProperty chaseSpeed = new NumberProperty("Chase Speed", 9.5, () -> mode.getValue() == Mode.Pearl && pearlToPlayer.getValue(), 1, 100, 0.5);

    private enum Mode {
        Vanilla("Vanilla"),
        OldIntaveBoat("Vanilla Intave Boat"),
        NCP("NCP"),
        DoubleJump("Double Jump"),

        Bow("Bow"),

        Pearl("Pearl");
        public final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private boolean reset;
    private double nSpeed;

    private boolean bowBoosted;
    private int bowBoostTicks;
    private int bowLastHurtTime;

    private enum PearlStage { SELECT, THROW, WAIT, LAUNCH, FLY }

    private static final C08PacketPlayerBlockPlacement PEARL_THROW =
            new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, null, 0.0F, 0.0F, 0.0F);

    private final Timer pearlTimer = new Timer();
    private PearlStage pearlStage = PearlStage.SELECT;
    private int pearlSlot = -1;
    private int pearlFlyTicks;
    private EntityPlayer pearlTarget;

    private int targetVoidTicks;

    @EventLink
    public final Listener<StrafeEvent> onStrafe = event -> {
        if (mode.getValue() == Mode.Vanilla) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.motionY = height.getValue().floatValue();
            }
            event.setSpeed(speed.getValue().floatValue());
        }
        if (mode.getValue() == Mode.NCP) {
            final double base = MovementUtils.getAllowedHorizontalDistance();

            if (MovementUtils.isMoving()) {
                switch (mc.thePlayer.offGroundTicks) {
                    case 0:
                        mc.thePlayer.motionY = 0.42f;
                        nSpeed = groundSpeed.getValue().doubleValue();
                        break;

                    case 1:
                        nSpeed = jumpSpeed.getValue().doubleValue();
                        break;

                    default:
                        nSpeed -= nSpeed / MovementUtils.BUNNY_FRICTION;
                        break;
                }

                mc.timer.timerSpeed = timer.getValue().floatValue();
                reset = false;
            } else if (!reset) {
                nSpeed = MovementUtils.getAllowedHorizontalDistance();
                mc.timer.timerSpeed = 1;
                reset = true;
            }

            if (mc.thePlayer.fallDistance > 0) {
                mc.thePlayer.motionY += glide.getValue().floatValue() / 100;
            }

            if (mc.thePlayer.isCollidedHorizontally) {
                nSpeed = MovementUtils.getAllowedHorizontalDistance();
            }

            event.setSpeed(Math.max(nSpeed, base), Math.random() / 2000);
        }
        if (mode.getValue() == Mode.Pearl) {
            switch (pearlStage) {
                case SELECT, THROW, WAIT -> {
                    event.setCancelled();
                    mc.thePlayer.motionX = 0;
                    mc.thePlayer.motionZ = 0;
                }
                case LAUNCH -> {
                    pearlFlyTicks = 0;
                    if (pearlToPlayer.getValue()) {
                        pearlTarget = closestPlayer();
                        targetVoidTicks = 0;
                        if (pearlTarget == null) {
                            event.setCancelled();
                            NotificationManager.post(NotificationType.WARNING, "Long Jump", "No player to fly to");
                            toggle();
                            return;
                        }
                        pearlStage = PearlStage.FLY;
                        chase(event);
                        return;
                    }
                    nSpeed = pearlSpeed.getValue().doubleValue();
                    mc.thePlayer.motionY = launchMotion(pearlHeight.getValue().doubleValue());
                    pearlFlyTicks = 0;
                    pearlStage = PearlStage.FLY;
                    pearlStrafe(event);
                }
                case FLY -> {
                    if (pearlTarget != null) {
                        chase(event);
                        return;
                    }
                    nSpeed -= nSpeed / MovementUtils.BUNNY_FRICTION;
                    if (mc.thePlayer.isCollidedHorizontally) {
                        nSpeed = MovementUtils.getAllowedHorizontalDistance();
                    }
                    pearlStrafe(event);
                }
            }
        }
        if (mode.getValue() == Mode.DoubleJump) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.jump();
                mc.thePlayer.jump();
                this.toggle();
            }
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
        if (!e.isPre()) return;
        if (mode.getValue() == Mode.Pearl) {
            pearlMotion(e);
        }
        if (mode.getValue() == Mode.Bow) {
            int ht = mc.thePlayer.hurtTime;
            if (!bowBoosted && ht > bowLastHurtTime && ht >= 8) {
                bowBoosted = true;
                bowBoostTicks = 0;
                mc.thePlayer.motionY = 0.42F;
                mc.timer.timerSpeed = 1.3F;
            }
            bowLastHurtTime = ht;
            if (bowBoosted) {
                MovementUtils.setSpeed(e, 2.5);
                bowBoostTicks++;
                if (bowBoostTicks > 20) {
                    bowBoosted = false;
                    mc.timer.timerSpeed = 1.0F;
                }
            }
        }
        if (mode.getValue() == Mode.OldIntaveBoat) {
            if (mc.thePlayer.isRiding()) {
                PacketUtils.sendSilentPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
                PacketUtils.sendSilentPacket(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX - Math.sin(Math.toRadians((double) mc.thePlayer.rotationYaw)) * 3.0D, mc.thePlayer.posY + 2.0D, mc.thePlayer.posZ + Math.cos(Math.toRadians((double) mc.thePlayer.rotationYaw)) * 3.0D, false));
                this.toggle();
            }
        }
    };

    @EventLink
    public final Listener<TeleportEvent> onTeleport = event -> {
        nSpeed = 0;
        if (mode.getValue() != Mode.Pearl) return;
        if (pearlStage == PearlStage.WAIT) {
            pearlStage = PearlStage.LAUNCH;
        } else if (pearlStage == PearlStage.FLY) {
            toggle();
        }
    };

    private void pearlMotion(MotionEvent e) {
        switch (pearlStage) {
            case SELECT -> {
                int slot = findPearl();
                if (slot == -1) {
                    NotificationManager.post(NotificationType.WARNING, "Long Jump", "You need an ender pearl on your hotbar");
                    toggle();
                    return;
                }
                pearlSlot = slot;
                if (mc.thePlayer.inventory.currentItem != slot) {
                    PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
                }

                e.setPitch(90.0F);
                pearlStage = PearlStage.THROW;
            }
            case THROW -> {
                PacketUtils.sendPacketNoEvent(PEARL_THROW);
                restorePearlSlot();
                pearlTimer.reset();
                pearlStage = PearlStage.WAIT;
            }
            case WAIT -> {
                if (pearlTimer.hasTimeElapsed(3000)) {
                    NotificationManager.post(NotificationType.WARNING, "Long Jump", "The pearl never landed");
                    toggle();
                }
            }
            case FLY -> {
                pearlFlyTicks++;
                boolean landed = pearlTarget == null && mc.thePlayer.onGround && pearlFlyTicks > 2;
                if (landed || pearlFlyTicks > 200) {
                    toggle();
                }
            }
            default -> {
            }
        }
    }

    private void chase(StrafeEvent event) {
        event.setCancelled();
        if (pearlTarget.isDead || !mc.theWorld.playerEntities.contains(pearlTarget)) {
            NotificationManager.post(NotificationType.WARNING, "Long Jump", "Lost the target");
            MovementUtils.stop();
            toggle();
            return;
        }

        boolean dropping = !pearlTarget.onGround && pearlTarget.posY < pearlTarget.lastTickPosY && overVoid(pearlTarget);
        targetVoidTicks = dropping ? targetVoidTicks + 1 : 0;
        if (targetVoidTicks >= 8) {
            NotificationManager.post(NotificationType.WARNING, "Long Jump", "Target is falling into the void");
            MovementUtils.stop();
            mc.thePlayer.motionY = 0;
            toggle();
            return;
        }

        double dx = pearlTarget.posX - mc.thePlayer.posX;
        double dy = pearlTarget.posY - mc.thePlayer.posY;
        double dz = pearlTarget.posZ - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 1.0) {
            MovementUtils.stop();
            mc.thePlayer.motionY = 0;
            toggle();
            return;
        }

        double step = chaseSpeed.getValue().doubleValue();

        double scale = distance <= step ? 1.0 : step / distance;
        mc.thePlayer.motionX = dx * scale;
        mc.thePlayer.motionY = dy * scale;
        mc.thePlayer.motionZ = dz * scale;
        if (mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.motionY = Math.max(mc.thePlayer.motionY, Math.min(step, 2.0));
        }
        mc.thePlayer.fallDistance = 0;
    }

    private static EntityPlayer closestPlayer() {
        EntityPlayer closest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : TargetSelectionProcess.targetsWithin(Float.MAX_VALUE)) {
            if (!(entity instanceof EntityPlayer)) continue;

            if (!entity.onGround && overVoid(entity)) continue;
            double distance = mc.thePlayer.getDistanceSqToEntity(entity);
            if (distance < best) {
                best = distance;
                closest = (EntityPlayer) entity;
            }
        }
        return closest;
    }

    private static boolean overVoid(Entity entity) {
        for (int y = (int) Math.floor(entity.posY) - 1; y >= 0; y--) {
            if (!(mc.theWorld.getBlockState(new BlockPos(entity.posX, y, entity.posZ)).getBlock() instanceof BlockAir)) return false;
        }
        return true;
    }

    private void pearlStrafe(StrafeEvent event) {
        event.setCancelled();
        MovementUtils.setSpeed(nSpeed, mc.thePlayer.rotationYaw, 0, 1);
    }

    private void restorePearlSlot() {
        if (pearlSlot != -1 && pearlSlot != mc.thePlayer.inventory.currentItem) {
            PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        }
        pearlSlot = -1;
    }

    private static int findPearl() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemEnderPearl) return i;
        }
        return -1;
    }

    private static double launchMotion(double height) {
        double lo = 0, hi = 5;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (apex(mid) < height) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    private static double apex(double motionY) {
        double y = 0;
        while (motionY > 0) {
            y += motionY;
            motionY = (motionY - 0.08) * 0.98;
        }
        return y;
    }

    @Override
    public void onEnable() {
        bowBoosted = false;
        bowBoostTicks = 0;
        bowLastHurtTime = 0;
        pearlStage = PearlStage.SELECT;
        pearlSlot = -1;
        pearlFlyTicks = 0;
        pearlTarget = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mode.getValue() == Mode.Vanilla || mode.getValue() == Mode.NCP) {
            MovementUtils.stop();
            mc.timer.timerSpeed = 1f;
        }
        if (mode.getValue() == Mode.Bow) {
            mc.timer.timerSpeed = 1f;
        }
        if (mode.getValue() == Mode.Pearl && mc.thePlayer != null) {
            if (pearlStage == PearlStage.THROW) restorePearlSlot();
            if (pearlStage == PearlStage.FLY) MovementUtils.stop();
        }
        pearlTarget = null;
        bowBoosted = false;
        nSpeed = 0;
        super.onDisable();
    }
}
