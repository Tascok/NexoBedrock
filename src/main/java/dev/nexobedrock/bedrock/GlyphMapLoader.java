package dev.nexobedrock.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Loads the source-codepoint map embedded in a generated NexoBedrock mcpack. */
public final class GlyphMapLoader {
    private static final String ENTRY = "nexobedrock/glyph-map.json";

    public Map<Integer, Integer> load(Path mcpack) throws IOException {
        if (!Files.isRegularFile(mcpack)) return Map.of();
        try (ZipFile zip = new ZipFile(mcpack.toFile())) {
            var entry = zip.getEntry(ENTRY);
            if (entry == null) return Map.of();
            JsonObject root = JsonParser.parseString(new String(
                    zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject glyphs = root.getAsJsonObject("glyphs");
            if (glyphs == null) return Map.of();
            Map<Integer, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> mapping : glyphs.entrySet()) {
                int separator = mapping.getKey().lastIndexOf(':');
                if (separator < 0 || !mapping.getValue().isJsonPrimitive()) continue;
                try {
                    int source = Integer.parseInt(mapping.getKey().substring(separator + 1), 16);
                    int target = Integer.parseInt(mapping.getValue().getAsString(), 16);
                    result.putIfAbsent(source, target);
                } catch (NumberFormatException ignored) {
                    // Ignore corrupt individual entries; other glyph mappings remain usable.
                }
            }
            return Map.copyOf(result);
        } catch (IllegalStateException exception) {
            throw new IOException("Invalid glyph map in " + mcpack, exception);
        }
    }
}
