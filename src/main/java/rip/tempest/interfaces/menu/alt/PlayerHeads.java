package rip.tempest.interfaces.menu.alt;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerHeads {
    private static final Map<String, ResourceLocation> loaded = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> requested = new ConcurrentHashMap<>();

    private static final Map<String, ResourceLocation> defaults = new ConcurrentHashMap<>();

    private PlayerHeads() {
    }

    static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    static ResourceLocation skin(String name, String uuid) {
        UUID id = parse(uuid);
        if (id == null) return defaults.computeIfAbsent(name, n -> DefaultPlayerSkin.getDefaultSkin(offlineId(n)));

        ResourceLocation skin = loaded.get(uuid);
        if (skin != null) return skin;
        if (requested.putIfAbsent(uuid, Boolean.TRUE) == null) fetch(name, id, uuid);
        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    private static void fetch(String name, UUID id, String key) {
        Thread thread = new Thread(() -> {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                GameProfile profile = mc.getSessionService().fillProfileProperties(new GameProfile(id, name), false);
                mc.getSkinManager().loadProfileTextures(profile, (type, location, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) loaded.put(key, location);
                }, false);
            } catch (Exception ignored) {
            }
        }, "account-head");
        thread.setDaemon(true);
        thread.start();
    }

    private static UUID parse(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        try {
            return UUIDTypeAdapter.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void draw(ResourceLocation skin, float x, float y, float size, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, alpha);
        int s = Math.round(size);
        Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), 8f, 8f, 8, 8, s, s, 64f, 64f);
        Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), 40f, 8f, 8, 8, s, s, 64f, 64f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
