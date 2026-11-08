package dev.nexobedrock.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.bedrock.GradientGlyphAtlas;
import dev.nexobedrock.conversion.ConvertedItem;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Gera o arquivo de custom mappings do Geyser no schema v2.
 */
public final class MappingWriter {
    private static final Set<String> CATEGORIES =
            Set.of("all", "construction", "nature", "equipment", "items",
                    "item_command_only", "none");

    public byte[] write(List<ConvertedItem> items, PluginConfig config) {
        Map<String, String> glyphNames;
        try {
            glyphNames = new GradientGlyphAtlas().build(items.stream()
                    .map(item -> item.entry().displayName()).toList()).rewritten();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not generate Bedrock text glyph mapping", exception);
        }
        Map<String, List<JsonObject>> byItem = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (ConvertedItem item : items) {
            JavaItemEntry entry = item.entry();
            if (entry.javaItem() == null || entry.javaItem().isBlank()) {
                continue;
            }

            JsonObject definition = new JsonObject();
            if (entry.mode() == MappingMode.MODERN && entry.itemModel() != null) {
                definition.addProperty("type", "definition");
                definition.addProperty("model", entry.itemModel());
            } else if (entry.customModelData() != null) {
                definition.addProperty("type", "legacy");
                definition.addProperty("custom_model_data", entry.customModelData());
            } else {
                continue;
            }

            String dedupe = entry.javaItem() + "|" + definition;
            if (!seen.add(dedupe)) {
                continue;
            }

            definition.addProperty("bedrock_identifier", item.bedrockId());
            if (entry.displayName() != null && !entry.displayName().isBlank()) {
                definition.addProperty("display_name", glyphNames.getOrDefault(
                        entry.displayName(), entry.displayName()));
            }

            JsonObject options = new JsonObject();
            options.addProperty("icon", item.iconKey());
            if (item.handheld()) {
                options.addProperty("display_handheld", true);
            }
            options.addProperty("creative_category", category(config.creativeCategory()));
            definition.add("bedrock_options", options);
            if (item.attachablePath() != null && item.geometryPath() == null) {
                JsonObject components = new JsonObject();
                JsonObject equippable = new JsonObject();
                equippable.addProperty("slot", dev.nexobedrock.pack.NexoArmorIndex.slot(entry.javaItem()));
                components.add("minecraft:equippable", equippable);
                components.addProperty("minecraft:max_stack_size", 1);
                definition.add("components", components);
            }

            byItem.computeIfAbsent(entry.javaItem(), ignored -> new ArrayList<>())
                    .add(definition);
        }

        JsonObject itemsObject = new JsonObject();
        byItem.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<JsonObject> definitions = entry.getValue();
                    definitions.sort(Comparator.comparing(MappingWriter::sortKey));
                    JsonArray array = new JsonArray();
                    definitions.forEach(array::add);
                    itemsObject.add(entry.getKey(), array);
                });

        JsonObject root = new JsonObject();
        root.addProperty("format_version", 2);
        root.add("items", itemsObject);
        return Json.prettyBytes(root);
    }

    private static String sortKey(JsonObject definition) {
        if (definition.has("custom_model_data")) {
            return String.format(Locale.ROOT, "0_%012d",
                    definition.get("custom_model_data").getAsInt());
        }
        return "1_" + definition.get("model").getAsString();
    }

    private static String category(String raw) {
        if (raw == null) {
            return "items";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return CATEGORIES.contains(value) ? value : "items";
    }
}
