package dev.nexobedrock.bedrock;

import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestGuiOverlayWriterTest {
    @TempDir Path temporary;

    @Test
    void createsOnlyMatchedClassicChestOverlaysForLargeSingleGlyphs() throws Exception {
        var font = Json.parseObject("""
                {"providers":[
                  {"type":"bitmap","file":"example:gui.png","height":135,"chars":["\\uF800"]},
                  {"type":"bitmap","file":"example:icon.png","height":9,"chars":["A"]}
                ]}
                """);
        JavaPack pack = new JavaPack("test", "hash", Map.of(), Map.of(),
                Map.of("minecraft:default", font), Map.of(
                "example:gui", dev.nexobedrock.TestFixtures.png(176, 135, 0xffffffff),
                "example:icon", dev.nexobedrock.TestFixtures.png(8, 8, 0xffffffff)),
                Set.of(), List.of(), 0);
        var result = new ChestGuiOverlayWriter().write(pack,
                Map.of("minecraft:default:F800", 0xF800), new TextureStore(temporary));
        assertEquals(1, result.count());
        var ui = Json.parseObject(Files.readAllBytes(temporary.resolve("ui/chest_screen.json")));
        assertEquals("chest", ui.get("namespace").getAsString());
        for (String panel : List.of("small_chest_panel", "large_chest_panel")) {
            var value = ui.getAsJsonObject(panel).getAsJsonArray("modifications").get(0)
                    .getAsJsonObject().getAsJsonObject("value");
            assertTrue(value.has("nexobedrock_gui_overlays@chest.nexobedrock_gui_overlays"));
            var instance = value.getAsJsonObject("nexobedrock_gui_overlays@chest.nexobedrock_gui_overlays");
            assertEquals(panel.startsWith("small") ? 27 : 2, instance.getAsJsonArray("offset").get(1).getAsInt());
        }
        assertEquals("$container_title", ui.getAsJsonObject("nexobedrock_gui_image")
                .get("$nexobedrock_title").getAsString());
        var overlay = ui.getAsJsonObject("nexobedrock_gui_overlays").getAsJsonArray("controls")
                .get(0).getAsJsonObject().getAsJsonObject("nexobedrock_f800@nexobedrock_gui_image");
        assertTrue(overlay.get("visible").getAsString().contains("$nexobedrock_title"));
        assertTrue(overlay.get("visible").getAsString().contains("\uF800"));
        assertEquals(176, overlay.getAsJsonArray("size").get(0).getAsInt());
        assertEquals(-44.5, overlay.getAsJsonArray("offset").get(1).getAsDouble());
        assertFalse(Files.exists(temporary.resolve("ui/pocket_containers.json")));
    }
}
