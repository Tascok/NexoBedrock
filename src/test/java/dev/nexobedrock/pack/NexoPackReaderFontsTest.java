package dev.nexobedrock.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NexoPackReaderFontsTest {
    @TempDir
    Path temporary;

    @Test
    void readsFontJsonAndBitmapTextureFromZip() throws Exception {
        Path zip = temporary.resolve("font-pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            add(output, "assets/nexo/font/default.json",
                    "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"minecraft:gui/test.png\","
                            .concat("\"chars\":[\"A\"]}]}"));
            add(output, "assets/minecraft/textures/gui/test.png",
                    dev.nexobedrock.TestFixtures.png(16, 16, 0xFFFFFFFF));
        }

        JavaPack pack = new NexoPackReader().read(
                new PackSource(PackSourceKind.ZIP, zip), 10);

        assertTrue(pack.fonts().containsKey("nexo:default"));
        assertTrue(pack.textures().containsKey("minecraft:gui/test"));
    }

    private void add(ZipOutputStream output, String path, String value) throws Exception {
        add(output, path, value.getBytes(StandardCharsets.UTF_8));
    }

    private void add(ZipOutputStream output, String path, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(path));
        output.write(bytes);
        output.closeEntry();
    }
}
