package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.ModelEngineBlueprintReader;

import java.util.ArrayList;
import java.util.List;

/** Emits static Bedrock geometry from a ModelEngine/Blockbench blueprint rig. */
public final class ModelEngineGeometryWriter {
    public record Result(JsonObject geometry, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    public Result write(String geometryId, ModelEngineBlueprintReader.Blueprint blueprint) {
        List<String> warnings = new ArrayList<>(blueprint.warnings());
        if (blueprint.textureBindings().size() > 1) {
            warnings.add("Multiple ModelEngine textures are not atlased by this static geometry writer.");
        }
        JsonArray bones = new JsonArray();
        for (ModelEngineBlueprintReader.Bone sourceBone : blueprint.bones()) {
            JsonObject bone = new JsonObject();
            bone.addProperty("name", sourceBone.name());
            if (sourceBone.parent() != null) bone.addProperty("parent", sourceBone.parent());
            float[] pivot = sourceBone.pivot();
            bone.add("pivot", vector(8f - pivot[0], pivot[1], pivot[2] - 8f));
            float[] rotation = sourceBone.rotation();
            if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
                bone.add("rotation", vector(-rotation[0], -rotation[1], rotation[2]));
            }
            JsonArray cubes = new JsonArray();
            if (sourceBone.visible()) {
                for (int elementIndex : sourceBone.elementIndices()) {
                    JsonObject element = blueprint.elements().get(elementIndex).getAsJsonObject();
                    JsonObject cube = cube(element, elementIndex, warnings);
                    if (cube != null) cubes.add(cube);
                }
            }
            if (!cubes.isEmpty()) bone.add("cubes", cubes);
            bones.add(bone);
        }

        JsonObject description = new JsonObject();
        description.addProperty("identifier", geometryId);
        description.addProperty("texture_width", blueprint.textureWidth());
        description.addProperty("texture_height", blueprint.textureHeight());
        description.addProperty("visible_bounds_width", 4);
        description.addProperty("visible_bounds_height", 4.5);
        description.add("visible_bounds_offset", vector(0, 0.75f, 0));
        JsonObject entry = new JsonObject();
        entry.add("description", description);
        entry.add("bones", bones);
        JsonArray geometries = new JsonArray();
        geometries.add(entry);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.16.0");
        root.add("minecraft:geometry", geometries);
        return new Result(root, warnings);
    }

    private JsonObject cube(JsonObject element, int index, List<String> warnings) {
        float[] from = vector(element.get("from"));
        float[] to = vector(element.get("to"));
        JsonObject sourceFaces = element.has("faces") && element.get("faces").isJsonObject()
                ? element.getAsJsonObject("faces") : null;
        if (from == null || to == null || sourceFaces == null) {
            warnings.add("Invalid ModelEngine cube omitted at element " + index);
            return null;
        }
        JsonObject faces = new JsonObject();
        for (String direction : TextureAtlasBuilder.FACE_NAMES) {
            JsonElement rawFace = sourceFaces.get(direction);
            if (rawFace == null || !rawFace.isJsonObject()) continue;
            JsonElement rawUv = rawFace.getAsJsonObject().get("uv");
            float[] uv = vector4(rawUv);
            if (uv == null) {
                warnings.add("Invalid UV omitted for element " + index + " face " + direction);
                continue;
            }
            JsonObject face = new JsonObject();
            face.add("uv", vector(uv[0], uv[1]));
            face.add("uv_size", vector(uv[2] - uv[0], uv[3] - uv[1]));
            faces.add(direction, face);
        }
        if (faces.isEmpty()) return null;
        JsonObject cube = new JsonObject();
        float maxX = Math.max(from[0], to[0]);
        float minX = Math.min(from[0], to[0]);
        float minY = Math.min(from[1], to[1]);
        float minZ = Math.min(from[2], to[2]);
        float maxY = Math.max(from[1], to[1]);
        float maxZ = Math.max(from[2], to[2]);
        cube.add("origin", vector(8f - maxX, minY, minZ - 8f));
        cube.add("size", vector(maxX - minX, maxY - minY, maxZ - minZ));
        cube.add("uv", faces);
        if (element.has("rotation") && element.get("rotation").isJsonObject()) {
            JsonObject rotation = element.getAsJsonObject("rotation");
            float angle = number(rotation, "angle", 0);
            String axis = string(rotation, "axis");
            float[] origin = vector(rotation.get("origin"));
            if (angle != 0 && origin != null && axis != null) {
                cube.add("pivot", vector(8f - origin[0], origin[1], origin[2] - 8f));
                cube.add("rotation", switch (axis) {
                    case "x" -> vector(-angle, 0, 0);
                    case "y" -> vector(0, -angle, 0);
                    case "z" -> vector(0, 0, angle);
                    default -> {
                        warnings.add("Unsupported rotated cube axis at element " + index);
                        yield vector(0, 0, 0);
                    }
                });
            }
        }
        return cube;
    }

    private JsonArray vector(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(value);
        return array;
    }

    private float[] vector(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < 3) {
            return null;
        }
        float[] result = new float[3];
        for (int index = 0; index < 3; index++) {
            JsonElement value = element.getAsJsonArray().get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            result[index] = value.getAsFloat();
        }
        return result;
    }

    private float[] vector4(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < 4) {
            return null;
        }
        float[] result = new float[4];
        for (int index = 0; index < 4; index++) {
            JsonElement value = element.getAsJsonArray().get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            result[index] = value.getAsFloat();
        }
        return result;
    }

    private float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsFloat() : fallback;
    }

    private String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
}
