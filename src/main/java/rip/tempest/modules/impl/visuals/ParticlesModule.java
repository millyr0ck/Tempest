package rip.tempest.modules.impl.visuals;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Particles", category = ModuleCategory.VISUALS)
public final class ParticlesModule extends Module {
    private final NumberProperty amount = new NumberProperty("Amount", 10, 1, 100, 1);

    private EntityLivingBase target;

    @EventLink
    public final Listener<AttackEvent> attackEventListener = event -> {
        if (event.target instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.target;
        }
    };

    @EventLink
    public final Listener<MotionEvent> motionEventListener = event -> {
        if (!event.isPre() || mc.thePlayer == null || mc.effectRenderer == null) return;

        setSuffix(amount.getValue().toString());

        if (target == null) {
            target = AuraModule.target();
        }

        if (target != null
                && target.hurtTime >= 9
                && mc.thePlayer.getDistance(target.posX, target.posY, target.posZ) < 10) {
            int count = amount.getValue().intValue();
            for (int i = 0; i < count; i++) {
                mc.effectRenderer.emitParticleAtEntity(target, EnumParticleTypes.CRIT_MAGIC);
            }

            target = null;
        }
    };

    @Override
    public void onDisable() {
        target = null;
        super.onDisable();
    }
}
