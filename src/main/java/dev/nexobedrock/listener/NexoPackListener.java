package dev.nexobedrock.listener;

import com.nexomc.nexo.api.events.resourcepack.NexoPackFinishedEvent;
import com.nexomc.nexo.api.events.resourcepack.NexoPostPackGenerateEvent;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.conversion.ConversionRequest;
import dev.nexobedrock.conversion.ConversionResult;
import dev.nexobedrock.conversion.ConversionService;
import dev.nexobedrock.pack.PackSource;
import dev.nexobedrock.pack.PackSourceKind;
import dev.nexobedrock.util.PathUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Escuta o Nexo sem tocar no ResourcePack do evento.
 *
 * NexoPostPackGenerateEvent acontece antes da obfuscacao, entao usamos o
 * diretorio assets pre-obfuscado. Se ele nao existir, NexoPackFinishedEvent
 * serve de fallback usando o pack.zip final (auto-consistente, mas com nomes
 * de modelo ofuscados).
 */
public final class NexoPackListener implements Listener {
    private static final long AUTO_DEBOUNCE_MS = 60_000L;

    private final Plugin plugin;
    private final ConversionService service;
    private final Supplier<PluginConfig> configSupplier;
    private final Logger logger;
    private final AtomicLong lastAutoAt = new AtomicLong();

    public NexoPackListener(Plugin plugin, ConversionService service,
                            Supplier<PluginConfig> configSupplier) {
        this.plugin = plugin;
        this.service = service;
        this.configSupplier = configSupplier;
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostPackGenerate(NexoPostPackGenerateEvent event) {
        PluginConfig config = configSupplier.get();
        if (config == null || !config.enabled() || !config.autoConvert()) {
            return;
        }
        Path assets = PathUtil.resolve(plugin, config.assetsPath());
        if (!config.preferAssetsOnEvent() || !Files.isDirectory(assets)) {
            // O pack final ainda pode ser publicado; deixa o FinishedEvent agir.
            return;
        }

        lastAutoAt.set(System.currentTimeMillis());
        service.request(ConversionRequest.event("post-pack-generate",
                new PackSource(PackSourceKind.ASSETS_DIRECTORY, assets)), this::logResult);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackFinished(NexoPackFinishedEvent event) {
        PluginConfig config = configSupplier.get();
        if (config == null || !config.enabled() || !config.autoConvert()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoAt.get() < AUTO_DEBOUNCE_MS) {
            return;
        }
        Path zip = PathUtil.resolve(plugin, config.packPath());
        if (!Files.isRegularFile(zip)) {
            return;
        }

        lastAutoAt.set(now);
        service.request(ConversionRequest.event("pack-finished",
                new PackSource(PackSourceKind.ZIP, zip)), this::logResult);
    }

    private void logResult(ConversionResult result) {
        if (result.success()) {
            logger.info("NexoBedrock: " + result.message()
                    + (result.fromCache() ? " (cache)" : "")
                    + " Pack: " + result.mcpack()
                    + " Mappings: " + result.mappings());
        } else {
            logger.warning("NexoBedrock: conversao automatica nao concluida: "
                    + result.message());
        }
    }
}
