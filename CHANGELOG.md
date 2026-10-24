# Changelog

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
