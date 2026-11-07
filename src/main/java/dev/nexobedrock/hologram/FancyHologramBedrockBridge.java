package dev.nexobedrock.hologram;

import dev.nexobedrock.NexoBedrockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/** Provides per-Bedrock-player armor-stand renderings for FancyHolograms text holograms. */
public final class FancyHologramBedrockBridge {
    private static final double LINE_HEIGHT = 0.25;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;
    private final Map<Key, List<ArmorStand>> renderings = new HashMap<>();
    private final Map<UUID, Set<UUID>> hiddenDisplays = new HashMap<>();
    private final Set<String> missingGlyphWarnings = ConcurrentHashMap.newKeySet();
    private volatile Map<String, String> glyphRewrites = Map.of();
    private BukkitTask task;
    private boolean enabled;

    public FancyHologramBedrockBridge(NexoBedrockPlugin plugin, String bedrockNamePrefix) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix == null ? "." : bedrockNamePrefix;
    }

    public void start(boolean enabled) {
        setEnabled(enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            clearAll();
        } else {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSafely, 1L, 10L);
            refreshSafely();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setGlyphRewrites(Map<String, String> glyphRewrites) {
        this.glyphRewrites = glyphRewrites == null ? Map.of() : Map.copyOf(glyphRewrites);
        missingGlyphWarnings.clear();
    }

    /** Captures stable FancyHolograms lines on the server thread for the next Bedrock pack build. */
    public List<String> snapshotGlyphInputs() {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) return List.of();
        try {
            Object manager = hologramManager();
            if (manager == null) return List.of();
            Class<?> managerApi = Class.forName("de.oliver.fancyholograms.api.HologramManager",
                    true, manager.getClass().getClassLoader());
            Object result = managerApi.getMethod("getHolograms").invoke(manager);
            if (!(result instanceof Iterable<?> holograms)) return List.of();

            List<String> glyphInputs = new ArrayList<>();
            for (Object hologram : holograms) {
                ClassLoader loader = hologram.getClass().getClassLoader();
                Class<?> hologramApi = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram",
                        true, loader);
                Object data = hologramApi.getMethod("getData").invoke(hologram);
                if (data == null || !data.getClass().getSimpleName().equals("TextHologramData")) continue;
                Class<?> textDataApi = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData",
                        true, loader);
                Object rawLines = textDataApi.getMethod("getText").invoke(data);
                if (!(rawLines instanceof Iterable<?> lines)) continue;
                for (Object rawLine : lines) {
                    String value = String.valueOf(rawLine);
                    Component component = value.indexOf('\u00a7') >= 0
                            ? LEGACY.deserialize(value)
                            : MiniMessage.miniMessage().deserialize(value);
                    String formatted = LEGACY.serialize(component);
                    if (!formatted.isBlank()) glyphInputs.add(formatted);
                }
            }
            return List.copyOf(glyphInputs);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Nao foi possivel incluir textos do FancyHolograms no pack Bedrock.", exception);
            return List.of();
        }
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        enabled = false;
        clearAll();
    }

    private void refreshSafely() {
        if (!enabled || !Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            if (!renderings.isEmpty() || !hiddenDisplays.isEmpty()) clearAll();
            return;
        }

        try {
            Object manager = hologramManager();
            if (manager == null) return;
            Class<?> managerApi = Class.forName("de.oliver.fancyholograms.api.HologramManager",
                    true, manager.getClass().getClassLoader());
            Method getHolograms = managerApi.getMethod("getHolograms");
            Object result = getHolograms.invoke(manager);
            if (!(result instanceof Iterable<?> holograms)) return;

            Set<Key> active = new HashSet<>();
            Map<UUID, Set<UUID>> currentDisplays = new HashMap<>();
            for (Object hologram : holograms) {
                renderHologram(hologram, active, currentDisplays);
            }
            removeStale(active);
            restoreNoLongerHidden(currentDisplays);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Falha ao sincronizar hologramas do FancyHolograms para Bedrock.", exception);
        }
    }

    private Object hologramManager() throws ReflectiveOperationException {
        Plugin fancy = Bukkit.getPluginManager().getPlugin("FancyHolograms");
        if (fancy == null || !fancy.isEnabled()) return null;
        Class<?> api = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin",
                true, fancy.getClass().getClassLoader());
        return api.getMethod("getHologramManager").invoke(fancy);
    }

    private void renderHologram(Object hologram, Set<Key> active,
                                Map<UUID, Set<UUID>> currentDisplays)
            throws ReflectiveOperationException {
        ClassLoader loader = hologram.getClass().getClassLoader();
        Class<?> hologramApi = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram",
                true, loader);
        Object data = hologramApi.getMethod("getData").invoke(hologram);
        if (data == null || !data.getClass().getSimpleName().equals("TextHologramData")) return;

        Object displayValue = hologramApi.getMethod("getDisplayEntity").invoke(hologram);
        if (!(displayValue instanceof TextDisplay display) || !display.isValid()) return;
        String hologramName = String.valueOf(hologramApi.getMethod("getName").invoke(hologram));

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!isBedrock(viewer) || !canView(hologram, viewer)) continue;
            currentDisplays.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>())
                    .add(display.getUniqueId());
            Set<UUID> hiddenForViewer = hiddenDisplays.computeIfAbsent(
                    viewer.getUniqueId(), ignored -> new HashSet<>());
            if (hiddenForViewer.add(display.getUniqueId())) {
                viewer.hideEntity(plugin, display);
            }

            Component shown = getShownText(hologram, data, viewer);
            List<Component> lines = splitLines(shown);
            Key key = new Key(viewer.getUniqueId(), hologramName);
            active.add(key);
            updateRendering(key, viewer, display, lines);
        }
    }

    private boolean isBedrock(Player player) {
        return !bedrockNamePrefix.isEmpty() && player.getName().startsWith(bedrockNamePrefix);
    }

    private boolean canView(Object hologram, Player player) {
        try {
            Class<?> hologramApi = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram",
                    true, hologram.getClass().getClassLoader());
            Object result = hologramApi
                    .getMethod("meetsVisibilityConditions", Player.class).invoke(hologram, player);
            return !(result instanceof Boolean visible) || visible;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private Component getShownText(Object hologram, Object data, Player player)
            throws ReflectiveOperationException {
        try {
            Class<?> hologramApi = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram",
                    true, hologram.getClass().getClassLoader());
            Object shown = hologramApi.getMethod("getShownText", Player.class)
                    .invoke(hologram, player);
            if (shown instanceof Component component) return component;
        } catch (NoSuchMethodException ignored) {
            // Fall through for FancyHolograms versions without per-player text API.
        }

        Class<?> textDataApi = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData",
                true, data.getClass().getClassLoader());
        Object rawLines = textDataApi.getMethod("getText").invoke(data);
        if (!(rawLines instanceof Iterable<?> iterable)) return Component.empty();
        List<Component> components = new ArrayList<>();
        for (Object line : iterable) {
            components.add(MiniMessage.miniMessage().deserialize(String.valueOf(line)));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), components);
    }

    private List<Component> splitLines(Component component) {
        String legacy = LEGACY.serialize(component);
        String[] rawLines = legacy.split("\\n", -1);
        List<Component> lines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            String glyphs = glyphRewrites.get(line);
            if (glyphs != null) {
                lines.add(Component.text(glyphs));
            } else {
                if (line.indexOf('\u00a7') >= 0 && missingGlyphWarnings.add(line)) {
                    plugin.getLogger().warning("Texto de holograma ausente no atlas Bedrock; "
                            + "rode /nexobedrock convert --force para atualizar o pack.");
                }
                lines.add(LEGACY.deserialize(line));
            }
        }
        return lines;
    }

    private void updateRendering(Key key, Player viewer, TextDisplay source, List<Component> lines) {
        List<ArmorStand> stands = renderings.computeIfAbsent(key, ignored -> new ArrayList<>());
        while (stands.size() > lines.size()) {
            ArmorStand removed = stands.remove(stands.size() - 1);
            removed.remove();
        }

        Location base = source.getLocation().clone();
        if (source instanceof Display display) {
            var translation = display.getTransformation().getTranslation();
            base.add(translation.x(), translation.y(), translation.z());
        }
        for (int index = 0; index < lines.size(); index++) {
            ArmorStand stand;
            if (index >= stands.size() || !stands.get(index).isValid()) {
                Location lineLocation = base.clone().subtract(0, LINE_HEIGHT * index, 0);
                stand = source.getWorld().spawn(lineLocation, ArmorStand.class, entity -> {
                    entity.setInvisible(true);
                    entity.setMarker(true);
                    entity.setGravity(false);
                    entity.setPersistent(false);
                    entity.setCanPickupItems(false);
                    entity.setCustomNameVisible(false);
                });
                hideFromOtherPlayers(stand, viewer);
                if (index >= stands.size()) stands.add(stand);
                else stands.set(index, stand);
            } else {
                stand = stands.get(index);
                stand.teleport(base.clone().subtract(0, LINE_HEIGHT * index, 0));
                stand.customName(lines.get(index));
                stand.setCustomNameVisible(true);
            }
            hideFromOtherPlayers(stand, viewer);
            stand.customName(lines.get(index));
            stand.setCustomNameVisible(true);
        }
    }

    private void hideFromOtherPlayers(Entity entity, Player visibleTo) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(visibleTo.getUniqueId()) && player.canSee(entity)) {
                player.hideEntity(plugin, entity);
            }
        }
    }

    private void removeStale(Set<Key> active) {
        renderings.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey())) return false;
            entry.getValue().forEach(Entity::remove);
            return true;
        });
    }

    private void restoreNoLongerHidden(Map<UUID, Set<UUID>> currentDisplays) {
        for (Map.Entry<UUID, Set<UUID>> entry : hiddenDisplays.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                entry.getValue().clear();
                continue;
            }
            Set<UUID> desired = currentDisplays.getOrDefault(entry.getKey(), Set.of());
            entry.getValue().removeIf(entityId -> {
                if (desired.contains(entityId)) return false;
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) player.showEntity(plugin, entity);
                return true;
            });
        }
        hiddenDisplays.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                || Bukkit.getPlayer(entry.getKey()) == null);
    }

    private void clearAll() {
        renderings.values().forEach(stands -> stands.forEach(Entity::remove));
        renderings.clear();
        for (Map.Entry<UUID, Set<UUID>> entry : hiddenDisplays.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            for (UUID entityId : entry.getValue()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) player.showEntity(plugin, entity);
            }
        }
        hiddenDisplays.clear();
    }

    private record Key(UUID viewer, String hologram) { }
}
