package dev.nexobedrock.pack;

import com.google.gson.JsonObject;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lê modelos, definições de itens, fontes bitmap e texturas do pack Java.
 */
public final class NexoPackReader {
    private static final int MAX_ENTRIES = 200_000;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TEXTURE_BYTES = 32 * 1024 * 1024;

    public JavaPack read(PackSource source, int maxPackSizeMb) throws IOException {
        long maxBytes = Math.max(1, maxPackSizeMb) * 1024L * 1024L;
        return switch (source.kind()) {
            case ASSETS_DIRECTORY -> readDirectory(source.path(), maxBytes);
            case ZIP -> readZip(source.path(), maxBytes);
        };
    }

    private JavaPack readDirectory(Path path, long maxBytes) throws IOException {
        Path assetsRoot = resolveAssetsRoot(path);
        if (assetsRoot == null) {
            throw new IOException("Diretorio de assets invalido: " + path);
        }

        Map<String, JsonObject> models = new LinkedHashMap<>();
        Map<String, JsonObject> itemDefinitions = new LinkedHashMap<>();
        Map<String, JsonObject> fonts = new LinkedHashMap<>();
        Map<String, byte[]> textures = new LinkedHashMap<>();
        Set<String> animatedTextures = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        long[] size = {0};

        List<Path> files = Hashing.listFiles(assetsRoot);
        for (Path file : files) {
            String relative = assetsRoot.relativize(file).toString().replace('\\', '/');
            long fileSize = Files.size(file);
            size[0] += fileSize;
            if (size[0] > maxBytes) {
                throw new IOException("Pack excede o limite de " + (maxBytes / 1024 / 1024) + " MB");
            }
            readAsset(relative, () -> Files.readAllBytes(file), fileSize, models,
                    itemDefinitions, fonts, textures, animatedTextures, warnings);
        }

        String hash = Hashing.sha256HexDirectory(assetsRoot,
                file -> {
                    String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".json") || name.endsWith(".png")
                            || name.endsWith(".mcmeta");
                });

        return new JavaPack(assetsRoot.toString(), hash, models, itemDefinitions, fonts,
                textures, animatedTextures, warnings, size[0]);
    }

    private JavaPack readZip(Path path, long maxBytes) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Arquivo de pack nao encontrado: " + path);
        }
        long fileSize = Files.size(path);
        if (fileSize > maxBytes) {
            throw new IOException("Pack excede o limite de " + (maxBytes / 1024 / 1024) + " MB");
        }

        Map<String, JsonObject> models = new LinkedHashMap<>();
        Map<String, JsonObject> itemDefinitions = new LinkedHashMap<>();
        Map<String, JsonObject> fonts = new LinkedHashMap<>();
        Map<String, byte[]> textures = new LinkedHashMap<>();
        Set<String> animatedTextures = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        long totalUncompressed = 0;
        int entries = 0;

        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("Zip com entradas demais");
                }
                String name = safeEntryName(entry.getName());
                if (name == null || !name.startsWith("assets/")) {
                    continue;
                }
                // Overlays versionados do Nexo nao fazem parte do pack base.
                if (name.startsWith("nexo_")) {
                    continue;
                }
                long declaredSize = entry.getSize();
                if (declaredSize > 0) {
                    totalUncompressed += declaredSize;
                    if (totalUncompressed > maxBytes) {
                        throw new IOException("Pack descompactado excede o limite de " + (maxBytes / 1024 / 1024) + " MB");
                    }
                }
                String relative = name.substring("assets/".length());
                try (InputStream input = zip.getInputStream(entry)) {
                    readAsset(relative, () -> readLimited(input, MAX_TEXTURE_BYTES),
                            declaredSize, models, itemDefinitions, fonts, textures,
                            animatedTextures, warnings);
                }
            }
        }

        String hash = Hashing.sha256Hex(path);
        return new JavaPack(path.toString(), hash, models, itemDefinitions, fonts, textures,
                animatedTextures, warnings, fileSize);
    }

    private void readAsset(String relative,
                           ByteSupplier bytes,
                           long size,
                            Map<String, JsonObject> models,
                            Map<String, JsonObject> itemDefinitions,
                            Map<String, JsonObject> fonts,
                            Map<String, byte[]> textures,
                           Set<String> animatedTextures,
                           List<String> warnings) {
        String lower = relative.toLowerCase(java.util.Locale.ROOT);
        try {
            if (lower.endsWith(".json") && relative.contains("/models/")) {
                String key = assetKey(relative, "/models/");
                if (key == null) {
                    return;
                }
                if (size > MAX_JSON_BYTES) {
                    warnings.add("Modelo ignorado por tamanho: " + relative);
                    return;
                }
                byte[] data = bytes.get();
                models.put(key, Json.parseObject(data));
            } else if (lower.endsWith(".json") && relative.contains("/items/")) {
                String key = assetKey(relative, "/items/");
                if (key == null) {
                    return;
                }
                if (size > MAX_JSON_BYTES) {
                    warnings.add("Item model ignorado por tamanho: " + relative);
                    return;
                }
                byte[] data = bytes.get();
                itemDefinitions.put(key, Json.parseObject(data));
            } else if (lower.endsWith(".json") && relative.contains("/font/")) {
                String key = assetKey(relative, "/font/");
                if (key == null) {
                    return;
                }
                if (size > MAX_JSON_BYTES) {
                    warnings.add("Fonte ignorada por tamanho: " + relative);
                    return;
                }
                byte[] data = bytes.get();
                fonts.put(key, Json.parseObject(data));
            } else if (lower.endsWith(".png") && relative.contains("/textures/")) {
                String key = textureKey(relative);
                if (key == null) {
                    return;
                }
                if (size > MAX_TEXTURE_BYTES) {
                    warnings.add("Textura ignorada por tamanho: " + relative);
                    return;
                }
                textures.put(key, bytes.get());
            } else if (lower.endsWith(".png.mcmeta") && relative.contains("/textures/")) {
                String key = textureKey(relative);
                if (key == null || size > MAX_JSON_BYTES) {
                    return;
                }
                try {
                    JsonObject meta = Json.parseObject(bytes.get());
                    if (meta.has("animation") && meta.get("animation").isJsonObject()) {
                        animatedTextures.add(key);
                    }
                } catch (Exception exception) {
                    warnings.add("Metadado de textura invalido: " + relative);
                }
            }
        } catch (Exception exception) {
            warnings.add("Falha ao ler " + relative + ": " + exception.getMessage());
        }
    }

    private static String assetKey(String relative, String marker) {
        int markerIndex = relative.indexOf(marker);
        if (markerIndex <= 0) {
            return null;
        }
        String namespace = relative.substring(0, markerIndex);
        String path = relative.substring(markerIndex + marker.length());
        return Names.normalizeKey(namespace + ":" + path, "minecraft");
    }

    private static String textureKey(String relative) {
        int markerIndex = relative.indexOf("/textures/");
        if (markerIndex <= 0) {
            return null;
        }
        String namespace = relative.substring(0, markerIndex);
        String path = relative.substring(markerIndex + "/textures/".length());
        if (path.endsWith(".png.mcmeta")) {
            path = path.substring(0, path.length() - ".png.mcmeta".length());
        } else if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        return Names.normalizeKey(namespace + ":" + path, "minecraft");
    }

    private static Path resolveAssetsRoot(Path path) {
        if (!Files.isDirectory(path)) {
            return null;
        }
        if (Files.isDirectory(path.resolve("minecraft"))) {
            return path;
        }
        if (Files.isDirectory(path.resolve("assets"))) {
            return path.resolve("assets");
        }
        if (Files.isDirectory(path.resolve("assets").resolve("minecraft"))) {
            return path.resolve("assets");
        }
        // Procura um diretorio "assets" em um nivel.
        try {
            return Files.walk(path, 3)
                    .filter(Files::isDirectory)
                    .filter(candidate -> candidate.getFileName().toString().equals("assets"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String safeEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.contains("..")) {
            return null;
        }
        return name;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) > 0) {
            total += read;
            if (total > limit) {
                throw new IOException("Arquivo maior que o limite de " + limit + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface ByteSupplier {
        byte[] get() throws IOException;
    }

    public static String decodeUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
