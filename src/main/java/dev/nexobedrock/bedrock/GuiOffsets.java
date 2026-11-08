package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.pack.NexoGlyphNames;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Persistent, per-source-glyph adjustments, in Bedrock UI pixels. */
public final class GuiOffsets {
    public record Offset(int x, int y) { }
    private final Path path;
    private final byte[] original;
    private final JsonObject root;
    private final JsonObject entries;
    private boolean changed;
    private Map<String, List<NexoGlyphNames.Glyph>> glyphNames = Map.of();

    private GuiOffsets(Path path, byte[] original, JsonObject root) throws IOException {
        this.path = path;
        this.original = original;
        this.root = root;
        if (!root.has("guis")) root.add("guis", new JsonObject());
        if (!root.get("guis").isJsonObject()) throw new IOException("gui-offsets.json: guis deve ser um objeto");
        entries = root.getAsJsonObject("guis");
    }

    public static GuiOffsets load(Path path) throws IOException {
        byte[] bytes = Files.exists(path) ? Files.readAllBytes(path) : null;
        try {
            JsonObject root = bytes == null ? new JsonObject() : Json.parseObject(bytes);
            if (bytes == null) root.addProperty("instrucoes", "x positivo: direita; y positivo: baixo. Edite e execute /nexobedrock convert.");
            GuiOffsets result = new GuiOffsets(path, bytes, root);
            result.changed = bytes == null;
            result.fingerprint(); // Validate before using cache or writing anything.
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("gui-offsets.json inválido; arquivo preservado", exception);
        }
    }

    public static GuiOffsets defaults() {
        try { return new GuiOffsets(null, null, new JsonObject()); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public Offset getOrCreate(String id, String texture, String character) throws IOException {
        if (!entries.has(id)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("textura", texture);
            entry.addProperty("caractere", character);
            entry.addProperty("x", 0);
            entry.addProperty("y", 0);
            entries.add(id, entry);
            changed = true;
        }
        if (!entries.get(id).isJsonObject()) throw new IOException("Ajuste inválido para GUI " + id);
        if (!entries.getAsJsonObject(id).has("caractere")) {
            entries.getAsJsonObject(id).addProperty("caractere", character);
            changed = true;
        }
        updateName(id);
        return read(id);
    }

    public void setGlyphNames(Map<String, List<NexoGlyphNames.Glyph>> names) {
        glyphNames = Map.copyOf(names);
        for (String id : entries.keySet()) updateName(id);
    }

    private void updateName(String id) {
        List<NexoGlyphNames.Glyph> matches = glyphNames.get(id);
        if (matches == null || matches.isEmpty() || !entries.get(id).isJsonObject()) return;
        JsonObject entry = entries.getAsJsonObject(id);
        JsonArray names = new JsonArray();
        JsonArray textures = new JsonArray();
        matches.stream().map(NexoGlyphNames.Glyph::name).distinct().sorted().forEach(names::add);
        matches.stream().map(NexoGlyphNames.Glyph::texture).distinct().sorted().forEach(textures::add);
        if (!names.equals(entry.get("glyphs_nexo"))) { entry.add("glyphs_nexo", names); changed = true; }
        if (!textures.equals(entry.get("nomes_gui_nexo"))) { entry.add("nomes_gui_nexo", textures); changed = true; }
    }

    private Offset read(String id) throws IOException {
        try {
            JsonObject entry = entries.get(id).getAsJsonObject();
            return new Offset(integer(entry, "x"), integer(entry, "y"));
        } catch (RuntimeException exception) {
            throw new IOException("Ajuste inválido para GUI " + id + "; use inteiros em x e y", exception);
        }
    }

    private static int integer(JsonObject entry, String name) {
        if (!entry.has(name) || !entry.get(name).isJsonPrimitive()
                || !entry.getAsJsonPrimitive(name).isNumber()) throw new IllegalArgumentException(name);
        return new java.math.BigDecimal(entry.get(name).getAsString()).intValueExact();
    }

    public String fingerprint() throws IOException {
        StringBuilder value = new StringBuilder();
        for (String id : entries.keySet().stream().sorted().toList()) {
            Offset offset = read(id);
            if (offset.x() != 0 || offset.y() != 0)
                value.append(id).append('=').append(offset.x()).append(',').append(offset.y()).append('\n');
        }
        return Hashing.sha256Hex(value.toString());
    }

    public void save() throws IOException {
        if (path == null) return;
        byte[] current = Files.exists(path) ? Files.readAllBytes(path) : null;
        if (!Arrays.equals(original, current)) throw new IOException("gui-offsets.json foi alterado durante a conversão; tente novamente");
        if (changed) AtomicFiles.writeBytes(path, Json.prettyBytes(root));
    }
}
