# Upgrader Wheel (Minecraft 1.12.2, Forge)

Put an item on the wheel, pick a more valuable item, spin. Win and your item becomes the one you picked. Lose and it is gone.
Made for RLCraft Dregora, works in any 1.12.2 Forge pack. Needed on both the client and the server.

## Build

1. Install **JDK 25** (for example Eclipse Temurin 25) and make it your `JAVA_HOME`; the build template runs Gradle on it.
   Minecraft itself is compiled with Java 8, which Gradle downloads by itself the first time.
2. In this folder run:
   - Windows: `gradlew.bat build`
   - macOS / Linux: `./gradlew build`
3. The first build downloads Minecraft, Forge and the mappings, so it takes a few minutes.
4. The mod is `build/libs/upgraderwheel-1.1.0.jar`. Put it in the `mods` folder of your RLCraft Dregora instance
   (CurseForge: right-click the profile, Open Folder, then `mods`). On a server, put it in the server's `mods` folder too.

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

Every crafting and smelting recipe is walked back to raw materials (CraftTweaker changes included, because values are
worked out after the server starts). Raw materials start from built-in values (iron ingot 256, gold 2048, diamond 8192,
ender pearl 1024, nether star 139264...). Common mod metals and gems get values through the ore dictionary
(`ingotSilver`, `gemSapphire`...). Anything with no recipe and no base value, which is most loot-only gear, gets a guess
from its rarity color, whether it stacks, and its durability.

Damaged gear is worth less in proportion to the durability left. Items holding other items (shulker boxes, backpacks)
must be emptied first. Items with NBT data (spawn eggs, potions, enchanted books) are never offered as results.

## Pack values

Recipes can only price things that are crafted. Boss and mob drops, loot and other raw materials get their value from
`config/upgraderwheel-pack.txt`, which comes with values for RLCraft Dregora (dragon bones, scales, blood and hearts,
other Ice and Fire creature drops, Scaling Health hearts). One line per item:

```
iceandfire:dragonbone = 8192        # all variants
iceandfire:dragon_skull:1 = 65536   # one variant
iceandfire:dragonscales_* = 12288   # * matches any text
srparasites:itemmobspawner_* = nowin   # never offered as a result
```

Everything crafted from a listed item is priced from it, so one line fixes a whole family of gear. Materials that
are in neither list are estimated from the gear they make (armor or weapons stronger than diamond are worth more than
diamond) or, failing that, guessed from their rarity. Delete the file to get the built-in list back.

## Tuning for your pack

| Command | Who | What |
|---|---|---|
| `/upgrader value` | anyone | Value of the held item and where it came from |
| `/upgrader set <value>` | op or single-player owner | Pin the held item's value in the config and re-price everything |
| `/upgrader unset` | op or single-player owner | Remove that pin |
| `/upgrader dump` | op or single-player owner | Write every value to `config/upgraderwheel-values.csv` (also done automatically) |
| `/upgrader reload` | op or single-player owner | Re-read the config and pack list and re-price everything |

`config/upgraderwheel.cfg` has the house edge, min/max chance, value overrides and multipliers, the rarity guesses,
blacklists (bet, result, both), an optional spin limit per time window, and client options (spin length, sounds,
double-click confirm). Ids are `modid:item`, `modid:item:meta`, `modid:*` or `ore:oreName`.

A good first pass for a big pack: open `config/upgraderwheel-values.csv`, sort by value, and fix anything that looks off
in the pack list or with `/upgrader set`. Entries marked `fallback` are rarity guesses and `gear` are estimates from
gear stats.

## Credits

- Odds formula, best-odds sorting, odds quick filters, amount stepper and the "free spin, then ease onto the win or
  lose arc" needle are adapted from [Item Upgrader](https://github.com/viniHNS/Item-Upgrader) by viniHNS (MIT),
  a mod for a different game. See `THIRD_PARTY_NOTICES.md`.
- The idea of pricing items by walking recipes comes from the Upgrader items mod for newer Minecraft versions.
  No code or art from it is used; the wheel UI and the item texture here are original.
- Build setup from [CleanroomMC TemplateDevEnv](https://github.com/CleanroomMC/TemplateDevEnv) (MIT).
