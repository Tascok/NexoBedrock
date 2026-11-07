package dev.nexobedrock.util;

import dev.nexobedrock.config.BedrockTextMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Converts Adventure components to Bedrock-friendly display-name strings. */
public final class BedrockTextFormatter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer
            .builder().character('\u00a7').build();

    private BedrockTextFormatter() { }

    public static String format(Component component, boolean enabled, BedrockTextMode mode) {
        if (component == null) return "";
        if (!enabled || mode == BedrockTextMode.PLAIN) {
            return PlainTextComponentSerializer.plainText().serialize(component);
        }
        // The default legacy serializer emits the 16 Bedrock-compatible colors,
        // downsampling RGB gradient steps to the nearest legacy color.
        return LEGACY.serialize(component);
    }
}
