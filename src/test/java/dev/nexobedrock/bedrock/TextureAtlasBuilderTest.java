package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.TestFixtures;
import dev.nexobedrock.pack.JavaModelResolver;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.ResolvedModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureAtlasBuilderTest {
    @Test
    void scalesJavaUvFromSixteenUnitSpaceToTexturePixels() throws Exception {
        JsonObject model = model();
        JavaPack pack = pack(model, TestFixtures.png(32, 32, 0xFFFF0000));
        ResolvedModel resolved = new JavaModelResolver().resolve(pack, "test:model");
        assertNotNull(resolved);

        AtlasResult atlas = new TextureAtlasBuilder().build(pack, resolved,
                TestFixtures.config());

        UvRect north = atlas.uvs().get(new FaceKey(0, "north"));
        assertNotNull(north);
        assertEquals(16f, Math.abs(north.width()), 0.5f,
                "uv 0..8 em uma textura 32px deve ocupar 16px");
        assertEquals(32f, Math.abs(north.height()), 0.5f);

        UvRect east = atlas.uvs().get(new FaceKey(0, "east"));
        assertNotNull(east);
        assertEquals(32f, Math.abs(east.width()), 0.5f);
        assertEquals(16f, Math.abs(east.height()), 0.5f);

        UvRect up = atlas.uvs().get(new FaceKey(0, "up"));
        assertNotNull(up);
        assertTrue(up.width() < 0);
        assertTrue(up.height() < 0);
        assertEquals(16f, Math.abs(up.width()), 0.5f);

        assertFalse(atlas.png().length == 0);
        assertNotNull(atlas.iconPng());
    }

    private JsonObject model() {
        JsonObject root = new JsonObject();
        JsonArray textureSize = new JsonArray();
        textureSize.add(32);
        textureSize.add(32);
        root.add("texture_size", textureSize);

        JsonObject textures = new JsonObject();
        textures.addProperty("0", "test:tex");
        root.add("textures", textures);

        JsonObject element = new JsonObject();
        element.add("from", array(0, 0, 0));
        element.add("to", array(16, 16, 16));
        JsonObject faces = new JsonObject();
        faces.add("north", face(0, 0, 8, 16, 0));
        faces.add("east", face(0, 0, 8, 16, 90));
        faces.add("up", face(0, 0, 8, 16, 0));
        element.add("faces", faces);

        JsonArray elements = new JsonArray();
        elements.add(element);
        root.add("elements", elements);
        return root;
    }

    private JsonObject face(float u1, float v1, float u2, float v2, int rotation) {
        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u1);
        uv.add(v1);
        uv.add(u2);
        uv.add(v2);
        face.add("uv", uv);
        face.addProperty("texture", "#0");
        if (rotation != 0) {
            face.addProperty("rotation", rotation);
        }
        return face;
    }

    private JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private JavaPack pack(JsonObject model, byte[] texture) {
        Map<String, JsonObject> models = new LinkedHashMap<>();
        models.put("test:model", model);
        Map<String, byte[]> textures = new LinkedHashMap<>();
        textures.put("test:tex", texture);
        return new JavaPack("test", "hash", models, Map.of(), textures, List.of(), 0);
    }
}
