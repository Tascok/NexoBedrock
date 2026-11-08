package dev.nexobedrock.sync;

import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SyncBundleWriterTest {
    @TempDir Path temporary;

    @Test
    void bundlesAllFilesAndTheirHashes() throws Exception {
        Path pack = temporary.resolve("pack.mcpack");
        try (var out = new ZipOutputStream(Files.newOutputStream(pack))) {
            out.putNextEntry(new ZipEntry("nexobedrock/furniture-mappings.properties"));
            out.write("1=example:item\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] mapping = "{\"items\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path bundle = temporary.resolve("sync.zip");
        new SyncBundleWriter().write(pack, mapping, bundle);
        Path second = temporary.resolve("sync-second.zip");
        new SyncBundleWriter().write(pack, mapping, second);
        assertArrayEquals(Files.readAllBytes(bundle), Files.readAllBytes(second));
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            var manifest = Json.parseObject(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes());
            assertEquals(Hashing.sha256Hex(Files.readAllBytes(pack)), manifest.get("pack_sha256").getAsString());
            assertEquals(Hashing.sha256Hex(mapping), manifest.get("mappings_sha256").getAsString());
            assertEquals(Hashing.sha256Hex(zip.getInputStream(zip.getEntry("furniture.properties")).readAllBytes()),
                    manifest.get("furniture_sha256").getAsString());
        }
    }

    @Test
    void failedInputDoesNotOverwriteExistingBundle() throws Exception {
        Path target = temporary.resolve("sync.zip");
        Files.writeString(target, "original");
        assertThrows(java.io.IOException.class, () -> new SyncBundleWriter().write(
                temporary.resolve("missing.mcpack"), new byte[0], target));
        assertEquals("original", Files.readString(target));
    }
}
