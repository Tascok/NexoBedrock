package dev.nexobedrock.pack;

import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.config.PluginConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combina o snapshot da API do Nexo com o que foi extraido do pack.
 *
 * A API do Nexo e a fonte de verdade para item base, CMD e nome. O pack
 * completa o caminho do modelo. Quando o pack ja passou pela obfuscacao do
 * Nexo (zip), os caminhos do pack sao os unicos que existem, entao eles tem
 * prioridade.
 */
public final class ItemIndexBuilder {
    public record Result(List<JavaItemEntry> entries, List<String> warnings) {
    }

    public Result build(NexoItemIndex nexoIndex, List<JavaItemEntry> packEntries,
                        PluginConfig config, boolean preferPackModelPaths) {
        List<String> warnings = new ArrayList<>();
        Map<String, JavaItemEntry> byLegacy = new LinkedHashMap<>();
        Map<String, JavaItemEntry> byModern = new LinkedHashMap<>();
        for (JavaItemEntry entry : packEntries) {
            if (entry.mode() == MappingMode.MODERN && entry.itemModel() != null) {
                byModern.putIfAbsent(entry.itemModel(), entry);
            } else if (entry.mode() == MappingMode.LEGACY && entry.customModelData() != null) {
                byLegacy.putIfAbsent(entry.javaItem() + "|" + entry.customModelData(), entry);
            }
        }

        Map<String, JavaItemEntry> merged = new LinkedHashMap<>();
        for (JavaItemEntry nexo : nexoIndex.entries()) {
            JavaItemEntry entry = applyMode(nexo, config.mappingMode());
            String modelPath = preferPackModelPaths
                    ? packModelPath(entry, byLegacy, byModern)
                    : null;
            if (modelPath == null || modelPath.isBlank()) {
                modelPath = entry.modelPath();
            }
            if (modelPath == null || modelPath.isBlank()) {
                modelPath = packModelPath(entry, byLegacy, byModern);
            }
            if (modelPath == null || modelPath.isBlank()) {
                warnings.add("Item " + entry.id() + " sem modelo associado (java="
                        + entry.javaItem() + ")");
            }
            JavaItemEntry withModel = entry.withModelPath(modelPath);
            merged.putIfAbsent(mergeKey(withModel), withModel);
        }

        for (JavaItemEntry packEntry : packEntries) {
            if (packEntry.mode() == MappingMode.MODERN) {
                // Item model moderno sem item base nao pode ser registrado no Geyser.
                if (packEntry.javaItem() == null) {
                    continue;
                }
            }
            String key = mergeKey(packEntry);
            JavaItemEntry existing = merged.get(key);
            if (existing == null) {
                merged.putIfAbsent(key, packEntry);
            } else if ((existing.modelPath() == null || existing.modelPath().isBlank())
                    && packEntry.modelPath() != null) {
                merged.put(key, existing.withModelPath(packEntry.modelPath()));
            }
        }

        return new Result(List.copyOf(merged.values()), List.copyOf(warnings));
    }

    private String packModelPath(JavaItemEntry entry, Map<String, JavaItemEntry> byLegacy,
                                 Map<String, JavaItemEntry> byModern) {
        if (entry.mode() == MappingMode.MODERN && entry.itemModel() != null) {
            JavaItemEntry packEntry = byModern.get(entry.itemModel());
            return packEntry == null ? null : packEntry.modelPath();
        }
        if (entry.customModelData() != null && entry.javaItem() != null) {
            JavaItemEntry packEntry = byLegacy.get(entry.javaItem() + "|"
                    + entry.customModelData());
            return packEntry == null ? null : packEntry.modelPath();
        }
        return null;
    }

    private JavaItemEntry applyMode(JavaItemEntry entry, MappingMode configured) {
        if (configured == MappingMode.LEGACY) {
            if (entry.customModelData() != null) {
                return entry.mode() == MappingMode.LEGACY ? entry : copy(entry, MappingMode.LEGACY);
            }
            if (entry.itemModel() != null) {
                return copy(entry, MappingMode.MODERN);
            }
            return entry;
        }
        if (configured == MappingMode.MODERN) {
            if (entry.itemModel() != null) {
                return copy(entry, MappingMode.MODERN);
            }
            if (entry.customModelData() != null) {
                return copy(entry, MappingMode.LEGACY);
            }
            return entry;
        }
        // AUTO
        if (entry.itemModel() != null) {
            return copy(entry, MappingMode.MODERN);
        }
        if (entry.customModelData() != null) {
            return copy(entry, MappingMode.LEGACY);
        }
        return entry;
    }

    private JavaItemEntry copy(JavaItemEntry entry, MappingMode mode) {
        return new JavaItemEntry(entry.id(), entry.javaItem(), mode, entry.customModelData(),
                entry.itemModel(), entry.modelPath(), entry.iconTexture(), entry.displayName(),
                entry.flags(), entry.source());
    }

    private String mergeKey(JavaItemEntry entry) {
        if (entry.mode() == MappingMode.MODERN && entry.itemModel() != null) {
            return "modern|" + entry.javaItem() + "|" + entry.itemModel();
        }
        return "legacy|" + entry.javaItem() + "|" + entry.customModelData();
    }
}
