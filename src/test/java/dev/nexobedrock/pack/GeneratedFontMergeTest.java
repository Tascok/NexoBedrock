package dev.nexobedrock.pack;

import dev.nexobedrock.bedrock.BedrockGlyphConverter;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedFontMergeTest {
    @Test
    void mergesGeneratedFontResourcesWithoutReplacingSourceModels() throws Exception {
        var model = Json.parseObject("{\"parent\":\"item/generated\"}");
        var font = Json.parseObject("{\"providers\":[{\"type\":\"bitmap\","
                + "\"file\":\"minecraft:generated.png\",\"chars\":[\"ꐒ\"]}]}");
        JavaPack source = new JavaPack("assets", "a", Map.of("minecraft:item", model),
                Map.of(), Map.of(), Map.of(), Set.of(), List.of(), 1);
        JavaPack generated = new JavaPack("zip", "b", Map.of(), Map.of(),
                Map.of("minecraft:default", font), Map.of("minecraft:generated",
                dev.nexobedrock.TestFixtures.png(8, 8, 0xffffffff)), Set.of(), List.of(), 1);
        JavaPack merged = source.withGeneratedFonts(generated);
        assertEquals(model, merged.model("minecraft:item"));
        assertNotEquals(source.sourceHash(), merged.sourceHash());
        assertTrue(new BedrockGlyphConverter().convert(merged).codepoints()
                .containsKey("minecraft:default:A412"));
    }
}
