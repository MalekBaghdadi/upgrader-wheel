# Upgrader Wheel (Minecraft 1.12.2, Forge)

Put an item on the wheel, pick a more valuable item, spin. Win and your item becomes the one you picked. Lose and it is gone.
Made for RLCraft Dregora, works in any 1.12.2 Forge pack. Needed on both the client and the server.

## Install

- **Windows, easiest:** download `Install-UpgraderWheel.bat` from the
  [latest release](https://github.com/MalekBaghdadi/upgrader-wheel/releases/latest) and double-click it. It finds your
  Minecraft instances (CurseForge, official launcher, TLauncher, Prism, MultiMC, ATLauncher, GDLauncher, Modrinth,
  Technic, FTB), asks which one, and puts the newest Upgrader Wheel in its `mods` folder, replacing older versions.
  Or in PowerShell: `irm https://raw.githubusercontent.com/MalekBaghdadi/upgrader-wheel/main/install/install.ps1 | iex`
- **By hand:** download `upgraderwheel-<version>.jar` from the latest release and drop it into the instance's `mods`
  folder (CurseForge: right-click the profile, Open Folder, `mods`). Remove any older `upgraderwheel-*.jar`.
- **Servers and LAN:** the mod goes on the server and on every player's game. Values come from the server's config.

## Build

1. Install **JDK 25** (for example Eclipse Temurin 25) and make it your `JAVA_HOME`; the build template runs Gradle on it.
   Minecraft itself is compiled with Java 8, which Gradle downloads by itself the first time.
2. In this folder run:
   - Windows: `gradlew.bat build`
   - macOS / Linux: `./gradlew build`
3. The first build downloads Minecraft, Forge and the mappings, so it takes a few minutes.
4. The mod is `build/libs/upgraderwheel-<version>.jar` (not the `-dev` one). Put it in your instance's `mods` folder.

To try it in a dev world first: `gradlew runClient`.

## Playing

- Press **U** to open the wheel (change the key in Options > Controls > Upgrader Wheel). It's available from the start.
- Prefer it as an item? Set `needItem=true` in `config/upgraderwheel.cfg` and restart. Then you craft the **Upgrader**
  (gold ingots in the corners, diamonds on the edges, an anvil in the middle) and right-click it, or press U while carrying it.
- Put the item you want to bet in the left slot. Its value shows under it.
- Pick a result on the right. The list is sorted best odds first and starts at the first real upgrade.
  Search by name, or `@modid` to filter by mod. Right-click the search box to clear it.
- `~75%`, `~50%`, `~25%`, `~10%` show only results near those odds (for stackable results it picks the amount that lands there).
- Stackable results have `-` / `+` (Shift for 10, or scroll the mouse over them).
- Click **Upgrade**, then click again to confirm. The server decides; the wheel shows it.

Chance = (1 - house edge) x 100 x your value / result value, capped at 90%. With the default 10% house edge,
a spin is worth 90% of what you bet on average. Odds under 1% are locked so that a single dirt block cannot be a
lottery ticket for a Nether Star.

## How items are valued

An item should be worth what it takes to get one more: then the wheel cannot be used to skip progression, and nothing
can be crafted cheaply and bet for profit.

- Every crafting, smelting and brewing recipe is walked back to raw materials (CraftTweaker changes included, because
  values are worked out after the server starts). An item is worth its cheapest recipe.
- Raw materials start from built-in values (iron ingot 1024, gold 2048, diamond 8192, ender pearl 1024, nether star
  139264...), the ore dictionary for mod metals and gems, and the pack value list.
- Loot-only gear, and materials that only go into gear, are priced from the gear's stats next to iron and diamond
  gear: armor as strong as diamond armor is worth about as much.
- Brewed potions are worth what they cost to brew (each brewing step a bit more), at least 1 iron.
- Anything left (rare loot) gets a guess from its rarity color, whether it stacks, and its durability.
- Items nobody can get are never offered: things the modpack hides from JEI, and things only made from materials that
  are not in the pack. Spawn eggs and enchanted books are never offered either.

Damaged gear is worth less in proportion to the durability left. Items holding other items (shulker boxes, backpacks)
must be emptied first.

## Pack values

The mod comes with values for RLCraft Dregora: dragon parts, creature and boss drops, Scaling Health hearts, trinkets,
rare potions, and mob drops priced from how hard each mob is to kill. Your own lines go in
`config/upgraderwheel-pack.txt` and win over the built-in ones. One line per item:

```
iceandfire:dragonbone = 8192        # all variants
iceandfire:dragon_skull:1 = 8d      # one variant; 8d = 8 diamonds (also 3i iron, 2g gold, 1e emerald)
iceandfire:dragonscales_* = 12288   # * matches any text
potion:potioncore:revival = 3d      # a potion type (splash:, lingering: for the others)
srparasites:itemmobspawner_* = nowin   # never offered as a result
```

Everything crafted from a listed item is priced from it, so one line fixes a whole family of gear. Price the raw
material, not the crafted item: an item worth more than its recipe can be crafted and bet for profit.

## Tuning for your pack

| Command | Who | What |
|---|---|---|
| `/upgrader value` | anyone | Value of the held item and where it came from |
| `/upgrader set <value>` | op or single-player owner | Pin the held item's value in the config and re-price everything |
| `/upgrader unset` | op or single-player owner | Remove that pin |
| `/upgrader dump` | op or single-player owner | Write every value to `config/upgraderwheel-values.csv` (also done automatically) |
| `/upgrader reload` | op or single-player owner | Re-read the config and pack list and re-price everything |

`config/upgraderwheel.cfg` has the house edge, min/max chance, value overrides and multipliers, the rarity guesses,
potion values (`brewedPotionFloor`, `potionValue` for loot-only potions), blacklists (bet, result, both), whether
JEI-hidden items are offered, an optional spin limit per time window, and client options (spin length, sounds,
double-click confirm). Ids are `modid:item`, `modid:item:meta`, `modid:*` or `ore:oreName`.

`config/upgraderwheel-values.csv` lists every value with where it came from (`recipe`, `pack`, `drop` for mob drop
estimates, `gear` for stat estimates, `fallback` for rarity guesses), its cheapest recipe and its stats. Modpack makers:
`tools/balance.py` in this repository reads it and lists only the values that disagree with the evidence.

## Credits

- Odds formula, best-odds sorting, odds quick filters, amount stepper and the "free spin, then ease onto the win or
  lose arc" needle are adapted from [Item Upgrader](https://github.com/viniHNS/Item-Upgrader) by viniHNS (MIT),
  a mod for a different game. See `THIRD_PARTY_NOTICES.md`.
- The idea of pricing items by walking recipes comes from the Upgrader items mod for newer Minecraft versions.
  No code or art from it is used; the wheel UI and the item texture here are original.
- Build setup from [CleanroomMC TemplateDevEnv](https://github.com/CleanroomMC/TemplateDevEnv) (MIT).
