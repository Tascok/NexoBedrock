package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Persistent, per-source-glyph adjustments, in Bedrock UI pixels. */
public final class GuiOffsets {
    public record Offset(int x, int y) { }
    private final Path path;
    private final byte[] original;
    private final JsonObject root;
    private final JsonObject entries;
    private boolean changed;

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

    public Offset getOrCreate(String id, String texture) throws IOException {
        if (!entries.has(id)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("textura", texture);
            entry.addProperty("x", 0);
            entry.addProperty("y", 0);
            entries.add(id, entry);
            changed = true;
        }
        return read(id);
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
