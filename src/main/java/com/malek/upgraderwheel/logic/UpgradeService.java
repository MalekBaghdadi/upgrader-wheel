package com.malek.upgraderwheel.logic;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.UpgraderWheel;
import com.malek.upgraderwheel.inventory.ContainerUpgrader;
import com.malek.upgraderwheel.network.Network;
import com.malek.upgraderwheel.network.PacketCatalog;
import com.malek.upgraderwheel.network.PacketRollResult;
import com.malek.upgraderwheel.value.ValueRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server side: checks bets, rolls, and hands out results. The client never decides anything.
 */
public final class UpgradeService {

    public static final class BetInfo {
        public final Codes.Bet status;
        public final double value;

        BetInfo(Codes.Bet status, double value) {
            this.status = status;
            this.value = value;
        }
    }

    private static final Map<UUID, Deque<Long>> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_CATALOG_REQUEST = new HashMap<>();
    private static final int CATALOG_CHUNK = 2048;

    private UpgradeService() {
    }

    public static void clearSessionState() {
        ATTEMPTS.clear();
        LAST_CATALOG_REQUEST.clear();
    }

    public static BetInfo evaluateBet(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new BetInfo(Codes.Bet.EMPTY, 0);
        }
        ValueRegistry registry = ValueRegistry.get();
        if (registry == null) {
            return new BetInfo(Codes.Bet.NOT_READY, 0);
        }
        if (registry.isBlacklistedBet(stack)) {
            return new BetInfo(Codes.Bet.BLACKLISTED, 0);
        }
        if (hasContents(stack)) {
            return new BetInfo(Codes.Bet.HAS_CONTENTS, 0);
        }
        double value = registry.stackValue(stack);
        if (!(value > 0)) {
            return new BetInfo(Codes.Bet.NO_VALUE, 0);
        }
        return new BetInfo(Codes.Bet.OK, value);
    }

    /** Backpacks, shulker boxes and the like must be emptied first, or their contents would vanish with them. */
    public static boolean hasContents(ItemStack stack) {
        try {
            if (stack.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                IItemHandler handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (!handler.getStackInSlot(i).isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the NBT checks
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            if (tag.hasKey("BlockEntityTag", 10)) {
                NBTTagCompound blockEntity = tag.getCompoundTag("BlockEntityTag");
                if (blockEntity.hasKey("Items", 9) && blockEntity.getTagList("Items", 10).tagCount() > 0) {
                    return true;
                }
            }
            if (tag.hasKey("Items", 9) && tag.getTagList("Items", 10).tagCount() > 0) {
                return true;
            }
            if (tag.hasKey("Inventory", 9) && tag.getTagList("Inventory", 10).tagCount() > 0) {
                return true;
            }
        }
        return false;
    }

    public static void handleRoll(EntityPlayerMP player, int windowId, int itemId, int meta, int amount) {
        Container open = player.openContainer;
        if (!(open instanceof ContainerUpgrader) || open.windowId != windowId) {
            return;
        }
        ContainerUpgrader container = (ContainerUpgrader) open;
        ValueRegistry registry = ValueRegistry.get();
        if (registry == null) {
            reply(player, windowId, Codes.Roll.NOT_READY, 0);
            return;
        }

        ItemStack bet = container.getBet();
        BetInfo betInfo = evaluateBet(bet);
        if (betInfo.status == Codes.Bet.EMPTY) {
            reply(player, windowId, Codes.Roll.NO_BET, 0);
            return;
        }
        if (betInfo.status != Codes.Bet.OK) {
            reply(player, windowId, Codes.Roll.BET_NOT_ALLOWED, 0);
            return;
        }

        Item targetItem = Item.getItemById(itemId);
        ValueRegistry.CatalogEntry target = targetItem == null ? null : registry.catalogEntry(targetItem, meta);
        if (target == null) {
            reply(player, windowId, Codes.Roll.BAD_TARGET, 0);
            return;
        }
        int count = Math.max(1, Math.min(target.maxStack, amount));
        double targetValue = target.value * count;
        double raw = UpgradeMath.rawChance(betInfo.value, targetValue, ModConfig.houseEdge);
        double chance = Math.min(ModConfig.maxChance, raw);
        if (raw < ModConfig.minChance) {
            reply(player, windowId, Codes.Roll.ODDS_TOO_LOW, chance);
            return;
        }
        if (!takeAttempt(player)) {
            reply(player, windowId, Codes.Roll.RATE_LIMITED, chance);
            return;
        }

        boolean win = player.getRNG().nextDouble() * 100.0 < chance;
        ItemStack betCopy = bet.copy();
        container.setBet(win ? target.createStack(count) : ItemStack.EMPTY);
        container.detectAndSendChanges();
        reply(player, windowId, win ? Codes.Roll.WIN : Codes.Roll.LOSE, chance);

        if (ModConfig.logRolls) {
            UpgraderWheel.LOGGER.info(String.format(Locale.ROOT, "%s bet %dx %s (%.1f) on %dx %s (%.1f) at %.3f%%: %s",
                    player.getName(), betCopy.getCount(), describe(betCopy), betInfo.value,
                    count, describe(target.createStack(1)), targetValue, chance, win ? "WON" : "lost"));
        }
    }

    private static String describe(ItemStack stack) {
        if (stack.getItem().getRegistryName() == null) {
            return "?";
        }
        String id = stack.getItem().getRegistryName().toString();
        return stack.getHasSubtypes() ? id + ":" + stack.getMetadata() : id;
    }

    private static void reply(EntityPlayerMP player, int windowId, Codes.Roll code, double chance) {
        Network.CHANNEL.sendTo(new PacketRollResult(windowId, code, chance), player);
    }

    private static boolean takeAttempt(EntityPlayerMP player) {
        if (ModConfig.attemptLimit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long window = ModConfig.attemptWindowSeconds * 1000L;
        Deque<Long> times = ATTEMPTS.get(player.getUniqueID());
        if (times == null) {
            times = new ArrayDeque<>();
            ATTEMPTS.put(player.getUniqueID(), times);
        }
        while (!times.isEmpty() && now - times.peekFirst() > window) {
            times.pollFirst();
        }
        if (times.size() >= ModConfig.attemptLimit) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /** Sends the result list in chunks. Throttled so a client cannot make the server spam it. */
    public static void sendCatalog(EntityPlayerMP player) {
        long now = System.currentTimeMillis();
        Long last = LAST_CATALOG_REQUEST.get(player.getUniqueID());
        if (last != null && now - last < 2000) {
            return;
        }
        LAST_CATALOG_REQUEST.put(player.getUniqueID(), now);

        ValueRegistry registry = ValueRegistry.getOrBuild();
        List<ValueRegistry.CatalogEntry> entries = registry.catalog();
        int chunks = Math.max(1, (entries.size() + CATALOG_CHUNK - 1) / CATALOG_CHUNK);
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * CATALOG_CHUNK;
            int to = Math.min(entries.size(), from + CATALOG_CHUNK);
            PacketCatalog packet = new PacketCatalog(registry.version(), chunk, chunks,
                    ModConfig.houseEdge, ModConfig.minChance, ModConfig.maxChance, entries.subList(from, to));
            Network.CHANNEL.sendTo(packet, player);
        }
    }
}
