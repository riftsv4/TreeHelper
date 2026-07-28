package com.zoee.treehelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zoee.treehelper.TreeHelperClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state, saved as JSON in the Fabric config directory
 * ({@code config/treehelper.json}). Gson is bundled with the Fabric loader, so no
 * extra dependency is needed.
 */
public class TreeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("treehelper.json");
    /** Pre-rename config file (mod id used to be hotm_tree_helper); migrated on first load. */
    private static final Path OLD_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("hotm_tree_helper.json");

    /** Which grind the user is currently working on. {@code null} = none selected yet. */
    public Progression activePath = null;

    /** Saved data keyed by grind / powder type. */
    public Map<Progression, ProgressionData> progressions = new EnumMap<>(Progression.class);

    /**
     * Current level of every real perk, keyed by display name — read from the "Level X/Y" lore
     * whenever the HOTM menu is scanned or open. Drives the "next upgrade step" logic.
     */
    public Map<String, Integer> perkLevels = new HashMap<>();

    /** Tree Helper (settings) panel drag offset from its default anchor. */
    public int panelOffsetX = 0;
    public int panelOffsetY = 0;

    /** Powder Helper panel drag offset from its default anchor (it's a separate GUI). */
    public int powderPanelOffsetX = 0;
    public int powderPanelOffsetY = 0;

    /** Settings panel minimized to just its header bar. */
    public boolean panelCollapsed = false;

    /** Show the Powder Helper GUI (per-powder cards: balance / next step / progress). */
    public boolean powderHelperEnabled = true;

    /** How a notification type behaves: silent, announce once, or re-announce on an interval. */
    public enum NotifyMode { OFF, ONCE, REPEAT }

    /** "You can afford the next step" notifications. REPEAT re-pings while it stays affordable. */
    public NotifyMode upgradeNotify = NotifyMode.ONCE;

    /**
     * Grind-progression advice ("you hit HOTM 7 — start Gemstone", powder milestones).
     * REPEAT re-reminds on the interval until the recommended path is actually selected.
     */
    public NotifyMode adviceNotify = NotifyMode.ONCE;

    /**
     * Fire-once flags for the grind-progression advice, so each recommendation is only ever
     * announced one time: HOTM 7 -> start Gemstone; 15,760,102 Gemstone -> move to Mithril;
     * 8,761,625 Mithril -> start Glacite.
     */
    public boolean advisedGemstoneStart = false;
    public boolean advisedMithrilMove = false;
    public boolean advisedGlaciteStart = false;

    /** Interval for anything set to {@link NotifyMode#REPEAT}, in seconds. */
    public int renotifySeconds = 60;

    /** Draw the in-tree highlights (target build diff + current upgrade step). */
    public boolean routingOverlay = true;

    /** Overlay ARGB colors: target-build node / wrongly-enabled node / current upgrade step. */
    public int colorTarget = 0xCC33DD33;
    public int colorWrong = 0xCCFF3838;
    public int colorStep = 0xCC3399FF;

    /** Per-grind / per-powder-type saved state. */
    public static class ProgressionData {
        /** Highest tier confirmed unlocked from the last scan. 0 = unknown / not scanned yet. */
        public int highestUnlockedTier = 0;

        /** Spendable powder balance (HOTM item lore / tab list). -1 = never seen. */
        public long currentPowder = -1;

        /** Powder already spent in the tree (the reset item's reimbursement). -1 = never seen. */
        public long inTreePowder = -1;

        /**
         * {@link com.zoee.treehelper.hotm.UpgradePlan.Step#id()} of the step already announced
         * as affordable in chat, so the notification fires once per step.
         */
        public String notifiedStep = "";
    }

    /** Returns (creating if needed) the saved data for a grind. */
    public ProgressionData data(Progression p) {
        return progressions.computeIfAbsent(p, k -> new ProgressionData());
    }

    // --- persistence -------------------------------------------------------

    public static TreeConfig load() {
        try {
            Path source = Files.exists(PATH) ? PATH : OLD_PATH;
            if (Files.exists(source)) {
                TreeConfig loaded = GSON.fromJson(Files.readString(source), TreeConfig.class);
                if (loaded != null) {
                    loaded.normalize();
                    if (source == OLD_PATH) loaded.save(); // migrate to the new filename
                    return loaded;
                }
            }
        } catch (Exception e) {
            TreeHelperClient.LOGGER.error("Failed to read config, starting fresh", e);
        }
        TreeConfig fresh = new TreeConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            TreeHelperClient.LOGGER.error("Failed to write config", e);
        }
    }

    /** Gson deserializes into plain maps; re-wrap so the fields behave and nulls are handled. */
    private void normalize() {
        // activePath stays null until the user picks a grind — do not default it.
        Map<Progression, ProgressionData> rewrapped = new EnumMap<>(Progression.class);
        if (progressions != null) {
            for (Map.Entry<Progression, ProgressionData> e : progressions.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (e.getValue().notifiedStep == null) e.getValue().notifiedStep = "";
                rewrapped.put(e.getKey(), e.getValue());
            }
        }
        progressions = rewrapped;
        if (perkLevels == null) perkLevels = new HashMap<>();
        if (upgradeNotify == null) upgradeNotify = NotifyMode.ONCE;
        if (adviceNotify == null) adviceNotify = NotifyMode.ONCE;
        // Older configs used 0 = "once"; ONCE is a mode now, so the interval must be real.
        if (renotifySeconds <= 0) renotifySeconds = 60;
    }
}
