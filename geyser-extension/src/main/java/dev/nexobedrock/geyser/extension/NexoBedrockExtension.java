package dev.nexobedrock.geyser.extension;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional Geyser-side runtime bridge for explicitly configured entity types. */
public final class NexoBedrockExtension implements org.geysermc.geyser.api.extension.Extension {
    private final Map<String, CustomEntityDefinition> definitions = new LinkedHashMap<>();
    private final Map<Integer, String> furnitureMappings = new LinkedHashMap<>();
    private final Map<Integer, CustomEntityDefinition> furnitureDefinitions = new LinkedHashMap<>();
    private Map<String, String> mappings = Map.of();

    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        Path config = dataFolder().resolve("entity-mappings.properties");
        try {
            Files.createDirectories(config.getParent());
            if (Files.notExists(config)) {
                try (InputStream defaults = getClass().getResourceAsStream(
                        "/entity-mappings.properties")) {
                    if (defaults == null) {
                        throw new IOException("Missing bundled entity-mappings.properties");
                    }
                    Files.copy(defaults, config);
                }
            }
            try (InputStream input = Files.newInputStream(config)) {
                mappings = EntityMappingConfig.read(input);
            }
            Path furnitureConfig = dataFolder().resolve("furniture-mappings.properties");
            if (Files.notExists(furnitureConfig)) {
                try (InputStream defaults = getClass().getResourceAsStream(
                        "/furniture-mappings.properties")) {
                    if (defaults == null) {
                        throw new IOException("Missing bundled furniture-mappings.properties");
                    }
                    Files.copy(defaults, furnitureConfig);
                }
            }
            try (InputStream input = Files.newInputStream(furnitureConfig)) {
                furnitureMappings.clear();
                furnitureMappings.putAll(FurnitureMappingConfig.read(input));
            }
            logger().info("Loaded " + mappings.size()
                    + " static Java-to-Bedrock entity replacements.");
            logger().info("Loaded " + furnitureMappings.size()
                    + " Nexo furniture proxy entity mappings.");
            if (!mappings.isEmpty()) {
                logger().warning("Static replacements affect every spawn of the configured Java entity type; "
                        + "ModelEngine blueprint and ItemDisplay furniture synchronization is not enabled yet.");
            }
        } catch (IOException exception) {
            mappings = Map.of();
            logger().error("Could not load entity mappings; replacements are disabled.", exception);
        }
    }

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        definitions.clear();
        furnitureDefinitions.clear();
        for (String bedrockId : mappings.values()) {
            try {
                CustomEntityDefinition definition = CustomEntityDefinition.of(
                        Identifier.of(bedrockId));
                event.register(definition);
                definitions.put(bedrockId, definition);
            } catch (RuntimeException exception) {
                logger().error("Could not register Bedrock entity " + bedrockId, exception);
            }
        }
        for (Map.Entry<Integer, String> entry : furnitureMappings.entrySet()) {
            try {
                CustomEntityDefinition definition = CustomEntityDefinition.of(
                        Identifier.of(entry.getValue()));
                event.register(definition);
                furnitureDefinitions.put(entry.getKey(), definition);
            } catch (RuntimeException exception) {
                logger().error("Could not register furniture entity " + entry.getValue(), exception);
            }
        }
    }

    @Subscribe
    public void onSpawnEntity(ServerSpawnEntityEvent event) {
        if (FurnitureEntityId.isFurniture(event.uuid())) {
            CustomEntityDefinition furniture = furnitureDefinitions.get(
                    FurnitureEntityId.modelIndex(event.uuid()));
            if (furniture != null) {
                event.definition(furniture);
                return;
            }
        }
        String bedrockId = mappings.get(event.entityType().identifier().toString());
        if (bedrockId == null) {
            return;
        }
        CustomEntityDefinition definition = definitions.get(bedrockId);
        if (definition != null) {
            event.definition(definition);
        }
    }
}
