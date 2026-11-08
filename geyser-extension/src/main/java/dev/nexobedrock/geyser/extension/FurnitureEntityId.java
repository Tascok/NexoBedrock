package dev.nexobedrock.geyser.extension;

import java.util.UUID;

/** UUID namespace used by Paper's synthetic entity packets to identify furniture models. */
public final class FurnitureEntityId {
    private static final long MARKER = 0x4E45584F00000000L; // "NEXO"
    private static final long MARKER_MASK = 0xFFFFFFFF00000000L;
    private static final long INSTANCE_MASK = 0xFFFFFFFFL;

    private FurnitureEntityId() {
    }

    public static UUID create(int modelIndex, long instanceId) {
        if (modelIndex < 0) throw new IllegalArgumentException("modelIndex must be non-negative");
        long most = MARKER | (modelIndex & INSTANCE_MASK);
        return new UUID(most, instanceId);
    }

    public static boolean isFurniture(UUID uuid) {
        return uuid != null && (uuid.getMostSignificantBits() & MARKER_MASK) == MARKER;
    }

    public static int modelIndex(UUID uuid) {
        if (!isFurniture(uuid)) throw new IllegalArgumentException("UUID is not Nexo furniture");
        return (int) (uuid.getMostSignificantBits() & INSTANCE_MASK);
    }
}
