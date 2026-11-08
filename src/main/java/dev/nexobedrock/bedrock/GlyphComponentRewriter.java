package dev.nexobedrock.bedrock;

import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Map;

/** Rewrites text while preserving Adventure styles, fonts and non-text fields. */
public final class GlyphComponentRewriter {
    private final GlyphTextRewriter rewriter = new GlyphTextRewriter();

    public Component rewrite(Component component, Map<String, Integer> mappings) {
        if (component == null || mappings.isEmpty()) return component;
        GsonComponentSerializer serializer = GsonComponentSerializer.gson();
        var json = JsonParser.parseString(serializer.serialize(component));
        var original = json.deepCopy();
        var rewritten = rewriter.rewriteByFont(json, mappings);
        return original.equals(rewritten) ? component : serializer.deserialize(rewritten.toString());
    }
}
