package dev.nexobedrock.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeBytes(Path target, byte[] bytes) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream output = Files.newOutputStream(temp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(bytes);
                output.flush();
            }
            move(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void copy(Path source, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            move(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void moveDirectory(Path source, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // Melhor esforco.
                }
            });
        } catch (IOException ignored) {
            // Melhor esforco.
        }
    }
}
