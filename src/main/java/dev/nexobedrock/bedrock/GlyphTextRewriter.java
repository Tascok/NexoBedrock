package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.nexobedrock.util.Names;

import java.util.Map;

/** Rewrites only literal text payloads in Adventure-style component JSON. */
public final class GlyphTextRewriter {
    public JsonElement rewrite(JsonElement component, Map<Integer, Integer> codepoints) {
        if (component == null || component.isJsonNull() || codepoints.isEmpty()) {
            return component;
        }
        if (component.isJsonArray()) {
            JsonArray array = component.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                array.set(i, rewrite(array.get(i), codepoints));
            }
        } else if (component.isJsonObject()) {
            JsonObject object = component.getAsJsonObject();
            JsonElement text = object.get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                object.add("text", new JsonPrimitive(rewriteString(text.getAsString(), codepoints)));
            }
            for (String child : new String[]{"extra", "with"}) {
                JsonElement value = object.get(child);
                if (value != null) object.add(child, rewrite(value, codepoints));
            }
        }
        return component;
    }

    /** Rewrites glyphs using the font selected on each Adventure component. */
    public JsonElement rewriteByFont(JsonElement component, Map<String, Integer> codepoints) {
        return rewriteByFont(component, codepoints, "minecraft:default");
    }

    private JsonElement rewriteByFont(JsonElement component, Map<String, Integer> codepoints,
                                      String inheritedFont) {
        if (component == null || component.isJsonNull() || codepoints.isEmpty()) return component;
        if (component.isJsonArray()) {
            JsonArray array = component.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                array.set(i, rewriteByFont(array.get(i), codepoints, inheritedFont));
            }
        } else if (component.isJsonObject()) {
            JsonObject object = component.getAsJsonObject();
            String font = object.has("font") && object.get("font").isJsonPrimitive()
                    ? Names.normalizeKey(object.get("font").getAsString(), "minecraft")
                    : inheritedFont;
            JsonElement text = object.get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                object.add("text", new JsonPrimitive(rewriteStringByFont(
                        text.getAsString(), font, codepoints)));
            }
            for (String child : new String[]{"extra", "with"}) {
                JsonElement value = object.get(child);
                if (value != null) object.add(child,
                        rewriteByFont(value, codepoints, font));
            }
        }
        return component;
    }

    private String rewriteString(String source, Map<Integer, Integer> codepoints) {
        StringBuilder output = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            int codepoint = source.codePointAt(offset);
            output.appendCodePoint(codepoints.getOrDefault(codepoint, codepoint));
            offset += Character.charCount(codepoint);
        }
        return output.toString();
    }

    private String rewriteStringByFont(String source, String font,
                                       Map<String, Integer> codepoints) {
        if (font == null) return source;
        StringBuilder output = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            int codepoint = source.codePointAt(offset);
            String key = font + ":" + String.format(java.util.Locale.ROOT, "%04X", codepoint);
            output.appendCodePoint(codepoints.getOrDefault(key, codepoint));
            offset += Character.charCount(codepoint);
        }
        return output.toString();
    }
}
