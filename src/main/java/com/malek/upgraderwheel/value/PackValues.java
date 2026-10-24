package com.malek.upgraderwheel.value;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pack value list, config/upgraderwheel-pack.txt: values for a modpack's raw materials (boss and mob drops,
 * loot) that recipes cannot price. One "id = value" per line, # starts a comment. A value of "nowin" keeps the
 * item out of the result list. Config overrides still win over this file. Written from the built-in
 * RLCraft Dregora list the first time the mod runs; delete the file to get the built-in list back.
 */
public final class PackValues {

    public static final String FILE_NAME = "upgraderwheel-pack.txt";
    private static final String DEFAULT_RESOURCE = "/assets/upgraderwheel/pack/rlcraft_dregora.txt";

    public static final class Entry {
        public final ItemPattern pattern;
        /** Negative means "nowin". */
        public final double value;

        Entry(ItemPattern pattern, double value) {
            this.pattern = pattern;
            this.value = value;
        }

        public boolean noWin() {
            return value < 0;
        }
    }

    private PackValues() {
    }

    public static File file() {
        return new File(Loader.instance().getConfigDir(), FILE_NAME);
    }

    public static List<Entry> load() {
        File file = file();
        if (!file.exists()) {
            writeDefault(file);
        }
        if (!file.exists()) {
            return Collections.emptyList();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            UpgraderWheel.LOGGER.warn("Could not read {}: {}", file, e.toString());
            return Collections.emptyList();
        }
        List<Entry> entries = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = raw;
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.lastIndexOf('=');
            ItemPattern pattern = eq > 0 ? ItemPattern.parse(line.substring(0, eq)) : null;
            if (pattern == null) {
                UpgraderWheel.LOGGER.warn("{} line {}: expected 'modid:item = value', got '{}'", FILE_NAME, lineNumber, raw.trim());
                continue;
            }
            String valueText = line.substring(eq + 1).trim();
            if (valueText.equalsIgnoreCase("nowin")) {
                entries.add(new Entry(pattern, -1));
                continue;
            }
            double value = com.malek.upgraderwheel.ModConfig.parseValue(valueText);
            if (!Double.isNaN(value)) {
                entries.add(new Entry(pattern, value));
                continue;
            }
            UpgraderWheel.LOGGER.warn("{} line {}: '{}' is not a value", FILE_NAME, lineNumber, valueText);
        }
        return entries;
    }

    private static void writeDefault(File file) {
        try (InputStream in = PackValues.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(in, file.toPath());
        } catch (IOException e) {
            UpgraderWheel.LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }
}
