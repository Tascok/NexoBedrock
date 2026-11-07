package dev.nexobedrock;

import dev.nexobedrock.config.BedrockTextMode;
import dev.nexobedrock.util.BedrockTextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockTextFormatterTest {
    @Test
    void preservesRgbGradientStopsForGlyphAtlas() {
        Component gradient = Component.text("Warm", TextColor.color(0xff0000))
                .append(Component.text(" cool", TextColor.color(0x2345ff)));

        String output = BedrockTextFormatter.format(gradient, true, BedrockTextMode.LEGACY);

        assertTrue(output.toLowerCase().startsWith("\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70"), output);
        assertTrue(output.toLowerCase().contains("\u00a7x\u00a72\u00a73\u00a74\u00a75\u00a7f\u00a7f"), output);
        assertFalse(output.contains("#"), output);
    }

    @Test
    void plainModeStripsFormatting() {
        Component component = Component.text("Gradient", TextColor.color(0x39c5bb));

        assertEquals("Gradient", BedrockTextFormatter.format(component, true,
                BedrockTextMode.PLAIN));
        assertEquals("Gradient", BedrockTextFormatter.format(component, false,
                BedrockTextMode.LEGACY));
    }

    @Test
    void legacyColorsRemainSupportedAndNullIsSafe() {
        Component component = Component.text("Gold", TextColor.color(0xffaa00));

        assertTrue(BedrockTextFormatter.format(component, true,
                BedrockTextMode.LEGACY).contains("Gold"));
        assertEquals("", BedrockTextFormatter.format(null, true, BedrockTextMode.LEGACY));
    }
}
