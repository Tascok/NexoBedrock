package dev.nexobedrock.util;

import dev.nexobedrock.config.BedrockTextMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Converts Adventure components to Bedrock-friendly display-name strings. */
public final class BedrockTextFormatter {
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer
            .builder().character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private BedrockTextFormatter() { }

    public static String format(Component component, boolean enabled, BedrockTextMode mode) {
        if (component == null) return "";
        if (!enabled || mode == BedrockTextMode.PLAIN) {
            return PlainTextComponentSerializer.plainText().serialize(component);
        }
        // Preserve per-character RGB stops. The glyph atlas builder consumes these
        // codes and bakes the actual colors into Bedrock font textures.
        return HEX.serialize(component);
    }
}
