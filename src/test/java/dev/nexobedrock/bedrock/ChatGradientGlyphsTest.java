package dev.nexobedrock.bedrock;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGradientGlyphsTest {
    @Test
    void buildsCatalogAndRewritesRgbLettersUsingBakedGlyphs() throws Exception {
        var catalog = ChatGradientGlyphs.catalog();
        assertEquals(95 * 48, catalog.size());
        var atlas = new GradientGlyphAtlas().build(catalog);
        assertTrue(atlas.pages().size() > 1);

        Map<String, String> map = new HashMap<>();
        map.put("\u00a7x\u00a7f\u00a7f\u00a7f\u00a7f\u00a7f\u00a7fA", "\uE200");
        map.put("\u00a7x\u00a70\u00a70\u00a7a\u00a7a\u00a7f\u00a7fB", "\uE201");
        String rewritten = ChatGradientGlyphs.rewrite(
                "\u00a7x\u00a7f\u00a7f\u00a7f\u00a7f\u00a7f\u00a7fA "
                        + "\u00a7x\u00a70\u00a70\u00a7a\u00a7a\u00a7f\u00a7fB", map);
        assertEquals("\uE200 \uE201", rewritten);
        assertEquals("\u00a7x\u00a7f\u00a7f\u00a7f\u00a7f\u00a7f\u00a7fA",
                ChatGradientGlyphs.rewrite(
                        "\u00a7x\u00a7f\u00a7f\u00a7f\u00a7f\u00a7f\u00a7fA", Map.of()));
    }
}
