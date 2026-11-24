package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.FilteredModeProperty;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.knife.KnifeAnimator;
import rip.tempest.utils.render.knife.KnifeModel;
import rip.tempest.utils.render.knife.KnifeRenderer;
import rip.tempest.utils.render.knife.KnifeSounds;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Inspect", category = ModuleCategory.VISUALS)
public final class InspectModule extends Module {
    private static final long LIGHT_COOLDOWN_MS = 300L, HEAVY_COOLDOWN_MS = 800L;
    private static final float[] LIGHT_DIRECTION = {0.36f, 0.82f, 0.45f};
    private static final long FLASH_MS = 55L;
    private static final String SOUND_SLASH = "csgo_knife.slash", SOUND_HIT = "csgo_knife.hit", SOUND_HIT_WALL = "csgo_knife.hitwall", SOUND_STAB = "csgo_knife.stab";

    private static InspectModule instance;

    private final ModeProperty<Knife> knife = new ModeProperty<>("Weapon", Knife.KARAMBIT);

    private final FilteredModeProperty<KnifeSkin> skin = new FilteredModeProperty<>("Skin", KnifeSkin.VANILLA, () -> knife.getValue().gun == null,
            value -> !value.newerOnly || knife.getValue().hasNewerSkins, () -> knife.getValue().hasNewerSkins);
    private final Map<Knife, FilteredModeProperty<GunSkin>> gunSkins = new EnumMap<>(Knife.class);

    private final FilteredModeProperty<GunSkin> ak47Skin = gunSkinSetting(Knife.AK47);
    private final FilteredModeProperty<GunSkin> m4a4Skin = gunSkinSetting(Knife.M4A4);
    private final FilteredModeProperty<GunSkin> m4a1sSkin = gunSkinSetting(Knife.M4A1S);
    private final FilteredModeProperty<GunSkin> awpSkin = gunSkinSetting(Knife.AWP);
    private final FilteredModeProperty<GunSkin> deagleSkin = gunSkinSetting(Knife.DEAGLE);
    private final FilteredModeProperty<GunSkin> glockSkin = gunSkinSetting(Knife.GLOCK);
    private final FilteredModeProperty<GunSkin> uspsSkin = gunSkinSetting(Knife.USPS);
    private final FilteredModeProperty<GunSkin> p2000Skin = gunSkinSetting(Knife.P2000);
    private final FilteredModeProperty<GunSkin> p250Skin = gunSkinSetting(Knife.P250);
    private final FilteredModeProperty<GunSkin> fivesevenSkin = gunSkinSetting(Knife.FIVE_SEVEN);
    private final FilteredModeProperty<GunSkin> tec9Skin = gunSkinSetting(Knife.TEC9);
    private final FilteredModeProperty<GunSkin> cz75Skin = gunSkinSetting(Knife.CZ75);
    private final FilteredModeProperty<GunSkin> mp9Skin = gunSkinSetting(Knife.MP9);
    private final FilteredModeProperty<GunSkin> mp7Skin = gunSkinSetting(Knife.MP7);
    private final FilteredModeProperty<GunSkin> ump45Skin = gunSkinSetting(Knife.UMP45);
    private final FilteredModeProperty<GunSkin> bizonSkin = gunSkinSetting(Knife.BIZON);
    private final FilteredModeProperty<GunSkin> famasSkin = gunSkinSetting(Knife.FAMAS);
    private final FilteredModeProperty<GunSkin> galilSkin = gunSkinSetting(Knife.GALIL);
    private final FilteredModeProperty<GunSkin> sg553Skin = gunSkinSetting(Knife.SG553);
    private final FilteredModeProperty<GunSkin> ssg08Skin = gunSkinSetting(Knife.SSG08);
    private final FilteredModeProperty<GunSkin> scar20Skin = gunSkinSetting(Knife.SCAR20);
    private final FilteredModeProperty<GunSkin> g3sg1Skin = gunSkinSetting(Knife.G3SG1);
    private final FilteredModeProperty<GunSkin> novaSkin = gunSkinSetting(Knife.NOVA);
    private final FilteredModeProperty<GunSkin> xm1014Skin = gunSkinSetting(Knife.XM1014);
    private final FilteredModeProperty<GunSkin> mag7Skin = gunSkinSetting(Knife.MAG7);
    private final Property<Boolean> clientColor = new Property<>("Client Color", false);
    private final ModeProperty<InspectKey> inspectKey = new ModeProperty<>("Inspect Key", InspectKey.F);
    private final ModeProperty<InspectKey> reloadKey = new ModeProperty<>("Reload Key", InspectKey.R, () -> knife.getValue().gun != null);
    private final Property<Boolean> showArm = new Property<>("Show Arm", true);
    private final Property<Boolean> emptyHand = new Property<>("Empty Hand", false);
    private final Property<Boolean> playSounds = new Property<>("Sounds", true);
    private final NumberProperty soundVolume = new NumberProperty("Sound Volume", 100, playSounds::getValue, 0, 100, 5);
    private final NumberProperty fov = new NumberProperty("Viewmodel FOV", 60, 40, 90, 1);
    private final NumberProperty offsetX = new NumberProperty("Offset X", 1, -6, 6, 0.5);
    private final NumberProperty offsetY = new NumberProperty("Offset Y", 1, -6, 6, 0.5);
    private final NumberProperty offsetZ = new NumberProperty("Offset Z", -1, -6, 6, 0.5);

    private final Map<Knife, KnifeModel> models = new HashMap<>();
    private final List<Knife> failed = new ArrayList<>();
    private final KnifeRenderer renderer = new KnifeRenderer();
    private KnifeSounds sounds;

    private Knife activeKnife;
    private KnifeModel model;
    private KnifeAnimator animator;
    private long lastFrame;
    private long nextSwing;
    private long lastAttack;
    private boolean alternate;
    private int inspectIndex;
    private boolean renderFailed;

    private EntityPlayerSP equippedPlayer;
    private int equippedSlot = -1;
    private boolean wasSwinging;
    private int lastSwingProgress;
    private boolean wasUsing;

    private long nextShot;
    private int ammo;
    private long flashUntil;
    private float flashAngle;
    private int scopeLevel;
    private int shellStage;
    private long shellStepAt;
    private long scopeResumeAt;

    public InspectModule() {
        instance = this;
    }

    private FilteredModeProperty<GunSkin> gunSkinSetting(final Knife gun) {
        final FilteredModeProperty<GunSkin> setting = new FilteredModeProperty<>(gun + " Skin", GunSkin.DEFAULT, () -> knife.getValue() == gun, value -> value.gun == null || value.gun == gun, () -> gun);
        gunSkins.put(gun, setting);
        return setting;
    }

    private GunSkin gunSkin() {
        final FilteredModeProperty<GunSkin> setting = gunSkins.get(knife.getValue());
        return setting == null ? GunSkin.DEFAULT : setting.getValue();
    }

    @Override
    public void onEnable() {
        equippedSlot = -1;
        renderFailed = false;
        failed.clear();
    }

    @Override
    public void onDisable() {
        if (sounds != null) sounds.stop();
        setScope(0);
    }

    public static boolean renderFirstPerson(final float partialTicks) {
        return instance != null && instance.isEnabled() && instance.render(partialTicks);
    }

    public static float scopedFov(final float fov) {
        return instance != null ? fov / instance.scopeZoom() : fov;
    }

    public static float scopedSensitivity() {
        return instance != null ? 1f / instance.scopeZoom() : 1f;
    }

    private int activeScope() {
        if (scopeLevel == 0 || !isEnabled() || mc.gameSettings.thirdPersonView != 0 || System.currentTimeMillis() < scopeResumeAt || !holdsKnife(mc.thePlayer)) return 0;
        final Gun gun = knife.getValue().gun;
        return gun != null && gun.scoped() ? Math.min(scopeLevel, gun.zoom.length - 1) : 0;
    }

    private float scopeZoom() {
        final int level = activeScope();
        return level == 0 ? 1f : knife.getValue().gun.zoom[level];
    }

    private void setScope(final int level) {
        scopeLevel = level;
        if (mc.renderGlobal != null) mc.renderGlobal.setDisplayListEntitiesDirty();
    }

    private boolean render(final float partialTicks) {
        final EntityPlayerSP player = mc.thePlayer;
        if (renderFailed || !holdsKnife(player)) {
            equippedSlot = -1;
            setScope(0);
            return false;
        }
        if (!loadKnife()) return false;

        if (player != equippedPlayer || player.inventory.currentItem != equippedSlot) {
            equippedPlayer = player;
            equippedSlot = player.inventory.currentItem;
            playDraw();
        }

        final Gun gun = knife.getValue().gun;
        if (gun != null) {
            updateShellReload();
            if (ammo <= 0 && shellStage == 0 && System.currentTimeMillis() >= nextShot) reload();
            if (gun.automatic && mc.currentScreen == null && mc.gameSettings.keyBindAttack.isKeyDown()) fire();
        }

        final long now = System.nanoTime();
        final float seconds = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0E9f);
        lastFrame = now;
        final float[] pose = animator.update(seconds);

        if (gun == null && !model.skins.containsKey(skin.getValue().id)) skin.setValue(KnifeSkin.VANILLA);
        final String wantedSkin = gun != null ? gunSkin().id : skin.getValue().id;
        final boolean hasSkin = model.skins.containsKey(wantedSkin);
        final KnifeModel.Skin chosen = model.skins.get(hasSkin ? wantedSkin : GunSkin.DEFAULT.id);
        setSuffix(knife.getValue() + " " + (!hasSkin ? GunSkin.DEFAULT : gun != null ? gunSkin() : skin.getValue()));

        if (activeScope() > 0) return true;

        final float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        final float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        final float[] offset = {offsetX.getValue().floatValue(), offsetY.getValue().floatValue(), offsetZ.getValue().floatValue()};

        int armTexture = 0;
        if (showArm.getValue() && !player.isInvisible()) {
            final ResourceLocation location = player.getLocationSkin();
            mc.getTextureManager().bindTexture(location);
            final ITextureObject texture = mc.getTextureManager().getTexture(location);
            if (texture != null) armTexture = texture.getGlTextureId();
        }

        try {
            final float flash = Math.max(0f, (flashUntil - System.currentTimeMillis()) / (float) FLASH_MS);
            renderer.render(model, chosen, pose, mc.displayWidth / (float) mc.displayHeight, fov.getValue().floatValue() * (gun != null ? gun.fovScale : 1f), offset,
                    brightness(player), toView(LIGHT_DIRECTION, yaw, pitch), toView(new float[]{0f, 1f, 0f}, yaw, pitch),
                    armTexture, "slim".equals(player.getSkinType()), clientColor.getValue() ? bladeColors() : null, flash, flashAngle);
        } catch (final IOException | RuntimeException exception) {
            System.err.println("Inspect: could not draw the knife: " + exception);
            setSuffix("Failed");
            renderFailed = true;
            return false;
        }
        return true;
    }

    private boolean loadKnife() {
        final Knife wanted = knife.getValue();
        if (wanted == activeKnife && animator != null) return true;
        if (failed.contains(wanted)) return false;
        KnifeModel loaded = models.get(wanted);
        if (loaded == null) {
            try {
                loaded = KnifeModel.load(new File(mc.mcDataDir, "Tempest/knives"), wanted.file);
            } catch (final IOException exception) {
                System.err.println("Inspect: could not load " + wanted + ": " + exception);
                failed.add(wanted);
                setSuffix("Missing Files");
                return false;
            }
            models.put(wanted, loaded);
        }
        activeKnife = wanted;
        model = loaded;
        animator = new KnifeAnimator(loaded, this::animationEvent);
        inspectIndex = 0;
        equippedSlot = -1;
        setScope(0);
        return true;
    }

    private boolean holdsKnife(final EntityPlayerSP player) {
        if (player == null) return false;
        final ItemStack held = player.getHeldItem();
        return held == null ? emptyHand.getValue() : held.getItem() instanceof ItemSword;
    }

    private static float[] bladeColors() {
        final Color now = ColorProcess.getColor();
        final Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        return new float[]{
                now.getRed() / 255f, now.getGreen() / 255f, now.getBlue() / 255f,
                opposite(first.getRed(), second.getRed(), now.getRed()),
                opposite(first.getGreen(), second.getGreen(), now.getGreen()),
                opposite(first.getBlue(), second.getBlue(), now.getBlue())};
    }

    private static float opposite(final int first, final int second, final int now) {
        return Math.max(0, Math.min(255, first + second - now)) / 255f;
    }

    private void animationEvent(final String name) {
        if (name.equals("@muzzle")) {
            flashUntil = System.currentTimeMillis() + FLASH_MS;
            flashAngle = (float) (Math.random() * Math.PI);
        } else {
            playSound(name);
        }
    }

    private void playSound(final String name) {
        if (!playSounds.getValue() || mc.gameSettings.thirdPersonView != 0) return;
        if (sounds == null) sounds = new KnifeSounds(new File(mc.mcDataDir, "Tempest/knives/sounds"));
        sounds.play(name, soundVolume.getValue().floatValue() / 100f * mc.gameSettings.getSoundLevel(SoundCategory.MASTER));
    }

    private static boolean lookingAt(final MovingObjectPosition.MovingObjectType type) {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == type;
    }

    private void playDraw() {
        animator.play(animator.has("draw2") && Math.random() < 0.5 ? "draw2" : "draw");
        nextSwing = 0L;
        nextShot = 0L;
        shellStage = 0;
        setScope(0);
        scopeResumeAt = 0L;
        if (knife.getValue().gun != null) ammo = knife.getValue().gun.magazine;
    }

    private void fire() {
        final Gun gun = knife.getValue().gun;
        if (animator == null || System.currentTimeMillis() < nextShot || ammo <= 0) return;

        shellStage = 0;
        nextShot = System.currentTimeMillis() + gun.delayMs;
        ammo--;
        final int variant = 1 + (int) (Math.random() * 3);
        animator.play(animator.has("shoot" + variant) ? "shoot" + variant : "shoot1");
        if (gun.fireSound != null) playSound(gun.fireSound);

        if (gun.scoped() && !gun.automatic && scopeLevel > 0) scopeResumeAt = nextShot;
    }

    private void reload() {
        final Gun gun = knife.getValue().gun;
        if (animator == null || shellStage != 0 || System.currentTimeMillis() < nextShot || ammo >= gun.magazine) return;
        if (animator.has("reload_start")) {
            animator.play("reload_start");
            shellStage = 1;
            shellStepAt = System.currentTimeMillis() + (long) (animator.duration("reload_start") * 1000f);
            setScope(0);
            return;
        }

        if (!animator.has("reload")) {
            ammo = gun.magazine;
            return;
        }
        animator.play("reload");
        nextShot = System.currentTimeMillis() + (long) (animator.duration("reload") * 1000f);
        ammo = gun.magazine;
        setScope(0);
    }

    private void updateShellReload() {
        if (shellStage == 0 || animator == null || System.currentTimeMillis() < shellStepAt) return;
        final Gun gun = knife.getValue().gun;
        if (gun != null && ammo < gun.magazine) {
            animator.play("insert");
            ammo++;
            shellStepAt = System.currentTimeMillis() + (long) (animator.duration("insert") * 1000f);
        } else {
            shellStage = 0;
            animator.play("reload_finish");
            nextShot = System.currentTimeMillis() + (long) (animator.duration("reload_finish") * 1000f);
        }
    }

    private void cycleScope() {
        if (System.currentTimeMillis() < nextShot) return;
        final Gun gun = knife.getValue().gun;
        setScope((scopeLevel + 1) % gun.zoom.length);
        playSound(gun.zoomSound);
    }

    private void swing(final boolean hit) {
        if (knife.getValue().gun != null) {
            fire();
            return;
        }
        if (animator == null || System.currentTimeMillis() < nextSwing) return;
        nextSwing = System.currentTimeMillis() + LIGHT_COOLDOWN_MS;
        alternate = !alternate;
        animator.play((hit ? "light_hit" : "light_miss") + (alternate ? "1" : "2"));
        playSound(hit ? SOUND_HIT : lookingAt(MovingObjectPosition.MovingObjectType.BLOCK) ? SOUND_HIT_WALL : SOUND_SLASH);
    }

    @EventLink
    public final Listener<AttackEvent> onAttack = event -> {
        if (!holdsKnife(mc.thePlayer)) return;
        lastAttack = System.currentTimeMillis();
        swing(true);
    };

    @EventLink
    public final Listener<MotionEvent> onMotion = event -> {
        if (!event.isPre() || animator == null) return;
        final EntityPlayerSP player = mc.thePlayer;
        if (!holdsKnife(player)) {
            wasSwinging = false;
            wasUsing = false;
            return;
        }

        final boolean swinging = player.isSwingInProgress;
        if (swinging && (!wasSwinging || player.swingProgressInt < lastSwingProgress)) {
            swing(System.currentTimeMillis() - lastAttack < 150L);
        }
        wasSwinging = swinging;
        lastSwingProgress = player.swingProgressInt;

        final boolean using = mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown();
        final Gun gun = knife.getValue().gun;
        if (gun != null) {
            if (using && !wasUsing && gun.scoped()) cycleScope();
        } else if (using && !wasUsing && System.currentTimeMillis() >= nextSwing) {
            nextSwing = System.currentTimeMillis() + HEAVY_COOLDOWN_MS;
            animator.play("heavy_miss1");
            playSound(lookingAt(MovingObjectPosition.MovingObjectType.ENTITY) ? SOUND_STAB : lookingAt(MovingObjectPosition.MovingObjectType.BLOCK) ? SOUND_HIT_WALL : SOUND_SLASH);
        }
        wasUsing = using;
    };

    @EventLink
    public final Listener<KeyPressEvent> onKey = event -> {
        if (animator == null || !holdsKnife(mc.thePlayer)) return;
        if (knife.getValue().gun != null && event.getKey() == reloadKey.getValue().code) {
            reload();
            return;
        }
        if (event.getKey() != inspectKey.getValue().code || (knife.getValue().gun != null && (shellStage != 0 || System.currentTimeMillis() < nextShot))) return;
        inspectIndex++;
        final String next = "lookat0" + inspectIndex;
        if (animator.has(next)) {
            animator.play(next);
        } else {
            inspectIndex = 1;
            animator.play("lookat01");
        }
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        if (activeScope() > 0) drawScope();
    };

    private static void drawScope() {
        final ScaledResolution resolution = new ScaledResolution(mc);
        final float w = resolution.getScaledWidth(), h = resolution.getScaledHeight();
        final float cx = w / 2f, cy = h / 2f, radius = h * 0.47f, outer = w + h;
        final int previousProgram = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GL20.glUseProgram(0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int k = 0; k <= 96; k++) {
            final double angle = k * Math.PI * 2.0 / 96.0;
            final float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
            GL11.glColor4f(0f, 0f, 0f, 1f);
            GL11.glVertex2f(cx + x * radius, cy + y * radius);
            GL11.glVertex2f(cx + x * outer, cy + y * outer);
        }
        GL11.glEnd();
        final float line = Math.max(0.5f, 1f / resolution.getScaleFactor());
        GL11.glColor4f(0f, 0f, 0f, 0.9f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(0f, cy - line);
        GL11.glVertex2f(w, cy - line);
        GL11.glVertex2f(w, cy + line);
        GL11.glVertex2f(0f, cy + line);
        GL11.glVertex2f(cx - line, 0f);
        GL11.glVertex2f(cx + line, 0f);
        GL11.glVertex2f(cx + line, h);
        GL11.glVertex2f(cx - line, h);
        GL11.glEnd();
        GL11.glPopAttrib();
        org.lwjgl.opengl.GL20.glUseProgram(previousProgram);
    }

    private static float brightness(final EntityPlayerSP player) {
        final float raw = mc.theWorld.getLightBrightness(new BlockPos(player.posX, player.posY + player.getEyeHeight(), player.posZ));
        return Math.min(1f, 0.28f + 0.72f * (float) Math.sqrt(raw));
    }

    private static float[] toView(final float[] world, final float yaw, final float pitch) {
        final double y = Math.toRadians(yaw + 180f), p = Math.toRadians(pitch);
        final double x1 = world[0] * Math.cos(y) + world[2] * Math.sin(y);
        final double z1 = -world[0] * Math.sin(y) + world[2] * Math.cos(y);
        final double y2 = world[1] * Math.cos(p) - z1 * Math.sin(p);
        final double z2 = world[1] * Math.sin(p) + z1 * Math.cos(p);
        final double length = Math.sqrt(x1 * x1 + y2 * y2 + z2 * z2);
        return new float[]{(float) (x1 / length), (float) (y2 / length), (float) (z2 / length)};
    }

    private static final class Gun {
        private static final float[] NO_SCOPE = {1f}, AWP_SCOPE = {1f, 2.25f, 9f}, SNIPER_SCOPE = {1f, 2.25f, 6f}, RIFLE_SCOPE = {1f, 2f};

        final String fireSound, zoomSound;
        final long delayMs;
        final int magazine;
        final boolean automatic;
        final float[] zoom;
        final float fovScale;

        Gun(final String fireSound, final long delayMs, final int magazine, final boolean automatic) {
            this(fireSound, delayMs, magazine, automatic, NO_SCOPE, null);
        }

        Gun(final String fireSound, final long delayMs, final int magazine, final boolean automatic, final float[] zoom, final String zoomSound) {
            this.fireSound = fireSound;
            this.delayMs = delayMs;
            this.magazine = magazine;
            this.automatic = automatic;
            this.zoom = zoom;
            this.zoomSound = zoomSound;
            this.fovScale = (zoom == AWP_SCOPE ? 45f : 50f) / 60f;
        }

        boolean scoped() {
            return zoom.length > 1;
        }
    }

    private enum Knife {
        KARAMBIT("Karambit", "karambit", null, true),
        BUTTERFLY("Butterfly", "butterfly", null),
        BAYONET("Bayonet", "bayonet", null, true),
        M9("M9 Bayonet", "m9", null, true),
        FLIP("Flip Knife", "flip", null, true),
        GUT("Gut Knife", "gut", null, true),
        HUNTSMAN("Huntsman", "huntsman", null),
        FALCHION("Falchion", "falchion", null),
        BOWIE("Bowie", "bowie", null),
        DAGGERS("Shadow Daggers", "daggers", null),
        AK47("AK-47", "ak47", new Gun("gun.ak47.fire", 100L, 30, true)),
        M4A4("M4A4", "m4a4", new Gun("gun.m4a4.fire", 90L, 30, true)),
        M4A1S("M4A1-S", "m4a1s", new Gun("gun.m4a1s.fire", 100L, 20, true)),
        AUG("AUG", "aug", new Gun("gun.aug.fire", 90L, 30, true, Gun.RIFLE_SCOPE, "gun.aug.zoom")),
        SG553("SG 553", "sg553", new Gun("gun.sg553.fire", 90L, 30, true, Gun.RIFLE_SCOPE, "gun.sg553.zoom")),
        FAMAS("FAMAS", "famas", new Gun("gun.famas.fire", 90L, 25, true)),
        GALIL("Galil AR", "galil", new Gun("gun.galil.fire", 90L, 35, true)),
        AWP("AWP", "awp", new Gun("gun.awp.fire", 1450L, 5, false, Gun.AWP_SCOPE, "gun.awp.zoom")),
        SSG08("SSG 08", "ssg08", new Gun("gun.ssg08.fire", 1250L, 10, false, Gun.SNIPER_SCOPE, "gun.awp.zoom")),
        SCAR20("SCAR-20", "scar20", new Gun("gun.scar20.fire", 250L, 20, true, Gun.SNIPER_SCOPE, "gun.awp.zoom")),
        G3SG1("G3SG1", "g3sg1", new Gun("gun.g3sg1.fire", 250L, 20, true, Gun.SNIPER_SCOPE, "gun.awp.zoom")),
        DEAGLE("Desert Eagle", "deagle", new Gun("gun.deagle.fire", 225L, 7, false)),
        GLOCK("Glock-18", "glock", new Gun("gun.glock.fire", 150L, 20, false)),
        USPS("USP-S", "usps", new Gun("gun.usps.fire", 170L, 12, false)),
        P2000("P2000", "p2000", new Gun("gun.p2000.fire", 170L, 13, false)),
        P250("P250", "p250", new Gun("gun.p250.fire", 150L, 13, false)),
        FIVE_SEVEN("Five-SeveN", "fiveseven", new Gun("gun.fiveseven.fire", 150L, 20, false)),
        TEC9("Tec-9", "tec9", new Gun("gun.tec9.fire", 120L, 18, false)),
        CZ75("CZ75-Auto", "cz75", new Gun("gun.cz75.fire", 100L, 12, true)),
        MP9("MP9", "mp9", new Gun("gun.mp9.fire", 70L, 30, true)),
        MP7("MP7", "mp7", new Gun("gun.mp7.fire", 80L, 30, true)),
        UMP45("UMP-45", "ump45", new Gun("gun.ump45.fire", 90L, 25, true)),
        BIZON("PP-Bizon", "bizon", new Gun("gun.bizon.fire", 80L, 64, true)),
        NOVA("Nova", "nova", new Gun("gun.nova.fire", 880L, 8, false)),
        XM1014("XM1014", "xm1014", new Gun("gun.xm1014.fire", 350L, 7, true)),
        MAG7("MAG-7", "mag7", new Gun(null, 850L, 5, false)),
        ZEUS("Zeus x27", "zeus", new Gun("gun.zeus.fire", 30000L, 1, false));

        private final String label, file;
        private final Gun gun;
        private final boolean hasNewerSkins;

        Knife(final String label, final String file, final Gun gun) {
            this(label, file, gun, false);
        }

        Knife(final String label, final String file, final Gun gun, final boolean hasNewerSkins) {
            this.label = label;
            this.file = file;
            this.gun = gun;
            this.hasNewerSkins = hasNewerSkins;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum KnifeSkin {
        VANILLA("Vanilla", "vanilla"),
        AUTOTRONIC("Autotronic", "autotronic", true),
        BLACK_LAMINATE("Black Laminate", "black_laminate", true),
        CRIMSON_WEB("Crimson Web", "crimsonweb"),
        DAMASCUS("Damascus Steel", "dam"),
        FADE("Fade", "fade"),
        FREEHAND("Freehand", "freehand"),
        GAMMA_DOPPLER("Gamma Doppler", "gamma_doppler"),
        LORE("Lore", "lore", true),
        MARBLE_FADE("Marble Fade", "marblefade"),
        SLAUGHTER("Slaughter", "slaughter"),
        TIGER_TOOTH("Tiger Tooth", "tiger");

        private final String label, id;
        private final boolean newerOnly;

        KnifeSkin(final String label, final String id) {
            this(label, id, false);
        }

        KnifeSkin(final String label, final String id, final boolean newerOnly) {
            this.label = label;
            this.id = id;
            this.newerOnly = newerOnly;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum GunSkin {
        DEFAULT(null, "Default", "vanilla"),
        AK47_REDLINE(Knife.AK47, "Redline", "redline"),
        AK47_VULCAN(Knife.AK47, "Vulcan", "vulcan"),
        AK47_FIRE_SERPENT(Knife.AK47, "Fire Serpent", "fire_serpent"),
        AK47_ASIIMOV(Knife.AK47, "Asiimov", "asiimov"),
        AK47_CASE_HARDENED(Knife.AK47, "Case Hardened", "case_hardened"),
        AK47_BLOODSPORT(Knife.AK47, "Bloodsport", "bloodsport"),
        AK47_THE_EMPRESS(Knife.AK47, "The Empress", "the_empress"),
        AK47_NEON_RIDER(Knife.AK47, "Neon Rider", "neon_rider"),
        AK47_WILD_LOTUS(Knife.AK47, "Wild Lotus", "wild_lotus"),
        AK47_AQUAMARINE_REVENGE(Knife.AK47, "Aquamarine Revenge", "aquamarine_revenge"),
        M4A4_ASIIMOV(Knife.M4A4, "Asiimov", "asiimov"),
        M4A4_HOWL(Knife.M4A4, "Howl", "howl"),
        M4A4_DRAGON_KING(Knife.M4A4, "Dragon King", "dragon_king"),
        M4A4_NEO_NOIR(Knife.M4A4, "Neo-Noir", "neo_noir"),
        M4A4_THE_EMPEROR(Knife.M4A4, "The Emperor", "the_emperor"),
        M4A4_DESOLATE_SPACE(Knife.M4A4, "Desolate Space", "desolate_space"),
        M4A4_BUZZ_KILL(Knife.M4A4, "Buzz Kill", "buzz_kill"),
        M4A4_HELLFIRE(Knife.M4A4, "Hellfire", "hellfire"),
        M4A4_POSEIDON(Knife.M4A4, "Poseidon", "poseidon"),
        M4A4_TEMUKAU(Knife.M4A4, "Temukau", "temukau"),
        M4A1S_PRINTSTREAM(Knife.M4A1S, "Printstream", "printstream"),
        M4A1S_HYPER_BEAST(Knife.M4A1S, "Hyper Beast", "hyper_beast"),
        M4A1S_GOLDEN_COIL(Knife.M4A1S, "Golden Coil", "golden_coil"),
        M4A1S_ICARUS_FELL(Knife.M4A1S, "Icarus Fell", "icarus_fell"),
        M4A1S_MASTER_PIECE(Knife.M4A1S, "Master Piece", "master_piece"),
        M4A1S_CYREX(Knife.M4A1S, "Cyrex", "cyrex"),
        M4A1S_PLAYER_TWO(Knife.M4A1S, "Player Two", "player_two"),
        M4A1S_WELCOME_TO_THE_JUNGLE(Knife.M4A1S, "Welcome To The Jungle", "welcome_to_the_jungle"),
        M4A1S_BLUE_PHOSPHOR(Knife.M4A1S, "Blue Phosphor", "blue_phosphor"),
        M4A1S_NIGHTMARE(Knife.M4A1S, "Nightmare", "nightmare"),
        AWP_DRAGON_LORE(Knife.AWP, "Dragon Lore", "dragonlore"),
        AWP_ASIIMOV(Knife.AWP, "Asiimov", "asiimov"),
        AWP_MEDUSA(Knife.AWP, "Medusa", "medusa"),
        AWP_GUNGNIR(Knife.AWP, "Gungnir", "gungnir"),
        AWP_HYPER_BEAST(Knife.AWP, "Hyper Beast", "hyper_beast"),
        AWP_NEO_NOIR(Knife.AWP, "Neo-Noir", "neo_noir"),
        AWP_LIGHTNING_STRIKE(Knife.AWP, "Lightning Strike", "lightning_strike"),
        AWP_WILDFIRE(Knife.AWP, "Wildfire", "wildfire"),
        AWP_FADE(Knife.AWP, "Fade", "fade"),
        AWP_CONTAINMENT_BREACH(Knife.AWP, "Containment Breach", "containment_breach"),
        DEAGLE_BLAZE(Knife.DEAGLE, "Blaze", "blaze"),
        DEAGLE_PRINTSTREAM(Knife.DEAGLE, "Printstream", "printstream"),
        DEAGLE_KUMICHO_DRAGON(Knife.DEAGLE, "Kumicho Dragon", "kumicho_dragon"),
        DEAGLE_CODE_RED(Knife.DEAGLE, "Code Red", "code_red"),
        DEAGLE_GOLDEN_KOI(Knife.DEAGLE, "Golden Koi", "golden_koi"),
        DEAGLE_MECHA_INDUSTRIES(Knife.DEAGLE, "Mecha Industries", "mecha_industries"),
        DEAGLE_HYPNOTIC(Knife.DEAGLE, "Hypnotic", "hypnotic"),
        DEAGLE_OCEAN_DRIVE(Knife.DEAGLE, "Ocean Drive", "ocean_drive"),
        DEAGLE_CONSPIRACY(Knife.DEAGLE, "Conspiracy", "conspiracy"),
        DEAGLE_CRIMSON_WEB(Knife.DEAGLE, "Crimson Web", "crimson_web"),
        GLOCK_FADE(Knife.GLOCK, "Fade", "fade"),
        GLOCK_WATER_ELEMENTAL(Knife.GLOCK, "Water Elemental", "water_elemental"),
        GLOCK_GAMMA_DOPPLER(Knife.GLOCK, "Gamma Doppler", "gamma_doppler"),
        GLOCK_BULLET_QUEEN(Knife.GLOCK, "Bullet Queen", "bullet_queen"),
        GLOCK_NEO_NOIR(Knife.GLOCK, "Neo-Noir", "neo_noir"),
        GLOCK_WASTELAND_REBEL(Knife.GLOCK, "Wasteland Rebel", "wasteland_rebel"),
        GLOCK_DRAGON_TATTOO(Knife.GLOCK, "Dragon Tattoo", "dragon_tattoo"),
        GLOCK_TWILIGHT_GALAXY(Knife.GLOCK, "Twilight Galaxy", "twilight_galaxy"),
        GLOCK_VOGUE(Knife.GLOCK, "Vogue", "vogue"),
        GLOCK_REACTOR(Knife.GLOCK, "Reactor", "reactor"),
        USPS_KILL_CONFIRMED(Knife.USPS, "Kill Confirmed", "kill_confirmed"),
        USPS_PRINTSTREAM(Knife.USPS, "Printstream", "printstream"),
        USPS_NEO_NOIR(Knife.USPS, "Neo-Noir", "neo_noir"),
        USPS_ORION(Knife.USPS, "Orion", "orion"),
        USPS_THE_TRAITOR(Knife.USPS, "The Traitor", "the_traitor"),
        USPS_CORTEX(Knife.USPS, "Cortex", "cortex"),
        USPS_CAIMAN(Knife.USPS, "Caiman", "caiman"),
        USPS_GUARDIAN(Knife.USPS, "Guardian", "guardian"),
        USPS_MONSTER_MASHUP(Knife.USPS, "Monster Mashup", "monster_mashup"),
        USPS_SERUM(Knife.USPS, "Serum", "serum"),
        P2000_FIRE_ELEMENTAL(Knife.P2000, "Fire Elemental", "fire_elemental"),
        P2000_WICKED_SICK(Knife.P2000, "Wicked Sick", "wicked_sick"),
        P2000_OCEAN_FOAM(Knife.P2000, "Ocean Foam", "ocean_foam"),
        P2000_IMPERIAL_DRAGON(Knife.P2000, "Imperial Dragon", "imperial_dragon"),
        P2000_CORTICERA(Knife.P2000, "Corticera", "corticera"),
        P2000_AMBER_FADE(Knife.P2000, "Amber Fade", "amber_fade"),
        P2000_CHAINMAIL(Knife.P2000, "Chainmail", "chainmail"),
        P2000_SCORPION(Knife.P2000, "Scorpion", "scorpion"),
        P2000_HANDGUN(Knife.P2000, "Handgun", "handgun"),
        P2000_ACID_ETCHED(Knife.P2000, "Acid Etched", "acid_etched"),
        P250_SEE_YA_LATER(Knife.P250, "See Ya Later", "see_ya_later"),
        P250_ASIIMOV(Knife.P250, "Asiimov", "asiimov"),
        P250_MUERTOS(Knife.P250, "Muertos", "muertos"),
        P250_MEHNDI(Knife.P250, "Mehndi", "mehndi"),
        P250_CARTEL(Knife.P250, "Cartel", "cartel"),
        P250_NUCLEAR_THREAT(Knife.P250, "Nuclear Threat", "nuclear_threat"),
        P250_SUPERNOVA(Knife.P250, "Supernova", "supernova"),
        P250_UNDERTOW(Knife.P250, "Undertow", "undertow"),
        P250_VISIONS(Knife.P250, "Visions", "visions"),
        P250_WINGSHOT(Knife.P250, "Wingshot", "wingshot"),
        FIVE_SEVEN_HYPER_BEAST(Knife.FIVE_SEVEN, "Hyper Beast", "hyper_beast"),
        FIVE_SEVEN_CASE_HARDENED(Knife.FIVE_SEVEN, "Case Hardened", "case_hardened"),
        FIVE_SEVEN_MONKEY_BUSINESS(Knife.FIVE_SEVEN, "Monkey Business", "monkey_business"),
        FIVE_SEVEN_ANGRY_MOB(Knife.FIVE_SEVEN, "Angry Mob", "angry_mob"),
        FIVE_SEVEN_FOWL_PLAY(Knife.FIVE_SEVEN, "Fowl Play", "fowl_play"),
        FIVE_SEVEN_COPPER_GALAXY(Knife.FIVE_SEVEN, "Copper Galaxy", "copper_galaxy"),
        FIVE_SEVEN_NEON_KIMONO(Knife.FIVE_SEVEN, "Neon Kimono", "neon_kimono"),
        FIVE_SEVEN_FAIRY_TALE(Knife.FIVE_SEVEN, "Fairy Tale", "fairy_tale"),
        FIVE_SEVEN_BOOST_PROTOCOL(Knife.FIVE_SEVEN, "Boost Protocol", "boost_protocol"),
        FIVE_SEVEN_RETROBUTION(Knife.FIVE_SEVEN, "Retrobution", "retrobution"),
        TEC9_FUEL_INJECTOR(Knife.TEC9, "Fuel Injector", "fuel_injector"),
        TEC9_DECIMATOR(Knife.TEC9, "Decimator", "decimator"),
        TEC9_NUCLEAR_THREAT(Knife.TEC9, "Nuclear Threat", "nuclear_threat"),
        TEC9_TITANIUM_BIT(Knife.TEC9, "Titanium Bit", "titanium_bit"),
        TEC9_AVALANCHE(Knife.TEC9, "Avalanche", "avalanche"),
        TEC9_BAMBOOZLE(Knife.TEC9, "Bamboozle", "bamboozle"),
        TEC9_RED_QUARTZ(Knife.TEC9, "Red Quartz", "red_quartz"),
        TEC9_RE_ENTRY(Knife.TEC9, "Re-Entry", "re_entry"),
        TEC9_ISAAC(Knife.TEC9, "Isaac", "isaac"),
        TEC9_TOXIC(Knife.TEC9, "Toxic", "toxic"),
        CZ75_VICTORIA(Knife.CZ75, "Victoria", "victoria"),
        CZ75_XIANGLIU(Knife.CZ75, "Xiangliu", "xiangliu"),
        CZ75_YELLOW_JACKET(Knife.CZ75, "Yellow Jacket", "yellow_jacket"),
        CZ75_RED_ASTOR(Knife.CZ75, "Red Astor", "red_astor"),
        CZ75_TIGRIS(Knife.CZ75, "Tigris", "tigris"),
        CZ75_THE_FUSCHIA_IS_NOW(Knife.CZ75, "The Fuschia Is Now", "the_fuschia_is_now"),
        CZ75_EMERALD(Knife.CZ75, "Emerald", "emerald"),
        CZ75_TACTICAT(Knife.CZ75, "Tacticat", "tacticat"),
        CZ75_CHALICE(Knife.CZ75, "Chalice", "chalice"),
        CZ75_POLE_POSITION(Knife.CZ75, "Pole Position", "pole_position"),
        MP9_STARLIGHT_PROTECTOR(Knife.MP9, "Starlight Protector", "starlight_protector"),
        MP9_HYDRA(Knife.MP9, "Hydra", "hydra"),
        MP9_WILD_LILY(Knife.MP9, "Wild Lily", "wild_lily"),
        MP9_BULLDOZER(Knife.MP9, "Bulldozer", "bulldozer"),
        MP9_HYPNOTIC(Knife.MP9, "Hypnotic", "hypnotic"),
        MP9_ROSE_IRON(Knife.MP9, "Rose Iron", "rose_iron"),
        MP9_FOOD_CHAIN(Knife.MP9, "Food Chain", "food_chain"),
        MP9_GOO(Knife.MP9, "Goo", "goo"),
        MP9_AIRLOCK(Knife.MP9, "Airlock", "airlock"),
        MP9_MOUNT_FUJI(Knife.MP9, "Mount Fuji", "mount_fuji"),
        MP7_BLOODSPORT(Knife.MP7, "Bloodsport", "bloodsport"),
        MP7_FADE(Knife.MP7, "Fade", "fade"),
        MP7_NEMESIS(Knife.MP7, "Nemesis", "nemesis"),
        MP7_SPECIAL_DELIVERY(Knife.MP7, "Special Delivery", "special_delivery"),
        MP7_NEON_PLY(Knife.MP7, "Neon Ply", "neon_ply"),
        MP7_IMPIRE(Knife.MP7, "Impire", "impire"),
        MP7_SKULLS(Knife.MP7, "Skulls", "skulls"),
        MP7_ABYSSAL_APPARITION(Knife.MP7, "Abyssal Apparition", "abyssal_apparition"),
        MP7_WHITEOUT(Knife.MP7, "Whiteout", "whiteout"),
        MP7_OCEAN_FOAM(Knife.MP7, "Ocean Foam", "ocean_foam"),
        UMP45_FADE(Knife.UMP45, "Fade", "fade"),
        UMP45_PRIMAL_SABER(Knife.UMP45, "Primal Saber", "primal_saber"),
        UMP45_BLAZE(Knife.UMP45, "Blaze", "blaze"),
        UMP45_MOMENTUM(Knife.UMP45, "Momentum", "momentum"),
        UMP45_MOONRISE(Knife.UMP45, "Moonrise", "moonrise"),
        UMP45_WILD_CHILD(Knife.UMP45, "Wild Child", "wild_child"),
        UMP45_CRIME_SCENE(Knife.UMP45, "Crime Scene", "crime_scene"),
        UMP45_ARCTIC_WOLF(Knife.UMP45, "Arctic Wolf", "arctic_wolf"),
        UMP45_GRAND_PRIX(Knife.UMP45, "Grand Prix", "grand_prix"),
        UMP45_EXPOSURE(Knife.UMP45, "Exposure", "exposure"),
        BIZON_JUDGEMENT_OF_ANUBIS(Knife.BIZON, "Judgement of Anubis", "judgement_of_anubis"),
        BIZON_FUEL_ROD(Knife.BIZON, "Fuel Rod", "fuel_rod"),
        BIZON_HIGH_ROLLER(Knife.BIZON, "High Roller", "high_roller"),
        BIZON_BLUE_STREAK(Knife.BIZON, "Blue Streak", "blue_streak"),
        BIZON_OSIRIS(Knife.BIZON, "Osiris", "osiris"),
        BIZON_ANTIQUE(Knife.BIZON, "Antique", "antique"),
        BIZON_RUNIC(Knife.BIZON, "Runic", "runic"),
        BIZON_SPACE_CAT(Knife.BIZON, "Space Cat", "space_cat"),
        BIZON_EMBARGO(Knife.BIZON, "Embargo", "embargo"),
        BIZON_WATER_SIGIL(Knife.BIZON, "Water Sigil", "water_sigil"),
        FAMAS_ROLL_CAGE(Knife.FAMAS, "Roll Cage", "roll_cage"),
        FAMAS_MECHA_INDUSTRIES(Knife.FAMAS, "Mecha Industries", "mecha_industries"),
        FAMAS_EYE_OF_ATHENA(Knife.FAMAS, "Eye of Athena", "eye_of_athena"),
        FAMAS_AFTERIMAGE(Knife.FAMAS, "Afterimage", "afterimage"),
        FAMAS_DJINN(Knife.FAMAS, "Djinn", "djinn"),
        FAMAS_COMMEMORATION(Knife.FAMAS, "Commemoration", "commemoration"),
        FAMAS_NEURAL_NET(Knife.FAMAS, "Neural Net", "neural_net"),
        FAMAS_RAPID_EYE_MOVEMENT(Knife.FAMAS, "Rapid Eye Movement", "rapid_eye_movement"),
        FAMAS_STYX(Knife.FAMAS, "Styx", "styx"),
        FAMAS_SPITFIRE(Knife.FAMAS, "Spitfire", "spitfire"),
        GALIL_CHATTERBOX(Knife.GALIL, "Chatterbox", "chatterbox"),
        GALIL_SUGAR_RUSH(Knife.GALIL, "Sugar Rush", "sugar_rush"),
        GALIL_CERBERUS(Knife.GALIL, "Cerberus", "cerberus"),
        GALIL_ECO(Knife.GALIL, "Eco", "eco"),
        GALIL_ROCKET_POP(Knife.GALIL, "Rocket Pop", "rocket_pop"),
        GALIL_STONE_COLD(Knife.GALIL, "Stone Cold", "stone_cold"),
        GALIL_AKOBEN(Knife.GALIL, "Akoben", "akoben"),
        GALIL_FIREFIGHT(Knife.GALIL, "Firefight", "firefight"),
        GALIL_CRIMSON_TSUNAMI(Knife.GALIL, "Crimson Tsunami", "crimson_tsunami"),
        GALIL_PHOENIX_BLACKLIGHT(Knife.GALIL, "Phoenix Blacklight", "phoenix_blacklight"),
        SG553_INTEGRALE(Knife.SG553, "Integrale", "integrale"),
        SG553_CYREX(Knife.SG553, "Cyrex", "cyrex"),
        SG553_TIGER_MOTH(Knife.SG553, "Tiger Moth", "tiger_moth"),
        SG553_PULSE(Knife.SG553, "Pulse", "pulse"),
        SG553_HYPNOTIC(Knife.SG553, "Hypnotic", "hypnotic"),
        SG553_PHANTOM(Knife.SG553, "Phantom", "phantom"),
        SG553_ULTRAVIOLET(Knife.SG553, "Ultraviolet", "ultraviolet"),
        SG553_COLONY_IV(Knife.SG553, "Colony IV", "colony_iv"),
        SG553_DANGER_CLOSE(Knife.SG553, "Danger Close", "danger_close"),
        SG553_DARKWING(Knife.SG553, "Darkwing", "darkwing"),
        SSG08_DRAGONFIRE(Knife.SSG08, "Dragonfire", "dragonfire"),
        SSG08_BLOOD_IN_THE_WATER(Knife.SSG08, "Blood in the Water", "blood_in_the_water"),
        SSG08_DEATHS_HEAD(Knife.SSG08, "Death's Head", "deaths_head"),
        SSG08_BIG_IRON(Knife.SSG08, "Big Iron", "big_iron"),
        SSG08_FEVER_DREAM(Knife.SSG08, "Fever Dream", "fever_dream"),
        SSG08_ABYSS(Knife.SSG08, "Abyss", "abyss"),
        SSG08_TURBO_PEEK(Knife.SSG08, "Turbo Peek", "turbo_peek"),
        SSG08_BLOODSHOT(Knife.SSG08, "Bloodshot", "bloodshot"),
        SSG08_NECROPOS(Knife.SSG08, "Necropos", "necropos"),
        SSG08_ACID_FADE(Knife.SSG08, "Acid Fade", "acid_fade"),
        SCAR20_EMERALD(Knife.SCAR20, "Emerald", "emerald"),
        SCAR20_BLOODSPORT(Knife.SCAR20, "Bloodsport", "bloodsport"),
        SCAR20_CYREX(Knife.SCAR20, "Cyrex", "cyrex"),
        SCAR20_CARDIAC(Knife.SCAR20, "Cardiac", "cardiac"),
        SCAR20_CRIMSON_WEB(Knife.SCAR20, "Crimson Web", "crimson_web"),
        SCAR20_SPLASH_JAM(Knife.SCAR20, "Splash Jam", "splash_jam"),
        SCAR20_ENFORCER(Knife.SCAR20, "Enforcer", "enforcer"),
        SCAR20_POWERCORE(Knife.SCAR20, "Powercore", "powercore"),
        SCAR20_MAGNA_CARTA(Knife.SCAR20, "Magna Carta", "magna_carta"),
        SCAR20_POULTRYGEIST(Knife.SCAR20, "Poultrygeist", "poultrygeist"),
        G3SG1_THE_EXECUTIONER(Knife.G3SG1, "The Executioner", "the_executioner"),
        G3SG1_CHRONOS(Knife.G3SG1, "Chronos", "chronos"),
        G3SG1_FLUX(Knife.G3SG1, "Flux", "flux"),
        G3SG1_DREAM_GLADE(Knife.G3SG1, "Dream Glade", "dream_glade"),
        G3SG1_STINGER(Knife.G3SG1, "Stinger", "stinger"),
        G3SG1_SCAVENGER(Knife.G3SG1, "Scavenger", "scavenger"),
        G3SG1_VENTILATOR(Knife.G3SG1, "Ventilator", "ventilator"),
        G3SG1_AZURE_ZEBRA(Knife.G3SG1, "Azure Zebra", "azure_zebra"),
        G3SG1_VIOLET_MURANO(Knife.G3SG1, "Violet Murano", "violet_murano"),
        G3SG1_NEW_ROOTS(Knife.G3SG1, "New Roots", "new_roots"),
        NOVA_HYPER_BEAST(Knife.NOVA, "Hyper Beast", "hyper_beast"),
        NOVA_BLOOMSTICK(Knife.NOVA, "Bloomstick", "bloomstick"),
        NOVA_KOI(Knife.NOVA, "Koi", "koi"),
        NOVA_ANTIQUE(Knife.NOVA, "Antique", "antique"),
        NOVA_TEMPEST(Knife.NOVA, "Tempest", "tempest"),
        NOVA_WILD_SIX(Knife.NOVA, "Wild Six", "wild_six"),
        NOVA_TOY_SOLDIER(Knife.NOVA, "Toy Soldier", "toy_soldier"),
        NOVA_GRAPHITE(Knife.NOVA, "Graphite", "graphite"),
        NOVA_RISING_SKULL(Knife.NOVA, "Rising Skull", "rising_skull"),
        NOVA_PLUME(Knife.NOVA, "Plume", "plume"),
        XM1014_TRANQUILITY(Knife.XM1014, "Tranquility", "tranquility"),
        XM1014_INCINEGATOR(Knife.XM1014, "Incinegator", "incinegator"),
        XM1014_SEASONS(Knife.XM1014, "Seasons", "seasons"),
        XM1014_XOXO(Knife.XM1014, "XOXO", "xoxo"),
        XM1014_BONE_MACHINE(Knife.XM1014, "Bone Machine", "bone_machine"),
        XM1014_BLACK_TIE(Knife.XM1014, "Black Tie", "black_tie"),
        XM1014_ENTOMBED(Knife.XM1014, "Entombed", "entombed"),
        XM1014_ZIGGY(Knife.XM1014, "Ziggy", "ziggy"),
        XM1014_TECLU_BURNER(Knife.XM1014, "Teclu Burner", "teclu_burner"),
        XM1014_ELEGANT_VINES(Knife.XM1014, "Elegant Vines", "elegant_vines"),
        MAG7_JUSTICE(Knife.MAG7, "Justice", "justice"),
        MAG7_HEAT(Knife.MAG7, "Heat", "heat"),
        MAG7_PRAETORIAN(Knife.MAG7, "Praetorian", "praetorian"),
        MAG7_BI83_SPECTRUM(Knife.MAG7, "BI83 Spectrum", "bi83_spectrum"),
        MAG7_SWAG_7(Knife.MAG7, "SWAG-7", "swag_7"),
        MAG7_BULLDOZER(Knife.MAG7, "Bulldozer", "bulldozer"),
        MAG7_CINQUEDEA(Knife.MAG7, "Cinquedea", "cinquedea"),
        MAG7_HEAVEN_GUARD(Knife.MAG7, "Heaven Guard", "heaven_guard"),
        MAG7_FIRESTARTER(Knife.MAG7, "Firestarter", "firestarter"),
        MAG7_MONSTER_CALL(Knife.MAG7, "Monster Call", "monster_call");

        private final Knife gun;
        private final String label, id;

        GunSkin(final Knife gun, final String label, final String id) {
            this.gun = gun;
            this.label = label;
            this.id = id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum InspectKey {
        R("R", Keyboard.KEY_R),
        F("F", Keyboard.KEY_F),
        G("G", Keyboard.KEY_G),
        H("H", Keyboard.KEY_H),
        V("V", Keyboard.KEY_V),
        X("X", Keyboard.KEY_X),
        Z("Z", Keyboard.KEY_Z);

        private final String label;
        private final int code;

        InspectKey(final String label, final int code) {
            this.label = label;
            this.code = code;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
