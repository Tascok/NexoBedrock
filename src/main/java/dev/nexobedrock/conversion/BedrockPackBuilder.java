package dev.nexobedrock.conversion;

import com.google.gson.JsonObject;
import dev.nexobedrock.bedrock.IconFactory;
import dev.nexobedrock.bedrock.BedrockGlyphConverter;
import dev.nexobedrock.bedrock.ChestGuiOverlayWriter;
import dev.nexobedrock.bedrock.GuiOffsets;
import dev.nexobedrock.bedrock.ScoreboardUiWriter;
import dev.nexobedrock.bedrock.ModelEngineAnimationWriter;
import dev.nexobedrock.bedrock.ModelEngineGeometryWriter;
import dev.nexobedrock.bedrock.ItemTextureWriter;
import dev.nexobedrock.bedrock.ManifestWriter;
import dev.nexobedrock.bedrock.McpackWriter;
import dev.nexobedrock.bedrock.BedrockPackValidator;
import dev.nexobedrock.bedrock.TextureStore;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.NexoArmorIndex;
import dev.nexobedrock.pack.ModelEngineAssetReader;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
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
        return build(pack, entries, config, packHash, pluginVersion, workDir, armor, List.of());
    }

    public BuildResult build(JavaPack pack, List<JavaItemEntry> entries, PluginConfig config,
                             String packHash, String pluginVersion, Path workDir,
                             Map<String, NexoArmorIndex.Armor> armor,
                             List<ModelEngineAssetReader.Asset> modelEngineAssets) throws IOException {
        return build(pack, entries, config, packHash, pluginVersion, workDir, armor,
                modelEngineAssets, List.of());
    }

    public BuildResult build(JavaPack pack, List<JavaItemEntry> entries, PluginConfig config,
                             String packHash, String pluginVersion, Path workDir,
                             Map<String, NexoArmorIndex.Armor> armor,
                              List<ModelEngineAssetReader.Asset> modelEngineAssets,
                              List<String> modelEngineWarnings) throws IOException {
        return build(pack, entries, config, packHash, pluginVersion, workDir, armor,
                modelEngineAssets, modelEngineWarnings, GuiOffsets.defaults());
    }

    public BuildResult build(JavaPack pack, List<JavaItemEntry> entries, PluginConfig config,
                             String packHash, String pluginVersion, Path workDir,
                             Map<String, NexoArmorIndex.Armor> armor,
                             List<ModelEngineAssetReader.Asset> modelEngineAssets,
                             List<String> modelEngineWarnings, GuiOffsets guiOffsets) throws IOException {
        Path packRoot = workDir.resolve("pack");
        Files.createDirectories(packRoot);

        TextureStore textures = new TextureStore(packRoot);
        ItemConverter converter = new ItemConverter();
        List<ConvertedItem> converted = new ArrayList<>();
        List<ItemReport> reports = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> itemTextures = new LinkedHashMap<>();
        JsonObject furnitureCatalog = new JsonObject();
        furnitureCatalog.addProperty("format_version", 1);
        furnitureCatalog.addProperty("runtime_ready", false);
        furnitureCatalog.addProperty("runtime_note",
                "Geometry assets only; a Geyser runtime bridge must spawn and synchronize each furniture instance.");
        JsonObject furnitureEntries = new JsonObject();
        furnitureCatalog.add("furniture", furnitureEntries);
        Map<String, ConvertedItem> furnitureItems = new LinkedHashMap<>();
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
            if (entry.hasFlag("furniture")) {
                furnitureItems.put(entry.id(), item);
            }
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
                Json.prettyBytes(new ManifestWriter().write(packHash + ":" + guiOffsets.fingerprint(), config, pluginVersion)));
        textures.writeJson("textures/item_texture.json",
                Json.prettyBytes(new ItemTextureWriter().write(itemTextures, config.packName())));
        textures.writeJson("animations/nexobedrock_disable.json",
                Json.prettyBytes(disableAnimation()));
        textures.writeBytes("pack_icon.png", new IconFactory().packIcon(firstIcon));
        List<String> furnitureIds = furnitureItems.keySet().stream().sorted().toList();
        StringBuilder furnitureMappings = new StringBuilder("# Generated by NexoBedrock; copy to the Geyser extension data folder.\n");
        JsonObject furnitureRenderControllers = new JsonObject();
        Map<Integer, String> furnitureIndexOwners = new HashMap<>();
        for (String furnitureId : furnitureIds) {
            ConvertedItem item = furnitureItems.get(furnitureId);
            if (item.geometryId() == null || item.atlasPath() == null) continue;
            int index = furnitureModelIndex(furnitureId);
            String previous = furnitureIndexOwners.putIfAbsent(index, furnitureId);
            if (previous != null) {
                throw new IOException("Colisão de índice de furniture: " + previous + " e " + furnitureId);
            }
            JsonObject furniture = new JsonObject();
            furniture.addProperty("model_index", index);
            furniture.addProperty("bedrock_identifier", item.bedrockId());
            furniture.addProperty("geometry", item.geometryId() == null ? "" : item.geometryId());
            furniture.addProperty("geometry_file", item.geometryPath() == null ? "" : item.geometryPath());
            furniture.addProperty("texture", item.atlasPath() == null ? item.iconPath() : item.atlasPath());
            furniture.addProperty("display_attachable", item.attachablePath() == null ? "" : item.attachablePath());
            furniture.addProperty("java_item", item.entry().javaItem());
            furniture.addProperty("model", item.entry().modelPath());
            furnitureEntries.add(furnitureId, furniture);
            furnitureMappings.append(index).append('=').append(item.bedrockId()).append('\n');
            if (item.geometryId() != null && item.atlasPath() != null) {
                String controllerId = "controller.render.nexobedrock.furniture_"
                        + Names.sanitizeId(furnitureId);
                textures.writeJson("entity/" + Names.pathOf(item.bedrockId()) + ".entity.json",
                        Json.prettyBytes(furnitureEntity(item, controllerId)));
                furnitureRenderControllers.add(controllerId,
                        modelEngineRenderController(item.geometryId(), item.atlasPath()));
            }
        }
        if (!furnitureEntries.isEmpty()) {
            textures.writeJson("nexobedrock/furniture-catalog.json",
                    Json.prettyBytes(furnitureCatalog));
            textures.writeBytes("nexobedrock/furniture-mappings.properties",
                    furnitureMappings.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!furnitureRenderControllers.isEmpty()) {
                JsonObject controllerRoot = new JsonObject();
                controllerRoot.addProperty("format_version", "1.10.0");
                controllerRoot.add("render_controllers", furnitureRenderControllers);
                textures.writeJson("render_controllers/nexobedrock_furniture.render_controllers.json",
                        Json.prettyBytes(controllerRoot));
            }
        }

        writeModelEngineAssets(textures, modelEngineAssets, config.namespace(), warnings);

        BedrockGlyphConverter.Result glyphs = new BedrockGlyphConverter().convert(pack);
        warnings.addAll(new ChestGuiOverlayWriter().write(pack, glyphs.codepoints(), textures, guiOffsets).warnings());
        new ScoreboardUiWriter().write(textures);
        for (Map.Entry<String, byte[]> page : glyphs.pages().entrySet()) {
            textures.writeBytes(page.getKey(), page.getValue());
        }
        if (!glyphs.codepoints().isEmpty()) {
            JsonObject glyphMap = new JsonObject();
            glyphMap.addProperty("format_version", 1);
            JsonObject mappings = new JsonObject();
            glyphs.codepoints().forEach((source, target) ->
                    mappings.addProperty(source, String.format("%04X", target)));
            glyphMap.add("glyphs", mappings);
            textures.writeJson("nexobedrock/glyph-map.json", Json.prettyBytes(glyphMap));
        }
        warnings.addAll(glyphs.warnings());
        warnings.addAll(modelEngineWarnings);

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

    static int furnitureModelIndex(String furnitureId) {
        String hash = dev.nexobedrock.util.Hashing.sha256Hex("nexobedrock:furniture:" + furnitureId);
        return (int) (Long.parseLong(hash.substring(0, 8), 16) & 0x7fffffffL);
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

    private JsonObject furnitureEntity(ConvertedItem item, String controllerId) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", item.bedrockId());
        description.addProperty("is_spawnable", false);
        description.addProperty("is_summonable", false);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", item.atlasPath());
        description.add("textures", textures);
        JsonObject geometries = new JsonObject();
        geometries.addProperty("default", item.geometryId());
        description.add("geometry", geometries);
        com.google.gson.JsonArray renderControllers = new com.google.gson.JsonArray();
        renderControllers.add(controllerId);
        description.add("render_controllers", renderControllers);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:client_entity", new JsonObject());
        root.getAsJsonObject("minecraft:client_entity").add("description", description);
        return root;
    }

    private void writeModelEngineAssets(TextureStore textures,
                                        List<ModelEngineAssetReader.Asset> assets,
                                        String namespace, List<String> warnings) throws IOException {
        if (assets.isEmpty()) return;
        JsonObject catalog = new JsonObject();
        catalog.addProperty("format_version", 1);
        catalog.addProperty("runtime_ready", false);
        catalog.addProperty("runtime_note",
                "Static geometry and animation assets only; live ModelEngine entity synchronization is not enabled.");
        JsonObject models = new JsonObject();
        JsonObject renderControllers = new JsonObject();
        ModelEngineGeometryWriter geometryWriter = new ModelEngineGeometryWriter();
        ModelEngineAnimationWriter animationWriter = new ModelEngineAnimationWriter();
        for (ModelEngineAssetReader.Asset asset : assets) {
            String id = Names.sanitizeId(asset.id());
            String bedrockId = Names.bedrockIdentifier(namespace, "modelengine_" + id);
            String geometryId = Names.geometryIdentifier("modelengine_" + id);
            String texturePath = textures.add(asset.texture(),
                    "textures/nexobedrock/modelengine/" + id);
            ModelEngineGeometryWriter.Result geometry = geometryWriter.write(geometryId,
                    asset.blueprint());
            ModelEngineAnimationWriter.Result animations = animationWriter.write(id,
                    asset.blueprint());
            String geometryFile = "models/entity/modelengine_" + id + ".geo.json";
            String animationFile = "animations/modelengine_" + id + ".animation.json";
            String entityFile = "entity/modelengine_" + id + ".entity.json";
            String controllerId = "controller.render.nexobedrock." + id;
            textures.writeJson(geometryFile, Json.prettyBytes(geometry.geometry()));
            if (!animations.identifiers().isEmpty()) {
                textures.writeJson(animationFile, Json.prettyBytes(animations.animations()));
            }
            textures.writeJson(entityFile, Json.prettyBytes(modelEngineEntity(bedrockId,
                    geometryId, texturePath, controllerId, animations.identifiers())));
            renderControllers.add(controllerId,
                    modelEngineRenderController(geometryId, texturePath));

            JsonObject entry = new JsonObject();
            entry.addProperty("bedrock_identifier", bedrockId);
            entry.addProperty("geometry", geometryId);
            entry.addProperty("geometry_file", geometryFile);
            entry.addProperty("texture", texturePath);
            entry.addProperty("entity_file", entityFile);
            entry.addProperty("animation_file", animations.identifiers().isEmpty() ? "" : animationFile);
            com.google.gson.JsonArray clips = new com.google.gson.JsonArray();
            animations.identifiers().keySet().forEach(clips::add);
            entry.add("clips", clips);
            models.add(asset.id(), entry);
            warnings.addAll(asset.warnings());
            warnings.addAll(geometry.warnings());
            warnings.addAll(animations.warnings());
        }
        catalog.add("models", models);
        if (!renderControllers.isEmpty()) {
            JsonObject controllerRoot = new JsonObject();
            controllerRoot.addProperty("format_version", "1.10.0");
            controllerRoot.add("render_controllers", renderControllers);
            textures.writeJson("render_controllers/nexobedrock.render_controllers.json",
                    Json.prettyBytes(controllerRoot));
        }
        textures.writeJson("nexobedrock/modelengine-catalog.json", Json.prettyBytes(catalog));
    }

    private JsonObject modelEngineEntity(String identifier, String geometryId, String texturePath,
                                         String controllerId, Map<String, String> animations) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("is_spawnable", false);
        description.addProperty("is_summonable", false);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);
        description.add("textures", textures);
        JsonObject geometries = new JsonObject();
        geometries.addProperty("default", geometryId);
        description.add("geometry", geometries);
        if (!animations.isEmpty()) {
            JsonObject animationMap = new JsonObject();
            animations.forEach(animationMap::addProperty);
            description.add("animations", animationMap);
        }
        com.google.gson.JsonArray renderControllers = new com.google.gson.JsonArray();
        renderControllers.add(controllerId);
        description.add("render_controllers", renderControllers);
        JsonObject clientEntity = new JsonObject();
        clientEntity.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:client_entity", clientEntity);
        return root;
    }

    private JsonObject modelEngineRenderController(String geometryId, String texturePath) {
        JsonObject controller = new JsonObject();
        controller.addProperty("geometry", geometryId);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);
        controller.add("textures", textures);
        com.google.gson.JsonArray materials = new com.google.gson.JsonArray();
        JsonObject material = new JsonObject();
        material.addProperty("*", "Material.default");
        materials.add(material);
        controller.add("materials", materials);
        return controller;
    }
}
