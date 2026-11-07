package dev.nexobedrock.bedrock;

import com.google.gson.reflect.TypeToken;
import dev.nexobedrock.util.Json;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Encodes the exact source-text to private-use-glyph substitutions shipped in the pack. */
public final class GlyphTextMap {
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private GlyphTextMap() { }

    public static byte[] encode(Map<String, String> map) {
        return Json.prettyGson().toJson(map).getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, String> decode(byte[] json) {
        Map<String, String> map = Json.prettyGson().fromJson(
                new String(json, StandardCharsets.UTF_8), MAP_TYPE);
        if (map == null || map.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
