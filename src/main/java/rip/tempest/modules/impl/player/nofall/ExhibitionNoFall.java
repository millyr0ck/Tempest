package rip.tempest.modules.impl.player.nofall;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.SubMode;
import rip.tempest.utils.client.Timer;
import rip.tempest.utils.mc.PacketUtils;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.BlockAir;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

public final class ExhibitionNoFall extends SubMode {
    private Property<Boolean> hypixel;
    private Property<Boolean> fastFall;

    private final Timer lagbackTimer = new Timer();
    private boolean lagbackPending = false;

    public ExhibitionNoFall(Property<Boolean> hypixel, Property<Boolean> fastFall) {
        this.hypixel = hypixel;
        this.fastFall = fastFall;
    }

    @Override
    protected void onEnable() {
        lagbackPending = false;
    }

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            lagbackPending = true;
            lagbackTimer.reset();
        }
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = e -> {
        if (!e.isPre() || !inGame()) return;
        if (lagbackPending && !lagbackTimer.hasTimeElapsed(1000)) return;
        lagbackPending = false;

        if (mc.thePlayer.fallDistance > 2.124f) {
            e.setOnGround(true);
        }

        if (fastFall.getValue() && !mc.thePlayer.onGround && mc.thePlayer.fallDistance > 0) {
            double dist = distToGround();
            if (dist > 0 && dist < 5.0) {
                mc.thePlayer.motionY = -Math.min(dist, 9.0);
            }
        }
    };

    @EventLink
    public final Listener<PacketSendEvent> onSend = e -> {
        if (!hypixel.getValue() || !ServerUtils.isOnHypixel()) return;

        if (e.getPacket() instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            C03PacketPlayer.C06PacketPlayerPosLook pkt = (C03PacketPlayer.C06PacketPlayerPosLook) e.getPacket();
            e.setCancelled();
            PacketUtils.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    pkt.getPositionX(), pkt.getPositionY(), pkt.getPositionZ(), pkt.isOnGround()));
        }

        if (e.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) e.getPacket();
            if (use.getAction() == C02PacketUseEntity.Action.ATTACK && mc.thePlayer.fallDistance > 0.5f) {
                e.setCancelled();
            }
        }
    };

    private static double distToGround() {
        double x = mc.thePlayer.posX, z = mc.thePlayer.posZ;
        for (int i = 1; i <= 20; i++) {
            BlockPos bp = new BlockPos(x, mc.thePlayer.posY - i, z);
            if (!(mc.theWorld.getBlockState(bp).getBlock() instanceof BlockAir)) return i;
        }
        return -1;
    }
}
