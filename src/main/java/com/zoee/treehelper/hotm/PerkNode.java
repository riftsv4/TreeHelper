package com.zoee.treehelper.hotm;

/**
 * One Heart of the Mountain node read from the live menu.
 *
 * @param name  perk display name, colour-stripped (e.g. "Mining Speed")
 * @param kind  {@link PerkKind} — a perk or a pickaxe ability, from the item material
 * @param state {@link PerkState} — from the item material
 * @param level current perk level from the "Level X/Y" lore line (0 = locked / no level line)
 * @param row   grid row within the 9-wide chest (0-based), at the scroll position it was seen
 * @param col   grid column (0-based)
 */
public record PerkNode(String name, PerkKind kind, PerkState state, int level, int row, int col) {
}
