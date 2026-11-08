package dev.nexobedrock.bedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphComponentRewriterTest {
    @Test
    void rewritesItemNameAndPreservesColorAndOtherContent() {
        Component source = Component.text("Tag ", NamedTextColor.GOLD)
                .append(Component.text("\uE80D", NamedTextColor.WHITE));
        Component result = new GlyphComponentRewriter().rewrite(source,
                Map.of("minecraft:default:E80D", 0xEB01));
        String json = GsonComponentSerializer.gson().serialize(result);
        assertTrue(json.contains("\uEB01"));
        assertTrue(json.contains("gold"));
        assertTrue(json.contains("white"));
        assertEquals(source, Component.text("Tag ", NamedTextColor.GOLD)
                .append(Component.text("\uE80D", NamedTextColor.WHITE)));
    }

    @Test
    void unchangedComponentIsNotReplaced() {
        Component input = Component.text("normal");
        assertSame(input, new GlyphComponentRewriter().rewrite(input,
                Map.of("minecraft:default:E80D", 0xEB01)));
    }
}
