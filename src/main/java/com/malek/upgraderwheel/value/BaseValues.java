package com.malek.upgraderwheel.value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in starting values for raw materials: things you mine, farm, kill or loot rather than craft.
 * Recipes then price everything made from them. The scale: iron ingot 1024, gold ingot 2048, diamond 8192.
 * Seeds can only be lowered by a cheaper recipe; config overrides are the way to pin a value.
 */
final class BaseValues {

    /** Key "modid:item" means every variant; "modid:item@meta" means one variant. */
    static final Map<String, Double> ITEMS = new LinkedHashMap<>();

    /** Ore dictionary names for common mod materials. Applied only to items with no other base value. */
    static final Map<String, Double> ORES = new LinkedHashMap<>();

    /** Potion types, for the drinkable bottle; splash, lingering and stronger versions come from brewing recipes. */
    static final Map<String, Double> POTIONS = new LinkedHashMap<>();

    private BaseValues() {
    }

    private static void item(String id, double value) {
        ITEMS.put(id, value);
    }

    private static void ore(double value, String... names) {
        for (String name : names) {
            ORES.put(name, value);
        }
    }

    static {
        // Ground and stone
        item("minecraft:cobblestone", 1);
        item("minecraft:stone@1", 1);
        item("minecraft:stone@3", 1);
        item("minecraft:stone@5", 1);
        item("minecraft:dirt", 1);
        item("minecraft:grass", 1);
        item("minecraft:mycelium", 1);
        item("minecraft:sand", 1);
        item("minecraft:gravel", 4);
        item("minecraft:netherrack", 1);
        item("minecraft:end_stone", 1);
        item("minecraft:soul_sand", 49);
        item("minecraft:ice", 1);
        item("minecraft:packed_ice", 4);
        item("minecraft:snowball", 1);
        item("minecraft:clay_ball", 16);
        item("minecraft:flint", 4);
        item("minecraft:obsidian", 64);
        item("minecraft:mossy_cobblestone", 4);
        item("minecraft:stonebrick@1", 4);
        item("minecraft:stonebrick@2", 2);
        item("minecraft:monster_egg", 1);

        // Plants
        item("minecraft:log", 32);
        item("minecraft:log2", 32);
        item("minecraft:leaves", 1);
        item("minecraft:leaves2", 1);
        item("minecraft:sapling", 32);
        item("minecraft:tallgrass", 1);
        item("minecraft:deadbush", 1);
        item("minecraft:double_plant", 32);
        item("minecraft:yellow_flower", 16);
        item("minecraft:red_flower", 16);
        item("minecraft:brown_mushroom", 32);
        item("minecraft:red_mushroom", 32);
        item("minecraft:brown_mushroom_block", 32);
        item("minecraft:red_mushroom_block", 32);
        item("minecraft:vine", 8);
        item("minecraft:waterlily", 16);
        item("minecraft:cactus", 8);
        item("minecraft:reeds", 32);
        item("minecraft:pumpkin", 144);
        item("minecraft:melon", 16);
        item("minecraft:chorus_plant", 64);
        item("minecraft:chorus_flower", 192);
        item("minecraft:chorus_fruit", 192);
        item("minecraft:sponge", 256);
        item("minecraft:web", 12);

        // Farming
        item("minecraft:wheat", 24);
        item("minecraft:wheat_seeds", 16);
        item("minecraft:beetroot", 24);
        item("minecraft:beetroot_seeds", 16);
        item("minecraft:carrot", 64);
        item("minecraft:potato", 64);
        item("minecraft:poisonous_potato", 8);
        item("minecraft:apple", 128);
        item("minecraft:egg", 32);
        item("minecraft:nether_wart", 24);
        item("minecraft:dye@0", 16);
        item("minecraft:dye@3", 64);

        // Ores and minerals
        item("minecraft:coal@0", 128);
        item("minecraft:coal_ore", 128);
        item("minecraft:iron_ore", 1024);
        item("minecraft:iron_ingot", 1024);
        item("minecraft:gold_ore", 2048);
        item("minecraft:gold_ingot", 2048);
        item("minecraft:diamond", 8192);
        item("minecraft:diamond_ore", 8192);
        item("minecraft:emerald", 16384);
        item("minecraft:emerald_ore", 16384);
        item("minecraft:dye@4", 864);
        item("minecraft:lapis_ore", 864);
        item("minecraft:redstone", 64);
        item("minecraft:redstone_ore", 320);
        item("minecraft:quartz", 256);
        item("minecraft:quartz_ore", 256);
        item("minecraft:glowstone_dust", 384);
        item("minecraft:prismarine_shard", 256);
        item("minecraft:prismarine_crystals", 512);

        // Mob drops
        item("minecraft:feather", 48);
        item("minecraft:leather", 64);
        item("minecraft:rabbit_hide", 16);
        item("minecraft:rabbit_foot", 128);
        item("minecraft:string", 12);
        item("minecraft:spider_eye", 128);
        item("minecraft:rotten_flesh", 24);
        item("minecraft:bone", 144);
        item("minecraft:gunpowder", 192);
        item("minecraft:ender_pearl", 1024);
        item("minecraft:blaze_rod", 1536);
        item("minecraft:ghast_tear", 4096);
        item("minecraft:slime_ball", 32);
        item("minecraft:shulker_shell", 2048);
        item("minecraft:dragon_breath", 1024);
        item("minecraft:porkchop", 64);
        item("minecraft:beef", 64);
        item("minecraft:chicken", 64);
        item("minecraft:mutton", 64);
        item("minecraft:rabbit", 64);
        item("minecraft:fish", 64);
        item("minecraft:skull@0", 1024);
        item("minecraft:skull@1", 16384);
        item("minecraft:skull@2", 1024);
        item("minecraft:skull@3", 1024);
        item("minecraft:skull@4", 1024);
        item("minecraft:skull@5", 32768);

        // Filled buckets have no recipe: a bucket plus what is in it
        item("minecraft:water_bucket", 769);
        item("minecraft:lava_bucket", 832);
        item("minecraft:milk_bucket", 784);

        // Loot
        item("minecraft:saddle", 192);
        item("minecraft:name_tag", 192);
        item("minecraft:experience_bottle", 256);
        // Horse armor that smelts back into more than this is raised to what it smelts into
        item("minecraft:iron_horse_armor", 5120);
        item("minecraft:golden_horse_armor", 8192);
        item("minecraft:diamond_horse_armor", 32768);
        item("minecraft:chainmail_helmet", 5120);
        item("minecraft:chainmail_chestplate", 8192);
        item("minecraft:chainmail_leggings", 7168);
        item("minecraft:chainmail_boots", 4096);
        item("minecraft:golden_apple@1", 32768);
        item("minecraft:totem_of_undying", 16384);
        item("minecraft:elytra", 32768);
        item("minecraft:nether_star", 139264);
        item("minecraft:dragon_egg", 262144);
        for (String record : new String[]{"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"}) {
            item("minecraft:record_" + record, 2048);
        }

        // A bottle of water: brewing starts here (potions with an effect are worth at least potionValue)
        POTIONS.put("minecraft:water", 16.0);

        // Common mod metals and gems (ore dictionary). Base metals are priced against iron (1024)
        ore(512, "ingotCopper", "oreCopper", "ingotAluminum", "oreAluminum", "ingotAluminium", "oreAluminium", "ingotZinc", "oreZinc");
        ore(640, "ingotBronze", "ingotBrass");
        ore(768, "ingotTin", "oreTin", "ingotConstantan");
        ore(1024, "ingotLead", "oreLead", "ingotInvar", "gemMalachite", "gemAmber");
        ore(1280, "ingotNickel", "oreNickel", "ingotOsmium", "oreOsmium");
        ore(1536, "ingotSilver", "oreSilver", "ingotSteel", "ingotUranium", "oreUranium");
        ore(1792, "ingotElectrum");
        ore(3072, "ingotCobalt", "oreCobalt", "ingotArdite", "oreArdite");
        ore(2048, "ingotTitanium", "oreTitanium", "gemOnyx", "gemJade");
        ore(4096, "ingotPlatinum", "orePlatinum", "ingotMithril", "oreMithril",
                "gemRuby", "oreRuby", "gemSapphire", "oreSapphire", "gemPeridot", "orePeridot",
                "gemAmethyst", "oreAmethyst", "gemTopaz", "oreTopaz", "gemTanzanite", "oreTanzanite");
        ore(8192, "ingotIridium", "oreIridium");
        ore(64, "dustSaltpeter", "dustSulfur", "gemApatite");
    }
}
