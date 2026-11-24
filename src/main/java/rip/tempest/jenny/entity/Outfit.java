package rip.tempest.jenny.entity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Outfit {
    public enum Slot { HELMET, CHEST, LEGS, BOOTS }

    public enum Material {
        NONE("None", -1), LEATHER("Leather", 4), CHAIN("Chain", 2), IRON("Iron", 2), GOLD("Gold", 1), DIAMOND("Diamond", 0);

        public final String label;

        public final int strip;

        Material(String label, int strip) {
            this.label = label;
            this.strip = strip;
        }

        public Material next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final int[] LEATHER_COLORS = {0xA06540, 0xF9FFFE, 0x1D1D21, 0xB02E26, 0xF38BAA, 0x8932B8, 0x3C44AA, 0x3AB3DA, 0x5E7C16, 0xFED83D};
    public static final String[] LEATHER_COLOR_NAMES = {"Brown", "White", "Black", "Red", "Pink", "Purple", "Blue", "Light Blue", "Green", "Yellow"};

    private static final Map<CharacterType, String[][][]> GROUPS = new HashMap<>();

    static {
        String[] helmet = {"armorHelmet"};
        String[] top = {"armorShoulderR", "armorShoulderL", "armorChest", "armorBoobs"};
        String[] bottom = {"armorBootyR", "armorBootyL", "armorPantsLowL", "armorPantsLowR", "armorPantsUpR", "armorPantsUpL", "armorHip"};
        String[] shoes = {"armorShoesL", "armorShoesR"};
        String[] none = {};
        GROUPS.put(CharacterType.JENNY, new String[][][]{
                {helmet, none}, {top, a("boobsFlesh", "upperBodyL", "upperBodyR")},
                {bottom, a("fleshL", "fleshR", "vagina", "curvesL", "curvesR", "kneeL", "kneeR")}, {shoes, none}});
        GROUPS.put(CharacterType.BIA, new String[][][]{
                {helmet, a("leaf7", "leaf8")}, {top, a("bra", "upperBodyR", "upperBodyL")},
                {bottom, a("slip", "fleshL", "fleshR", "vagina", "curvesL", "curvesR", "kneeL", "kneeR")}, {shoes, none}});
        GROUPS.put(CharacterType.ELLIE, new String[][][]{
                {helmet, a("headband")}, {top, a("boobsFlesh", "upperBodyL", "upperBodyR")},
                {bottom, a("fleshL", "fleshR", "vagina", "hotpants", "slip", "curvesL", "curvesR", "kneeL", "kneeR")}, {shoes, none}});
        GROUPS.put(CharacterType.SLIME, new String[][][]{
                {helmet, a("bigblob")}, {top, a("boobsFlesh", "upperBodyL", "upperBodyR", "cloth")},
                {bottom, a("fleshL", "fleshR", "vagina", "curvesL", "curvesR", "kneeL", "kneeR")}, {shoes, none}});
        GROUPS.put(CharacterType.ALLIE, new String[][][]{
                {helmet, none}, {top, a("boobsFlesh", "clothes", "clothesR", "clothesL")}, {none, none}, {none, none}});
        GROUPS.put(CharacterType.KOBOLD, new String[][][]{
                {helmet, none}, {top, a("boobsFlesh", "upperBodyL", "upperBodyR")},
                {a("armorBootyR", "armorBootyL", "armorPantsLowL", "armorPantsLowR", "armorPantsUpR", "armorPantsUpL", "armorHip", "armorKneeR", "armorKneeL"),
                        a("fleshL", "fleshR", "vagina", "fuckhole", "curvesL", "curvesR", "kneeL", "kneeR")},
                {shoes, a("toesR", "toesL")}});
        GROUPS.put(CharacterType.GALATH, new String[][][]{{helmet, none}, {none, none}, {none, none}, {none, none}});
    }

    private static String[] a(String... s) { return s; }

    private final Material[] worn = {Material.NONE, Material.NONE, Material.NONE, Material.NONE};
    private int leatherColor = 0;

    private final Map<String, Integer> armourBones = new HashMap<>();
    private final Set<String> hiddenBones = new HashSet<>();
    private CharacterType builtFor;

    public static boolean supports(CharacterType type, Slot slot) {
        String[][][] g = GROUPS.get(type);
        return g != null && g[slot.ordinal()][0].length > 0;
    }

    public Material get(Slot slot) { return worn[slot.ordinal()]; }

    public void cycle(Slot slot) {
        worn[slot.ordinal()] = worn[slot.ordinal()].next();
        builtFor = null;
    }

    public void clear() {
        Arrays.fill(worn, Material.NONE);
        builtFor = null;
    }

    public boolean any() {
        for (Material m : worn) if (m != Material.NONE) return true;
        return false;
    }

    public void cycleLeatherColor() { leatherColor = (leatherColor + 1) % LEATHER_COLORS.length; }

    public int leatherColorIndex() { return leatherColor; }

    public boolean wearsLeather() {
        for (Material m : worn) if (m == Material.LEATHER) return true;
        return false;
    }

    public int stripFor(CharacterType type, String bone) {
        rebuild(type);
        Integer s = armourBones.get(bone);
        return s == null ? -1 : s;
    }

    public boolean hides(CharacterType type, String bone) {
        rebuild(type);
        return hiddenBones.contains(bone);
    }

    public boolean isLeather(CharacterType type, String bone) {
        rebuild(type);
        Integer s = armourBones.get(bone);
        return s != null && s == Material.LEATHER.strip;
    }

    public int leatherRgb() { return LEATHER_COLORS[leatherColor]; }

    private void rebuild(CharacterType type) {
        if (builtFor == type) return;
        builtFor = type;
        armourBones.clear();
        hiddenBones.clear();
        String[][][] groups = GROUPS.get(type);
        if (groups == null) return;
        for (Slot slot : Slot.values()) {
            String[][] g = groups[slot.ordinal()];
            Material m = worn[slot.ordinal()];
            if (m == Material.NONE) {
                hiddenBones.addAll(Arrays.asList(g[0]));
            } else {
                for (String bone : g[0]) armourBones.put(bone, m.strip);
                hiddenBones.addAll(Arrays.asList(g[1]));
            }
        }
    }
}
