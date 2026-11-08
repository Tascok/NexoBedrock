package dev.nexobedrock.pack;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
