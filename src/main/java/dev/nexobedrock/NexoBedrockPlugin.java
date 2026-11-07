package dev.nexobedrock;

import dev.nexobedrock.command.NexoBedrockCommand;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.config.BedrockTextMode;
import dev.nexobedrock.conversion.ConversionService;
import dev.nexobedrock.listener.NexoPackListener;
import dev.nexobedrock.listener.BedrockItemNamePacketListener;
import dev.nexobedrock.listener.BedrockChatGradientListener;
import dev.nexobedrock.hologram.FancyHologramBedrockBridge;
import dev.nexobedrock.bedrock.GlyphTextMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class NexoBedrockPlugin extends JavaPlugin {
    private final AtomicReference<PluginConfig> config = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> glyphTextMap = new AtomicReference<>(Map.of());
    private ConversionService conversionService;
    private FancyHologramBedrockBridge hologramBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();
        loadGlyphTextMap();

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
                && config.get().bedrockTextMode() == BedrockTextMode.LEGACY) {
            String prefix = getConfig().getString("holograms.bedrock-name-prefix", ".");
            getServer().getPluginManager().registerEvents(
                    new BedrockChatGradientListener(this, prefix), this);
            if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
                new BedrockItemNamePacketListener(this, prefix).register();
            } else {
                getLogger().warning("ProtocolLib ausente; reescrita RGB dos nomes de itens desativada.");
            }
            getLogger().info("Reescrita de chat RGB por glyphs ativa para prefixo Floodgate '"
                    + prefix + "'.");
        }

        if (getServer().getPluginManager().isPluginEnabled("FancyHolograms")) {
            hologramBridge = new FancyHologramBedrockBridge(this,
                    getConfig().getString("holograms.bedrock-name-prefix", "."));
            hologramBridge.setGlyphRewrites(glyphTextMap.get());
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
        getConfig().options().copyDefaults(true);
        saveConfig();
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

    public Map<String, String> getGlyphTextMap() {
        return glyphTextMap.get();
    }

    public void setGlyphTextMap(Map<String, String> glyphs) {
        Map<String, String> safe = glyphs == null ? Map.of() : Map.copyOf(glyphs);
        glyphTextMap.set(safe);
        Runnable apply = () -> {
            FancyHologramBedrockBridge bridge = hologramBridge;
            if (bridge != null) bridge.setGlyphRewrites(safe);
            String prefix = getConfig().getString("holograms.bedrock-name-prefix", ".");
            if (prefix == null || prefix.isEmpty()) return;
            getServer().getOnlinePlayers().stream()
                    .filter(player -> player.getName().startsWith(prefix))
                    .forEach(org.bukkit.entity.Player::updateInventory);
        };
        if (getServer().isPrimaryThread()) apply.run();
        else getServer().getScheduler().runTask(this, apply);
    }

    private void loadGlyphTextMap() {
        Path path = getDataFolder().toPath().resolve("glyph-map.json");
        if (!Files.isRegularFile(path)) return;
        try {
            setGlyphTextMap(GlyphTextMap.decode(Files.readAllBytes(path)));
        } catch (Exception exception) {
            getLogger().warning("Mapa de glyphs Bedrock invalido; textos serao exibidos sem reescrita.");
        }
    }
}
