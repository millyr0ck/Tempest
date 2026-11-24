package rip.tempest.modules.impl.movement.speed;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.player.MovePlayerEvent;
import rip.tempest.utils.mc.MovementUtils;
import rip.tempest.utils.mc.PacketUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.util.concurrent.ThreadLocalRandom;

import static rip.tempest.utils.Util.mc;

public final class WatchdogLowHopSpeed extends SpeedBase {
    @EventLink
    public final Listener<MovePlayerEvent> onMove = this::move;

    @EventLink
    public final Listener<MotionEvent> onMotion = this::update;

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = this::receive;

    @Override
    protected void update(MotionEvent e) {
        if (e.isPre() && inGame()) {
            offGroundHeight(e);
        }
        super.update(e);
    }

    @Override
    protected void move(MovePlayerEvent e) {
        if (!inGame()) return;

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            final boolean scaffold = scaffold();
            hopCycle(e, 0.4001F,
                    scaffold ? 1.399999976158142D : speedPotion() ? (jumpPotion() ? 1.925F : 2.14F) : 1.7999999523162842D,
                    scaffold ? 0.725D : speedPotion() ? (jumpPotion() ? 0.905F : 0.73F) : 0.69F,
                    159.9D);
            return;
        }

        if (!MovementUtils.isMoving()) {
            setSpeed(e, 0.0D);
            return;
        }

        stage++;
        if (mc.thePlayer.onGround) {
            if (ThreadLocalRandom.current().nextInt(4) > 2) {
                PacketUtils.sendPacketNoEvent(new C03PacketPlayer(true));
            }
            stage = 0;
        }

        switch (stage) {
            case 0 -> {
                e.setY(mc.thePlayer.motionY = jumpMotion(0.42F));
                moveSpeed *= 2.0D;
            }
            case 1 -> moveSpeed = lastDist - 0.8199999928474426D * (lastDist - baseSpeed());
            case 2 -> {
                moveSpeed = lastDist - lastDist / 90.0D;
                e.setY(-0.0784000015258789D);
            }
            case 3 -> moveSpeed = lastDist - lastDist / 50.0D;
            default -> moveSpeed = lastDist - lastDist / 5.0D;
        }

        setSpeed(e, moveSpeed);
    }
}
