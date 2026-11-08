package dev.nexobedrock.pack;

import dev.nexobedrock.config.MappingMode;

import java.util.Set;

public record JavaItemEntry(
        String id,
        String javaItem,
        MappingMode mode,
        Integer customModelData,
        String itemModel,
        String modelPath,
        String iconTexture,
        String displayName,
        Set<String> flags,
        String source
) {
    public JavaItemEntry(String id, String javaItem, MappingMode mode,
                         Integer customModelData, String itemModel, String modelPath,
                         String displayName, Set<String> flags, String source) {
        this(id, javaItem, mode, customModelData, itemModel, modelPath, null,
                displayName, flags, source);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public JavaItemEntry withModelPath(String newModelPath) {
        return new JavaItemEntry(id, javaItem, mode, customModelData, itemModel, newModelPath,
                iconTexture, displayName, flags, source);
    }

    public JavaItemEntry withIconTexture(String newIconTexture) {
        return new JavaItemEntry(id, javaItem, mode, customModelData, itemModel, modelPath,
                newIconTexture, displayName, flags, source);
    }

    public JavaItemEntry withDisplayName(String newDisplayName) {
        return new JavaItemEntry(id, javaItem, mode, customModelData, itemModel, modelPath,
                iconTexture, newDisplayName, flags, source);
    }

    public String key() {
        return javaItem + "|" + mode + "|" + (customModelData == null ? "-" : customModelData)
                + "|" + (itemModel == null ? "-" : itemModel);
    }
}
