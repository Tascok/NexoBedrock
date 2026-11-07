package dev.nexobedrock.pack;

import dev.nexobedrock.util.Names;
import dev.nexobedrock.util.Json;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Explicit CustomArmor layers from Nexo's item configuration (not the GUI icon). */
public final class NexoArmorIndex {
    public record Armor(String slot, String textureKey, String assetId, String iconKey) {}

    public Map<String, Armor> read(Path itemsDirectory) throws IOException {
        if (!Files.isDirectory(itemsDirectory)) return Map.of();
        Map<String, Armor> result = new HashMap<>();
        try (Stream<Path> files = Files.walk(itemsDirectory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                for (String id : yaml.getKeys(false)) {
                    String material = yaml.getString(id + ".material", "");
                    String slot = slot(material);
                    if (slot == null || !yaml.isConfigurationSection(id + ".CustomArmor")) continue;
                    String layer = yaml.getString(id + ".CustomArmor."
                            + ("legs".equals(slot) ? "layer2" : "layer1"));
                    String key = layer == null ? null : Names.normalizeKey(layer, "minecraft");
                    String asset = yaml.getString(id + ".Components.equippable.asset_id");
                    String icon = yaml.getString(id + ".Pack.texture");
                    if (key != null) result.put(id, new Armor(slot, key, asset,
                            Names.normalizeKey(icon, "minecraft")));
                }
            }
        }
        return Map.copyOf(result);
    }

    public String texture(JavaPack pack, Armor armor) throws IOException {
        if (pack.texture(armor.textureKey()) != null) return armor.textureKey();
        String asset = Names.normalizeKey(armor.assetId(), "nexo");
        if (asset == null || pack.source() == null || !pack.source().endsWith(".zip")) return null;
        String namespace = Names.namespaceOf(asset);
        try (ZipFile zip = new ZipFile(pack.source())) {
            var file = zip.getEntry("assets/" + namespace + "/equipment/" + Names.pathOf(asset) + ".json");
            if (file == null) return null;
            JsonObject root = Json.parseObject(zip.getInputStream(file).readAllBytes());
            String layer = "legs".equals(armor.slot()) ? "humanoid_leggings" : "humanoid";
            String texture = root.getAsJsonObject("layers").getAsJsonArray(layer)
                    .get(0).getAsJsonObject().get("texture").getAsString();
            String key = Names.normalizeKey(texture, namespace);
            String resolved = Names.namespaceOf(key) + ":entity/equipment/" + layer + "/" + Names.pathOf(key);
            return pack.texture(resolved) == null ? null : resolved;
        } catch (RuntimeException exception) {
            throw new IOException("Equipment JSON invalido para " + asset, exception);
        }
    }

    public static String slot(String javaItem) {
        if (javaItem == null) return null;
        String material = javaItem.toUpperCase(java.util.Locale.ROOT);
        if (material.endsWith("_HELMET")) return "head";
        if (material.endsWith("_CHESTPLATE")) return "chest";
        if (material.endsWith("_LEGGINGS")) return "legs";
        if (material.endsWith("_BOOTS")) return "feet";
        return null;
    }
}
