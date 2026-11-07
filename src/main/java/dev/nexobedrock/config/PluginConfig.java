package dev.nexobedrock.config;

import dev.nexobedrock.util.Hashing;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

public record PluginConfig(
        boolean enabled,
        boolean autoConvert,
        String packPath,
        String assetsPath,
        boolean preferAssetsOnEvent,
        String geyserPacksFolder,
        String geyserMappingsFolder,
        String packName,
        boolean restartGeyser,
        String reloadCommand,
        boolean createFolders,
        boolean backup,
        int backupKeep,
        boolean include2d,
        boolean include3d,
        int maxPackSizeMb,
        boolean debug,
        MappingMode mappingMode,
        String namespace,
        String creativeCategory,
        HandheldMode handheldMode,
        int[] minEngineVersion,
        int maxAtlasTiles,
        int maxAtlasTileSize,
        boolean keepOutputCopy,
        String attachableMaterial,
        boolean bedrockTextFormatting,
        BedrockTextMode bedrockTextMode
) {
    public static PluginConfig from(FileConfiguration config) {
        List<Integer> minEngine = config.getIntegerList("conversion.min-engine-version");
        int[] minEngineArray;
        if (minEngine.size() >= 3) {
            minEngineArray = new int[]{minEngine.get(0), minEngine.get(1), minEngine.get(2)};
        } else {
            minEngineArray = new int[]{1, 20, 0};
        }

        return new PluginConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("auto-convert-on-pack-generate", true),
                config.getString("source.pack-path", "plugins/Nexo/pack/pack.zip"),
                config.getString("source.assets-path", "plugins/Nexo/pack/assets"),
                config.getBoolean("source.prefer-assets-on-event", true),
                config.getString("geyser.packs-folder", "plugins/Geyser-Spigot/packs"),
                config.getString("geyser.mappings-folder", "plugins/Geyser-Spigot/custom_mappings"),
                config.getString("geyser.pack-name", "nexobedrock"),
                config.getBoolean("geyser.restart-geyser", false),
                config.getString("geyser.reload-command", "geyser reload"),
                config.getBoolean("geyser.create-folders", true),
                config.getBoolean("geyser.backup", true),
                Math.max(1, config.getInt("geyser.backup-keep", 10)),
                config.getBoolean("conversion.include-2d", true),
                config.getBoolean("conversion.include-3d", true),
                Math.max(1, config.getInt("conversion.max-pack-size-mb", 100)),
                config.getBoolean("conversion.debug", false),
                MappingMode.parse(config.getString("conversion.mapping-mode", "legacy")),
                sanitizeNamespace(config.getString("conversion.namespace", "nexobedrock")),
                config.getString("conversion.creative-category", "items"),
                HandheldMode.parse(config.getString("conversion.display-handheld", "auto")),
                minEngineArray,
                Math.max(1, config.getInt("conversion.max-atlas-tiles", 64)),
                Math.max(16, config.getInt("conversion.max-atlas-tile-size", 256)),
                config.getBoolean("conversion.keep-output-copy", true),
                sanitizeMaterial(config.getString("geyser.attachable-material",
                        "entity_alphatest_one_sided")),
                config.getBoolean("bedrock-text.enabled", true),
                BedrockTextMode.parse(config.getString("bedrock-text.output", "legacy"))
        );
    }

    private static String sanitizeMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return "entity_alphatest_one_sided";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.matches("[a-z0-9_]+") ? value : "entity_alphatest_one_sided";
    }

    private static String sanitizeNamespace(String raw) {
        String value = raw == null ? "nexobedrock" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_.-]", "_");
        if (value.isBlank() || "minecraft".equals(value)) {
            value = "nexobedrock";
        }
        return value;
    }

    public String fingerprint() {
        String canonical = String.join("|",
                "v1",
                "2d=" + include2d,
                "3d=" + include3d,
                "mode=" + mappingMode,
                "ns=" + namespace,
                "creative=" + creativeCategory,
                "handheld=" + handheldMode,
                "minEngine=" + minEngineVersion[0] + "." + minEngineVersion[1] + "." + minEngineVersion[2],
                "atlasTiles=" + maxAtlasTiles,
                "atlasSize=" + maxAtlasTileSize,
                "attachable=" + attachableMaterial,
                "bedrockText=" + bedrockTextFormatting + ":" + bedrockTextMode
        );
        return Hashing.sha256Hex(canonical);
    }
}
