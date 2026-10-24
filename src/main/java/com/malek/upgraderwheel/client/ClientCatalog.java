package com.malek.upgraderwheel.client;

import com.malek.upgraderwheel.network.PacketCatalog;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The client's copy of the result list, assembled from the server's chunks.
 */
public final class ClientCatalog {

    public static final class Entry {
        public final Item item;
        public final int meta;
        public final int itemId;
        public final double value;
        public final int maxStack;
        public final ItemStack stack;
        public final String name;
        public final String searchName;
        public final String modId;

        Entry(Item item, int itemId, int meta, double value, int maxStack) {
            this.item = item;
            this.itemId = itemId;
            this.meta = meta;
            this.value = value;
            this.maxStack = Math.max(1, maxStack);
            this.stack = new ItemStack(item, 1, meta);
            String display;
            try {
                display = stack.getDisplayName();
            } catch (Throwable t) {
                display = String.valueOf(item.getRegistryName());
            }
            this.name = display == null ? "?" : display;
            this.searchName = this.name.toLowerCase(Locale.ROOT);
            ResourceLocation id = item.getRegistryName();
            this.modId = id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
        }

        public boolean stackable() {
            return maxStack > 1;
        }
    }

    private static int version = Integer.MIN_VALUE;
    private static List<Entry> entries = Collections.emptyList();
    private static java.util.Map<Long, Entry> byKey = Collections.emptyMap();
    private static double houseEdge = 0.10;
    private static double minChance = 1.0;
    private static double maxChance = 90.0;

    private static int pendingVersion = Integer.MIN_VALUE;
    private static PacketCatalog[] pendingChunks;
    private static int pendingReceived;

    private ClientCatalog() {
    }

    public static int version() {
        return version;
    }

    public static boolean isReady(int wantedVersion) {
        return version == wantedVersion;
    }

    public static List<Entry> entries() {
        return entries;
    }

    private static long lookupKey(int itemId, int meta) {
        return ((long) itemId << 16) | (meta & 0xFFFF);
    }

    /** The listed entry for this item, trying its exact variant first; null when it is not in the result list. */
    @javax.annotation.Nullable
    public static Entry find(ItemStack stack) {
        int id = Item.getIdFromItem(stack.getItem());
        Entry e = byKey.get(lookupKey(id, stack.getMetadata()));
        return e != null ? e : byKey.get(lookupKey(id, 0));
    }

    public static double houseEdge() {
        return houseEdge;
    }

    public static double minChance() {
        return minChance;
    }

    public static double maxChance() {
        return maxChance;
    }

    /** Returns true when the last chunk of a version arrived and the list is ready. */
    static boolean accept(PacketCatalog chunk) {
        if (chunk.chunkCount <= 0 || chunk.chunk < 0 || chunk.chunk >= chunk.chunkCount) {
            return false;
        }
        if (pendingChunks == null || pendingVersion != chunk.version || pendingChunks.length != chunk.chunkCount) {
            pendingVersion = chunk.version;
            pendingChunks = new PacketCatalog[chunk.chunkCount];
            pendingReceived = 0;
        }
        if (pendingChunks[chunk.chunk] == null) {
            pendingChunks[chunk.chunk] = chunk;
            pendingReceived++;
        }
        if (pendingReceived < pendingChunks.length) {
            return false;
        }

        List<Entry> built = new ArrayList<>();
        for (PacketCatalog part : pendingChunks) {
            for (int i = 0; i < part.itemIds.length; i++) {
                Item item = Item.getItemById(part.itemIds[i]);
                if (item == null || item == Items.AIR) {
                    continue;
                }
                built.add(new Entry(item, part.itemIds[i], part.metas[i] & 0xFFFF, part.values[i], part.maxStacks[i] & 0xFF));
            }
        }
        built.sort(Comparator.comparingDouble((Entry e) -> e.value).thenComparing(e -> e.name));

        PacketCatalog first = pendingChunks[0];
        houseEdge = first.houseEdge;
        minChance = first.minChance;
        maxChance = first.maxChance;
        entries = Collections.unmodifiableList(built);
        java.util.Map<Long, Entry> map = new java.util.HashMap<>();
        for (Entry e : built) {
            map.put(lookupKey(e.itemId, e.meta), e);
        }
        byKey = map;
        version = pendingVersion;
        pendingChunks = null;
        pendingReceived = 0;
        return true;
    }
}
