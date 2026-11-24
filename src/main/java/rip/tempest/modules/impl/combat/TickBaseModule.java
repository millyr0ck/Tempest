package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.TargetSelectionProcess;
import rip.tempest.utils.client.Logger;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Tick Base", category = ModuleCategory.COMBAT)
public final class TickBaseModule extends Module {
    private final NumberProperty lagRange = new NumberProperty("Range", 8, 1, 15, 0.1);
    public Property<Boolean> swingCheckProperty = new Property<>("Swing Check", true);
    private Mode MODE = Mode.NONE;
    private long time, balance;
    private double range, distance;
    Entity target;

    @EventLink
    public Listener<PreUpdateEvent> onPreUpdate = event -> {
        setSuffix(lagRange.getValue().toString());
        if (MODE.equals(Mode.REDUCING)) {
            return;
        }

        target = TargetSelectionProcess.getTarget();
        if (target == null) return;

        if (swingCheckProperty.getValue() && !mc.thePlayer.isSwingInProgress)
            return;

        distance = mc.thePlayer.getDistanceToEntity(target);
        double range = distance;

        if (range >= 1 && balance >= 50 && MODE.equals(Mode.BASING)) {
            balance -= 50;
            mc.timer.elapsedTicks += 1;
        } else {
            if (balance != 0) {
                Logger.chatPrint("Balance " + balance + " " + range);
            }
            balance = 0;
            MODE = Mode.NONE;
        }

        if ((
                range < lagRange.getValue().doubleValue() && this.range >=
                        lagRange.getValue().doubleValue()) && MODE.equals(Mode.NONE)) {
            MODE = Mode.REDUCING;
            time = System.currentTimeMillis();
            balance = 0;
        }

        this.range = range;
    };

    @EventLink
    public Listener<Render3DEvent> onRender3D = event -> {
        if (!MODE.equals(Mode.REDUCING) || target == null) return;

        if (distance <= 1 || System.currentTimeMillis() - time >= ((range / (mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 0.36 : 0.25)) * 25) + 25) {
            mc.timer.timerSpeed = 1;
            MODE = Mode.BASING;
            balance = System.currentTimeMillis() - time;
            return;
        }

        mc.timer.timerSpeed = 0;
    };
    enum Mode {REDUCING, BASING, NONE}
}
