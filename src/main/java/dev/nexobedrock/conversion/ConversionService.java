package dev.nexobedrock.conversion;

import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.bedrock.BedrockPackValidator;
import dev.nexobedrock.geyser.GeyserDeployer;
import dev.nexobedrock.geyser.MappingWriter;
import dev.nexobedrock.pack.ItemIndexBuilder;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.NexoItemIndex;
import dev.nexobedrock.pack.NexoArmorIndex;
import dev.nexobedrock.pack.NexoPackReader;
import dev.nexobedrock.pack.ModelEngineAssetReader;
import dev.nexobedrock.pack.PackItemDefinitionParser;
import dev.nexobedrock.pack.PackSource;
import dev.nexobedrock.pack.PackSourceKind;
import dev.nexobedrock.pack.NexoItemConfigIndex;
import dev.nexobedrock.pack.PackTextureResolver;
import dev.nexobedrock.report.Report;
import dev.nexobedrock.report.ReportWriter;
import dev.nexobedrock.report.Stats;
import dev.nexobedrock.util.AtomicFiles;
import dev.nexobedrock.util.Hashing;
import dev.nexobedrock.util.PathUtil;
import dev.nexobedrock.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Orquestra leitura do pack, conversao, cache e deploy. A conversao roda em
 * thread assincrona; o callback e sempre chamado na main thread.
 */
public final class ConversionService {
    private final Plugin plugin;
    private final Logger logger;
    private final Supplier<PluginConfig> configSupplier;
    private final String pluginVersion;
    private final NexoPackReader reader = new NexoPackReader();
    private final PackItemDefinitionParser parser = new PackItemDefinitionParser();
    private final ItemIndexBuilder indexBuilder = new ItemIndexBuilder();
    private final BedrockPackBuilder packBuilder = new BedrockPackBuilder();
    private final MappingWriter mappingWriter = new MappingWriter();
    private final GeyserDeployer deployer;
    private final PackCache cache;
    private final ReportWriter reportWriter = new ReportWriter();

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<ConversionResult> lastResult = new AtomicReference<>();
    private volatile boolean closed;

    public ConversionService(Plugin plugin, Supplier<PluginConfig> configSupplier,
                             String pluginVersion) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configSupplier = configSupplier;
        this.pluginVersion = pluginVersion;
        this.deployer = new GeyserDeployer(plugin, logger);
        this.cache = new PackCache(plugin.getDataFolder().toPath().resolve("cache"), logger, 10);
    }

    public boolean isRunning() {
        return running.get();
    }

    public ConversionResult lastResult() {
        return lastResult.get();
    }

    public Path latestReportPath() {
        return plugin.getDataFolder().toPath().resolve("reports").resolve("latest.json");
    }

    public void close() {
        closed = true;
    }

    /**
     * Deve ser chamado na main thread: o snapshot da API do Nexo usa objetos
     * do Bukkit. O trabalho pesado e agendado em thread assincrona.
     */
    public void request(ConversionRequest request, Consumer<ConversionResult> callback) {
        Consumer<ConversionResult> safeCallback = callback == null ? result -> { } : callback;
        if (closed) {
            safeCallback.accept(ConversionResult.failure("Plugin desabilitado.",
                    null, 0));
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> request(request, safeCallback));
            } catch (Throwable throwable) {
                safeCallback.accept(ConversionResult.failure(
                        "Nao foi possivel agendar a conversao: " + throwable.getMessage(),
                        null, 0));
            }
            return;
        }
        if (!running.compareAndSet(false, true)) {
            safeCallback.accept(ConversionResult.busy(0));
            return;
        }

        NexoItemIndex nexoIndex;
        try {
            nexoIndex = NexoItemIndex.capture(configSupplier.get());
        } catch (Throwable throwable) {
            nexoIndex = NexoItemIndex.empty();
            logger.warning("NexoBedrock: falha ao capturar itens do Nexo: "
                    + throwable.getMessage());
        }

        NexoItemIndex snapshot = nexoIndex;
        org.bukkit.plugin.Plugin nexoPlugin = Bukkit.getPluginManager().getPlugin("Nexo");
        Path nexoItemsFolder = nexoPlugin == null ? null
                : nexoPlugin.getDataFolder().toPath().resolve("items");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ConversionResult result;
            try {
                result = run(request, snapshot, nexoItemsFolder);
            } catch (Throwable throwable) {
                logger.severe("NexoBedrock: conversao falhou: " + throwable);
                result = ConversionResult.failure("Erro inesperado: "
                        + throwable.getMessage(), null, 0);
            } finally {
                running.set(false);
            }

            ConversionResult finished = result;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> complete(finished, safeCallback));
            } catch (Throwable throwable) {
                // Plugin desabilitado durante a conversao.
                lastResult.set(finished);
            }
        });
    }

    private void complete(ConversionResult result, Consumer<ConversionResult> callback) {
        lastResult.set(result);
        if (result.success()) {
            maybeReload();
        }
        if (callback != null) {
            callback.accept(result);
        }
    }

    private ConversionResult run(ConversionRequest request, NexoItemIndex nexoIndex,
                                 Path nexoItemsFolder) {
        long started = System.nanoTime();
        if (closed) {
            return ConversionResult.failure("Plugin desabilitado durante a conversao.",
                    null, elapsedMs(started));
        }
        PluginConfig config = configSupplier.get();
        if (config == null || !config.enabled()) {
            return ConversionResult.failure("NexoBedrock esta desabilitado na configuracao.",
                    null, elapsedMs(started));
        }

        PackSource source = request.source() != null ? request.source() : chooseSource(config);
        if (source == null) {
            return ConversionResult.failure(
                    "Nenhum pack encontrado. Verifique source.pack-path e source.assets-path.",
                    null, elapsedMs(started));
        }
        if (source.kind() == PackSourceKind.ZIP && !Files.isRegularFile(source.path())) {
            return ConversionResult.failure("Pack zip nao encontrado: " + source.path(),
                    null, elapsedMs(started));
        }
        if (source.kind() == PackSourceKind.ASSETS_DIRECTORY
                && !Files.isDirectory(source.path())) {
            return ConversionResult.failure("Diretorio de assets nao encontrado: "
                    + source.path(), null, elapsedMs(started));
        }

        String sourceLabel = source.kind() + ": " + source.path();
        try {
            JavaPack pack = reader.read(source, config.maxPackSizeMb());
            if (source.kind() == PackSourceKind.ASSETS_DIRECTORY) {
                Path generatedZip = PathUtil.resolve(plugin, config.packPath());
                if (Files.isRegularFile(generatedZip)) {
                    try {
                        JavaPack generated = reader.read(new PackSource(PackSourceKind.ZIP,
                                generatedZip), config.maxPackSizeMb());
                        pack = pack.withGeneratedFonts(generated);
                    } catch (IOException exception) {
                        logger.warning("NexoBedrock: não foi possível ler fontes do pack gerado: "
                                + exception.getMessage());
                    }
                }
            }
            Path modelEngineBlueprints = PathUtil.resolve(plugin,
                    config.modelEngineBlueprintsFolder());
            Path modelEngineResourcePack = PathUtil.resolve(plugin,
                    config.modelEngineResourcePackPath());
            ModelEngineAssetReader.Result modelEngine = new ModelEngineAssetReader().readResult(
                    modelEngineBlueprints, modelEngineResourcePack);
            List<JavaItemEntry> packEntries = parser.parse(pack);
            ItemIndexBuilder.Result indexed = indexBuilder.build(nexoIndex, packEntries,
                    config, source.kind() == PackSourceKind.ZIP);
            List<JavaItemEntry> entries = indexed.entries();
            Path nexoItems = nexoItemsFolder == null
                    ? plugin.getDataFolder().toPath().getParent().resolve("Nexo/items")
                    : nexoItemsFolder;
            Map<String, NexoArmorIndex.Armor> armor = new NexoArmorIndex().read(nexoItems);
            Map<String, String> textureReferences = new NexoItemConfigIndex()
                    .readTextureReferences(nexoItems);
            PackTextureResolver textureResolver = new PackTextureResolver();
            List<String> appearanceWarnings = new ArrayList<>();
            List<JavaItemEntry> withIcons = new ArrayList<>(entries.size());
            for (JavaItemEntry entry : entries) {
                String reference = textureReferences.get(entry.id());
                if (reference == null) {
                    withIcons.add(entry);
                    continue;
                }
                String texture = textureResolver.resolve(pack, reference);
                if (texture == null) {
                    appearanceWarnings.add("Textura Pack.texture não encontrada ou ambígua para "
                            + entry.id() + ": " + reference);
                    withIcons.add(entry);
                } else {
                    withIcons.add(entry.withIconTexture(texture));
                }
            }
            entries = List.copyOf(withIcons);

            if (entries.isEmpty() && modelEngine.assets().isEmpty()) {
                Report report = report(config, request, pack.source(), pack.sourceHash(),
                        new Stats(0, 0, 0, 0, 0, 0, 0), List.of(),
                        List.of("Nenhum item do Nexo encontrado no pack."),
                        List.of("Nada para converter."), Map.of(), started);
                writeReports(report);
                return ConversionResult.failure(
                        "Nenhum item encontrado no pack; arquivos antigos preservados.",
                        report, elapsedMs(started));
            }

            Path guiOffsetsPath = plugin.getDataFolder().toPath().resolve("gui-offsets.json");
            boolean guiOffsetsExists = Files.exists(guiOffsetsPath);
            dev.nexobedrock.bedrock.GuiOffsets guiOffsets = dev.nexobedrock.bedrock.GuiOffsets.load(guiOffsetsPath);
            Path nexoGlyphs = nexoItems.getParent().resolve("glyphs");
            guiOffsets.setGlyphNames(new dev.nexobedrock.pack.NexoGlyphNames().read(nexoGlyphs));
            String fingerprint = config.fingerprint() + guiOffsets.fingerprint();
            // Armor definitions live outside the Java resource pack: changing their
            // layers must invalidate the cached Bedrock pack too.
            String armorFingerprint = Hashing.sha256Hex(
                    armor.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()
                            .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String appearanceFingerprint = Hashing.sha256Hex(textureReferences.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce("", (left, right) -> left + "\n" + right)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String modelEngineFingerprint = Hashing.sha256Hex(modelEngine.assets().stream()
                    .map(asset -> asset.id() + "|" + Hashing.sha256Hex(asset.texture()) + "|"
                            + asset.blueprint().elements() + "|" + asset.blueprint().animationData())
                    .reduce("", (left, right) -> left + "\n" + right));
            String cacheKey = cache.key(pack.sourceHash(),
                    fingerprint + armorFingerprint + appearanceFingerprint
                            + modelEngineFingerprint, pluginVersion);
            if (!request.force() && guiOffsetsExists) {
                PackCache.Entry cached = cache.find(cacheKey);
                if (cached != null) {
                    guiOffsets.save();
                    return deployCached(config, cached, started);
                }
            }

            Path workRoot = plugin.getDataFolder().toPath().resolve("work");
            Path workDir = workRoot.resolve(Time.fileSafeNow() + "-"
                    + Hashing.shortHash(pack.sourceHash()));
            AtomicFiles.deleteTree(workDir);
            Files.createDirectories(workDir);

            BedrockPackBuilder.BuildResult built;
            try {
                built = packBuilder.build(pack, entries, config, pack.sourceHash(),
                        pluginVersion, workDir, armor, modelEngine.assets(), modelEngine.warnings(), guiOffsets);
                guiOffsets.save();
            } catch (Throwable throwable) {
                AtomicFiles.deleteTree(workDir);
                throw throwable;
            }

            int converted = built.flat() + built.threeD();
            Stats stats = new Stats(entries.size(), converted, built.flat(), built.threeD(),
                    built.unsupported(), built.skipped(), built.failed());

            List<String> warnings = new ArrayList<>();
            warnings.addAll(pack.warnings());
            warnings.addAll(indexed.warnings());
            warnings.addAll(appearanceWarnings);
            warnings.addAll(built.warnings());

            Report baseReport = report(config, request, pack.source(), pack.sourceHash(),
                    stats, built.reports(), warnings, List.of(), Map.of(), started);

            if (converted == 0 && modelEngine.assets().isEmpty()) {
                writeWorkReport(workDir, baseReport);
                writeReports(baseReport);
                if (!config.debug()) {
                    AtomicFiles.deleteTree(workDir);
                }
                return ConversionResult.failure(
                        "Nenhum item pode ser convertido; arquivos antigos preservados.",
                        baseReport, elapsedMs(started));
            }

            long maxBytes = Math.max(1, config.maxPackSizeMb()) * 1024L * 1024L;
            if (built.sizeBytes() > maxBytes) {
                Report report = baseReport;
                writeReports(report);
                AtomicFiles.deleteTree(workDir);
                return ConversionResult.failure("Pack gerado excede o limite de "
                        + config.maxPackSizeMb() + " MB; arquivos antigos preservados.",
                        report, elapsedMs(started));
            }

            byte[] mappings = mappingWriter.write(built.items(), config);
            new BedrockPackValidator().validateMappings(built.items(), mappings);
            AtomicFiles.writeBytes(workDir.resolve("mappings.json"), mappings);

            if (closed) {
                AtomicFiles.deleteTree(workDir);
                return ConversionResult.failure(
                        "Plugin desabilitado antes do deploy; arquivos antigos preservados.",
                        baseReport, elapsedMs(started));
            }

            GeyserDeployer.DeployResult deployed = deployer.deploy(config,
                    built.mcpackPath(), mappings, Time.fileSafeNow());

            Map<String, String> outputs = new LinkedHashMap<>();
            outputs.put("pack", deployed.packPath().toString());
            outputs.put("mappings", deployed.mappingsPath().toString());
            if (deployed.backupDir() != null) {
                outputs.put("backup", deployed.backupDir().toString());
            }
            outputs.put("cacheKey", cacheKey);
            outputs.put("configFingerprint", fingerprint);
            if (config.debug()) {
                outputs.put("work", workDir.toString());
            }

            Report finalReport = baseReport.withOutputs(outputs);
            try {
                cache.store(cacheKey, pack.sourceHash(), fingerprint, built.mcpackPath(),
                        mappings, finalReport);
            } catch (Exception exception) {
                // O deploy ja aconteceu; uma falha de cache nao deve
                // transformar uma conversao publicada em erro.
                logger.warning("NexoBedrock: conversao publicada, mas o cache falhou: "
                        + exception.getMessage());
                outputs.put("cacheError", exception.getMessage());
                finalReport = baseReport.withOutputs(outputs);
            }
            writeWorkReport(workDir, finalReport);
            writeReports(finalReport);

            if (!config.debug()) {
                AtomicFiles.deleteTree(workDir);
            }

            List<String> deployWarnings = deployed.warnings();
            String message = "Conversao concluida: " + converted + " itens ("
                    + built.flat() + " 2D, " + built.threeD() + " 3D)."
                    + (deployWarnings.isEmpty() ? "" : " Avisos: " + deployWarnings.size() + ".");
            return new ConversionResult(true, false, message, finalReport,
                    deployed.packPath(), deployed.mappingsPath(), deployed.backupDir(),
                    elapsedMs(started));
        } catch (Throwable throwable) {
            logger.severe("NexoBedrock: falha na conversao de " + sourceLabel + ": "
                    + throwable.getMessage());
            Report report = report(config, request, sourceLabel, "",
                    new Stats(0, 0, 0, 0, 0, 0, 0), List.of(), List.of(),
                    List.of(throwable.getClass().getSimpleName() + ": "
                            + throwable.getMessage()), Map.of(), started);
            writeReports(report);
            return ConversionResult.failure("Falha na conversao: " + throwable.getMessage(),
                    report, elapsedMs(started));
        }
    }

    private ConversionResult deployCached(PluginConfig config, PackCache.Entry cached,
                                          long started) throws IOException {
        byte[] mappings = Files.readAllBytes(cached.mappings());
        GeyserDeployer.DeployResult deployed = deployer.deploy(config, cached.mcpack(),
                mappings, Time.fileSafeNow());

        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("pack", deployed.packPath().toString());
        outputs.put("mappings", deployed.mappingsPath().toString());
        if (deployed.backupDir() != null) {
            outputs.put("backup", deployed.backupDir().toString());
        }
        outputs.put("cacheKey", cached.key());
        outputs.put("cache", "true");

        Report report = cached.report().withOutputs(outputs);
        writeReports(report);

        String message = "Cache reutilizado: " + report.stats().converted()
                + " itens (" + report.stats().flat() + " 2D, "
                + report.stats().threeD() + " 3D).";
        return new ConversionResult(true, true, message, report, deployed.packPath(),
                deployed.mappingsPath(), deployed.backupDir(), elapsedMs(started));
    }

    private PackSource chooseSource(PluginConfig config) {
        Path assets = PathUtil.resolve(plugin, config.assetsPath());
        Path zip = PathUtil.resolve(plugin, config.packPath());
        if (config.preferAssetsOnEvent() && Files.isDirectory(assets)) {
            return new PackSource(PackSourceKind.ASSETS_DIRECTORY, assets);
        }
        if (Files.isRegularFile(zip)) {
            return new PackSource(PackSourceKind.ZIP, zip);
        }
        if (Files.isDirectory(assets)) {
            return new PackSource(PackSourceKind.ASSETS_DIRECTORY, assets);
        }
        return null;
    }

    private Report report(PluginConfig config, ConversionRequest request, String source,
                          String sourceHash, Stats stats,
                          List<dev.nexobedrock.report.ItemReport> items,
                          List<String> warnings, List<String> errors,
                          Map<String, String> outputs, long started) {
        return new Report(Time.isoNow(), pluginVersion, source, sourceHash,
                request.trigger(), elapsedMs(started), stats, items, warnings, errors, outputs);
    }

    private void writeWorkReport(Path workDir, Report report) {
        try {
            AtomicFiles.writeBytes(workDir.resolve("report.json"),
                    dev.nexobedrock.util.Json.prettyBytes(
                            dev.nexobedrock.util.Json.prettyGson().toJsonTree(report)));
        } catch (IOException exception) {
            logger.warning("NexoBedrock: falha ao escrever report de trabalho: "
                    + exception.getMessage());
        }
    }

    private void writeReports(Report report) {
        try {
            Path reports = plugin.getDataFolder().toPath().resolve("reports");
            Files.createDirectories(reports);
            reportWriter.write(reports.resolve("latest.json"), report);
            reportWriter.write(reports.resolve(Time.fileSafeNow() + ".json"), report);
        } catch (IOException exception) {
            logger.warning("NexoBedrock: falha ao escrever report: " + exception.getMessage());
        }
    }

    private void maybeReload() {
        PluginConfig config = configSupplier.get();
        if (config == null || !config.restartGeyser()) {
            return;
        }
        String command = config.reloadCommand();
        if (command == null || command.isBlank()) {
            return;
        }
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
        } catch (Throwable throwable) {
            logger.warning("NexoBedrock: falha ao executar '" + normalized + "': "
                    + throwable.getMessage());
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
