package com.malek.upgraderwheel.value;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-server tool, never run in normal play: kills every kind of mob many times (a fake player gets the kill, the
 * drops are recorded and thrown away) and writes config/upgraderwheel-drops.csv with each mob's health, armor, attack
 * and boss flag, and how often it drops each item. tools/drops.py turns that into values for drops that nothing else
 * prices, scaled by how hard the mob is. Mods react to deaths in their own ways (some spawn things), so only run this
 * on a throwaway world: start the server with -Dupgraderwheel.analyze=true (tools/test.sh does with ANALYZE=1).
 */
public final class DropAnalyzer {

    private static final int KILLS = 200;
    private static final long NANOS_PER_MOB = 2_000_000_000L;
    private static final long NANOS_TOTAL = 15L * 60 * 1_000_000_000L;

    private DropAnalyzer() {
    }

    public static boolean requested() {
        return Boolean.getBoolean("upgraderwheel.analyze");
    }

    /** Collects the drops of the one mob being killed and cancels them, so nothing lands in the world. */
    public static final class Catcher {
        Entity target;
        final List<ItemStack> drops = new ArrayList<>();

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onDrops(LivingDropsEvent event) {
            if (target == null || event.getEntity() != target) {
                return;
            }
            for (EntityItem item : event.getDrops()) {
                ItemStack stack = item.getItem();
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
            event.setCanceled(true);
        }
    }

    private static final class Tally {
        double count;
        int kills;
        String name;
    }

    public static File run(MinecraftServer server) throws IOException {
        File file = new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), "upgraderwheel-drops.csv");
        WorldServer world = server.getWorld(0);
        BlockPos spawn = world.getSpawnPoint();
        FakePlayer player = FakePlayerFactory.getMinecraft(world);
        player.setPosition(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
        Field recentlyHit = ObfuscationReflectionHelper.findField(EntityLivingBase.class, "field_70718_bc");
        Field attackingPlayer = ObfuscationReflectionHelper.findField(EntityLivingBase.class, "field_70717_bb");
        Catcher catcher = new Catcher();
        MinecraftForge.EVENT_BUS.register(catcher);
        long start = System.nanoTime();
        int mobs = 0;
        int failed = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            out.println("entity,mob,health,armor,attack,boss,monster,kills,id,meta,name,per_kill,chance");
            for (EntityEntry entry : ForgeRegistries.ENTITIES.getValuesCollection()) {
                Class<? extends Entity> type = entry.getEntityClass();
                if (type == null || !EntityLiving.class.isAssignableFrom(type) || System.nanoTime() - start > NANOS_TOTAL) {
                    continue;
                }
                String entityId = String.valueOf(entry.getRegistryName());
                String mobName = entityId;
                double health = 0;
                double armor = 0;
                double attack = 0;
                boolean boss = false;
                boolean monster = false;
                int kills = 0;
                Map<String, Tally> tallies = new LinkedHashMap<>();
                long mobStart = System.nanoTime();
                try {
                    for (int i = 0; i < KILLS && System.nanoTime() - mobStart < NANOS_PER_MOB; i++) {
                        Entity entity = entry.newInstance(world);
                        if (!(entity instanceof EntityLiving)) {
                            break;
                        }
                        EntityLiving living = (EntityLiving) entity;
                        living.setPosition(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
                        if (i == 0) {
                            mobName = safeName(living, entityId);
                            health = living.getMaxHealth();
                            armor = living.getTotalArmorValue();
                            IAttributeInstance damage = living.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.ATTACK_DAMAGE);
                            attack = damage == null ? 0 : damage.getAttributeValue();
                            boss = !living.isNonBoss();
                            monster = living instanceof IMob;
                        }
                        recentlyHit.setInt(living, 100);
                        attackingPlayer.set(living, player);
                        catcher.target = living;
                        catcher.drops.clear();
                        living.onDeath(DamageSource.causePlayerDamage(player));
                        catcher.target = null;
                        kills++;
                        Map<String, Boolean> seen = new LinkedHashMap<>();
                        for (ItemStack stack : catcher.drops) {
                            long key = ValueRegistry.key(stack);
                            String id = ValueRegistry.idOf(key) + "," + ValueRegistry.metaOf(key);
                            Tally tally = tallies.get(id);
                            if (tally == null) {
                                tally = new Tally();
                                tally.name = safeName(stack);
                                tallies.put(id, tally);
                            }
                            tally.count += stack.getCount();
                            if (seen.put(id, Boolean.TRUE) == null) {
                                tally.kills++;
                            }
                        }
                    }
                } catch (Throwable t) {
                    failed++;
                    catcher.target = null;
                    if (kills == 0) {
                        UpgraderWheel.LOGGER.debug("Drop analysis could not kill {}: {}", entityId, t.toString());
                        continue;
                    }
                }
                if (kills == 0) {
                    continue;
                }
                mobs++;
                String prefix = entityId + "," + csv(mobName) + "," + num(health) + "," + num(armor) + "," + num(attack) + ","
                        + boss + "," + monster + "," + kills + ",";
                if (tallies.isEmpty()) {
                    out.println(prefix + ",,,,");
                }
                for (Map.Entry<String, Tally> e : tallies.entrySet()) {
                    Tally tally = e.getValue();
                    out.println(prefix + e.getKey() + "," + csv(tally.name) + "," + num(tally.count / kills) + ","
                            + num(tally.kills / (double) kills));
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(catcher);
        }
        UpgraderWheel.LOGGER.info("Drop analysis: {} kinds of mob killed ({} failed) in {} s, written to {}", mobs, failed,
                (System.nanoTime() - start) / 1_000_000_000L, file.getName());
        scanLootTables(world, player);
        return file;
    }

    private static final int ROLLS = 100;

    /**
     * Rolls every loot table that is registered or shipped inside a mod jar (chests, fishing, gameplay; not entity
     * tables, the kills cover those) and writes config/upgraderwheel-loot.csv: how often each table gives each item.
     */
    private static void scanLootTables(WorldServer world, FakePlayer player) throws IOException {
        long start = System.nanoTime();
        Set<ResourceLocation> names = new LinkedHashSet<>(LootTableList.getAll());
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            File source = mod.getSource();
            if (source == null || !source.isFile()) {
                continue;
            }
            try (ZipFile zip = new ZipFile(source)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.startsWith("assets/") || !name.endsWith(".json")) {
                        continue;
                    }
                    String[] parts = name.split("/", 4);
                    if (parts.length == 4 && parts[2].equals("loot_tables")) {
                        names.add(new ResourceLocation(parts[1], parts[3].substring(0, parts[3].length() - 5)));
                    }
                }
            } catch (Throwable ignored) {
                // Not a readable jar
            }
        }
        File file = new File(Loader.instance().getConfigDir(), "upgraderwheel-loot.csv");
        int tables = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            out.println("table,rolls,id,meta,name,per_roll,chance");
            for (ResourceLocation name : names) {
                if (name.getPath().startsWith("entities/")) {
                    continue;
                }
                Map<String, Tally> tallies = new LinkedHashMap<>();
                int rolls = 0;
                long tableStart = System.nanoTime();
                try {
                    LootTable table = world.getLootTableManager().getLootTableFromLocation(name);
                    if (table == null || table == LootTable.EMPTY_LOOT_TABLE) {
                        continue;
                    }
                    for (int i = 0; i < ROLLS && System.nanoTime() - tableStart < 1_000_000_000L; i++) {
                        LootContext context = new LootContext.Builder(world).withLuck(0).withPlayer(player).build();
                        List<ItemStack> loot = table.generateLootForPools(world.rand, context);
                        rolls++;
                        Map<String, Boolean> seen = new LinkedHashMap<>();
                        for (ItemStack stack : loot) {
                            if (stack == null || stack.isEmpty()) {
                                continue;
                            }
                            long key = ValueRegistry.key(stack);
                            String id = ValueRegistry.idOf(key) + "," + ValueRegistry.metaOf(key);
                            Tally tally = tallies.get(id);
                            if (tally == null) {
                                tally = new Tally();
                                tally.name = safeName(stack);
                                tallies.put(id, tally);
                            }
                            tally.count += stack.getCount();
                            if (seen.put(id, Boolean.TRUE) == null) {
                                tally.kills++;
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (rolls == 0) {
                        continue;
                    }
                }
                if (rolls == 0 || tallies.isEmpty()) {
                    continue;
                }
                tables++;
                for (Map.Entry<String, Tally> e : tallies.entrySet()) {
                    Tally tally = e.getValue();
                    out.println(name + "," + rolls + "," + e.getKey() + "," + csv(tally.name) + "," + num(tally.count / rolls) + ","
                            + num(tally.kills / (double) rolls));
                }
            }
        }
        UpgraderWheel.LOGGER.info("Drop analysis: {} loot tables rolled in {} s, written to {}", tables,
                (System.nanoTime() - start) / 1_000_000_000L, file.getName());
    }

    private static String safeName(EntityLiving living, String fallback) {
        try {
            String name = living.getName();
            return name == null || name.isEmpty() ? fallback : name;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String safeName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable t) {
            return String.valueOf(stack.getItem().getRegistryName());
        }
    }

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.4f", v);
    }

    private static String csv(String text) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (clean.indexOf(',') >= 0 || clean.indexOf('"') >= 0) {
            return '"' + clean.replace("\"", "\"\"") + '"';
        }
        return clean;
    }
}
