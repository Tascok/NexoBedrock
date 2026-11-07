package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.ResolvedModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeometryWriterTest {
    @Test
    void keepsZeroThicknessJavaElementsAsThinBedrockCubes() {
        JsonArray elements = new JsonArray();
        elements.add(element(2, 3, 7, 14, 13, 7, null));
        ResolvedModel model = new ResolvedModel("test:plate", elements,
                Map.of("0", "test:tex"), new JsonObject(), 16, 16, List.of());

        Map<FaceKey, UvRect> uvs = new LinkedHashMap<>();
        for (String face : TextureAtlasBuilder.FACE_NAMES) {
            uvs.put(new FaceKey(0, face), new UvRect(0, 0, 4, 4));
        }
        AtlasResult atlas = new AtlasResult(new byte[0], 16, 16, uvs, new byte[0],
                List.of());

        JsonObject geometry = new GeometryWriter().write("geometry.plate", model, atlas);
        JsonArray bones = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones");
        JsonObject cube = bone(bones, "root_z").getAsJsonArray("cubes")
                .get(0).getAsJsonObject();

        assertArray(cube.getAsJsonArray("origin"), -6f, 3f, -1.005f);
        assertArray(cube.getAsJsonArray("size"), 12f, 10f, 0.01f);
    }

    @Test
    void convertsElementsAndGroupsRotatedCubes() {
        JsonArray elements = new JsonArray();
        elements.add(element(0, 0, 0, 4, 4, 4, null));
        elements.add(element(4, 0, 4, 8, 4, 8, rotation(90, "y", 6, 2, 6)));

        ResolvedModel model = new ResolvedModel("test:model", elements,
                Map.of("0", "test:tex"), new JsonObject(), 16, 16, List.of());

        Map<FaceKey, UvRect> uvs = new LinkedHashMap<>();
        for (int element = 0; element < 2; element++) {
            for (String face : TextureAtlasBuilder.FACE_NAMES) {
                uvs.put(new FaceKey(element, face), new UvRect(0, 0, 4, 4));
            }
        }
        AtlasResult atlas = new AtlasResult(new byte[0], 16, 16, uvs, new byte[0],
                List.of());

        JsonObject geometry = new GeometryWriter().write("geometry.test", model, atlas);
        assertEquals("1.16.0", geometry.get("format_version").getAsString());

        JsonObject entry = geometry.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject();
        JsonObject description = entry.getAsJsonObject("description");
        assertEquals("geometry.test", description.get("identifier").getAsString());
        assertEquals(16, description.get("texture_width").getAsInt());

        JsonArray bones = entry.getAsJsonArray("bones");
        JsonObject rootZ = bone(bones, "root_z");
        assertNotNull(rootZ);
        JsonArray rootCubes = rootZ.getAsJsonArray("cubes");
        assertEquals(1, rootCubes.size());
        JsonObject cube = rootCubes.get(0).getAsJsonObject();
        assertArray(cube.getAsJsonArray("origin"), 4f, 0f, -8f);
        assertArray(cube.getAsJsonArray("size"), 4f, 4f, 4f);

        JsonObject rotated = bone(bones, "rot_1");
        assertNotNull(rotated);
        assertEquals("root_z", rotated.get("parent").getAsString());
        assertArray(rotated.getAsJsonArray("pivot"), 2f, 2f, -2f);
        assertArray(rotated.getAsJsonArray("rotation"), 0f, -90f, 0f);
        JsonObject rotatedCube = rotated.getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertArray(rotatedCube.getAsJsonArray("origin"), 0f, 0f, -4f);
    }

    private JsonObject bone(JsonArray bones, String name) {
        for (int index = 0; index < bones.size(); index++) {
            JsonObject bone = bones.get(index).getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) {
                return bone;
            }
        }
        return null;
    }

    private JsonObject element(float fromX, float fromY, float fromZ,
                               float toX, float toY, float toZ, JsonObject rotation) {
        JsonObject element = new JsonObject();
        element.add("from", array(fromX, fromY, fromZ));
        element.add("to", array(toX, toY, toZ));
        if (rotation != null) {
            element.add("rotation", rotation);
        }
        JsonObject faces = new JsonObject();
        for (String face : TextureAtlasBuilder.FACE_NAMES) {
            JsonObject object = new JsonObject();
            object.add("uv", array(0, 0, 4, 4));
            object.addProperty("texture", "#0");
            faces.add(face, object);
        }
        element.add("faces", faces);
        return element;
    }

    private JsonObject rotation(int angle, String axis, float x, float y, float z) {
        JsonObject rotation = new JsonObject();
        rotation.addProperty("angle", angle);
        rotation.addProperty("axis", axis);
        rotation.add("origin", array(x, y, z));
        return rotation;
    }

    private JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private void assertArray(JsonArray array, float... expected) {
        assertEquals(expected.length, array.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], array.get(index).getAsFloat(), 0.001f);
        }
    }
}
