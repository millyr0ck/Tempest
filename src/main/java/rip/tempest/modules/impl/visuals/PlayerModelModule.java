package rip.tempest.modules.impl.visuals;

import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.render.models.CustomPlayerModel;
import rip.tempest.utils.render.models.GltfModel;
import rip.tempest.utils.render.models.LocalPlayerHidingLayer;
import rip.tempest.utils.render.models.PlayerModelPreset;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Player Model", category = ModuleCategory.VISUALS)
public final class PlayerModelModule extends Module {
    private final ModeProperty<PlayerModelPreset> model = new ModeProperty<>("Model", PlayerModelPreset.POMNI);

    private final NumberProperty modelScale = new NumberProperty("Model Scale", 1.0, 0.1, 5.0, 0.05);
    private final NumberProperty yOffset = new NumberProperty("Model Y Offset", 0.0, -3.0, 3.0, 0.05);
    private final NumberProperty rotationY = new NumberProperty("Model Rotation", 180.0, 0.0, 360.0, 1.0);
    private final NumberProperty tiltX = new NumberProperty("Model Tilt", 0.0, 0.0, 360.0, 1.0);

    private final ModeProperty<CustomPlayerModel.Animation> animation = new ModeProperty<>("Animation", CustomPlayerModel.Animation.LIMBS);
    private final NumberProperty armSpread = new NumberProperty("Arm Spread", 8.0,
            () -> animation.getValue() == CustomPlayerModel.Animation.LIMBS, 0.0, 45.0, 1.0);

    private final Map<RenderPlayer, ModelPlayer> originalModels = new HashMap<>();
    private final Map<RenderPlayer, LayerRenderer<AbstractClientPlayer>> hiddenArmorLayers = new HashMap<>();

    private GltfModel loadedModel;

    private static Field mainModelField;

    static {
        try {
            mainModelField = RendererLivingEntity.class.getDeclaredField("mainModel");
            mainModelField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Logger.chatPrint("\247cPlayerModel: could not find mainModel field — check your mappings.");
        }
    }

    @Override
    public void onEnable() {
        if (mainModelField == null) {
            Logger.chatPrint("\247cPlayerModel: reflection target missing, cannot enable.");
            setEnabled(false);
            return;
        }

        File file = new File(model.getValue().getPath());
        if (!file.exists()) {
            Logger.chatPrint("\247cPlayerModel: file not found: " + file.getAbsolutePath());
            setEnabled(false);
            return;
        }

        try {
            loadedModel = new GltfModel(file);
        } catch (Exception e) {
            Logger.chatPrint("\247cPlayerModel: failed to load glTF — " + e.getMessage());
            setEnabled(false);
            return;
        }

        swapModels();
        Logger.chatPrint("\247aPlayerModel: loaded " + file.getName()
                + (loadedModel.hasTexture() ? "" : " \247e(no texture found, untextured)"));

        super.onEnable();
    }

    @Override
    public void onDisable() {
        restoreModels();
        originalModels.clear();
        hiddenArmorLayers.clear();
        if (loadedModel != null) loadedModel.dispose();
        loadedModel = null;
        super.onDisable();
    }

    private void swapModels() {
        Map<String, RenderPlayer> skinMap = mc.getRenderManager().getSkinMap();

        CustomPlayerModel.Rigging rigging = new CustomPlayerModel.Rigging(loadedModel, found -> Logger.chatPrint("\2477PlayerModel: " + found));

        for (RenderPlayer renderPlayer : skinMap.values()) {
            try {
                ModelPlayer current = (ModelPlayer) mainModelField.get(renderPlayer);
                originalModels.putIfAbsent(renderPlayer, current);
                mainModelField.set(renderPlayer, new CustomPlayerModel(loadedModel, current, modelScale, yOffset, rotationY, tiltX,
                        animation, armSpread, rigging));
            } catch (IllegalAccessException e) {
                Logger.chatPrint("\247cPlayerModel: failed to swap model on a RenderPlayer instance.");
            }

            List<LayerRenderer<AbstractClientPlayer>> layers = renderPlayer.getLayerRenderers();
            for (int i = 0; i < layers.size(); i++) {
                LayerRenderer<AbstractClientPlayer> layer = layers.get(i);

                if (LayerBipedArmor.class.isInstance(layer)) {
                    hiddenArmorLayers.putIfAbsent(renderPlayer, layer);
                    layers.set(i, new LocalPlayerHidingLayer(layer));
                    break;
                }
            }
        }
    }

    private void restoreModels() {
        for (Map.Entry<RenderPlayer, ModelPlayer> entry : originalModels.entrySet()) {
            try {
                mainModelField.set(entry.getKey(), entry.getValue());
            } catch (IllegalAccessException ignored) {
            }
        }

        for (Map.Entry<RenderPlayer, LayerRenderer<AbstractClientPlayer>> entry : hiddenArmorLayers.entrySet()) {
            List<LayerRenderer<AbstractClientPlayer>> layers = entry.getKey().getLayerRenderers();
            for (int i = 0; i < layers.size(); i++) {
                if (layers.get(i) instanceof LocalPlayerHidingLayer) {
                    layers.set(i, entry.getValue());
                    break;
                }
            }
        }
    }
}
