package rip.tempest.jenny.entity;

import rip.tempest.jenny.anim.AnimLoader;
import rip.tempest.jenny.anim.NpcAnimation;
import rip.tempest.jenny.geo.GeoLoader;
import rip.tempest.jenny.geo.GeoModel;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

public enum CharacterType {
    JENNY("jenny", "animation.jenny.idle",
            new String[]{"Hihi~", "Hehe~", "Huh?", "What do you need?", "I'm here for you~"},
            "animation.jenny.strip",
            new String[]{"Dance", "Wave"},
            new String[]{"animation.jenny.fortnite", "animation.jenny.wave"}),
    BIA("bia", "animation.bia.idle",
            new String[]{"Hihi~", "Heyaaa~", "What's up?", "Hey there!"},
            "animation.bia.strip",
            new String[]{"Wave"},
            new String[]{"animation.bia.wave"}),
    ELLIE("ellie", "animation.ellie.idle",
            new String[]{"I'm busy right now honey~", "What do you want?", "Hey!", "Hi~"},
            "animation.ellie.strip",
            new String[0], new String[0]),
    ALLIE("allie", "animation.allie.tail",
            new String[]{"HIIYAAYA!", "Your wish is my command!", "What do you desire?", "Tell me your first wish, mortal~"},
            null,
            new String[0], new String[0]),
    GALATH("galath", "animation.galath.idle",
            new String[]{"...", "What brings you here?", "I am watching.", "Speak your purpose."},
            null, new String[0], new String[0]),
    KOBOLD("kobold", "animation.kobold.idle",
            new String[]{"Yip!", "Kobold greets you!", "What do you need?", "*tail wag*"},
            null, new String[0], new String[0]),
    SLIME("slime", "animation.slime.idle",
            new String[]{"*squish*", "Hii~", "I'm squishy!", "Hello there~"},
            "animation.slime.strip", new String[0], new String[0]);

    public final String id;
    public final String idleAnimName;
    public final String[] dialogueLines;

    public final String stripAnimName;

    public final String[] idleActNames;

    public final String[] idleActAnimNames;

    private GeoModel cachedModel;

    private GeoModel cachedNudeModel;

    private GeoModel cachedArmoredModel;
    private Map<String, NpcAnimation> cachedAnims;
    private boolean loaded = false;

    CharacterType(String id, String idleAnimName, String[] dialogueLines,
                  String stripAnimName, String[] idleActNames, String[] idleActAnimNames) {
        this.id = id;
        this.idleAnimName = idleAnimName;
        this.dialogueLines = dialogueLines;
        this.stripAnimName = stripAnimName;
        this.idleActNames = idleActNames;
        this.idleActAnimNames = idleActAnimNames;
    }

    public GeoModel getModel() {
        ensureLoaded();
        return cachedModel;
    }

    public GeoModel getModel(boolean naked) {
        return getModel(naked, false);
    }

    public GeoModel getModel(boolean naked, boolean armored) {
        ensureLoaded();
        if (naked && cachedNudeModel != null) return cachedNudeModel;
        if (armored && cachedArmoredModel != null) return cachedArmoredModel;
        return cachedModel;
    }

    public boolean showsArmorOn(GeoModel model) {
        ensureLoaded();
        return model != null && model != cachedNudeModel && model.bones.containsKey("armorHelmet");
    }

    public NpcAnimation eyesAnimation() {
        Map<String, NpcAnimation> anims = getAnimations();
        if (anims == null) return null;
        switch (this) {
            case JENNY: return anims.get("animation.jenny.fhappy");
            case BIA: return anims.get("animation.bia.fhappy");
            case ELLIE: return anims.get("animation.ellie.eyes");
            case SLIME: return anims.get("animation.slime.fhappy");
            case KOBOLD: return anims.get("animation.kobold.blink");
            case GALATH: return anims.get("animation.galath.blink");
            default: return null;
        }
    }

    public boolean canUndress() {
        ensureLoaded();
        return allowsAdultContent() && cachedNudeModel != null && stripAnimName != null
                && cachedAnims != null && cachedAnims.containsKey(stripAnimName);
    }

    public Map<String, NpcAnimation> getAnimations() {
        ensureLoaded();
        return cachedAnims;
    }

    public ResourceLocation getTexture() {
        return new ResourceLocation("jenny", "textures/" + id + "/" + id + ".png");
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            ResourceLocation geoLoc = new ResourceLocation("jenny", "geo/" + id + "/" + id + ".geo.json");
            ResourceLocation animLoc = new ResourceLocation("jenny", "animations/" + id + "/" + id + ".animation.json");
            cachedModel = GeoLoader.load(geoLoc);
            cachedAnims = AnimLoader.load(animLoc);
            ResourceLocation nudeLoc = new ResourceLocation("jenny", "geo/" + id + "/" + id + "_nude.geo.json");
            if (CharacterType.class.getResource("/assets/jenny/" + nudeLoc.getResourcePath()) != null) {
                cachedNudeModel = GeoLoader.load(nudeLoc);
            }
            ResourceLocation armoredLoc = new ResourceLocation("jenny", "geo/" + id + "/" + id + "_armored.geo.json");
            if (CharacterType.class.getResource("/assets/jenny/" + armoredLoc.getResourcePath()) != null) {
                cachedArmoredModel = GeoLoader.load(armoredLoc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean allowsAdultContent() {
        return true;
    }

    public static CharacterType fromName(String name) {
        for (CharacterType t : values()) {
            if (t.id.equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
