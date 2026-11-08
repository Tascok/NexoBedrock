package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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

    private String rewriteString(String source, Map<Integer, Integer> codepoints) {
        StringBuilder output = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            int codepoint = source.codePointAt(offset);
            output.appendCodePoint(codepoints.getOrDefault(codepoint, codepoint));
            offset += Character.charCount(codepoint);
        }
        return output.toString();
    }
}
