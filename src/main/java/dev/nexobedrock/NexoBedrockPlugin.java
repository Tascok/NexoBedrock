package dev.nexobedrock;

import dev.nexobedrock.command.NexoBedrockCommand;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.config.BedrockTextMode;
import dev.nexobedrock.conversion.ConversionService;
import dev.nexobedrock.listener.NexoPackListener;
import dev.nexobedrock.listener.BedrockItemNamePacketListener;
import dev.nexobedrock.hologram.FancyHologramBedrockBridge;
import dev.nexobedrock.listener.BedrockGuiGlyphPacketListener;
import dev.nexobedrock.listener.FurnitureProxyBridge;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicReference;

public final class NexoBedrockPlugin extends JavaPlugin {
    private final AtomicReference<PluginConfig> config = new AtomicReference<>();
    private ConversionService conversionService;
    private FancyHologramBedrockBridge hologramBridge;
    private FurnitureProxyBridge furnitureProxyBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        conversionService = new ConversionService(this, config::get,
                getDescription().getVersion());

        PluginCommand command = getCommand("nexobedrock");
        if (command == null) {
            getLogger().severe("Comando /nexobedrock nao registrado no plugin.yml.");
        } else {
            NexoBedrockCommand executor = new NexoBedrockCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        try {
            getServer().getPluginManager().registerEvents(
                    new NexoPackListener(this, conversionService, config::get), this);
            getLogger().info(config.get().autoConvert()
                    ? "Conversao automatica ativa (eventos do Nexo registrados)."
                    : "Eventos do Nexo registrados; conversao automatica desativada na config.");
        } catch (Throwable throwable) {
            getLogger().warning("NexoBedrock: nao foi possivel registrar os eventos do Nexo: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        if (config.get().bedrockTextFormatting()
                && config.get().bedrockTextMode() == BedrockTextMode.LEGACY
                && getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            String prefix = getConfig().getString("holograms.bedrock-name-prefix", ".");
            new BedrockItemNamePacketListener(this, prefix).register();
            getLogger().info("Nomes RGB dos itens por pacote ativos para jogadores com prefixo Floodgate '"
                    + prefix + "'.");
        } else if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().warning("ProtocolLib nao encontrado; reescrita RGB dos nomes de itens desativada.");
        }

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            new BedrockGuiGlyphPacketListener(this,
                    getConfig().getString("holograms.bedrock-name-prefix", ".")).register();
            getLogger().info("Reescrita de glyphs de títulos de inventário Bedrock registrada.");
        } else {
            getLogger().warning("ProtocolLib nao encontrado; glyphs de GUI Bedrock nao serao remapeados.");
        }

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                furnitureProxyBridge = new FurnitureProxyBridge(this,
                        getConfig().getString("holograms.bedrock-name-prefix", "."));
                getLogger().info("Ponte de proxies de furniture registrada; requer a extensão Geyser compatível e catálogo convertido.");
            } catch (Exception exception) {
                getLogger().warning("Ponte de furniture desativada: " + exception.getMessage());
            }
        } else {
            getLogger().info("Ponte de furniture não iniciada: requer ProtocolLib; para Geyser remoto, instale a extensão no proxy.");
        }

        if (getServer().getPluginManager().isPluginEnabled("FancyHolograms")) {
            hologramBridge = new FancyHologramBedrockBridge(this,
                    getConfig().getString("holograms.bedrock-name-prefix", "."));
            hologramBridge.start(getConfig().getBoolean("holograms.enabled", false));
            getLogger().info("Integração Bedrock/FancyHolograms: "
                    + (hologramBridge.isEnabled() ? "ativa" : "desativada") + ".");
        } else if (getConfig().getBoolean("holograms.enabled", false)) {
            getLogger().warning("holograms.enabled está ativo, mas FancyHolograms não foi encontrado.");
        }

        getLogger().info("NexoBedrock habilitado. Modo de mapping: "
                + config.get().mappingMode() + ", namespace: " + config.get().namespace());
    }

    @Override
    public void onDisable() {
        if (furnitureProxyBridge != null) {
            furnitureProxyBridge.close();
            furnitureProxyBridge = null;
        }
        if (hologramBridge != null) {
            hologramBridge.close();
            hologramBridge = null;
        }
        if (conversionService != null) {
            conversionService.close();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config.set(PluginConfig.from(getConfig()));
        if (hologramBridge != null) {
            hologramBridge.setEnabled(getConfig().getBoolean("holograms.enabled", false));
        }
    }

    public PluginConfig getPluginConfig() {
        PluginConfig current = config.get();
        if (current == null) {
            reloadPluginConfig();
            current = config.get();
        }
        return current;
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public FancyHologramBedrockBridge getHologramBridge() {
        return hologramBridge;
    }
}
