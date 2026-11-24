package rip.tempest.jenny.render;

import rip.tempest.jenny.anim.AnimPlayer;
import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.jenny.entity.KoboldColor;
import rip.tempest.jenny.entity.NpcEntity;
import rip.tempest.jenny.entity.Outfit;
import rip.tempest.jenny.geo.GeoBone;
import rip.tempest.jenny.geo.GeoCube;
import rip.tempest.jenny.geo.GeoFace;
import rip.tempest.jenny.geo.GeoModel;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GeoRenderer {
    private static final float SCALE = 1f / 16f;

    private static final Set<String> HIDDEN_BONES = new HashSet<>(Arrays.asList(
            "items", "coin", "futaCock", "futaBallLL", "futaBallLR", "crown", "egg"));

    private static final String PLAYER_BONE = "steve";

    private static AnimPlayer anim;
    private static int texW, texH;
    private static boolean playerPass;
    private static boolean hideHead;
    private static boolean slimArms;
    private static Set<String> playerAncestors = new HashSet<>();
    private static NpcEntity npc;
    private static Outfit outfit;

    private static boolean armorActive;

    private static float uOffset;

    public static void render(NpcEntity npc, double rx, double ry, double rz) {
        GeoModel model = npc.getModel();
        if (model == null) return;

        GeoRenderer.npc = npc;
        anim = npc.getAnimPlayer();

        anim.update();
        npc.updateLook();
        outfit = npc.getOutfit();
        armorActive = outfit.any() && !npc.isNaked() && npc.getCharacterType().showsArmorOn(model);
        uOffset = 0f;
        texW = model.textureWidth;
        texH = model.textureHeight;
        ResourceLocation texture = npc.getTexture();

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) rx, (float) ry, (float) rz);
        GlStateManager.rotate(180f - npc.rotationYaw, 0f, 1f, 0f);
        GlStateManager.scale(SCALE, SCALE, SCALE);

        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.depthMask(true);
        GlStateManager.color(1f, 1f, 1f, 1f);

        playerPass = false;
        TextureLoader.bind("/assets/" + texture.getResourceDomain() + "/" + texture.getResourcePath());
        for (GeoBone root : model.rootBones) {
            renderBone(root, false, -1);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        uOffset = 0f;

        if (npc.showsPlayer() && model.bones.containsKey(PLAYER_BONE)) {
            playerPass = true;

            hideHead = SceneCamera.isFirstPerson(npc);
            slimArms = PlayerSkin.isSlim();
            playerAncestors = ancestorsOf(model, PLAYER_BONE);
            PlayerSkin.bind();
            for (GeoBone root : model.rootBones) {
                if (playerAncestors.contains(root.name)) renderBone(root, false, -1);
            }
            playerPass = false;
        }

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static Set<String> ancestorsOf(GeoModel model, String name) {
        Set<String> out = new HashSet<>();
        for (GeoBone b = model.bones.get(name); b != null; b = b.parentName == null ? null : model.bones.get(b.parentName)) {
            out.add(b.name);
        }
        return out;
    }

    private static boolean isHidden(String boneName) {
        if (HIDDEN_BONES.contains(boneName)) return true;
        if (boneName.startsWith("custom")) return true;
        if (!playerPass) {
            if (boneName.startsWith("armor")) {
                return !armorActive || outfit.stripFor(npc.getCharacterType(), boneName) < 0;
            }
            if (armorActive && outfit.hides(npc.getCharacterType(), boneName)) return true;
        } else if (boneName.startsWith("armor")) {
            return true;
        }
        if (playerPass) {
            if (hideHead && boneName.equals("Head2")) return true;

            if (boneName.endsWith("Alex")) return !slimArms;
            if (boneName.endsWith("Steve")) return slimArms;
        }
        return false;
    }

    private static void renderBone(GeoBone bone, boolean inPlayer, int strip) {
        if (bone.name.equals(PLAYER_BONE)) {
            if (!playerPass) return;
            inPlayer = true;
        } else if (playerPass && !inPlayer && !playerAncestors.contains(bone.name)) {
            return;
        }
        if (isHidden(bone.name)) return;

        GlStateManager.pushMatrix();

        float[] pos = anim.getBonePosition(bone.name);
        if (pos != null) {
            GlStateManager.translate(-pos[0], pos[1], pos[2]);
        }

        float px = -bone.pivot[0];
        float py = bone.pivot[1];
        float pz = bone.pivot[2];
        GlStateManager.translate(px, py, pz);

        float[] rot = npc.boneRotation(bone);
        applyRotation(rot[0], rot[1], rot[2]);

        float[] scale = anim.getBoneScale(bone.name);
        if (scale != null) {
            GlStateManager.scale(scale[0], scale[1], scale[2]);
        }

        GlStateManager.translate(-px, -py, -pz);

        if (armorActive && !playerPass && bone.name.startsWith("armor")) {
            strip = outfit.stripFor(npc.getCharacterType(), bone.name);
        }
        if (!playerPass || inPlayer) {
            uOffset = strip > 0 ? 72f * strip / 4096f : 0f;

            int rgb = 0xFFFFFF;
            if (!playerPass && npc.getCharacterType() == CharacterType.KOBOLD) {
                int k = KoboldColor.tintFor(bone.name, npc.getKoboldBody(), npc.getKoboldEyes());
                if (k >= 0) rgb = k;
            }
            if (strip == Outfit.Material.LEATHER.strip) rgb = multiply(rgb, outfit.leatherRgb());
            boolean tint = rgb != 0xFFFFFF;
            if (tint) {
                GlStateManager.color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f);
            }
            for (GeoCube cube : bone.cubes) {
                renderCube(cube, texW, texH);
            }
            if (tint) GlStateManager.color(1f, 1f, 1f, 1f);
        }
        for (GeoBone child : bone.children) {
            renderBone(child, inPlayer, strip);
        }

        GlStateManager.popMatrix();
    }

    private static int multiply(int a, int b) {
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
        int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    private static void applyRotation(float x, float y, float z) {
        if (z != 0f) GlStateManager.rotate(z, 0f, 0f, 1f);
        if (y != 0f) GlStateManager.rotate(-y, 0f, 1f, 0f);
        if (x != 0f) GlStateManager.rotate(-x, 1f, 0f, 0f);
    }

    private static void renderCube(GeoCube cube, int texW, int texH) {
        boolean rotated = cube.localRotation != null && cube.localPivot != null;
        if (rotated) {
            float px = -cube.localPivot[0];
            float py = cube.localPivot[1];
            float pz = cube.localPivot[2];
            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            applyRotation(cube.localRotation[0], cube.localRotation[1], cube.localRotation[2]);
            GlStateManager.translate(-px, -py, -pz);
        }

        drawCube(cube, texW, texH);

        if (rotated) {
            GlStateManager.popMatrix();
        }
    }

    private static void drawCube(GeoCube cube, int texW, int texH) {
        float inf = cube.inflate;
        float x0 = -(cube.origin[0] + cube.size[0]) - inf;
        float x1 = -cube.origin[0] + inf;
        float y0 = cube.origin[1] - inf;
        float y1 = cube.origin[1] + cube.size[1] + inf;
        float z0 = cube.origin[2] - inf;
        float z1 = cube.origin[2] + cube.size[2] + inf;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        face(wr, cube.west, texW, texH, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
        face(wr, cube.east, texW, texH, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
        face(wr, cube.north, texW, texH, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
        face(wr, cube.south, texW, texH, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
        face(wr, cube.up, texW, texH, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(wr, cube.down, texW, texH, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);

        tess.draw();
    }

    private static void face(WorldRenderer wr, GeoFace face, int texW, int texH,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        if (face == null) return;

        float u1 = face.uv[0] / texW + uOffset;
        float v1 = face.uv[1] / texH;
        float u2 = (face.uv[0] + face.uvSize[0]) / texW + uOffset;
        float v2 = (face.uv[1] + face.uvSize[1]) / texH;

        wr.pos(ax, ay, az).tex(u2, v1).endVertex();
        wr.pos(bx, by, bz).tex(u1, v1).endVertex();
        wr.pos(cx, cy, cz).tex(u1, v2).endVertex();
        wr.pos(dx, dy, dz).tex(u2, v2).endVertex();
    }
}
