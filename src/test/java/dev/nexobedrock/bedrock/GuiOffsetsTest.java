package dev.nexobedrock.bedrock;

import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import dev.nexobedrock.pack.NexoGlyphNames;

import static org.junit.jupiter.api.Assertions.*;

class GuiOffsetsTest {
    @TempDir Path temporary;

    @Test
    void generatesEntriesPreservesEditsAndChangesFingerprint() throws Exception {
        Path path = temporary.resolve("gui-offsets.json");
        GuiOffsets offsets = GuiOffsets.load(path);
        String before = offsets.fingerprint();
        assertEquals(new GuiOffsets.Offset(0, 0), offsets.getOrCreate("minecraft:default:F800", "example:gui.png", "\uF800"));
        offsets.save();
        var json = Json.readObject(path);
        var entry = json.getAsJsonObject("guis").getAsJsonObject("minecraft:default:F800");
        entry.addProperty("x", -3);
        entry.addProperty("y", 2);
        Files.write(path, Json.prettyBytes(json));
        GuiOffsets edited = GuiOffsets.load(path);
        assertNotEquals(before, edited.fingerprint());
        assertEquals(new GuiOffsets.Offset(-3, 2), edited.getOrCreate("minecraft:default:F800", "example:gui.png", "\uF800"));
        edited.getOrCreate("minecraft:default:F801", "example:other.png", "\uF801");
        edited.save();
        assertEquals(-3, Json.readObject(path).getAsJsonObject("guis")
                .getAsJsonObject("minecraft:default:F800").get("x").getAsInt());
    }

    @Test
    void refusesInvalidOrConcurrentlyEditedConfiguration() throws Exception {
        Path path = temporary.resolve("gui-offsets.json");
        GuiOffsets offsets = GuiOffsets.load(path);
        offsets.getOrCreate("font:F800", "texture", "\uF800");
        Files.writeString(path, "{\"guis\":{\"font:F800\":{\"x\":1.5,\"y\":0}}}");
        assertThrows(java.io.IOException.class, offsets::save);
        assertThrows(java.io.IOException.class, () -> GuiOffsets.load(path));
    }

    @Test
    void addsNexoNamesWithoutReplacingOffsetsAndRefreshesNames() throws Exception {
        Path path = temporary.resolve("gui-offsets.json");
        Files.writeString(path, "{\"guis\":{\"minecraft:default:F809\":{\"x\":-3,\"y\":2}}}");
        GuiOffsets offsets = GuiOffsets.load(path);
        offsets.setGlyphNames(Map.of("minecraft:default:F809", List.of(
                new NexoGlyphNames.Glyph("tags", "ranks/tags"),
                new NexoGlyphNames.Glyph("alias", "ranks/tags"))));
        offsets.save();
        var entry = Json.readObject(path).getAsJsonObject("guis").getAsJsonObject("minecraft:default:F809");
        assertEquals(-3, entry.get("x").getAsInt());
        assertEquals(2, entry.get("y").getAsInt());
        assertEquals("ranks/tags", entry.getAsJsonArray("nomes_gui_nexo").get(0).getAsString());
        assertEquals(2, entry.getAsJsonArray("glyphs_nexo").size());
    }
}
