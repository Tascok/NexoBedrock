package dev.nexobedrock.bedrock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphMapLoaderTest {
    @TempDir
    Path temporary;

    @Test
    void readsMappingsEmbeddedInTheMcpack() throws Exception {
        Path pack = temporary.resolve("glyphs.mcpack");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(pack))) {
            output.putNextEntry(new ZipEntry("nexobedrock/glyph-map.json"));
            output.write(("{\"format_version\":1,\"glyphs\":{" +
                    "\"nexo:default:A410\":\"E200\",\"minecraft:default:A450\":\"F100\"}}")
                    .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        Map<Integer, Integer> mappings = new GlyphMapLoader().load(pack);

        assertEquals(Map.of(0xA410, 0xE200, 0xA450, 0xF100), mappings);
    }
}
