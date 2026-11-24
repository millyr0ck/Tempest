package rip.tempest.modules.impl.visuals;

import rip.tempest.modules.impl.combat.AuraModule;
import rip.tempest.Tempest;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.api.events.impl.game.EntityHurtSoundEvent;
import rip.tempest.api.events.impl.player.AttackEvent;
import rip.tempest.api.events.impl.player.MotionEvent;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;

import java.util.HashMap;
import java.util.Map;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Animations", category = ModuleCategory.VISUALS)
public final class AnimationsModule extends Module {
    @EventLink
    public final Listener<MotionEvent> motionEventListener = e -> {
        setSuffix(mode.getValue().toString());
    };

    private static final long HIT_WINDOW_MS = 1000L;

    private final Map<Integer, Long> recentHits = new HashMap<>();

    public static boolean hitBobActive() {
        return hitBob.getValue() && Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled();
    }

    @EventLink
    public final Listener<AttackEvent> attackEventListener = e -> {
        if (!hitBob.getValue() || mc.thePlayer == null || mc.thePlayer.capabilities.isCreativeMode) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword || held.getItem() instanceof ItemTool)) return;
        if (e.target instanceof EntityLivingBase) recentHits.put(e.target.getEntityId(), System.currentTimeMillis());
    };

    @EventLink
    public final Listener<EntityHurtSoundEvent> hurtEventListener = e -> {
        if (!hitBob.getValue() || e.getEntity() == null || recentHits.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long hitAt = recentHits.get(e.getEntity().getEntityId());
        recentHits.values().removeIf(time -> now - time > HIT_WINDOW_MS);
        if (hitAt != null && now - hitAt <= HIT_WINDOW_MS) mc.getItemRenderer().replayEquip();
    };

    public static ModeProperty<AnimationMode> mode = new ModeProperty<>("Style", AnimationMode.OnePointEight);
    public static NumberProperty x = new NumberProperty("X", 0.0F, -2.0F, 2.0F, 0.05f);
    public static NumberProperty y = new NumberProperty("Y", 0.0F, -2.0F, 2.0F, 0.05f);
    public static NumberProperty z = new NumberProperty("Z", 0.0F, -2.0F, 2.0F, 0.05f);
    public static NumberProperty scale = new NumberProperty("Scale", 1, 0.1, 2, 0.1);
    public static NumberProperty spinSpeed = new NumberProperty("Spin Speed", 1, () -> mode.getValue() == AnimationMode.Spin, 1, 10, 1);

    public static NumberProperty astolfoSpin = new NumberProperty("Astolfo Spin", 2, () -> mode.getValue() == AnimationMode.AstolfoSpin
            || mode.getValue() == AnimationMode.Spinny, 0, 10, 0.1);
    public static NumberProperty swingSpeed = new NumberProperty("Swing Speed", 0, -10, 10, 1);
    public static Property<Boolean> fluxSwing = new Property<>("Flux Swing", false);
    public static Property<Boolean> smoothHit = new Property<>("Smooth Hit", false);
    public static Property<Boolean> swingEating = new Property<>("Swing While Eating", false);

    public static Property<Boolean> hitBob = new Property<>("Hit Bob", true);

    public static Property<Boolean> blockWithTools = new Property<>("Block With Tools", false);

    public static Property<Boolean> swingTrail = new Property<>("Swing Trail", false);
    public static ModeProperty<TrailColor> trailColor = new ModeProperty<>("Trail Color", TrailColor.White, () -> swingTrail.getValue());
    public static NumberProperty trailLength = new NumberProperty("Trail Length", 300, () -> swingTrail.getValue(), 80, 800, 10);
    public static NumberProperty trailOpacity = new NumberProperty("Trail Opacity", 0.6, () -> swingTrail.getValue(), 0.1, 1.0, 0.05);
    public static Property<Boolean> noHurtCamera = new Property<>("No Hurt Camera", true);
    public static Property<Boolean> noSneakCamera = new Property<>("No Sneak Camera", false);
    public static Property<Boolean> noFireOverlay = new Property<>("No Fire Overlay", true);
    public static Property<Boolean> noBlindness = new Property<>("No Blindness", true);

    public AnimationsModule() {
        this.toggle();
    }

    public static boolean toolBlocking(EntityPlayer player, ItemStack held) {
        if (player != mc.thePlayer || held == null || !blockWithTools.getValue()) return false;
        if (!(held.getItem() instanceof ItemTool || held.getItem() instanceof ItemHoe)) return false;
        if (!Tempest.INSTANCE.getModuleManager().getModule(AnimationsModule.class).isEnabled()) return false;
        if (mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown()) return true;

        return AuraModule.target() != null && AuraModule.autoBlocks();
    }

    public enum AnimationMode {
        OnePointEight("1.8"),
        Exhibition("Exhibition"),
        Novoline("Novoline"),
        Sigma("Sigma"),
        SigmaSlide("Sigma Slide"),
        SigmaAvatar("Sigma Avatar"),
        SigmaVanilla("Sigma Vanilla"),
        SigmaTap("Sigma Tap"),
        SigmaTap2("Sigma Tap 2"),
        Swang("Swang"),
        UpNDown("UpNDown"),
        Virtue("Virtue"),
        SevenClassic("1.7 Classic"),
        Smooth2("Smooth 2"),
        Ethereal("Ethereal"),

        Spin("Spin"),
        Smooth3("Smooth 3"),

        Slide3Alt("Slide 3 Alt"),

        SevenAccurate("1.7 Accurate"),
        Lucid("Lucid"),
        Astro("Astro"),
        ExhibitionNew("Exhibition New"),
        Swong("Swong"),
        Question("?"),
        Dortware("Dortware"),

        Lol("Lol"),
        Lul("Lul"),
        Lel("Lel"),
        Lil("Lil"),
        Exhibibi("Exhibibi"),

        AstolfoDefault("Astolfo Default"),
        AstolfoOld("Astolfo Old"),
        AstolfoSpin("Astolfo Spin"),
        Spinny("Spinny"),
        Dash("Dash");

        private final String display;

        AnimationMode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum TrailColor {
        White,
        Client
    }
}
