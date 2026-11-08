package dev.nexobedrock.geyser.extension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A bounded archive with fixed entry names; never extracts arbitrary paths. */
public record SyncBundle(byte[] pack, byte[] mappings, byte[] furniture) {
    private static final Map<String, Integer> LIMITS = Map.of(
            "manifest.json", 16384, "pack.mcpack", 200 * 1024 * 1024,
            "mappings.json", 32 * 1024 * 1024, "furniture.properties", 4 * 1024 * 1024);

    public static SyncBundle read(Path file) throws IOException {
        Map<String, byte[]> data = new HashMap<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Integer limit = LIMITS.get(entry.getName());
                if (limit == null || entry.isDirectory() || data.containsKey(entry.getName()))
                    throw new IOException("Unexpected or duplicate archive entry");
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(limit + 1); }
                if (bytes.length > limit) throw new IOException("Archive entry exceeds limit");
                data.put(entry.getName(), bytes);
            }
        }
        if (!data.keySet().equals(Set.of("manifest.json", "pack.mcpack", "mappings.json", "furniture.properties")))
            throw new IOException("Bundle incomplete");
        try {
            JsonObject manifest = JsonParser.parseString(new String(data.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (manifest.get("format").getAsInt() != 1) throw new IOException("Unsupported sync format");
            verify(manifest, "pack_sha256", data.get("pack.mcpack"));
            verify(manifest, "mappings_sha256", data.get("mappings.json"));
            verify(manifest, "furniture_sha256", data.get("furniture.properties"));
            JsonObject mappings = JsonParser.parseString(new String(data.get("mappings.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (mappings.get("format_version").getAsInt() != 2 || !mappings.get("items").isJsonObject())
                throw new IOException("Invalid Geyser mappings");
        } catch (RuntimeException exception) { throw new IOException("Invalid sync manifest or mappings", exception); }
        FurnitureMappingConfig.read(new java.io.ByteArrayInputStream(data.get("furniture.properties")));
        validatePack(data.get("pack.mcpack"), file.toAbsolutePath().getParent());
        return new SyncBundle(data.get("pack.mcpack"), data.get("mappings.json"), data.get("furniture.properties"));
    }

    private static void validatePack(byte[] pack, Path directory) throws IOException {
        Path temp = java.nio.file.Files.createTempFile(directory, ".sync-pack-check-", ".tmp");
        try {
            java.nio.file.Files.write(temp, pack);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                ZipEntry manifest = zip.getEntry("manifest.json");
                if (manifest == null || manifest.isDirectory()) throw new IOException("Missing pack manifest");
                byte[] bytes;
                try (var input = zip.getInputStream(manifest)) { bytes = input.readNBytes(1024 * 1024 + 1); }
                if (bytes.length > 1024 * 1024) throw new IOException("Pack manifest exceeds limit");
                try {
                    JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    java.util.UUID.fromString(root.getAsJsonObject("header").get("uuid").getAsString());
                } catch (RuntimeException exception) { throw new IOException("Invalid pack manifest", exception); }
            }
        } finally { java.nio.file.Files.deleteIfExists(temp); }
    }

    private static void verify(JsonObject manifest, String name, byte[] bytes) throws IOException {
        try {
            String expected = manifest.get(name).getAsString();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected)) throw new IOException("Bundle hash mismatch");
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
