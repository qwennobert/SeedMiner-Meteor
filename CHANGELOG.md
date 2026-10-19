# Changelog

Every push builds a new version `0.2.0+build.<N>` with its own [release](../../releases). Newest first.
Settings are named as in the Meteor GUI. Defaults only change for settings you never changed yourself:
Meteor saves changed settings only.

## 0.2.0+build.34 — 2026-10-19

### Added
- `repair-with-mending` (Auto Tool, default on): repairs a worn Mending pickaxe while mining. Mending repairs
  from experience, and ancient debris gives none. Once a Mending pickaxe is below `repair-below` (default 20 %),
  ores within 16 blocks that give experience are mined first, with that pickaxe in hand: nether quartz in the
  Nether; coal, lapis, redstone, diamond and emerald in the overworld (more experience per ore counts more than
  a slightly shorter way). Other blocks are mined with that pickaxe too while experience orbs are flying to you,
  so none are lost. When it is back at `repair-until` (default 60 %), the module goes back to your ores. The
  status shows e.g. "Repairing pickaxe (15% -> 60%): Mining nether quartz at ...". With `server-anti-xray` only
  ores you can see are used, ores next to water or lava are skipped, and one Baritone can't reach is given up
  right away without a message (there are plenty of others). With none nearby, it keeps mining and tries again.
  Pickaxes with Silk Touch are never used for it, ores mined for experience don't count as mined.
- Tested on Paper with GrimAC in the Nether, with a Mending netherite pickaxe at 15 % and a second pickaxe:
  repaired to 60 % in 6.5 minutes (226 nether quartz, all with the Mending pickaxe in hand, no experience went
  to the experience bar), then back to ancient debris. No lava, no damage, no anticheat alerts.
- `leave-when-no-pickaxe` (Safety, default off, shown while `require-pickaxe` is on): also leave the server when
  no usable pickaxe is left.

### Changed
- Pickaxes never break anymore. `min-durability` (default 5 %) now really holds: before, when every pickaxe was
  below it, the one in your hand simply kept being used. Now the block is not broken and the module stops,
  also with `auto-tool` off (then Baritone picks the tool) and for blocks you break yourself while it runs.
- `require-pickaxe` only counts pickaxes that can still be used: when all of them are worn down to
  `min-durability`, the module stops ("All pickaxes in your hotbar are worn down to min-durability (5%),
  stopping.").
- Tested with a diamond pickaxe at 6 % and no Unbreaking: it stopped at 78 of 1561 durability left (just under
  5 %), also when enabled again; with `require-pickaxe` off the block was not broken either, and with
  `leave-when-no-pickaxe` the client left the server and stayed out.
- Settings tidied up: only **Targets** and **Safety** are expanded, all other groups start collapsed and are
  sorted by how often you need them (Auto Tool, Mining, Anti-Detection, Auto Compress, Render, Seed). The rarely
  needed `chunk-radius`, `follow-player`, `configure-baritone`, `allow-place` and `allow-parkour` moved to a new
  **Advanced** group. Your saved values move along with them (tested: all moved values arrive), and the groups
  are collapsed once when you update; after that they stay the way you leave them.
- `min-durability` is always shown now (it also protects with `auto-tool` off).
- The "only a fortune pickaxe" message now ends with ", stopping." like the other stop messages.

## 0.2.0+build.33 — 2026-10-15

### Fixed
- With Meteor's Auto Eat, the player kept mining with the food in hand instead of eating. Auto Eat switched
  to the food, the auto tool switched back to the pickaxe as soon as the next block started breaking, and
  every switch interrupted eating. Ores in reach were also still broken directly while Auto Eat paused
  Baritone. Now, while eating (Auto Eat or by hand), the module pauses completely: no breaking, no tool
  switching, Baritone waits; the status shows "Eating". Afterwards it continues, and the pause doesn't count
  towards `target-timeout` or `path-retries`.

## 0.2.0+build.32 — 2026-10-13

### Changed
- Auto compress now uses the same "full" as `stop-when-full`: it only crafts when the drops of the selected
  ores don't fit anymore (no empty slot **and** no stack of them with room). Before, it also started as soon
  as only one slot was empty, even if more diamonds still fit.
- `compress-at-free-slots` default 1 → **0**. 0 = only the rule above; above 0 it also starts earlier, as
  soon as fewer than that many slots are empty.
- Tested on Paper with GrimAC: inventory full of stone, three full diamond stacks, one crafting table and 160
  loose stone and tuff lying under the player. Junk was thrown out, 21 diamond blocks crafted, the table was
  back in the inventory afterwards, no anticheat alerts.

## 0.2.0+build.31 — 2026-10-13

### Fixed
- Auto compress: the crafting table could not be picked up again. With a full inventory, the stone that
  Baritone mines stays on the ground next to you, and the moment the table was placed, that stone was picked
  up into its slot. The same could happen to the slots needed for the crafted blocks.
- Auto compress started even when crafting freed no space at all (e.g. one stack of 64 diamonds becomes
  7 blocks + 1 diamond, one slot more). It now only starts when crafting really frees at least one slot.

### Added
- `drop-junk` (Auto Compress, default on): before placing the table, junk is thrown out (like Ctrl+Q in the
  inventory, towards the direction with the most open space so it isn't picked up again) until two slots
  are free. One stack of cobblestone/netherrack/cobbled deepslate is kept for Baritone. If the table still
  doesn't fit when picking it up, one more junk stack is thrown out. Off: auto compress only starts when no
  junk lies next to you.
- `junk-items`: what may be thrown out (stone, deepslate, tuff, diorite, granite, andesite, calcite,
  dripstone, gravel, flint, dirt, sand, netherrack, basalt, blackstone, soul sand/soil, end stone).

## 0.2.0+build.30 — 2026-10-13

### Changed
- Auto compress: the chat message now reads e.g. "Crafted 3x Block of Diamond." and the status no longer
  shows "crafting" without an item name for a moment.
- Tested on a Paper server with the GrimAC anticheat: placing, opening, crafting, closing, mining and
  picking up the table caused no alerts.

### Docs
- Auto-craft marked as done in the TODO list.

## 0.2.0+build.29 — 2026-10-13

### Fixed
- Auto compress never crafted: every spot counted as "no free spot for the crafting table", because the check
  for whether the top of the block is visible aimed exactly at its surface and a ray that ends on a surface
  doesn't hit it. It now aims just inside the block.

## 0.2.0+build.28 — 2026-10-13

### Added
- Auto Compress group (experimental, off by default, starts collapsed): when the inventory is (almost) full,
  the module crafts items into blocks to make room, the way a player would:
  1. take a crafting table from the inventory (moved into the hotbar like pressing a number key in the
     inventory, if needed),
  2. place it on a free spot next to you (turning the head smoothly, no snapping),
  3. open it, click the recipe in the recipe book (like shift-clicking it, the server fills the grid) and
     shift-click the result,
  4. close it, mine the table and pick it up again, then continue mining.
  There is a short random pause between every action.
- `auto-compress`: on/off.
- `compress-items`: which items are crafted into blocks (default diamond; also emerald, coal, redstone,
  lapis, raw iron/copper/gold, gold nuggets and quartz).
- `compress-at-free-slots` (default 1): start when this many free slots or fewer are left, or when the ore
  drops don't fit anymore.
- `show-crafting-gui` (default off = silent): the crafting window is really opened, clicked and closed, only
  not drawn on your screen. The server gets exactly the same packets either way.
- `action-delay-min` / `action-delay-max` (default 100 / 300 ms): pause between two actions.
- Without a crafting table, enough items, the recipe in your recipe book or a free spot for the table, nothing
  is crafted and `stop-when-full` / `leave-when-full` work as before.

## 0.2.0+build.27 — 2026-10-13

### Added
- This changelog. Every GitHub release now shows its section from this file plus all commits since the
  previous build.

## 0.2.0+build.26 — 2026-10-13

### Fixed
- The time limit message said "1 minutes" instead of "1 minute".

## 0.2.0+build.25 — 2026-10-13

### Added
- `leave-when-full` (Safety, default off, shown while `stop-when-full` is on): when the drops of the selected
  ores don't fit into the inventory anymore, also leave the server instead of only stopping.
- `time-limit` (Safety, minutes, default 0 = off): disable the module after that long, counted from
  enabling it.
- `leave-at-time-limit` (Safety, default off, shown while a time limit is set): also leave the server when
  the time is up.
- The time left ("Time left: 12:34") is shown on the module page, in the HUD and with `.seedminer`.
- Leaving works like Meteor's Auto Log: the disconnect screen shows the reason (e.g.
  "[Seed Miner] Time limit of 30 minutes reached."), and Auto Reconnect is turned off first so you don't
  join again right away.

## 0.2.0+build.24 — 2026-10-13

### Changed
- `stop-when-full` only stops when the drops of the selected ores really don't fit anymore: no free slot
  **and** no stack of them that still has room. Before, it stopped as soon as no slot was empty, e.g. with a
  single diamond in every slot.
- While some drops still fit, only ores whose drops fit are mined; the others are left for later.
- Drops that can't be picked up are no longer walked to.
- New message: "No room left for the drops of the selected ores, stopping."

## 0.2.0+build.23 — 2026-10-13

### Fixed
- Right after a teleport or a portal, ores were skipped with "Baritone could not reach ore" a few seconds
  after arriving: Baritone finds no path until the chunks around you have loaded. Failed paths no longer
  count while the chunks around you are still loading.

## 0.2.0+build.22 — 2026-10-13

### Fixed
- Safety: an ore in reach was broken directly even when water or lava touched it. Ancient debris often sits
  right next to lava, which then flowed towards the player (in a test the player stood in lava for 10
  seconds). Ores touching water or lava are now left to Baritone, which has its own rules for that.

## 0.2.0+build.21 — 2026-10-13

### Changed
- How the next target is picked: it is now the 3D distance with height differences weighted by
  `vertical-cost`. Before, the height was added on top of the horizontal distance, so an ore 10 blocks
  diagonally below counted like 30 blocks and lost against ores straight ahead that were much further away.
- `vertical-cost` default 2 → **1.5** (1 = plain 3D distance).
- `vein-priority` default 1 → **0.5**: a group of 4 ores may now be twice as far away as a single ore,
  instead of four times.
- Measured with the same world and start position: Nether 41 → 48 ancient debris in 4 minutes,
  overworld 38 → 52 diamond ores in 3 minutes. Targets more than twice as far away as the nearest ore:
  5 → 0 (Nether).

### Fixed
- When Baritone found no path, all retries were used up within a fraction of a second. It now waits 1 s,
  2 s, ... before asking again.

## 0.2.0+build.20 — 2026-10-13

### Fixed
- Ores above you were skipped with "could not reach" after about a second: while Baritone pillared up, the
  ore came into reach at the top of each jump, the module took over, the player fell back out of reach and
  Baritone was restarted. Every restart counted as a failed path. Breaking directly now only takes over
  while you stand on the ground, and restarting Baritone after such a break doesn't count as a retry.

### Changed
- `target-timeout` and `path-retries` only count while you get no closer to the ore (at least one block
  closer resets them), so long ways, common in the Nether, are no longer cut off halfway.
- `target-timeout` default 90 → **60** seconds without progress.
- New message: "Got no closer to ore at X, Y, Z for 60 seconds, skipping."

## 0.2.0+build.19 — 2026-10-13

### Changed
- The repository is now called SeedCracker-Meteor; the links in the mod list and in Meteor point there.

## 0.2.0+build.18 — 2026-10-12

### Added
- `vein-priority` (Mining): groups of ores (at most 2 blocks apart) are preferred over single ores.
  Before, the next target was simply the nearest ore.

## 0.2.0+build.17 — 2026-10-12

### Added
- Ores within reach (about 4 blocks) and visible are mined right from where you stand, without Baritone
  (status "... (in reach)"). Before, an ore right above your head was skipped when there were no blocks to
  pillar up with.
- Ores within reach but hidden behind other blocks: those blocks are mined first (status
  "... (uncovering)"), but never the block you stand on, and never a block next to water or lava or under
  sand or gravel.

### Fixed
- Drops of ores broken from a distance were left behind: the block is gone on your side right away, its
  drops only arrive a moment later. The module now waits up to a second for them.
- Collecting drops walks into the item's block instead of next to it. Standing next to an item can be just
  out of pickup range, which wasted the whole `collect-timeout`.

## 0.2.0+build.16 — 2026-10-12

### Added
- `server-anti-xray` (Targets, default on): servers like Paper hide ores with anti-xray and fill hidden
  blocks with fake ores. Only blocks that touch air are trusted, since anti-xray always shows those
  truthfully. Before, Baritone dug towards fake ores.

## 0.2.0+build.15 — 2026-10-12

### Added
- `mine-seen-ores` (Targets, default on): ores of the selected kinds visible within 5 blocks are mined even
  if the prediction missed them.

### Fixed
- Veins were left half mined: ores you can see within 5 blocks are now mined before anything else.

## 0.2.0+build.14 — 2026-10-12

### Added
- Auto Tool group: `auto-tool`, `fortune-only-on-ores`, `fortune-fallback`, `min-durability`. The module
  picks the tool for every block: the highest fortune for ores, the fastest tool without fortune for
  everything else, so the fortune pickaxe only wears down on ores. Baritone's own tool switching is turned
  off while the module runs.

### Fixed
- Predicted positions in bedrock (bottom of the world) were tried several times before being skipped. They
  now count as invalid as soon as the chunk is loaded.

## 0.2.0+build.13 — 2026-10-12

### Added
- `collect-drops`, `collect-radius`, `collect-timeout` (Mining): walk to the drops of mined ores before
  moving on. Before, Baritone often walked off during the pickup delay and left them behind.

## 0.2.0+build.12 — 2026-10-12

### Docs
- Auto-craft to blocks idea added to the TODO list.

## 0.2.0+build.11 — 2026-10-12

### Docs
- README, guide and TODO list translated to English.

## 0.2.0+build.10 — 2026-10-12

First published build (builds 1–9 were not published).

### Added
- Seed Miner module for Meteor Client on Minecraft 1.21.11: predicts ore positions from the world seed and
  mines them with Baritone.
- Seed from SeedMapper (Seed config, seeds saved per server, online database) or entered by hand
  (`seed-source`, `manual-seed`, `large-biomes`).
- Ore prediction with the cubiomes library bundled with SeedMapper, chunk by chunk on a background thread.
- `verify`: drop predicted positions that are air (SkipAir) or not the ore (Exact) once the chunk is loaded.
- Baritone mining with timeout, retries and safety stops (low health, full inventory, no pickaxe, players
  nearby).
- Anti-Detection group: `max-ores-per-minute`, random pauses, stone detours.
- Module page with status and the buttons Rescan, Skip target and Reset stats; HUD element; `.seedminer`
  command.
- Every build gets its own version and release.
- GUIDE.txt (step-by-step guide) and TODO.txt.

### Fixed
- An ore only counts as mined if the real ore block was there; ores mined on the way count as mined.
- `chunk-radius` and the Baritone options apply right away instead of after a restart.
- `max-distance` is measured horizontally, so ores far below you are not out of range.
