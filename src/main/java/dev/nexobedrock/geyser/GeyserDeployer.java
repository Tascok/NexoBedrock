package dev.nexobedrock.geyser;

import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.PathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Publica o pack e as custom mappings no Geyser sem deixar arquivos
 * parcialmente escritos. Antes de substituir, guarda uma copia dos arquivos
 * anteriores em plugins/NexoBedrock/backups/&lt;timestamp&gt;/.
 */
public final class GeyserDeployer {
    private final org.bukkit.plugin.Plugin plugin;
    private final Logger logger;

    public GeyserDeployer(org.bukkit.plugin.Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public record DeployResult(
            Path packPath,
            Path mappingsPath,
            Path backupDir,
            List<String> warnings
    ) {
    }

    public DeployResult deploy(PluginConfig config, Path mcpack, byte[] mappings,
                               String timestamp) throws IOException {
        Path packsFolder = PathUtil.resolve(plugin, config.geyserPacksFolder());
        Path mappingsFolder = PathUtil.resolve(plugin, config.geyserMappingsFolder());

        ensureFolder(packsFolder, config.createFolders(), "packs do Geyser");
        ensureFolder(mappingsFolder, config.createFolders(), "custom_mappings do Geyser");

        String packFileName = config.packName() + ".mcpack";
        String mappingsFileName = config.packName() + ".json";
        Path packTarget = packsFolder.resolve(packFileName);
        Path mappingsTarget = mappingsFolder.resolve(mappingsFileName);

        List<String> warnings = new ArrayList<>();
        Path backupDir = null;

        if (config.backup() && (Files.exists(packTarget) || Files.exists(mappingsTarget))) {
            backupDir = createBackup(config, timestamp, packTarget, mappingsTarget);
        }

        Path packTemp = null;
        Path mappingsTemp = null;
        Path packRollback = null;
        Path mappingsRollback = null;

        try {
            // Criados dentro do try para que uma falha no segundo arquivo
            // temporario ainda limpe o primeiro no finally.
            packTemp = Files.createTempFile(packsFolder, ".nexobedrock-pack-", ".tmp");
            mappingsTemp = Files.createTempFile(mappingsFolder,
                    ".nexobedrock-mappings-", ".tmp");

            Files.copy(mcpack, packTemp, StandardCopyOption.REPLACE_EXISTING);
            Files.write(mappingsTemp, mappings);

            if (Files.exists(packTarget)) {
                packRollback = packsFolder.resolve(".nexobedrock-rollback-"
                        + UUID.randomUUID() + ".tmp");
                Files.copy(packTarget, packRollback, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(mappingsTarget)) {
                mappingsRollback = mappingsFolder.resolve(".nexobedrock-rollback-"
                        + UUID.randomUUID() + ".tmp");
                Files.copy(mappingsTarget, mappingsRollback,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            AtomicFiles.move(packTemp, packTarget);
            packTemp = null;

            try {
                AtomicFiles.move(mappingsTemp, mappingsTarget);
                mappingsTemp = null;
            } catch (Throwable throwable) {
                rollback(packTarget, packRollback, mappingsTarget, mappingsRollback);
                throw new IOException(
                        "Falha ao publicar as mappings do Geyser; arquivos anteriores restaurados",
                        throwable);
            }
        } finally {
            deleteQuietly(packTemp);
            deleteQuietly(mappingsTemp);
            deleteQuietly(packRollback);
            deleteQuietly(mappingsRollback);
        }

        if (config.keepOutputCopy()) {
            try {
                Path output = plugin.getDataFolder().toPath().resolve("output");
                Files.createDirectories(output);
                AtomicFiles.copy(mcpack, output.resolve(packFileName));
                AtomicFiles.writeBytes(output.resolve(mappingsFileName), mappings);
            } catch (IOException exception) {
                warnings.add("Nao foi possivel atualizar a copia em output/: "
                        + exception.getMessage());
                logger.warning("NexoBedrock: falha ao atualizar output/: " + exception.getMessage());
            }
        }

        return new DeployResult(packTarget, mappingsTarget, backupDir, List.copyOf(warnings));
    }

    private void ensureFolder(Path folder, boolean create, String description) throws IOException {
        if (Files.isDirectory(folder)) {
            return;
        }
        if (!create) {
            throw new IOException("Diretorio de " + description + " nao existe: " + folder);
        }
        Files.createDirectories(folder);
    }

    private Path createBackup(PluginConfig config, String timestamp, Path packTarget,
                              Path mappingsTarget) throws IOException {
        Path root = plugin.getDataFolder().toPath().resolve("backups");
        Files.createDirectories(root);

        Path backupDir = root.resolve(timestamp);
        int suffix = 2;
        while (Files.exists(backupDir)) {
            backupDir = root.resolve(timestamp + "-" + suffix++);
        }

        if (Files.exists(packTarget)) {
            Path target = backupDir.resolve("packs").resolve(packTarget.getFileName());
            Files.createDirectories(target.getParent());
            Files.copy(packTarget, target, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(mappingsTarget)) {
            Path target = backupDir.resolve("custom_mappings").resolve(mappingsTarget.getFileName());
            Files.createDirectories(target.getParent());
            Files.copy(mappingsTarget, target, StandardCopyOption.REPLACE_EXISTING);
        }

        pruneBackups(root, config.backupKeep());
        return backupDir;
    }

    private void pruneBackups(Path root, int keep) {
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> backups = new ArrayList<>(stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList());
            int remove = backups.size() - Math.max(1, keep);
            for (int index = 0; index < remove; index++) {
                deleteTree(backups.get(index));
            }
        } catch (IOException exception) {
            logger.warning("NexoBedrock: falha ao limpar backups antigos: " + exception.getMessage());
        }
    }

    /**
     * Restaura o pack e as mappings anteriores. Se nao havia arquivo anterior,
     * remove o arquivo recem-publicado para nao deixar um par inconsistente.
     */
    private void rollback(Path packTarget, Path packRollback,
                          Path mappingsTarget, Path mappingsRollback) {
        restore(packTarget, packRollback, "pack");
        restore(mappingsTarget, mappingsRollback, "mappings");
    }

    private void restore(Path target, Path rollback, String description) {
        try {
            if (rollback != null && Files.exists(rollback)) {
                AtomicFiles.move(rollback, target);
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException exception) {
            logger.severe("NexoBedrock: falha ao restaurar " + description
                    + " anterior: " + exception.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Arquivo temporario; sera limpo na proxima execucao.
        }
    }

    public static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
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
