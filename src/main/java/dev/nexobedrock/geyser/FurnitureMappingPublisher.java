package dev.nexobedrock.geyser;

import dev.nexobedrock.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/** Safely publishes furniture model mappings to the extension's configured data folder. */
public final class FurnitureMappingPublisher {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    public record Result(Path target, Path backup, boolean changed) {
    }

    public Result publish(byte[] content, Path extensionDataFolder, Path backupFolder)
            throws IOException {
        Files.createDirectories(extensionDataFolder);
        Path target = extensionDataFolder.resolve("furniture-mappings.properties");
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
            return new Result(target, null, false);
        }
        Path backup = null;
        if (Files.isRegularFile(target)) {
            Files.createDirectories(backupFolder);
            String name = "furniture-mappings.properties.bak-" + BACKUP_TIME.format(Instant.now());
            backup = backupFolder.resolve(name);
            int suffix = 2;
            while (Files.exists(backup)) {
                backup = backupFolder.resolve(name + "-" + suffix++);
            }
            Files.copy(target, backup);
        }
        AtomicFiles.writeBytes(target, content);
        return new Result(target, backup, true);
    }
}
