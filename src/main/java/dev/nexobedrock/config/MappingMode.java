package dev.nexobedrock.config;

public enum MappingMode {
    LEGACY,
    MODERN,
    AUTO;

    public static MappingMode parse(String raw) {
        if (raw == null) {
            return LEGACY;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "modern", "item_model", "item-model" -> MODERN;
            case "auto" -> AUTO;
            default -> LEGACY;
        };
    }
}
