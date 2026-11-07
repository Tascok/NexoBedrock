package dev.nexobedrock.util;

import org.bukkit.plugin.Plugin;

import java.nio.file.Path;

public final class PathUtil {
    private PathUtil() {
    }

    public static Path serverRoot(Plugin plugin) {
        Path data = plugin.getDataFolder().toPath().toAbsolutePath();
        Path plugins = data.getParent();
        if (plugins != null && plugins.getParent() != null) {
            return plugins.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    public static Path resolve(Plugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return serverRoot(plugin);
        }
        Path path = Path.of(raw);
        return path.isAbsolute() ? path.normalize() : serverRoot(plugin).resolve(path).normalize();
    }
}
