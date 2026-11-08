package dev.nexobedrock.pack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Reads appearance references from every Nexo item YAML without depending on item IDs. */
public final class NexoItemConfigIndex {
    public Map<String, String> readTextureReferences(Path itemsDirectory) throws IOException {
        if (itemsDirectory == null || !Files.isDirectory(itemsDirectory)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(itemsDirectory)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase()
                            .endsWith(".yml"))
                    .sorted().toList()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                for (String id : yaml.getKeys(false)) {
                    String texture = yaml.getString(id + ".Pack.texture");
                    if (texture != null && !texture.isBlank()) {
                        result.putIfAbsent(id, texture.trim());
                    }
                }
            }
        }
        return Map.copyOf(result);
    }
}
