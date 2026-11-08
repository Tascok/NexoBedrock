package dev.nexobedrock.listener;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FurnitureProxyUuidTest {
    @Test
    void encodesTheCatalogIndexInNexoMarkerAndRetainsInstanceUniqueness() {
        UUID proxyId = UUID.randomUUID();
        UUID encoded = FurnitureProxyUuid.encode(23, proxyId);

        assertEquals(0x4E45584F00000017L, encoded.getMostSignificantBits());
        assertEquals(proxyId.getLeastSignificantBits(), encoded.getLeastSignificantBits());
        assertNotEquals(proxyId, encoded);
    }
}
