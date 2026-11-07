package dev.nexobedrock.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Json {
    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    private static final Gson COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static JsonObject readObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static JsonObject parseObject(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static JsonObject parseObject(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static byte[] prettyBytes(JsonElement element) {
        return PRETTY.toJson(element).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] compactBytes(JsonElement element) {
        return COMPACT.toJson(element).getBytes(StandardCharsets.UTF_8);
    }

    public static String pretty(JsonElement element) {
        return PRETTY.toJson(element);
    }

    public static Gson prettyGson() {
        return PRETTY;
    }
}
