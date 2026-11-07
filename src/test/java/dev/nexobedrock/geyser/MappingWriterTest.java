package dev.nexobedrock.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.TestFixtures;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.conversion.ConvertedItem;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingWriterTest {
    @Test
    void writesGeyserV2LegacyAndModernDefinitions() {
        JavaItemEntry legacy = TestFixtures.legacy("key_lvl1", "minecraft:paper",
                13400, "default/key_lvl1");
        ConvertedItem legacyItem = TestFixtures.converted(legacy, "nexobedrock:key_lvl1",
                "nexobedrock.key_lvl1", true);

        JavaItemEntry modern = TestFixtures.modern("yummy", "minecraft:flint",
                "geyser_mc:yummy_food", "geyser_mc:yummy_food");
        ConvertedItem modernItem = TestFixtures.converted(modern, "nexobedrock:yummy",
                "nexobedrock.yummy", false);

        byte[] bytes = new MappingWriter().write(List.of(legacyItem, modernItem),
                TestFixtures.config());
        JsonObject root = Json.parseObject(bytes);

        assertEquals(2, root.get("format_version").getAsInt());
        JsonObject items = root.getAsJsonObject("items");
        assertTrue(items.has("minecraft:paper"));
        assertTrue(items.has("minecraft:flint"));

        JsonObject legacyDefinition = items.getAsJsonArray("minecraft:paper").get(0)
                .getAsJsonObject();
        assertEquals("legacy", legacyDefinition.get("type").getAsString());
        assertEquals(13400, legacyDefinition.get("custom_model_data").getAsInt());
        assertEquals("nexobedrock:key_lvl1",
                legacyDefinition.get("bedrock_identifier").getAsString());
        JsonObject options = legacyDefinition.getAsJsonObject("bedrock_options");
        assertEquals("nexobedrock.key_lvl1", options.get("icon").getAsString());
        assertTrue(options.get("display_handheld").getAsBoolean());
        assertEquals("items", options.get("creative_category").getAsString());

        JsonObject modernDefinition = items.getAsJsonArray("minecraft:flint").get(0)
                .getAsJsonObject();
        assertEquals("definition", modernDefinition.get("type").getAsString());
        assertEquals("geyser_mc:yummy_food", modernDefinition.get("model").getAsString());
        assertEquals("nexobedrock:yummy",
                modernDefinition.get("bedrock_identifier").getAsString());
        assertFalse(modernDefinition.getAsJsonObject("bedrock_options")
                .has("display_handheld"));
    }

    @Test
    void deduplicatesTheSameJavaMapping() {
        JavaItemEntry entry = TestFixtures.legacy("key", "minecraft:paper", 1, "default/key");
        ConvertedItem first = TestFixtures.converted(entry, "nexobedrock:key",
                "nexobedrock.key", false);
        ConvertedItem second = TestFixtures.converted(entry, "nexobedrock:key2",
                "nexobedrock.key2", false);

        JsonObject root = Json.parseObject(new MappingWriter().write(List.of(first, second),
                TestFixtures.config()));
        JsonArray definitions = root.getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper");
        assertEquals(1, definitions.size());
        assertEquals("nexobedrock:key", definitions.get(0).getAsJsonObject()
                .get("bedrock_identifier").getAsString());
    }

    @Test
    void mapsUnknownCategoryToItems() {
        JavaItemEntry entry = new JavaItemEntry("key", "minecraft:paper",
                MappingMode.LEGACY, 5, null, "default/key", "Key",
                java.util.Set.of(), "test");
        ConvertedItem item = TestFixtures.converted(entry, "nexobedrock:key",
                "nexobedrock.key", false);
        JsonObject root = Json.parseObject(new MappingWriter().write(List.of(item),
                TestFixtures.config()));
        String category = root.getAsJsonObject("items")
                .getAsJsonArray("minecraft:paper").get(0).getAsJsonObject()
                .getAsJsonObject("bedrock_options").get("creative_category").getAsString();
        assertEquals("items", category);
    }

    @Test
    void rewritesDisplayNameToPrivateUseGlyphs() {
        JavaItemEntry entry = new JavaItemEntry("gradient", "minecraft:paper",
                MappingMode.LEGACY, 7, null, "default/gradient",
                "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70G"
                        + "\u00a7x\u00a70\u00a70\u00a7f\u00a7f\u00a7f\u00a7fP",
                java.util.Set.of(), "test");
        ConvertedItem item = TestFixtures.converted(entry, "nexobedrock:gradient",
                "nexobedrock.gradient", false);
        JsonObject root = Json.parseObject(new MappingWriter().write(List.of(item),
                TestFixtures.config()));
        String name = root.getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(0).getAsJsonObject().get("display_name").getAsString();
        assertEquals(2, name.codePointCount(0, name.length()));
        assertTrue(name.codePointAt(0) >= 0xE200 && name.codePointAt(0) <= 0xF8FF);
        assertTrue(name.codePointAt(1) >= 0xE200 && name.codePointAt(1) <= 0xF8FF);
    }
}
