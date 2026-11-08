package dev.nexobedrock.pack;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import dev.nexobedrock.util.Hashing;

public record JavaPack(
        String source,
        String sourceHash,
        Map<String, JsonObject> models,
        Map<String, JsonObject> itemDefinitions,
        Map<String, JsonObject> fonts,
        Map<String, byte[]> textures,
        Set<String> animatedTextures,
        List<String> warnings,
        long sizeBytes
) {
    /** Adds generated font definitions and their assets without replacing item models. */
    public JavaPack withGeneratedFonts(JavaPack generated) {
        Map<String, JsonObject> combinedFonts = new LinkedHashMap<>(fonts);
        combinedFonts.putAll(generated.fonts());
        Map<String, byte[]> combinedTextures = new LinkedHashMap<>(textures);
        generated.textures().forEach(combinedTextures::putIfAbsent);
        List<String> combinedWarnings = new ArrayList<>(warnings);
        combinedWarnings.addAll(generated.warnings());
        return new JavaPack(source, Hashing.sha256Hex(sourceHash + "|fonts|" + generated.sourceHash()),
                models, itemDefinitions, Map.copyOf(combinedFonts), Map.copyOf(combinedTextures),
                animatedTextures, List.copyOf(combinedWarnings), sizeBytes + generated.sizeBytes());
    }
    public JavaPack(String source, String sourceHash, Map<String, JsonObject> models,
                    Map<String, JsonObject> itemDefinitions, Map<String, byte[]> textures,
                    List<String> warnings, long sizeBytes) {
        this(source, sourceHash, models, itemDefinitions, Map.of(), textures, Set.of(),
                warnings, sizeBytes);
    }

    public JavaPack(String source, String sourceHash, Map<String, JsonObject> models,
                    Map<String, JsonObject> itemDefinitions, Map<String, byte[]> textures,
                    Set<String> animatedTextures, List<String> warnings, long sizeBytes) {
        this(source, sourceHash, models, itemDefinitions, Map.of(), textures, animatedTextures,
                warnings, sizeBytes);
    }

    public JsonObject model(String key) {
        return models.get(key);
    }

    public JsonObject itemDefinition(String key) {
        return itemDefinitions.get(key);
    }

    public JsonObject font(String key) {
        return fonts.get(key);
    }

    public byte[] texture(String key) {
        return textures.get(key);
    }

    public boolean isAnimated(String key) {
        return key != null && animatedTextures.contains(key);
    }
}
