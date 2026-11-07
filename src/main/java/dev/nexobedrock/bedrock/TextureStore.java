package dev.nexobedrock.bedrock;

import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escreve texturas no pack e reaproveita arquivos identicos por hash.
 */
public final class TextureStore {
    private final Path root;
    private final Map<String, String> byHash = new LinkedHashMap<>();
    private final Map<String, String> byPath = new LinkedHashMap<>();

    public TextureStore(Path root) {
        this.root = root;
    }

    public String add(byte[] png, String preferredPath) throws IOException {
        String hash = Hashing.sha256Hex(png);
        String existing = byHash.get(hash);
        if (existing != null) {
            return existing;
        }

        String path = Names.sanitizePath(preferredPath);
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        String owner = byPath.get(path);
        if (owner != null && !owner.equals(hash)) {
            path = path + "_" + Hashing.shortHash(hash);
        }

        Path target = root.resolve(path + ".png");
        Files.createDirectories(target.getParent());
        AtomicFiles.writeBytes(target, png);

        byHash.put(hash, path);
        byPath.put(path, hash);
        return path;
    }

    public void writeJson(String path, byte[] bytes) throws IOException {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        AtomicFiles.writeBytes(target, bytes);
    }

    public void writeBytes(String path, byte[] bytes) throws IOException {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        AtomicFiles.writeBytes(target, bytes);
    }
}
