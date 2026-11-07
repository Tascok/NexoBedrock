package dev.nexobedrock.conversion;

import com.google.gson.JsonObject;
import dev.nexobedrock.pack.JavaItemEntry;

import java.util.List;
import java.util.Map;

public record ConvertedItem(
        JavaItemEntry entry,
        String bedrockId,
        String iconKey,
        String iconPath,
        String atlasPath,
        String geometryId,
        String geometryPath,
        String attachablePath,
        String animationPath,
        byte[] iconPng,
        JsonObject geometry,
        JsonObject attachable,
        JsonObject animations,
        boolean flat,
        boolean handheld,
        List<String> warnings,
        Map<String, byte[]> extraResources
) {
    public ConvertedItem(JavaItemEntry entry, String bedrockId, String iconKey, String iconPath,
                         String atlasPath, String geometryId, String geometryPath,
                         String attachablePath, String animationPath, byte[] iconPng,
                         JsonObject geometry, JsonObject attachable, JsonObject animations,
                         boolean flat, boolean handheld, List<String> warnings) {
        this(entry, bedrockId, iconKey, iconPath, atlasPath, geometryId, geometryPath,
                attachablePath, animationPath, iconPng, geometry, attachable, animations,
                flat, handheld, warnings, Map.of());
    }
}
