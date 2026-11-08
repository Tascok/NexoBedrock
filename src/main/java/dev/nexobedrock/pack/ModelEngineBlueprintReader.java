package dev.nexobedrock.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads the Blockbench blueprint and rig metadata used by ModelEngine R4 blueprints. */
public final class ModelEngineBlueprintReader {
    public record Bone(String uuid, String name, String parent, float[] pivot, float[] rotation,
                       List<Integer> elementIndices, boolean visible) {
        public Bone {
            pivot = pivot.clone();
            rotation = rotation.clone();
            elementIndices = List.copyOf(elementIndices);
        }

        @Override
        public float[] pivot() {
            return pivot.clone();
        }

        @Override
        public float[] rotation() {
            return rotation.clone();
        }
    }

    public record Blueprint(String name, int textureWidth, int textureHeight,
                            JsonArray elements, List<Bone> bones,
                            Map<Integer, String> textureBindings,
                            Map<String, String> boneNamesByUuid, JsonArray animationData,
                            List<String> animations, List<String> warnings) {
        public Blueprint {
            elements = elements.deepCopy();
            bones = List.copyOf(bones);
            textureBindings = Map.copyOf(textureBindings);
            boneNamesByUuid = Map.copyOf(boneNamesByUuid);
            animationData = animationData.deepCopy();
            animations = List.copyOf(animations);
            warnings = List.copyOf(warnings);
        }

        @Override
        public JsonArray elements() {
            return elements.deepCopy();
        }

        @Override
        public JsonArray animationData() {
            return animationData.deepCopy();
        }
    }

    public Blueprint read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return parse(JsonParser.parseReader(new java.io.InputStreamReader(input,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (RuntimeException exception) {
            throw new IOException("Invalid ModelEngine/Blockbench blueprint: " + file, exception);
        }
    }

    public Blueprint parse(JsonObject root) throws IOException {
        List<String> warnings = new ArrayList<>();
        String name = string(root, "name");
        if (name == null || name.isBlank()) name = "unnamed_model";

        JsonObject resolution = object(root, "resolution");
        int textureWidth = positiveInt(resolution, "width", 16);
        int textureHeight = positiveInt(resolution, "height", 16);

        JsonArray elements = array(root, "elements");
        if (elements == null) elements = new JsonArray();
        Map<String, Integer> elementIndices = new HashMap<>();
        for (int index = 0; index < elements.size(); index++) {
            JsonElement element = elements.get(index);
            if (!element.isJsonObject()) {
                warnings.add("Non-object element ignored at index " + index);
                continue;
            }
            JsonObject cube = element.getAsJsonObject();
            if (!"cube".equals(string(cube, "type"))) {
                warnings.add("Unsupported ModelEngine element type at index " + index
                        + ": " + string(cube, "type"));
                continue;
            }
            String uuid = string(cube, "uuid");
            if (uuid != null) elementIndices.put(uuid, index);
        }

        JsonArray textureArray = array(root, "textures");
        Map<Integer, String> textureBindings = new LinkedHashMap<>();
        if (textureArray != null) {
            for (int index = 0; index < textureArray.size(); index++) {
                JsonElement texture = textureArray.get(index);
                if (!texture.isJsonObject()) continue;
                JsonObject value = texture.getAsJsonObject();
                String textureName = string(value, "name");
                String path = string(value, "path");
                if (textureName != null || path != null) {
                    textureBindings.put(index, path != null ? path : textureName);
                }
                String source = string(value, "source");
                if (source == null || !source.startsWith("data:image/")) {
                    warnings.add("Texture " + index + " is external; resolve it relative to the blueprint folder.");
                }
            }
        }

        List<Bone> bones = new ArrayList<>();
        Map<String, String> boneNamesByUuid = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        JsonArray outliner = array(root, "outliner");
        if (outliner != null) {
            for (JsonElement node : outliner) {
                if (node.isJsonObject()) {
                    visit(node.getAsJsonObject(), null, elementIndices, bones, names,
                            boneNamesByUuid, warnings);
                }
            }
        }

        List<String> animations = new ArrayList<>();
        JsonArray clips = array(root, "animations");
        if (clips == null) clips = new JsonArray();
        if (clips != null) {
            for (JsonElement clip : clips) {
                if (!clip.isJsonObject()) continue;
                JsonObject animation = clip.getAsJsonObject();
                String animationName = string(animation, "name");
                if (animationName != null && !animationName.isBlank()) animations.add(animationName);
                if (array(animation, "animators") == null) {
                    // Blockbench stores animators as an object keyed by bone UUID.
                    JsonElement animatorData = animation.get("animators");
                    if (animatorData != null && !animatorData.isJsonObject()) {
                        warnings.add("Animation " + animationName + " has unsupported animator data.");
                    }
                }
            }
        }
        if (!animations.isEmpty()) {
            warnings.add("Blueprint has " + animations.size()
                    + " animation clip(s); basic position/rotation/scale keyframes can be exported.");
        }
        return new Blueprint(name, textureWidth, textureHeight, elements, bones,
                textureBindings, boneNamesByUuid, clips, animations, warnings);
    }

    private void visit(JsonObject node, String parent, Map<String, Integer> elementIndices,
                       List<Bone> output, Set<String> names,
                       Map<String, String> boneNamesByUuid, List<String> warnings) {
        String uuid = string(node, "uuid");
        String name = safeName(string(node, "name"), uuid, names);
        float[] pivot = vector(node.get("origin"), new float[]{0f, 0f, 0f});
        float[] rotation = vector(node.get("rotation"), new float[]{0f, 0f, 0f});
        List<Integer> cubes = new ArrayList<>();
        JsonArray children = array(node, "children");
        if (children != null) {
            for (JsonElement child : children) {
                if (child.isJsonPrimitive()) {
                    Integer element = elementIndices.get(child.getAsString());
                    if (element != null) cubes.add(element);
                    else warnings.add("Outliner references missing cube " + child.getAsString());
                }
            }
        }
        boolean visible = !node.has("visibility") || !node.get("visibility").isJsonPrimitive()
                || node.get("visibility").getAsBoolean();
        output.add(new Bone(uuid, name, parent, pivot, rotation, cubes, visible));
        if (uuid != null) boneNamesByUuid.put(uuid, name);
        if (children != null) {
            for (JsonElement child : children) {
                if (child.isJsonObject()) visit(child.getAsJsonObject(), name, elementIndices,
                        output, names, boneNamesByUuid, warnings);
            }
        }
    }

    private String safeName(String raw, String fallback, Set<String> names) {
        String candidate = raw == null ? "bone" : raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        if (candidate.isBlank()) candidate = "bone";
        if (!names.add(candidate)) {
            String suffix = fallback == null ? Integer.toHexString(names.size())
                    : fallback.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
            if (suffix.isBlank()) suffix = Integer.toHexString(names.size());
            candidate += "_" + suffix.substring(0, Math.min(8, suffix.length()));
            names.add(candidate);
        }
        return candidate;
    }

    private float[] vector(JsonElement element, float[] fallback) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < 3) {
            return fallback;
        }
        float[] values = new float[3];
        for (int index = 0; index < 3; index++) {
            JsonElement value = element.getAsJsonArray().get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
            values[index] = value.getAsFloat();
        }
        return values;
    }

    private int positiveInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) return fallback;
        try {
            int value = object.get(key).getAsInt();
            return value > 0 && value <= 4096 ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    private JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
