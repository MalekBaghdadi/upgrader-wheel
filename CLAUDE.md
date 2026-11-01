# Upgrader Wheel: handoff notes

A Forge 1.12.2 mod written for **RLCraft Dregora**. Malek plays it through **TLauncher** on Windows (he also has a
CurseForge "RLCraft Dregora" instance). It is a 1.12.2 reimplementation of the "Upgrader items" idea from newer
Minecraft: put an item on a wheel, pick a more valuable result, spin. Chance = (1 - houseEdge) x 100 x betValue /
resultValue, capped at maxChance (90%), locked under minChance (1%). The server rolls; the client only animates.

Original code. Do not copy code or art from the "Upgrader items" mod (All Rights Reserved, no public source).
The odds formula, best-odds list and spin flow are adapted from viniHNS/Item-Upgrader (MIT, an Escape from Tarkov
mod); see THIRD_PARTY_NOTICES.md.

## Paths (Windows)

- Project: `C:\Users\Malek Baghdadi\Downloads\upgrader-wheel` (git repo, branch `main`, `origin` =
  github.com/MalekBaghdadi/upgrader-wheel, PUBLIC since 2026-10-31)
- Continuing on another machine: `git clone` the repo; every path below is specific to Malek's Windows PC (game
  instances, JDK, harness), so adjust them. Gradle fetches Java 8 itself; the harness server is NOT in git (see below).
- Game instance (TLauncher): `C:\Users\Malek Baghdadi\AppData\Roaming\.minecraft\versions\RLCraftDragoraEZ`
  - `mods\upgraderwheel-<version>.jar` (currently still the old 1.1.0 jar; the new build has NOT been installed)
  - `config\upgraderwheel.cfg`: odds, rules (`needItem`), value options, `overrides`
  - `config\upgraderwheel-pack.txt`: the player's own pack values (see below). `/upgrader reload` in game applies edits
  - `config\upgraderwheel-values.csv`: every value with source and recipe, rewritten each time values are worked out.
    If it is open in Excel the write fails silently (log: "Could not write upgraderwheel-values.csv"), leaving a STALE file.
  - `logs\latest.log`: look for "Upgrader values ready:"
  - `scripts\*.zs`: the pack's CraftTweaker scripts. `Shiv.zs` holds ~225 brewing recipes (Flight = Thick + *Familiar Fauna* Pixie Dust,
    Recall = Awkward + Warp Scroll, Wormhole = Recall + Eye of Ender, Thick = Water + Bone Mushroom...)
- Other instances on this PC: `C:\Users\Malek Baghdadi\curseforge\minecraft\Instances\RLCraft Dregora`, plus RLCraft ones.
  `%APPDATA%\.minecraft\mods` has a stray `upgraderwheel-1.0.0.jar` (shared folder, not the Dregora instance).

## Build and install

- JDK 25 runs Gradle (`JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"`); JDK 21 also installed.
  It has to be 25: the CurseForgeGradle plugin refuses older JVMs ("Dependency requires at least JVM runtime version 25").
  Gradle downloads Java 8 for compiling Minecraft. Build: `./gradlew build` (seconds after the first time); `gradlew`
  is not executable in git, so on Linux/macOS run `sh ./gradlew build`.
- `update.bat` (close Minecraft first): deletes `mods\upgraderwheel-*.jar`, builds, copies the new jar into the
  TLauncher mods folder.
- Git Bash gotcha: heredocs with many quotes/backticks/backslashes break. Write Python edit scripts to a file and run
  them; open files with `newline=''` (repo is LF, `.gitattributes` has `eol=lf`, `.bat/.ps1` CRLF).

## Headless test harness (use it after every engine change)

A Forge 1.12.2 dedicated server with the whole Dregora pack (client-only mods removed). Build one anywhere (Windows,
Linux, a cloud session) with `python tools/make_harness.py <folder> <java 8 binary>`: it downloads the pack zip from the
CurseForge CDN (Dregora v1.1.2b, the version Malek plays: same 6842 rows), resolves its 230 mods through api.cfwidget.com,
installs Forge and copies config/scripts. About 600 MB, a few minutes. Java 8 for Linux:
`https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jre/hotspot/normal/eclipse`; JDK 25 the same with `25`/`jdk`.
(Malek's PC had one in a temp folder: `C:\Users\MALEKB~1\AppData\Local\Temp\claude\C--Users-Malek-Baghdadi\5d3c2c4c-49bd-428b-9b13-1c66bbf6b952\scratchpad\server`.)
- `SERVER=<folder>/server [JAVA8=<java>] [GRADLE_JDK=<jdk 25>] sh tools/test.sh`: builds the mod, copies the jar in,
  boots the server, sends `stop` after "Done", retries up to 4 times (a third-party mixin crashes at startup about half
  the time on Windows; it never did on Linux), then the fresh CSV is `server/config/upgraderwheel-values.csv`.
  Java paths default to Malek's PC. One cycle is 1.5-2.5 minutes. `PACK=<file>` copies a pack list in first,
  `NOBUILD=1` skips the build.
- `ANALYZE=1` also starts the server with `-Dupgraderwheel.analyze=true` (`value/DropAnalyzer`): every mob kind is
  killed 200 times by a fake player (drops recorded and cancelled) -> `config/upgraderwheel-drops.csv` (health, armor,
  attack, boss, drops per kill), and every loot table (registered or shipped in a mod jar, not entity tables) is rolled
  100 times -> `config/upgraderwheel-loot.csv`. About 45 s extra. Throwaway worlds only: mods react to deaths.
- Only kill java processes whose command line contains `forge-1.12.2-14.23.5.2860.jar` (Gradle daemons also run java).
- The GUI cannot be tested here (no way to drive the game window). Ask Malek to test client-side changes in game.

## Layout (src/main/java/com/malek/upgraderwheel)

- `UpgraderWheel`: @Mod, rebuilds values on FMLServerStartedEvent.
- `ModConfig`: classic Forge Configuration (`reload()` re-reads the file). `parseValue` accepts `2d`/`3i`/`1g`/`1e`
  (`i` = 1024 now). Value options include `potionValue` (8192: loot-only potion types), `brewedPotionFloor` (1024),
  `brewingStepMultiplier` (1.25), `potionResults`, `builtInPackList`; rules include `hideJeiHidden`.
- `value/ValueRegistry`: the value engine (server side). Order in `build()`:
  creative stacks -> crafting recipes -> smelting -> `collectBrewing` (probes every Forge IBrewingRecipe with every potion
  type x bottle form and every creative stack; one ingredient makes 3 bottles) -> `addTippedArrowRecipes` ->
  overrides (cfg) -> pack lists -> BaseValues -> potion base values (water bottle 16) -> ore dictionary seeds ->
  `raiseSeedsToSmeltYield` (a seed that smelts into more than it's worth is raised, e.g. Diamond Pet Armor = 7 diamonds)
  -> mob drop estimates (DROP, only where nothing else priced it; `raiseDropsToYield`: a drop is worth at least what it
  turns straight into) -> guesses for raw items no recipe makes (FALLBACK; unpriced music discs = a vanilla disc) ->
  `deriveAll()` (`relax()` = cheapest recipe, repeated; then guesses for loop-only items) -> ores worth what they smelt
  into (SMELTS_INTO, target kept in `smeltTarget`) -> gear estimates -> multipliers -> `findUnobtainable` (only made by
  recipes needing an ingredient nothing provides, or by recipes needing such items) -> `computeCraftCosts` (cheapest
  recipe at final values, for the printer check) -> catalog (skips unobtainable, and JEI-hidden unless pinned).
  Rules inside `relax()`: a recipe may not lower an item using an ingredient priced from that item (loops; SMELTS_INTO
  counts as depending on its output); guesses never lower a known value; a real recipe replaces a guessed value even if
  higher (and on a tie: before that fix, recipe order decided whether every wooden item was "guessed", so two worlds
  with the same mods disagreed on ~120 values; now a fresh harness and an old one give identical CSVs, checked
  2026-10-31); smelting one seed into another seed never lowers it; an item follows its own recipe upward when an ingredient
  is re-priced (unless that loops); brewing steps x1.25; brewed potions with an effect never go below brewedPotionFloor
  (x1.25 splash, x1.5625 lingering, and never below the lower form of the same type x1.25). Loot-only potion types (no
  brewing recipe) are guessed at potionValue.
- Gear estimates (`estimateFromGear`/`gearValue`): gear is compared with the iron and diamond version of the same thing.
  Yardsticks are found by name shape (`longsword_umbrium` -> `longsword_diamond`/`longsword_iron`, `stone_sword` ->
  `diamond_sword`), then by class if the class has exactly one, then vanilla. Armor: vanilla iron/diamond of the slot,
  score = armor + toughness. Tools (`ItemTool`): score = mining level + 1 (a stone axe hits as hard as a diamond one).
  Weapons: attack damage. Value: diamond value x (score/diamond)^2 above diamond, log-linear between iron and diamond,
  iron x (score/iron)^8 below; x durability factor 0.7-1.5. Used to price loot-only gear and to back-solve guessed
  materials, and written to the CSV as `statvalue` for the gear check. A yardstick must be at most 10% guessed
  (`guessShare`), else one guessed scrap in a handle (wool, a saw) disqualifies every Spartan weapon.
- CSV columns after `recipe`: `craftcost` + `craftrecipe` (cheapest recipe at final values), `uses`, `statvalue`,
  `hidden` (unobtainable / jei), `basis` (weakest evidence anywhere below: guess < gear < drop < known),
  `guessshare` / `estimateshare` (0-1, how much of the value comes from guesses / from any estimate, weighed by value),
  then `value/ItemSignals`: kind, armor, toughness, damage, speed, durability, harvest, food, saturation, burn, rarity, stack.
- `value/JeiHidden`: reads `config/jei/itemBlacklist.cfg` (`hideJeiHidden`, default on). Dregora hides 830 items there:
  shields for absent mods, Varied Commodities armor, disabled features.
- `value/DropAnalyzer`: the harness-only analysis above. `tools/drops.py` turns its output into
  `pack/rlcraft_dregora_drops.txt` (loaded by `PackValues.loadEstimates()` as source DROP).
- Potion keys: potions keep their type in NBT, so `key(ItemStack)` returns a synthetic key (bit 40 set, form in bits
  24-27: 0 potion, 1 splash, 2 lingering, 3 tipped arrow, potion type id below). `idOf(key)` / `configId(stack)` give
  `potion:potioncore:flight`, `splash:...`, `lingering:...`, `arrow:...`. CSV rows use those ids.
- Catalog: brewed potions (and pack-pinned potion types) are results, except tipped arrows. `CatalogEntry.potion` carries
  the type; `PacketCatalog` sends a potion id string per entry, `PacketRoll` sends the target's potion id,
  `ClientCatalog.Entry.potion` / `find()` / `GuiUpgrader.findEntry` match on it.
- `value/PackValues`: built-in list `src/main/resources/assets/upgraderwheel/pack/rlcraft_dregora.txt` (always loaded,
  unless `builtInPackList=false`), then `config/upgraderwheel-pack.txt` (player's lines, win). New file = 
  `pack/user_template.txt` (first line is the format marker). Old 1.1.0 full copies are migrated once: lines matching
  `pack/legacy_defaults.txt` are dropped, own lines kept, old file saved as `.bak`. Tested on the harness.
  `pack/rlcraft_dregora_drops.txt` (generated, never edit by hand) holds mob drop estimates: weaker than pins.
- `value/ItemPattern`: `mod:item`, `mod:item:meta`, `mod:*`, `ore:name`, globs `mod:prefix_*`, `*:*text*`, and
  `potion:|splash:|lingering:|arrow:` + `ns:type` (globs allowed, `potion:type` means minecraft).
- `value/BaseValues`: iron ingot/ore 1024, gold ingot/ore 2048, diamond 8192, emerald 16384; horse armor and chainmail
  scaled for iron 1024; iron-tier ore-dict metals scaled x4-ish (copper 512 ... silver 1536, cobalt 3072).
- `logic/UpgradeService`: bet checks, the roll, catalog packets. `logic/WheelAccess`: opening rules + login hint.
- `inventory/`, `client/GuiUpgrader` (wheel screen), `client/ClientEvents` (U key, value tooltips).
- `command/CommandUpgrader`: `/upgrader value|set|unset|reload|dump` (`set` on a held potion writes a `potion:` id).

## Value scale and Malek's decisions (2026-10-24)

- Iron ingot 1024 (raised from 256 on purpose; gold and diamond unchanged), gold 2048, diamond 8192 (1d).
- Malek wants hard-to-make things valued HIGH, and (2026-11-01) everything endgame the most expensive.
- Potions (2026-11-01, replaces the 2026-10-24 pins): EVERYTHING stays bettable, potions included, so a brewed potion is
  worth its brewing cost (x1.25 a step), at least brewedPotionFloor = 1 iron. Brewable potions are not pinned (a pin
  above brewing cost is a printer); Flight is dear through its ingredient: Familiar Fauna Pixie Dust 5d (pixies have
  spawn weight 1, group 1) -> Flight about 2d. Recall follows its brewing cost (~400). Loot-only potions keep pins:
  Resistance/Absorption/Magic Shield 2d, 1-Up 3d, Return 4d, long Flight 3d. The 1-iron floor leaves a small printer
  (cheapest brews ~140, worth 1.25 iron): `brewedPotionFloor = 0` removes it.
- All brewable potions are winnable (drinkable, splash, lingering).
- 2026-11-01, pack list sections "Boss loot" and "Endgame and rare items": Skeleton King drops Famine/War/Halo
  Necklace/Dream Catcher 40d, "Death" 16d, Crown of Rule 10d; Forgotten Items golem armor 32d a piece. Sentient armor is
  endgame: Blood Tear 8d (Walking Head drop, 1 per kill) makes the Sentient Core ~66d and each piece 73-79d; Sentient
  weapons (no recipe) 80d. Living armor stays recipe-priced (~13d chest). Boss soulstones (Undead/Aberration/Demon)
  24d, other soulstones 6d. Xat race rings 16d, Stones of Inertia 8d, Miner's Ring nowin, Famine Boots and Midnight
  Mourne 4d, Bound Axe/Pickaxe and Gamble Pickaxe 4d, troll weapons and Dread Knight's Sword 4d. Risk to check in game:
  if Walking Heads are common, Blood Tears at 8d print diamonds. Above Sentient sit only Xat's Arcing Orb (211d) and race
  foods (~95-105d): their recipes really eat several diamond blocks. balance.py section 6 flags the Sentient Core
  (66d vs gear stats 3d): intended.
- GitHub repo should be PUBLIC (MIT). Friends play the same pack single-player, together on a server/LAN, and some in
  other 1.12.2 packs, some on the official launcher or CurseForge rather than TLauncher.

## How values are balanced (token-cheap; NEVER read the CSV row by row)

Reading items one by one failed (1.4M tokens for under a quarter). Instead every value is checked against independent
evidence by tools, and only disagreements are read; fixes are rules or a few pins, never item-by-item edits.

Principles:
- The wheel is fair only if value = what it costs to get one more. Gambling converts any value into any item at ~95%,
  so an item worth more than its cost is a printer (craft it, bet it, win diamonds) and one worth less than its power
  lets players skip progression. Gates (needing a brewing stand) do not count, marginal cost does.
- Evidence, strongest first: hand pins (Malek's decisions; dragon parts) > recipes from known values > gear stats
  (armor/damage vs vanilla iron and diamond) > mob drop analysis > name rules (`gen_rules.py`) > rarity guess.
  Pins win in the engine; drop estimates can be lowered by recipes; guesses never lower anything known.
- Mob drop analysis is wrong for mobs whose stats come from spawn data: Ice and Fire creatures are measured as babies
  (a sea serpent rates like a cow), so Ice and Fire stays hand-pinned. Lycanites levels, Champions also not seen.

The loop (each run is ~2 minutes on the harness):
1. `ANALYZE=1 sh tools/test.sh` when mobs/loot may have changed (new pack version), else plain `sh tools/test.sh`.
2. `python tools/drops.py <values.csv> <drops.csv> > src/main/resources/assets/upgraderwheel/pack/rlcraft_dregora_drops.txt`
   when pins or drops changed, then run again (two passes: freed items must show up as guesses first).
   Model: kill value = C x effective health/20 (EH = health x (1+armor/20) x (1+attack/20)), C fitted on vanilla monsters
   (~460), bosses x~17 fitted on the Wither, square root above EH 300; known drops take their share, unpriced drops split
   the rest (at least half the kill value) by drop rate; cheapest mob wins; global drops (on >25% of mobs) and rare
   cross-mod drops skipped. The linear curve was fitted so gear crafted from drops lands near its stat value (x1.3-1.8;
   a square curve gave x16). Refit with a grid search if the pack changes (see the KNOBS comment).
3. `python tools/balance.py <values.csv> [--diff <older.csv>] [--top N]` (~70 lines): ladder (prizes per tier x kind),
   printers (value > 2x cheapest recipe; shows the recipe), gear at iron strength or better vs its stats, jackpots
   (valuable prizes resting on estimates), guesses by dependents, materials vs what their gear implies.
4. Fix by rule: pin the raw material the evidence points at (an ore, a drop), not the crafted item; add `nowin` for a
   family; change a tool knob. Then run again and `--diff` against the previous CSV.
- `tools/gen_rules.py <values.csv> [drops.csv]`: name/mod regex rules (metal tables, foods, `nowin` hides) for guessed
  items only; its output is the "Generated" block of `rlcraft_dregora.txt` (to regenerate, cut the file after the
  "Generated" header, rerun against a fresh CSV, append, re-add "Manual extras"). It never pins mob drops (pass the
  drops CSV) or ores; items are written with an explicit `:meta`.

## State of the work (end of session 2026-10-31, cloud session)

This session built the evidence tooling above and ran it on a Linux harness made with `make_harness.py`. Against the
baseline (end of 2026-10-27): 989 items no longer offered (832 JEI-hidden incl. the 2,000,000 ghost shields, 157
unobtainable Spartan/Better Survival gear); 150-160 mob drops priced from the analysis (SRParasites, Lycanites, Lava
Cow, Defiled Lands...); strong gear far below its stats 105 -> 7 and far above 190 -> 0; materials 3x off their gear
17 -> 0. Pack list: 17 name-rule pins on mob drops and 106 NuclearCraft fuel pins removed; Defiled Lands ores (umbrium
1i, scarlite 1g, hephaestite 256), cincinnasite ore 256, Weta Jaw 4i, Ice and Fire mid-tier materials raised to what
their gear says (myrmex chitin 2i, troll leather 2i/4i, death worm chitin 2560, sea serpent scales 6i). The dragon pins
already agreed with their gear (x0.8-1.4). Not yet in game; the jar is not installed.

Potions and the questionable items were decided on 2026-11-01 (see Malek's decisions above); built and checked on the
harness, branch `progression-balance`, not yet tested in game.

Still to do, in rough order:
1. Malek tests in game (item 7 below), especially Walking Head rarity (Blood Tear 8d) and potion prices.
2. Enchantments: `enchantmentLevelValue` is a flat 256/level on bets only (books and enchanted gear are never prizes).
   A per-enchantment value (rarity, max level, treasure, curses at 0) must stay below what an enchanting-table roll
   costs (3 levels at 30 = ~300 XP points = ~22k in XP bottles at 512 each, plus 3 lapis = 2.6k) or it becomes a printer.
3. Remaining report lines: a few Lava Cow daggers below their stats; ~1000 guessed prizes are junk/stone tier (32).
4. `install/install.sh` for macOS/Linux (curl one-liner; find `~/Library/Application Support/minecraft`,
   `~/.minecraft`, Prism/CurseForge folders). `install/Install-UpgraderWheel.bat` exists (2026-11-01; runs `install.ps1`
   beside it, else downloads it from raw.githubusercontent) but was never run on Windows.
5. README.md: install, valuation and pack-list sections were corrected for 1.2.0 (2026-11-01); still to add for friends:
   per-launcher manual steps (official launcher with Forge 1.12.2, TLauncher...), other packs, uninstall, troubleshooting.
6. Releases: `.github/workflows/release.yml` publishes one when a `v*` tag is pushed (builds on JDK 25, attaches the jar,
   `install.ps1` and `Install-UpgraderWheel.bat`, notes = that version's CHANGELOG section; fails if the tag and
   `mod_version` differ). v1.2.0 was tagged 2026-11-01 before any in-game test. Test the installer's download path.
   (`update.bat` failed from Claude's shell, consider `call "%~dp0gradlew.bat"` and building before deleting the old jar.)
7. Malek tests in game: potion results on the wheel (icon, name, search `@potioncore`, winning one gives the right
   potion), tooltips, `/upgrader value` on a potion, multiplayer if possible.

Also in git from before: `gradle.properties` (`mod_version = 1.2.0`, `mod_url`, `mod_update_json`), `update.json`
(Forge update checker), `install/install.ps1` (Windows installer: finds CurseForge, official/TLauncher, Prism/PolyMC/
MultiMC, ATLauncher, GDLauncher, Modrinth App, Technic, FTB instances; Dregora first; downloads the latest release jar;
options `-ModsFolder`, `-Jar`, `-ListOnly`/`UPGRADERWHEEL_LISTONLY=1`, `UPGRADERWHEEL_NOPAUSE=1`; download path untested).
