package com.zoee.treehelper.hotm;

import com.zoee.treehelper.TreeHelperClient;
import com.zoee.treehelper.config.Progression;
import com.zoee.treehelper.config.TreeConfig;
import com.zoee.treehelper.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single owner of the player's powder state. Three sources feed it:
 * <ul>
 *   <li><b>HOTM menu, "Heart of the Mountain" item lore</b> — current (spendable) balances,
 *       lines like {@code Mithril Powder: 286,166}. The colon form is the balance display;
 *       perk <em>cost</em> lines ("1,000 Mithril Powder") have no colon.</li>
 *   <li><b>HOTM menu, "Reset Heart of the Mountain" item lore</b> — powder already spent in
 *       the tree, from the reimbursement lines like {@code - 278,775 Mithril Powder}.
 *       Total powder = current + in-tree.</li>
 *   <li><b>Tab list</b> — the Powders section entries ({@code Mithril: 286,166}) keep the
 *       current balances live while mining, so "you can afford the next step" notifications
 *       fire without opening any menu.</li>
 * </ul>
 *
 * <p>It also records every perk's current level (from the "Level X/Y" lore) while the menu is
 * open — that's what decides which {@link UpgradePlan.Step} is next — and cross-checks the
 * {@link UpgradePlan} cost formulas against the real tooltip costs, logging any drift.
 *
 * <p>Called from the {@code /tree gmd} scan (worker thread), the helper panel (render thread)
 * and the client tick (tab list). {@link ChatUtil} is thread-safe.
 */
public final class PowderTracker {

    private static final Pattern BALANCE_LINE =
            Pattern.compile("^(Mithril|Gemstone|Glacite) Powder: ([\\d,]+)$");
    private static final Pattern RESET_LINE =
            Pattern.compile("^- ([\\d,]+) (Mithril|Gemstone|Glacite) Powder$");
    private static final Pattern TAB_LINE =
            Pattern.compile("^(Mithril|Gemstone|Glacite): ([\\d,]+)$");
    /** A perk tooltip's next-level cost, e.g. {@code 2,411 Mithril Powder} (no colon). */
    private static final Pattern COST_LINE =
            Pattern.compile("^([\\d,]+) (Mithril|Gemstone|Glacite) Powder$");
    private static final String RESET_ITEM = "reset heart of the mountain";

    /** Total Gemstone Powder at which the recommended grind moves on to Mithril. */
    private static final long GEMSTONE_DONE = 15_760_102L;
    /** Total Mithril Powder at which the recommended grind moves on to Glacite. */
    private static final long MITHRIL_DONE = 8_761_625L;

    private final TreeConfig config;
    /** Formula-vs-tooltip mismatches already logged this session (perk:level), to log once. */
    private final Set<String> loggedCostDrift = new HashSet<>();
    /** When each powder type's step was last announced, for the re-notify interval. */
    private final Map<Progression, Long> lastNotifyMs = new EnumMap<>(Progression.class);

    public PowderTracker(TreeConfig config) {
        this.config = config;
    }

    // --- HOTM menu reading -------------------------------------------------

    /**
     * Reads everything useful off the currently loaded HOTM page: current balances, in-tree
     * (reset) amounts, and perk levels. Fires step notifications on changes.
     */
    void scanOpenMenu(Minecraft mc) {
        boolean levelsChanged = false;
        int n = MenuNav.containerSlotCount(mc);
        for (int i = 0; i < n; i++) {
            // Perk levels (+ cost formula cross-check) from real tree nodes.
            PerkNode perk = MenuNav.readPerk(mc, i);
            if (perk != null && TreeData.ALL_NODES.contains(perk.name())) {
                Integer old = config.perkLevels.put(perk.name(), perk.level());
                if (old == null || old != perk.level()) levelsChanged = true;
                verifyCost(mc, i, perk);
                continue;
            }

            String name = MenuNav.slotName(mc, i).toLowerCase(Locale.ROOT);
            boolean resetItem = name.contains(RESET_ITEM);
            for (String raw : MenuNav.slotLore(mc, i)) {
                String line = raw.trim();
                Matcher balance = BALANCE_LINE.matcher(line);
                if (balance.matches()) {
                    updateCurrent(Progression.fromArg(balance.group(1)), parse(balance.group(2)));
                    continue;
                }
                if (resetItem) {
                    Matcher reimb = RESET_LINE.matcher(line);
                    if (reimb.matches()) {
                        updateInTree(Progression.fromArg(reimb.group(2)), parse(reimb.group(1)));
                    }
                }
            }
        }
        if (levelsChanged) {
            config.save();
            // Perk levels moved -> the next step may have changed; re-check notifications.
            for (Progression p : Progression.values()) {
                if (p.isPowder()) checkAffordable(p);
            }
        }
    }

    /**
     * Compares the perk tooltip's next-level cost against the {@link UpgradePlan} formula and
     * logs drift once per perk+level. A hit here means Hypixel changed a cost formula.
     */
    private void verifyCost(Minecraft mc, int slot, PerkNode perk) {
        long expected = UpgradePlan.levelCost(perk.name(), perk.level() + 1);
        if (expected < 0) return; // perk not in any plan
        for (String raw : MenuNav.slotLore(mc, slot)) {
            Matcher m = COST_LINE.matcher(raw.trim());
            if (!m.matches()) continue;
            long actual = parse(m.group(1));
            String key = perk.name() + ":" + (perk.level() + 1);
            if (actual != expected && loggedCostDrift.add(key)) {
                TreeHelperClient.LOGGER.warn(
                        "Cost formula drift for {} level {}: tooltip says {}, formula says {} — "
                                + "update UpgradePlan.COST_EXP",
                        perk.name(), perk.level() + 1, actual, expected);
            }
            return;
        }
    }

    // --- tab list reading (live while mining) ------------------------------

    /** Reads the tab-list Powders entries ({@code Mithril: 286,166}) into current balances. */
    public void scanTabList(Minecraft mc) {
        if (mc.getConnection() == null || !MenuNav.onHypixel(mc)) return;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) continue;
            Matcher m = TAB_LINE.matcher(MenuNav.strip(display.getString()).trim());
            if (m.matches()) {
                updateCurrent(Progression.fromArg(m.group(1)), parse(m.group(2)));
            }
        }
        // Even without balance changes, re-check so the re-notify interval can fire while afk.
        for (Progression p : Progression.values()) {
            if (p.isPowder()) checkAffordable(p);
        }
    }

    // --- state updates + notifications -------------------------------------

    private void updateCurrent(Progression type, long amount) {
        if (type == null) return;
        TreeConfig.ProgressionData data = config.data(type);
        if (data.currentPowder == amount) return;
        data.currentPowder = amount;
        config.save();
        checkAffordable(type);
        checkGrindAdvice();
    }

    private void updateInTree(Progression type, long amount) {
        if (type == null) return;
        TreeConfig.ProgressionData data = config.data(type);
        if (data.inTreePowder == amount) return;
        data.inTreePowder = amount;
        config.save();
        checkGrindAdvice();
    }

    /**
     * Grind-progression advice, fired at the powder milestones (once each, persisted):
     * total Gemstone ≥ {@link #GEMSTONE_DONE} -> move to Mithril; total Mithril ≥
     * {@link #MITHRIL_DONE} -> start Glacite. (The HOTM-7 "start Gemstone" advice lives in
     * {@code HotmScanner.report}, where the tier is learned.) Totals = current + in-tree, so
     * spending powder on upgrades doesn't push a milestone back out of reach.
     */
    private void checkGrindAdvice() {
        if (!config.grindAdvice) return;
        if (!config.advisedMithrilMove && totalPowder(Progression.GEMSTONE) >= GEMSTONE_DONE) {
            config.advisedMithrilMove = true;
            config.save();
            if (config.activePath != Progression.MITHRIL) {
                ChatUtil.send("You've reached " + fmt(GEMSTONE_DONE) + " Gemstone Powder — we"
                        + " recommend moving on to the Mithril powder grind (set your Path to"
                        + " Mithril).", ChatFormatting.GOLD);
            }
        }
        if (!config.advisedGlaciteStart && totalPowder(Progression.MITHRIL) >= MITHRIL_DONE) {
            config.advisedGlaciteStart = true;
            config.save();
            if (config.activePath != Progression.GLACITE) {
                ChatUtil.send("You've reached " + fmt(MITHRIL_DONE) + " Mithril Powder — we"
                        + " recommend starting the Glacite grind (set your Path to Glacite).",
                        ChatFormatting.GOLD);
            }
        }
    }

    /** Current + in-tree powder for a type, or -1 while the balance has never been seen. */
    private long totalPowder(Progression type) {
        TreeConfig.ProgressionData data = config.data(type);
        if (data.currentPowder < 0) return -1;
        return data.currentPowder + Math.max(data.inTreePowder, 0);
    }

    /**
     * Chat ping when the current (spendable) balance covers the full remaining cost of the
     * next step of the active grind's plan for this powder type. Fires once per step; with
     * {@code renotifyMinutes > 0} it repeats on that interval while the step stays affordable.
     */
    private void checkAffordable(Progression type) {
        if (!config.chatNotifications) return;
        UpgradePlan.Step next = UpgradePlan.nextStep(config.activePath, type, this::levelOf);
        if (next == null) return;
        if (levelOf(next.perk()) <= 0) return; // perk not unlocked -> no upgrade to announce

        TreeConfig.ProgressionData data = config.data(type);
        long cost = UpgradePlan.costToFinish(next, levelOf(next.perk()));
        if (cost < 0 || data.currentPowder < cost) return;

        boolean firstTime = !next.id().equals(data.notifiedStep);
        if (!firstTime) {
            int secs = config.renotifySeconds;
            if (secs <= 0) return;
            Long last = lastNotifyMs.get(type);
            if (last != null && System.currentTimeMillis() - last < secs * 1000L) return;
        }

        lastNotifyMs.put(type, System.currentTimeMillis());
        if (firstTime) {
            data.notifiedStep = next.id();
            config.save();
        }
        ChatUtil.send("You have enough " + type.displayName + " for "
                        + next.label(levelOf(next.perk())) + " (" + fmt(cost) + ")!",
                ChatFormatting.GREEN);
    }

    /** Current level of a perk per the last menu read; unknown = 0. */
    public int levelOf(String perk) {
        return config.perkLevels.getOrDefault(perk, 0);
    }

    private static long parse(String digits) {
        return Long.parseLong(digits.replace(",", ""));
    }

    static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }
}
