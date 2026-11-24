package rip.tempest.modules.impl.client;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.notifications.NotificationManager;
import rip.tempest.api.notifications.NotificationType;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.visuals.NotificationsModule;
import rip.tempest.utils.client.Logger;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.Arrays;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Murder Detector", category = ModuleCategory.CLIENT)
public class MurderDetectorModule extends Module {
    private static final ArrayList<EntityPlayer> murderers = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>(Arrays.asList(
            Items.iron_sword,
            Items.stone_sword,
            Items.iron_shovel,
            Items.stick,
            Items.wooden_axe,
            Items.wooden_sword,
            Item.getItemFromBlock(Blocks.deadbush),
            Items.reeds,
            Items.stone_shovel,
            Items.blaze_rod,
            Items.diamond_shovel,
            Items.quartz,
            Items.pumpkin_pie,
            Items.golden_pickaxe,
            Items.leather,
            Items.name_tag,
            Items.coal,
            Items.flint,
            Items.bone,
            Items.golden_carrot,
            Items.cookie,
            Items.diamond_axe,
            Item.getItemFromBlock(Blocks.double_plant),
            Items.prismarine_shard,
            Items.cooked_beef,
            Items.netherbrick,
            Items.cooked_chicken,
            Items.record_blocks,
            Items.golden_hoe,
            Items.dye,
            Items.golden_sword,
            Items.diamond_sword,
            Items.diamond_hoe,
            Items.shears,
            Items.fish,
            Items.bread,
            Items.boat,
            Items.speckled_melon,
            Items.book,
            Item.getItemFromBlock(Blocks.sapling),
            Items.golden_axe,
            Items.diamond_pickaxe,
            Items.golden_shovel
    ));

    @EventLink
    public final Listener<PacketReceiveEvent> PacketReceiveEvent = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!player.getName().isEmpty() && player.getHeldItem() != null && (!murderers.contains(player) && items.contains(player.getHeldItem().getItem()))) {
                murderers.add(player);
                if (Tempest.INSTANCE.getModuleManager().getModule(NotificationsModule.class).isEnabled()) {
                    NotificationManager.post(NotificationType.WARNING, "Murder Detector", "Murderer " + player.getName() + " was detected!");
                } else {
                    Logger.chatPrint("Murderer " + player.getName() + " was detected!");
                }
            }
        }
    };

    @EventLink
    public final Listener<WorldLoadEvent> worldLoadEventListener = event -> {
        murderers.clear();
    };

    @Override
    public void onDisable() {
        murderers.clear();
        super.onDisable();
    }
}
