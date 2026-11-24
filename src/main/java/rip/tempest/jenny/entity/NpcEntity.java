package rip.tempest.jenny.entity;

import rip.tempest.jenny.anim.AnimPlayer;
import rip.tempest.jenny.anim.NpcAnimation;
import rip.tempest.jenny.geo.GeoModel;
import rip.tempest.jenny.geo.GeoBone;
import rip.tempest.jenny.render.CumTrails;
import rip.tempest.jenny.sound.SceneSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.Map;

public class NpcEntity extends EntityLivingBase {
    private static final double SCENE_DISTANCE = 1.0;
    private static final double SOLO_DISTANCE = 1.8;

    private final CharacterType type;
    private final AnimPlayer animPlayer = new AnimPlayer();
    private final Outfit outfit = new Outfit();
    private static final java.util.Random RNG = new java.util.Random();

    private boolean koboldRight = true;

    private int koboldBody = RNG.nextInt(KoboldColor.values().length);
    private int koboldEyes = RNG.nextInt(KoboldColor.values().length);

    private boolean actPlaying = false;
    private int dialogueIndex = 0;

    private SexScene currentScene = null;

    private int sceneStage = 0;
    private int introIndex = 0;

    private boolean naked = false;
    private boolean stripping = false;

    private boolean preluding = false;

    private boolean stripTarget = true;

    private SexScene pendingScene = null;

    private float cumProgress = 0f;

    private boolean meterFromEvents = false;

    private String line = "";
    private String lineSpeaker = null;
    private long lineTime = 0L;

    private float lookYaw, lookPitch;
    private long lookNanos;

    public NpcEntity(World world, CharacterType type, double x, double y, double z) {
        super(world);
        this.type = type;
        this.setSize(0.6f, 1.9f);
        this.noClip = true;
        moveTo(x, y, z);
        this.setEntityId(-Math.abs(type.hashCode() + (int) (x * 100 + z * 7)));

        playIdle();
    }

    private void moveTo(double x, double y, double z) {
        this.posX = this.prevPosX = this.lastTickPosX = x;
        this.posY = this.prevPosY = this.lastTickPosY = y;
        this.posZ = this.prevPosZ = this.lastTickPosZ = z;
        this.setPosition(x, y, z);
    }

    private void playIdle() {
        Map<String, NpcAnimation> anims = type.getAnimations();
        if (anims != null) {
            NpcAnimation idle = anims.get(type.idleAnimName);
            if (idle != null) animPlayer.setAnimation(idle, true);
        }
    }

    public void playAct(NpcAnimation anim) {
        if (anim != null && !isInScene()) {
            animPlayer.setAnimation(anim, false);
            actPlaying = true;
        }
    }

    public void startScene(SexScene scene) {
        if (scene.character != type || (!scene.act && !type.allowsAdultContent())) return;
        stopScene();
        placeForScene(scene);
        NpcAnimation prelude = scene.prelude(type.getAnimations());
        if (prelude != null) {
            preluding = true;
            pendingScene = scene;
            animPlayer.setAnimation(prelude, false);
        } else {
            afterPrelude(scene);
        }
    }

    private void afterPrelude(SexScene scene) {
        preluding = false;
        if (!scene.act && !naked && type.canUndress()) {
            startStrip(true, scene);
        } else {
            beginScene(scene);
        }
    }

    public void toggleNaked() {
        if (isInScene() || !type.canUndress()) return;
        faceLocalPlayer();
        startStrip(!naked, null);
    }

    private void startStrip(boolean target, SexScene then) {
        stripping = true;
        actPlaying = false;
        stripTarget = target;
        pendingScene = then;
        animPlayer.setAnimation(type.getAnimations().get(type.stripAnimName), false);
    }

    private void beginScene(SexScene scene) {
        currentScene = scene;
        pendingScene = null;
        actPlaying = false;
        cumProgress = 0f;
        meterFromEvents = false;
        koboldRight = true;
        introIndex = 0;
        if (scene.hasIntro() && scene.intro(0, type.getAnimations()) != null) {
            setStage(0);
        } else {
            enterLoop();
        }
    }

    private void enterLoop() {
        if (currentScene.hasLoop()) setStage(1);
        else stopScene();
    }

    public void goFaster() {
        if (currentScene == null || !currentScene.hasFast() || sceneStage != 1) return;
        setStage(2);
    }

    public void goSlower() {
        if (currentScene == null || sceneStage != 2) return;
        setStage(1);
    }

    public void triggerCum() {
        if (currentScene == null || sceneStage == 3) return;
        if (currentScene.cum(type.getAnimations()) == null) {
            stopScene();
            return;
        }
        setStage(3);
    }

    public void stopScene() {
        currentScene = null;
        sceneStage = 0;
        stripping = false;
        preluding = false;
        pendingScene = null;
        cumProgress = 0f;
        meterFromEvents = false;
        CumTrails.clear(this);
        playIdle();
    }

    private void setStage(int stage) {
        sceneStage = stage;
        Map<String, NpcAnimation> anims = type.getAnimations();
        NpcAnimation anim;
        boolean loop;
        switch (stage) {
            case 0:
                anim = currentScene.intro(introIndex, anims);
                loop = false;
                break;
            case 2:
                anim = currentScene.fast(anims);
                loop = !currentScene.fastCycles();
                break;
            case 3:
                anim = currentScene.cum(anims);
                loop = false;
                break;
            default:

                anim = currentScene == SexScene.KOBOLD_BLOWJOB
                        ? anims.get("animation.kobold.blowjobSlow" + (koboldRight ? "R" : "L"))
                        : currentScene.loop(anims);
                loop = true;
        }
        if (anim == null) return;
        if (animPlayer.getCurrent() == anim) {
            animPlayer.setAnimation(anim, loop);
            animPlayer.resetTime();
        } else {
            animPlayer.setAnimation(anim, loop);
        }
    }

    private void placeForScene(SexScene scene) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        double dx = posX - mc.thePlayer.posX;
        double dz = posZ - mc.thePlayer.posZ;
        float yaw = dx * dx + dz * dz > 0.04
                ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                : mc.thePlayer.rotationYaw;
        double dist = scene.withPlayer ? SCENE_DISTANCE : SOLO_DISTANCE;
        double rad = Math.toRadians(yaw);
        moveTo(mc.thePlayer.posX - Math.sin(rad) * dist, mc.thePlayer.posY, mc.thePlayer.posZ + Math.cos(rad) * dist);
        rotationYaw = prevRotationYaw = yaw + 180f;

        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = scene.cameraPitch;
    }

    private void faceLocalPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        double dx = mc.thePlayer.posX - posX;
        double dz = mc.thePlayer.posZ - posZ;
        if (dx * dx + dz * dz < 1.0E-4) return;
        rotationYaw = prevRotationYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void onSceneEvent(String event) {
        if ("becomeNude".equals(event) && stripping) naked = stripTarget;

        if ("paymentMSG2".equals(event) && pendingScene != null) {
            switch (pendingScene) {
                case JENNY_BLOWJOB: sayAsPlayer("Give me the sucky sucky and these are yours"); break;
                case JENNY_PAIZURI: sayAsPlayer("gib booba OwO"); break;
                case JENNY_DOGGY: sayAsPlayer("Give me the sex pls :)"); break;
                default: break;
            }
        }

        if (currentScene == SexScene.KOBOLD_BLOWJOB && sceneStage == 1) {
            if ("switch".equals(event) && RNG.nextBoolean()) {
                NpcAnimation sw = type.getAnimations().get("animation.kobold.blowjobSlow" + (koboldRight ? "R" : "L") + "Switch");
                if (sw != null) animPlayer.setAnimation(sw, true);
            } else if ("endSwitch".equals(event)) {
                koboldRight = !koboldRight;
                setStage(1);
            }
        }

        if ("creampie".equals(event) || ("cum".equals(event) && currentScene == SexScene.GALATH_BED)) {
            CumTrails.creampie(this);
        }
        if ("clearcum".equals(event)) CumTrails.clear(this);
    }

    public void tick() {
        animPlayer.update();
        for (String event : animPlayer.drainEvents()) {
            onSceneEvent(event);

            SceneSounds.handle(this, event);
        }

        animPlayer.setOverlay(isIdle() ? type.eyesAnimation() : null);

        if (preluding) {
            if (animPlayer.isFinished()) afterPrelude(pendingScene);
            return;
        }

        if (stripping) {
            if (animPlayer.isFinished()) {
                stripping = false;
                naked = stripTarget;
                SexScene next = pendingScene;
                pendingScene = null;
                if (next != null) beginScene(next);
                else playIdle();
            }
            return;
        }

        if (currentScene == null) {
            if (animPlayer.isFinished()) {
                actPlaying = false;
                playIdle();
            }
            return;
        }

        if (animPlayer.isFinished()) {
            if (sceneStage == 0) {
                introIndex++;
                if (currentScene.intro(introIndex, type.getAnimations()) != null) setStage(0);
                else enterLoop();
            } else if (sceneStage == 2) {
                setStage(2);
            } else if (sceneStage == 3) {
                stopScene();
                return;
            }
        }

        if (!meterFromEvents && !currentScene.act) {
            if (sceneStage == 1) cumProgress = Math.min(1f, cumProgress + 0.003f);
            else if (sceneStage == 2) cumProgress = Math.min(1f, cumProgress + 0.008f);
        }
    }

    public void addCumProgress(float amount) {
        if (currentScene == null || sceneStage == 3) return;
        meterFromEvents = true;
        cumProgress = Math.min(1f, cumProgress + amount);
    }

    public boolean isIdle() {
        return currentScene == null && !stripping && !actPlaying;
    }

    public float[] boneRotation(GeoBone bone) {
        float[] rest = bone.rotation;
        float[] anim = animPlayer.getBoneRotation(bone.name);
        float x = (rest != null ? rest[0] : 0f) + (anim != null ? anim[0] : 0f);
        float y = (rest != null ? rest[1] : 0f) + (anim != null ? anim[1] : 0f);
        float z = (rest != null ? rest[2] : 0f) + (anim != null ? anim[2] : 0f);

        if (isIdle()) {
            switch (bone.name) {
                case "neck":
                    y = lookYaw * 0.5f;
                    break;
                case "head":
                    y = lookYaw;
                    x = lookPitch;
                    break;
                case "body":
                case "dd":
                    if (isIdle()) y = 0f;
                    break;
                default:
                    break;
            }
        }
        return new float[]{x, y, z};
    }

    public void updateLook() {
        long now = System.nanoTime();
        float dt = lookNanos == 0 ? 0f : Math.min((now - lookNanos) / 1e9f, 0.1f);
        lookNanos = now;

        float targetYaw = 0f, targetPitch = 0f;
        if (!isIdle()) {
            lookYaw = lookPitch = 0f;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (isIdle() && mc.thePlayer != null && mc.thePlayer.getDistanceSqToEntity(this) < 144.0) {
            double dx = mc.thePlayer.posX - posX;
            double dz = mc.thePlayer.posZ - posZ;
            double dy = (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()) - (posY + 1.5);
            float yawTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float delta = wrap(yawTo - rotationYaw);
            if (Math.abs(delta) > 75f) {
                float turn = Math.signum(delta) * Math.min(Math.abs(delta) - 75f, 180f * dt);
                rotationYaw = prevRotationYaw = rotationYaw + turn;
                delta -= turn;
            }
            targetYaw = Math.max(-70f, Math.min(70f, delta));
            double horiz = Math.sqrt(dx * dx + dz * dz);
            targetPitch = (float) Math.max(-40.0, Math.min(40.0, -Math.toDegrees(Math.atan2(dy, horiz))));
        }
        float k = 1f - (float) Math.exp(-8f * dt);
        lookYaw += (targetYaw - lookYaw) * k;
        lookPitch += (targetPitch - lookPitch) * k;
        if (Math.abs(lookYaw) < 0.01f && targetYaw == 0f) lookYaw = 0f;
        if (Math.abs(lookPitch) < 0.01f && targetPitch == 0f) lookPitch = 0f;
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    public boolean isInScene() { return currentScene != null || stripping || preluding; }
    public boolean isPreluding() { return preluding; }

    public SexScene getPendingScene() { return pendingScene; }
    public boolean isStripping() { return stripping; }

    public boolean showsPlayer() {
        if (preluding) return pendingScene != null && pendingScene.withPlayer;
        if (currentScene == null || !currentScene.withPlayer) return false;
        return !(sceneStage == 0 && currentScene.isSoloIntro(introIndex));
    }

    public KoboldColor getKoboldBody() { return KoboldColor.values()[koboldBody]; }
    public KoboldColor getKoboldEyes() { return KoboldColor.values()[koboldEyes]; }
    public void cycleKoboldBody() { koboldBody = (koboldBody + 1) % KoboldColor.values().length; }
    public void cycleKoboldEyes() { koboldEyes = (koboldEyes + 1) % KoboldColor.values().length; }
    public SexScene getCurrentScene() { return currentScene; }
    public int getSceneStage() { return sceneStage; }
    public boolean isNaked() { return naked && type.allowsAdultContent(); }
    public float getCumProgress() { return cumProgress; }

    public void say(String text) {
        line = text;
        lineSpeaker = null;
        lineTime = System.currentTimeMillis();
    }

    public void sayAsPlayer(String text) {
        line = text;
        Minecraft mc = Minecraft.getMinecraft();
        lineSpeaker = mc.thePlayer != null ? mc.thePlayer.getName() : "You";
        lineTime = System.currentTimeMillis();
    }

    public String getLineSpeaker() {
        return lineSpeaker != null ? lineSpeaker : getName();
    }

    public Outfit getOutfit() { return outfit; }

    public String getLine() {
        return line != null && System.currentTimeMillis() - lineTime < 7000L ? line : null;
    }

    @Override public boolean isEntityAlive() { return true; }
    @Override public float getEyeHeight() { return 1.62f; }
    @Override protected void entityInit() { super.entityInit(); }
    @Override public ItemStack getHeldItem() { return null; }
    @Override public ItemStack getEquipmentInSlot(int slot) { return null; }
    @Override public ItemStack getCurrentArmor(int slot) { return null; }
    @Override public void setCurrentItemOrArmor(int slot, ItemStack stack) {}
    @Override public ItemStack[] getInventory() { return new ItemStack[0]; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean isPushedByWater() { return false; }

    public CharacterType getCharacterType() { return type; }
    public GeoModel getModel() { return type.getModel(isNaked(), outfit.any()); }
    public AnimPlayer getAnimPlayer() { return animPlayer; }
    public ResourceLocation getTexture() { return type.getTexture(); }

    public String nextDialogue() {
        String[] lines = type.dialogueLines;
        String text = lines[dialogueIndex % lines.length];
        dialogueIndex++;
        return text;
    }

    public String getName() {
        return type.id.substring(0, 1).toUpperCase() + type.id.substring(1);
    }
}
