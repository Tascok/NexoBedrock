package dev.nexobedrock.conversion;

import dev.nexobedrock.TestFixtures;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.report.Report;
import dev.nexobedrock.report.Stats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackCacheTest {
    @TempDir
    Path temp;

    @Test
    void storesAndReadsConversionByKey() throws Exception {
        Path mcpack = temp.resolve("pack.mcpack");
        Files.write(mcpack, new byte[]{1, 2, 3, 4});

        PackCache cache = new PackCache(temp.resolve("cache"),
                Logger.getLogger("test"), 2);
        String key = cache.key("hash", "fingerprint", "1.0.0");
        Report report = new Report("now", "1.0.0", "test", "hash", "test", 12,
                new Stats(1, 1, 1, 0, 0, 0, 0),
                List.of(ItemReport.converted("id", "minecraft:paper", "legacy", 1,
                        null, "test:model", "nexobedrock:id", "flat",
                        "nexobedrock.id", null, null, "textures/icon", List.of())),
                List.of(), List.of(), Map.of("pack", "x"));

        PackCache.Entry stored = cache.store(key, "hash", "fingerprint", mcpack,
                new byte[]{9, 8, 7}, report);
        assertNotNull(stored);
        assertTrue(Files.isRegularFile(stored.mcpack()));
        assertTrue(Files.isRegularFile(stored.mappings()));
        assertEquals(1, stored.report().stats().converted());

        PackCache.Entry found = cache.find(key);
        assertNotNull(found);
        assertEquals(1, found.report().stats().converted());
        assertEquals("nexobedrock:id", found.report().items().get(0).bedrockIdentifier());
        assertEquals(4, found.sizeBytes());
    }

    @Test
    void missesWhenKeyIsUnknown() {
        PackCache cache = new PackCache(temp.resolve("cache"),
                Logger.getLogger("test"), 2);
        assertEquals(null, cache.find("missing"));
    }
}
