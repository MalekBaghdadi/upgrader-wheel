package com.malek.upgraderwheel.value;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;

/**
 * An item selector from the config: modid:item, modid:item:meta, modid:*, ore:oreName,
 * or a path with * wildcards such as srparasites:itemmobspawner_* or *:*spawn_egg* (any mod, any meta).
 * Potions are picked by potion type: potion:potioncore:flight (the drinkable bottle), splash:..., lingering:...,
 * arrow:... (tipped arrows); the type may use * as well (potion:potioncore:long_*).
 */
public final class ItemPattern {

    public enum Kind {
        ITEM, ITEM_META, MOD, ORE, GLOB, POTION
    }

    private static final String[] POTION_PREFIXES = {"potion", "splash", "lingering", "arrow"};

    public final Kind kind;
    public final String namespace;
    public final String path;
    public final int meta;
    public final String ore;
    public final String text;
    /** For POTION patterns: 0 potion, 1 splash, 2 lingering, 3 tipped arrow. */
    public final int potionForm;
    @Nullable
    private final java.util.regex.Pattern glob;

    private ItemPattern(Kind kind, String namespace, String path, int meta, String ore, String text) {
        this(kind, namespace, path, meta, ore, text, -1);
    }

    private ItemPattern(Kind kind, String namespace, String path, int meta, String ore, String text, int potionForm) {
        this.kind = kind;
        this.namespace = namespace;
        this.path = path;
        this.meta = meta;
        this.ore = ore;
        this.text = text;
        this.potionForm = potionForm;
        this.glob = kind == Kind.GLOB || kind == Kind.POTION ? java.util.regex.Pattern.compile(globToRegex(path)) : null;
    }

    /** For POTION patterns: whether the potion type with this registry name is selected. */
    public boolean matchesPotionName(ResourceLocation id) {
        return kind == Kind.POTION && (namespace.equals("*") || id.getNamespace().equals(namespace))
                && glob != null && glob.matcher(id.getPath()).matches();
    }

    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(java.util.regex.Pattern.quote(part));
        }
        return regex.toString();
    }

    /** True when this pattern can match the given registry name (ignoring meta and ore names). */
    public boolean matchesName(ResourceLocation id) {
        switch (kind) {
            case MOD:
                return id.getNamespace().equals(namespace);
            case GLOB:
                return (namespace.equals("*") || id.getNamespace().equals(namespace)) && glob != null && glob.matcher(id.getPath()).matches();
            case ITEM:
            case ITEM_META:
                return id.getNamespace().equals(namespace) && id.getPath().equals(path);
            default:
                return false;
        }
    }

    @Nullable
    public static ItemPattern parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.startsWith("#")) {
            return null;
        }
        if (s.regionMatches(true, 0, "ore:", 0, 4)) {
            String ore = s.substring(4).trim();
            return ore.isEmpty() ? null : new ItemPattern(Kind.ORE, null, null, 0, ore, s);
        }
        String[] parts = s.split(":");
        for (int form = 0; form < POTION_PREFIXES.length; form++) {
            if (parts.length >= 2 && parts[0].trim().equalsIgnoreCase(POTION_PREFIXES[form])) {
                // potion:<namespace>:<type>, potion:<type> (minecraft), or potion:* (every type)
                String namespace;
                String type;
                if (parts.length == 2) {
                    type = parts[1].trim().toLowerCase();
                    namespace = type.equals("*") ? "*" : "minecraft";
                } else if (parts.length == 3) {
                    namespace = parts[1].trim().toLowerCase();
                    type = parts[2].trim().toLowerCase();
                } else {
                    return null;
                }
                if (namespace.isEmpty() || type.isEmpty()) {
                    return null;
                }
                return new ItemPattern(Kind.POTION, namespace, type, 0, null, s, form);
            }
        }
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        String namespace = parts[0].trim().toLowerCase();
        String path = parts[1].trim().toLowerCase();
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        if (path.equals("*")) {
            return parts.length == 2 ? new ItemPattern(Kind.MOD, namespace, null, 0, null, s) : null;
        }
        if (path.indexOf('*') >= 0 || namespace.equals("*")) {
            return parts.length == 2 ? new ItemPattern(Kind.GLOB, namespace, path, 0, null, s) : null;
        }
        if (parts.length == 3) {
            String metaText = parts[2].trim();
            if (metaText.equals("*")) {
                return new ItemPattern(Kind.ITEM, namespace, path, 0, null, s);
            }
            try {
                int meta = Integer.parseInt(metaText);
                if (meta < 0 || meta > 32767) {
                    return null;
                }
                return new ItemPattern(Kind.ITEM_META, namespace, path, meta, null, s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new ItemPattern(Kind.ITEM, namespace, path, 0, null, s);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        ResourceLocation id = item.getRegistryName();
        if (id == null) {
            return false;
        }
        switch (kind) {
            case MOD:
                return id.getNamespace().equals(namespace);
            case GLOB:
                return (namespace.equals("*") || id.getNamespace().equals(namespace)) && glob != null && glob.matcher(id.getPath()).matches();
            case ITEM:
                return id.getNamespace().equals(namespace) && id.getPath().equals(path);
            case ITEM_META:
                return id.getNamespace().equals(namespace) && id.getPath().equals(path)
                        && ValueRegistry.normalizeMeta(item, stack.getMetadata()) == ValueRegistry.normalizeMeta(item, meta);
            case POTION: {
                if (ValueRegistry.potionKey(stack) < 0 || ValueRegistry.potionForm(item) != potionForm) {
                    return false;
                }
                ResourceLocation type = net.minecraft.potion.PotionUtils.getPotionFromItem(stack).getRegistryName();
                return type != null && matchesPotionName(type);
            }
            case ORE:
                try {
                    for (int oreId : OreDictionary.getOreIDs(stack)) {
                        if (ore.equals(OreDictionary.getOreName(oreId))) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {
                    // Some items break when asked for ore ids at odd metadata
                }
                return false;
            default:
                return false;
        }
    }

    /** The single item this pattern names, for ITEM and ITEM_META patterns. */
    @Nullable
    public Item item() {
        if (kind != Kind.ITEM && kind != Kind.ITEM_META) {
            return null;
        }
        Item item = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
        return item == null || item == net.minecraft.init.Items.AIR ? null : item;
    }

    @Override
    public String toString() {
        return text;
    }

    /** A pattern with a number after '=' (overrides and multipliers). */
    public static final class Valued {
        public final ItemPattern pattern;
        public final double value;

        public Valued(ItemPattern pattern, double value) {
            this.pattern = pattern;
            this.value = value;
        }

        @Nullable
        public static Valued parse(String raw) {
            if (raw == null) {
                return null;
            }
            int eq = raw.lastIndexOf('=');
            if (eq <= 0) {
                return null;
            }
            ItemPattern pattern = ItemPattern.parse(raw.substring(0, eq));
            if (pattern == null) {
                return null;
            }
            double value = com.malek.upgraderwheel.ModConfig.parseValue(raw.substring(eq + 1));
            return Double.isNaN(value) ? null : new Valued(pattern, value);
        }
    }
}
