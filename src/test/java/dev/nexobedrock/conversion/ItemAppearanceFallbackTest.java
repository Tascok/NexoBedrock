package dev.nexobedrock.conversion;

import dev.nexobedrock.bedrock.TextureStore;
import dev.nexobedrock.config.HandheldMode;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemAppearanceFallbackTest {
    @TempDir
    Path temporary;

    @Test
    void importsConfiguredIconWithoutModelAndDoesNotRejectFurnitureCarrierMaterial()
            throws Exception {
        byte[] icon = dev.nexobedrock.TestFixtures.png(16, 16, 0xFFFF0000);
        JavaPack pack = new JavaPack("test", "hash", Map.of(), Map.of(), Map.of(),
                Map.of("minecraft:textures/gui/custom_icon", icon), Set.of(), List.of(), 0);
        JavaItemEntry entry = new JavaItemEntry("any_furniture", "minecraft:leather_horse_armor",
                MappingMode.LEGACY, 42, null, null,
                "minecraft:textures/gui/custom_icon", "Furniture", Set.of("furniture"), "test");
        PluginConfig config = new PluginConfig(true, false, "", "", true, "", "", "", "",
                "", "test", false, "", true, true, 3, true, true, 100, false,
                MappingMode.LEGACY, "nexobedrock", "items", HandheldMode.AUTO,
                new int[]{1, 20, 0}, 64, 256, true, "entity_alphatest_one_sided", true,
                dev.nexobedrock.config.BedrockTextMode.LEGACY);

        ItemConverter.Outcome outcome = new ItemConverter().convert(pack, entry, config,
                "nexobedrock:any_furniture", new TextureStore(temporary));

        assertNotNull(outcome.item());
        assertEquals("flat", outcome.report().type());
        assertTrue(outcome.item().flat());
    }
}
