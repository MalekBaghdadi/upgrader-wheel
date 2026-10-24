package com.malek.upgraderwheel.value;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.UpgraderWheel;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.init.PotionTypes;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.IRarity;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Prices every item by walking crafting and smelting recipes back to raw materials, the way the Upgrader
 * mod for newer versions does: an iron ingot is worth what its ore costs, a sword is worth its ingots and stick.
 * Built once when the server starts (and on /upgrader reload); only ever touched from the server thread.
 */
public final class ValueRegistry {

    public enum Source {
        OVERRIDE("config override"),
        PACK("pack value list (upgraderwheel-pack.txt)"),
        BASE("built-in base value"),
        ORE_DICT("ore dictionary base value"),
        RECIPE("crafting recipe"),
        SMELTING("smelting recipe"),
        BREWING("brewing recipe"),
        GEAR("estimate from the gear it makes or its own stats"),
        SMELTS_INTO("worth what it smelts into"),
        FALLBACK("rarity guess (no recipe)");

        public final String description;

        Source(String description) {
            this.description = description;
        }
    }

    public static final class CatalogEntry {
        public final Item item;
        public final int meta;
        /** The potion type for potions, splash and lingering potions; null for everything else. */
        @Nullable
        public final PotionType potion;
        public final double value;
        public final int maxStack;
        public final Source source;

        CatalogEntry(Item item, int meta, @Nullable PotionType potion, double value, int maxStack, Source source) {
            this.item = item;
            this.meta = meta;
            this.potion = potion;
            this.value = value;
            this.maxStack = maxStack;
            this.source = source;
        }

        /** Registry name of the potion type, or "" when this is not a potion. */
        public String potionId() {
            ResourceLocation id = potion == null ? null : potion.getRegistryName();
            return id == null ? "" : id.toString();
        }

        public ItemStack createStack(int amount) {
            ItemStack stack = new ItemStack(item, amount, meta);
            return potion == null ? stack : PotionUtils.addPotionToItemStack(stack, potion);
        }
    }

    public static final class Explanation {
        public final double unitValue;
        public final Source source;
        public final boolean listed;
        /** For recipe values, what the recipe uses, e.g. "2 Dragon Bone + 1 Witherbone"; otherwise null. */
        @Nullable
        public final String recipe;

        Explanation(double unitValue, Source source, boolean listed, @Nullable String recipe) {
            this.unitValue = unitValue;
            this.source = source;
            this.listed = listed;
            this.recipe = recipe;
        }
    }

    private static final class Rec {
        final long out;
        final double outCount;
        final long[][] alternatives;
        final long[][] containers;
        final boolean smelting;
        /** A brewing stand step: three bottles and one ingredient make three bottles. */
        final boolean brewing;
        /** The concrete ingredient keys that gave this recipe its value, when it is the one an item is priced by. */
        long[] chosen;

        Rec(long out, double outCount, long[][] alternatives, long[][] containers, boolean smelting) {
            this(out, outCount, alternatives, containers, smelting, false);
        }

        Rec(long out, double outCount, long[][] alternatives, long[][] containers, boolean smelting, boolean brewing) {
            this.out = out;
            this.outCount = outCount;
            this.alternatives = alternatives;
            this.containers = containers;
            this.smelting = smelting;
            this.brewing = brewing;
        }
    }

    private static final int WILDCARD = OreDictionary.WILDCARD_VALUE;
    private static final int MAX_ALTERNATIVES = 64;
    private static final int MAX_PASSES = 64;
    private static final double MIN_VALUE = 0.01;

    private static ValueRegistry current;
    private static int versionCounter = 1 + new Random().nextInt(1 << 30);

    private final int version;
    private final Long2DoubleOpenHashMap values = new Long2DoubleOpenHashMap();
    private final Long2ObjectOpenHashMap<Source> sources = new Long2ObjectOpenHashMap<>();
    private final LongSet fixed = new LongOpenHashSet();
    /** Index of the recipe each item is priced by; missing for seeded and guessed values. */
    private final Long2IntOpenHashMap via = new Long2IntOpenHashMap();
    /** How many recipes use each item as an ingredient. */
    private final Long2IntOpenHashMap uses = new Long2IntOpenHashMap();
    private final LongSet recipeOutputs = new LongOpenHashSet();
    private final Map<Item, IntSet> metasSeen = new IdentityHashMap<>();
    private final Long2ObjectOpenHashMap<long[]> wildcardExpansion = new Long2ObjectOpenHashMap<>();
    private final List<Rec> recipes = new ArrayList<>();
    private final List<CatalogEntry> catalog = new ArrayList<>();
    private final Long2ObjectOpenHashMap<CatalogEntry> catalogByKey = new Long2ObjectOpenHashMap<>();
    private final List<ItemPattern.Valued> multipliers = new ArrayList<>();
    private final List<ItemPattern> blacklist = new ArrayList<>();
    private final List<ItemPattern> targetBlacklist = new ArrayList<>();
    private final List<ItemPattern> betBlacklist = new ArrayList<>();
    /** Every potion key seen (potion, splash, lingering, tipped arrow of one potion type). */
    private final LongSet potionKeys = new LongOpenHashSet();
    /** Potions some brewing recipe makes: these are offered as results. */
    private final LongSet brewedKeys = new LongOpenHashSet();
    /** For values worked out from what an item smelts into: that output, so loops back through it are refused. */
    private final Long2LongOpenHashMap smeltTarget = new Long2LongOpenHashMap();

    private int skippedRecipes;
    private int smeltingRecipes;
    private int brewingRecipes;
    private long brewingMillis;
    private int creativeFailures;
    private int creativeItems;
    private long buildMillis;

    private ValueRegistry(int version) {
        this.version = version;
        this.values.defaultReturnValue(-1);
        this.via.defaultReturnValue(-1);
        this.uses.defaultReturnValue(0);
        this.smeltTarget.defaultReturnValue(-1);
    }

    // ------------------------------------------------------------------ access

    @Nullable
    public static ValueRegistry get() {
        return current;
    }

    public static ValueRegistry getOrBuild() {
        if (current == null) {
            rebuild();
        }
        return current;
    }

    public static ValueRegistry rebuild() {
        ValueRegistry registry = new ValueRegistry(versionCounter++);
        registry.build();
        current = registry;
        registry.writeCsvQuietly();
        return registry;
    }

    public static void clear() {
        current = null;
    }

    public int version() {
        return version;
    }

    public List<CatalogEntry> catalog() {
        return Collections.unmodifiableList(catalog);
    }

    @Nullable
    public CatalogEntry catalogEntry(Item item, int meta) {
        return catalogByKey.get(key(item, meta));
    }

    /** The listed entry for an item, or for a potion when potionId names a potion type. */
    @Nullable
    public CatalogEntry catalogEntry(Item item, int meta, String potionId) {
        if (potionId == null || potionId.isEmpty()) {
            return catalogEntry(item, meta);
        }
        PotionType type = ForgeRegistries.POTION_TYPES.getValue(new ResourceLocation(potionId));
        long key = type == null ? -1 : potionKey(potionForm(item), type);
        return key < 0 ? null : catalogByKey.get(key);
    }

    // ------------------------------------------------------------------ keys

    public static int normalizeMeta(Item item, int meta) {
        if (item.isDamageable() || !item.getHasSubtypes()) {
            return 0;
        }
        return meta & 0xFFFF;
    }

    public static long key(Item item, int meta) {
        int normalized;
        if (meta == WILDCARD) {
            normalized = item.getHasSubtypes() && !item.isDamageable() ? WILDCARD : 0;
        } else {
            normalized = normalizeMeta(item, meta);
        }
        return ((long) Item.getIdFromItem(item) << 16) | (normalized & 0xFFFFL);
    }

    public static long key(ItemStack stack) {
        long potion = potionKey(stack);
        return potion >= 0 ? potion : key(stack.getItem(), stack.getMetadata());
    }

    // Potions keep their type in NBT, so each (form, potion type) gets a key of its own, far above item keys:
    // bit 40 set, the form in bits 24-27, the potion type's numeric id below.
    private static final long POTION_FLAG = 1L << 40;
    private static final Item[] POTION_FORMS = {Items.POTIONITEM, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW};
    static final String[] POTION_FORM_NAMES = {"potion", "splash", "lingering", "arrow"};
    static final int FORM_ARROW = 3;

    /** 0 potion, 1 splash, 2 lingering, 3 tipped arrow; -1 for anything else. */
    public static int potionForm(Item item) {
        for (int i = 0; i < POTION_FORMS.length; i++) {
            if (POTION_FORMS[i] == item) {
                return i;
            }
        }
        return -1;
    }

    /** The potion key of a potion stack with a real potion type, or -1. */
    public static long potionKey(ItemStack stack) {
        int form = potionForm(stack.getItem());
        if (form < 0 || !stack.hasTagCompound()) {
            return -1;
        }
        return potionKey(form, PotionUtils.getPotionFromItem(stack));
    }

    static long potionKey(int form, @Nullable PotionType type) {
        if (form < 0 || type == null || type == PotionTypes.EMPTY) {
            return -1;
        }
        int id = PotionType.REGISTRY.getIDForObject(type);
        return id < 0 ? -1 : POTION_FLAG | ((long) form << 24) | (id & 0xFFFFFFL);
    }

    public static boolean isPotionKey(long key) {
        return (key & POTION_FLAG) != 0;
    }

    private static int formOf(long key) {
        return (int) ((key >>> 24) & 0xF);
    }

    @Nullable
    private static PotionType potionTypeOf(long key) {
        return isPotionKey(key) ? PotionType.REGISTRY.getObjectById((int) (key & 0xFFFFFFL)) : null;
    }

    private static Item itemOf(long key) {
        if (isPotionKey(key)) {
            int form = formOf(key);
            return form < POTION_FORMS.length ? POTION_FORMS[form] : null;
        }
        return Item.getItemById((int) (key >>> 16));
    }

    private static int metaOf(long key) {
        return isPotionKey(key) ? 0 : (int) (key & 0xFFFF);
    }

    /** A one-item stack for a key (with the potion type for potion keys), or EMPTY. */
    private static ItemStack stackOf(long key) {
        Item item = itemOf(key);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int meta = metaOf(key);
        ItemStack stack = new ItemStack(item, 1, meta == WILDCARD ? 0 : meta);
        PotionType type = potionTypeOf(key);
        return type == null ? stack : PotionUtils.addPotionToItemStack(stack, type);
    }

    /** The id used in the CSV and in the config for a key: modid:item, or potion:/splash:/lingering:/arrow: + potion type. */
    public static String idOf(long key) {
        if (isPotionKey(key)) {
            PotionType type = potionTypeOf(key);
            int form = formOf(key);
            return (form < POTION_FORM_NAMES.length ? POTION_FORM_NAMES[form] : "potion") + ":"
                    + (type == null ? "?" : String.valueOf(type.getRegistryName()));
        }
        Item item = itemOf(key);
        return item == null ? "?" : String.valueOf(item.getRegistryName());
    }

    /** The config id for a held stack: potion:type for potions, modid:item(:meta) otherwise. */
    public static String configId(ItemStack stack) {
        long potion = potionKey(stack);
        if (potion >= 0) {
            return idOf(potion);
        }
        String id = String.valueOf(stack.getItem().getRegistryName());
        return stack.getHasSubtypes() ? id + ":" + stack.getMetadata() : id;
    }

    // ------------------------------------------------------------------ values for bets

    /** Value of one item of this kind, ignoring count, damage and enchantments. */
    public double unitValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double value = values.get(key(stack));
        if (value < 0) {
            value = Math.max(MIN_VALUE, fallback(stack) * multiplierFor(stack));
        }
        return value;
    }

    /** What a stack is worth as a bet: unit value x count, scaled by durability left, plus enchantments if enabled. */
    public double stackValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double value = unitValue(stack) * stack.getCount();
        try {
            if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
                double left = (stack.getMaxDamage() - stack.getItemDamage()) / (double) stack.getMaxDamage();
                value *= Math.max(0.0, Math.min(1.0, left));
            }
        } catch (Throwable ignored) {
            // Odd modded damage handling; keep the undamaged value
        }
        if (ModConfig.enchantmentLevelValue > 0) {
            try {
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
                int levels = 0;
                for (Integer level : enchantments.values()) {
                    levels += level == null ? 0 : Math.max(0, level);
                }
                value += levels * ModConfig.enchantmentLevelValue * stack.getCount();
            } catch (Throwable ignored) {
                // Malformed enchantment data
            }
        }
        return value;
    }

    public Explanation explain(ItemStack stack) {
        long key = key(stack);
        double value = values.get(key);
        if (value >= 0) {
            return new Explanation(value, sources.get(key), catalogByKey.containsKey(key), describeRecipe(key));
        }
        return new Explanation(unitValue(stack), Source.FALLBACK, false, null);
    }

    @Nullable
    private String describeRecipe(long key) {
        long target = smeltTarget.get(key);
        if (target >= 0 && sources.get(key) == Source.SMELTS_INTO) {
            return "smelts into " + nameOf(target);
        }
        int index = via.get(key);
        if (index < 0 || index >= recipes.size()) {
            return null;
        }
        Rec rec = recipes.get(index);
        if (rec.chosen == null) {
            return null;
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (long k : rec.chosen) {
            counts.merge(k, 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(e.getValue()).append(' ').append(nameOf(e.getKey()));
        }
        if (rec.brewing) {
            text.append(" (brewed, makes ").append(ModConfig.formatNumber(rec.outCount)).append(')');
        } else if (rec.outCount != 1) {
            text.append(rec.smelting ? " (smelted, makes " : " (makes ").append(ModConfig.formatNumber(rec.outCount)).append(')');
        } else if (rec.smelting) {
            text.append(" (smelted)");
        }
        return text.toString();
    }

    private static String nameOf(long key) {
        ItemStack stack = stackOf(key);
        if (stack.isEmpty()) {
            return "?";
        }
        try {
            String name = stack.getDisplayName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
            // Fall back to the id
        }
        return idOf(key);
    }

    public boolean isBlacklistedBet(ItemStack stack) {
        return matchesAny(blacklist, stack) || matchesAny(betBlacklist, stack);
    }

    /** Every valued item, for /upgrader dump. */
    public List<Object[]> dumpRows() {
        List<Object[]> rows = new ArrayList<>(values.size());
        for (Long2DoubleMap.Entry e : values.long2DoubleEntrySet()) {
            long key = e.getLongKey();
            Item item = itemOf(key);
            if (item == null || item.getRegistryName() == null || (isPotionKey(key) && potionTypeOf(key) == null)) {
                continue;
            }
            rows.add(new Object[]{idOf(key), metaOf(key), e.getDoubleValue(), sources.get(key), catalogByKey.containsKey(key), nameOf(key), describeRecipe(key)});
        }
        rows.sort(Comparator.comparing((Object[] r) -> (String) r[0]).thenComparing(r -> (Integer) r[1]));
        return rows;
    }

    /** Writes every value to config/upgraderwheel-values.csv. Returns the file. */
    public File writeCsv() throws IOException {
        File file = new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), "upgraderwheel-values.csv");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            out.println("id,meta,name,value,source,listed,recipe");
            for (Object[] row : dumpRows()) {
                Source source = (Source) row[3];
                out.println(row[0] + "," + row[1] + "," + csv((String) row[5]) + ","
                        + ModConfig.formatNumber(roundValue((Double) row[2])) + ","
                        + (source == null ? "" : source.name().toLowerCase(Locale.ROOT)) + "," + row[4] + ","
                        + csv(row[6] == null ? "" : (String) row[6]));
            }
        }
        return file;
    }

    private void writeCsvQuietly() {
        try {
            writeCsv();
        } catch (Throwable t) {
            UpgraderWheel.LOGGER.warn("Could not write upgraderwheel-values.csv: {}", t.toString());
        }
    }

    private static String csv(String text) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (clean.indexOf(',') >= 0 || clean.indexOf('"') >= 0) {
            return '"' + clean.replace("\"", "\"\"") + '"';
        }
        return clean;
    }

    public static double roundValue(double value) {
        return value >= 100 ? Math.round(value) : Math.round(value * 100.0) / 100.0;
    }

    public String summary() {
        return String.format("%d items valued, %d listed as results, %d recipes walked (%d smelting, %d brewing in %d ms), %d skipped, %d ms",
                values.size(), catalog.size(), recipes.size(), smeltingRecipes, brewingRecipes, brewingMillis, skippedRecipes, buildMillis);
    }

    // ------------------------------------------------------------------ build

    private void build() {
        long start = System.currentTimeMillis();
        readPatterns();

        List<ItemStack> targetStacks = collectCreativeStacks();
        collectRecipes();
        collectSmelting();
        collectBrewing(targetStacks);
        addTippedArrowRecipes();

        seedOverrides();
        seedPackValues();
        seedBaseValues();
        seedPotionBaseValues();
        seedOreDictionary();
        raiseSeedsToSmeltYield();
        buildWildcardExpansion();
        countUses();

        // Raw materials that no recipe makes get a guess right away, so recipes using them can be priced
        for (Rec rec : recipes) {
            recipeOutputs.add(rec.out);
        }
        for (long key : allKnownKeys()) {
            if (!values.containsKey(key) && !recipeOutputs.contains(key)) {
                assignFallback(key);
            }
        }
        deriveAll();
        if (priceOresFromSmelting()) {
            resetDerived();
            deriveAll();
        }
        estimateFromGear(targetStacks);
        applyMultipliers();
        if (targetStacks.isEmpty() || creativeFailures > creativeItems) {
            // Creative tab lookups failed across the board (client-only code on a dedicated server):
            // offer everything that a recipe makes or that has a base value instead
            UpgraderWheel.LOGGER.warn("Could not read creative tabs ({} items failed); listing crafted and base-valued items as results instead", creativeFailures);
            targetStacks = fallbackTargets();
        }
        buildCatalog(targetStacks);

        buildMillis = System.currentTimeMillis() - start;
        UpgraderWheel.LOGGER.info("Upgrader values ready: {}", summary());
    }

    private void readPatterns() {
        for (String line : ModConfig.valueMultipliers) {
            ItemPattern.Valued valued = ItemPattern.Valued.parse(line);
            if (valued == null) {
                warnBadLine("multipliers", line);
            } else {
                multipliers.add(valued);
            }
        }
        readList(ModConfig.blacklist, blacklist, "blacklist");
        readList(ModConfig.targetBlacklist, targetBlacklist, "targetBlacklist");
        readList(ModConfig.betBlacklist, betBlacklist, "betBlacklist");
    }

    private static void readList(String[] lines, List<ItemPattern> into, String name) {
        for (String line : lines) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            ItemPattern pattern = ItemPattern.parse(line);
            if (pattern == null) {
                warnBadLine(name, line);
            } else {
                into.add(pattern);
            }
        }
    }

    private static void warnBadLine(String option, String line) {
        if (line != null && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
            UpgraderWheel.LOGGER.warn("Ignoring unreadable line in {}: '{}'", option, line);
        }
    }

    private void note(Item item, int meta) {
        if (item == null || item == Items.AIR) {
            return;
        }
        int normalized = meta == WILDCARD ? -1 : normalizeMeta(item, meta);
        IntSet metas = metasSeen.get(item);
        if (metas == null) {
            metas = new IntOpenHashSet(2);
            metasSeen.put(item, metas);
        }
        if (normalized >= 0) {
            metas.add(normalized);
        }
    }

    private void note(ItemStack stack) {
        if (!stack.isEmpty()) {
            note(stack.getItem(), stack.getMetadata());
            long potion = potionKey(stack);
            if (potion >= 0) {
                potionKeys.add(potion);
            }
        }
    }

    /** Every concrete (non-wildcard) key seen anywhere. Items only ever seen as wildcards count as meta 0. */
    private long[] allKnownKeys() {
        LongSet keys = new LongOpenHashSet(potionKeys);
        for (Map.Entry<Item, IntSet> entry : metasSeen.entrySet()) {
            Item item = entry.getKey();
            if (entry.getValue().isEmpty()) {
                keys.add(key(item, 0));
            } else {
                for (int meta : entry.getValue()) {
                    keys.add(key(item, meta));
                }
            }
        }
        return keys.toLongArray();
    }

    private List<ItemStack> collectCreativeStacks() {
        List<ItemStack> result = new ArrayList<>();
        NonNullList<ItemStack> buffer = NonNullList.create();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            buffer.clear();
            try {
                item.getSubItems(CreativeTabs.SEARCH, buffer);
                creativeItems++;
            } catch (Throwable t) {
                // Some mods touch client-only code in here; their items are simply not offered on a dedicated server
                buffer.clear();
                creativeFailures++;
            }
            for (ItemStack stack : buffer) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                note(stack);
                result.add(stack);
            }
        }
        return result;
    }

    private void collectRecipes() {
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            try {
                if (recipe == null || recipe.isDynamic()) {
                    continue;
                }
                ItemStack out = recipe.getRecipeOutput();
                if (out == null || out.isEmpty()) {
                    continue;
                }
                NonNullList<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients == null || ingredients.isEmpty()) {
                    continue;
                }
                List<long[]> alternatives = new ArrayList<>();
                List<long[]> containers = new ArrayList<>();
                boolean craftable = true;
                for (Ingredient ingredient : ingredients) {
                    if (ingredient == null || ingredient == Ingredient.EMPTY) {
                        continue;
                    }
                    ItemStack[] matching = ingredient.getMatchingStacks();
                    if (matching.length == 0) {
                        // An ore dictionary name nothing provides: nobody can craft this
                        craftable = false;
                        break;
                    }
                    int n = Math.min(matching.length, MAX_ALTERNATIVES);
                    long[] alt = new long[n];
                    long[] cont = new long[n];
                    int k = 0;
                    for (int i = 0; i < n; i++) {
                        ItemStack s = matching[i];
                        if (s == null || s.isEmpty()) {
                            continue;
                        }
                        note(s);
                        alt[k] = key(s);
                        cont[k] = -1;
                        try {
                            if (s.getItem().hasContainerItem(s)) {
                                ItemStack container = s.getItem().getContainerItem(s);
                                if (container != null && !container.isEmpty()) {
                                    note(container);
                                    cont[k] = key(container);
                                }
                            }
                        } catch (Throwable ignored) {
                            // Container lookups at wildcard metadata can fail; treat as fully used up
                        }
                        k++;
                    }
                    if (k == 0) {
                        craftable = false;
                        break;
                    }
                    alternatives.add(Arrays.copyOf(alt, k));
                    containers.add(Arrays.copyOf(cont, k));
                }
                if (!craftable || alternatives.isEmpty()) {
                    continue;
                }
                note(out);
                recipes.add(new Rec(key(out), out.getCount(), alternatives.toArray(new long[0][]), containers.toArray(new long[0][]), false));
            } catch (Throwable t) {
                skippedRecipes++;
            }
        }
    }

    private void collectSmelting() {
        Map<ItemStack, ItemStack> smelting;
        try {
            smelting = FurnaceRecipes.instance().getSmeltingList();
        } catch (Throwable t) {
            return;
        }
        for (Map.Entry<ItemStack, ItemStack> entry : smelting.entrySet()) {
            try {
                ItemStack in = entry.getKey();
                ItemStack out = entry.getValue();
                if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
                    continue;
                }
                note(in);
                note(out);
                double count = out.getCount() / (double) Math.max(1, in.getCount());
                recipes.add(new Rec(key(out), count, new long[][]{{key(in)}}, new long[][]{{-1}}, true));
                smeltingRecipes++;
            } catch (Throwable t) {
                skippedRecipes++;
            }
        }
    }

    /**
     * Brewing stand recipes. Forge only exposes them as black boxes (isInput / isIngredient / getOutput), so every
     * potion and creative-tab stack is offered to every recipe: vanilla mixes, mod potions and CraftTweaker brews all
     * show up the same way. One ingredient brews three bottles, so a step costs a bottle plus a third of the ingredient.
     */
    private void collectBrewing(List<ItemStack> creativeStacks) {
        long start = System.currentTimeMillis();
        List<IBrewingRecipe> brewing;
        try {
            brewing = BrewingRecipeRegistry.getRecipes();
        } catch (Throwable t) {
            return;
        }
        // One stack per key: every potion type in every bottle form, then everything from the creative tabs
        Long2ObjectOpenHashMap<ItemStack> unique = new Long2ObjectOpenHashMap<>();
        for (PotionType type : ForgeRegistries.POTION_TYPES.getValuesCollection()) {
            if (type == null || type == PotionTypes.EMPTY) {
                continue;
            }
            for (int form = 0; form < FORM_ARROW; form++) {
                ItemStack stack = PotionUtils.addPotionToItemStack(new ItemStack(POTION_FORMS[form]), type);
                unique.putIfAbsent(key(stack), stack);
            }
        }
        for (ItemStack stack : creativeStacks) {
            if (!stack.isEmpty()) {
                unique.putIfAbsent(key(stack), stack);
            }
        }
        List<ItemStack> candidates = new ArrayList<>(unique.values());
        for (IBrewingRecipe recipe : brewing) {
            if (recipe == null) {
                continue;
            }
            List<ItemStack> inputs = new ArrayList<>();
            List<ItemStack> ingredients = new ArrayList<>();
            for (ItemStack stack : candidates) {
                try {
                    if (recipe.isInput(stack)) {
                        inputs.add(stack);
                    }
                } catch (Throwable ignored) {
                    // A recipe that cannot look at this stack does not take it
                }
                try {
                    if (recipe.isIngredient(stack)) {
                        ingredients.add(stack);
                    }
                } catch (Throwable ignored) {
                    // Same
                }
            }
            if ((long) inputs.size() * ingredients.size() > 200000) {
                UpgraderWheel.LOGGER.warn("Skipping brewing recipe {}: it accepts {} inputs and {} ingredients", recipe.getClass().getName(), inputs.size(), ingredients.size());
                skippedRecipes++;
                continue;
            }
            for (ItemStack input : inputs) {
                long in = key(input);
                for (ItemStack ingredient : ingredients) {
                    ItemStack out;
                    try {
                        out = recipe.getOutput(input.copy(), ingredient.copy());
                    } catch (Throwable t) {
                        continue;
                    }
                    if (out == null || out.isEmpty()) {
                        continue;
                    }
                    long outKey = key(out);
                    if (outKey == in) {
                        continue;
                    }
                    long ing = key(ingredient);
                    long container = -1;
                    try {
                        if (ingredient.getItem().hasContainerItem(ingredient)) {
                            ItemStack left = ingredient.getItem().getContainerItem(ingredient);
                            if (left != null && !left.isEmpty()) {
                                container = key(left);
                            }
                        }
                    } catch (Throwable ignored) {
                        // Treat the ingredient as fully used up
                    }
                    note(input);
                    note(ingredient);
                    note(out);
                    // Three bottles and one ingredient make three bottles
                    recipes.add(new Rec(outKey, 3.0 * out.getCount(),
                            new long[][]{{in}, {in}, {in}, {ing}},
                            new long[][]{{-1}, {-1}, {-1}, {container}}, false, true));
                    if (isPotionKey(outKey)) {
                        brewedKeys.add(outKey);
                    }
                    brewingRecipes++;
                }
            }
        }
        brewingMillis = System.currentTimeMillis() - start;
    }

    /** Tipped arrows are made by a dynamic recipe (8 arrows around a lingering potion), so add it for every potion type. */
    private void addTippedArrowRecipes() {
        long arrow = key(Items.ARROW, 0);
        note(Items.ARROW, 0);
        for (PotionType type : ForgeRegistries.POTION_TYPES.getValuesCollection()) {
            long lingering = potionKey(2, type);
            long tipped = potionKey(FORM_ARROW, type);
            if (lingering < 0 || tipped < 0 || type.getEffects().isEmpty()) {
                continue;
            }
            potionKeys.add(lingering);
            potionKeys.add(tipped);
            long[][] alternatives = new long[9][];
            long[][] containers = new long[9][];
            for (int i = 0; i < 8; i++) {
                alternatives[i] = new long[]{arrow};
                containers[i] = new long[]{-1};
            }
            alternatives[8] = new long[]{lingering};
            containers[8] = new long[]{-1};
            recipes.add(new Rec(tipped, 8, alternatives, containers, false));
        }
    }

    /** Keys a pattern applies to, from what has been seen plus the item it names directly. */
    private long[] keysFor(ItemPattern pattern) {
        LongSet keys = new LongOpenHashSet();
        if (pattern.kind == ItemPattern.Kind.POTION) {
            for (PotionType type : ForgeRegistries.POTION_TYPES.getValuesCollection()) {
                ResourceLocation id = type == null ? null : type.getRegistryName();
                if (id != null && pattern.matchesPotionName(id)) {
                    long k = potionKey(pattern.potionForm, type);
                    if (k >= 0) {
                        keys.add(k);
                        potionKeys.add(k);
                    }
                }
            }
            return keys.toLongArray();
        }
        Item named = pattern.item();
        if (named != null) {
            if (pattern.kind == ItemPattern.Kind.ITEM_META) {
                note(named, pattern.meta);
                keys.add(key(named, pattern.meta));
                return keys.toLongArray();
            }
            IntSet metas = metasSeen.get(named);
            if (metas == null || metas.isEmpty()) {
                note(named, 0);
                keys.add(key(named, 0));
            } else {
                for (int meta : metas) {
                    keys.add(key(named, meta));
                }
            }
            return keys.toLongArray();
        }
        if (pattern.kind == ItemPattern.Kind.ORE) {
            if (OreDictionary.doesOreNameExist(pattern.ore)) {
                for (ItemStack stack : OreDictionary.getOres(pattern.ore, false)) {
                    addConcreteKeys(stack, keys);
                }
            }
            return keys.toLongArray();
        }
        if (pattern.kind == ItemPattern.Kind.MOD || pattern.kind == ItemPattern.Kind.GLOB) {
            for (Map.Entry<Item, IntSet> entry : metasSeen.entrySet()) {
                ResourceLocation id = entry.getKey().getRegistryName();
                if (id != null && pattern.matchesName(id)) {
                    if (entry.getValue().isEmpty()) {
                        keys.add(key(entry.getKey(), 0));
                    } else {
                        for (int meta : entry.getValue()) {
                            keys.add(key(entry.getKey(), meta));
                        }
                    }
                }
            }
        }
        return keys.toLongArray();
    }

    /** Adds the stack's key, or for a wildcard stack every variant seen so far (meta 0 if none). */
    private void addConcreteKeys(ItemStack stack, LongSet keys) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (stack.getMetadata() == WILDCARD && item.getHasSubtypes() && !item.isDamageable()) {
            IntSet metas = metasSeen.get(item);
            if (metas == null || metas.isEmpty()) {
                note(item, 0);
                keys.add(key(item, 0));
            } else {
                for (int meta : metas) {
                    keys.add(key(item, meta));
                }
            }
        } else {
            note(stack);
            keys.add(key(stack));
        }
    }

    private void seedOverrides() {
        for (String line : ModConfig.valueOverrides) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            ItemPattern.Valued valued = ItemPattern.Valued.parse(line);
            if (valued == null) {
                warnBadLine("overrides", line);
                continue;
            }
            long[] keys = keysFor(valued.pattern);
            if (keys.length == 0) {
                UpgraderWheel.LOGGER.warn("Override '{}' matches no item", line.trim());
            }
            double value = Math.max(MIN_VALUE, valued.value);
            for (long key : keys) {
                values.put(key, value);
                sources.put(key, Source.OVERRIDE);
                fixed.add(key);
            }
        }
    }

    /** Values from config/upgraderwheel-pack.txt. Pinned like overrides, but config overrides win. */
    private void seedPackValues() {
        for (PackValues.Entry entry : PackValues.load()) {
            if (entry.noWin()) {
                targetBlacklist.add(entry.pattern);
                continue;
            }
            long[] keys = keysFor(entry.pattern);
            if (keys.length == 0) {
                UpgraderWheel.LOGGER.debug("Pack value '{}' matches no item", entry.pattern);
            }
            double value = Math.max(MIN_VALUE, entry.value);
            for (long key : keys) {
                if (sources.get(key) == Source.OVERRIDE) {
                    continue;
                }
                values.put(key, value);
                sources.put(key, Source.PACK);
                fixed.add(key);
            }
        }
    }

    private void seedBaseValues() {
        for (Map.Entry<String, Double> entry : BaseValues.ITEMS.entrySet()) {
            String id = entry.getKey();
            int at = id.indexOf('@');
            String name = at < 0 ? id : id.substring(0, at);
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            if (item == null || item == Items.AIR) {
                continue;
            }
            if (at >= 0) {
                int meta = Integer.parseInt(id.substring(at + 1));
                note(item, meta);
                seed(key(item, meta), entry.getValue(), Source.BASE);
            } else {
                IntSet metas = metasSeen.get(item);
                if (metas == null || metas.isEmpty()) {
                    note(item, 0);
                    seed(key(item, 0), entry.getValue(), Source.BASE);
                } else {
                    for (int meta : metas.toIntArray()) {
                        seed(key(item, meta), entry.getValue(), Source.BASE);
                    }
                }
            }
        }
    }

    /** Built-in potion values (by potion type, for the drinkable bottle). */
    private void seedPotionBaseValues() {
        for (Map.Entry<String, Double> entry : BaseValues.POTIONS.entrySet()) {
            PotionType type = ForgeRegistries.POTION_TYPES.getValue(new ResourceLocation(entry.getKey()));
            long key = potionKey(0, type);
            if (key >= 0) {
                potionKeys.add(key);
                seed(key, entry.getValue(), Source.BASE);
            }
        }
    }

    /**
     * Seeds have to agree with what they smelt into: horse armor that smelts into 7 diamonds is worth 7 diamonds.
     * Otherwise the walk would read "7 diamonds for the price of 2" and lower diamonds, and everything after them.
     */
    private void raiseSeedsToSmeltYield() {
        for (int round = 0; round < 4; round++) {
            boolean changed = false;
            for (Rec rec : recipes) {
                if (!rec.smelting) {
                    continue;
                }
                long in = rec.alternatives[0][0];
                Source inSource = sources.get(in);
                Source outSource = sources.get(rec.out);
                if (metaOf(in) == WILDCARD || fixed.contains(in) || (inSource != Source.BASE && inSource != Source.ORE_DICT && inSource != Source.SMELTS_INTO)) {
                    continue;
                }
                if (outSource != Source.BASE && outSource != Source.ORE_DICT && outSource != Source.PACK && outSource != Source.OVERRIDE) {
                    continue;
                }
                double worth = values.get(rec.out) * rec.outCount;
                if (worth > values.get(in) * (1 + 1.0E-9)) {
                    values.put(in, worth);
                    sources.put(in, Source.SMELTS_INTO);
                    smeltTarget.put(in, rec.out);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private void seedOreDictionary() {
        for (Map.Entry<String, Double> entry : BaseValues.ORES.entrySet()) {
            if (!OreDictionary.doesOreNameExist(entry.getKey())) {
                continue;
            }
            LongSet keys = new LongOpenHashSet();
            for (ItemStack stack : OreDictionary.getOres(entry.getKey(), false)) {
                addConcreteKeys(stack, keys);
            }
            for (long key : keys.toLongArray()) {
                if (!values.containsKey(key)) {
                    seed(key, entry.getValue(), Source.ORE_DICT);
                }
            }
        }
    }

    private void seed(long key, double value, Source source) {
        if (fixed.contains(key)) {
            return;
        }
        double current = values.get(key);
        if (current < 0 || value < current) {
            values.put(key, value);
            sources.put(key, source);
        }
    }

    private void buildWildcardExpansion() {
        for (Rec rec : recipes) {
            for (long[] alternatives : rec.alternatives) {
                for (long key : alternatives) {
                    if (metaOf(key) == WILDCARD && !wildcardExpansion.containsKey(key)) {
                        Item item = itemOf(key);
                        IntSet metas = item == null ? null : metasSeen.get(item);
                        long base = key & ~0xFFFFL;
                        if (metas == null || metas.isEmpty()) {
                            wildcardExpansion.put(key, new long[]{base});
                        } else {
                            int[] list = metas.toIntArray();
                            long[] expanded = new long[list.length];
                            for (int i = 0; i < list.length; i++) {
                                expanded[i] = base | (list[i] & 0xFFFFL);
                            }
                            wildcardExpansion.put(key, expanded);
                        }
                    }
                }
            }
        }
    }

    /** The concrete key of the cheapest valued variant (wildcards pick their cheapest variant), or -1. */
    private long cheapestKey(long key) {
        if (metaOf(key) == WILDCARD) {
            long[] expanded = wildcardExpansion.get(key);
            if (expanded == null) {
                long base = key & ~0xFFFFL;
                return values.get(base) >= 0 ? base : -1;
            }
            long best = -1;
            double bestValue = -1;
            for (long k : expanded) {
                double v = values.get(k);
                if (v >= 0 && (bestValue < 0 || v < bestValue)) {
                    bestValue = v;
                    best = k;
                }
            }
            return best;
        }
        return values.get(key) >= 0 ? key : -1;
    }

    private double lookup(long key) {
        long concrete = cheapestKey(key);
        return concrete < 0 ? -1 : values.get(concrete);
    }

    /**
     * Prices everything recipes can price, then guesses what is left: things only made inside recipe loops with
     * no priced way in (a gem and its block, with no base value). The most-used material is guessed first and
     * recipes price the rest, so the block is 9 gems instead of the gem being a ninth of a guessed block.
     */
    private void deriveAll() {
        relax();
        for (int threshold : new int[]{16, 4, 1, 0}) {
            boolean added = false;
            for (long key : allKnownKeys()) {
                if (!values.containsKey(key) && uses.get(key) >= threshold) {
                    assignFallback(key);
                    added = true;
                }
            }
            if (added) {
                relax();
            }
        }
    }

    /** Ores and other things with no value of their own are worth what they smelt into. */
    private boolean priceOresFromSmelting() {
        boolean changed = false;
        Long2IntOpenHashMap memo = new Long2IntOpenHashMap();
        for (Rec rec : recipes) {
            if (!rec.smelting) {
                continue;
            }
            long in = rec.alternatives[0][0];
            if (metaOf(in) == WILDCARD || fixed.contains(in) || sources.get(in) != Source.FALLBACK) {
                continue;
            }
            double out = values.get(rec.out);
            if (out < 0 || isGuessed(rec.out, memo, 0)) {
                continue;
            }
            double worth = out * rec.outCount;
            if (worth > values.get(in)) {
                values.put(in, worth);
                sources.put(in, Source.SMELTS_INTO);
                smeltTarget.put(in, rec.out);
                changed = true;
            }
        }
        return changed;
    }

    private void countUses() {
        for (Rec rec : recipes) {
            LongSet seen = new LongOpenHashSet();
            for (long[] alternatives : rec.alternatives) {
                for (long key : alternatives) {
                    if (metaOf(key) == WILDCARD) {
                        long[] expanded = wildcardExpansion.get(key);
                        if (expanded != null) {
                            for (long k : expanded) {
                                seen.add(k);
                            }
                        }
                    } else {
                        seen.add(key);
                    }
                }
            }
            for (long key : seen.toLongArray()) {
                uses.addTo(key, 1);
            }
        }
    }

    /**
     * Repeats "an output costs its cheapest recipe" until nothing gets cheaper.
     * A recipe may not lower an item's value when one of its ingredients is itself priced from that item
     * (ingots to block and block back to ingots, or gear recycled into its material): such loops would
     * otherwise drive the whole family towards zero.
     */
    private void relax() {
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (int r = 0; r < recipes.size(); r++) {
                Rec rec = recipes.get(r);
                if (fixed.contains(rec.out) || rec.outCount <= 0) {
                    continue;
                }
                double total = 0;
                long[] chosen = new long[rec.alternatives.length];
                boolean known = true;
                for (int i = 0; i < rec.alternatives.length && known; i++) {
                    long[] alternatives = rec.alternatives[i];
                    long[] containers = rec.containers[i];
                    double best = -1;
                    long bestKey = -1;
                    for (int j = 0; j < alternatives.length; j++) {
                        long concrete = cheapestKey(alternatives[j]);
                        if (concrete < 0) {
                            continue;
                        }
                        double v = values.get(concrete);
                        if (containers[j] >= 0) {
                            double back = lookup(containers[j]);
                            if (back > 0) {
                                v = Math.max(0, v - back);
                            }
                        }
                        if (best < 0 || v < best) {
                            best = v;
                            bestKey = concrete;
                        }
                    }
                    if (best < 0) {
                        known = false;
                    } else {
                        total += best;
                        chosen[i] = bestKey;
                    }
                }
                if (!known) {
                    continue;
                }
                double unit = total / rec.outCount;
                if (rec.brewing) {
                    // Brewing takes a stand, blaze powder and time; each step adds to what goes in
                    unit *= ModConfig.brewingStepMultiplier;
                }
                unit = Math.max(MIN_VALUE, Math.max(unit, potionFloor(rec.out)));
                double current = values.get(rec.out);
                boolean update;
                if (current < 0) {
                    update = true;
                } else if (rec.smelting && isSeed(chosen[0]) && isSeed(rec.out) && unit < current) {
                    // Smelting one fixed value into another never lowers it: seeds are raised to agree with what
                    // they smelt into instead (raiseSeedsToSmeltYield), and ores priced from their output loop
                    update = false;
                } else {
                    boolean lower = unit < current - 1.0E-9 * Math.max(1, current);
                    if (!lower && via.get(rec.out) == r && sources.get(rec.out) != Source.FALLBACK
                            && unit > current + 1.0E-9 * Math.max(1, current)) {
                        // The recipe this item is priced by went up (an ingredient was re-priced, or a potion floor
                        // rose): follow it, unless that recipe loops back through this item (then it would never settle)
                        if (dependsOn(chosen, rec.out)) {
                            continue;
                        }
                        values.put(rec.out, unit);
                        rec.chosen = chosen;
                        changed = true;
                        continue;
                    }
                    boolean currentGuessed = isGuessed(rec.out, new Long2IntOpenHashMap(), 0);
                    if (!lower && !currentGuessed) {
                        continue;
                    }
                    boolean recipeGuessed = anyGuessed(chosen);
                    if (currentGuessed && !recipeGuessed) {
                        // A real recipe beats a value that rests on guesses, even when it is higher
                        update = Math.abs(unit - current) > 1.0E-9 * Math.max(1, current);
                    } else if (!currentGuessed && recipeGuessed) {
                        // Guesses never cheapen a known value: an emerald stays an emerald even though some
                        // mod's emerald ore, which has no value of its own, smelts into one
                        update = false;
                    } else if (!lower || dependsOn(chosen, rec.out)) {
                        update = false;
                    } else {
                        // One guess does not replace another: a gem guessed at 32 stays 32 rather than
                        // becoming a ninth of its block, which was guessed at 32 too
                        Source currentSource = sources.get(rec.out);
                        update = !(recipeGuessed && (currentSource == Source.FALLBACK || currentSource == Source.GEAR));
                    }
                }
                if (update) {
                    values.put(rec.out, unit);
                    sources.put(rec.out, rec.brewing ? Source.BREWING : rec.smelting ? Source.SMELTING : Source.RECIPE);
                    via.put(rec.out, r);
                    rec.chosen = chosen;
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
        UpgraderWheel.LOGGER.debug("Value walk stopped after {} passes", MAX_PASSES);
    }

    /** Values that are not worked out from recipes: built in, from the config or pack list, or estimates. */
    private boolean isSeed(long key) {
        Source source = sources.get(key);
        return source == Source.BASE || source == Source.ORE_DICT || source == Source.PACK || source == Source.OVERRIDE
                || source == Source.GEAR || source == Source.SMELTS_INTO;
    }

    /**
     * The least a potion with an effect is worth: potionValue for the drinkable bottle, one brewing step more for a
     * splash potion and two for a lingering one (some packs brew those straight from a cheap base); 0 for the rest.
     */
    private double potionFloor(long key) {
        if (!isPotionKey(key) || formOf(key) == FORM_ARROW) {
            return 0;
        }
        PotionType type = potionTypeOf(key);
        if (type == null || type.getEffects().isEmpty()) {
            return 0;
        }
        double floor = potionFallback(key);
        int form = formOf(key);
        if (form > 0) {
            // A splash potion is never worth less than the drinkable one it is brewed from, a lingering one never
            // less than the splash one, even when the pack has a cheaper direct recipe for it
            double lower = values.get(potionKey(form - 1, type));
            if (lower > 0) {
                floor = Math.max(floor, lower * ModConfig.brewingStepMultiplier);
            }
        }
        return floor;
    }

    private boolean anyGuessed(long[] keys) {
        Long2IntOpenHashMap memo = new Long2IntOpenHashMap();
        for (long key : keys) {
            if (isGuessed(key, memo, 0)) {
                return true;
            }
        }
        return false;
    }

    /** True when any of these keys is priced, directly or through other recipes, from the target. */
    private boolean dependsOn(long[] keys, long target) {
        LongSet visited = new LongOpenHashSet();
        long[] stack = new long[Math.max(16, keys.length * 2)];
        int size = 0;
        for (long key : keys) {
            stack[size++] = key;
        }
        int steps = 0;
        while (size > 0 && steps++ < 4096) {
            long key = stack[--size];
            if (key == target) {
                return true;
            }
            if (!visited.add(key)) {
                continue;
            }
            long smeltsInto = smeltTarget.get(key);
            if (smeltsInto >= 0 && sources.get(key) == Source.SMELTS_INTO) {
                // Priced from what it smelts into, so it depends on that
                if (size == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length * 2);
                }
                stack[size++] = smeltsInto;
                continue;
            }
            int index = via.get(key);
            if (index < 0) {
                continue;
            }
            long[] chosen = recipes.get(index).chosen;
            if (chosen == null) {
                continue;
            }
            for (long k : chosen) {
                if (size == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length * 2);
                }
                stack[size++] = k;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ gear-based estimates

    /**
     * Mob drops and other raw materials that no recipe or base value prices get a rarity guess, which is far too low
     * for things like dragon scales. Gear says what its material is worth: armor or a weapon at least as strong as the
     * diamond version should cost about as much, more if it is stronger. So for gear priced from guessed materials,
     * work out what the gear's stats are worth, split that over the guessed materials in its recipe, and price
     * everything again. Loot-only gear with no recipe gets the stat value directly. Rounds go outward, so a sword
     * that takes another sword plus a guessed ingredient is handled after the first sword is priced.
     */
    private void estimateFromGear(List<ItemStack> stacks) {
        List<ItemStack> gear = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (isGear(stack) && values.get(key(stack)) >= 0) {
                gear.add(stack);
            }
        }
        if (gear.isEmpty()) {
            return;
        }
        Map<Class<?>, ItemStack> diamondByClass = new IdentityHashMap<>();
        for (int round = 0; round < 4; round++) {
            LongSet guessed = guessedKeys();
            if (round == 0) {
                for (ItemStack stack : gear) {
                    ResourceLocation id = stack.getItem().getRegistryName();
                    if (id != null && id.getPath().contains("diamond") && !(stack.getItem() instanceof ItemArmor)
                            && !guessed.contains(key(stack))) {
                        diamondByClass.putIfAbsent(stack.getItem().getClass(), stack);
                    }
                }
            }
            Long2ObjectOpenHashMap<List<Double>> estimates = new Long2ObjectOpenHashMap<>();
            for (ItemStack stack : gear) {
                long key = key(stack);
                if (!guessed.contains(key)) {
                    continue;
                }
                double floor = gearValue(stack, diamondByClass);
                if (floor <= values.get(key) * 1.05) {
                    continue;
                }
                if (sources.get(key) == Source.FALLBACK) {
                    addEstimate(estimates, key, floor);
                    continue;
                }
                int index = via.get(key);
                if (index < 0) {
                    continue;
                }
                Rec rec = recipes.get(index);
                if (rec.chosen == null) {
                    continue;
                }
                double known = 0;
                int guessedUnits = 0;
                boolean waitForIngredient = false;
                for (long k : rec.chosen) {
                    if (sources.get(k) == Source.FALLBACK) {
                        guessedUnits++;
                    } else if (guessed.contains(k)) {
                        // An ingredient made from guessed materials: price it first, use it in a later round
                        waitForIngredient = true;
                    } else {
                        known += values.get(k);
                    }
                }
                if (waitForIngredient || guessedUnits == 0) {
                    continue;
                }
                double need = (floor * rec.outCount - known) / guessedUnits;
                if (need <= 0) {
                    continue;
                }
                for (long k : rec.chosen) {
                    if (sources.get(k) == Source.FALLBACK) {
                        addEstimate(estimates, k, need);
                    }
                }
            }
            boolean changed = false;
            for (Long2ObjectMap.Entry<List<Double>> entry : estimates.long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                if (fixed.contains(key)) {
                    continue;
                }
                double estimate = median(entry.getValue());
                if (estimate > values.get(key)) {
                    values.put(key, estimate);
                    sources.put(key, Source.GEAR);
                    via.remove(key);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            resetDerived();
            deriveAll();
        }
    }

    private static void addEstimate(Long2ObjectOpenHashMap<List<Double>> estimates, long key, double value) {
        List<Double> list = estimates.get(key);
        if (list == null) {
            list = new ArrayList<>(2);
            estimates.put(key, list);
        }
        list.add(value);
    }

    private static double median(List<Double> list) {
        List<Double> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** Recipe-made values (and guesses standing in for them) are worked out again after some seeds went up. */
    private void resetDerived() {
        for (long key : values.keySet().toLongArray()) {
            Source source = sources.get(key);
            if (source == Source.RECIPE || source == Source.SMELTING || source == Source.BREWING
                    || (source == Source.FALLBACK && recipeOutputs.contains(key))) {
                values.remove(key);
                sources.remove(key);
                via.remove(key);
            }
        }
        for (Rec rec : recipes) {
            rec.chosen = null;
        }
    }

    /** Every key whose value is a rarity guess, or is made (through any number of recipes) from one. */
    private LongSet guessedKeys() {
        LongSet result = new LongOpenHashSet();
        Long2IntOpenHashMap memo = new Long2IntOpenHashMap();
        for (long key : values.keySet().toLongArray()) {
            if (isGuessed(key, memo, 0)) {
                result.add(key);
            }
        }
        return result;
    }

    private boolean isGuessed(long key, Long2IntOpenHashMap memo, int depth) {
        int known = memo.get(key);
        if (known != 0) {
            return known > 0;
        }
        boolean result = false;
        Source source = sources.get(key);
        if (source == Source.FALLBACK) {
            result = true;
        } else if (depth < 64 && (source == Source.RECIPE || source == Source.SMELTING || source == Source.BREWING)) {
            int index = via.get(key);
            long[] chosen = index < 0 ? null : recipes.get(index).chosen;
            if (chosen != null) {
                for (long k : chosen) {
                    if (k != key && isGuessed(k, memo, depth + 1)) {
                        result = true;
                        break;
                    }
                }
            }
        }
        memo.put(key, result ? 1 : -1);
        return result;
    }

    private static boolean isGear(ItemStack stack) {
        try {
            if (stack.getMaxDamage() <= 0) {
                return false;
            }
            return stack.getItem() instanceof ItemArmor || attackDamage(stack) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static double attackDamage(ItemStack stack) {
        double total = 0;
        try {
            for (AttributeModifier modifier : stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND)
                    .get(SharedMonsterAttributes.ATTACK_DAMAGE.getName())) {
                if (modifier.getOperation() == 0) {
                    total += modifier.getAmount();
                }
            }
        } catch (Throwable ignored) {
            // Modded attribute code that needs a player or a world
        }
        return total;
    }

    /**
     * What gear's stats are worth, measured against the diamond version: the same value at the same strength,
     * growing with the square of how much stronger it is. 0 for gear weaker than diamond.
     */
    private double gearValue(ItemStack stack, Map<Class<?>, ItemStack> diamondByClass) {
        Item item = stack.getItem();
        ItemStack reference;
        double score;
        double referenceScore;
        if (item instanceof ItemArmor) {
            ItemArmor armor = (ItemArmor) item;
            Item diamond;
            switch (armor.armorType) {
                case HEAD:
                    diamond = Items.DIAMOND_HELMET;
                    break;
                case CHEST:
                    diamond = Items.DIAMOND_CHESTPLATE;
                    break;
                case LEGS:
                    diamond = Items.DIAMOND_LEGGINGS;
                    break;
                case FEET:
                    diamond = Items.DIAMOND_BOOTS;
                    break;
                default:
                    return 0;
            }
            reference = new ItemStack(diamond);
            ItemArmor diamondArmor = (ItemArmor) diamond;
            score = armor.damageReduceAmount + armor.toughness;
            referenceScore = diamondArmor.damageReduceAmount + diamondArmor.toughness;
        } else {
            reference = diamondByClass.get(item.getClass());
            if (reference == null) {
                if (item instanceof ItemPickaxe) {
                    reference = new ItemStack(Items.DIAMOND_PICKAXE);
                } else if (item instanceof ItemAxe) {
                    reference = new ItemStack(Items.DIAMOND_AXE);
                } else if (item instanceof ItemSpade) {
                    reference = new ItemStack(Items.DIAMOND_SHOVEL);
                } else {
                    reference = new ItemStack(Items.DIAMOND_SWORD);
                }
            }
            score = attackDamage(stack);
            referenceScore = attackDamage(reference);
        }
        if (score <= 0 || referenceScore <= 0) {
            return 0;
        }
        double ratio = score / referenceScore;
        if (ratio < 0.95) {
            return 0;
        }
        double referenceValue = values.get(key(reference));
        if (referenceValue <= 0 || reference.getMaxDamage() <= 0) {
            return 0;
        }
        double durability = Math.sqrt(stack.getMaxDamage() / (double) reference.getMaxDamage());
        durability = Math.max(0.7, Math.min(1.5, durability));
        return Math.min(referenceValue * 16, referenceValue * ratio * ratio * durability);
    }

    private void assignFallback(long key) {
        ItemStack stack = stackOf(key);
        if (stack.isEmpty()) {
            return;
        }
        values.put(key, Math.max(MIN_VALUE, fallback(stack)));
        sources.put(key, Source.FALLBACK);
    }

    /** The guess for things with no recipe and no base value: rarity, stackability and durability. */
    public static double fallback(ItemStack stack) {
        long potion = potionKey(stack);
        if (potion >= 0) {
            return potionFallback(potion);
        }
        double value = ModConfig.fallbackCommon;
        try {
            IRarity rarity = stack.getItem().getForgeRarity(stack);
            if (rarity == EnumRarity.UNCOMMON) {
                value = ModConfig.fallbackUncommon;
            } else if (rarity == EnumRarity.RARE) {
                value = ModConfig.fallbackRare;
            } else if (rarity == EnumRarity.EPIC) {
                value = ModConfig.fallbackEpic;
            } else if (rarity != null && rarity != EnumRarity.COMMON) {
                value = ModConfig.fallbackRare;
            }
        } catch (Throwable ignored) {
            // Rarity lookups that need client classes or NBT; stay common
        }
        try {
            if (stack.getMaxStackSize() <= 1) {
                value *= ModConfig.fallbackUnstackableMultiplier;
            }
            if (stack.isItemStackDamageable()) {
                value = Math.max(value, stack.getMaxDamage() * ModConfig.fallbackPerDurabilityPoint);
            }
        } catch (Throwable ignored) {
            // Keep the rarity guess
        }
        return value;
    }

    /**
     * A potion that no brewing recipe makes (loot only): potionValue for one with an effect, a bit more for splash and
     * lingering bottles, an eighth for a tipped arrow; plain bottles (water, awkward, thick, mundane) are cheap.
     */
    private static double potionFallback(long key) {
        PotionType type = potionTypeOf(key);
        boolean effect = type != null && !type.getEffects().isEmpty();
        double base = effect ? ModConfig.potionValue : ModConfig.fallbackCommon;
        switch (formOf(key)) {
            case 1:
                return base * ModConfig.brewingStepMultiplier;
            case 2:
                return base * ModConfig.brewingStepMultiplier * ModConfig.brewingStepMultiplier;
            case FORM_ARROW:
                return base * ModConfig.brewingStepMultiplier * ModConfig.brewingStepMultiplier / 8;
            default:
                return base;
        }
    }

    private void applyMultipliers() {
        if (multipliers.isEmpty()) {
            return;
        }
        for (ItemPattern.Valued multiplier : multipliers) {
            for (long key : keysFor(multiplier.pattern)) {
                double v = values.get(key);
                if (v >= 0) {
                    values.put(key, Math.max(MIN_VALUE, v * multiplier.value));
                }
            }
        }
    }

    private double multiplierFor(ItemStack stack) {
        double factor = 1;
        for (ItemPattern.Valued multiplier : multipliers) {
            if (multiplier.pattern.matches(stack)) {
                factor *= multiplier.value;
            }
        }
        return factor;
    }

    private List<ItemStack> fallbackTargets() {
        LongSet keys = new LongOpenHashSet();
        for (Rec rec : recipes) {
            keys.add(rec.out);
        }
        for (long key : allKnownKeys()) {
            Source source = sources.get(key);
            if (source == Source.BASE || source == Source.ORE_DICT || source == Source.OVERRIDE || source == Source.PACK || source == Source.GEAR) {
                keys.add(key);
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (long key : keys.toLongArray()) {
            if (metaOf(key) != WILDCARD) {
                ItemStack stack = stackOf(key);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        }
        return stacks;
    }

    private void buildCatalog(List<ItemStack> targetStacks) {
        for (ItemStack stack : targetStacks) {
            long key = key(stack);
            PotionType potion = null;
            if (isPotionKey(key)) {
                // Potions are offered when a brewing recipe makes them (or the pack list prices them); tipped
                // arrows and loot-only potion types are not
                if (!ModConfig.potionResults || formOf(key) == FORM_ARROW || !(brewedKeys.contains(key) || fixed.contains(key))) {
                    continue;
                }
                potion = potionTypeOf(key);
            } else if (stack.hasTagCompound()) {
                // Spawn eggs, enchanted books: their value lives in NBT, a blank one would be pointless
                continue;
            }
            if (catalogByKey.containsKey(key)) {
                continue;
            }
            ResourceLocation id = stack.getItem().getRegistryName();
            if (id != null && id.getNamespace().equals(UpgraderWheel.MODID)) {
                continue;
            }
            if (matchesAny(blacklist, stack) || matchesAny(targetBlacklist, stack)) {
                continue;
            }
            double value = values.get(key);
            if (value < 0) {
                continue;
            }
            Source source = sources.get(key);
            if (source == Source.FALLBACK && !ModConfig.allowFallbackTargets) {
                continue;
            }
            if (value < ModConfig.minTargetValue) {
                continue;
            }
            int maxStack;
            try {
                // Anything with durability is handed out one at a time, whatever its stack limit claims
                boolean gear = stack.isItemStackDamageable() || stack.getItem().isDamageable() || stack.getMaxDamage() > 0;
                maxStack = gear ? 1 : Math.max(1, Math.min(64, stack.getMaxStackSize()));
            } catch (Throwable t) {
                maxStack = 1;
            }
            CatalogEntry entry = new CatalogEntry(stack.getItem(), metaOf(key), potion, value, maxStack, source);
            catalog.add(entry);
            catalogByKey.put(key, entry);
        }
        catalog.sort(Comparator.comparingDouble((CatalogEntry e) -> e.value));
    }

    private static boolean matchesAny(List<ItemPattern> patterns, ItemStack stack) {
        for (ItemPattern pattern : patterns) {
            if (pattern.matches(stack)) {
                return true;
            }
        }
        return false;
    }
}
