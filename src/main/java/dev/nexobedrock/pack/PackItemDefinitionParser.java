package dev.nexobedrock.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.util.Names;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extrai mapeamentos Java -> modelo a partir de:
 *  - assets/<ns>/items/*.json (item model definitions modernas, incluindo
 *    range_dispatch em custom_model_data);
 *  - overrides legados dentro dos modelos.
 *
 * E usado como fallback quando a API do Nexo nao esta disponivel e tambem
 * para capturar itens de outros plugins no mesmo pack.
 */
public final class PackItemDefinitionParser {
    public List<JavaItemEntry> parse(JavaPack pack) {
        Map<String, JavaItemEntry> entries = new LinkedHashMap<>();
        parseItemDefinitions(pack, entries);
        parseLegacyOverrides(pack, entries);
        return new ArrayList<>(entries.values());
    }

    private void parseItemDefinitions(JavaPack pack, Map<String, JavaItemEntry> entries) {
        for (Map.Entry<String, JsonObject> definition : pack.itemDefinitions().entrySet()) {
            String key = definition.getKey();
            String namespace = Names.namespaceOf(key);
            String path = Names.pathOf(key);
            JsonObject root = definition.getValue();
            JsonElement modelNode = root.get("model");
            if (modelNode == null) {
                continue;
            }

            if ("minecraft".equals(namespace)) {
                String baseItem = "minecraft:" + path;
                Set<String> seen = new LinkedHashSet<>();
                parseNode(modelNode, baseItem, null, null, entries, seen, "pack-items", namespace);
            } else {
                // Item model definition de um namespace customizado (ex.: nexo:key).
                // O item base sera preenchido pelo indice do Nexo.
                Set<String> seen = new LinkedHashSet<>();
                parseNode(modelNode, null, null, key, entries, seen, "pack-items", namespace);
            }
        }
    }

    private void parseNode(JsonElement node, String baseItem, Integer customModelData, String itemModelKey,
                           Map<String, JavaItemEntry> entries, Set<String> seen, String source,
                           String defaultNamespace) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        String type = object.has("type") && object.get("type").isJsonPrimitive()
                ? object.get("type").getAsString()
                : null;

        if (type == null) {
            String model = string(object, "model");
            if (model != null) {
                emit(model, baseItem, customModelData, itemModelKey, entries, seen, source,
                        defaultNamespace);
            }
            return;
        }

        switch (type) {
            case "model" -> {
                String model = string(object, "model");
                if (model != null) {
                    emit(model, baseItem, customModelData, itemModelKey, entries, seen, source,
                            defaultNamespace);
                }
            }
            case "range_dispatch" -> {
                String property = string(object, "property");
                if (!"custom_model_data".equals(property)) {
                    return;
                }
                JsonArray array = array(object, "entries");
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject entry = element.getAsJsonObject();
                        Integer threshold = intValue(entry.get("threshold"));
                        if (threshold == null) {
                            continue;
                        }
                        parseNode(entry.get("model"), baseItem, threshold, itemModelKey, entries,
                                seen, source, defaultNamespace);
                    }
                }
                parseNode(object.get("fallback"), baseItem, customModelData, itemModelKey, entries,
                        seen, source, defaultNamespace);
            }
            case "condition" -> {
                // For bows, 'using_item' true is a pulling variant, not the
                // inventory/standby model that the item mapping must reference.
                boolean using = "using_item".equals(string(object, "property"));
                parseNode(object.get(using ? "on_false" : "on_true"), baseItem,
                        customModelData, itemModelKey, entries, seen, source, defaultNamespace);
                parseNode(object.get(using ? "on_true" : "on_false"), baseItem,
                        customModelData, itemModelKey, entries, seen, source, defaultNamespace);
            }
            case "select" -> {
                boolean chargeType = "charge_type".equals(string(object, "property"));
                if (chargeType) {
                    parseNode(object.get("fallback"), baseItem, customModelData,
                            itemModelKey, entries, seen, source, defaultNamespace);
                }
                JsonArray cases = array(object, "cases");
                if (cases != null) {
                    for (JsonElement element : cases) {
                        if (element.isJsonObject()) {
                            parseNode(element.getAsJsonObject().get("model"), baseItem,
                                    customModelData, itemModelKey, entries, seen, source,
                                    defaultNamespace);
                        }
                    }
                }
                if (!chargeType) {
                    parseNode(object.get("fallback"), baseItem, customModelData,
                            itemModelKey, entries, seen, source, defaultNamespace);
                }
            }
            case "composite" -> {
                JsonArray models = array(object, "models");
                if (models != null && !models.isEmpty()) {
                    // Geyser nao representa composite; usamos o primeiro modelo
                    // como aproximacao e registramos no relatorio.
                    parseNode(models.get(0), baseItem, customModelData, itemModelKey, entries,
                            seen, source, defaultNamespace);
                }
            }
            default -> {
                // "special" e tipos desconhecidos ficam de fora.
            }
        }
    }

    private void emit(String model, String baseItem, Integer customModelData, String itemModelKey,
                      Map<String, JavaItemEntry> entries, Set<String> seen, String source,
                      String defaultNamespace) {
        if (model == null || model.isBlank()) {
            return;
        }
        String modelKey = Names.normalizeKey(model, defaultNamespace);
        if (modelKey == null) {
            return;
        }
        if (itemModelKey != null) {
            // Item model moderno sem item base: sera resolvido pelo Nexo.
            String dedupe = "modern|" + itemModelKey + "|" + modelKey;
            if (!seen.add(dedupe)) {
                return;
            }
            String id = Names.pathOf(itemModelKey).replace('/', '_');
            entries.putIfAbsent(dedupe, new JavaItemEntry(
                    id, null, MappingMode.MODERN, null, itemModelKey, modelKey,
                    id, Set.of(), source));
            return;
        }
        if (baseItem == null || customModelData == null) {
            return;
        }
        String dedupe = "legacy|" + baseItem + "|" + customModelData;
        if (!seen.add(dedupe)) {
            return;
        }
        String id = Names.pathOf(baseItem) + "_" + customModelData;
        entries.putIfAbsent(dedupe, new JavaItemEntry(
                id, baseItem, MappingMode.LEGACY, customModelData, null, modelKey,
                id, Set.of(), source));
    }

    private void parseLegacyOverrides(JavaPack pack, Map<String, JavaItemEntry> entries) {
        for (Map.Entry<String, JsonObject> modelEntry : pack.models().entrySet()) {
            JsonObject model = modelEntry.getValue();
            JsonArray overrides = array(model, "overrides");
            if (overrides == null || overrides.isEmpty()) {
                continue;
            }
            String baseItem = baseItemFor(pack, modelEntry.getKey());
            if (baseItem == null) {
                continue;
            }
            for (JsonElement element : overrides) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject override = element.getAsJsonObject();
                JsonObject predicate = override.has("predicate") && override.get("predicate").isJsonObject()
                        ? override.getAsJsonObject("predicate")
                        : null;
                if (predicate == null) {
                    continue;
                }
                Integer customModelData = intValue(predicate.get("custom_model_data"));
                if (customModelData == null) {
                    continue;
                }
                String modelPath = string(override, "model");
                if (modelPath == null) {
                    continue;
                }
                String modelKey = Names.normalizeKey(modelPath,
                        Names.namespaceOf(modelEntry.getKey()));
                String dedupe = "legacy|" + baseItem + "|" + customModelData;
                String id = Names.pathOf(baseItem) + "_" + customModelData;
                entries.putIfAbsent(dedupe, new JavaItemEntry(
                        id, baseItem, MappingMode.LEGACY, customModelData, null, modelKey,
                        id, Set.of(), "pack-overrides"));
            }
        }
    }

    private String baseItemFor(JavaPack pack, String modelKey) {
        String normalized = Names.normalizeKey(modelKey, "minecraft");
        if (normalized == null) {
            return null;
        }
        String current = normalized;
        for (int depth = 0; depth < 32 && current != null; depth++) {
            if (current.startsWith("minecraft:item/")) {
                return "minecraft:" + current.substring("minecraft:item/".length());
            }
            if (current.startsWith("minecraft:block/")) {
                return null;
            }
            JsonObject model = pack.model(current);
            if (model == null) {
                return null;
            }
            JsonElement parent = model.get("parent");
            current = parent != null && parent.isJsonPrimitive()
                    ? Names.normalizeKey(parent.getAsString(), "minecraft")
                    : null;
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static Integer intValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return (int) value;
    }
}
