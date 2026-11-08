package dev.nexobedrock.sync;

import com.google.gson.JsonObject;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Creates a single immutable generation of the files required by a Geyser instance. */
public final class SyncBundleWriter {
    private static final int MAX_FURNITURE_BYTES = 4 * 1024 * 1024;

    public void write(Path pack, byte[] mappings, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path snapshot = Files.createTempFile(parent, ".nexobedrock-pack-snapshot-", ".tmp");
        try {
            Files.copy(pack, snapshot, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeSnapshot(snapshot, mappings.clone(), target, parent);
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    private void writeSnapshot(Path pack, byte[] mappings, Path target, Path parent) throws IOException {
        byte[] furniture;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry("nexobedrock/furniture-mappings.properties");
            if (entry == null) {
                furniture = new byte[0];
            } else {
                try (var input = zip.getInputStream(entry)) {
                    furniture = input.readNBytes(MAX_FURNITURE_BYTES + 1);
                }
                if (furniture.length > MAX_FURNITURE_BYTES) throw new IOException("Furniture mapping too large");
            }
        }
        Path temp = Files.createTempFile(parent, ".nexobedrock-sync-", ".tmp");
        try {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("format", 1);
            manifest.addProperty("pack_sha256", sha256(pack));
            manifest.addProperty("mappings_sha256", Hashing.sha256Hex(mappings));
            manifest.addProperty("furniture_sha256", Hashing.sha256Hex(furniture));
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                out.putNextEntry(entry("manifest.json"));
                out.write(Json.prettyBytes(manifest));
                out.closeEntry();
                out.putNextEntry(entry("pack.mcpack"));
                Files.copy(pack, out);
                out.closeEntry();
                out.putNextEntry(entry("mappings.json"));
                out.write(mappings);
                out.closeEntry();
                out.putNextEntry(entry("furniture.properties"));
                out.write(furniture);
                out.closeEntry();
            }
            AtomicFiles.move(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static ZipEntry entry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        return entry;
    }

    private String sha256(Path path) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
