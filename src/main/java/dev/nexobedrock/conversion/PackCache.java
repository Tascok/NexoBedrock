package dev.nexobedrock.conversion;

import com.google.gson.JsonObject;
import dev.nexobedrock.report.Report;
import dev.nexobedrock.bedrock.GlyphTextMap;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Time;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Cache de conversoes concluidas. A chave inclui o hash do pack, a
 * configuracao relevante e a versao do plugin; assim mudar qualquer opcao que
 * altere a saida invalida o cache.
 */
public final class PackCache {
    public record Entry(
            String key,
            Path directory,
            Path mcpack,
            Path mappings,
            Path glyphMap,
            Path reportPath,
            Report report,
            long sizeBytes
    ) {
    }

    private final Path root;
    private final Logger logger;
    private final int keep;

    public PackCache(Path root, Logger logger, int keep) {
        this.root = root;
        this.logger = logger;
        this.keep = Math.max(1, keep);
    }

    public String key(String sourceHash, String configFingerprint, String pluginVersion) {
        return Hashing.shortHash(Hashing.sha256Hex(
                sourceHash + "|" + configFingerprint + "|" + pluginVersion));
    }

    public Entry find(String key) {
        Path directory = root.resolve(key);
        Path metaPath = directory.resolve("meta.json");
        if (!Files.isRegularFile(metaPath)) {
            return null;
        }
        try {
            JsonObject meta = Json.readObject(metaPath);
            String packName = string(meta, "pack", "pack.mcpack");
            String mappingsName = string(meta, "mappings", "mappings.json");
            String glyphMapName = string(meta, "glyphMap", "glyph-map.json");
            String reportName = string(meta, "report", "report.json");

            Path pack = directory.resolve(packName).normalize();
            Path mappings = directory.resolve(mappingsName).normalize();
            Path glyphMap = directory.resolve(glyphMapName).normalize();
            Path reportPath = directory.resolve(reportName).normalize();
            if (!pack.startsWith(directory) || !mappings.startsWith(directory)
                    || !glyphMap.startsWith(directory) || !reportPath.startsWith(directory)) {
                return null;
            }
            if (!Files.isRegularFile(pack) || !Files.isRegularFile(mappings)
                    || !Files.isRegularFile(glyphMap)
                    || !Files.isRegularFile(reportPath)) {
                return null;
            }

            Report report;
            try (Reader reader = Files.newBufferedReader(reportPath, StandardCharsets.UTF_8)) {
                report = Json.prettyGson().fromJson(reader, Report.class);
            }
            if (report == null || report.stats() == null) {
                return null;
            }
            long size = Files.size(pack);
            return new Entry(key, directory, pack, mappings, glyphMap, reportPath, report, size);
        } catch (Exception exception) {
            logger.warning("NexoBedrock: cache invalido para " + key + ": "
                    + exception.getMessage());
            return null;
        }
    }

    public Entry store(String key, String sourceHash, String fingerprint, Path mcpack,
                       byte[] mappings, Report report) throws IOException {
        return store(key, sourceHash, fingerprint, mcpack, mappings, GlyphTextMap.encode(Map.of()), report);
    }

    public Entry store(String key, String sourceHash, String fingerprint, Path mcpack,
                       byte[] mappings, byte[] glyphMap, Report report) throws IOException {
        Files.createDirectories(root);
        Path temp = root.resolve("." + key + ".tmp-" + UUID.randomUUID());
        Path target = root.resolve(key);

        try {
            Files.createDirectories(temp);
            AtomicFiles.copy(mcpack, temp.resolve("pack.mcpack"));
            AtomicFiles.writeBytes(temp.resolve("mappings.json"), mappings);
            AtomicFiles.writeBytes(temp.resolve("glyph-map.json"), glyphMap);
            AtomicFiles.writeBytes(temp.resolve("report.json"),
                    Json.prettyBytes(Json.prettyGson().toJsonTree(report)));

            JsonObject meta = new JsonObject();
            meta.addProperty("key", key);
            meta.addProperty("sourceHash", sourceHash);
            meta.addProperty("fingerprint", fingerprint);
            meta.addProperty("createdAt", Time.isoNow());
            meta.addProperty("pack", "pack.mcpack");
            meta.addProperty("mappings", "mappings.json");
            meta.addProperty("glyphMap", "glyph-map.json");
            meta.addProperty("report", "report.json");
            meta.addProperty("sizeBytes", Files.size(temp.resolve("pack.mcpack")));
            AtomicFiles.writeBytes(temp.resolve("meta.json"), Json.prettyBytes(meta));

            if (Files.exists(target)) {
                AtomicFiles.deleteTree(target);
            }
            AtomicFiles.moveDirectory(temp, target);
            prune();
        } finally {
            AtomicFiles.deleteTree(temp);
        }

        Entry entry = find(key);
        if (entry == null) {
            throw new IOException("Cache gravado mas ilegivel: " + key);
        }
        return entry;
    }

    private void prune() {
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> entries = new ArrayList<>(stream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparingLong(this::createdAt)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList());
            int remove = entries.size() - keep;
            for (int index = 0; index < remove; index++) {
                AtomicFiles.deleteTree(entries.get(index));
            }
        } catch (IOException exception) {
            logger.warning("NexoBedrock: falha ao limpar cache antigo: "
                    + exception.getMessage());
        }
    }

    /**
     * Momento de criacao da entrada, usado para remover as mais antigas.
     * Le createdAt do meta.json e cai para o mtime do diretorio.
     */
    private long createdAt(Path directory) {
        Path metaPath = directory.resolve("meta.json");
        if (Files.isRegularFile(metaPath)) {
            try {
                JsonObject meta = Json.readObject(metaPath);
                if (meta.has("createdAt") && meta.get("createdAt").isJsonPrimitive()) {
                    return Instant.parse(meta.get("createdAt").getAsString()).toEpochMilli();
                }
            } catch (Exception ignored) {
                // meta corrompido: usa o mtime abaixo
            }
        }
        try {
            return Files.getLastModifiedTime(directory).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : fallback;
    }
}
