package rip.tempest.modules.impl.movement.flight;

import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.mc.MovementUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

public final class DamageFly extends SubMode {
    private Module module;
    private boolean flying;
    private int lastHurtTime;

    public DamageFly(Module module) {
        this.module = module;
    }

    @Override
    protected void onEnable() {
        flying = false;
        lastHurtTime = 0;
    }

    @Override
    protected void onDisable() {
        if (inGame()) mc.timer.timerSpeed = 1.0F;
    }

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<MovePlayerEvent> onMove = e -> {
        if (!inGame() || flying) return;

        e.setCancelled();
    };

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;

        int ht = mc.thePlayer.hurtTime;
        if (!flying && ht > lastHurtTime && ht >= 8) {
            flying = true;
        }
        lastHurtTime = ht;

        if (!flying) {
            mc.thePlayer.motionY = 0;
            return;
        }

        if (MovementUtils.isMoving()) {
            MovementUtils.setSpeed(e, 0.9);
        }
        mc.thePlayer.motionY = mc.gameSettings.keyBindJump.isKeyDown() ? 0.5
                : mc.gameSettings.keyBindSneak.isKeyDown() ? -0.5 : 0;
    };
}
