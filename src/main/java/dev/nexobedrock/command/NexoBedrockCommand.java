package dev.nexobedrock.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.conversion.ConversionRequest;
import dev.nexobedrock.conversion.ConversionResult;
import dev.nexobedrock.report.ItemReport;
import dev.nexobedrock.report.Report;
import dev.nexobedrock.util.Json;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NexoBedrockCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§8[§bNexoBedrock§8] §r";

    private final NexoBedrockPlugin plugin;

    public NexoBedrockCommand(NexoBedrockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label,
                             String[] args) {
        if (!sender.hasPermission("nexobedrock.admin")) {
            sender.sendMessage(PREFIX + "§cVoce nao tem permissao.");
            return true;
        }
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "convert" -> convert(sender, args);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "report" -> report(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void convert(CommandSender sender, String[] args) {
        if (!plugin.getPluginConfig().enabled()) {
            sender.sendMessage(PREFIX + "§cNexoBedrock esta desabilitado na configuracao.");
            return;
        }
        boolean force = false;
        for (int index = 1; index < args.length; index++) {
            if ("--force".equalsIgnoreCase(args[index]) || "-f".equalsIgnoreCase(args[index])) {
                force = true;
            }
        }
        sender.sendMessage(PREFIX + "§7Convertendo o pack do Nexo em segundo plano..."
                + (force ? " (cache ignorado)" : ""));
        boolean forceCache = force;
        plugin.getConversionService().request(ConversionRequest.manual(forceCache), result -> {
            if (result.success()) {
                sender.sendMessage(PREFIX + "§a" + result.message()
                        + (result.fromCache() ? " §7(cache)" : ""));
                sender.sendMessage(PREFIX + "§7Pack: §f" + result.mcpack());
                sender.sendMessage(PREFIX + "§7Mappings: §f" + result.mappings());
                if (result.backupDir() != null) {
                    sender.sendMessage(PREFIX + "§7Backup: §f" + result.backupDir());
                }
                sender.sendMessage(PREFIX + "§7Tempo: §f" + result.durationMs() + " ms");
            } else {
                sender.sendMessage(PREFIX + "§c" + result.message());
                if (result.report() != null) {
                    sender.sendMessage(PREFIX + "§7Report: §f"
                            + plugin.getConversionService().latestReportPath());
                }
            }
        });
    }

    private void status(CommandSender sender) {
        PluginConfig config = plugin.getPluginConfig();
        ConversionResult last = plugin.getConversionService().lastResult();
        sender.sendMessage(PREFIX + "§bStatus");
        sender.sendMessage("§7- Habilitado: §f" + config.enabled()
                + " §7| Auto: §f" + config.autoConvert()
                + " §7| Rodando: §f" + plugin.getConversionService().isRunning());
        sender.sendMessage("§7- Modo: §f" + config.mappingMode()
                + " §7| Namespace: §f" + config.namespace()
                + " §7| 2D/3D: §f" + config.include2d() + "/" + config.include3d());
        sender.sendMessage("§7- Packs: §f" + config.geyserPacksFolder());
        sender.sendMessage("§7- Mappings: §f" + config.geyserMappingsFolder());
        if (last == null) {
            sender.sendMessage("§7- Ultima conversao: §fnunca");
            return;
        }
        if (last.report() != null) {
            Report report = last.report();
            sender.sendMessage("§7- Ultima: §f" + (last.success() ? "ok" : "falhou")
                    + " §7| itens: §f" + report.stats().converted()
                    + " §7(2D " + report.stats().flat() + ", 3D "
                    + report.stats().threeD() + ")"
                    + " §7| ignorados: §f" + report.stats().unsupported()
                    + " §7| falhas: §f" + report.stats().failed());
            sender.sendMessage("§7- Fonte: §f" + report.source());
            sender.sendMessage("§7- Tempo: §f" + report.durationMs() + " ms"
                    + (last.fromCache() ? " §7(cache)" : ""));
        } else {
            sender.sendMessage("§7- Ultima: §f" + last.message());
        }
        sender.sendMessage("§7- Report: §f" + plugin.getConversionService().latestReportPath());
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadPluginConfig();
            PluginConfig config = plugin.getPluginConfig();
            sender.sendMessage(PREFIX + "§aConfiguracao recarregada. Modo: §f"
                    + config.mappingMode() + "§a, namespace: §f" + config.namespace());
        } catch (Throwable throwable) {
            sender.sendMessage(PREFIX + "§cFalha ao recarregar: " + throwable.getMessage());
        }
    }

    private void report(CommandSender sender) {
        Path path = plugin.getConversionService().latestReportPath();
        if (!Files.isRegularFile(path)) {
            sender.sendMessage(PREFIX + "§cNenhum report encontrado. Rode /nexobedrock convert.");
            return;
        }

        Report report = readReport(path);
        if (report == null) {
            sender.sendMessage(PREFIX + "§cNao foi possivel ler " + path);
            return;
        }

        sender.sendMessage(PREFIX + "§bReport: §f" + path);
        sender.sendMessage("§7Gerado: §f" + report.generatedAt()
                + " §7| Fonte: §f" + report.source());
        sender.sendMessage("§7Total: §f" + report.stats().total()
                + " §7| Convertidos: §f" + report.stats().converted()
                + " §7(2D " + report.stats().flat() + ", 3D " + report.stats().threeD() + ")"
                + " §7| Ignorados: §f" + report.stats().unsupported()
                + " §7| Pulados: §f" + report.stats().skipped()
                + " §7| Falhas: §f" + report.stats().failed());

        Map<String, Integer> reasons = new java.util.LinkedHashMap<>();
        for (ItemReport item : report.items()) {
            if ("converted".equals(item.status())) {
                continue;
            }
            String reason = item.reason() == null ? "sem motivo" : item.reason();
            reasons.merge(item.status() + ": " + reason, 1, Integer::sum);
        }
        if (!reasons.isEmpty()) {
            sender.sendMessage("§7Motivos mais comuns:");
            reasons.entrySet().stream()
                    .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                    .limit(6)
                    .forEach(entry -> sender.sendMessage("§8- §7" + entry.getValue()
                            + "x §f" + entry.getKey()));
        }
        if (report.outputs() != null && !report.outputs().isEmpty()) {
            sender.sendMessage("§7Pack: §f" + report.outputs().getOrDefault("pack", "-"));
            sender.sendMessage("§7Mappings: §f"
                    + report.outputs().getOrDefault("mappings", "-"));
        }
    }

    private Report readReport(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            return Json.prettyGson().fromJson(reader, Report.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + "§7Uso:");
        sender.sendMessage("§8/§f" + label + " convert §7[--force] §8- converte o pack");
        sender.sendMessage("§8/§f" + label + " status §8- mostra o estado");
        sender.sendMessage("§8/§f" + label + " reload §8- recarrega a config");
        sender.sendMessage("§8/§f" + label + " report §8- resume o ultimo report");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        if (!sender.hasPermission("nexobedrock.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("convert", "status", "reload", "report"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && "convert".equalsIgnoreCase(args[0])) {
            return filter(List.of("--force"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
