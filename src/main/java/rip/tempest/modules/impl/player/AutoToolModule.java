package rip.tempest.modules.impl.player;

import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Auto Tool", category = ModuleCategory.PLAYER)
public class AutoToolModule extends Module {

    private static final Property<Boolean> sneakOnly = new Property<>("Sneak Only", false);

    public static boolean shouldSwap = true;

    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {

        if (sneakOnly.getValue() && !mc.thePlayer.isSneaking()) return;

        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.objectMouseOver == null) return;

        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) return;

        int itemToUse = getBestToolSlot(pos);
        if (itemToUse == -1) return;

        if (shouldSwap) {
            mc.thePlayer.inventory.currentItem = itemToUse;
        }
    };

    @EventLink
    public final Listener<AttackEvent> attackEventListener = e -> {
        float bestStr = 0.0F;
        int itemToUse = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack == null) continue;

            if (!(itemStack.getItem() instanceof ItemSword)) continue;

            ItemSword item = (ItemSword) itemStack.getItem();

            if (item.attackDamage > bestStr) {
                bestStr = item.attackDamage;
                itemToUse = i;
            }
        }
        if (itemToUse != -1) mc.thePlayer.inventory.currentItem = itemToUse;
    };

    private int getBestToolSlot(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();

        float bestStr = 1.0F;
        int itemTouse = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack == null) continue;

            if (itemStack.getStrVsBlock(block) > bestStr) {
                bestStr = itemStack.getStrVsBlock(block);
                itemTouse = i;
            }
        }

        return itemTouse;
    }

}
