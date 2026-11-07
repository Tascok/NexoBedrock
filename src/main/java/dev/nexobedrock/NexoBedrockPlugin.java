package dev.nexobedrock;

import dev.nexobedrock.command.NexoBedrockCommand;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.config.BedrockTextMode;
import dev.nexobedrock.conversion.ConversionService;
import dev.nexobedrock.listener.NexoPackListener;
import dev.nexobedrock.listener.BedrockHologramPacketListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicReference;

public final class NexoBedrockPlugin extends JavaPlugin {
    private final AtomicReference<PluginConfig> config = new AtomicReference<>();
    private ConversionService conversionService;

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
            new BedrockHologramPacketListener(this, prefix).register();
            getLogger().info("Hologramas RGB por pacote ativos para jogadores com prefixo Floodgate '"
                    + prefix + "'.");
        } else if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().warning("ProtocolLib nao encontrado; hologramas RGB por jogador Bedrock desativados.");
        }

        getLogger().info("NexoBedrock habilitado. Modo de mapping: "
                + config.get().mappingMode() + ", namespace: " + config.get().namespace());
    }

    @Override
    public void onDisable() {
        if (conversionService != null) {
            conversionService.close();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config.set(PluginConfig.from(getConfig()));
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
}
