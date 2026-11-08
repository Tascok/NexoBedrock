package dev.nexobedrock.geyser.extension;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityMappingConfigTest {
    @Test
    void readsNamespacedStaticEntityMappings() throws Exception {
        var mappings = EntityMappingConfig.read(input(
                "minecraft:zombie=nexobedrock:custom_zombie\n"));
        assertEquals("nexobedrock:custom_zombie", mappings.get("minecraft:zombie"));
    }

    @Test
    void rejectsVanillaBedrockDefinitionIds() {
        assertThrows(Exception.class, () -> EntityMappingConfig.read(
                input("minecraft:zombie=minecraft:zombie\n")));
    }

    @Test
    void rejectsMalformedIdentifiers() {
        assertThrows(Exception.class, () -> EntityMappingConfig.read(
                input("zombie=nexobedrock:custom_zombie\n")));
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
