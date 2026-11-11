# Seed Miner – Meteor Client addon (Minecraft 26.3)

Seed Miner predicts where ores are from the world seed and lets Baritone mine them automatically.
The seed is taken from [SeedMapper](https://github.com/xpple/SeedMapper), or you enter it yourself.
Everything can be configured in the Meteor GUI.

A detailed step-by-step guide is in [GUIDE.txt](GUIDE.txt), all changes per version are in
[CHANGELOG.md](CHANGELOG.md).

> Meant for your own (self-hosted) server or singleplayer. On public servers this is cheating and will get you banned.

## How it works

1. **Seed**: The addon asks SeedMapper for the seed of the current world, the same way SeedMapper does for its own
   commands. SeedMapper's order (`SeedResolutionOrder`) applies: first `/sm:config Seed set <seed>`, then the seeds
   saved per server (`/sm:config SavedSeeds add <seed>`), then SeedMapper's online database.
   You can also enter the seed in the module settings. On your own server you get it with `/seed`.
2. **Prediction**: The addon uses the cubiomes library bundled with SeedMapper (same ore logic as `/sm:highlight block`)
   to compute the ore positions of all chunks around you on a background thread.
3. **Verification**: In loaded chunks, predicted positions that are actually air (caves, already mined) are dropped.
4. **Mining**: Baritone walks or digs to the next ore and mines it, then moves on to the next one. Groups of ores
   (veins) are preferred over single ores, height differences weigh more. New chunks are scanned while you move. Ores within reach are mined right from where you stand;
   if other blocks hide them, those are mined first (unless water, lava, sand or gravel would come in).

## Installation

Put these into your `mods` folder (all for **26.3**):

| Mod | Required | Notes |
|---|---|---|
| [Fabric Loader](https://fabricmc.net/use/) ≥ 0.19.5 | yes | |
| [Fabric API](https://modrinth.com/mod/fabric-api) | yes | needed by SeedMapper |
| [Meteor Client](https://meteorclient.com/) 26.3 | yes | |
| [SeedMapper](https://modrinth.com/mod/seedmapper/versions) **2.33.0 or newer** (for 26.3) | yes | provides the ore prediction (cubiomes) |
| [Baritone (Meteor build)](https://maven.meteordev.org/#/snapshots/meteordevelopment/baritone) 26.3 | for mining | without Baritone, ores are only highlighted |
| Seed Miner (`seed-miner-<version>.jar`) | yes | this addon, from the latest [release](../../releases/latest) |

**Java 25 or newer is required** (Minecraft 26.3, Meteor and SeedMapper need it). Recent launchers bring it along;
otherwise set a Java 25+ runtime for the instance in your launcher (e.g. [Temurin](https://adoptium.net/temurin/releases/)).

Every build has its own version (e.g. `0.2.0+build.12`). Remove the old Seed Miner jar from `mods` before adding a new one.

## Usage

1. Open the Meteor GUI (Right Shift) → category **Seed Miner** → module **seed-miner**.
2. Set the seed source and the ores you want, then enable the module.
3. Optional: add the **seed-miner** element in Meteor's HUD editor (status, number of ores, mined/skipped).

The module page also shows the status and the seed in use, plus the buttons *Rescan*, *Skip target* and *Reset stats*.

**Eating:** Baritone doesn't eat. Use Meteor's Auto Eat: while you eat, the module pauses (no breaking, no tool
switching) and continues afterwards.

**Pickaxe:** no tool is ever used below `min-durability` (default 5 %), so it never breaks. With a Mending pickaxe the
module repairs it on its own: once it is below `repair-below` (20 %), ores nearby that give experience (nether quartz;
coal, lapis, redstone, diamond, emerald) are mined first, with that pickaxe in hand, until it is back at `repair-until`
(60 %). Ancient debris gives no experience, so mining it alone never repairs anything. Without a usable pickaxe the
module stops, and with `leave-when-no-pickaxe` it also leaves the server.

**Stopping:** disable the module (bind a key to it). Baritone's own `#stop` does not stop the module for good,
because the module simply gives Baritone the next ore.

### Settings

The groups you need most, **Targets** and **Safety**, are expanded; all others start collapsed (click a group title
to expand it). Options that only matter while another one is on are hidden until then.

| Group | Setting | Description |
|---|---|---|
| Targets | `blocks` | Ores: coal, copper, iron, gold, redstone, lapis, diamond, emerald, raw iron/copper blocks, nether gold, quartz, ancient debris |
| | `min-y` / `max-y` | Height range |
| | `ore-veins` | Include large copper/iron ore veins (slower) |
| | `mine-seen-ores` | Also mine ores of the selected types that are visible near you, even if the prediction missed them |
| | `server-anti-xray` | The server may use anti-xray: only blocks that touch air are trusted, hidden blocks can be fake ores (default on) |
| | `verify` | `Off`, `SkipAir` (default), `Exact` (also drop positions that are not the ore; with `server-anti-xray` only blocks that touch air are checked) |
| Safety | `stop-when-full` | Disable when the drops of the selected ores don't fit anymore (no free slot and no stack of them with room left) |
| | `leave-when-full` | Also leave the server then (Auto Reconnect is turned off) |
| | `time-limit` / `leave-at-time-limit` | Disable after N minutes (0 = off), optionally leave the server; the time left shows in the HUD |
| | `min-health` | Disable at low health |
| | `require-pickaxe` | Disable when there is no usable pickaxe in the hotbar: none at all, or all worn down to `min-durability` |
| | `leave-when-no-pickaxe` | Also leave the server then |
| | `pause-near-players` / `player-range` | Pause while other players are nearby |
| Auto Tool | `auto-tool` | Pick the tool for every block Baritone breaks (tools must be in the hotbar) |
| | `fortune-only-on-ores` | Fortune pickaxe only on ores, a pickaxe without fortune for everything else |
| | `fortune-fallback` | Use the fortune pickaxe for other blocks too if there is no other pickaxe (otherwise the module stops) |
| | `min-durability` | Never use a tool with less durability left than this (percent), so it never breaks; also with `auto-tool` off |
| | `repair-with-mending` | Repair a worn Mending pickaxe by mining ores that give experience (see below, default on) |
| | `repair-below` / `repair-until` | Start repairing below this durability (default 20 %), stop at this one (default 60 %) |
| Mining | `auto-mine` | Mine with Baritone, or only highlight |
| | `collect-drops` / `collect-radius` / `collect-timeout` | Walk to the drops of mined ores before moving on, so none are left behind |
| | `vein-priority` | How much groups of ores are preferred over single ores (0 = always the nearest ore, 0.5 = a group of 4 may be twice as far away, 1 = least walking per ore) |
| | `vertical-cost` | How much height differences count when picking the next ore (1 = plain 3D distance) |
| | `max-distance` | Maximum horizontal distance to an ore |
| | `target-timeout` | Skip an ore when you get no closer to it for N seconds |
| | `path-retries` | Retries when Baritone can't find a path (start again from 0 whenever you got closer) |
| | `disable-when-done` | Disable when no ores are left |
| Anti-Detection | `anti-detection` | Master switch; the options below only show while it is on |
| | `max-ores-per-minute` | Mine at most this many ores within 60 seconds, then wait (0 = no limit) |
| | `random-pause` / `pause-min` / `pause-max` | Random pause after each ore (seconds) |
| | `stone-detours` / `detour-chance` / `detour-distance` | After an ore, mine a random nearby stone block with X % chance |
| Auto Compress | `auto-compress` | Experimental: when the ore drops don't fit anymore, place a crafting table from the inventory, craft blocks with the recipe book, mine the table again |
| | `compress-items` | Items crafted into blocks (default diamond) |
| | `compress-at-free-slots` | 0 = start when the ore drops don't fit anymore (same "full" as `stop-when-full`); above 0 also start when fewer slots than that are empty |
| | `drop-junk` / `junk-items` | Throw out junk (stone, deepslate, gravel, ...) first so the crafting table fits back in; one stack of building blocks is kept |
| | `show-crafting-gui` | Off = silent: the window is really opened and used, just not drawn |
| | `action-delay-min` / `action-delay-max` | Random pause between two actions (ms) |
| Render | `render`, `render-limit`, `shape-mode`, colors, `tracer` | Highlighting of the predicted ores |
| Seed | `seed-source` | `Auto` (manual seed, otherwise SeedMapper), `Manual`, `SeedMapper` |
| | `manual-seed` | Seed as a number (or text, like in the world creation screen); leave empty to use the seed from SeedMapper |
| | `large-biomes` | Enable if the world was created with the Large Biomes preset |
| Advanced | `chunk-radius` | Scan radius in chunks |
| | `follow-player` | Keep scanning new chunks while you move |
| | `configure-baritone` | Sets `allowBreak=true` plus `allow-place` / `allow-parkour`; restored afterwards |

### Command

```
.seedminer                 status
.seedminer seed <seed>     set the manual seed
.seedminer clear-seed      clear the manual seed
.seedminer rescan          recompute all predictions
.seedminer skip            skip the current ore
```

## Limitations

- The prediction doesn't know whether an ore touches air, so many positions predicted from far away are wrong
  (especially diamonds next to caves, and coal, copper and emerald). In loaded chunks `verify` corrects this.
  Wrong targets are noticed once Baritone gets there and are skipped. They count as *invalid*, not as mined.
- If the server runs a different version than 26.3 (e.g. via ViaFabricPlus), the prediction won't match.
- Baritone mines by pathing: it digs its way to the ore and breaks it as part of the path. To get up to an ore it may
  need blocks to pillar with, so carry some cobblestone. Ores within reach don't need that.

## Building

```bash
./gradlew build
```

The jar ends up in `build/libs/`. Gradle itself has to run on Java 25 or newer (Fabric Loom requires it).
`./gradlew runClient` starts a dev client with Meteor, Baritone, SeedMapper and Fabric API.
