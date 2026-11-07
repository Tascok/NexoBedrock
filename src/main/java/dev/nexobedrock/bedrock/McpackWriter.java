package dev.nexobedrock.bedrock;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class McpackWriter {
    public void zip(Path directory, Path output) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .toList();
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream fileOutput = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(fileOutput)) {
            for (Path file : files) {
                String name = directory.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }
}
