package dev.nexobedrock.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads ModelEngine R4 blueprints and their generated resource-pack textures. */
public final class ModelEngineAssetReader {
    private static final int MAX_BLUEPRINTS = 10_000;
    private static final long MAX_BLUEPRINT_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TEXTURE_BYTES = 32L * 1024 * 1024;

    public record Asset(String id, ModelEngineBlueprintReader.Blueprint blueprint,
                        String textureName, byte[] texture, List<String> warnings) {
        public Asset {
            texture = texture.clone();
            warnings = List.copyOf(warnings);
        }

        @Override
        public byte[] texture() {
            return texture.clone();
        }
    }

    public record Result(List<Asset> assets, List<String> warnings) {
        public Result {
            assets = List.copyOf(assets);
            warnings = List.copyOf(warnings);
        }
    }

    private record Texture(String path, byte[] data) {
    }

    private final ModelEngineBlueprintReader blueprintReader = new ModelEngineBlueprintReader();

    public List<Asset> read(Path blueprintsFolder, Path resourcePack) throws IOException {
        return readResult(blueprintsFolder, resourcePack).assets();
    }

    public Result readResult(Path blueprintsFolder, Path resourcePack) throws IOException {
        if (!Files.isDirectory(blueprintsFolder)) return new Result(List.of(), List.of());
        if (!Files.isRegularFile(resourcePack)) {
            return new Result(List.of(), List.of("ModelEngine blueprints found, but generated "
                    + "resource pack is missing; ModelEngine conversion skipped: " + resourcePack));
        }
        Map<String, Texture> textures = readTextures(resourcePack);
        List<Path> files;
        try (var paths = Files.walk(blueprintsFolder)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(".bbmodel"))
                    .sorted().limit(MAX_BLUEPRINTS + 1L).toList();
        }
        if (files.size() > MAX_BLUEPRINTS) {
            throw new IOException("Too many ModelEngine blueprints (limit " + MAX_BLUEPRINTS + ")");
        }

        List<Asset> assets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path file : files) {
            if (Files.size(file) > MAX_BLUEPRINT_BYTES) {
                throw new IOException("ModelEngine blueprint exceeds size limit: " + file.getFileName());
            }
            ModelEngineBlueprintReader.Blueprint blueprint = blueprintReader.read(file);
            Texture texture = findTexture(blueprint, textures);
            if (texture == null) {
                warnings.add("ModelEngine blueprint " + blueprint.name()
                        + " skipped: matching generated texture not found in resource pack.");
                continue;
            }
            List<String> assetWarnings = new ArrayList<>(blueprint.warnings());
            if (blueprint.textureBindings().size() > 1) {
                assetWarnings.add("Multiple textures found; only the first resolved texture is exported.");
            }
            assets.add(new Asset(blueprint.name(), blueprint, texture.path(), texture.data(), assetWarnings));
        }
        return new Result(assets, warnings);
    }

    private Map<String, Texture> readTextures(Path resourcePack) throws IOException {
        Map<String, Texture> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(resourcePack.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int entryCount = 0;
            long total = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (++entryCount > 200_000) throw new IOException("ModelEngine pack has too many entries");
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("assets/modelengine/textures/")
                        || !name.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                long size = entry.getSize();
                if (size < 0 || size > MAX_TEXTURE_BYTES) continue;
                total += size;
                if (total > 512L * 1024 * 1024) {
                    throw new IOException("ModelEngine textures exceed aggregate size limit");
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] data = input.readNBytes((int) MAX_TEXTURE_BYTES + 1);
                    if (data.length > MAX_TEXTURE_BYTES) continue;
                    String filename = name.substring(name.lastIndexOf('/') + 1)
                            .toLowerCase(Locale.ROOT);
                    result.putIfAbsent(filename, new Texture(name, data));
                }
            }
        }
        return result;
    }

    private Texture findTexture(ModelEngineBlueprintReader.Blueprint blueprint,
                                Map<String, Texture> textures) {
        for (String reference : blueprint.textureBindings().values()) {
            if (reference == null) continue;
            String normalized = reference.replace('\\', '/');
            String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                    .toLowerCase(Locale.ROOT);
            Texture texture = textures.get(filename);
            if (texture != null) return texture;
        }
        String model = blueprint.name().toLowerCase(Locale.ROOT);
        return textures.get(model + "_texture.png");
    }
}
