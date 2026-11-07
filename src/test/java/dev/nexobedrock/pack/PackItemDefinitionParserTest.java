package dev.nexobedrock.pack;

import com.google.gson.JsonObject;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackItemDefinitionParserTest {
    @Test
    void readsModernRangeDispatchCustomModelData() {
        JsonObject definition = Json.parseObject("""
                {
                  "model": {
                    "type": "range_dispatch",
                    "property": "custom_model_data",
                    "entries": [
                      {"threshold": 10, "model": {"type": "model", "model": "test:ten"}},
                      {"threshold": 20, "model": {"type": "model", "model": "test:twenty"}}
                    ],
                    "fallback": {"type": "model", "model": "test:default"}
                  }
                }
                """);

        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        definitions.put("minecraft:paper", definition);
        JavaPack pack = new JavaPack("test", "hash", Map.of(), definitions, Map.of(),
                List.of(), 0);

        List<JavaItemEntry> entries = new PackItemDefinitionParser().parse(pack);
        assertEquals(2, entries.size());
        JavaItemEntry ten = find(entries, "minecraft:paper", 10);
        assertNotNull(ten);
        assertEquals(MappingMode.LEGACY, ten.mode());
        assertEquals("test:ten", ten.modelPath());

        JavaItemEntry twenty = find(entries, "minecraft:paper", 20);
        assertNotNull(twenty);
        assertEquals("test:twenty", twenty.modelPath());
    }

    @Test
    void readsLegacyOverridesFromBaseItemModel() {
        JsonObject base = Json.parseObject("""
                {
                  "parent": "minecraft:item/paper",
                  "overrides": [
                    {"predicate": {"custom_model_data": 30}, "model": "test:thirty"}
                  ]
                }
                """);
        Map<String, JsonObject> models = new LinkedHashMap<>();
        models.put("test:base", base);
        JavaPack pack = new JavaPack("test", "hash", models, Map.of(), Map.of(),
                List.of(), 0);

        List<JavaItemEntry> entries = new PackItemDefinitionParser().parse(pack);
        JavaItemEntry entry = find(entries, "minecraft:paper", 30);
        assertNotNull(entry);
        assertEquals("test:thirty", entry.modelPath());
    }

    @Test
    void readsCustomNamespaceItemModelDefinitions() {
        JsonObject definition = Json.parseObject("""
                {"model": {"type": "model", "model": "model"}}
                """);
        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        definitions.put("test:my_item", definition);
        JavaPack pack = new JavaPack("test", "hash", Map.of(), definitions, Map.of(),
                List.of(), 0);

        List<JavaItemEntry> entries = new PackItemDefinitionParser().parse(pack);
        assertEquals(1, entries.size());
        JavaItemEntry entry = entries.get(0);
        assertEquals(MappingMode.MODERN, entry.mode());
        assertEquals("test:my_item", entry.itemModel());
        assertEquals("test:model", entry.modelPath());
        assertTrue(entry.javaItem() == null);
    }

    private JavaItemEntry find(List<JavaItemEntry> entries, String javaItem, int cmd) {
        return entries.stream()
                .filter(entry -> javaItem.equals(entry.javaItem()))
                .filter(entry -> entry.customModelData() != null
                        && entry.customModelData() == cmd)
                .findFirst()
                .orElse(null);
    }
}
