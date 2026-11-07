package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableWriterTest {
    @Test
    void nestsAttachableFieldsInsideRequiredDescriptionObject() {
        JsonObject root = new AttachableWriter().write("nexobedrock:test_item",
                "textures/test/atlas", "geometry.test_item", "animation.test_item", null);

        JsonObject attachable = root.getAsJsonObject("minecraft:attachable");
        assertTrue(attachable.has("description"));
        JsonObject description = attachable.getAsJsonObject("description");
        assertEquals("nexobedrock:test_item", description.get("identifier").getAsString());
        assertEquals("geometry.test_item",
                description.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("textures/test/atlas",
                description.getAsJsonObject("textures").get("default").getAsString());
    }
}
