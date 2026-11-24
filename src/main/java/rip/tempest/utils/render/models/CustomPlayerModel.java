package rip.tempest.utils.render.models;

import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import java.util.function.Consumer;

import static rip.tempest.utils.Util.mc;

public class CustomPlayerModel extends ModelPlayer {
    private static final float TARGET_HEIGHT = 1.8f;

    public enum Animation {
        LIMBS("Limbs"), BODY("Body only"), OFF("Off");

        private final String label;

        Animation(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final GltfModel model;
    private final ModelPlayer originalModel;
    private final NumberProperty modelScale;
    private final NumberProperty yOffset;
    private final NumberProperty rotationY;
    private final NumberProperty tiltX;
    private final ModeProperty<Animation> animation;
    private final NumberProperty armSpread;
    private final Rigging rigging;

    private final float autoScale;
    private final AutoRig.Angles angles = new AutoRig.Angles();

    public CustomPlayerModel(GltfModel model, ModelPlayer originalModel, NumberProperty modelScale, NumberProperty yOffset, NumberProperty rotationY,
                             NumberProperty tiltX, ModeProperty<Animation> animation, NumberProperty armSpread, Rigging rigging) {
        super(0.0F, false);
        this.model = model;
        this.originalModel = originalModel;
        this.modelScale = modelScale;
        this.yOffset = yOffset;
        this.rotationY = rotationY;
        this.tiltX = tiltX;
        this.animation = animation;
        this.armSpread = armSpread;
        this.rigging = rigging;

        float rawHeight = model.getHeight();
        this.autoScale = rawHeight > 1e-4f ? (TARGET_HEIGHT / rawHeight) : 1.0f;
    }

    public static final class Rigging {
        private static final int MAX_VERTICES = 400_000;

        private final GltfModel model;
        private final Consumer<String> report;
        private Poser rig;
        private float yaw = Float.NaN, tilt = Float.NaN;
        private long builtAt;
        private float[][] positions, normals;
        private String lastReport;

        private float[] posedWith;
        private int version;

        boolean newPose(AutoRig.Angles a) {
            float[] now = {a.bodyX, a.bodyY, a.headX, a.headY, a.rightArmX, a.rightArmY, a.rightArmZ,
                    a.leftArmX, a.leftArmY, a.leftArmZ, a.rightLegX, a.rightLegY, a.rightLegZ,
                    a.leftLegX, a.leftLegY, a.leftLegZ, a.armSpread};
            if (java.util.Arrays.equals(now, posedWith)) return false;
            posedWith = now;
            version = (version + 1) & Integer.MAX_VALUE;
            return true;
        }

        public Rigging(GltfModel model, Consumer<String> report) {
            this.model = model;
            this.report = report;
        }

        Poser rig(float yaw, float tilt) {
            if ((yaw != this.yaw || tilt != this.tilt) && System.currentTimeMillis() - builtAt > 300L) {
                this.yaw = yaw;
                this.tilt = tilt;
                builtAt = System.currentTimeMillis();
                rig = null;
                String message;
                SkeletalRig bones = null;
                try {
                    bones = SkeletalRig.build(model, yaw, tilt);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                if (bones != null) {
                    rig = bones;
                    message = "animating its " + bones.describe();
                } else if (model.vertexCount() > MAX_VERTICES) {
                    message = "too detailed to animate its limbs, so it moves as a whole";
                } else {
                    try {
                        rig = AutoRig.build(model, yaw, tilt);
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                    message = rig != null ? "animating its " + rig.describe() : "no arms or legs found, so it moves as a whole";
                }
                posedWith = null;
                if (rig != null) {
                    positions = new float[model.groupCount()][];
                    normals = new float[model.groupCount()][];
                    for (int g = 0; g < positions.length; g++) {
                        positions[g] = new float[model.groupPositions(g).length];
                        normals[g] = new float[model.groupNormals(g).length];
                    }
                }
                if (!message.equals(lastReport)) {
                    lastReport = message;
                    report.accept(message);
                }
            }
            return rig;
        }
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (entity != mc.thePlayer) {
            originalModel.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            return;
        }

        Animation mode = animation.getValue();
        if (mode != Animation.OFF) {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        }
        Poser rig = mode == Animation.LIMBS ? rigging.rig(rotationY.getValue().floatValue(), tiltX.getValue().floatValue()) : null;

        GlStateManager.pushMatrix();

        float finalScale = autoScale * modelScale.getValue().floatValue();

        GlStateManager.translate(0f, 1.5f + yOffset.getValue().floatValue(), 0f);

        if (rig != null) {
            readAngles();

            if (rigging.newPose(angles)) rig.pose(angles, rigging.positions, rigging.normals);
            GlStateManager.scale(finalScale, finalScale, finalScale);
            model.render(rigging.positions, rigging.normals, rigging.version);
        } else {
            if (mode != Animation.OFF) moveAsAWhole(limbSwing, limbSwingAmount);

            GlStateManager.rotate(rotationY.getValue().floatValue(), 0f, 1f, 0f);
            GlStateManager.rotate(tiltX.getValue().floatValue(), 1f, 0f, 0f);
            GlStateManager.scale(-finalScale, -finalScale, finalScale);
            model.render();
        }

        GlStateManager.popMatrix();
    }

    private void readAngles() {
        angles.bodyX = bipedBody.rotateAngleX;
        angles.bodyY = bipedBody.rotateAngleY;
        angles.headX = bipedHead.rotateAngleX;
        angles.headY = bipedHead.rotateAngleY;
        angles.rightArmX = bipedRightArm.rotateAngleX;
        angles.rightArmY = bipedRightArm.rotateAngleY;
        angles.rightArmZ = bipedRightArm.rotateAngleZ;
        angles.leftArmX = bipedLeftArm.rotateAngleX;
        angles.leftArmY = bipedLeftArm.rotateAngleY;
        angles.leftArmZ = bipedLeftArm.rotateAngleZ;
        angles.rightLegX = bipedRightLeg.rotateAngleX;
        angles.rightLegY = bipedRightLeg.rotateAngleY;
        angles.rightLegZ = bipedRightLeg.rotateAngleZ;
        angles.leftLegX = bipedLeftLeg.rotateAngleX;
        angles.leftLegY = bipedLeftLeg.rotateAngleY;
        angles.leftLegZ = bipedLeftLeg.rotateAngleZ;
        angles.armSpread = (float) Math.toRadians(armSpread.getValue());
    }

    private void moveAsAWhole(float limbSwing, float limbSwingAmount) {
        float step = MathHelper.cos(limbSwing * 0.6662F);
        GlStateManager.translate(0f, -Math.abs(step) * 0.07f * limbSwingAmount, 0f);
        GlStateManager.rotate((float) Math.toDegrees(bipedBody.rotateAngleY) * 1.6f, 0f, 1f, 0f);
        GlStateManager.rotate(limbSwingAmount * 7f + (isSneak ? 14f : 0f), 1f, 0f, 0f);
        GlStateManager.rotate(step * limbSwingAmount * 4f, 0f, 0f, 1f);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale, Entity entity) {
        if (entity != mc.thePlayer) {
            originalModel.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
            return;
        }
    }
}
