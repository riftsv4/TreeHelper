package com.zoee.treehelper.config;

/**
 * The four grinds this mod can be pointed at. Only HOTM is scanned from a GUI right now;
 * the powder progressions are here so their upgrade thresholds and last-known state can be
 * stored per-grind as the mod grows.
 */
public enum Progression {
    HOTM("Heart of the Mountain"),
    MITHRIL("Mithril Powder"),
    GEMSTONE("Gemstone Powder"),
    GLACITE("Glacite Powder");

    /** Human-readable name printed in chat. */
    public final String displayName;

    Progression(String displayName) {
        this.displayName = displayName;
    }

    /** True for the three powder grinds; HOTM itself levels via HOTM XP, not powder. */
    public boolean isPowder() {
        return this != HOTM;
    }

    /** Case-insensitive lookup from a command argument like "mithril". Returns null if unknown. */
    public static Progression fromArg(String arg) {
        if (arg == null) return null;
        for (Progression p : values()) {
            if (p.name().equalsIgnoreCase(arg)) return p;
        }
        return null;
    }
}
