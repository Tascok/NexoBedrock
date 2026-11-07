package dev.nexobedrock.bedrock;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphTextMapTest {
    @Test
    void roundTripsFormattedSourceTextToPrivateUseGlyphs() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("\u00a7x\u00a7f\u00a70\u00a70\u00a70\u00a70A", "\uE200");
        source.put("plain", "\uE201\uE202\uE203\uE204\uE205");

        assertEquals(source, GlyphTextMap.decode(GlyphTextMap.encode(source)));
    }
}
