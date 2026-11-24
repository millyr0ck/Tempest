package rip.tempest.modules.impl.combat.velocity;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.SubMode;
import rip.tempest.modules.impl.client.AntiBotModule;
import rip.tempest.modules.impl.client.MCFModule;
import rip.tempest.utils.client.Timer;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.Vec3;

import static rip.tempest.utils.Util.mc;

public final class PacketVelocity extends SubMode {
    private NumberProperty horizontal, vertical;
    private Property<Boolean> alert, nearbyOnly;

    private Vec3 velocity;
    private final Timer ignore = new Timer(), hurtDelay = new Timer();
    private boolean checkDamage, projectileNearby;

    public PacketVelocity(NumberProperty horizontal, NumberProperty vertical, Property<Boolean> alert, Property<Boolean> nearbyOnly) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.alert = alert;
        this.nearbyOnly = nearbyOnly;
    }

    @Override
    protected void onEnable() {
        checkDamage = false;
        velocity = null;
    }

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (alert.getValue()) {
            if (hurtDelay.hasTimeElapsed(250) && mc.thePlayer.ticksExisted > 60) {
                if (checkDamage && mc.thePlayer.hurtTime == 0 && mc.thePlayer.capabilities.allowEdit && velocity != null) {
                    NotificationManager.post(NotificationType.WARNING, "\247cSuspicious Knockback", "You may have been KB checked!", 5000);
                    checkDamage = false;
                    mc.thePlayer.setVelocity(velocity.xCoord, velocity.yCoord, velocity.zCoord);
                    velocity = null;
                } else {
                    checkDamage = false;
                }
            } else if (checkDamage && mc.thePlayer.hurtTime > 0) {
                checkDamage = false;
            }
        }

        projectileNearby = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (entity instanceof IProjectile && mc.thePlayer.getDistanceToEntity(entity) <= 5) {
                projectileNearby = true;
                break;
            }
        }
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (e.getPacket() instanceof S45PacketTitle title && title.getType() == S45PacketTitle.Type.TITLE
                && title.getMessage() != null && title.getMessage().getUnformattedText().equals("YOU DIED")) {
            ignore.reset();
        }

        if (e.getPacket() instanceof S12PacketEntityVelocity packet) {
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;
            double x = packet.getMotionX() / 8000.0D, y = packet.getMotionY() / 8000.0D, z = packet.getMotionZ() / 8000.0D;

            if (nearbyOnly.getValue() && !enemyNearby() && !projectileNearby) {
                remember(x, y, z);
                return;
            }

            int v = vertical.getValue().intValue(), h = horizontal.getValue().intValue();
            if (v != 0 || h != 0) {
                packet.setMotionX((int) (packet.getMotionX() * (h / 100F)));
                packet.setMotionY((int) (packet.getMotionY() * (v / 100F)));
                packet.setMotionZ((int) (packet.getMotionZ() * (h / 100F)));
            } else {
                e.setCancelled();
            }
            remember(x, y, z);
        } else if (e.getPacket() instanceof S27PacketExplosion packet) {
            float x = packet.func_149149_c(), y = packet.func_149144_d(), z = packet.func_149147_e();

            if (nearbyOnly.getValue() && y > -0.5 && !enemyNearby() && !projectileNearby) {
                remember(x, y, z);
                return;
            }

            double v = vertical.getValue(), h = horizontal.getValue();
            if (v != 0 || h != 0) {
                packet.setMotion(x * (float) (h / 100F), y * (float) (v / 100F), z * (float) (h / 100F));
            } else {
                e.setCancelled();
            }

            remember(packet.func_149149_c(), packet.func_149144_d(), packet.func_149147_e());
        }
    };

    private void remember(double x, double y, double z) {
        if (ignore.hasTimeElapsed(500) && x != 0 && y != 0 && z != 0) {
            velocity = new Vec3(x, y, z);
            checkDamage = true;
            hurtDelay.reset();
        }
    }

    private static boolean enemyNearby() {
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (!(entity instanceof EntityPlayer) || entity instanceof EntityPlayerSP) continue;
            if (!AntiBotModule.botList.contains(entity) && !MCFModule.isExcluded(entity)
                    && mc.thePlayer.getDistanceToEntity(entity) <= 15) return true;
        }
        return false;
    }
}
