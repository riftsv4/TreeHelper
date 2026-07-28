# Tree Helper

*Mining progression made simple.*

A client-side Fabric mod for **Minecraft 26.1.2** that
shows you the optimal **Heart of the Mountain** build for your chosen grind and tracks your powder
upgrades — right on the HOTM screen.

## Installing

Grab the latest jar from **[Modrinth](https://modrinth.com/mod/hotmgrindsavior)** and drop it in your
`.minecraft/mods/` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api).

## Using da mod

1. Open your HOTM menu (`/hotm`) and pick a **Path** in the Tree Helper panel
   (HOTM, Mithril, Gemstone, or Glacite).
2. Press **Get Data** and right-click the arrows it highlights.

## Features

- **Optimal build overlay** — your tree is diffed against the optimal build in accordance with **[The Mining Cult Discord Server](https://discord.gg/ndtpNmRKPx)** based on your HOTM level even providing helpful overlay you can folllow along.
- **Powder Helper** — one card per powder type: live balance, the next perk upgrade in your
  path's plan (e.g. `Mole 25 ➜ 30`), its cost, and a progress bar toward affording it. The
  next upgrade's perk is boxed in the tree. Once again sourced directly from **[The Mining Cult Discord Server](https://discord.gg/ndtpNmRKPx)**
- **Live powder tracking** — balances update from the tab list while you mine, so you get a
  chat ping the moment you can afford the next upgrade. Each notification type can be set to
  Once, Repeat (30s–10m interval), or Off.
- **Grind advice** — one-time recommendations as you progress (reach HOTM 7 → start Gemstone;
  powder milestones → move on to Mithril / Glacite), with the same Once/Repeat/Off control.

Settings persist in `config/treehelper.json`.

## Downloading

Most people should just [download from Modrinth](https://modrinth.com/mod/hotmgrindsavior)

If for whatever reason you dont trust Modrinth you can build from the github ig 

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
