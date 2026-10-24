# Upgrader Wheel: handoff notes

A Forge 1.12.2 mod written for **RLCraft Dregora**, played through **TLauncher** on Windows. It is a 1.12.2
reimplementation of the "Upgrader items" idea from newer Minecraft: put an item on a wheel, pick a more valuable
result, spin. Chance = (1 - houseEdge) x 100 x betValue / resultValue, capped at maxChance (90%), locked under
minChance (1%). The server rolls; the client only animates.

Original code. Do not copy code or art from the "Upgrader items" mod (All Rights Reserved, no public source).
The odds formula, best-odds list and spin flow are adapted from viniHNS/Item-Upgrader (MIT, an Escape from Tarkov
mod); see THIRD_PARTY_NOTICES.md.

## Paths (Windows)

- Project: `C:\Users\Malek Baghdadi\Downloads\upgrader-wheel`
- Game instance (TLauncher): `C:\Users\Malek Baghdadi\AppData\Roaming\.minecraft\versions\RLCraftDragoraEZ`
  - `mods\upgraderwheel-<version>.jar`
  - `config\upgraderwheel.cfg`: odds, rules (`needItem`), value options, `overrides`
  - `config\upgraderwheel-pack.txt`: pack value list (see below). Edits apply in game with `/upgrader reload`
  - `config\upgraderwheel-values.csv`: every value with its source, written each time values are worked out
  - `logs\latest.log`: look for "Upgrader values ready:"
  - `scripts\*.zs`: the pack's CraftTweaker scripts (Dregora changes many recipes)

## Build and install

- JDK 25 runs Gradle; JDK 21 is also needed (the RetroFuturaGradle build plugin wants it for one step).
  Gradle downloads Java 8 for compiling Minecraft itself. The template pinned the Azul vendor; that was removed from
  build.gradle because Azul's download failed.
- `update.bat` (close Minecraft first): deletes `mods\upgraderwheel-*.jar`, runs `gradlew build`, copies the new jar
  into the mods folder. If the jar is locked, Minecraft is still running.
- Value-only changes need no rebuild: edit `config\upgraderwheel-pack.txt`, then `/upgrader reload` in game.

## Layout (src/main/java/com/malek/upgraderwheel)

- `UpgraderWheel`: @Mod, rebuilds values on FMLServerStartedEvent.
- `ModConfig`: classic Forge Configuration (`reload()` re-reads the file). `parseValue` accepts `2d`/`3i`/`1g`/`1e`.
- `value/ValueRegistry`: the value engine (server side). Order in `build()`:
  overrides (cfg) -> pack list -> BaseValues (vanilla raw materials, EMC-like: iron 256, gold 2048, diamond 8192)
  -> ore dictionary seeds -> guess for raw items no recipe makes (FALLBACK, rarity based) -> `deriveAll()`
  (`relax()` = cheapest recipe, repeated; then guesses for loop-only items, most-used first)
  -> ores worth what they smelt into -> gear estimates (armor/weapons vs the diamond version; guessed materials get
  their share) -> multipliers -> catalog (creative-tab stacks, no NBT, not blacklisted, >= minTargetValue).
  Rules inside `relax()`: a recipe may not lower an item using an ingredient priced from that item (loops); guesses
  never lower a known value; a real recipe replaces a value that rests on guesses even if higher.
- `value/PackValues`: reads `config/upgraderwheel-pack.txt`; default copied from
  `src/main/resources/assets/upgraderwheel/pack/rlcraft_dregora.txt` when missing.
- `value/ItemPattern`: `mod:item`, `mod:item:meta`, `mod:*`, `ore:name`, globs `mod:prefix_*`, `*:*text*`.
- `logic/UpgradeService`: bet checks, the roll, catalog packets. `logic/WheelAccess`: opening rules + login hint.
- `inventory/`: container with one bet slot. `client/GuiUpgrader`: the wheel screen. `client/ClientEvents`:
  U key, value tooltips while the wheel is open.
- `command/CommandUpgrader`: `/upgrader value|set|unset|reload|dump` (set/unset/reload/dump: op or single-player owner).

## Access

`needItem=false` (default): press U from the start. `needItem=true`: the Upgrader item (recipe only exists then,
via the `upgraderwheel:item_mode` recipe condition in `recipes/_factories.json`), and U works while carrying one.

## Status and open work

- Values are the main open issue. Many modded items are still mispriced. Workflow: load the world, read
  `config\upgraderwheel-values.csv`, find `fallback` (guess) and odd `recipe` rows, add lines to the pack list, `/upgrader reload`.
  The player (Malek) gives tiers in plain words ("Lycanite boss drops are endgame, about 10 diamonds").
- Pack list so far covers Ice and Fire (dragon parts: bone 1d, scales 1.5d, blood 4d, heart/skull 8d, eggs 16d,
  other creature drops), Scaling Health shards, enchanted books, and `nowin` entries for spawn eggs, SRParasites
  spawners/wands/lures, Champions eggs, Lycanites elemental spawns. Not yet reviewed: Lycanites drops and charges,
  SRParasites drops, Bountiful Baubles, Trinkets and Baubles, RLArtifacts, Forgotten Items, Defiled Lands,
  BetterNether, NuclearCraft ingots, Varied Commodities coins, Dregora ContentTweaker items.
- Seen in game before the 1.1.0 fixes: emeralds valued 32 (modded emerald ores smelting into them; fixed by the
  guess rules), a modded ingot at 32, a cyan gem at 32, baubles (Mixed Color / Ender Dragon Scale) at 128.
- Unexplained: Ice and Fire flamed swords reported a max stack size > 1 in this pack; the catalog now forces any
  item with durability to stack size 1.
- Nothing here has been run under a test harness. Verify in game after each build.
