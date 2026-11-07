package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.ResolvedModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte elements do formato Java para a geometria Bedrock.
 *
 * O eixo X do Java e espelhado no Bedrock. Elementos rotacionados sao
 * agrupados em bones separados porque a rotacao de bone e mais previsivel
 * no cliente Bedrock do que a rotacao por cube.
 */
public final class GeometryWriter {
    /**
     * Bedrock does not render a cube with a zero-sized axis reliably. Java item
     * models commonly use zero-thickness elements as visible plates, so give
     * those elements a sub-pixel depth instead of dropping them.
     */
    private static final float MIN_CUBE_THICKNESS = 0.01f;
    private static final String BINDING =
            "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)";

    public JsonObject write(String geometryId, ResolvedModel model, AtlasResult atlas) {
        JsonArray bones = new JsonArray();
        bones.add(bone("root", null, new float[]{0f, 8f, 0f}, null, null, BINDING));
        bones.add(bone("root_x", "root", new float[]{0f, 8f, 0f}, null, null, null));
        bones.add(bone("root_y", "root_x", new float[]{0f, 8f, 0f}, null, null, null));

        JsonArray unrotated = new JsonArray();
        Map<String, RotatedGroup> rotated = new LinkedHashMap<>();

        JsonArray elements = model.elements() == null ? new JsonArray() : model.elements();
        for (int index = 0; index < elements.size(); index++) {
            JsonElement element = elements.get(index);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cube = cube(element.getAsJsonObject(), index, atlas);
            if (cube == null) {
                continue;
            }

            JsonObject rotation = object(element.getAsJsonObject(), "rotation");
            int angle = rotation == null ? 0 : intValue(rotation, "angle", 0);
            String axis = rotation == null ? null : string(rotation, "axis");
            if (rotation == null || angle == 0 || axis == null) {
                unrotated.add(cube);
                continue;
            }

            float[] origin = floats(rotation, "origin");
            if (origin == null || origin.length < 3) {
                origin = new float[]{8f, 8f, 8f};
            }
            String key = axis + "|" + angle + "|" + round(origin[0]) + "|"
                    + round(origin[1]) + "|" + round(origin[2]);
            float[] pivot = new float[]{8f - origin[0], origin[1], origin[2] - 8f};
            float[] boneRotation = switch (axis) {
                case "x" -> new float[]{-angle, 0f, 0f};
                case "y" -> new float[]{0f, -angle, 0f};
                default -> new float[]{0f, 0f, angle};
            };
            RotatedGroup group = rotated.computeIfAbsent(key,
                    ignored -> new RotatedGroup(pivot, boneRotation, new JsonArray()));
            group.cubes().add(cube);
        }

        bones.add(bone("root_z", "root_y", new float[]{0f, 8f, 0f}, null,
                unrotated.isEmpty() ? null : unrotated, null));

        int rotationIndex = 1;
        for (RotatedGroup group : rotated.values()) {
            bones.add(bone("rot_" + rotationIndex++, "root_z", group.pivot(),
                    group.rotation(), group.cubes(), null));
        }

        JsonObject description = new JsonObject();
        description.addProperty("identifier", geometryId);
        description.addProperty("texture_width", atlas.width());
        description.addProperty("texture_height", atlas.height());
        description.addProperty("visible_bounds_width", 4);
        description.addProperty("visible_bounds_height", 4.5);
        description.add("visible_bounds_offset", array(0f, 0.75f, 0f));

        JsonObject entry = new JsonObject();
        entry.add("description", description);
        entry.add("bones", bones);

        JsonArray geometryArray = new JsonArray();
        geometryArray.add(entry);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.16.0");
        root.add("minecraft:geometry", geometryArray);
        return root;
    }

    private JsonObject cube(JsonObject element, int elementIndex, AtlasResult atlas) {
        float[] from = floats(element, "from");
        float[] to = floats(element, "to");
        if (from == null || to == null || from.length < 3 || to.length < 3) {
            return null;
        }

        float[] bounds = paddedBounds(from, to);
        float minX = bounds[0];
        float minY = bounds[1];
        float minZ = bounds[2];
        float maxX = bounds[3];
        float maxY = bounds[4];
        float maxZ = bounds[5];
        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        JsonObject faces = object(element, "faces");
        if (faces == null) {
            return null;
        }

        JsonObject uv = new JsonObject();
        for (String faceName : TextureAtlasBuilder.FACE_NAMES) {
            UvRect rect = atlas.uvs().get(new FaceKey(elementIndex, faceName));
            if (rect == null) {
                continue;
            }
            JsonObject face = new JsonObject();
            face.add("uv", array(rect.u(), rect.v()));
            face.add("uv_size", array(rect.width(), rect.height()));
            uv.add(faceName, face);
        }
        if (uv.isEmpty()) {
            return null;
        }

        JsonObject cube = new JsonObject();
        cube.add("origin", array(8f - maxX, minY, minZ - 8f));
        cube.add("size", array(sizeX, sizeY, sizeZ));
        cube.add("uv", uv);
        return cube;
    }

    private float[] paddedBounds(float[] from, float[] to) {
        float[] bounds = new float[6];
        for (int axis = 0; axis < 3; axis++) {
            float min = Math.min(from[axis], to[axis]);
            float max = Math.max(from[axis], to[axis]);
            if (max - min < MIN_CUBE_THICKNESS) {
                float center = (min + max) / 2f;
                min = center - MIN_CUBE_THICKNESS / 2f;
                max = center + MIN_CUBE_THICKNESS / 2f;
            }
            bounds[axis] = min;
            bounds[axis + 3] = max;
        }
        return bounds;
    }

    private JsonObject bone(String name, String parent, float[] pivot, float[] rotation,
                            JsonArray cubes, String binding) {
        JsonObject bone = new JsonObject();
        bone.addProperty("name", name);
        if (parent != null) {
            bone.addProperty("parent", parent);
        }
        if (binding != null) {
            bone.addProperty("binding", binding);
        }
        if (pivot != null) {
            bone.add("pivot", vector(pivot));
        }
        if (rotation != null) {
            bone.add("rotation", vector(rotation));
        }
        if (cubes != null && !cubes.isEmpty()) {
            bone.add("cubes", cubes);
        }
        return bone;
    }

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(round(value));
        }
        return array;
    }

    private static JsonArray vector(float[] values) {
        return array(values[0], values[1], values[2]);
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : fallback;
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

    private static float round(float value) {
        return Math.round(value * 10000f) / 10000f;
    }

    private record RotatedGroup(float[] pivot, float[] rotation, JsonArray cubes) {
    }
}
