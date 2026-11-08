package dev.nexobedrock.conversion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nexobedrock.TestFixtures;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.ModelEngineAssetReader;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEnginePackIntegrationTest {
    @TempDir
    Path temporary;

    @Test
    @EnabledIfEnvironmentVariable(named = "NEXOBEDROCK_MODELENGINE_ROOT", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "NEXOBEDROCK_MODELENGINE_PACK", matches = ".+")
    void convertsRealModelEngineR4PackAndBlueprint() throws Exception {
        var loaded = new ModelEngineAssetReader().readResult(
                Path.of(System.getenv("NEXOBEDROCK_MODELENGINE_ROOT")),
                Path.of(System.getenv("NEXOBEDROCK_MODELENGINE_PACK")));
        assertFalse(loaded.assets().isEmpty(), "no modelengine blueprint resolved a texture");
        JavaPack javaPack = new JavaPack("test", "hash", Map.of(), Map.of(), Map.of(),
                Map.of(), Set.of(), List.of(), 0);
        var result = new BedrockPackBuilder().build(javaPack, List.of(), TestFixtures.config(),
                "hash", "test", temporary.resolve("real-modelengine"), Map.of(),
                loaded.assets(), loaded.warnings());
        try (ZipFile generated = new ZipFile(result.mcpackPath().toFile())) {
            JsonObject catalog = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry("nexobedrock/modelengine-catalog.json")).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            int modelCount = catalog.getAsJsonObject("models").size();
            assertTrue(modelCount > 0);
            JsonObject model = catalog.getAsJsonObject("models").getAsJsonObject("nm_black_widow");
            assertTrue(generated.getEntry(model.get("entity_file").getAsString()) != null);
            JsonObject geometry = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry(model.get("geometry_file").getAsString())).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            var bones = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones");
            int cubes = 0;
            for (var bone : bones) {
                if (bone.getAsJsonObject().has("cubes")) {
                    cubes += bone.getAsJsonObject().getAsJsonArray("cubes").size();
                }
            }
            JsonObject animationFile = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry(model.get("animation_file").getAsString())).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            int clips = animationFile.getAsJsonObject("animations").size();
            assertTrue(cubes > 0);
            assertEquals(3, clips);
            System.out.println("MODELENGINE pack models=" + modelCount
                    + " bones=" + bones.size() + " cubes=" + cubes + " clips=" + clips
                    + " warnings=" + result.warnings().size()
                    + " pack_bytes=" + result.sizeBytes());
        }
    }

    @Test
    void readsBlueprintAndPackTextureThenWritesBedrockEntityGeometryAndAnimations()
            throws Exception {
        Path blueprints = temporary.resolve("blueprints");
        Files.createDirectories(blueprints);
        Files.writeString(blueprints.resolve("test.bbmodel"), blueprintJson(),
                StandardCharsets.UTF_8);
        Path resourcePack = temporary.resolve("modelengine-pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(resourcePack))) {
            output.putNextEntry(new ZipEntry(
                    "assets/modelengine/textures/entity/test_model_texture.png"));
            output.write(TestFixtures.png(16, 16, 0xFFFFFFFF));
            output.closeEntry();
        }

        var assets = new ModelEngineAssetReader().readResult(blueprints, resourcePack);
        assertEquals(1, assets.assets().size());
        assertTrue(assets.warnings().isEmpty());

        JavaPack javaPack = new JavaPack("test", "hash", Map.of(), Map.of(), Map.of(),
                Map.of(), Set.of(), List.of(), 0);
        Path work = temporary.resolve("output");
        var build = new BedrockPackBuilder().build(javaPack, List.of(), TestFixtures.config(),
                "hash", "test", work, Map.of(), assets.assets(), assets.warnings());

        try (ZipFile generated = new ZipFile(build.mcpackPath().toFile())) {
            assertTrue(generated.getEntry("entity/modelengine_test_model.entity.json") != null);
            assertTrue(generated.getEntry("models/entity/modelengine_test_model.geo.json") != null);
            assertTrue(generated.getEntry("animations/modelengine_test_model.animation.json") != null);
            assertTrue(generated.getEntry(
                    "render_controllers/nexobedrock.render_controllers.json") != null);
            assertTrue(generated.getEntry("textures/nexobedrock/modelengine/test_model.png") != null);
            JsonObject entity = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry("entity/modelengine_test_model.entity.json")).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            String controllerId = entity.getAsJsonObject("minecraft:client_entity")
                    .getAsJsonObject("description").getAsJsonArray("render_controllers")
                    .get(0).getAsString();
            JsonObject controllers = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry("render_controllers/nexobedrock.render_controllers.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("render_controllers");
            assertTrue(controllers.has(controllerId));
            JsonObject catalog = JsonParser.parseString(new String(generated.getInputStream(
                    generated.getEntry("nexobedrock/modelengine-catalog.json")).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(catalog.get("runtime_ready").getAsBoolean());
            assertTrue(catalog.getAsJsonObject("models").has("test_model"));
            JsonObject animations = Json.parseObject(generated.getInputStream(generated.getEntry(
                    "animations/modelengine_test_model.animation.json")).readAllBytes());
            assertEquals(1, animations.getAsJsonObject("animations").size());
        }
    }

    private String blueprintJson() {
        return """
                {
                  "name":"test_model","resolution":{"width":16,"height":16},
                  "textures":[{"name":"test_model_texture.png","path":"C:\\\\models\\\\test_model_texture.png","source":"data:image/png;base64,AA=="}],
                  "elements":[{"type":"cube","uuid":"cube-uuid","from":[0,0,0],"to":[8,8,8],
                    "faces":{"north":{"uv":[0,0,8,8]},"south":{"uv":[8,0,16,8]},
                             "east":{"uv":[0,8,8,16]},"west":{"uv":[8,8,16,16]}}}],
                  "outliner":[{"name":"root","uuid":"root-uuid","origin":[0,0,0],"children":["cube-uuid"]}],
                  "animations":[{"name":"idle","length":1,"loop":"loop","animators":{
                    "root-uuid":{"keyframes":[{"channel":"rotation","time":0,
                      "data_points":[{"x":0,"y":0,"z":0}]}]}
                  }}]
                }
                """;
    }
}
