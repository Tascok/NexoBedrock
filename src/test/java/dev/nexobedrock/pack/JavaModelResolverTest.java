package dev.nexobedrock.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JavaModelResolverTest {
    @Test
    void mergesParentTexturesDisplayAndElements() {
        JsonObject parent = Json.parseObject("""
                {
                  "textures": {"layer0": "test:tex"},
                  "display": {"thirdperson_righthand": {"rotation": [0, 45, 0]}}
                }
                """);
        JsonObject child = Json.parseObject("""
                {
                  "parent": "minecraft:item/handheld",
                  "texture_size": [32, 32],
                  "textures": {"0": "#layer0"},
                  "elements": [{
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#0"}}
                  }]
                }
                """);

        Map<String, JsonObject> models = new LinkedHashMap<>();
        models.put("minecraft:item/handheld", parent);
        models.put("test:child", child);
        JavaPack pack = new JavaPack("test", "hash", models, Map.of(), Map.of(),
                List.of(), 0);

        ResolvedModel resolved = new JavaModelResolver().resolve(pack, "test:child");
        assertNotNull(resolved);
        assertEquals("test:tex", resolved.texture("0"));
        assertEquals("test:tex", resolved.primaryTexture());
        assertEquals(32, resolved.textureWidth());
        assertEquals(32, resolved.textureHeight());
        assertEquals(1, resolved.elements().size());
        assertNotNull(resolved.display().get("thirdperson_righthand"));
    }

    @Test
    void resolvesTextureVariableChains() {
        JsonObject model = Json.parseObject("""
                {
                  "textures": {"0": "#layer0", "layer0": "test:tex"}
                }
                """);
        JavaPack pack = new JavaPack("test", "hash",
                Map.of("test:model", model), Map.of(), Map.of(), List.of(), 0);
        ResolvedModel resolved = new JavaModelResolver().resolve(pack, "test:model");
        assertNotNull(resolved);
        assertEquals("test:tex", resolved.texture("0"));
    }

    @Test
    void readsTextureSizeAsSingleNumber() {
        JsonObject model = Json.parseObject("""
                {"texture_size": 64}
                """);
        JavaPack pack = new JavaPack("test", "hash",
                Map.of("test:model", model), Map.of(), Map.of(), List.of(), 0);
        ResolvedModel resolved = new JavaModelResolver().resolve(pack, "test:model");
        assertNotNull(resolved);
        assertEquals(64, resolved.textureWidth());
        assertEquals(64, resolved.textureHeight());
    }
}
