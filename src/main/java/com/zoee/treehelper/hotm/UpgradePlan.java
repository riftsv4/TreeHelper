package com.zoee.treehelper.hotm;

import com.zoee.treehelper.config.Progression;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The hardcoded powder-spending plans: for a chosen grind, the exact order to level perks with
 * each powder type. Transcribed from the user's guide files
 * ({@code Downloads/powder upgrade thresholds/...}, 2026-07-27):
 * <ul>
 *   <li><b>HOTM and Mithril grinds</b> share one plan (their source files are identical), with
 *       step lists for all three powder types.</li>
 *   <li><b>Gemstone grind</b> has its own gemstone-powder list only (Great Explorer / Mole /
 *       Powder Buff); by decision, Mithril/Glacite show no steps on that grind.</li>
 *   <li><b>Glacite grind</b> has no plan at all — the recommendation is to grind commissions to
 *       HOTM 10 while collecting powder.</li>
 * </ul>
 *
 * <p>Costs use the per-perk formulas from the community wiki
 * (hypixelskyblock.minecraft.wiki, July 2026): reaching level {@code n} costs
 * {@code floor((n+1)^exponent)} powder, and level 1 is free (it comes with the token unlock).
 * Everything is cross-checked against real tooltip costs during menu reads
 * ({@link PowderTracker}); a logged "cost formula drift" means an exponent here is off — fix it
 * from the tooltip sequence like Efficient Miner's was (wiki said 3.05, tooltips proved 2.6).
 */
public final class UpgradePlan {

    private UpgradePlan() {}

    /** One entry in a spending plan: level {@code perk} from {@code from} up to {@code to}. */
    public record Step(String perk, int from, int to) {
        public String id() {
            return perk + ":" + to;
        }

        public String label(int currentLevel) {
            int shownFrom = Math.max(from, Math.min(currentLevel, to));
            return perk + " " + shownFrom + "➜" + to;
        }
    }

    // --- cost formulas -----------------------------------------------------

    /**
     * Perk name -> cost exponent; cost of reaching level n = floor((n+1)^exp).
     * All exponents transcribed from the wiki's raw per-perk math markup (2026-07-27) and
     * numerically verified: each one reproduces the wiki's cost-summary bracket totals exactly
     * (e.g. Efficient Miner 2-10 = 1,812; Mole 2-10 = 727; Surveyor 2-10 = 39,957).
     * Efficient Miner's 2.6 additionally matches real in-game tooltips (17, 36, 65, 105, …).
     */
    private static final Map<String, Double> COST_EXP = Map.ofEntries(
            Map.entry("Mining Speed", 3.0),
            Map.entry("Efficient Miner", 2.6),
            Map.entry("Titanium Insanium", 3.1),
            Map.entry("Mining Fortune", 3.05),
            Map.entry("Speedy Mineman", 3.2),
            Map.entry("Powder Buff", 3.2),
            Map.entry("Fortunate Mineman", 3.2),
            Map.entry("Great Explorer", 4.0),
            Map.entry("Mole", 2.17883),
            Map.entry("Surveyor", 4.0),
            Map.entry("No Stone Unturned", 3.05));

    /**
     * Powder cost of reaching level {@code n} of {@code perk} from {@code n-1}, or -1 if the
     * perk is unknown. Level 1 costs 0 powder — it comes with the Token of the Mountain unlock.
     */
    public static long levelCost(String perk, int n) {
        Double exp = COST_EXP.get(perk);
        if (exp == null) return -1;
        if (n <= 1) return 0;
        return (long) Math.floor(Math.pow(n + 1, exp));
    }

    /** Total powder to level {@code perk} from {@code fromLevel} up to {@code toLevel}, or -1. */
    public static long costBetween(String perk, int fromLevel, int toLevel) {
        if (!COST_EXP.containsKey(perk)) return -1;
        long sum = 0;
        for (int n = fromLevel + 1; n <= toLevel; n++) sum += levelCost(perk, n);
        return sum;
    }

    // --- the plans ---------------------------------------------------------

    private static List<Step> steps(int[][] raw, String... perks) {
        return java.util.Arrays.stream(raw)
                .map(r -> new Step(perks[r[0]], r[1], r[2]))
                .toList();
    }

    /** Shared HOTM/Mithril plan — Mithril Powder section. */
    private static final List<Step> SHARED_MITHRIL = steps(new int[][]{
            {0, 0, 10}, {1, 0, 10}, {2, 0, 5}, {0, 10, 15}, {1, 10, 20}, {2, 5, 10},
            {0, 15, 20}, {1, 20, 30}, {2, 10, 15}, {1, 30, 35}, {2, 15, 20}, {1, 35, 40},
            {2, 20, 22}, {1, 40, 45}, {2, 22, 25}, {1, 45, 50}, {2, 25, 30}, {1, 50, 55},
            {2, 30, 32}, {1, 55, 60}, {2, 32, 35}, {0, 20, 25}, {1, 60, 75}, {2, 35, 40},
            {0, 25, 30}, {1, 75, 85}, {2, 40, 42}, {0, 30, 35}, {1, 85, 90}, {2, 42, 45},
            {0, 35, 40}, {1, 90, 95}, {0, 40, 45}, {1, 95, 100}, {2, 45, 50}, {0, 45, 50},
            {3, 0, 50},
    }, "Mining Speed", "Efficient Miner", "Titanium Insanium", "Mining Fortune");

    /** Shared HOTM/Mithril plan — Gemstone Powder section. */
    private static final List<Step> SHARED_GEMSTONE = steps(new int[][]{
            {0, 0, 30}, {1, 0, 5}, {2, 0, 10}, {0, 30, 35}, {1, 5, 15}, {2, 10, 20},
            {0, 35, 40}, {1, 15, 20}, {2, 20, 25}, {0, 40, 45}, {1, 20, 25}, {2, 25, 35},
            {0, 45, 50}, {1, 25, 30}, {2, 35, 40}, {1, 30, 35}, {2, 40, 45}, {1, 40, 45},
            {1, 45, 50}, {2, 45, 50},
    }, "Speedy Mineman", "Powder Buff", "Fortunate Mineman");

    /** Shared HOTM/Mithril plan — Glacite Powder section. */
    private static final List<Step> SHARED_GLACITE = steps(new int[][]{
            {0, 0, 20}, {1, 0, 50},
    }, "Surveyor", "No Stone Unturned");

    /** Gemstone grind — Gemstone Powder section (Great Explorer / Mole / Powder Buff ladder). */
    private static final List<Step> GEMSTONE_GRIND = steps(new int[][]{
            {0, 0, 20}, {1, 1, 5}, {2, 1, 2}, {1, 5, 8}, {2, 2, 3}, {1, 8, 12}, {2, 3, 4},
            {1, 12, 16}, {2, 4, 5}, {1, 16, 20}, {2, 5, 6}, {1, 20, 25}, {2, 6, 7},
            {1, 25, 30}, {2, 7, 8}, {1, 30, 35}, {2, 8, 9}, {1, 35, 40}, {2, 9, 10},
            {1, 40, 46}, {2, 10, 11}, {1, 46, 52}, {2, 11, 12}, {1, 52, 58}, {2, 12, 13},
            {1, 58, 65}, {2, 13, 14}, {1, 65, 71}, {2, 14, 15}, {1, 71, 78}, {2, 15, 16},
            {1, 78, 85}, {2, 16, 17}, {1, 85, 92}, {2, 17, 18}, {1, 92, 100}, {2, 18, 19},
            {1, 100, 107}, {2, 19, 20}, {1, 107, 115}, {2, 20, 21}, {1, 115, 123}, {2, 21, 22},
            {1, 123, 131}, {2, 22, 23}, {1, 131, 139}, {2, 23, 24}, {1, 139, 147}, {2, 24, 25},
            {1, 147, 155}, {2, 25, 26}, {1, 155, 164}, {2, 26, 27}, {1, 164, 173}, {2, 27, 28},
            {1, 173, 182}, {2, 28, 29}, {1, 182, 191}, {2, 29, 30}, {1, 191, 200}, {2, 30, 50},
    }, "Great Explorer", "Mole", "Powder Buff");

    /** grind -> powder type -> ordered step list. */
    private static final Map<Progression, Map<Progression, List<Step>>> PLANS = buildPlans();

    private static Map<Progression, Map<Progression, List<Step>>> buildPlans() {
        Map<Progression, List<Step>> shared = new EnumMap<>(Progression.class);
        shared.put(Progression.MITHRIL, SHARED_MITHRIL);
        shared.put(Progression.GEMSTONE, SHARED_GEMSTONE);
        shared.put(Progression.GLACITE, SHARED_GLACITE);

        Map<Progression, List<Step>> gemstone = new EnumMap<>(Progression.class);
        gemstone.put(Progression.GEMSTONE, GEMSTONE_GRIND);

        Map<Progression, Map<Progression, List<Step>>> plans = new EnumMap<>(Progression.class);
        plans.put(Progression.HOTM, shared);
        plans.put(Progression.MITHRIL, shared);
        plans.put(Progression.GEMSTONE, gemstone);
        // GLACITE grind: no plan — grind commissions to HOTM 10 instead.
        return plans;
    }

    /** The spending order for {@code powderType} on {@code grind}, or null if there is none. */
    public static List<Step> steps(Progression grind, Progression powderType) {
        if (grind == null) return null;
        Map<Progression, List<Step>> byPowder = PLANS.get(grind);
        return byPowder == null ? null : byPowder.get(powderType);
    }

    /**
     * The first step whose target level the player hasn't reached, per {@code levelOf}
     * (current perk levels; unknown perks count as level 0). Null = no plan or plan complete.
     */
    public static Step nextStep(Progression grind, Progression powderType, ToIntFunction<String> levelOf) {
        List<Step> steps = steps(grind, powderType);
        if (steps == null) return null;
        for (Step s : steps) {
            if (levelOf.applyAsInt(s.perk()) < s.to()) return s;
        }
        return null;
    }

    /** How many steps of the plan are already complete (target level reached). */
    public static int stepsDone(Progression grind, Progression powderType, ToIntFunction<String> levelOf) {
        List<Step> steps = steps(grind, powderType);
        if (steps == null) return 0;
        int done = 0;
        for (Step s : steps) {
            if (levelOf.applyAsInt(s.perk()) >= s.to()) done++;
        }
        return done;
    }

    /** Powder still needed to finish {@code step} from the player's current level, or -1. */
    public static long costToFinish(Step step, int currentLevel) {
        return costBetween(step.perk(), Math.max(currentLevel, 0), step.to());
    }
}
