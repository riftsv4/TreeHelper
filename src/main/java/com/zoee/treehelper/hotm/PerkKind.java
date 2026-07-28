package com.zoee.treehelper.hotm;

/**
 * Which kind of Heart of the Mountain node this is, inferred from the item's material:
 * plain items (coal/emerald/diamond) are {@link #PERK}s, block items
 * (coal/emerald/redstone block) are pickaxe {@link #ABILITY} nodes.
 */
public enum PerkKind {
    PERK,
    ABILITY
}
