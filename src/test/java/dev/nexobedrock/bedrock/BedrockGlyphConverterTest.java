package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGlyphConverterTest {
    @Test
    void convertsBitmapProviderToSafeBedrockGlyphPageAndKeepsSourceMapping() throws Exception {
        JsonObject font = Json.parseObject("""
                {"providers":[{"type":"bitmap","file":"minecraft:gui/icon.png",
                  "height":32,"ascent":8,"chars":["\\uA410"]}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JavaPack pack = new JavaPack("test", "hash", Map.of(), Map.of(),
                Map.of("minecraft:gui/icon", font),
                Map.of("minecraft:gui/icon", dev.nexobedrock.TestFixtures.png(32, 32,
                        0xFF12AB34)), java.util.Set.of(), List.of(), 0);

        BedrockGlyphConverter.Result result = new BedrockGlyphConverter().convert(pack);

        assertEquals(1, result.pages().size());
        assertNotNull(result.pages().get("font/glyph_E2.png"));
        assertEquals(0xE200, result.codepoints().get("minecraft:gui/icon:A410"));
        var image = ImageIO.read(new ByteArrayInputStream(result.pages().get("font/glyph_E2.png")));
        assertEquals(512, image.getWidth());
        assertEquals(image.getWidth(), image.getHeight());
        assertEquals(0xFF12AB34, image.getRGB(0, 0));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("ascent")));
    }

    @Test
    void allocatesDifferentBedrockPagesWhenGlyphSizesDiffer() throws Exception {
        JsonObject font = Json.parseObject("""
                {"providers":[
                  {"type":"bitmap","file":"minecraft:small.png","chars":["A"]},
                  {"type":"bitmap","file":"minecraft:large.png","chars":["B"]}
                ]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JavaPack pack = new JavaPack("test", "hash", Map.of(), Map.of(), Map.of("x:default", font),
                Map.of("minecraft:small", dev.nexobedrock.TestFixtures.png(8, 8, 0xFFFFFFFF),
                        "minecraft:large", dev.nexobedrock.TestFixtures.png(32, 32, 0xFFFFFFFF)),
                java.util.Set.of(), List.of(), 0);

        var result = new BedrockGlyphConverter().convert(pack);

        assertEquals(2, result.pages().size());
        assertEquals(0xE200, result.codepoints().get("x:default:0041"));
        assertEquals(0xE300, result.codepoints().get("x:default:0042"));
        for (byte[] bytes : result.pages().values()) {
            var page = ImageIO.read(new ByteArrayInputStream(bytes));
            assertEquals(page.getWidth(), page.getHeight());
        }
    }
}
