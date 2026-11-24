package rip.tempest.modules.impl.combat;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.util.MovingObjectPosition;

import java.util.concurrent.ThreadLocalRandom;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Clicker", category = ModuleCategory.COMBAT)
public final class AutoClickerModule extends Module {
    public static boolean clicking;

    private final Property<Boolean> left = new Property<>("Left Click", true);
    private final NumberProperty lminCPS = new NumberProperty("Left Min CPS", 10.0, () -> left.getValue(), 1.0, 20.0, 1.0);
    private final NumberProperty lmaxCPS = new NumberProperty("Left Max CPS", 12.0, () -> left.getValue(), 1.0, 20.0, 1.0);
    private final Property<Boolean> breakBlocks = new Property<>("Break Blocks", true, () -> left.getValue());

    private final Property<Boolean> right = new Property<>("Right Click", true);
    private final NumberProperty rminCPS = new NumberProperty("Right Min CPS", 10.0, () -> right.getValue(), 1.0, 20.0, 1.0);
    private final NumberProperty rmaxCPS = new NumberProperty("Right Max CPS", 12.0, () -> right.getValue(), 1.0, 20.0, 1.0);

    private long leftLastClick = 0;
    private long rightLastClick = 0;

    @EventLink
    public final Listener<PreUpdateEvent> preUpdateEventListener = e -> {
            handleRightClick();
            handleLeftClick();
    };

    private void handleRightClick() {
        if (right.getValue() && mc.gameSettings.keyBindUseItem.isKeyDown()) {
            long currentTime = System.currentTimeMillis();
            int minCPS = rminCPS.getValue().intValue();
            int maxCPS = rmaxCPS.getValue().intValue();
            int cps = ThreadLocalRandom.current().nextInt(minCPS, maxCPS + 1);
            long delay = 1000 / cps;

            if (currentTime - rightLastClick >= delay) {
                mc.rightClickMouse();
                rightLastClick = currentTime;
            }
        }
    }

    private void handleLeftClick() {
        if (left.getValue() && mc.gameSettings.keyBindAttack.isKeyDown()) {
            if (breakBlocks.getValue() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            int minCPS = lminCPS.getValue().intValue();
            int maxCPS = lmaxCPS.getValue().intValue();
            int cps = ThreadLocalRandom.current().nextInt(minCPS, maxCPS + 1);
            long delay = 1000 / cps;

            if (currentTime - leftLastClick >= delay) {
                clicking = true;
                mc.clickMouse();
                clicking = false;
                mc.leftClickCounter = 0;
                leftLastClick = currentTime;
            }
        }
    }

    @Override
    public void onEnable() {
        leftLastClick = 0;
        rightLastClick = 0;
    }
}
