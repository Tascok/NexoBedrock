package dev.nexobedrock.config;

import java.util.Locale;

public enum BedrockTextMode {
    LEGACY,
    PLAIN;

    public static BedrockTextMode parse(String raw) {
        if (raw == null) return LEGACY;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return LEGACY;
        }
    }
}
