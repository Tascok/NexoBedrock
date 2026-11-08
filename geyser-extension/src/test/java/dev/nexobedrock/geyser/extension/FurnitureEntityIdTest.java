package dev.nexobedrock.geyser.extension;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureEntityIdTest {
    @Test
    void encodesStableModelIndexAndPreservesUniqueInstanceId() {
        UUID first = FurnitureEntityId.create(37, 1234L);
        UUID second = FurnitureEntityId.create(37, 5678L);

        assertTrue(FurnitureEntityId.isFurniture(first));
        assertEquals(37, FurnitureEntityId.modelIndex(first));
        assertEquals(37, FurnitureEntityId.modelIndex(second));
        assertFalse(first.equals(second));
        assertFalse(FurnitureEntityId.isFurniture(UUID.randomUUID()));
    }
}
