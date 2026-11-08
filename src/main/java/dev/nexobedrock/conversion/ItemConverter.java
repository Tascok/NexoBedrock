package dev.nexobedrock.conversion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.bedrock.AtlasResult;
import dev.nexobedrock.bedrock.ArmorAttachableWriter;
import dev.nexobedrock.bedrock.AttachableWriter;
import dev.nexobedrock.bedrock.AnimationWriter;
import dev.nexobedrock.bedrock.GeometryWriter;
import dev.nexobedrock.bedrock.ModelIconRenderer;
import dev.nexobedrock.bedrock.RangedItemWriter;
import dev.nexobedrock.bedrock.TextureAtlasBuilder;
import dev.nexobedrock.bedrock.TextureStore;
import dev.nexobedrock.config.HandheldMode;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.NexoArmorIndex;
import dev.nexobedrock.pack.JavaModelResolver;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.ResolvedModel;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.util.Names;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decide se um item vira 2D ou 3D e monta os recursos Bedrock correspondentes.
 */
public final class ItemConverter {
    public record Outcome(ConvertedItem item, ItemReport report) {
        public static Outcome unsupported(JavaItemEntry entry, String reason) {
            return new Outcome(null, ItemReport.unsupported(entry.id(), entry.javaItem(),
                    entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                    entry.itemModel(), entry.modelPath(), reason));
        }

        public static Outcome skipped(JavaItemEntry entry, String reason) {
            return new Outcome(null, ItemReport.skipped(entry.id(), entry.javaItem(),
                    entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                    entry.itemModel(), entry.modelPath(), reason));
        }

        public static Outcome failed(JavaItemEntry entry, String reason) {
            return new Outcome(null, ItemReport.failed(entry.id(), entry.javaItem(),
                    entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                    entry.itemModel(), entry.modelPath(), reason));
        }
    }

    private final JavaModelResolver resolver = new JavaModelResolver();
    private final TextureAtlasBuilder atlasBuilder = new TextureAtlasBuilder();
    private final GeometryWriter geometryWriter = new GeometryWriter();
    private final AnimationWriter animationWriter = new AnimationWriter();
    private final AttachableWriter attachableWriter = new AttachableWriter();
    private final ModelIconRenderer iconRenderer = new ModelIconRenderer();
    private final RangedItemWriter rangedItemWriter = new RangedItemWriter();
    private final ArmorAttachableWriter armorWriter = new ArmorAttachableWriter();

    public Outcome convert(JavaPack pack, JavaItemEntry entry, PluginConfig config,
                           String bedrockId, TextureStore textures) {
        return convert(pack, entry, config, bedrockId, textures, Map.of());
    }

    public Outcome convert(JavaPack pack, JavaItemEntry entry, PluginConfig config,
                           String bedrockId, TextureStore textures,
                           Map<String, NexoArmorIndex.Armor> armor) {
        if (entry.javaItem() == null || entry.javaItem().isBlank()) {
            return Outcome.unsupported(entry, "item base Java desconhecido");
        }
        NexoArmorIndex.Armor armorInfo = armor.get(entry.id());
        if (armorInfo != null && armorInfo.slot().equals(NexoArmorIndex.slot(entry.javaItem()))) {
            try {
                return armor(pack, entry, config, bedrockId, textures, armorInfo,
                        configuredIcon(pack, entry));
            } catch (IOException exception) {
                byte[] icon = configuredIcon(pack, entry);
                if (icon != null) {
                    return iconOnly(entry, config, bedrockId, icon, textures,
                            List.of("Camada de armadura não convertida; Pack.texture importada como ícone 2D."));
                }
                return Outcome.failed(entry, "erro ao converter armadura: " + exception.getMessage());
            }
        }
        if (entry.hasFlag("armor") || isArmor(entry.javaItem())) {
            return Outcome.unsupported(entry, "armadura/elytra fora do escopo do MVP");
        }
        byte[] iconOverride = configuredIcon(pack, entry);
        if (entry.modelPath() == null || entry.modelPath().isBlank()) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Pack.texture importada como ícone 2D; modelo de item ausente."));
            }
            return Outcome.skipped(entry, "sem modelo associado");
        }

        String modelKey = Names.normalizeKey(entry.modelPath(), "minecraft");
        if (modelKey == null) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Pack.texture importada como ícone 2D; caminho de modelo inválido."));
            }
            return Outcome.skipped(entry, "caminho de modelo invalido");
        }
        if (isBlockModel(pack, modelKey)) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Modelo de bloco não renderizado; Pack.texture importada como ícone."));
            }
            return Outcome.unsupported(entry, "modelo de bloco fora do escopo do MVP");
        }

        ResolvedModel model = resolver.resolve(pack, modelKey);
        if (model == null) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Modelo não encontrado; Pack.texture importada como ícone 2D."));
            }
            return Outcome.unsupported(entry, "modelo nao encontrado no pack: " + modelKey);
        }

        boolean threeD = model.hasElements();
        if (threeD && !config.include3d()) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Conversão 3D desabilitada; Pack.texture importada como ícone 2D."));
            }
            return Outcome.skipped(entry, "modelo 3D desabilitado na configuracao");
        }
        if (!threeD && !config.include2d()) {
            return Outcome.skipped(entry, "item 2D desabilitado na configuracao");
        }

        String id = Names.sanitizeId(Names.pathOf(bedrockId));
        String iconKey = Names.iconKey(Names.namespaceOf(bedrockId), id);
        boolean handheld = handheld(entry, pack, modelKey, config);

        try {
            if (!threeD) {
                return flat(pack, entry, model, config, bedrockId, iconKey, id, handheld,
                        textures, iconOverride);
            }
            return threeD(pack, entry, model, config, bedrockId, iconKey, id, handheld,
                    textures, iconOverride);
        } catch (Exception exception) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Modelo não convertido (" + exception.getMessage()
                                + "); Pack.texture importada como ícone 2D."));
            }
            return Outcome.failed(entry, "erro inesperado: " + exception.getMessage());
        }
    }

    private Outcome armor(JavaPack pack, JavaItemEntry entry, PluginConfig config,
                          String bedrockId, TextureStore textures,
                          NexoArmorIndex.Armor armor, byte[] iconOverride) throws IOException {
        String modelKey = Names.normalizeKey(entry.modelPath(), "minecraft");
        ResolvedModel model = modelKey == null ? null : resolver.resolve(pack, modelKey);
        byte[] icon;
        if (iconOverride != null) {
            icon = iconOverride;
        } else if (model != null && model.iconTexture() != null && !model.hasElements()) {
            icon = atlasBuilder.icon(pack, model, new ArrayList<>());
        } else {
            icon = pack.texture(armor.iconKey());
            if (icon == null) return Outcome.unsupported(entry, "icone de armadura ausente: " + armor.iconKey());
        }
        String textureKey = new NexoArmorIndex().texture(pack, armor);
        byte[] layer = textureKey == null ? null : pack.texture(textureKey);
        if (layer == null) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("CustomArmor sem camada Bedrock; Pack.texture importada como ícone 2D."));
            }
            return Outcome.unsupported(entry, "CustomArmor sem textura: " + armor.textureKey());
        }
        if (!config.include2d()) return Outcome.skipped(entry, "icone 2D desabilitado na configuracao");
        String id = Names.sanitizeId(Names.pathOf(bedrockId));
        String iconKey = Names.iconKey(Names.namespaceOf(bedrockId), id);
        String iconPath = textures.add(icon, "textures/nexobedrock/icons/" + id);
        String layerPath = textures.add(layer, "textures/nexobedrock/armor/" + id);
        String attachablePath = "attachables/" + id + ".attachable.json";
        JsonObject attachable = armorWriter.write(bedrockId, armor.slot(), layerPath);
        ItemReport report = ItemReport.converted(entry.id(), entry.javaItem(),
                entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                entry.itemModel(), entry.modelPath(), bedrockId, "armor", iconKey,
                null, attachablePath, layerPath, List.of());
        return new Outcome(new ConvertedItem(entry, bedrockId, iconKey, iconPath, layerPath,
                null, null, attachablePath, null, icon, null, attachable, null,
                true, false, List.of()), report);
    }

    private Outcome flat(JavaPack pack, JavaItemEntry entry, ResolvedModel model,
                         PluginConfig config, String bedrockId, String iconKey, String id,
                         boolean handheld, TextureStore textures, byte[] iconOverride) throws IOException {
        String primary = model.iconTexture();
        if (primary == null && iconOverride == null) {
            return Outcome.unsupported(entry, "modelo sem textura principal");
        }
        byte[] rawIcon = iconOverride == null ? pack.texture(primary) : iconOverride;
        if (rawIcon == null) {
            return Outcome.unsupported(entry, "textura nao encontrada: " + primary);
        }

        List<String> warnings = new ArrayList<>(model.warnings());
        byte[] icon = iconOverride == null ? atlasBuilder.icon(pack, model, warnings) : iconOverride;
        String iconPath = textures.add(icon, "textures/nexobedrock/icons/" + id);
        ItemReport report = ItemReport.converted(entry.id(), entry.javaItem(),
                entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                entry.itemModel(), entry.modelPath(), bedrockId, "flat", iconKey,
                null, null, iconPath, warnings);

        return new Outcome(new ConvertedItem(entry, bedrockId, iconKey, iconPath, null,
                null, null, null, null, icon, null, null, null, true, handheld,
                List.copyOf(warnings)), report);
    }

    private Outcome threeD(JavaPack pack, JavaItemEntry entry, ResolvedModel model,
                           PluginConfig config, String bedrockId, String iconKey, String id,
                           boolean handheld, TextureStore textures, byte[] iconOverride) throws IOException {
        AtlasResult atlas;
        try {
            atlas = atlasBuilder.build(pack, model, config);
        } catch (IllegalArgumentException exception) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of(exception.getMessage(), "Pack.texture importada como ícone 2D."));
            }
            return Outcome.unsupported(entry, exception.getMessage());
        }
        if (atlas.uvs().isEmpty()) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Modelo 3D sem faces; Pack.texture importada como ícone 2D."));
            }
            return Outcome.unsupported(entry, "modelo sem faces validas");
        }
        if (!atlasBuilder.hasVisiblePixel(atlas.png())) {
            if (iconOverride != null) {
                return iconOnly(entry, config, bedrockId, iconOverride, textures,
                        List.of("Atlas sem pixels; Pack.texture importada como ícone 2D."));
            }
            return Outcome.unsupported(entry, "atlas 3D sem pixels visiveis");
        }

        String atlasPath = textures.add(atlas.png(), "textures/nexobedrock/atlas/" + id);
        byte[] icon = iconOverride == null ? iconRenderer.render(model, atlas) : iconOverride;
        if (!atlasBuilder.hasVisiblePixel(icon)) {
            return Outcome.unsupported(entry, "modelo 3D sem icone GUI visivel");
        }
        String iconPath = textures.add(icon, "textures/nexobedrock/icons/" + id);

        String geometryId = Names.geometryIdentifier(id);
        String animationPrefix = "animation.nexobedrock." + id;
        JsonObject geometry = geometryWriter.write(geometryId, model, atlas);
        JsonObject animations = animationWriter.write(animationPrefix, model.display());
        JsonObject attachable = attachableWriter.write(bedrockId, atlasPath, geometryId,
                animationPrefix, config.attachableMaterial());

        List<String> warnings = new ArrayList<>(model.warnings());
        warnings.addAll(atlas.warnings());
        Map<String, byte[]> extra = rangedItemWriter.add(pack, config, entry,
                model.key(), id, attachable, warnings);

        String geometryPath = "models/entity/" + id + ".geo.json";
        String attachablePath = "attachables/" + id + ".attachable.json";
        String animationPath = "animations/" + id + ".animation.json";

        ItemReport report = ItemReport.converted(entry.id(), entry.javaItem(),
                entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                entry.itemModel(), entry.modelPath(), bedrockId, "3d", iconKey,
                geometryId, attachablePath, atlasPath, warnings);

        return new Outcome(new ConvertedItem(entry, bedrockId, iconKey, iconPath, atlasPath,
                geometryId, geometryPath, attachablePath, animationPath, icon,
                geometry, attachable, animations, false, handheld, List.copyOf(warnings), extra),
                report);
    }

    private Outcome iconOnly(JavaItemEntry entry, PluginConfig config, String bedrockId,
                             byte[] icon, TextureStore textures, List<String> warnings) {
        if (!config.include2d()) return Outcome.skipped(entry, "ícone 2D desabilitado");
        try {
            String id = Names.sanitizeId(Names.pathOf(bedrockId));
            String iconKey = Names.iconKey(Names.namespaceOf(bedrockId), id);
            String iconPath = textures.add(icon, "textures/nexobedrock/icons/" + id);
            ItemReport report = ItemReport.converted(entry.id(), entry.javaItem(),
                    entry.mode().name().toLowerCase(Locale.ROOT), entry.customModelData(),
                    entry.itemModel(), entry.modelPath(), bedrockId, "flat", iconKey,
                    null, null, iconPath, warnings);
            return new Outcome(new ConvertedItem(entry, bedrockId, iconKey, iconPath, null,
                    null, null, null, null, icon, null, null, null, true, false,
                    List.copyOf(warnings)), report);
        } catch (IOException exception) {
            return Outcome.failed(entry, "erro ao importar ícone: " + exception.getMessage());
        }
    }

    private byte[] configuredIcon(JavaPack pack, JavaItemEntry entry) {
        return entry.iconTexture() == null ? null : pack.texture(entry.iconTexture());
    }

    private boolean handheld(JavaItemEntry entry, JavaPack pack, String modelKey,
                             PluginConfig config) {
        if (config.handheldMode() == HandheldMode.ALWAYS) {
            return true;
        }
        if (config.handheldMode() == HandheldMode.NEVER) {
            return false;
        }
        String material = entry.javaItem() == null ? "" : entry.javaItem().toUpperCase(Locale.ROOT);
        if (material.contains("SWORD") || material.contains("AXE") || material.contains("PICKAXE")
                || material.contains("SHOVEL") || material.contains("HOE")
                || material.contains("FISHING_ROD") || material.contains("TRIDENT")
                || material.contains("MACE") || material.contains("SHEARS")
                || material.contains("BRUSH") || material.contains("FLINT_AND_STEEL")
                || material.contains("CARROT_ON_A_STICK")
                || material.contains("WARPED_FUNGUS_ON_A_STICK")) {
            return true;
        }
        return parentContains(pack, modelKey, "minecraft:item/handheld")
                || parentContains(pack, modelKey, "minecraft:item/handheld_rod");
    }

    private boolean isBlockModel(JavaPack pack, String modelKey) {
        return parentContains(pack, modelKey, "minecraft:block/");
    }

    private boolean parentContains(JavaPack pack, String modelKey, String prefix) {
        String current = modelKey;
        for (int depth = 0; depth < 32 && current != null; depth++) {
            if (current.startsWith(prefix)) {
                return true;
            }
            JsonObject model = pack.model(current);
            if (model == null) {
                return false;
            }
            JsonElement parent = model.get("parent");
            current = parent != null && parent.isJsonPrimitive()
                    ? Names.normalizeKey(parent.getAsString(), "minecraft") : null;
        }
        return false;
    }

    private boolean isArmor(String javaItem) {
        String material = javaItem.toUpperCase(Locale.ROOT);
        return material.contains("HELMET") || material.contains("CHESTPLATE")
                || material.contains("LEGGINGS") || material.contains("BOOTS")
                || material.equals("ELYTRA")
                || material.equals("TURTLE_HELMET") || material.equals("WOLF_ARMOR");
    }
}
