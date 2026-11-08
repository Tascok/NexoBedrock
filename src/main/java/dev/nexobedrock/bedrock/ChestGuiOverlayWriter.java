package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds title-matched classic chest overlays without changing ordinary chests. */
public final class ChestGuiOverlayWriter {
    public record Result(int count, List<String> warnings) { }

    public Result write(JavaPack pack, Map<String, Integer> mappings, TextureStore textures)
            throws IOException {
        return write(pack, mappings, textures, GuiOffsets.defaults());
    }

    public Result write(JavaPack pack, Map<String, Integer> mappings, TextureStore textures,
                        GuiOffsets offsets) throws IOException {
        JsonArray overlays = new JsonArray();
        List<String> tests = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (var font : pack.fonts().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            JsonArray providers = font.getValue().getAsJsonArray("providers");
            if (providers == null) continue;
            for (JsonElement element : providers) {
                if (!element.isJsonObject()) continue;
                JsonObject provider = element.getAsJsonObject();
                if (!provider.has("type") || !"bitmap".equals(provider.get("type").getAsString())
                        || !provider.has("height") || provider.get("height").getAsInt() < 32
                        || !provider.has("file") || !provider.has("chars")
                        || !provider.get("chars").isJsonArray()) continue;
                JsonArray chars = provider.getAsJsonArray("chars");
                if (chars.size() != 1 || !chars.get(0).isJsonPrimitive()
                        || !chars.get(0).getAsJsonPrimitive().isString()) continue;
                String row = chars.get(0).getAsString();
                if (row.codePointCount(0, row.length()) != 1) continue;
                int source = row.codePointAt(0);
                Integer target = mappings.get(font.getKey() + ":" + String.format("%04X", source));
                if (target == null || target < 0xE000 || target > 0xF8FF) continue;
                String key = Names.normalizeKey(provider.get("file").getAsString(), "minecraft");
                if (key == null) continue;
                String path = Names.pathOf(key);
                if (path.startsWith("textures/")) key = Names.namespaceOf(key) + ":" + path.substring(9);
                byte[] png = pack.texture(key);
                if (png == null) continue;
                var image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null || image.getHeight() == 0) continue;
                if (!seen.add(target)) continue;
                int height = Math.min(256, provider.get("height").getAsInt());
                int width = Math.min(512, Math.max(1,
                        (int) Math.round((double) image.getWidth() * height / image.getHeight())));
                String character = new String(Character.toChars(target));
                GuiOffsets.Offset adjustment = offsets.getOrCreate(font.getKey() + ":" + String.format("%04X", source), key);
                String match = "(not ($nexobedrock_title - '" + character + "' = $nexobedrock_title))";
                tests.add("(not (($container_title - '" + character + "') = $container_title))");
                String texture = textures.add(png, "textures/nexobedrock/gui/glyph_"
                        + Integer.toHexString(target));
                JsonObject imageControl = new JsonObject();
                imageControl.addProperty("texture", texture);
                imageControl.addProperty("visible", match);
                JsonArray size = new JsonArray(); size.add(width); size.add(height);
                imageControl.add("size", size);
                JsonArray offset = new JsonArray(); offset.add(adjustment.x()); offset.add((height - 220) / 2.0 - 2 + adjustment.y());
                imageControl.add("offset", offset);
                JsonObject wrapper = new JsonObject();
                wrapper.add("nexobedrock_" + Integer.toHexString(target) + "@nexobedrock_gui_image", imageControl);
                overlays.add(wrapper);
            }
        }
        if (overlays.isEmpty()) return new Result(0, List.of());
        JsonObject root = new JsonObject(); root.addProperty("namespace", "chest");
        JsonObject imageBase = new JsonObject();
        imageBase.addProperty("type", "image");
        imageBase.addProperty("layer", 11);
        imageBase.addProperty("$nexobedrock_title", "$container_title");
        root.add("nexobedrock_gui_image", imageBase);
        JsonObject overlayPanel = new JsonObject();
        overlayPanel.addProperty("type", "panel");
        overlayPanel.add("controls", overlays);
        root.add("nexobedrock_gui_overlays", overlayPanel);
        for (String panel : List.of("small_chest_panel", "large_chest_panel")) {
            JsonObject modification = new JsonObject();
            modification.addProperty("array_name", "controls");
            modification.addProperty("operation", "insert_back");
            JsonObject value = new JsonObject();
            JsonObject instance = new JsonObject();
            if (panel.startsWith("small")) {
                JsonArray offset = new JsonArray(); offset.add(0); offset.add(27);
                instance.add("offset", offset);
            } else {
                JsonArray offset = new JsonArray(); offset.add(0); offset.add(2);
                instance.add("offset", offset);
            }
            value.add("nexobedrock_gui_overlays@chest.nexobedrock_gui_overlays", instance);
            modification.add("value", value);
            JsonArray modifications = new JsonArray(); modifications.add(modification);
            JsonObject panelPatch = new JsonObject(); panelPatch.add("modifications", modifications);
            root.add(panel, panelPatch);
        }
        JsonObject label = new JsonObject();
        label.addProperty("visible", "(not (" + String.join(" or ", tests) + "))");
        root.add("chest_label", label);
        textures.writeJson("ui/chest_screen.json", Json.prettyBytes(root));
        warnings.add("Overlays de GUI para baús clássicos gerados; alinhamento e interface Pocket "
                + "precisam de validação no cliente Bedrock.");
        return new Result(overlays.size(), List.copyOf(warnings));
    }
}
