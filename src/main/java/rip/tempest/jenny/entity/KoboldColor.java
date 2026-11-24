package rip.tempest.jenny.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum KoboldColor {
    GREEN("Green", 69, 141, 113, 91, 167, 128),
    YELLOW("Yellow", 241, 177, 77, 255, 226, 170),
    RED("Red", 230, 27, 57, 253, 232, 239),
    PURPLE("Purple", 196, 148, 207, 246, 188, 96),
    LIGHT_GREEN("Light Green", 170, 208, 47, 230, 214, 104),
    OLD_BLUE("Old Blue", 173, 138, 128, 118, 151, 180),
    DARK_GREY("Dark Grey", 92, 92, 110, 198, 193, 165),
    BROWN("Brown", 200, 145, 112, 253, 228, 198),
    DARK_BLUE("Dark Blue", 65, 84, 116, 104, 137, 146),
    LIGHT_BLUE("Light Blue", 100, 163, 206, 138, 235, 242),
    SILVER("Silver", 136, 136, 134, 255, 255, 255);

    public static final Set<String> MAIN_BONES = new HashSet<>(Arrays.asList(
            "colorSpots", "neck", "head", "snout", "midSectionR", "midSectionL", "innerCheekLR", "innerCheekRR",
            "gayL", "gayR", "legR", "legL", "shinL", "toesL", "kneeL", "curvesL", "shinR", "toesR", "kneeR", "curvesR",
            "sideL", "sideR", "hip", "torsoL", "torsoR", "armR", "lowerArmR", "ellbowR", "armL", "lowerArmL", "ellbowL",
            "hornUL", "hornUR", "tail", "tail2", "tail3", "tail4", "tail5", "hornDL2", "hornDR2", "hornDR3M", "hornDL3M",
            "frecklesAL1", "frecklesAL2", "frecklesAR1", "frecklesAR2", "frecklesHL1", "frecklesHL2", "frecklesHR1", "frecklesHR2"));

    public static final Set<String> SECONDARY_BONES = new HashSet<>(Arrays.asList(
            "boobR", "boobL", "frontNeck", "Rside", "Lside", "frontAndInside", "innerCheekLL", "innerCheekRL",
            "layer", "layer2", "down", "down2", "down3", "down4", "down5", "fuckhole", "hornDR3S", "hornDL3S",
            "assholeCoverUp", "assholeCoverUp2"));

    public final String label;
    public final int main;
    public final int secondary;

    KoboldColor(String label, int r, int g, int b, int r2, int g2, int b2) {
        this.label = label;
        this.main = (r << 16) | (g << 8) | b;
        this.secondary = (r2 << 16) | (g2 << 8) | b2;
    }

    public static int tintFor(String bone, KoboldColor body, KoboldColor eyes) {
        if (MAIN_BONES.contains(bone)) return body.main;
        if (SECONDARY_BONES.contains(bone)) return body.secondary;
        if (bone.equals("irisR") || bone.equals("irisL")) return eyes.main;
        return -1;
    }
}
