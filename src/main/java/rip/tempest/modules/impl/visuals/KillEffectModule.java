package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Kill Effect", category = ModuleCategory.VISUALS)
public final class KillEffectModule extends Module {
    private final Property<Boolean> lightning = new Property<>("Lightning", true);
    private final Property<Boolean> bloodExplosion = new Property<>("Blood Explosion", true);
    private final Property<Boolean> explosion = new Property<>("Explosion", true);

    private static final long LEFT_WORLD_WINDOW = 3000L;

    private static final long FORGET_AFTER = 15000L;

    private EntityLivingBase target;
    private long lastHit;

    @EventLink
    public final Listener<AttackEvent> attackEventListener = event -> {
        if (event.target instanceof EntityLivingBase && event.target != mc.thePlayer) {
            target = (EntityLivingBase) event.target;
            lastHit = System.currentTimeMillis();
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre() || target == null || mc.theWorld == null) return;

        long sinceHit = System.currentTimeMillis() - lastHit;
        boolean died = target.getHealth() <= 0 || target.isDead && target.deathTime > 0;
        boolean left = !mc.theWorld.loadedEntityList.contains(target) && sinceHit <= LEFT_WORLD_WINDOW;

        if (died || left) {
            play(target);
            target = null;
        } else if (sinceHit > FORGET_AFTER || !mc.theWorld.loadedEntityList.contains(target)) {
            target = null;
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> target = null;

    private void play(EntityLivingBase dead) {
        if (lightning.getValue()) {
            EntityLightningBolt bolt = new EntityLightningBolt(mc.theWorld, dead.posX, dead.posY, dead.posZ);
            mc.theWorld.addEntityToWorld(-1 - (int) (Math.random() * 100000.0), bolt);
            sound("ambient.weather.thunder", 10000.0F, 0.95F);
            sound("random.explode", 2.0F, 0.57F);
        }

        if (explosion.getValue()) {
            for (int i = 0; i <= 8; i++) {
                mc.effectRenderer.emitParticleAtEntity(dead, EnumParticleTypes.FLAME);
            }
            sound("item.fireCharge.use", 1.0F, 1.0F);
        }

        if (bloodExplosion.getValue()) {
            double bottom = dead.posY, top = dead.posY + dead.height + 0.4, step = 0.4;
            int redstone = Block.getStateId(Blocks.redstone_block.getDefaultState());
            for (int i = 0; i < 100; i++) {
                for (double y = bottom; y <= top; y += step) {
                    mc.theWorld.spawnParticle(EnumParticleTypes.BLOCK_CRACK, dead.posX, y, dead.posZ, 0.0, 0.0, 0.0, redstone);
                }
            }
            for (double y = bottom; y <= top; y += step) {
                sound("dig.stone", 1.0F, 1.0F);
            }
        }
    }

    private static void sound(String name, float volume, float pitch) {
        mc.theWorld.playSound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, name, volume, pitch, false);
    }

    @Override
    public void onDisable() {
        target = null;
        super.onDisable();
    }
}
