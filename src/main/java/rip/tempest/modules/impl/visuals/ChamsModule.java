package rip.tempest.modules.impl.visuals;

import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Chams", category = ModuleCategory.VISUALS)
public final class ChamsModule extends Module {

    private static final Property<Boolean> tileEntities = new Property<>("Tile Entities", false);

    public static boolean shouldRender(Entity entity) {
        return entity instanceof EntityPlayer && (!(entity instanceof EntityPlayerSP) || mc.gameSettings.thirdPersonView != 0);
    }

    public static boolean doRenderTileEntities() {
        return tileEntities.getValue();
    }
}
