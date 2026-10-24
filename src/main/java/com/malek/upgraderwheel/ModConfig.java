package com.malek.upgraderwheel;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * config/upgraderwheel.cfg. Uses the classic Configuration API so /upgrader reload can re-read the file from disk.
 */
public final class ModConfig {

    public static final String CAT_ODDS = "odds";
    public static final String CAT_VALUES = "values";
    public static final String CAT_RULES = "rules";
    public static final String CAT_CLIENT = "client";

    private static final String OVERRIDES_COMMENT = "Fixed values. They win over recipes and also feed into recipes (an override on a scale changes the armor made from it).\n"
            + "Format: id=value. id is modid:item, modid:item:meta, modid:* (whole mod) or ore:oreDictName.\n"
            + "Later lines win. Examples:\n"
            + "  iceandfire:dragonbone=6000\n"
            + "  minecraft:wool:14=40\n"
            + "  ore:ingotSilver=600\n"
            + "In game: hold an item and run /upgrader set <value> (op, or the owner of a single-player world).\n"
            + "Raw materials for the whole modpack are listed in config/upgraderwheel-pack.txt.";

    private static Configuration config;

    // Odds
    public static double houseEdge = 0.10;
    public static double minChance = 1.0;
    public static double maxChance = 90.0;

    // Values
    public static String[] valueOverrides = new String[0];
    public static String[] valueMultipliers = new String[0];
    public static double enchantmentLevelValue = 256;
    public static boolean allowFallbackTargets = true;
    public static double minTargetValue = 16;
    public static double fallbackCommon = 32;
    public static double fallbackUncommon = 1024;
    public static double fallbackRare = 8192;
    public static double fallbackEpic = 32768;
    public static double fallbackUnstackableMultiplier = 4;
    public static double fallbackPerDurabilityPoint = 2;

    // Rules
    public static String[] blacklist = new String[0];
    public static String[] targetBlacklist = new String[0];
    public static String[] betBlacklist = new String[0];
    public static int attemptLimit = 0;
    public static int attemptWindowSeconds = 60;
    public static boolean logRolls = true;
    public static boolean needItem = false;

    // Client
    public static double spinSeconds = 3.0;
    public static boolean sounds = true;
    public static boolean confirmUpgrade = true;

    private ModConfig() {
    }

    public static void init(File file) {
        config = new Configuration(file);
        load();
    }

    /** Re-reads the file from disk. */
    public static void reload() {
        if (config == null) {
            return;
        }
        config = new Configuration(config.getConfigFile());
        load();
    }

    private static void load() {
        Configuration c = config;
        c.load();

        c.setCategoryComment(CAT_ODDS, "chance (in percent) = (1 - houseEdge) x 100 x betValue / resultValue, capped at maxChance.");
        houseEdge = clamp(c.get(CAT_ODDS, "houseEdge", 0.10,
                "Share of value the house keeps on every roll. 0.10 means a roll is worth 90% of what you bet on average.", 0.0, 0.95).getDouble(), 0.0, 0.95);
        minChance = clamp(c.get(CAT_ODDS, "minChance", 1.0,
                "Results whose chance would be lower than this (in percent) are locked.\n"
                        + "This stops a single block of dirt from being a lottery ticket for anything. Set to 0 to allow any odds.", 0.0, 100.0).getDouble(), 0.0, 100.0);
        maxChance = clamp(c.get(CAT_ODDS, "maxChance", 90.0,
                "Highest possible chance in percent, so even a downgrade is never guaranteed.", 1.0, 100.0).getDouble(), 1.0, 100.0);

        c.setCategoryComment(CAT_VALUES, "How items are valued. Values come from walking crafting and smelting recipes back to raw materials.\n"
                + "Raw materials use built-in base values (iron ingot = 256, diamond = 8192). Items with no recipe and no base value get a guess from their rarity.\n"
                + "Use /upgrader value while holding an item to see its value and where it came from, and /upgrader dump to export every value.");
        valueOverrides = c.get(CAT_VALUES, "overrides", new String[0], OVERRIDES_COMMENT).getStringList();
        valueMultipliers = c.get(CAT_VALUES, "multipliers", new String[0],
                "Multiply final values without feeding into recipes. Same id format as overrides. Example: iceandfire:*=1.5").getStringList();
        enchantmentLevelValue = Math.max(0, c.get(CAT_VALUES, "enchantmentLevelValue", 256.0,
                "Extra value per enchantment level on the item you bet (per item in the stack), so a Sharpness V book is worth\n"
                        + "the book plus 5 x this. 0 ignores enchantments.", 0.0, 1.0E9).getDouble());
        allowFallbackTargets = c.getBoolean("allowFallbackTargets", CAT_VALUES, true,
                "Offer items that have no recipe and no base value (priced by the rarity guess) as results.\n"
                        + "Most loot-only gear falls here. Turn off if the guesses make rare loot too cheap.");
        minTargetValue = Math.max(0, c.get(CAT_VALUES, "minTargetValue", 16.0,
                "Results worth less than this are not listed (keeps dirt, planks and sticks out of the list).", 0.0, 1.0E12).getDouble());
        fallbackCommon = positive(c.get(CAT_VALUES, "fallbackCommon", 32.0, "Guess for a common item with no recipe and no base value.", 0.0001, 1.0E12).getDouble(), 32);
        fallbackUncommon = positive(c.get(CAT_VALUES, "fallbackUncommon", 1024.0, "Guess for an uncommon (yellow name) item.", 0.0001, 1.0E12).getDouble(), 1024);
        fallbackRare = positive(c.get(CAT_VALUES, "fallbackRare", 8192.0, "Guess for a rare (aqua name) item, or a custom mod rarity.", 0.0001, 1.0E12).getDouble(), 8192);
        fallbackEpic = positive(c.get(CAT_VALUES, "fallbackEpic", 32768.0, "Guess for an epic (purple name) item.", 0.0001, 1.0E12).getDouble(), 32768);
        fallbackUnstackableMultiplier = positive(c.get(CAT_VALUES, "fallbackUnstackableMultiplier", 4.0,
                "The rarity guess is multiplied by this for items that do not stack (gear, trinkets).", 0.0001, 1.0E6).getDouble(), 4);
        fallbackPerDurabilityPoint = Math.max(0, c.get(CAT_VALUES, "fallbackPerDurabilityPoint", 2.0,
                "Gear with durability is guessed at least this much per durability point.", 0.0, 1.0E6).getDouble());

        c.setCategoryComment(CAT_RULES, "What can be bet or won, and limits. Ids use the same format as overrides (modid:item, modid:item:meta, modid:*, ore:name).");
        blacklist = c.get(CAT_RULES, "blacklist", new String[0], "Items that can neither be bet nor won.").getStringList();
        targetBlacklist = c.get(CAT_RULES, "targetBlacklist", defaultTargetBlacklist(),
                "Items that can never be won. Items without a creative tab and items with NBT data are never offered anyway.").getStringList();
        betBlacklist = c.get(CAT_RULES, "betBlacklist", new String[0], "Items that can never be bet.").getStringList();
        attemptLimit = Math.max(0, c.getInt("attemptLimit", CAT_RULES, 0, 0, 100000,
                "Most spins a player can do per window. 0 means no limit."));
        attemptWindowSeconds = Math.max(1, c.getInt("attemptWindowSeconds", CAT_RULES, 60, 1, 86400,
                "Length of the attempt-limit window in seconds."));
        logRolls = c.getBoolean("logRolls", CAT_RULES, true, "Write one line to the server log per spin.");
        needItem = c.getBoolean("needItem", CAT_RULES, false,
                "false: everyone can open the wheel from the start with a key (U by default, change it in Options > Controls).\n"
                        + "true: the wheel needs the Upgrader item. Craft it (gold ingots in the corners, diamonds on the edges, an anvil\n"
                        + "in the middle), then right-click it or press the key while carrying it.\n"
                        + "The crafting recipe only exists when this is true; restart the game after changing it.");

        c.setCategoryComment(CAT_CLIENT, "Only read by your own game client.");
        spinSeconds = clamp(c.get(CAT_CLIENT, "spinSeconds", 3.0, "How long the needle takes to settle after the server answers.", 1.2, 15.0).getDouble(), 1.2, 15.0);
        sounds = c.getBoolean("sounds", CAT_CLIENT, true, "Ticking and result sounds.");
        confirmUpgrade = c.getBoolean("confirmUpgrade", CAT_CLIENT, true, "Click the Upgrade button twice to spin, so a stray click never costs you an item.");

        if (c.hasChanged()) {
            c.save();
        }
    }

    private static String[] defaultTargetBlacklist() {
        return new String[]{
                "minecraft:bedrock",
                "minecraft:barrier",
                "minecraft:command_block",
                "minecraft:chain_command_block",
                "minecraft:repeating_command_block",
                "minecraft:command_block_minecart",
                "minecraft:structure_block",
                "minecraft:structure_void",
                "minecraft:mob_spawner",
                "minecraft:end_portal_frame",
                "minecraft:knowledge_book",
                "minecraft:monster_egg",
                "minecraft:farmland",
                "minecraft:grass_path"
        };
    }

    /**
     * Adds, replaces or (value == null) removes the override line for one id, saves the file and updates the field.
     * Returns true when a line was removed.
     */
    public static boolean setOverride(String id, Double value) {
        Property property = config.get(CAT_VALUES, "overrides", new String[0], OVERRIDES_COMMENT);
        List<String> lines = new ArrayList<>();
        boolean removed = false;
        for (String line : property.getStringList()) {
            int eq = line.indexOf('=');
            String lineId = eq < 0 ? line.trim() : line.substring(0, eq).trim();
            if (lineId.equalsIgnoreCase(id)) {
                removed = true;
                continue;
            }
            lines.add(line);
        }
        if (value != null) {
            lines.add(id + "=" + formatNumber(value));
        }
        property.set(lines.toArray(new String[0]));
        config.save();
        valueOverrides = property.getStringList();
        return removed;
    }

    /**
     * A value typed by a person: a plain number, or a count of a reference item: 2d = 2 diamonds,
     * 3i = 3 iron ingots, 1.5g = 1.5 gold ingots, 1e = 1 emerald. NaN when unreadable.
     */
    public static double parseValue(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replace("_", "");
        if (t.isEmpty()) {
            return Double.NaN;
        }
        double unit = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'd' || last == 'i' || last == 'g' || last == 'e') {
            unit = last == 'd' ? 8192 : last == 'i' ? 256 : last == 'g' ? 2048 : 16384;
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            double value = Double.parseDouble(t) * unit;
            return value >= 0 && !Double.isInfinite(value) ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double positive(double value, double fallback) {
        return value > 0 && !Double.isNaN(value) && !Double.isInfinite(value) ? value : fallback;
    }
}
