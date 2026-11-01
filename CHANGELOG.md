# Changelog

## [1.2.0] - 2026-11-01

### Added
- Windows installer: `Install-UpgraderWheel.bat` on the release page (or the PowerShell one-liner in the README) finds
  your Minecraft instances and installs or updates the mod.
- Potions are valued and offered by potion type (drinkable, splash, lingering), with every brewing recipe (vanilla,
  mods and CraftTweaker) walked; tipped arrows are valued but not offered.
- Value scale: iron ingot 1024, gold 2048, diamond 8192. Built-in values for progression items (XP, hearts, trinkets).
- Items nobody can get are never offered: things only made by recipes that need a missing material (Spartan weapons for
  metals the pack lacks), and things the modpack hides from JEI (`hideJeiHidden`, on by default).
- Gear is checked against its stats at every tier (iron and diamond versions of the same weapon as yardsticks, tools by
  mining level), so materials that only make gear get sensible values.
- Mob drop values for RLCraft Dregora worked out from how hard each mob is and how often it drops them.
- Unpriced modded music discs are worth what a vanilla disc is worth.
- Brewed potions are worth what they cost to brew, at least `brewedPotionFloor` (1 iron); `potionValue` now only
  prices potion types no brewing recipe makes. Brewable potions are no longer pinned (a pin above the brewing cost let
  anyone with a brewing stand make diamonds); Flight is dear through its Pixie Dust instead.
- RLCraft Dregora values decided by Malek: Sentient armor and weapons at the top (70-80 diamonds), boss loot,
  soulstones, race rings and other rare items.
- Forgiving search: words in any order, small typos ("strenght"), filler words ("of", "the") ignored; if nothing has
  every word, the best partial matches are shown ("ring of strength" finds Potion of Strength).
- Dearer: XP Tome 20 diamonds, Jar of Experience 8, Bottle o' Enchanting 2048, Armor Underwear liners x4 (Ozzy liners
  come out x4 from their recipes), Magma Chunk x2, Golden Book Wyrm Scale 64 diamonds.
- `upgraderwheel-values.csv` has more columns: cheapest recipe and its cost, stat value, why an item is hidden, how much
  of a value rests on estimates, and the item's own stats.

### Fixed
- Values could differ between two worlds with the same mods (Bookshelf 3936 or 384, Purple Dye 440 or 16): when two
  recipes cost the same, whichever came first decided whether an item counted as guessed. Real recipes now win ties.
- Diamonds were worth 2341 through a cheap modded recipe.
- Spartan Weaponry wood and stone weapons were compared with the wrong diamond weapon.

## [1.1.0] - 2026-10-23

### Added
- Open the wheel with a key (U by default, in Options > Controls) from the start of the game. `needItem = true` in the
  config brings back the Upgrader item: the recipe only exists then, and the key works while you carry one.
- `config/upgraderwheel-pack.txt`: values for a modpack's raw materials (boss and mob drops, loot), with a built-in
  list for RLCraft Dregora (Ice and Fire dragon parts, creature drops, Scaling Health hearts). `nowin` entries keep
  spawn eggs, wands and other broken-when-blank items out of the result list.
- Item patterns with `*` wildcards (`iceandfire:dragonscales_*`, `*:*spawn_egg*`).
- Materials that recipes cannot price are estimated from the gear made from them (armor and weapons stronger than
  diamond are worth more than diamond), and loot-only gear from its own stats.
- Every value is written to `config/upgraderwheel-values.csv` whenever values are worked out.
- `/upgrader value` shows the recipe a value comes from.
- The owner of a single-player world can use `/upgrader set|unset|reload|dump` without cheats.
- Enchantment levels add to bet value (256 per level by default).
- Values can be written as counts of a reference item: `2d` = 2 diamonds, `3i` = 3 iron ingots, `1g` = 1 gold ingot,
  `1e` = 1 emerald (pack list, config and `/upgrader set`).
- While the wheel is open, item tooltips show what each listed item is worth.
- Ores and other items with no value of their own are worth what they smelt into.

### Fixed
- Recipe loops (a material and its storage block) no longer drive values towards zero: a dragon bone was worth a
  ninth of a guessed dragon bone block.
- Items with durability are only ever won one at a time.
- Guessed items no longer cheapen real values: modded emerald ores (no value of their own) smelting into emeralds made
  emeralds worth 32.

## [1.0.0] - 2026-10-23

### Added
- Upgrader item that opens the upgrade wheel.
- Item values worked out from crafting and smelting recipes, with base values for raw materials and a rarity-based guess for loot-only items.
- Searchable result list sorted by best odds, quick odds filters, amount stepper for stackable results.
- Server-side rolls, optional attempt limit, roll logging.
- `/upgrader` command to inspect, override, reload and export values.
