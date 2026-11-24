package rip.tempest.modules.impl.client;

import rip.tempest.api.events.impl.game.MiddleClickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Logger;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

import rip.tempest.Tempest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "MCF", category = ModuleCategory.CLIENT, description = "Middle Click to exclude players from targeting")
public class MCFModule extends Module {
    public static final List<Entity> excludedPlayers = new ArrayList<>();

    private static final Map<String, String> friends = new LinkedHashMap<>();
    private static final File FRIENDS_FILE = new File(Tempest.NAME, "friends.txt");

    static {
        loadFriends();
    }

    public static boolean isExcluded(Entity entity) {
        return excludedPlayers.contains(entity)
                || entity instanceof EntityPlayer && friends.containsKey(entity.getName().toLowerCase(Locale.ROOT));
    }

    public static boolean toggleFriend(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        boolean added = friends.remove(key) == null;
        if (added) friends.put(key, name);
        saveFriends();
        return added;
    }

    public static Collection<String> friendNames() {
        return Collections.unmodifiableCollection(friends.values());
    }

    private static void loadFriends() {
        if (!FRIENDS_FILE.isFile()) return;
        try {
            for (String line : Files.readAllLines(FRIENDS_FILE.toPath(), StandardCharsets.UTF_8)) {
                String name = line.trim();
                if (!name.isEmpty()) friends.put(name.toLowerCase(Locale.ROOT), name);
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveFriends() {
        try {
            File folder = FRIENDS_FILE.getParentFile();
            if (folder != null) folder.mkdirs();
            Files.write(FRIENDS_FILE.toPath(), friends.values(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    @EventLink
    public final Listener<MiddleClickEvent> onMiddleClick = event -> {
        if (mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
            mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            EntityPlayer clickedPlayer = (EntityPlayer) mc.objectMouseOver.entityHit;

            if (clickedPlayer != mc.thePlayer) {
                if (excludedPlayers.contains(clickedPlayer)) {
                    excludedPlayers.remove(clickedPlayer);
                    Logger.chatPrint("§a[MCF] §fRemoved §c" + clickedPlayer.getName() + "§f from exclusion list");
                } else {
                    excludedPlayers.add(clickedPlayer);
                    Logger.chatPrint("§a[MCF] §fAdded §c" + clickedPlayer.getName() + "§f to exclusion list");
                }
                event.setCancelled(true);
            }
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        excludedPlayers.clear();
    };

    @Override
    public void onDisable() {
        excludedPlayers.clear();
        super.onDisable();
    }
}
