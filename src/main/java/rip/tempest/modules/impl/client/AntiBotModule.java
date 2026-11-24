package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import com.mojang.authlib.GameProfile;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.EntityLivingBase;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Anti Bot", category = ModuleCategory.CLIENT)
public class AntiBotModule extends Module {
    public ModeProperty<Mode> modeProperty = new ModeProperty<>("Mode", Mode.TabList);

    private enum Mode {
        TabList("Tab List"), NPC("NPC");

        public final String name;

        Mode(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    public static final List<EntityLivingBase> botList = new ArrayList<>();

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        setSuffix(modeProperty.getValue().toString());

        botList.clear();

        switch (modeProperty.getValue()) {
            case NPC:
                mc.theWorld.playerEntities.forEach(player -> {
                    if (player != mc.thePlayer && !player.moved) {
                        botList.add(player);
                    }
                });
                break;
            case TabList:
                List<String> tabList = getTablist();
                mc.theWorld.playerEntities.forEach(player -> {
                    if (player != mc.thePlayer && !tabList.contains(player.getName())) {
                        botList.add(player);
                    }
                });
                break;
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        if (!mc.theWorld.playerEntities.isEmpty()) {
            mc.theWorld.playerEntities.forEach(player -> {
                botList.clear();
            });
        }
    };

    private static boolean impossibleName(String name) {
        return name == null || name.isEmpty() || name.length() > 16 || name.indexOf('§') >= 0 || name.chars().anyMatch(Character::isWhitespace);
    }

    public static boolean isHologram(net.minecraft.entity.Entity entity) {
        if (entity instanceof net.minecraft.entity.item.EntityArmorStand) return true;
        if (entity instanceof net.minecraft.entity.player.EntityPlayer player) {
            if (player == mc.thePlayer) return false;
            if (impossibleName(player.getName())) return true;

            return player.isInvisible() && mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(player.getUniqueID()) == null;
        }
        return entity.isInvisible() && (entity.hasCustomName() || entity.getAlwaysRenderNameTag());
    }

    private static @NonNull List<String> getTablist() {
        return Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap().parallelStream()
                .map(NetworkPlayerInfo::getGameProfile)
                .filter(profile -> profile.getId() != Minecraft.getMinecraft().thePlayer.getUniqueID())
                .map(GameProfile::getName)
                .collect(Collectors.toList());
    }

    @Override
    public void onDisable() {
        botList.clear();
        super.onDisable();
    }
}
