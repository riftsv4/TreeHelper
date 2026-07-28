package com.zoee.treehelper.hotm;

import com.zoee.treehelper.config.Progression;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The correct set of Heart of the Mountain nodes that should be UNLOCKED/ENABLED for each
 * grind at each HOTM level, decoded from @TheUltimateNon's guide images (see TREE_STRUCTURE.md).
 * These are per-level target builds, not cumulative — the optimal tree is respec'd per level.
 * The Core of the Mountain is never included (it is always ignored in comparisons).
 *
 * <p>Node names match the live tooltip names read by {@link MenuNav#readPerk}. Grind/level
 * combos with no guide image are simply absent — callers must treat a null lookup as
 * "no data, do not validate" (never reset on missing data).
 */
public final class TreeData {

    private TreeData() {}

    /** Every real tree node name (perks + abilities), excluding the Core. */
    public static final Set<String> ALL_NODES = Set.of(
        "Blockhead", "Crystalline", "Daily Grind", "Daily Powder",
        "Dead Man's Chest", "Eager Adventurer", "Efficient Miner", "Fortunate Mineman",
        "Front Loaded", "Gem Lover", "Gemstone Infusion", "Gifts from the Departed",
        "Great Explorer", "Keep It Cool", "Lonesome Miner", "Luck of the Cave",
        "Maniac Miner", "Metal Head", "Miner's Blessing", "Mineshaft Mayhem",
        "Mining Fortune", "Mining Master", "Mining Speed", "Mining Speed Boost",
        "Mole", "No Stone Unturned", "Old-School", "Pickobulus",
        "Powder Buff", "Precision Mining", "Professional", "Quick Forge",
        "Rags to Riches", "Seasoned Mineman", "Sheer Force", "Sky Mall",
        "Speedy Mineman", "Steady Hand", "Strong Arm", "Subterranean Fisher",
        "Surveyor", "Titanium Insanium", "Tunnel Vision", "Vanguard Seeker",
        "Warm Heart"
    );

    /** Node name -> HOTM tier it sits on (rows from TREE_STRUCTURE.md; tier 1 = bottom). */
    public static final Map<String, Integer> NODE_TIERS = buildTiers();

    /** grind -> (HOTM level -> names that should be enabled). Unmodifiable. */
    public static final Map<Progression, Map<Integer, Set<String>>> CORRECT_ENABLED = build();

    /**
     * The target enabled-set for a grind at a level. Guides only exist for some levels, so a
     * missing level falls back to the closest guide <em>below</em> it (e.g. level 9 on a powder
     * path uses the level-8 build; a higher build would reference unreachable tiers).
     * {@code null} only when there is no guide at or below the level.
     */
    public static Set<String> enabledFor(Progression grind, int level) {
        Map<Integer, Set<String>> byLevel = CORRECT_ENABLED.get(grind);
        if (byLevel == null) return null;
        Set<String> exact = byLevel.get(level);
        if (exact != null) return exact;
        int best = -1;
        for (int k : byLevel.keySet()) {
            if (k < level && k > best) best = k;
        }
        return best < 0 ? null : byLevel.get(best);
    }

    private static Map<String, Integer> buildTiers() {
        Map<String, Integer> m = new HashMap<>();
        for (String n : new String[]{"Mining Speed"}) m.put(n, 1);
        for (String n : new String[]{"Mining Speed Boost", "Precision Mining", "Mining Fortune",
                "Titanium Insanium", "Pickobulus"}) m.put(n, 2);
        for (String n : new String[]{"Luck of the Cave", "Efficient Miner", "Quick Forge"}) m.put(n, 3);
        for (String n : new String[]{"Sky Mall", "Old-School", "Professional", "Mole",
                "Gem Lover", "Seasoned Mineman", "Front Loaded"}) m.put(n, 4);
        for (String n : new String[]{"Daily Grind", "Daily Powder"}) m.put(n, 5);
        for (String n : new String[]{"Tunnel Vision", "Blockhead", "Subterranean Fisher",
                "Keep It Cool", "Lonesome Miner", "Great Explorer", "Maniac Miner"}) m.put(n, 6);
        for (String n : new String[]{"Speedy Mineman", "Powder Buff", "Fortunate Mineman"}) m.put(n, 7);
        for (String n : new String[]{"Miner's Blessing", "No Stone Unturned", "Strong Arm",
                "Steady Hand", "Warm Heart", "Surveyor", "Mineshaft Mayhem"}) m.put(n, 8);
        for (String n : new String[]{"Metal Head", "Rags to Riches", "Eager Adventurer"}) m.put(n, 9);
        for (String n : new String[]{"Gemstone Infusion", "Crystalline", "Gifts from the Departed",
                "Mining Master", "Dead Man's Chest", "Vanguard Seeker", "Sheer Force"}) m.put(n, 10);
        return Collections.unmodifiableMap(m);
    }

    private static Map<Progression, Map<Integer, Set<String>>> build() {
        Map<Progression, Map<Integer, Set<String>>> m = new HashMap<>();
        m.put(Progression.HOTM, hotm());
        m.put(Progression.MITHRIL, mithril());
        m.put(Progression.GEMSTONE, gemstone());
        m.put(Progression.GLACITE, glacite());
        return Collections.unmodifiableMap(m);
    }

    private static Map<Integer, Set<String>> hotm() {
        Map<Integer, Set<String>> m = new HashMap<>();
        m.put(1, Set.of("Mining Speed"));
        m.put(2, Set.of("Mining Fortune", "Mining Speed", "Precision Mining"));
        m.put(3, Set.of("Efficient Miner", "Mining Fortune", "Mining Speed", "Pickobulus", "Titanium Insanium"));
        m.put(4, Set.of("Efficient Miner", "Mining Fortune", "Mining Speed", "Pickobulus", "Precision Mining", "Quick Forge", "Titanium Insanium"));
        m.put(5, Set.of("Efficient Miner", "Luck of the Cave", "Mining Fortune", "Mining Speed", "Mining Speed Boost", "Old-School", "Pickobulus", "Precision Mining", "Sky Mall", "Titanium Insanium"));
        m.put(6, Set.of("Efficient Miner", "Luck of the Cave", "Mining Fortune", "Mining Speed", "Mining Speed Boost", "Old-School", "Pickobulus", "Precision Mining", "Quick Forge", "Seasoned Mineman", "Sky Mall", "Titanium Insanium"));
        m.put(7, Set.of("Blockhead", "Efficient Miner", "Fortunate Mineman", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Pickobulus", "Powder Buff", "Precision Mining", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(8, Set.of("Efficient Miner", "Fortunate Mineman", "Keep It Cool", "Mining Fortune", "Mining Speed", "Mole", "No Stone Unturned", "Pickobulus", "Powder Buff", "Precision Mining", "Speedy Mineman", "Steady Hand", "Strong Arm", "Surveyor", "Titanium Insanium", "Warm Heart"));
        m.put(9, Set.of("Efficient Miner", "Fortunate Mineman", "Keep It Cool", "Metal Head", "Mining Fortune", "Mining Speed", "Mole", "No Stone Unturned", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Sky Mall", "Speedy Mineman", "Steady Hand", "Strong Arm", "Surveyor", "Titanium Insanium", "Warm Heart"));
        return m;
    }

    private static Map<Integer, Set<String>> mithril() {
        Map<Integer, Set<String>> m = new HashMap<>();
        m.put(7, Set.of("Blockhead", "Efficient Miner", "Keep It Cool", "Mining Fortune", "Mining Speed", "Mole", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Sky Mall", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(8, Set.of("Efficient Miner", "Keep It Cool", "Mining Fortune", "Mining Speed", "Mole", "No Stone Unturned", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Sky Mall", "Speedy Mineman", "Steady Hand", "Strong Arm", "Titanium Insanium"));
        m.put(10, Set.of("Eager Adventurer", "Efficient Miner", "Keep It Cool", "Mining Fortune", "Mining Speed", "Mole", "No Stone Unturned", "Old-School", "Powder Buff", "Precision Mining", "Professional", "Sheer Force", "Sky Mall", "Speedy Mineman", "Steady Hand", "Strong Arm", "Surveyor", "Titanium Insanium", "Vanguard Seeker", "Warm Heart"));
        return m;
    }

    private static Map<Integer, Set<String>> gemstone() {
        Map<Integer, Set<String>> m = new HashMap<>();
        m.put(7, Set.of("Blockhead", "Efficient Miner", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Pickobulus", "Powder Buff", "Precision Mining", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(8, Set.of("Blockhead", "Efficient Miner", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Sky Mall", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(10, Set.of("Blockhead", "Eager Adventurer", "Efficient Miner", "Fortunate Mineman", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Old-School", "Powder Buff", "Precision Mining", "Professional", "Sheer Force", "Sky Mall", "Speedy Mineman", "Subterranean Fisher", "Surveyor", "Vanguard Seeker"));
        return m;
    }

    private static Map<Integer, Set<String>> glacite() {
        Map<Integer, Set<String>> m = new HashMap<>();
        m.put(7, Set.of("Blockhead", "Efficient Miner", "Fortunate Mineman", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Pickobulus", "Powder Buff", "Precision Mining", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(8, Set.of("Blockhead", "Efficient Miner", "Fortunate Mineman", "Gem Lover", "Great Explorer", "Keep It Cool", "Lonesome Miner", "Mining Fortune", "Mining Speed", "Mole", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Sky Mall", "Speedy Mineman", "Subterranean Fisher", "Titanium Insanium"));
        m.put(10, Set.of("Dead Man's Chest", "Efficient Miner", "Fortunate Mineman", "Gem Lover", "Keep It Cool", "Metal Head", "Mining Fortune", "Mining Master", "Mining Speed", "Mole", "No Stone Unturned", "Old-School", "Pickobulus", "Powder Buff", "Precision Mining", "Professional", "Rags to Riches", "Sky Mall", "Speedy Mineman", "Steady Hand", "Strong Arm", "Surveyor", "Titanium Insanium", "Warm Heart"));
        return m;
    }
}
