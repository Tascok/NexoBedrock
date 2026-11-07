package dev.nexobedrock.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public final class Hashing {
    private Hashing() {
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    public static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Hash estavel de um diretorio. Apenas os arquivos relevantes para a
     * conversao entram no hash, e a ordem e sempre a mesma.
     */
    public static String sha256HexDirectory(Path directory, java.util.function.Predicate<Path> include) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(include)
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .toList();
        }
        MessageDigest digest = sha256();
        for (Path file : files) {
            String relative = directory.relativize(file).toString().replace('\\', '/');
            digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String shortHash(String value) {
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    public static List<Path> listFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> result = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
            result.sort(Comparator.naturalOrder());
            return result;
        }
    }
}
