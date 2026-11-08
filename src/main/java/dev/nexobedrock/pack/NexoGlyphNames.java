package dev.nexobedrock.pack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads identifiers and original texture names from Nexo glyph definitions. */
public final class NexoGlyphNames {
    public record Glyph(String name, String texture) { }

    public Map<String, List<Glyph>> read(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) return Map.of();
        Map<String, List<Glyph>> result = new LinkedHashMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return name.endsWith(".yml") || name.endsWith(".yaml");
            }).sorted().toList()) {
                YamlConfiguration yaml = new YamlConfiguration();
                try { yaml.load(file.toFile()); }
                catch (InvalidConfigurationException exception) {
                    throw new IOException("YAML de glyphs Nexo inválido: " + file, exception);
                }
                for (String name : yaml.getKeys(false)) {
                    ConfigurationSection glyph = yaml.getConfigurationSection(name);
                    if (glyph == null) continue;
                    String character = glyph.getString("char");
                    String texture = glyph.getString("texture");
                    if (character == null || character.codePointCount(0, character.length()) != 1
                            || texture == null || texture.isBlank()) continue;
                    String font = glyph.getString("font", "minecraft:default");
                    if (font == null || font.isBlank()) font = "minecraft:default";
                    if (!font.contains(":")) font = "minecraft:" + font;
                    String key = font + ":" + String.format("%04X", character.codePointAt(0));
                    result.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new Glyph(name, texture.trim()));
                }
            }
        }
        result.replaceAll((key, value) -> List.copyOf(value.stream().distinct().toList()));
        return Map.copyOf(result);
    }
}
