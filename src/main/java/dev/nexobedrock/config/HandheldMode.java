package dev.nexobedrock.config;

public enum HandheldMode {
    AUTO,
    ALWAYS,
    NEVER;

    public static HandheldMode parse(String raw) {
        if (raw == null) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "always", "yes" -> ALWAYS;
            case "false", "never", "no" -> NEVER;
            default -> AUTO;
        };
    }
}
