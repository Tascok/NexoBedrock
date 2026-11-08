package dev.nexobedrock.bedrock;

import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.util.PathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Cached mapping for the locally generated pack; never rewrites source items. */
public final class GlyphMappings {
    private final NexoBedrockPlugin plugin;
    private final GlyphMapLoader loader = new GlyphMapLoader();
    private Map<String, Integer> mappings = Map.of();
    private Path loadedPath;
    private long modified = Long.MIN_VALUE;

    public GlyphMappings(NexoBedrockPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized Map<String, Integer> get() {
        PluginConfig config = plugin.getPluginConfig();
        Path pack = PathUtil.resolve(plugin, config.geyserPacksFolder())
                .resolve(config.packName() + ".mcpack");
        try {
            long current = Files.getLastModifiedTime(pack).toMillis();
            if (!pack.equals(loadedPath) || current != modified) {
                mappings = loader.load(pack);
                loadedPath = pack;
                modified = current;
            }
        } catch (IOException exception) {
            mappings = Map.of();
            loadedPath = null;
            modified = Long.MIN_VALUE;
        }
        return mappings;
    }
}
