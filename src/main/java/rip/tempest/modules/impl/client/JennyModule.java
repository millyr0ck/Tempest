package rip.tempest.modules.impl.client;

import rip.tempest.jenny.NpcManager;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.gui.CharacterSelectGui;
import rip.tempest.jenny.gui.DialogueGui;
import rip.tempest.jenny.render.GeoRenderer;
import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.api.events.impl.game.RightClickEvent;
import rip.tempest.api.events.impl.player.MoveEvent;
import rip.tempest.jenny.entity.SexScene;
import rip.tempest.jenny.render.SceneCamera;
import org.lwjgl.input.Keyboard;
import rip.tempest.api.events.impl.render.Render3DEvent;
import rip.tempest.api.events.impl.world.TickEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Jenny", category = ModuleCategory.CLIENT, description = "Spawn and interact with Jenny NPCs")
public class JennyModule extends Module {
    private boolean openSelectNextTick = false;

    @EventLink
    public final Listener<TickEvent> onTick = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        for (NpcEntity npc : NpcManager.getAll()) {
            npc.tick();
        }
        rip.tempest.jenny.render.CumTrails.tick();

        if (openSelectNextTick) {
            openSelectNextTick = false;
            mc.displayGuiScreen(new CharacterSelectGui());
        }
    };

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        RenderManager rm = mc.getRenderManager();
        double vx = rm.getRenderPosX();
        double vy = rm.getRenderPosY();
        double vz = rm.getRenderPosZ();

        for (NpcEntity npc : NpcManager.getAll()) {
            double rx = npc.posX - vx;
            double ry = npc.posY - vy;
            double rz = npc.posZ - vz;
            rip.tempest.jenny.render.NpcShadow.render(npc, rx, ry, rz);
            GeoRenderer.render(npc, rx, ry, rz);
        }
        rip.tempest.jenny.render.CumTrails.render(rip.tempest.jenny.render.SceneCamera.partialTicks(), vx, vy, vz);
    };

    @EventLink
    public final Listener<MoveEvent> onMove = event -> {
        if (!SceneCamera.isActive()) return;
        event.setForward(0f);
        event.setStrafe(0f);
        event.setJump(false);
        event.setSneak(false);
    };

    @EventLink
    public final Listener<KeyPressEvent> onKey = event -> {
        NpcEntity npc = sceneNpc();
        if (npc == null) return;
        int key = event.getKey();
        boolean act = npc.getCurrentScene() != null && npc.getCurrentScene().act;
        if (key == mc.gameSettings.keyBindJump.getKeyCode() && !act) {
            if (npc.getCurrentScene() != null && npc.getCumProgress() >= 1f && npc.getSceneStage() != 3) {
                npc.triggerCum();
                npc.say(DialogueGui.sceneDialogue(npc.getCurrentScene(), 3));
            } else if (npc.getSceneStage() == 2) npc.goSlower();
            else if (npc.getSceneStage() == 1) {
                npc.goFaster();
                if (npc.getSceneStage() == 2) npc.say(DialogueGui.sceneDialogue(npc.getCurrentScene(), 2));
            }
        } else if (key == Keyboard.KEY_F && !act) {
            if (npc.getCurrentScene() != null && npc.getSceneStage() != 3) {
                npc.triggerCum();
                npc.say(DialogueGui.sceneDialogue(npc.getCurrentScene(), 3));
            }
        } else if (key == mc.gameSettings.keyBindSneak.getKeyCode()) {
            SexScene scene = npc.getCurrentScene();
            if (scene != null && scene.act && scene.cumAnim != null && npc.getSceneStage() != 3) {
                npc.triggerCum();
                return;
            }
            npc.stopScene();
            npc.say(npc.nextDialogue());
        }
    };

    private static NpcEntity sceneNpc() {
        return rip.tempest.jenny.gui.SceneHud.sceneNpc();
    }

    @EventLink
    public final Listener<RightClickEvent> onRightClick = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        NpcEntity scene = sceneNpc();
        if (scene != null) {
            mc.displayGuiScreen(new DialogueGui(scene));
            event.setCancelled(true);
            return;
        }

        NpcEntity npc = findLookedAtNpc();
        if (npc == null) return;

        mc.displayGuiScreen(new DialogueGui(npc));
        event.setCancelled(true);
    };

    private NpcEntity findLookedAtNpc() {
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = mc.thePlayer.getLook(1f);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

        double closest = reach;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            closest = eye.distanceTo(mc.objectMouseOver.hitVec);
        }

        NpcEntity result = null;
        for (NpcEntity npc : NpcManager.getAll()) {
            AxisAlignedBB box = npc.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
            if (box.isVecInside(eye)) return npc;

            MovingObjectPosition hit = box.calculateIntercept(eye, end);
            if (hit == null) continue;

            double distance = eye.distanceTo(hit.hitVec);
            if (distance < closest) {
                closest = distance;
                result = npc;
            }
        }
        return result;
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = event -> {
        NpcManager.onWorldLoad();
        rip.tempest.jenny.render.CumTrails.clearAll();
    };

    @Override
    public void onEnable() {
        openSelectNextTick = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        NpcManager.despawnAll();
        rip.tempest.jenny.render.CumTrails.clearAll();
        super.onDisable();
    }
}
