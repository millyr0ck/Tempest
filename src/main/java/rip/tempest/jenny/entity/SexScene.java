package rip.tempest.jenny.entity;

import rip.tempest.jenny.anim.NpcAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public enum SexScene {
    JENNY_BLOWJOB(CharacterType.JENNY, "Blowjob", "bjmoan", true, 60f,
            a("animation.jenny.blowjobintro"), a("animation.jenny.blowjobsuck"),
            a("animation.jenny.blowjobthrust"), "animation.jenny.blowjobcum"),
    JENNY_PAIZURI(CharacterType.JENNY, "Paizuri", "bjmoan", true, 60f,
            a("animation.jenny.paizuri_start"), a("animation.jenny.paizuri_slow"),
            a("animation.jenny.paizuri_fast"), "animation.jenny.paizuri_cum"),
    JENNY_DOGGY(CharacterType.JENNY, "Doggy Style", "moan", true, 45f,
            a("animation.jenny.doggygoonbed", "animation.jenny.doggywait", "animation.jenny.doggystart"),
            a("animation.jenny.doggyslow"),
            a("animation.jenny.doggyfast_soft", "animation.jenny.doggyfast_hard"), "animation.jenny.doggycum"),

    BIA_PRONE_DOGGY(CharacterType.BIA, "Prone Doggy", "moan", true, 45f,
            a("animation.bia.prone_doggy_intro", "animation.bia.prone_doggy_insert"), a("animation.bia.prone_doggy_soft"),
            a("animation.bia.prone_doggy_hard1", "animation.bia.prone_doggy_hard2", "animation.bia.prone_doggy_hard3"),
            "animation.bia.prone_doggy_cum"),
    BIA_ANAL(CharacterType.BIA, "Anal", "ahh", true, 45f,
            a("animation.bia.anal_start"), a("animation.bia.anal_slow"),
            a("animation.bia.anal_fast"), "animation.bia.anal_cum"),

    ELLIE_COWGIRL(CharacterType.ELLIE, "Cowgirl", "moan", true, 10f,
            a("animation.ellie.cowgirlstart"), a("animation.ellie.cowgirlslow2"),
            a("animation.ellie.cowgirlfast"), "animation.ellie.cowgirlcum"),
    ELLIE_MISSIONARY(CharacterType.ELLIE, "Missionary", "moan", true, 40f,
            a("animation.ellie.missionary_start"), a("animation.ellie.missionary_slow"),
            a("animation.ellie.missionary_fast"), "animation.ellie.missionary_cum"),
    ELLIE_CARRY(CharacterType.ELLIE, "Carry", "moan", true, 20f,
            a("animation.ellie.carry_intro"),
            a("animation.ellie.carry_slow1", "animation.ellie.carry_slow2", "animation.ellie.carry_slow3", "animation.ellie.carry_slow4"),
            a("animation.ellie.carry_fast"), "animation.ellie.carry_cum"),

    ALLIE_REVERSE_COWGIRL(CharacterType.ALLIE, "Reverse Cowgirl", "moan", true, 30f,
            a("animation.allie.reverse_cowgirl_start"),
            a("animation.allie.reverse_cowgirl_slow1", "animation.allie.reverse_cowgirl_slow2", "animation.allie.reverse_cowgirl_slow3"),
            a("animation.allie.reverse_cowgirl_fastc1", "animation.allie.reverse_cowgirl_fastc2", "animation.allie.reverse_cowgirl_fastc3"),
            "animation.allie.reverse_cowgirl_cum"),
    ALLIE_DEEPTHROAT(CharacterType.ALLIE, "Deepthroat", "bjmoan", true, 40f,
            a("animation.allie.deepthroat_start"), a("animation.allie.deepthroat_slow"),
            a("animation.allie.deepthroat_fast"), "animation.allie.deepthroat_cum"),

    SLIME_BLOWJOB(CharacterType.SLIME, "Blowjob", null, true, 60f,
            a(), a("animation.slime.blowjobsuck"),
            a("animation.slime.blowjobthrust"), "animation.slime.blowjobcum"),
    SLIME_DOGGY(CharacterType.SLIME, "Doggy Style", null, true, 45f,
            a("animation.slime.doggystart"), a("animation.slime.doggyslow"),
            a("animation.slime.doggyfast"), "animation.slime.doggycum"),

    KOBOLD_BLOWJOB(CharacterType.KOBOLD, "Blowjob", "bjmoan", true, 60f,
            a("animation.kobold.blowjobStart"), a("animation.kobold.blowjobSlowR"),
            a("animation.kobold.blowjobFast"), "animation.kobold.blowjobCum"),
    KOBOLD_MATING_PRESS(CharacterType.KOBOLD, "Mating Press", "moan", true, 50f,
            a("animation.kobold.mating_press_start"), a("animation.kobold.mating_press_soft"),
            a("animation.kobold.mating_press_hard"), "animation.kobold.mating_press_cum"),
    KOBOLD_ANAL(CharacterType.KOBOLD, "Anal", "ahh", true, 45f,
            a("animation.kobold.analStart"), a("animation.kobold.analSoft"),
            a("animation.kobold.analHard"), "animation.kobold.analCum"),

    GALATH_BED(CharacterType.GALATH, "In Bed", "moan", true, 10f,
            a(), a("animation.shared.bed_slow"),
            a("animation.shared.bed_fast", "animation.shared.bed_fast1", "animation.shared.bed_fast2", "animation.shared.bed_fast3"),
            "animation.shared.bed_cum"),
    GALATH_LICK(CharacterType.GALATH, "Pussy Licking", "ahh", true, 30f,
            a(), a("animation.galath.pussy_licking"),
            a("animation.galath.pussy_licking_forward"), null),
    GALATH_MASTURBATE(CharacterType.GALATH, "Masturbate", "ahh", false, 20f,
            a(), a("animation.galath.masterbating_sitting"),
            a(), "animation.galath.masterbating_sitting_cum"),

    ELLIE_HUG(CharacterType.ELLIE, "Hug", true, 10f,
            a("animation.ellie.hug"), a("animation.ellie.hugidle"), "animation.ellie.hugselected"),
    ELLIE_SIT(CharacterType.ELLIE, "Sit Down", false, 20f,
            a("animation.ellie.sitdown"), a("animation.ellie.sitdownidle"), null),
    BIA_HEADPAT(CharacterType.BIA, "Head Pat", true, 30f,
            a("animation.bia.headpat"), a(), null),
    BIA_FLIRT(CharacterType.BIA, "Flirt", true, 10f,
            a("animation.bia.talk_horny2"), a("animation.bia.talk_idle2"), "animation.bia.talk_response"),
    BIA_SIT(CharacterType.BIA, "Sit Down", false, 20f,
            a("animation.bia.sitdown"), a("animation.bia.sitdownidle"), null),
    ALLIE_SUMMON(CharacterType.ALLIE, "Summon", false, 0f,
            a("animation.allie.summon"), a("animation.allie.summon_wait"), null),
    ALLIE_WISH(CharacterType.ALLIE, "Wish", false, 0f,
            a("animation.allie.rich"), a(), null),
    ;

    static {
        JENNY_BLOWJOB.prelude = "animation.jenny.payment";
        JENNY_PAIZURI.prelude = "animation.jenny.payment";
        JENNY_DOGGY.prelude = "animation.jenny.payment";

        JENNY_DOGGY.soloIntros = 2;
    }

    public final CharacterType character;
    public final String displayName;

    public final String soundCategory;

    public final boolean withPlayer;

    public final float cameraPitch;

    public final String[] introAnims;

    public final String[] loopAnims;

    public final String[] fastAnims;

    public final String cumAnim;

    public final boolean act;

    String prelude;

    int soloIntros;

    SexScene(CharacterType character, String displayName, String soundCategory, boolean withPlayer, float cameraPitch,
             String[] introAnims, String[] loopAnims, String[] fastAnims, String cumAnim) {
        this.character = character;
        this.displayName = displayName;
        this.soundCategory = soundCategory;
        this.withPlayer = withPlayer;
        this.cameraPitch = cameraPitch;
        this.introAnims = introAnims;
        this.loopAnims = loopAnims;
        this.fastAnims = fastAnims;
        this.cumAnim = cumAnim;
        this.act = false;
    }

    SexScene(CharacterType character, String displayName, boolean withPlayer, float cameraPitch,
             String[] introAnims, String[] loopAnims, String outro) {
        this.character = character;
        this.displayName = displayName;
        this.soundCategory = null;
        this.withPlayer = withPlayer;
        this.cameraPitch = cameraPitch;
        this.introAnims = introAnims;
        this.loopAnims = loopAnims;
        this.fastAnims = new String[0];
        this.cumAnim = outro;
        this.act = true;
    }

    private static String[] a(String... names) { return names; }

    private static String pick(String[] names) {
        if (names.length == 0) return null;
        return names[ThreadLocalRandom.current().nextInt(names.length)];
    }

    public boolean hasIntro() { return introAnims.length > 0; }

    public boolean hasFast() { return fastAnims.length > 0; }

    public boolean hasLoop() { return loopAnims.length > 0; }

    public boolean fastCycles() { return fastAnims.length > 1; }

    public NpcAnimation prelude(Map<String, NpcAnimation> anims) { return get(prelude, anims); }

    public boolean isSoloIntro(int index) { return index < soloIntros; }

    public NpcAnimation intro(int index, Map<String, NpcAnimation> anims) {
        return index < introAnims.length ? anims.get(introAnims[index]) : null;
    }

    public NpcAnimation loop(Map<String, NpcAnimation> anims) { return get(pick(loopAnims), anims); }

    public NpcAnimation fast(Map<String, NpcAnimation> anims) { return get(pick(fastAnims), anims); }

    public NpcAnimation cum(Map<String, NpcAnimation> anims) { return get(cumAnim, anims); }

    private static NpcAnimation get(String name, Map<String, NpcAnimation> anims) {
        return name == null || anims == null ? null : anims.get(name);
    }

    public static List<SexScene> forCharacter(CharacterType type) {
        List<SexScene> list = new ArrayList<>();
        if (!type.allowsAdultContent()) return list;
        for (SexScene s : values()) {
            if (s.character == type && !s.act) list.add(s);
        }
        return list;
    }

    public static List<SexScene> actsFor(CharacterType type) {
        List<SexScene> list = new ArrayList<>();
        for (SexScene s : values()) {
            if (s.character == type && s.act) list.add(s);
        }
        return list;
    }
}
