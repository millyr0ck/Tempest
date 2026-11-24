package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.utils.mc.ServerUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.EnumChatFormatting;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Bed Wars Stats", category = ModuleCategory.VISUALS)
public final class BedWarsStatsModule extends Module {
    private static final String DRAG_KEY = "BedWarsStats";
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final Property<Boolean> onlyBedWars = new Property<>("Only In Bed Wars", true);
    private final Property<Boolean> showLevels = new Property<>("Show Levels", false);
    private final Property<Boolean> shadow = new Property<>("Shadow", true);
    private final NumberProperty scale = new NumberProperty("Scale", 1.0, 0.5, 2.0, 0.05);

    private int traps;
    private boolean sharpBought;
    private int protBought;

    private boolean sharpWorn;
    private int protWorn;
    private long lastTrapGone;

    @Override
    public void onEnable() {
        reset();
        super.onEnable();
    }

    private void reset() {
        traps = 0;
        sharpBought = false;
        protBought = 0;
        sharpWorn = false;
        protWorn = 0;
    }

    private static boolean inBedWars() {
        return ServerUtils.sidebarTitle().toUpperCase().contains("BED WARS");
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = e -> reset();

    @EventLink
    public final Listener<TickEvent> onTick = e -> {
        if (mc.thePlayer == null) return;
        boolean sharp = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemSword
                    && EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) > 0) {
                sharp = true;
                break;
            }
        }
        int prot = 0;
        for (ItemStack piece : mc.thePlayer.inventory.armorInventory) {
            if (piece != null) prot = Math.max(prot, EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, piece));
        }
        sharpWorn = sharp;
        protWorn = prot;
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = e -> {
        if (e.getPacket() instanceof S02PacketChat chat) {
            if (chat.getType() == 2 || chat.getChatComponent() == null) return;
            String formatted = chat.getChatComponent().getFormattedText();
            String text = EnumChatFormatting.getTextWithoutFormattingCodes(chat.getChatComponent().getUnformattedText()).trim();
            String lower = text.toLowerCase();

            if (formatted.contains("Protect your bed and destroy the enemy bed")
                    || formatted.contains("Destroy the enemy bed and then eliminate them")
                    || formatted.contains("Every few seconds brings a new surprise")) {
                reset();
                return;
            }

            if (lower.contains(" purchased ")) {
                String bought = lower.substring(lower.indexOf(" purchased ") + 11);
                if (bought.startsWith("sharpened swords")) {
                    sharpBought = true;
                } else if (bought.startsWith("reinforced armor")) {
                    protBought = Math.max(protBought, Math.max(1, roman(text.substring(text.lastIndexOf(' ') + 1))));
                } else if (bought.contains("trap")) {
                    traps++;
                }
                return;
            }

            if (lower.contains("trap") && (lower.contains("set off") || lower.contains("triggered"))) trapGone();
        } else if (e.getPacket() instanceof S45PacketTitle title && title.getMessage() != null
                && title.getType() == S45PacketTitle.Type.TITLE) {
            String text = EnumChatFormatting.getTextWithoutFormattingCodes(title.getMessage().getUnformattedText()).toLowerCase();
            if (text.contains("trap") && text.contains("triggered")) trapGone();
        }
    };

    private void trapGone() {
        long now = System.currentTimeMillis();
        if (now - lastTrapGone < 1000L) return;
        lastTrapGone = now;
        if (traps > 0) traps--;
    }

    private static int roman(String s) {
        for (int i = ROMAN.length - 1; i > 0; i--) if (ROMAN[i].equals(s.replaceAll("[^IVX]", ""))) return i;
        return 0;
    }

    @EventLink
    public final Listener<Render2DEvent> onRender = e -> {
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        if (onlyBedWars.getValue() && !inBedWars() && !(mc.currentScreen instanceof GuiChat)) return;

        boolean sharp = sharpBought || sharpWorn;
        int prot = Math.max(protBought, protWorn);
        String[] lines = {
                line("Trap", traps > 0, showLevels.getValue() && traps > 1 ? " (" + traps + ")" : ""),
                line("Sharp", sharp, ""),
                line("Prot", prot > 0, showLevels.getValue() && prot > 0 ? " (" + ROMAN[Math.min(prot, 5)] + ")" : "")
        };

        float s = scale.getValue().floatValue();
        int width = 0;
        for (String l : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(l));

        DraggingProcess.DraggableComponent d = DraggingProcess.components.get(DRAG_KEY);
        if (d == null) {
            d = new DraggingProcess.DraggableComponent(2, 2);
            DraggingProcess.components.put(DRAG_KEY, d);
        }
        d.setWidth(width * s);
        d.setHeight((lines.length * 10 - 1) * s);

        GlStateManager.pushMatrix();
        GlStateManager.translate(d.getX(), d.getY(), 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.enableBlend();
        for (int i = 0; i < lines.length; i++) {
            mc.fontRendererObj.drawString(lines[i], 0, i * 10, 0xFFFFFFFF, shadow.getValue());
        }
        GlStateManager.popMatrix();
    };

    private static String line(String name, boolean has, String extra) {
        return "\2477- \247f" + name + ": " + (has ? "\247atrue" : "\247cfalse") + "\2477" + extra;
    }
}
