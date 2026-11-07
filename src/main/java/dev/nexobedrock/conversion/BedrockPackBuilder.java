package dev.nexobedrock.conversion;

import com.google.gson.JsonObject;
import dev.nexobedrock.bedrock.IconFactory;
import dev.nexobedrock.bedrock.ItemTextureWriter;
import dev.nexobedrock.bedrock.ManifestWriter;
import dev.nexobedrock.bedrock.McpackWriter;
import dev.nexobedrock.bedrock.BedrockPackValidator;
import dev.nexobedrock.bedrock.GradientGlyphAtlas;
import dev.nexobedrock.bedrock.TextureStore;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.NexoArmorIndex;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta a arvore de arquivos do resource pack Bedrock e gera o .mcpack.
 */
public final class BedrockPackBuilder {
    public record BuildResult(
            Path mcpackPath,
            List<ConvertedItem> items,
            List<ItemReport> reports,
            List<String> warnings,
            int flat,
            int threeD,
            int unsupported,
            int skipped,
            int failed,
            long sizeBytes
    ) {
    }

    public BuildResult build(JavaPack pack, List<JavaItemEntry> entries, PluginConfig config,
                              String packHash, String pluginVersion, Path workDir) throws IOException {
        return build(pack, entries, config, packHash, pluginVersion, workDir, Map.of());
    }

    public BuildResult build(JavaPack pack, List<JavaItemEntry> entries, PluginConfig config,
                             String packHash, String pluginVersion, Path workDir,
                             Map<String, NexoArmorIndex.Armor> armor) throws IOException {
        Path packRoot = workDir.resolve("pack");
        Files.createDirectories(packRoot);

        TextureStore textures = new TextureStore(packRoot);
        ItemConverter converter = new ItemConverter();
        List<ConvertedItem> converted = new ArrayList<>();
        List<ItemReport> reports = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> itemTextures = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();
        byte[] firstIcon = null;

        for (JavaItemEntry entry : entries) {
            String bedrockId = allocateId(config.namespace(), entry, usedIds);
            ItemConverter.Outcome outcome;
            try {
                outcome = converter.convert(pack, entry, config, bedrockId, textures, armor);
            } catch (Throwable throwable) {
                outcome = ItemConverter.Outcome.failed(entry,
                        "erro inesperado: " + throwable.getMessage());
            }

            reports.add(outcome.report());
            if (outcome.item() == null) {
                continue;
            }

            ConvertedItem item = outcome.item();
            converted.add(item);
            itemTextures.put(item.iconKey(), item.iconPath());
            if (firstIcon == null) {
                firstIcon = item.iconPng();
            }

            if (item.geometryPath() == null && item.attachablePath() != null) {
                textures.writeJson(item.attachablePath(), Json.prettyBytes(item.attachable()));
            } else if (!item.flat()) {
                textures.writeJson(item.geometryPath(), Json.prettyBytes(item.geometry()));
                textures.writeJson(item.attachablePath(), Json.prettyBytes(item.attachable()));
                textures.writeJson(item.animationPath(), Json.prettyBytes(item.animations()));
                for (Map.Entry<String, byte[]> extra : item.extraResources().entrySet()) {
                    textures.writeBytes(extra.getKey(), extra.getValue());
                }
            }
        }

        textures.writeJson("manifest.json",
                Json.prettyBytes(new ManifestWriter().write(packHash, config, pluginVersion)));
        textures.writeJson("textures/item_texture.json",
                Json.prettyBytes(new ItemTextureWriter().write(itemTextures, config.packName())));
        textures.writeJson("animations/nexobedrock_disable.json",
                Json.prettyBytes(disableAnimation()));
        textures.writeBytes("pack_icon.png", new IconFactory().packIcon(firstIcon));

        List<String> displayNames = converted.stream().map(item -> item.entry().displayName()).toList();
        GradientGlyphAtlas.Result glyphAtlas = new GradientGlyphAtlas().build(displayNames);
        for (Map.Entry<String, byte[]> page : glyphAtlas.pages().entrySet()) {
            textures.writeBytes(page.getKey(), page.getValue());
        }

        Path mcpack = workDir.resolve(config.packName() + ".mcpack");
        new McpackWriter().zip(packRoot, mcpack);
        new BedrockPackValidator().validate(mcpack, converted);

        int flat = 0;
        int threeD = 0;
        int unsupported = 0;
        int skipped = 0;
        int failed = 0;
        for (ConvertedItem item : converted) {
            if (item.flat()) {
                flat++;
            } else {
                threeD++;
            }
        }
        for (ItemReport report : reports) {
            switch (report.status()) {
                case "unsupported" -> unsupported++;
                case "skipped" -> skipped++;
                case "failed" -> failed++;
                default -> {
                }
            }
        }

        return new BuildResult(mcpack, List.copyOf(converted), List.copyOf(reports),
                List.copyOf(warnings), flat, threeD, unsupported, skipped, failed,
                Files.size(mcpack));
    }

    private String allocateId(String namespace, JavaItemEntry entry, Set<String> usedIds) {
        String base = Names.sanitizeId(entry.id());
        String candidate = base;
        int counter = 2;
        while (!usedIds.add(Names.bedrockIdentifier(namespace, candidate))) {
            candidate = base + "_" + counter++;
        }
        return Names.bedrockIdentifier(namespace, candidate);
    }

    private JsonObject disableAnimation() {
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        animation.addProperty("override_previous_animation", true);

        JsonObject animations = new JsonObject();
        animations.add("animation.nexobedrock.disable", animation);

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", animations);
        return root;
    }
}
