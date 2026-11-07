package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationWriterTest {
    @Test
    void mirrorsTranslationsLikeJavaToBedrock() {
        JsonObject display = Json.parseObject("""
                {
                  "thirdperson_righthand": {
                    "rotation": [10, 20, 30],
                    "translation": [1, 2, 3],
                    "scale": [0.5, 0.5, 0.5]
                  },
                  "thirdperson_lefthand": {
                    "rotation": [10, 20, 30],
                    "translation": [1, 2, 3],
                    "scale": [0.5, 0.5, 0.5]
                  },
                  "firstperson_righthand": {
                    "rotation": [10, 20, 30],
                    "translation": [1, 2, 3],
                    "scale": [0.5, 0.5, 0.5]
                  },
                  "firstperson_lefthand": {
                    "rotation": [10, 20, 30],
                    "translation": [1, 2, 3],
                    "scale": [0.5, 0.5, 0.5]
                  },
                  "head": {
                    "rotation": [10, 20, 30],
                    "translation": [2, 4, 6],
                    "scale": [1, 1, 1]
                  }
                }
                """);

        JsonObject animations = new AnimationWriter().write("animation.test", display)
                .getAsJsonObject("animations");

        JsonObject rightThird = bones(animations, "animation.test.thirdperson_main_hand");
        assertArray(rightThird.getAsJsonObject("root_x").getAsJsonArray("position"),
                -1f, 2f, 3f);
        assertArray(rightThird.getAsJsonObject("root_x").getAsJsonArray("rotation"),
                -10f, 0f, 0f);
        assertArray(rightThird.getAsJsonObject("root_y").getAsJsonArray("rotation"),
                0f, -20f, 0f);
        assertArray(rightThird.getAsJsonObject("root_z").getAsJsonArray("rotation"),
                0f, 0f, 30f);

        JsonObject leftThird = bones(animations, "animation.test.thirdperson_off_hand");
        assertArray(leftThird.getAsJsonObject("root_x").getAsJsonArray("position"),
                1f, 2f, 3f);

        JsonObject rightFirst = bones(animations, "animation.test.firstperson_main_hand");
        assertArray(rightFirst.getAsJsonObject("root_x").getAsJsonArray("position"),
                -1f, 2f, -3f);

        JsonObject leftFirst = bones(animations, "animation.test.firstperson_off_hand");
        assertArray(leftFirst.getAsJsonObject("root_x").getAsJsonArray("position"),
                1f, 2f, -3f);

        JsonObject head = bones(animations, "animation.test.head");
        assertArray(head.getAsJsonObject("root_x").getAsJsonArray("position"),
                -1.25f, 2.5f, 3.75f);
        assertArray(head.getAsJsonObject("root_x").getAsJsonArray("scale"),
                0.625f, 0.625f, 0.625f);
        assertArray(head.getAsJsonObject("root").getAsJsonArray("position"),
                0f, 19.9f, 0f);
    }

    @Test
    void usesFallbackFirstPersonRotationWhenSlotHasNoRotation() {
        JsonObject display = Json.parseObject("""
                {"firstperson_righthand": {"translation": [1, 2, 3]}}
                """);
        JsonObject animations = new AnimationWriter().write("animation.test", display)
                .getAsJsonObject("animations");
        JsonObject bone = bones(animations, "animation.test.firstperson_main_hand")
                .getAsJsonObject("root_x");
        assertNotNull(bone);
        assertArray(bone.getAsJsonArray("rotation"), 0.1f, 0.1f, 0.1f);
    }

    @Test
    void defaultsHeadScaleWhenDisplayIsMissing() {
        JsonObject animations = new AnimationWriter().write("animation.test", null)
                .getAsJsonObject("animations");
        JsonObject head = bones(animations, "animation.test.head");
        assertArray(head.getAsJsonObject("root_x").getAsJsonArray("scale"),
                0.625f, 0.625f, 0.625f);
        assertTrue(animations.has("animation.test.firstperson_main_hand"));
    }

    private JsonObject bones(JsonObject animations, String name) {
        return animations.getAsJsonObject(name).getAsJsonObject("bones");
    }

    private void assertArray(JsonArray array, float... expected) {
        assertEquals(expected.length, array.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], array.get(index).getAsFloat(), 0.001f);
        }
    }
}
