package com.malek.upgraderwheel.value;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pack value lists: values for a modpack's raw materials (boss and mob drops, loot) that recipes cannot price.
 * One "id = value" per line, # starts a comment. A value of "nowin" keeps the item out of the result list.
 * <p>
 * The built-in list (RLCraft Dregora, inside the jar) is read first, then config/upgraderwheel-pack.txt, which holds the
 * player's own lines and wins over the built-in ones. Config overrides still win over both. Lines for mods that are not
 * installed simply match nothing, so the built-in list is safe in any pack.
 */
public final class PackValues {

    public static final String FILE_NAME = "upgraderwheel-pack.txt";
    private static final String BUILT_IN = "/assets/upgraderwheel/pack/rlcraft_dregora.txt";
    private static final String TEMPLATE = "/assets/upgraderwheel/pack/user_template.txt";
    /** The 1.1.0 default, which used to be copied into the config folder whole. */
    private static final String LEGACY = "/assets/upgraderwheel/pack/legacy_defaults.txt";
    /** First line of a file in the current format. */
    private static final String MARKER = "# Upgrader Wheel - your pack values";

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

    /** Built-in entries first (when enabled), then the player's own, so later lines win. */
    public static List<Entry> load() {
        List<Entry> entries = new ArrayList<>();
        if (ModConfig.builtInPackList) {
            List<String> builtIn = readResource(BUILT_IN);
            entries.addAll(parse(builtIn, "built-in pack list"));
        }
        File file = file();
        if (!file.exists()) {
            writeTemplate(file, Collections.<String>emptyList());
        } else {
            migrate(file);
        }
        if (file.exists()) {
            try {
                entries.addAll(parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8), FILE_NAME));
            } catch (IOException e) {
                UpgraderWheel.LOGGER.warn("Could not read {}: {}", file, e.toString());
            }
        }
        return entries;
    }

    private static List<Entry> parse(List<String> lines, String where) {
        List<Entry> entries = new ArrayList<>();
        int lineNumber = 0;
        for (String raw : lines) {
            lineNumber++;
            String line = stripComment(raw);
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.lastIndexOf('=');
            ItemPattern pattern = eq > 0 ? ItemPattern.parse(line.substring(0, eq)) : null;
            if (pattern == null) {
                UpgraderWheel.LOGGER.warn("{} line {}: expected 'modid:item = value', got '{}'", where, lineNumber, raw.trim());
                continue;
            }
            String valueText = line.substring(eq + 1).trim();
            if (valueText.equalsIgnoreCase("nowin")) {
                entries.add(new Entry(pattern, -1));
                continue;
            }
            double value = ModConfig.parseValue(valueText);
            if (!Double.isNaN(value)) {
                entries.add(new Entry(pattern, value));
                continue;
            }
            UpgraderWheel.LOGGER.warn("{} line {}: '{}' is not a value", where, lineNumber, valueText);
        }
        return entries;
    }

    private static String stripComment(String raw) {
        int hash = raw.indexOf('#');
        return (hash >= 0 ? raw.substring(0, hash) : raw).trim();
    }

    /** "id = value" with spacing and case evened out, for comparing lines. */
    private static String normalize(String raw) {
        return stripComment(raw).replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Files from 1.1.0 were a full copy of the built-in list. Keep only the lines the player wrote (anything that is not
     * one of the old defaults) in a fresh file, so old default values no longer hide the new built-in ones.
     */
    private static void migrate(File file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        if (!lines.isEmpty() && lines.get(0).startsWith(MARKER)) {
            return;
        }
        Set<String> legacy = new HashSet<>();
        for (String line : readResource(LEGACY)) {
            String n = normalize(line);
            if (!n.isEmpty()) {
                legacy.add(n);
            }
        }
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String n = normalize(line);
            if (!n.isEmpty() && !legacy.contains(n)) {
                kept.add(line.trim());
            }
        }
        try {
            Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            UpgraderWheel.LOGGER.warn("Could not back up {}: {}", file, e.toString());
            return;
        }
        writeTemplate(file, kept);
        UpgraderWheel.LOGGER.info("Moved {} to the new format (built-in values now come from the mod; {} line(s) of your own kept, old file saved as {}.bak)",
                FILE_NAME, kept.size(), FILE_NAME);
    }

    private static void writeTemplate(File file, List<String> ownLines) {
        List<String> out = new ArrayList<>(readResource(TEMPLATE));
        if (out.isEmpty() || !out.get(0).startsWith(MARKER)) {
            out.add(0, MARKER);
        }
        if (!ownLines.isEmpty()) {
            out.add("");
            out.add("# Kept from your old file:");
            out.addAll(ownLines);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            UpgraderWheel.LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static List<String> readResource(String path) {
        try (InputStream in = PackValues.class.getResourceAsStream(path)) {
            if (in == null) {
                return Collections.emptyList();
            }
            byte[] bytes = new byte[8192];
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(bytes)) > 0) {
                buffer.write(bytes, 0, n);
            }
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8).replace("\r", "");
            List<String> lines = new ArrayList<>();
            Collections.addAll(lines, text.split("\n", -1));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            return lines;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
