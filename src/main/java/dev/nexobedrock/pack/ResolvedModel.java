package dev.nexobedrock.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResolvedModel {
    private final String key;
    private final JsonArray elements;
    private final Map<String, String> textures;
    private final JsonObject display;
    private final int textureWidth;
    private final int textureHeight;
    private final List<String> warnings;

    public ResolvedModel(String key, JsonArray elements, Map<String, String> textures,
                         JsonObject display, int textureWidth, int textureHeight,
                         List<String> warnings) {
        this.key = key;
        this.elements = elements;
        this.textures = textures;
        this.display = display;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.warnings = warnings;
    }

    public String key() {
        return key;
    }

    public JsonArray elements() {
        return elements;
    }

    public Map<String, String> textures() {
        return textures;
    }

    public JsonObject display() {
        return display;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public List<String> warnings() {
        return warnings;
    }

    public boolean hasElements() {
        return elements != null && !elements.isEmpty();
    }

    public String texture(String variable) {
        return textures.get(variable);
    }

    public String primaryTexture() {
        String[] preferred = {"layer0", "0", "particle", "layer1"};
        for (String key : preferred) {
            String value = textures.get(key);
            if (value != null && !value.startsWith("#")) {
                return value;
            }
        }
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            if (!"particle".equals(entry.getKey()) && !entry.getValue().startsWith("#")) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Textura mais representativa do modelo. Para modelos 3D escolhe a
     * textura usada pela maior area de faces, evitando a textura "particle"
     * (frequentemente um brilho compartilhado entre varios itens).
     */
    public String iconTexture() {
        if (elements == null || elements.isEmpty()) {
            return primaryTexture();
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        for (JsonElement elementNode : elements) {
            if (!elementNode.isJsonObject()) {
                continue;
            }
            JsonObject element = elementNode.getAsJsonObject();
            float[] from = floats(element, "from");
            float[] to = floats(element, "to");
            if (from == null || to == null || from.length < 3 || to.length < 3) {
                continue;
            }
            double sizeX = Math.abs(to[0] - from[0]);
            double sizeY = Math.abs(to[1] - from[1]);
            double sizeZ = Math.abs(to[2] - from[2]);
            JsonElement facesNode = element.get("faces");
            if (facesNode == null || !facesNode.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> face : facesNode.getAsJsonObject().entrySet()) {
                if (!face.getValue().isJsonObject()) {
                    continue;
                }
                String texture = textureVariable(face.getValue().getAsJsonObject()
                        .get("texture"));
                if (texture == null) {
                    continue;
                }
                double area = switch (face.getKey()) {
                    case "north", "south" -> sizeX * sizeY;
                    case "east", "west" -> sizeZ * sizeY;
                    case "up", "down" -> sizeX * sizeZ;
                    default -> 0;
                };
                if (area > 0) {
                    weights.merge(texture, area, Double::sum);
                }
            }
        }

        String best = null;
        double bestWeight = -1;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() > bestWeight) {
                best = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return best != null ? best : primaryTexture();
    }

    private String textureVariable(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        if (raw == null || raw.isBlank() || "#missing".equals(raw)) {
            return null;
        }
        String value = raw.startsWith("#") ? textures.get(raw.substring(1)) : raw;
        if (value == null || value.startsWith("#")) {
            return null;
        }
        return value;
    }

    private static float[] floats(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        float[] result = new float[array.size()];
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            result[index] = value.getAsFloat();
        }
        return result;
    }
}
