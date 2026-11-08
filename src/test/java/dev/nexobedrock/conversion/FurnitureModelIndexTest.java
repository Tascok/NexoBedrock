package dev.nexobedrock.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureModelIndexTest {
    @Test
    void modelIndexDependsOnFurnitureIdentityNotCatalogOrder() {
        int first = BedrockPackBuilder.furnitureModelIndex("furniture_a");
        BedrockPackBuilder.furnitureModelIndex("added_before_furniture_a");
        assertEquals(first, BedrockPackBuilder.furnitureModelIndex("furniture_a"));
        assertNotEquals(first, BedrockPackBuilder.furnitureModelIndex("furniture_b"));
        assertTrue(first >= 0);
    }
}
