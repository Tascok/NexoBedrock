package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.geyser.FurnitureMappingPublisher;
import dev.nexobedrock.util.PathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipFile;

/**
 * Creates Java proxy Armor Stands for Nexo furniture. ProtocolLib suppresses the
 * proxies for Java clients and marks their UUID for the Geyser extension on Bedrock.
 */
public final class FurnitureProxyBridge implements Listener, AutoCloseable {
    private static final String CATALOG_ENTRY = "nexobedrock/furniture-catalog.json";
    private final NexoBedrockPlugin plugin;
    private final String bedrockPrefix;
    private final NamespacedKey furnitureIdKey;
    private final NamespacedKey proxyKey;
    private final NamespacedKey indexKey;
    private final Map<String, Integer> indicesByFurniture = new HashMap<>();
    private final Map<UUID, ArmorStand> proxiesBySource = new HashMap<>();
    private final Map<Integer, UUID> sourceByProxyEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> modelIndexByProxyUuid = new ConcurrentHashMap<>();
    private final FurnitureMappingPublisher mappingPublisher = new FurnitureMappingPublisher();
    private PacketAdapter packetListener;
    private long catalogModified = Long.MIN_VALUE;
    private final BukkitTask reconcileTask;
    private final BukkitTask updateTask;
    private boolean closed;

    public FurnitureProxyBridge(NexoBedrockPlugin plugin, String bedrockPrefix) throws IOException {
        this.plugin = plugin;
        this.bedrockPrefix = bedrockPrefix;
        this.furnitureIdKey = new NamespacedKey(plugin, "furniture_id");
        this.proxyKey = new NamespacedKey(plugin, "furniture_proxy");
        this.indexKey = new NamespacedKey(plugin, "furniture_model_index");
        loadCatalog();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPackets();
        reconcileTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileLoadedWorlds,
                20L, 20L);
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTrackedProxies,
                2L, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(NexoFurniturePlaceEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> attach(event.getBaseEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(NexoFurnitureBreakEvent event) {
        UUID sourceId = event.getBaseEntity().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemDisplay source = findSource(sourceId);
            if (source == null || !source.isValid() || !NexoFurniture.isFurniture(source)) {
                removeProxy(sourceId);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof ItemDisplay display) {
            removeProxy(display.getUniqueId());
        } else if (event.getEntity() instanceof ArmorStand stand
                && stand.getPersistentDataContainer().has(proxyKey, PersistentDataType.BYTE)) {
            sourceByProxyEntityId.remove(stand.getEntityId());
            modelIndexByProxyUuid.remove(stand.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isBedrock(event.getPlayer().getName())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ArmorStand proxy : proxiesBySource.values()) {
                if (proxy.isValid()) event.getPlayer().hideEntity(plugin, proxy);
            }
        });
    }

    private void registerPackets() {
        List<PacketType> tracked = List.of(PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.SPAWN_ENTITY_LIVING,
                PacketType.Play.Server.ENTITY_TELEPORT,
                PacketType.Play.Server.REL_ENTITY_MOVE,
                PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                PacketType.Play.Server.ENTITY_MOVE_LOOK,
                PacketType.Play.Server.ENTITY_LOOK,
                PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_EQUIPMENT,
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Server.ENTITY_DESTROY);
        packetListener = new PacketAdapter(plugin,
                ListenerPriority.HIGHEST, tracked) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (closed) return;
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY
                        || event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
                    rewriteProxySpawn(event);
                    return;
                }
                try {
                    filterProxyUpdate(event);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.FINE,
                            "Could not inspect proxy update packet " + event.getPacketType(), exception);
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);
    }

    private void rewriteProxySpawn(PacketEvent event) {
        UUID packetUuid = event.getPacket().getUUIDs().readSafely(0);
        if (packetUuid == null) return;
        Integer modelIndex = modelIndexByProxyUuid.get(packetUuid);
        if (modelIndex == null) {
            return;
        }
        Integer entityId = event.getPacket().getIntegers().readSafely(0);
        if (entityId != null) sourceByProxyEntityId.put(entityId, packetUuid);
        if (!isBedrock(event.getPlayer().getName())) {
            event.setCancelled(true);
            return;
        }
        event.getPacket().getUUIDs().write(0,
                FurnitureProxyUuid.encode(modelIndex, packetUuid));
    }

    private void filterProxyUpdate(PacketEvent event) {
        Set<Integer> entityIds = entityIds(event);
        for (int entityId : entityIds) {
            UUID proxyId = sourceByProxyEntityId.get(entityId);
            if (proxyId == null) continue;
            if (!isBedrock(event.getPlayer().getName())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private Set<Integer> entityIds(PacketEvent event) {
        Set<Integer> result = new HashSet<>();
        var packet = event.getPacket();
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_DESTROY) {
            var values = packet.getIntegerArrays().readSafely(0);
            if (values != null) for (int value : values) result.add(value);
        } else {
            Integer entityId = packet.getIntegers().readSafely(0);
            if (entityId != null) result.add(entityId);
        }
        return result;
    }

    private void loadCatalog() throws IOException {
        Path pack = PathUtil.resolve(plugin, plugin.getPluginConfig().geyserPacksFolder())
                .resolve(plugin.getPluginConfig().packName() + ".mcpack");
        if (!Files.isRegularFile(pack)) {
            if (!indicesByFurniture.isEmpty()) {
                indicesByFurniture.clear();
                for (UUID sourceId : new ArrayList<>(proxiesBySource.keySet())) removeProxy(sourceId);
            }
            return;
        }
        long modified = Files.getLastModifiedTime(pack).toMillis();
        if (modified == catalogModified) return;
        Map<String, Integer> loaded = new HashMap<>();
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(CATALOG_ENTRY);
            if (entry == null) {
                if (!indicesByFurniture.isEmpty()) {
                    for (UUID sourceId : new ArrayList<>(proxiesBySource.keySet())) removeProxy(sourceId);
                    indicesByFurniture.clear();
                }
                catalogModified = modified;
                return;
            }
            publishExtensionMappings(zip);
            JsonObject root = JsonParser.parseString(new String(
                    zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject furniture = root.getAsJsonObject("furniture");
            if (furniture == null) return;
            for (Map.Entry<String, com.google.gson.JsonElement> item : furniture.entrySet()) {
                JsonObject definition = item.getValue().getAsJsonObject();
                if (definition.has("model_index")) {
                    loaded.put(item.getKey(),
                            definition.get("model_index").getAsInt());
                }
            }
            if (!loaded.equals(indicesByFurniture)) {
                for (UUID sourceId : new ArrayList<>(proxiesBySource.keySet())) removeProxy(sourceId);
                indicesByFurniture.clear();
                indicesByFurniture.putAll(loaded);
            }
            catalogModified = modified;
        } catch (RuntimeException exception) {
            throw new IOException("Catálogo de furniture inválido em " + pack, exception);
        }
    }

    private void publishExtensionMappings(ZipFile zip) throws IOException {
        String configuredFolder = plugin.getPluginConfig().geyserExtensionDataFolder();
        if (configuredFolder == null || configuredFolder.isBlank()) return;
        var mappingEntry = zip.getEntry("nexobedrock/furniture-mappings.properties");
        if (mappingEntry == null) return;
        byte[] mappings = zip.getInputStream(mappingEntry).readAllBytes();
        Path destination = PathUtil.resolve(plugin, configuredFolder);
        Path backupFolder = plugin.getDataFolder().toPath().resolve("backups/extensions");
        FurnitureMappingPublisher.Result result = mappingPublisher.publish(mappings,
                destination, backupFolder);
        if (result.changed()) {
            plugin.getLogger().warning("Furniture mapping publicado em " + result.target()
                    + "; reinicie o Geyser para a extensão carregar o mapping."
                    + (result.backup() == null ? "" : " Backup: " + result.backup()));
        }
    }

    private void reconcileLoadedWorlds() {
        if (closed) return;
        try {
            loadCatalog();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh Nexo furniture catalog", exception);
            return;
        }
        if (indicesByFurniture.isEmpty()) return;
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay display && NexoFurniture.isFurniture(display)) {
                    seen.add(display.getUniqueId());
                    attach(display);
                }
            }
        }
        for (UUID sourceId : new ArrayList<>(proxiesBySource.keySet())) {
            if (!seen.contains(sourceId)) removeProxy(sourceId);
        }
    }

    private void updateTrackedProxies() {
        if (closed) return;
        for (Map.Entry<UUID, ArmorStand> entry : new ArrayList<>(proxiesBySource.entrySet())) {
            ItemDisplay source = findSource(entry.getKey());
            ArmorStand proxy = entry.getValue();
            if (source == null || !source.isValid() || !proxy.isValid()) {
                removeProxy(entry.getKey());
            } else {
                updateProxy(source, proxy);
            }
        }
    }

    private void attach(ItemDisplay source) {
        if (closed || !source.isValid() || !NexoFurniture.isFurniture(source)) return;
        String furnitureId;
        try {
            furnitureId = NexoItems.idFromItem(NexoFurniture.furnitureItem(source));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Could not identify Nexo furniture display", throwable);
            return;
        }
        Integer index = indicesByFurniture.get(furnitureId);
        if (index == null) return;
        ArmorStand existing = proxiesBySource.get(source.getUniqueId());
        if (existing != null && existing.isValid()) {
            updateProxy(source, existing);
            return;
        }
        Location location = source.getLocation();
        ArmorStand proxy = source.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCanPickupItems(false);
            stand.getPersistentDataContainer().set(proxyKey, PersistentDataType.BYTE, (byte) 1);
            stand.getPersistentDataContainer().set(furnitureIdKey, PersistentDataType.STRING,
                    furnitureId);
            stand.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, index);
            modelIndexByProxyUuid.put(stand.getUniqueId(), index);
        });
        proxiesBySource.put(source.getUniqueId(), proxy);
        sourceByProxyEntityId.put(proxy.getEntityId(), proxy.getUniqueId());
        updateProxy(source, proxy);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (!isBedrock(player.getName())) player.hideEntity(plugin, proxy);
        }
    }

    private void updateProxy(ItemDisplay source, ArmorStand proxy) {
        Location target = source.getLocation();
        Location current = proxy.getLocation();
        if (!current.getWorld().equals(target.getWorld()) || current.distanceSquared(target) > 1.0e-6
                || Math.abs(current.getYaw() - target.getYaw()) > 0.5f
                || Math.abs(current.getPitch() - target.getPitch()) > 0.5f) {
            proxy.teleport(target);
        }
    }

    private ItemDisplay findSource(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        return entity instanceof ItemDisplay display ? display : null;
    }

    private void removeProxy(UUID sourceId) {
        ArmorStand proxy = proxiesBySource.remove(sourceId);
        if (proxy == null) return;
        sourceByProxyEntityId.remove(proxy.getEntityId());
        modelIndexByProxyUuid.remove(proxy.getUniqueId());
        if (proxy.isValid()) proxy.remove();
    }

    private boolean isBedrock(String playerName) {
        return bedrockPrefix != null && !bedrockPrefix.isEmpty()
                && playerName.startsWith(bedrockPrefix);
    }

    @Override
    public void close() {
        closed = true;
        reconcileTask.cancel();
        updateTask.cancel();
        for (UUID sourceId : new ArrayList<>(proxiesBySource.keySet())) removeProxy(sourceId);
        if (packetListener != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
        }
    }
}
