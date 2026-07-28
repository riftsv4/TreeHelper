package com.zoee.treehelper.hotm;

/**
 * State of a Heart of the Mountain node, read from the item's <em>material</em> (far more
 * reliable than tooltip text). Perks (single items) and pickaxe abilities (block items) share
 * {@link #LOCKED} but otherwise use different states:
 * <ul>
 *   <li>{@link #LOCKED}    — coal (perk) / coal block (ability): not unlocked yet.</li>
 *   <li>{@link #UNLOCKED}  — emerald: perk unlocked but not maxed.</li>
 *   <li>{@link #MAXED}     — diamond: perk unlocked and maxed.</li>
 *   <li>{@link #ENABLED}   — emerald block: pickaxe ability unlocked and turned on.</li>
 *   <li>{@link #DISABLED}  — redstone block: pickaxe ability unlocked but turned off.</li>
 *   <li>{@link #UNKNOWN}   — a node whose material wasn't recognised.</li>
 * </ul>
 */
public enum PerkState {
    LOCKED,
    UNLOCKED,
    MAXED,
    ENABLED,
    DISABLED,
    UNKNOWN;

    /** True if the node has been unlocked (a perk with any level, or an ability, enabled or not). */
    public boolean isUnlocked() {
        return this == UNLOCKED || this == MAXED || this == ENABLED || this == DISABLED;
    }
}
