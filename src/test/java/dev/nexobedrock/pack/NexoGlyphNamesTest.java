package dev.nexobedrock.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NexoGlyphNamesTest {
    @TempDir Path temp;

    @Test
    void findsNamesAcrossFoldersAndFontsWithoutGuessingAmbiguousGlyphs() throws Exception {
        Files.createDirectories(temp.resolve("nested"));
        Files.writeString(temp.resolve("nested/guis.yml"), """
                menu_tags:
                  texture: ranks/tags
                  char: ''
                outro_nome:
                  texture: ranks/tags
                  char: ''
                fonte_diferente:
                  font: exemplo:fonte
                  texture: outro/menu
                  char: ''
                invalido:
                  texture: ranks/sem-caractere
                """);
        var index = new NexoGlyphNames().read(temp);
        assertEquals(2, index.get("minecraft:default:F809").size());
        assertTrue(index.get("minecraft:default:F809").contains(new NexoGlyphNames.Glyph("menu_tags", "ranks/tags")));
        assertEquals("fonte_diferente", index.get("exemplo:fonte:F809").getFirst().name());
    }
}
