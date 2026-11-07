package dev.nexobedrock.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.util.Names;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolve heranca de modelos Java (parent), texturas, elements e display.
 * O pack do Nexo normalmente nao usa parent, mas packs de terceiros usam.
 */
public final class JavaModelResolver {
    private static final int MAX_DEPTH = 32;

    public ResolvedModel resolve(JavaPack pack, String rawModelKey) {
        String modelKey = Names.normalizeKey(rawModelKey, "minecraft");
        if (modelKey == null) {
            return null;
        }

        List<JsonObject> chain = new ArrayList<>();
        List<String> chainKeys = new ArrayList<>();
        String current = modelKey;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH) {
            JsonObject model = pack.model(current);
            if (model == null) {
                break;
            }
            if (chainKeys.contains(current)) {
                break;
            }
            chain.add(model);
            chainKeys.add(current);
            JsonElement parent = model.get("parent");
            current = parent != null && parent.isJsonPrimitive()
                    ? Names.normalizeKey(parent.getAsString(), "minecraft")
                    : null;
        }

        if (chain.isEmpty()) {
            return null;
        }

        List<String> warnings = new ArrayList<>();
        Map<String, String> textures = new LinkedHashMap<>();
        JsonObject display = new JsonObject();
        JsonArray elements = null;
        int[] textureSize = null;

        // Do pai para o filho: o filho sobrescreve.
        for (int index = chain.size() - 1; index >= 0; index--) {
            JsonObject model = chain.get(index);
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("textures").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        textures.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            if (model.has("display") && model.get("display").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("display").entrySet()) {
                    display.add(entry.getKey(), entry.getValue());
                }
            }
            if (model.has("texture_size")) {
                textureSize = readTextureSize(model.get("texture_size"), warnings);
            }
        }

        // Elements: o primeiro modelo da cadeia (do filho para o pai) que tiver
        // elements substitui completamente o pai.
        for (JsonObject model : chain) {
            if (model.has("elements") && model.get("elements").isJsonArray()
                    && !model.getAsJsonArray("elements").isEmpty()) {
                elements = model.getAsJsonArray("elements").deepCopy();
                break;
            }
        }

        Map<String, String> resolvedTextures = resolveTextureVariables(textures, warnings);
        int width = textureSize == null ? 16 : textureSize[0];
        int height = textureSize == null ? 16 : textureSize[1];
        if (width <= 0 || height <= 0) {
            width = 16;
            height = 16;
        }

        return new ResolvedModel(modelKey, elements, resolvedTextures, display, width, height, warnings);
    }

    private static Map<String, String> resolveTextureVariables(Map<String, String> raw, List<String> warnings) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String variable : raw.keySet()) {
            String value = resolveVariable(variable, raw, 0, warnings);
            if (value != null) {
                resolved.put(variable, value);
            }
        }
        return resolved;
    }

    private static String resolveVariable(String variable, Map<String, String> raw, int depth, List<String> warnings) {
        if (depth > 16) {
            warnings.add("Referencia circular de textura em #" + variable);
            return null;
        }
        String value = raw.get(variable);
        if (value == null) {
            return null;
        }
        if (value.startsWith("#")) {
            String referenced = value.substring(1);
            if (referenced.equals(variable)) {
                return null;
            }
            return resolveVariable(referenced, raw, depth + 1, warnings);
        }
        return Names.normalizeKey(value, "minecraft");
    }

    private static int[] readTextureSize(JsonElement element, List<String> warnings) {
        try {
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                if (array.size() >= 2) {
                    return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
                }
            } else if (element.isJsonPrimitive()) {
                int value = element.getAsInt();
                return new int[]{value, value};
            }
        } catch (Exception exception) {
            warnings.add("texture_size invalido: " + element);
        }
        return null;
    }
}
