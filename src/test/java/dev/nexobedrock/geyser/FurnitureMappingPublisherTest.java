package dev.nexobedrock.geyser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureMappingPublisherTest {
    @TempDir
    Path temporary;

    @Test
    void backsUpPreviousMappingAndSkipsUnchangedWrites() throws Exception {
        Path extension = temporary.resolve("geyser/extensions/nexobedrock");
        Path backups = temporary.resolve("NexoBedrock/backups");
        Files.createDirectories(extension);
        Path target = extension.resolve("furniture-mappings.properties");
        byte[] original = "0=nexobedrock:old\n".getBytes(StandardCharsets.UTF_8);
        byte[] generated = "0=nexobedrock:new\n".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);

        var publisher = new FurnitureMappingPublisher();
        var changed = publisher.publish(generated, extension, backups);
        var unchanged = publisher.publish(generated, extension, backups);

        assertTrue(changed.changed());
        assertNotNull(changed.backup());
        assertArrayEquals(original, Files.readAllBytes(changed.backup()));
        assertArrayEquals(generated, Files.readAllBytes(target));
        assertFalse(unchanged.changed());
        assertTrue(unchanged.backup() == null);
    }
}
