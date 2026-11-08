package dev.nexobedrock.bedrock;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlyphTextRewriterTest {
    @Test
    void rewritesGlyphTextRecursivelyWithoutChangingOtherComponentFields() {
        var component = JsonParser.parseString("""
                {"text":"Menu \\uA410","font":"nexo:default",
                 "extra":[{"text":" \\uA450"}]}
                """);

        var result = new GlyphTextRewriter().rewrite(component,
                Map.of(0xA410, 0xE200, 0xA450, 0xF100));

        assertEquals("Menu \uE200", result.getAsJsonObject().get("text").getAsString());
        assertEquals("nexo:default", result.getAsJsonObject().get("font").getAsString());
        assertEquals(" \uF100", result.getAsJsonObject().getAsJsonArray("extra")
                .get(0).getAsJsonObject().get("text").getAsString());
    }
}
