package dev.nexobedrock;

import com.google.gson.JsonObject;
import dev.nexobedrock.bedrock.McpackWriter;
import dev.nexobedrock.bedrock.BedrockPackValidator;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.conversion.BedrockPackBuilder;
import dev.nexobedrock.geyser.MappingWriter;
import dev.nexobedrock.pack.ItemIndexBuilder;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.NexoItemIndex;
import dev.nexobedrock.pack.NexoArmorIndex;
import dev.nexobedrock.pack.NexoPackReader;
import dev.nexobedrock.pack.PackItemDefinitionParser;
import dev.nexobedrock.pack.PackSource;
import dev.nexobedrock.pack.PackSourceKind;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de fumaca opcional contra o pack real. Nao roda por padrao e nunca
 * escreve dentro de Contexto/.
 */
@EnabledIfEnvironmentVariable(named = "NEXOBEDROCK_SMOKE", matches = "true")
class SmokeRealPackTest {
    @Test
    void convertsRealNexoPackWithoutCrashing() throws Exception {
        String packPath = System.getenv().getOrDefault("NEXOBEDROCK_SMOKE_PACK",
                "Contexto/Plugins/Nexo/pack/pack.zip");
        Path zip = Path.of(packPath).toAbsolutePath();
        assertTrue(Files.isRegularFile(zip), "pack nao encontrado: " + zip);

        JavaPack pack = new NexoPackReader().read(
                new PackSource(PackSourceKind.ZIP, zip), 100);
        System.out.println("PACK models=" + pack.models().size()
                + " definitions=" + pack.itemDefinitions().size()
                + " textures=" + pack.textures().size()
                + " warnings=" + pack.warnings().size());
        assertFalse(pack.models().isEmpty());
        assertFalse(pack.textures().isEmpty());
        assertFalse(pack.fonts().isEmpty(), "font providers nao foram lidos");
        assertTrue(pack.fonts().containsKey("nexo:default"), "fonte do Nexo nao encontrada");

        List<JavaItemEntry> packEntries = new PackItemDefinitionParser().parse(pack);
        System.out.println("PACK entries=" + packEntries.size());

        PluginConfig config = TestFixtures.config();
        ItemIndexBuilder.Result indexed = new ItemIndexBuilder().build(NexoItemIndex.empty(),
                packEntries, config, true);
        System.out.println("INDEXED entries=" + indexed.entries().size()
                + " warnings=" + indexed.warnings().size());

        Path work = Path.of("build/smoke").toAbsolutePath();
        if (Files.exists(work)) {
            dev.nexobedrock.util.AtomicFiles.deleteTree(work);
        }
        Files.createDirectories(work);

        BedrockPackBuilder.BuildResult built = new BedrockPackBuilder().build(pack,
                indexed.entries(), config, pack.sourceHash(), "smoke", work);
        System.out.println("BUILT flat=" + built.flat() + " 3d=" + built.threeD()
                + " unsupported=" + built.unsupported() + " skipped=" + built.skipped()
                + " failed=" + built.failed() + " size=" + built.sizeBytes());

        int converted = built.flat() + built.threeD();
        assertTrue(converted > 0, "nenhum item convertido");
        assertTrue(Files.isRegularFile(built.mcpackPath()));
        assertTrue(built.sizeBytes() > 0);

        byte[] mappings = new MappingWriter().write(built.items(), config);
        new BedrockPackValidator().validateMappings(built.items(), mappings);
        Files.write(work.resolve("mappings.json"), mappings);
        var mappingRoot = Json.parseObject(mappings);
        assertTrue(mappingRoot.get("format_version").getAsInt() == 2);
        System.out.println("MAPPINGS items="
                + mappingRoot.getAsJsonObject("items").size());

        List<String> geometryFiles = new ArrayList<>();
        List<String> attachableFiles = new ArrayList<>();
        List<String> animationFiles = new ArrayList<>();
        List<String> glyphPages = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(built.mcpackPath().toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("models/entity/") && name.endsWith(".json")) {
                    geometryFiles.add(name);
                    Json.parseObject(zipFile.getInputStream(
                            zipFile.getEntry(name)).readAllBytes());
                } else if (name.startsWith("attachables/")) {
                    attachableFiles.add(name);
                    Json.parseObject(zipFile.getInputStream(
                            zipFile.getEntry(name)).readAllBytes());
                } else if (name.startsWith("animations/")) {
                    animationFiles.add(name);
                    Json.parseObject(zipFile.getInputStream(
                            zipFile.getEntry(name)).readAllBytes());
                } else if (name.startsWith("font/glyph_") && name.endsWith(".png")) {
                    glyphPages.add(name);
                }
            }
        }
        System.out.println("FILES geometry=" + geometryFiles.size()
                + " attachables=" + attachableFiles.size()
                + " animations=" + animationFiles.size());
        assertTrue(geometryFiles.size() >= built.threeD());
        assertTrue(attachableFiles.size() == built.threeD());
        assertTrue(!glyphPages.isEmpty(), "nenhuma pagina Bedrock de glyph foi gerada");
        try (ZipFile generated = new ZipFile(built.mcpackPath().toFile())) {
            JsonObject glyphMap = Json.parseObject(generated.getInputStream(
                    generated.getEntry("nexobedrock/glyph-map.json")).readAllBytes());
            assertTrue(glyphMap.getAsJsonObject("glyphs").size() > 0);
        }
        JavaItemEntry furnitureSource = built.items().stream()
                .filter(item -> !item.flat())
                .map(dev.nexobedrock.conversion.ConvertedItem::entry)
                .findFirst().orElseThrow();
        JavaItemEntry furniture = new JavaItemEntry("smoke_furniture",
                furnitureSource.javaItem(), furnitureSource.mode(),
                furnitureSource.customModelData(), furnitureSource.itemModel(),
                furnitureSource.modelPath(), furnitureSource.displayName(),
                java.util.Set.of("furniture"), "smoke");
        var furnitureBuild = new BedrockPackBuilder().build(pack, List.of(furniture), config,
                pack.sourceHash(), "smoke", work.resolve("furniture"));
        assertEquals(1, furnitureBuild.threeD(), "furniture model geometry was not generated");
        try (ZipFile generated = new ZipFile(furnitureBuild.mcpackPath().toFile())) {
            JsonObject catalog = Json.parseObject(generated.getInputStream(
                    generated.getEntry("nexobedrock/furniture-catalog.json")).readAllBytes());
            assertTrue(!catalog.get("runtime_ready").getAsBoolean());
            JsonObject entry = catalog.getAsJsonObject("furniture").getAsJsonObject("smoke_furniture");
            assertTrue(entry.has("model_index"));
            assertTrue(entry.has("geometry"));
            String entityPath = "entity/smoke_furniture.entity.json";
            assertTrue(generated.getEntry(entityPath) != null,
                    "custom Bedrock client entity definition was not generated");
            JsonObject entity = Json.parseObject(generated.getInputStream(
                    generated.getEntry(entityPath)).readAllBytes());
            JsonObject description = entity.getAsJsonObject("minecraft:client_entity")
                    .getAsJsonObject("description");
            assertEquals("nexobedrock:smoke_furniture",
                    description.get("identifier").getAsString());
            String controllerId = description.getAsJsonArray("render_controllers")
                    .get(0).getAsString();
            String controllerPath = "render_controllers/nexobedrock_furniture.render_controllers.json";
            assertTrue(generated.getEntry(controllerPath) != null,
                    "furniture render controller file was not generated");
            JsonObject controllerFile = Json.parseObject(generated.getInputStream(
                    generated.getEntry(controllerPath)).readAllBytes());
            assertTrue(controllerFile.getAsJsonObject("render_controllers").has(controllerId),
                    "entity references an undeclared render controller");
            String runtimeMap = new String(generated.getInputStream(generated.getEntry(
                    "nexobedrock/furniture-mappings.properties")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(runtimeMap.contains(entry.get("model_index").getAsInt()
                    + "=nexobedrock:smoke_furniture"));
        }
        JavaItemEntry crossbow = indexed.entries().stream()
                .filter(item -> "minecraft:crossbow".equals(item.javaItem()))
                .findFirst().orElseThrow();
        JavaItemEntry longNamed = new JavaItemEntry(
                "broodmother_assortment_weapon_crossbow", crossbow.javaItem(),
                crossbow.mode(), crossbow.customModelData(), crossbow.itemModel(),
                crossbow.modelPath(), crossbow.displayName(), crossbow.flags(), crossbow.source());
        var longBuild = new BedrockPackBuilder().build(pack, List.of(longNamed), config,
                pack.sourceHash(), "smoke", work.resolve("long-names"));
        assertEquals(1, longBuild.threeD());
        assertEquals(5, longBuild.items().get(0).extraResources().keySet().stream()
                .filter(path -> path.startsWith("models/entity/")).count());
        try (ZipFile generated = new ZipFile(built.mcpackPath().toFile())) {
            for (String base : List.of("bow_1000", "crossbow_1000")) {
                var data = Json.parseObject(generated.getInputStream(generated.getEntry(
                        "attachables/" + base + ".attachable.json")).readAllBytes())
                        .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
                assertEquals(base.startsWith("cross") ? 6 : 4,
                        data.getAsJsonObject("geometry").size(), base);
                String scripts = data.getAsJsonObject("scripts")
                        .getAsJsonArray("pre_animation").toString();
                assertTrue(scripts.contains("q.main_hand_item_max_duration"), base);
                assertTrue(scripts.contains("q.main_hand_item_use_duration"), base);
                assertFalse(scripts.contains("q.item_in_use_duration"), base);
                assertTrue(scripts.contains(base.startsWith("cross") ? "16.25" : "13.00"), base);
                assertTrue(scripts.contains(base.startsWith("cross") ? "22.50" : "18.00"), base);
                if (base.startsWith("cross")) assertTrue(scripts.contains("q.item_is_charged"));
                assertTrue(data.getAsJsonArray("render_controllers").get(0)
                        .getAsString().startsWith("controller.render.nexobedrock."));
                byte[] png = generated.getInputStream(generated.getEntry(
                        "textures/nexobedrock/icons/" + base + ".png")).readAllBytes();
                var icon = ImageIO.read(new ByteArrayInputStream(png));
                assertEquals(128, icon.getWidth());
                assertEquals(128, icon.getHeight());
                int opaque = 0, transparent = 0;
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    if ((icon.getRGB(x, y) >>> 24) == 0) transparent++;
                    else opaque++;
                }
                assertTrue(opaque > 20 && transparent > 20, "icone GUI nao renderizado: " + base);
            }
        }

        List<String> reasons = new ArrayList<>();
        for (ItemReport item : built.reports()) {
            if (!"converted".equals(item.status())) {
                reasons.add(item.status() + ": " + item.reason());
            }
        }
        System.out.println("REASONS " + reasons.stream().limit(15).toList());
    }

    @Test
    void convertsAssetsDirectoryWithSyntheticNexoIndex() throws Exception {
        String assetsPath = System.getenv().getOrDefault("NEXOBEDROCK_SMOKE_ASSETS",
                "Contexto/Plugins/Nexo/pack/assets");
        Path assets = Path.of(assetsPath).toAbsolutePath();
        assertTrue(Files.isDirectory(assets), "assets nao encontrado: " + assets);

        JavaPack pack = new NexoPackReader().read(
                new PackSource(PackSourceKind.ASSETS_DIRECTORY, assets), 100);
        System.out.println("ASSETS models=" + pack.models().size()
                + " textures=" + pack.textures().size()
                + " animated=" + pack.animatedTextures().size());

        List<JavaItemEntry> synthetic = new ArrayList<>();
        int cmd = 2000;
        for (String model : pack.models().keySet()) {
            if (model.startsWith("minecraft:block/")) {
                continue;
            }
            synthetic.add(new JavaItemEntry("synthetic_" + cmd, "minecraft:paper",
                    dev.nexobedrock.config.MappingMode.LEGACY, cmd++, null, model,
                    "Synthetic " + cmd, java.util.Set.of(), "smoke"));
        }
        NexoItemIndex index = new NexoItemIndex(List.copyOf(synthetic), List.of());

        PluginConfig config = TestFixtures.config();
        ItemIndexBuilder.Result indexed = new ItemIndexBuilder().build(index, List.of(),
                config, false);
        Path work = Path.of("build/smoke-assets").toAbsolutePath();
        if (Files.exists(work)) {
            dev.nexobedrock.util.AtomicFiles.deleteTree(work);
        }
        Files.createDirectories(work);

        BedrockPackBuilder.BuildResult built = new BedrockPackBuilder().build(pack,
                indexed.entries(), config, pack.sourceHash(), "smoke-assets", work);
        System.out.println("ASSETS BUILT flat=" + built.flat() + " 3d=" + built.threeD()
                + " unsupported=" + built.unsupported() + " skipped=" + built.skipped()
                + " failed=" + built.failed() + " size=" + built.sizeBytes());

        assertTrue(built.flat() + built.threeD() > 100);
        assertTrue(Files.isRegularFile(built.mcpackPath()));
        byte[] mappings = new MappingWriter().write(built.items(), config);
        new BedrockPackValidator().validateMappings(built.items(), mappings);
        var root = Json.parseObject(mappings);
        assertTrue(root.getAsJsonObject("items").size() > 0);
        System.out.println("ASSETS MAPPINGS items="
                + root.getAsJsonObject("items").size());
    }

    @Test
    void convertsBroodmotherArmorIconsAndEquippedLayers() throws Exception {
        Path zip = Path.of("Contexto/Plugins/Nexo/pack/pack.zip").toAbsolutePath();
        JavaPack pack = new NexoPackReader().read(new PackSource(PackSourceKind.ZIP, zip), 100);
        Map<String, NexoArmorIndex.Armor> armor = new NexoArmorIndex().read(
                Path.of("Contexto/Plugins/Nexo/items"));
        JavaPack assets = new NexoPackReader().read(new PackSource(PackSourceKind.ASSETS_DIRECTORY,
                Path.of("Contexto/Plugins/Nexo/pack/assets")), 100);
        for (var definition : armor.values()) {
            assertEquals(definition.textureKey(), new NexoArmorIndex().texture(assets, definition));
        }
        List<JavaItemEntry> assetEntries = new ArrayList<>();
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            assetEntries.add(new JavaItemEntry("broodmother_" + piece,
                    "minecraft:netherite_" + piece, MappingMode.LEGACY, 1000, null,
                    "minecraft:broodmother_" + piece, piece, Set.of("armor"), "nexo"));
        }
        var fromAssets = new BedrockPackBuilder().build(assets, assetEntries,
                TestFixtures.config(), assets.sourceHash(), "smoke-armor-assets",
                Path.of("build/smoke-armor-assets").toAbsolutePath(), armor);
        assertEquals(4, fromAssets.items().size(), fromAssets.reports().toString());
        List<JavaItemEntry> fromPack = new PackItemDefinitionParser().parse(pack);
        List<JavaItemEntry> entries = new ArrayList<>();
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            String material = "minecraft:netherite_" + piece;
            JavaItemEntry indexed = fromPack.stream().filter(e -> material.equals(e.javaItem())
                    && Integer.valueOf(1000).equals(e.customModelData())).findFirst().orElseThrow();
            entries.add(new JavaItemEntry("broodmother_" + piece, material, MappingMode.LEGACY,
                    1000, null, indexed.modelPath(), piece, Set.of("armor"), "nexo"));
        }
        Path work = Path.of("build/smoke-armor").toAbsolutePath();
        var built = new BedrockPackBuilder().build(pack, entries, TestFixtures.config(),
                pack.sourceHash(), "smoke-armor", work, armor);
        assertEquals(4, built.items().size(), "armor=" + armor + " reports=" + built.reports());
        new BedrockPackValidator().validateMappings(built.items(),
                new MappingWriter().write(built.items(), TestFixtures.config()));
        try (ZipFile generated = new ZipFile(built.mcpackPath().toFile())) {
            for (var item : built.items()) {
                String slot = NexoArmorIndex.slot(item.entry().javaItem());
                var attachable = Json.parseObject(generated.getInputStream(generated.getEntry(
                        item.attachablePath())).readAllBytes())
                        .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
                assertEquals("geometry.player.armor." + item.entry().javaItem()
                        .substring("minecraft:netherite_".length()),
                        attachable.getAsJsonObject("geometry").get("default").getAsString());
                assertEquals("controller.render.armor", attachable.getAsJsonArray(
                        "render_controllers").get(0).getAsString());
                String expectedLayer = slot.equals("legs") ? "layer_2" : "layer_1";
                assertTrue(armor.get(item.entry().id()).textureKey().endsWith(expectedLayer));
                byte[] equipped = generated.getInputStream(generated.getEntry(
                        item.atlasPath() + ".png")).readAllBytes();
                String resolved = new NexoArmorIndex().texture(pack, armor.get(item.entry().id()));
                assertTrue(java.util.Arrays.equals(pack.texture(resolved), equipped));
                assertFalse(item.iconPath().equals(item.atlasPath()));
                var icon = ImageIO.read(new ByteArrayInputStream(generated.getInputStream(
                        generated.getEntry(item.iconPath() + ".png")).readAllBytes()));
                assertTrue(icon.getWidth() > 0);
            }
        }
        assertEquals(null, NexoArmorIndex.slot("LEATHER_HORSE_ARMOR"));
        JavaPack missingLayer = new JavaPack(pack.source(), pack.sourceHash(), pack.models(),
                pack.itemDefinitions(), Map.of(), pack.warnings(), pack.sizeBytes());
        var rejected = new BedrockPackBuilder().build(missingLayer, entries, TestFixtures.config(),
                pack.sourceHash(), "missing-layer", work.resolve("missing-layer"), armor);
        assertEquals(0, rejected.items().size());
        assertEquals(4, rejected.unsupported());
    }
}
