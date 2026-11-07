package dev.nexobedrock.report;

import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportWriter {
    public void write(Path path, Report report) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        AtomicFiles.writeBytes(path, Json.prettyBytes(Json.prettyGson().toJsonTree(report)));
    }
}
