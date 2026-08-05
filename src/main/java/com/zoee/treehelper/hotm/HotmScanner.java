package com.zoee.treehelper.hotm;

import com.zoee.treehelper.config.Progression;
import com.zoee.treehelper.config.TreeConfig;
import com.zoee.treehelper.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guided "Get Data" scan (started from the panel's Get Data button). The mod never opens
 * {@code /hotm} or clicks anything itself: the user opens the menu and presses Get Data, then
 * the overlay highlights which scroll arrow to <em>right-click</em> (Hypixel's
 * jump-to-top/bottom shortcuts). Each settled page is scanned passively from the client tick
 * ({@link #guideTick}); once both ends of the tree have been seen it reports the highest
 * UNLOCKED tier (== HOTM level) plus the powder totals.
 *
 * <p>All menu automation (the old {@code /tree gmd} auto-scan and the {@code /tree dump} dev
 * walker, with their background thread) was removed 2026-07-28 — the mod is read-only against
 * the menu, full stop.
 */
public class HotmScanner {

    private static final String HOTM_TITLE = "Heart of the Mountain";
    private static final Pattern TIER_NAME = Pattern.compile("Tier\\s+(\\d+)");
    /**
     * The tier-up banner Hypixel prints in chat, e.g. {@code HEART OF THE MOUNTAIN TIER 6}
     * (see hotm level up detection.txt for a captured log). Color codes are stripped by the
     * time we see the message, so this matches the plain text.
     */
    private static final Pattern TIER_UP =
            Pattern.compile("HEART OF THE MOUNTAIN\\s+TIER\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    /** Powder grinds below this HOTM tier are strongly suboptimal — warn the user. */
    static final int MIN_POWDER_TIER = 7;
    /** How long the visible page must stay unchanged before we trust what we read off it. */
    private static final long GUIDE_SETTLE_MS = 250;

    private enum GuidePhase { IDLE, TO_TOP, TO_BOTTOM }

    private final TreeConfig config;
    private final PowderTracker powder;

    private volatile GuidePhase guide = GuidePhase.IDLE;
    private GuidePhase guideAnnounced = GuidePhase.IDLE;
    private final Map<Integer, Boolean> guideTiers = new HashMap<>();
    private String guideSig = "";
    private long guideSigMs;
    private boolean guideScanned;

    public HotmScanner(TreeConfig config, PowderTracker powder) {
        this.config = config;
        this.powder = powder;
    }

    public boolean guideActive() {
        return guide != GuidePhase.IDLE;
    }

    /**
     * Entry point for the panel's Get Data button. Requires the HOTM menu to already be open —
     * the mod never opens it itself.
     */
    public void startGuidedScan(Minecraft mc) {
        if (!MenuNav.onHypixel(mc)) {
            ChatUtil.send("You're not on Hypixel — Tree Helper only works on Hypixel SkyBlock.",
                    ChatFormatting.RED);
            return;
        }
        if (config.activePath == null) {
            ChatUtil.send("Please select a grind first — pick a Path in the Tree Helper panel.",
                    ChatFormatting.RED);
            return;
        }
        if (!hotmOpen(mc)) {
            ChatUtil.send("Open your HOTM menu (/hotm), then press Get Data in the Tree Helper panel.",
                    ChatFormatting.RED);
            return;
        }
        guideTiers.clear();
        guideSig = "";
        guideSigMs = 0;
        guideScanned = false;
        guideAnnounced = GuidePhase.IDLE;
        guide = GuidePhase.TO_TOP;
    }

    public void cancelGuidedScan() {
        if (guide == GuidePhase.IDLE) return;
        guide = GuidePhase.IDLE;
        ChatUtil.send("Get Data cancelled.", ChatFormatting.YELLOW);
    }

    /**
     * Automatic HOTM level-up detection: Hypixel prints a "HEART OF THE MOUNTAIN TIER N" banner
     * in chat when you tier up, so we bump the stored level (and re-run grind advice) the moment
     * it appears — the build overlay reads {@code highestUnlockedTier}, so it follows along
     * without a fresh Get Data scan. Called from the client chat listener.
     */
    public void onChatMessage(Minecraft mc, String message) {
        if (guide != GuidePhase.IDLE) return;   // a Get Data scan already owns the level
        if (!MenuNav.onHypixel(mc)) return;
        Matcher m = TIER_UP.matcher(message);
        if (!m.find()) return;

        int tier = Integer.parseInt(m.group(1));
        if (tier <= config.data(Progression.HOTM).highestUnlockedTier) return;

        config.data(Progression.HOTM).highestUnlockedTier = tier;
        config.save();

        ChatUtil.send("HOTM level up detected — you're now Tier " + tier
                + ". Your recommended build has been updated.", ChatFormatting.AQUA);

        // Re-evaluate grind advice immediately (e.g. reaching Tier 7 -> start Gemstone) rather
        // than waiting for the next tab poll.
        powder.checkGrindAdvice();
    }

    /**
     * The container slot the user should right-click next (the arrow the overlay highlights),
     * or -1 when no guided scan is running.
     */
    public int guideTargetSlot(Minecraft mc) {
        return switch (guide) {
            case TO_TOP -> MenuNav.findSlotByName(mc, "scroll up");
            case TO_BOTTOM -> MenuNav.findSlotByName(mc, "scroll down");
            default -> -1;
        };
    }

    /**
     * Runs every client tick while a guided scan is active: waits for the visible page to
     * settle, scans it once, and advances the phase when the relevant scroll arrow is gone
     * (no "Scroll Up" = at the top tier; no "Scroll Down" = at the bottom).
     */
    public void guideTick(Minecraft mc) {
        if (guide == GuidePhase.IDLE) return;
        if (!hotmOpen(mc)) {
            guide = GuidePhase.IDLE;
            ChatUtil.send("Get Data cancelled — the HOTM menu was closed.", ChatFormatting.YELLOW);
            return;
        }

        // A page is only read after its slot contents stop changing for GUIDE_SETTLE_MS,
        // otherwise a half-populated view could fake "no scroll arrow" and end a phase early.
        String sig = MenuNav.signature(mc);
        long now = System.currentTimeMillis();
        if (!sig.equals(guideSig)) {
            guideSig = sig;
            guideSigMs = now;
            guideScanned = false;
            return;
        }
        if (guideScanned || now - guideSigMs < GUIDE_SETTLE_MS || !anyTierVisible(mc)) return;
        guideScanned = true;

        scanPage(mc, guideTiers);
        powder.scanOpenMenu(mc);

        if (guide == GuidePhase.TO_TOP && MenuNav.findSlotByName(mc, "scroll up") == -1) {
            guide = GuidePhase.TO_BOTTOM; // reached (or started at) the top
        }
        if (guide == GuidePhase.TO_BOTTOM && MenuNav.findSlotByName(mc, "scroll down") == -1) {
            guide = GuidePhase.IDLE; // reached the bottom: both ends seen -> done
            finishGuidedScan();
            return;
        }
        if (guideAnnounced != guide) {
            guideAnnounced = guide;
            ChatUtil.send(guide == GuidePhase.TO_TOP
                    ? "Right-click the highlighted arrow to jump to the top tier."
                    : "Now right-click the highlighted arrow to jump to the bottom tier.",
                    ChatFormatting.AQUA);
        }
    }

    private void finishGuidedScan() {
        int highest = -1;
        for (Map.Entry<Integer, Boolean> e : guideTiers.entrySet()) {
            if (e.getValue() && e.getKey() > highest) highest = e.getKey();
        }
        if (highest < 0) {
            ChatUtil.send("Couldn't find any unlocked tier.", ChatFormatting.RED);
            return;
        }
        report(highest);
    }

    private static boolean hotmOpen(Minecraft mc) {
        return MenuNav.openContainer(mc) != null
                && MenuNav.title(mc).toLowerCase(Locale.ROOT)
                        .contains(HOTM_TITLE.toLowerCase(Locale.ROOT));
    }

    private boolean anyTierVisible(Minecraft mc) {
        int n = MenuNav.containerSlotCount(mc);
        for (int i = 0; i < n; i++) {
            if (TIER_NAME.matcher(MenuNav.slotName(mc, i)).find()) return true;
        }
        return false;
    }

    /**
     * Records every "Tier N" block on the current page into {@code tiers} (tier -> unlocked).
     * A block only counts if its lore has an explicit UNLOCKED/LOCKED status line, which keeps
     * perk nodes that merely mention a tier from being mistaken for tier blocks.
     */
    private void scanPage(Minecraft mc, Map<Integer, Boolean> tiers) {
        int n = MenuNav.containerSlotCount(mc);
        for (int i = 0; i < n; i++) {
            Matcher m = TIER_NAME.matcher(MenuNav.slotName(mc, i));
            if (!m.find()) continue;
            int tier = Integer.parseInt(m.group(1));

            boolean unlocked = false;
            boolean sawStatus = false;
            for (String line : MenuNav.slotLore(mc, i)) {
                String t = line.trim();
                String lo = t.toLowerCase(Locale.ROOT);
                if (t.equalsIgnoreCase("UNLOCKED") || lo.contains("you have unlocked this tier")) {
                    unlocked = true;
                    sawStatus = true;
                } else if (t.equalsIgnoreCase("LOCKED")) {
                    sawStatus = true;
                }
            }
            if (sawStatus) tiers.merge(tier, unlocked, (a, b) -> a || b);
        }
    }

    private void report(int tier) {
        config.data(Progression.HOTM).highestUnlockedTier = tier;
        config.save();

        // The mining-data block: HOTM level + TOTAL powder (current balance + in-tree).
        ChatUtil.send("Your Mining Data", ChatFormatting.AQUA);
        dataLine("HOTM Level", String.valueOf(tier));
        for (Progression p : Progression.values()) {
            if (!p.isPowder()) continue;
            long current = config.data(p).currentPowder;
            long inTree = config.data(p).inTreePowder;
            dataLine(p.displayName.replace(" Powder", ""),
                    current < 0 ? "unknown" : PowderTracker.fmt(current + Math.max(inTree, 0)));
        }

        // Powder grinds (Mithril/Gemstone/Glacite) below Tier 7 are strongly suboptimal.
        Progression grind = config.activePath;
        if (grind != null && grind != Progression.HOTM && tier < MIN_POWDER_TIER) {
            ChatUtil.send(grind.displayName + " grinding below Tier " + MIN_POWDER_TIER
                    + " is not optimal and is highly advised against — consider swapping your"
                    + " Path to HOTM.", ChatFormatting.YELLOW);
        }

        // Grind-progression advice (HOTM 7 -> start Gemstone, powder milestones) — the tier
        // just changed, so re-evaluate immediately rather than waiting for the tab poll.
        powder.checkGrindAdvice();
    }

    /** One {@code - Label: value} mining-data line (gray label, gold value). */
    private static void dataLine(String label, String value) {
        ChatUtil.send(Component.literal("- " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD)));
    }
}
