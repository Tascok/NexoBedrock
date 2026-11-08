package dev.nexobedrock.geyser.extension;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FurnitureMappingConfigTest {
    @Test
    void parsesIndicesToNamespacedCustomEntities() throws Exception {
        var mappings = FurnitureMappingConfig.read(input(
                "# generated\n0=nexobedrock:chair\n1=nexobedrock:lamp\n"));
        assertEquals("nexobedrock:chair", mappings.get(0));
        assertEquals("nexobedrock:lamp", mappings.get(1));
    }

    @Test
    void rejectsDuplicateIndicesAndVanillaEntityIds() {
        assertThrows(Exception.class, () -> FurnitureMappingConfig.read(
                input("0=nexobedrock:chair\n0=nexobedrock:lamp\n")));
        assertThrows(Exception.class, () -> FurnitureMappingConfig.read(
                input("0=minecraft:armor_stand\n")));
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
