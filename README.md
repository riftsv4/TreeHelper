# Tree Helper

*Mining progression made simple.*

A client-side Fabric mod for **Minecraft 26.1.2** (the version Hypixel now hosts SkyBlock on)
that reads your **Heart of the Mountain** tree and tracks powder-upgrade progressions.

## Commands

- **Pick a grind:** `/tree hotm`, `/tree mithril`, `/tree gemstone`, `/tree glacite`
  → prints e.g. `Upgrade path set to: Mithril`
- **Scan your mining data:** `/tree gmd` → opens `/hotm`, walks the tree and prints
  `Your Mining Data`: HOTM level + total Mithril/Gemstone/Glacite powder (balance + in-tree)
- **Upgrade plans:** each grind has a hardcoded perk-upgrade order (e.g. `Efficient Miner
  60 ➜ 75`). The perk of the current step is boxed **blue** in the tree; green/red mark the
  target-build diff (colors configurable). Powder is tracked from the HOTM menu and the tab
  list while mining, so you get a chat ping the moment you can afford the next step.
  (Glacite grind: no plan — grind commissions to HOTM 10.)
- **Tree Helper panel** (on the open HOTM menu): draggable + minimizable settings hub — toggle
  the Powder Helper, pick the active path from a dropdown (same as `/tree <path>`), toggle
  notifications and their re-notify interval (Once/30s/1m/5m/10m), toggle the routing overlay,
  and click any of the three overlay color swatches to open an RGB picker.
- **Powder Helper** (its own draggable GUI): per-powder cards with live balance, next step,
  progress bar, and steps done.
- **Dump perks (dev):** `/tree dump` → scrolls the whole tree and logs every perk node's name,
  state and grid position — used to build the correct-tree data sets
- **Status:** `/tree status` → active grind + last scanned tier (+ powder & thresholds for
  powder grinds)
- **Help:** `/tree help`

State is saved to `config/hotm_tree_helper.json` (per-grind `upgradeThresholds` +
`lastPowder`).

### How the tier scan works
The tree scrolls, so one page isn't enough. `/tree get` runs on a background thread:

1. sends `/hotm` and waits for the **Heart of the Mountain** menu to open and populate;
2. right-clicks the **Scroll Up** button to jump to the top (highest) tier;
3. scans each page's left-column `Tier N` blocks downward. A block counts only if its lore
   has an explicit `UNLOCKED` / `LOCKED` status line (so perk nodes that merely mention a tier
   are ignored). The first page from the top containing an unlocked tier settles the answer —
   everything above it was locked — and the highest such tier is reported;
4. closes the menu.

All GUI mutations (command send, slot clicks, close) hop to the client thread via
`mc.execute(...)`; only reads happen on the worker thread.

## Building

Requires **JDK 25** (MC 26.1.2's Java target). `gradle.properties` pins the Gradle daemon to
the PrismLauncher JDK 25 runtime; change `org.gradle.java.home` if yours lives elsewhere.

```bash
./gradlew build         # Windows: .\gradlew.bat build
```

The jar lands in `build/libs/` as `treehelper-<version>-<mc version>.jar`. Drop it in
`.minecraft/mods/` alongside **Fabric API**. Dev client: `./gradlew runClient`.
State is saved to `config/treehelper.json` (an old `hotm_tree_helper.json` is migrated
automatically).

## Toolchain (mirrors the working CoalFlipper-26.1.2 mod)

| Piece | Version |
|-------|---------|
| Minecraft | `26.1.2` (Java 25) |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.155.2+26.1.2` |
| Loom | `1.16.1` |
| Gradle | `9.4.1` |
| Mappings | none — 26.x ships a deobf jar (plain `implementation`, no remap) |

All Minecraft-internal calls are isolated in
[`MenuNav.java`](src/main/java/com/zoee/treehelper/hotm/MenuNav.java), ported from CoalFlipper's
verified 26.1.2 primitives.
