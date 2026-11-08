package dev.nexobedrock.listener;

import java.util.UUID;

/** Must match the UUID marker parsed by the Geyser extension. */
final class FurnitureProxyUuid {
    private static final long MARKER = 0x4E45584F00000000L;

    private FurnitureProxyUuid() {
    }

    static UUID encode(int modelIndex, UUID proxyUuid) {
        return new UUID(MARKER | (modelIndex & 0xFFFFFFFFL),
                proxyUuid.getLeastSignificantBits());
    }
}
