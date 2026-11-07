package dev.nexobedrock.pack;

import dev.nexobedrock.config.MappingMode;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Snapshot dos itens do Nexo. Precisa ser chamado na main thread porque a API
 * do Nexo expoe objetos do Bukkit. O snapshot e imutavel e pode ser usado
 * pela thread de conversao.
 */
public record NexoItemIndex(List<JavaItemEntry> entries, List<String> warnings) {
    public static NexoItemIndex empty() {
        return new NexoItemIndex(List.of(), List.of());
    }

    public static NexoItemIndex capture() {
        List<JavaItemEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            Class.forName("com.nexomc.nexo.api.NexoItems");
        } catch (Throwable throwable) {
            warnings.add("API do Nexo indisponivel: " + throwable.getClass().getSimpleName());
            return new NexoItemIndex(entries, warnings);
        }

        try {
            for (com.nexomc.nexo.items.ItemBuilder item : com.nexomc.nexo.api.NexoItems.items()) {
                try {
                    JavaItemEntry entry = toEntry(item);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Throwable throwable) {
                    warnings.add("Falha ao ler item do Nexo: " + throwable.getMessage());
                }
            }
        } catch (Throwable throwable) {
            warnings.add("Falha ao enumerar itens do Nexo: " + throwable.getMessage());
        }

        return new NexoItemIndex(List.copyOf(entries), List.copyOf(warnings));
    }

    private static JavaItemEntry toEntry(com.nexomc.nexo.items.ItemBuilder item) {
        String id = com.nexomc.nexo.api.NexoItems.idFromItem(item);
        if (id == null || id.isBlank()) {
            return null;
        }
        org.bukkit.Material material = item.getType();
        if (material == null || material.isAir()) {
            return null;
        }
        com.nexomc.nexo.items.NexoMeta meta = item.getNexoMeta();
        if (meta == null) {
            return null;
        }

        String javaItem = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        Integer customModelData = meta.getCustomModelData();
        Key itemModelKey = item.getItemModel();
        String itemModel = itemModelKey == null ? null : itemModelKey.asString();
        Key modelKey = meta.getModel();
        String modelPath = modelKey == null ? null : modelKey.asString();

        MappingMode mode;
        if (customModelData != null) {
            mode = MappingMode.LEGACY;
        } else if (itemModel != null) {
            mode = MappingMode.MODERN;
        } else {
            mode = MappingMode.LEGACY;
        }

        Set<String> flags = new LinkedHashSet<>();
        String materialName = material.name().toUpperCase(Locale.ROOT);
        if (materialName.contains("HELMET") || materialName.contains("CHESTPLATE")
                || materialName.contains("LEGGINGS") || materialName.contains("BOOTS")
                || materialName.equals("ELYTRA") || materialName.equals("TURTLE_HELMET")) {
            flags.add("armor");
        }
        try {
            if (com.nexomc.nexo.api.NexoItems.hasMechanic(id, "furniture")) {
                flags.add("furniture");
            }
        } catch (Throwable ignored) {
            // Mechanic opcional; nao impede a conversao do item.
        }

        String displayName = id;
        Component itemName = item.getItemName();
        if (itemName != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(itemName);
            if (!plain.isBlank()) {
                displayName = plain;
            }
        }

        return new JavaItemEntry(id, javaItem, mode, customModelData, itemModel, modelPath,
                displayName, Set.copyOf(flags), "nexo");
    }
}
