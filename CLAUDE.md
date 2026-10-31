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
  github.com/MalekBaghdadi/upgrader-wheel, PRIVATE until the README is done; see "Still to do" 4)
- Continuing on another machine: `git clone` the repo; every path below is specific to Malek's Windows PC (game
  instances, JDK, harness), so adjust them. Gradle fetches Java 8 itself; the harness server is NOT in git (see below).
- Game instance (TLauncher): `C:\Users\Malek Baghdadi\AppData\Roaming\.minecraft\versions\RLCraftDragoraEZ`
  - `mods\upgraderwheel-<version>.jar` (currently still the old 1.1.0 jar; the new build has NOT been installed)
  - `config\upgraderwheel.cfg`: odds, rules (`needItem`), value options, `overrides`
  - `config\upgraderwheel-pack.txt`: the player's own pack values (see below). `/upgrader reload` in game applies edits
  - `config\upgraderwheel-values.csv`: every value with source and recipe, rewritten each time values are worked out.
    If it is open in Excel the write fails silently (log: "Could not write upgraderwheel-values.csv"), leaving a STALE file.
  - `logs\latest.log`: look for "Upgrader values ready:"
  - `scripts\*.zs`: the pack's CraftTweaker scripts. `Shiv.zs` holds ~225 brewing recipes (Flight = Thick + Pixie Dust,
    Recall = Awkward + Warp Scroll, Wormhole = Recall + Eye of Ender, Thick = Water + Bone Mushroom...)
- Other instances on this PC: `C:\Users\Malek Baghdadi\curseforge\minecraft\Instances\RLCraft Dregora`, plus RLCraft ones.
  `%APPDATA%\.minecraft\mods` has a stray `upgraderwheel-1.0.0.jar` (shared folder, not the Dregora instance).

## Build and install

- JDK 25 runs Gradle (`JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"`); JDK 21 also installed.
  Gradle downloads Java 8 for compiling Minecraft. Build: `./gradlew build` (seconds after the first time).
- `update.bat` (close Minecraft first): deletes `mods\upgraderwheel-*.jar`, builds, copies the new jar into the
  TLauncher mods folder.
- Git Bash gotcha: heredocs with many quotes/backticks/backslashes break. Write Python edit scripts to a file and run
  them; open files with `newline=''` (repo is LF, `.gitattributes` has `eol=lf`, `.bat/.ps1` CRLF).

## Headless test harness (use it after every engine change)

A Forge 1.12.2 dedicated server with the whole Dregora pack (client-only mods removed) lives in the scratchpad of the
session that made it, on Malek's PC only (temp folder, may be gone): `C:\Users\MALEKB~1\AppData\Local\Temp\claude\C--Users-Malek-Baghdadi\5d3c2c4c-49bd-428b-9b13-1c66bbf6b952\scratchpad\server`
- `tools/test.sh` (run `SERVER=<server folder> sh tools/test.sh`): builds the mod, copies the jar into `server/mods`, boots the server with Java 8
  (`C:\Program Files\Java\jre1.8.0_351`), sends `stop` after "Done", retries up to 4 times (a third-party mixin crashes
  at startup about half the time; it is not ours), then the fresh CSV is `server/config/upgraderwheel-values.csv`.
  One cycle is about 1 minute. `PACK=<file>` copies a pack list in first.
- If that folder is gone, recreate: download `forge-1.12.2-14.23.5.2860-installer.jar`, `--installServer` into a new
  folder, copy the instance's `mods` (minus OptiFine, OptifineCITPatch, mod-director, RLFoliage, DynamicSurroundingsHuds,
  fancymenu, konkrete, BHMenu, EntityCulling, particleculling, ChunkAnimator, MoBends, SoundFilters, FancyBlockParticles,
  Neat, LegendaryTooltips, Controlling, MouseTweaks, itlt, LoadingScreens, tlskincape, ResourceLoader, betterbiomeblend,
  BlockOverlayFix, Toast Control, GooglyEyes, SpartanHUDBaubles, paintingselgui, InventoryTweaks), `config`, `scripts`;
  `eula=true`; server.properties `online-mode=false`, `level-type=FLAT`, `max-tick-time=-1`.
- Only kill java processes whose command line contains `forge-1.12.2-14.23.5.2860.jar` (Gradle daemons also run java).
- The GUI cannot be tested here (no way to drive the game window). Ask Malek to test client-side changes in game.

## Layout (src/main/java/com/malek/upgraderwheel)

- `UpgraderWheel`: @Mod, rebuilds values on FMLServerStartedEvent.
- `ModConfig`: classic Forge Configuration (`reload()` re-reads the file). `parseValue` accepts `2d`/`3i`/`1g`/`1e`
  (`i` = 1024 now). Value options include `potionValue` (8192), `brewingStepMultiplier` (1.25), `potionResults`,
  `builtInPackList`.
- `value/ValueRegistry`: the value engine (server side). Order in `build()`:
  creative stacks -> crafting recipes -> smelting -> `collectBrewing` (probes every Forge IBrewingRecipe with every potion
  type x bottle form and every creative stack; one ingredient makes 3 bottles) -> `addTippedArrowRecipes` ->
  overrides (cfg) -> pack lists -> BaseValues -> potion base values (water bottle 16) -> ore dictionary seeds ->
  `raiseSeedsToSmeltYield` (a seed that smelts into more than it's worth is raised, e.g. Diamond Pet Armor = 7 diamonds)
  -> guesses for raw items no recipe makes (FALLBACK) -> `deriveAll()` (`relax()` = cheapest recipe, repeated; then
  guesses for loop-only items) -> ores worth what they smelt into (SMELTS_INTO, target kept in `smeltTarget`) -> gear
  estimates -> multipliers -> catalog.
  Rules inside `relax()`: a recipe may not lower an item using an ingredient priced from that item (loops; SMELTS_INTO
  counts as depending on its output); guesses never lower a known value; a real recipe replaces a guessed value even if
  higher; smelting one seed into another seed never lowers it; an item follows its own recipe upward when an ingredient
  is re-priced (unless that loops); brewing steps x1.25; potions with an effect never go below the potion floor
  (potionValue for drinkable, x1.25 splash, x1.5625 lingering, and never below the lower form of the same type x1.25).
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
- `value/ItemPattern`: `mod:item`, `mod:item:meta`, `mod:*`, `ore:name`, globs `mod:prefix_*`, `*:*text*`, and
  `potion:|splash:|lingering:|arrow:` + `ns:type` (globs allowed, `potion:type` means minecraft).
- `value/BaseValues`: iron ingot/ore 1024, gold ingot/ore 2048, diamond 8192, emerald 16384; horse armor and chainmail
  scaled for iron 1024; iron-tier ore-dict metals scaled x4-ish (copper 512 ... silver 1536, cobalt 3072).
- `logic/UpgradeService`: bet checks, the roll, catalog packets. `logic/WheelAccess`: opening rules + login hint.
- `inventory/`, `client/GuiUpgrader` (wheel screen), `client/ClientEvents` (U key, value tooltips).
- `command/CommandUpgrader`: `/upgrader value|set|unset|reload|dump` (`set` on a held potion writes a `potion:` id).

## Value scale and Malek's decisions (2026-10-24)

- Iron ingot 1024 (raised from 256 on purpose; gold and diamond unchanged), gold 2048, diamond 8192 (1d).
- Potions: basic brewed potion 4 gold (= potionValue 8192); strong tier (Regeneration, Strength, Healing II) 2 diamonds;
  Recall Potion 4 diamonds (Wormhole derives to about 5); Flight 18 diamonds. Malek wants hard-to-make things valued
  HIGH. These pins are at the top of the built-in pack list (also Resistance/Absorption/Magic Shield/Escape/Curing/
  Curse Break 2d, 1-Up 3d, Return 4d, long Flight 22d).
- All brewable potions are winnable (drinkable, splash, lingering).
- GitHub repo should be PUBLIC (MIT). Friends play the same pack single-player, together on a server/LAN, and some in
  other 1.12.2 packs, some on the official launcher or CurseForge rather than TLauncher.

## Value-checking workflow (token-cheap; do NOT read the CSV row by row)

Crafted rows follow from their ingredients, so only guessed leaves (`source` = fallback/gear) need review.
- `tools/triage.py <values.csv> [top_n]`: counts guessed leaves per mod, ranks them by how many recipes depend on them,
  and lists crafted items priced far below their ingredients (noisy: seeds, cake, multi-output recipes are false alarms).
- `tools/gen_rules.py <values.csv>`: name/mod regex rules (metal tables, fuels, drops, `nowin` hides) that print pack-list
  lines for guessed items only. Its output is the "Generated" block at the end of `rlcraft_dregora.txt`; to regenerate,
  cut the file after the "Generated" header line, rerun against a fresh CSV, append, then re-add "Manual extras".
  Items are written with an explicit `:meta` (a bare id would cover every variant).
- Loop: `test.sh` on the harness -> triage -> add rules/pins -> `test.sh` again. Pins for progression items (XP, hearts,
  Bountiful Baubles, Artifacts, talismans) are the "Progression items" section of the pack list.

## State of the work (end of session 2026-10-27)

Committed locally: `e0332d6` baseline, `04e3a32` engine, `ec17844` progression pins + bulk rules + tools. Guessed leaves
went 2394 -> 2041 of 6842 rows. Verified on the harness: pins land (Copper Ingot 512, Heart Dust 512, XP bottle 512,
Magic Mirror 32768, Spectral Silt 2048).
**Installed in the game**: `upgraderwheel-1.2.0.jar` is in the TLauncher `RLCraftDragoraEZ\mods` (copied by hand).
Not yet tested in game by Malek (potion results on the wheel, tooltips, new prices).
`update.bat` failed when launched from Claude's shell ("gradlew.bat is not recognized" after it had already deleted the old
jar); probably fine double-clicked, but consider `call "%~dp0gradlew.bat"` and building before deleting the old jar.
Run Gradle from Bash with `JAVA_HOME` set, and quote paths containing "Malek Baghdadi" (use "$HOME/...").

Also committed (`cc3ac2a`, `3f62ea2`):
- `gradle.properties`: `mod_version = 1.2.0`, `mod_url` and `mod_update_json` pointing at github.com/MalekBaghdadi/upgrader-wheel.
- `update.json` (Forge update checker file, served from the repo's main branch).
- `install/install.ps1`: Windows installer. Finds instances (CurseForge, official/TLauncher incl. per-version folders,
  Prism/PolyMC/MultiMC, ATLauncher, GDLauncher, Modrinth App, Technic, FTB), sorts Dregora first, downloads the latest
  release jar from the GitHub API, removes old `upgraderwheel-*.jar`, copies the new one. Options `-ModsFolder`, `-Jar`,
  `-ListOnly` (or env `UPGRADERWHEEL_LISTONLY=1`), env `UPGRADERWHEEL_NOPAUSE=1`. Tested: detection on this PC (found 5
  instances), `irm | iex` mode, and replacing an old jar in a fake folder. Download path untested (no release yet).

Still to do, in rough order:
1. **Value scan, remaining**: done for the top-impact leaves and progression items. Left: Spartan Shields for absent mods
   (EnderIO/RFTools/Redstone Arsenal) guessed 2000000 (should be nowin), Lycanites/fish/Varied Commodities gear rows,
   Pixie Dust and Leech at 32, potion types with no brewing recipe all at the 8192 floor, optional RLCraft-wiki cross-check
   of boss/mob drops. The rest of the ~2000 guesses are mostly zero-dependent junk (flowers, leaves): leave them.
2. `install/Install-UpgraderWheel.bat` (double-click wrapper: runs `install.ps1` beside it, else downloads it from
   raw.githubusercontent) and `install/install.sh` for macOS/Linux (curl one-liner; find `~/Library/Application Support/
   minecraft`, `~/.minecraft`, Prism/CurseForge folders).
3. Rewrite README.md for friends: quick install (one-liner, .bat, manual per launcher: CurseForge, official launcher with
   Forge 1.12.2, TLauncher, Prism/MultiMC, ATLauncher...), server/LAN (mod on server and every client; values come from
   the server's config), other packs, uninstall, troubleshooting; then playing, values/tuning (new scale, potion lines,
   own pack file), building. Update CHANGELOG.md with 1.2.0.
4. The repo `MalekBaghdadi/upgrader-wheel` exists and `main` is pushed, but it was created PRIVATE (so work could move
   between machines before the README was ready). When step 3 is done: `gh repo edit MalekBaghdadi/upgrader-wheel
   --visibility public --accept-visibility-change-consequences` (the installer and `update.json` need public raw access;
   `gh auth status` on Malek's PC now lists the `workflow` scope, earlier notes said it did not), then create release
   `v1.2.0` with the jar
   (non-dev), `Install-UpgraderWheel.bat` and `install.ps1` attached. Then test the installer's download path.
5. Malek tests in game: potion results on the wheel (icon, name, search `@potioncore`, winning one gives the right
   potion), tooltips, `/upgrader value` on a potion, multiplayer if possible.
