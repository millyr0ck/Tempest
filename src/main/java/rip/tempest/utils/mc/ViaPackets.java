package rip.tempest.utils.mc;

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.Iterator;

public final class ViaPackets {
    private static int sequence;

    private ViaPackets() {
    }

    public static ProtocolVersion target() {
        try {
            ViaLoadingBase via = ViaLoadingBase.getInstance();
            return via == null ? null : via.getTargetVersion();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean atLeast(ProtocolVersion version) {
        ProtocolVersion target = target();
        return target != null && target.newerThanOrEqualTo(version);
    }

    private static UserConnection connection() {
        try {
            Iterator<UserConnection> connections = Via.getManager().getConnectionManager().getConnections().iterator();
            return connections.hasNext() ? connections.next() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void useItemOn(BlockPos pos, EnumFacing face, float hitX, float hitY, float hitZ) {
        if (!atLeast(ProtocolVersion.v1_19)) return;
        UserConnection connection = connection();
        if (connection == null) return;
        try {
            PacketWrapper packet = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM_ON, connection);
            packet.write(Types.VAR_INT, 0);
            packet.write(Types.BLOCK_POSITION1_14, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
            packet.write(Types.VAR_INT, face.ordinal());
            packet.write(Types.FLOAT, hitX);
            packet.write(Types.FLOAT, hitY);
            packet.write(Types.FLOAT, hitZ);
            packet.write(Types.BOOLEAN, false);
            packet.write(Types.VAR_INT, ++sequence);
            packet.sendToServer(Protocol1_19To1_18_2.class);
        } catch (Throwable ignored) {
        }
    }
}
