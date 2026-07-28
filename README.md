# Tree Helper

*Mining progression made simple.*

A client-side Fabric mod for **Minecraft 26.1.2** (the version Hypixel hosts SkyBlock on) that
shows you the right **Heart of the Mountain** build for your chosen grind and tracks your powder
upgrades — right on the HOTM screen.

Tree Helper is **report-only**: it never clicks your tree, never resets anything, and never sends
commands for you. It highlights what to change; you make the changes. Everything is automatically
disabled outside Hypixel.

## Installing

Grab the latest jar from **[Modrinth](https://modrinth.com/mod/treehelper)** and drop it in your
`.minecraft/mods/` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api).
That's it — no config needed.

## Using it

1. Open your HOTM menu (`/hotm`) and pick a **Path** in the Tree Helper panel
   (HOTM, Mithril, Gemstone, or Glacite).
2. Press **Get Data** and right-click the arrows it highlights. Done.

The only chat command is `/tree help`, which repeats the two steps above.

## Features

- **Target build overlay** — your tree is diffed against the correct build for your path and
  HOTM level: one color for perks to enable, another for perks that shouldn't be on. When the
  tree matches, the overlay disappears. Colors are configurable via an in-panel RGB picker.
- **Powder Helper** — one card per powder type: live balance, the next perk upgrade in your
  path's plan (e.g. `Mole 25 ➜ 30`), its cost, and a progress bar toward affording it. The
  next upgrade's perk is boxed in the tree.
- **Live powder tracking** — balances update from the tab list while you mine, so you get a
  chat ping the moment you can afford the next upgrade. Each notification type can be set to
  Once, Repeat (30s–10m interval), or Off.
- **Grind advice** — one-time recommendations as you progress (reach HOTM 7 → start Gemstone;
  powder milestones → move on to Mithril / Glacite), with the same Once/Repeat/Off control.
- **Draggable GUIs** — both panels live on the open HOTM screen, drag by the header, minimize
  with the [–] button.

Settings persist in `config/treehelper.json`.

## Building from source (developers)

Most people should just [download from Modrinth](https://modrinth.com/mod/treehelper) — this is
only for working on the mod itself. Requires **JDK 25** (MC 26.1.2's Java target);
`gradle.properties` pins the Gradle daemon to a local JDK 25 path — change
`org.gradle.java.home` to your JDK 25 if it lives elsewhere.

```bash
./gradlew build         # Windows: .\gradlew.bat build
```

The jar lands in `build/libs/` as `treehelper-<version>-<mc version>.jar`.
Dev client: `./gradlew runClient`.

## Toolchain

| Piece | Version |
|-------|---------|
| Minecraft | `26.1.2` (Java 25) |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.155.2+26.1.2` |
| Loom | `1.16.1` |
| Gradle | `9.4.1` |
| Mappings | none — 26.x ships a deobf jar (plain `implementation`, no remap) |

All Minecraft-internal calls are isolated in
[`MenuNav.java`](src/main/java/com/zoee/treehelper/hotm/MenuNav.java).

## License

[MIT](LICENSE)
