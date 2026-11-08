package dev.nexobedrock.geyser.extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** Applies staged content only before Geyser loads its registries. */
public final class SyncInstaller {
    private final Path journal;
    private final List<Path> targets;

    public SyncInstaller(Path geyserDirectory, Path extensionDirectory) {
        journal = extensionDirectory.resolve("sync-transaction");
        targets = List.of(geyserDirectory.resolve("packs/nexobedrock.mcpack"),
                geyserDirectory.resolve("custom_mappings/nexobedrock.json"),
                extensionDirectory.resolve("furniture-mappings.properties"));
    }

    public void recover() throws IOException {
        if (!Files.exists(journal.resolve("prepared"))) return;
        for (int i = 0; i < targets.size(); i++) {
            Path target = targets.get(i);
            Path backup = journal.resolve("old-" + i);
            Path absent = journal.resolve("absent-" + i);
            if (Files.isRegularFile(backup)) replace(target, Files.readAllBytes(backup));
            else if (Files.exists(absent)) Files.deleteIfExists(target);
            else throw new IOException("Incomplete sync recovery journal");
        }
        Files.delete(journal.resolve("prepared"));
        archiveJournal("recovered");
    }

    public void install(SyncBundle bundle) throws IOException {
        recover();
        if (Files.exists(journal)) archiveJournal("incomplete");
        Files.createDirectories(journal);
        for (int i = 0; i < targets.size(); i++) {
            Path target = targets.get(i);
            if (Files.exists(target)) Files.copy(target, journal.resolve("old-" + i));
            else Files.createFile(journal.resolve("absent-" + i));
        }
        Files.createFile(journal.resolve("prepared"));
        try {
            replace(targets.get(0), bundle.pack());
            replace(targets.get(1), bundle.mappings());
            replace(targets.get(2), bundle.furniture());
        } catch (IOException exception) {
            try { recover(); } catch (IOException rollback) { exception.addSuppressed(rollback); }
            throw exception;
        }
        Files.delete(journal.resolve("prepared"));
        archiveJournal("backup");
    }

    private void archiveJournal(String label) throws IOException {
        if (!Files.exists(journal)) return;
        Path archive = journal.resolveSibling("sync-" + label + "-" + UUID.randomUUID());
        Files.move(journal, archive);
    }

    private static void replace(Path target, byte[] contents) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".nexobedrock-sync-", ".tmp");
        try {
            Files.write(temporary, contents);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
