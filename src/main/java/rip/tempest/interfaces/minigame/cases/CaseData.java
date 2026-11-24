package rip.tempest.interfaces.minigame.cases;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class CaseData {
    public static final int MIL_SPEC = 3, RESTRICTED = 4, CLASSIFIED = 5, COVERT = 6;

    public static final class Item {
        public String name, weapon, paint, color, icon, description, flavour;
        public int rarity;
        public double minFloat, maxFloat;
        public boolean hasWear, stattrak, gloves, knife;
    }

    public static final class Container {
        public String name, icon, keyName, keyIcon, rareName, rareIcon;
        public List<Item> items, rare;
    }

    public static final class Roll {
        public final Item item;
        public final boolean special, stattrak;
        public final double wear;
        public final int pattern;

        Roll(final Item item, final boolean special, final boolean stattrak, final double wear, final int pattern) {
            this.item = item;
            this.special = special;
            this.stattrak = stattrak;
            this.wear = wear;
            this.pattern = pattern;
        }

        public String displayName() {
            if (!stattrak) return item.name;
            return item.name.startsWith("★ ") ? "★ StatTrak™ " + item.name.substring(2) : "StatTrak™ " + item.name;
        }

        public String exterior() {
            return CaseData.exterior(wear);
        }
    }

    private static final double[] TIER_ODDS = {79.92, 15.98, 3.20, 0.64, 0.26};
    private static final int[] TIERS = {MIL_SPEC, RESTRICTED, CLASSIFIED, COVERT};
    private static final double STATTRAK_ODDS = 0.10;

    private static final double[][] WEAR_BUCKETS = {{0, .07, .03}, {.07, .15, .24}, {.15, .38, .33}, {.38, .45, .24}, {.45, 1, .16}};

    private CaseData() {
    }

    public static List<Container> load(final File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return new ArrayList<>(Arrays.asList(new Gson().fromJson(reader, Container[].class)));
        }
    }

    public static Roll roll(final Container container, final Random random) {
        final List<List<Item>> tiers = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < TIER_ODDS.length; i++) {
            final List<Item> tier = i < TIERS.length ? ofRarity(container, TIERS[i]) : container.rare;
            tiers.add(tier);
            if (!tier.isEmpty()) total += TIER_ODDS[i];
        }
        double at = random.nextDouble() * total;
        int chosen = -1;
        for (int i = 0; i < TIER_ODDS.length; i++) {
            if (tiers.get(i).isEmpty()) continue;
            chosen = i;
            at -= TIER_ODDS[i];
            if (at < 0) break;
        }
        final List<Item> tier = tiers.get(chosen);
        final Item item = tier.get(random.nextInt(tier.size()));
        final boolean special = chosen == TIERS.length;
        final boolean stattrak = item.stattrak && random.nextDouble() < STATTRAK_ODDS;
        return new Roll(item, special, stattrak, item.hasWear ? rollWear(item, random) : -1, item.hasWear ? random.nextInt(1001) : -1);
    }

    private static double rollWear(final Item item, final Random random) {
        double at = random.nextDouble();
        double[] bucket = WEAR_BUCKETS[WEAR_BUCKETS.length - 1];
        for (final double[] candidate : WEAR_BUCKETS) {
            at -= candidate[2];
            if (at < 0) {
                bucket = candidate;
                break;
            }
        }
        final double raw = bucket[0] + random.nextDouble() * (bucket[1] - bucket[0]);
        return item.minFloat + raw * (item.maxFloat - item.minFloat);
    }

    public static String exterior(final double wear) {
        if (wear < 0) return "";
        if (wear < .07) return "Factory New";
        if (wear < .15) return "Minimal Wear";
        if (wear < .38) return "Field-Tested";
        if (wear < .45) return "Well-Worn";
        return "Battle-Scarred";
    }

    private static List<Item> ofRarity(final Container container, final int rarity) {
        final List<Item> found = new ArrayList<>();
        for (final Item item : container.items) if (item.rarity == rarity) found.add(item);
        return found;
    }

    public static List<Item> fillReel(final Container container, final int tiles, final Random random) {
        final int[] weights = {150000, 30000, 6000, 1250, 250, 50, 10};
        int total = 0;
        for (final Item item : container.items) total += weights[item.rarity];
        final List<Item> reel = new ArrayList<>(tiles);
        for (int i = 0; i < tiles; i++) {
            final int pick = random.nextInt(total);
            int weight = 0;
            for (final Item item : container.items) {
                weight += weights[item.rarity];
                if (pick <= weight) {
                    reel.add(item);
                    break;
                }
            }
        }
        return reel;
    }
}
