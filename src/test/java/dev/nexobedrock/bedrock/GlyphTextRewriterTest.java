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

    @Test
    void sharedSourceCodepointUsesComponentFont() {
        var component = JsonParser.parseString("""
                {"text":"\\uA410","font":"nexo:default","extra":[
                  {"text":"\\uA410","font":"other:default"},
                  {"text":"\\uA410","font":"minecraft:default"}]}
                """);

        var result = new GlyphTextRewriter().rewriteByFont(component, Map.of(
                "nexo:default:A410", 0xE200,
                "other:default:A410", 0xE201));

        assertEquals("\uE200", result.getAsJsonObject().get("text").getAsString());
        assertEquals("\uE201", result.getAsJsonObject().getAsJsonArray("extra")
                .get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("\uA410", result.getAsJsonObject().getAsJsonArray("extra")
                .get(1).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void rewritesLiteralTextComponentsWithoutChangingTranslationKeys() {
        var input = JsonParser.parseString("""
                {"translate":"message.type.text","with":["\\uA410",{"text":"\\uA410"}]}
                """);
        var result = new GlyphTextRewriter().rewriteByFont(input,
                Map.of("minecraft:default:A410", 0xE200)).getAsJsonObject();
        assertEquals("message.type.text", result.get("translate").getAsString());
        assertEquals("\uE200", result.getAsJsonArray("with").get(0).getAsString());
        assertEquals("\uE200", result.getAsJsonArray("with").get(1)
                .getAsJsonObject().get("text").getAsString());
    }
}
