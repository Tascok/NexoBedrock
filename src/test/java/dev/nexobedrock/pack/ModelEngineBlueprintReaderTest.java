package dev.nexobedrock.pack;

import com.google.gson.JsonParser;
import dev.nexobedrock.bedrock.ModelEngineGeometryWriter;
import dev.nexobedrock.bedrock.ModelEngineAnimationWriter;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEngineBlueprintReaderTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "NEXOBEDROCK_MODELENGINE_BLUEPRINT", matches = ".+")
    void readsRealModelEngineBlueprintWhenProvided() throws Exception {
        var blueprint = new ModelEngineBlueprintReader().read(Path.of(
                System.getenv("NEXOBEDROCK_MODELENGINE_BLUEPRINT")));
        var geometryResult = new ModelEngineGeometryWriter().write(
                "geometry.nexobedrock." + blueprint.name(), blueprint);
        var animationResult = new ModelEngineAnimationWriter().write(blueprint.name(), blueprint);
        var geometryBones = geometryResult.geometry().getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject().getAsJsonArray("bones");
        int cubeCount = 0;
        for (var bone : geometryBones) {
            if (bone.getAsJsonObject().has("cubes")) {
                cubeCount += bone.getAsJsonObject().getAsJsonArray("cubes").size();
            }
        }
        System.out.println("MODELENGINE blueprint=" + blueprint.name()
                + " elements=" + blueprint.elements().size()
                + " bones=" + blueprint.bones().size()
                + " textures=" + blueprint.textureBindings().size()
                + " animations=" + blueprint.animations().size()
                + " output_bones=" + geometryBones.size()
                + " output_cubes=" + cubeCount
                + " output_clips=" + animationResult.identifiers().size()
                + " warnings=" + (geometryResult.warnings().size()
                + animationResult.warnings().size()));
        assertTrue(!blueprint.bones().isEmpty());
        assertTrue(!blueprint.animations().isEmpty());
        assertTrue(cubeCount > 0);
        assertEquals(blueprint.animations().size(), animationResult.identifiers().size());
    }

    @Test
    void preservesRigHierarchyCubeReferencesAndAnimationInventory() throws Exception {
        var source = JsonParser.parseString("""
                {
                  "name":"example_rig",
                  "resolution":{"width":32,"height":16},
                  "textures":[{"name":"body","path":"textures/body.png","source":"data:image/png;base64,AA=="}],
                  "elements":[{"type":"cube","uuid":"cube-1"}],
                  "outliner":[{"name":"root","uuid":"bone-root","origin":[0,8,0],"children":[
                    {"name":"Body Main","uuid":"bone-body","origin":[0,4,0],"rotation":[0,15,0],"children":["cube-1"]}
                  ]}],
                  "animations":[{"name":"idle","animators":{"bone-body":{"keyframes":[]}}}]
                }
                """).getAsJsonObject();

        var blueprint = new ModelEngineBlueprintReader().parse(source);

        assertEquals("example_rig", blueprint.name());
        assertEquals(32, blueprint.textureWidth());
        assertEquals(16, blueprint.textureHeight());
        assertEquals(2, blueprint.bones().size());
        assertEquals("root", blueprint.bones().get(0).name());
        assertEquals("root", blueprint.bones().get(1).parent());
        assertEquals("body_main", blueprint.bones().get(1).name());
        assertEquals(15f, blueprint.bones().get(1).rotation()[1]);
        assertEquals(java.util.List.of(0), blueprint.bones().get(1).elementIndices());
        assertEquals("textures/body.png", blueprint.textureBindings().get(0));
        assertEquals(java.util.List.of("idle"), blueprint.animations());
        assertTrue(blueprint.warnings().stream().anyMatch(warning -> warning.contains("basic position/rotation/scale")));
    }

    @Test
    void handlesDuplicateBoneNamesDeterministically() throws Exception {
        var source = JsonParser.parseString("""
                {"name":"duplicate","elements":[],"outliner":[
                  {"name":"leg","uuid":"a","children":[]},
                  {"name":"leg","uuid":"b","children":[]}
                ]}
                """).getAsJsonObject();

        var blueprint = new ModelEngineBlueprintReader().parse(source);

        assertEquals("leg", blueprint.bones().get(0).name());
        assertEquals("leg_b", blueprint.bones().get(1).name());
    }

    @Test
    void writesBedrockGeometryWithModelEngineBoneHierarchyAndCubeUvs() throws Exception {
        var source = JsonParser.parseString("""
                {
                  "name":"writer_test","resolution":{"width":16,"height":16},
                  "elements":[{"type":"cube","uuid":"cube","from":[1,2,3],"to":[5,7,9],
                    "faces":{"north":{"uv":[0,0,4,5]},"up":{"uv":[4,5,0,0]}}}],
                  "outliner":[{"name":"root","uuid":"r","origin":[0,8,0],"children":[
                    {"name":"arm","uuid":"a","origin":[1,4,2],"rotation":[0,20,0],"children":["cube"]}
                  ]}]
                }
                """).getAsJsonObject();
        var blueprint = new ModelEngineBlueprintReader().parse(source);

        var geometry = new ModelEngineGeometryWriter().write("geometry.nexobedrock.test", blueprint)
                .geometry().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

        var bones = geometry.getAsJsonArray("bones");
        assertEquals("arm", bones.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals(1, bones.get(1).getAsJsonObject().getAsJsonArray("cubes").size());
        var cube = bones.get(1).getAsJsonObject().getAsJsonArray("cubes")
                .get(0).getAsJsonObject();
        assertEquals(3f, cube.getAsJsonArray("origin").get(0).getAsFloat());
        assertEquals(4f, cube.getAsJsonArray("size").get(0).getAsFloat());
        assertEquals(2, cube.getAsJsonObject("uv").size());
    }

    @Test
    void convertsBasicBoneKeyframesAndReportsInterpolationApproximation() throws Exception {
        var source = JsonParser.parseString("""
                {
                  "name":"animated","elements":[],
                  "outliner":[{"name":"arm","uuid":"arm-uuid","children":[]}],
                  "animations":[{"name":"wave","length":1,"loop":"loop","animators":{
                    "arm-uuid":{"keyframes":[
                      {"channel":"rotation","time":0,"interpolation":"catmullrom",
                       "data_points":[{"x":0,"y":10,"z":0}]},
                      {"channel":"position","time":1,"data_points":[{"x":0,"y":-2,"z":0}]}
                    ]}
                  }}]
                }
                """).getAsJsonObject();
        var blueprint = new ModelEngineBlueprintReader().parse(source);

        var result = new ModelEngineAnimationWriter().write("animated", blueprint);
        String animationId = result.identifiers().get("wave");
        var animation = result.animations().getAsJsonObject("animations")
                .getAsJsonObject(animationId);
        var bone = animation.getAsJsonObject("bones").getAsJsonObject("arm");

        assertEquals(true, animation.get("loop").getAsBoolean());
        assertEquals(-10f, bone.getAsJsonObject("rotation").getAsJsonArray("0.0")
                .get(1).getAsFloat());
        assertEquals(2f, bone.getAsJsonObject("position").getAsJsonArray("1.0")
                .get(1).getAsFloat());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("approximated as linear")));
    }
}
