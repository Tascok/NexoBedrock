package dev.nexobedrock.pack;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NexoItemAppearanceIndexTest {
    @TempDir
    Path temporary;

    @Test
    void readsPackTextureFromNestedYamlWithoutItemSpecificRules() throws Exception {
        Path nested = Files.createDirectories(temporary.resolve("catalog/subcategory"));
        Files.writeString(nested.resolve("items.yml"), """
                arbitrary_item:
                  material: PAPER
                  Pack:
                    texture: custom/icons/arbitrary
                other_item:
                  Pack:
                    model: custom/other
                """);

        assertEquals(Map.of("arbitrary_item", "custom/icons/arbitrary"),
                new NexoItemConfigIndex().readTextureReferences(temporary));
    }

    @Test
    void resolvesNexoTextureReferenceByActualPackPathAndRejectsAmbiguousBasename()
            throws Exception {
        byte[] icon = dev.nexobedrock.TestFixtures.png(16, 16, 0xFF00FF00);
        JavaPack pack = new JavaPack("test", "hash", Map.of(), Map.of(), Map.of(),
                Map.of("minecraft:textures/emojis/emote_right", icon,
                        "other:textures/emojis/emote_right", icon,
                        "minecraft:textures/a/duplicate", new byte[]{1},
                        "minecraft:textures/b/duplicate", new byte[]{2}), Set.of(), List.of(), 0);
        PackTextureResolver resolver = new PackTextureResolver();

        assertEquals("minecraft:textures/emojis/emote_right",
                resolver.resolve(pack, "emojis/emote_right"));
        assertNull(resolver.resolve(pack, "duplicate"));
    }
}
