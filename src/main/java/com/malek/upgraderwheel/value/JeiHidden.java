package com.malek.upgraderwheel.value;

import com.malek.upgraderwheel.UpgraderWheel;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Items the modpack hides from JEI (config/jei/itemBlacklist.cfg). Pack makers hide what players are not meant to get:
 * disabled features, compatibility items for mods that are not installed, creative-only things. Those make poor prizes.
 */
final class JeiHidden {

    private JeiHidden() {
    }

    static List<ItemPattern> load() {
        File file = new File(new File(Loader.instance().getConfigDir(), "jei"), "itemBlacklist.cfg");
        if (!file.isFile()) {
            return Collections.emptyList();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            UpgraderWheel.LOGGER.warn("Could not read {}: {}", file, e.toString());
            return Collections.emptyList();
        }
        List<ItemPattern> patterns = new ArrayList<>();
        boolean inList = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (!inList) {
                inList = line.startsWith("S:itemBlacklist") && line.endsWith("<");
                continue;
            }
            if (line.startsWith(">")) {
                break;
            }
            // JEI ids are modid:item, modid:item:meta or modid:item:*; fluid containers and NBT forms are skipped
            if (line.isEmpty() || line.indexOf(';') >= 0 || line.indexOf('{') >= 0) {
                continue;
            }
            if (line.endsWith(":*")) {
                line = line.substring(0, line.length() - 2);
            }
            ItemPattern pattern = ItemPattern.parse(line);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }
}
