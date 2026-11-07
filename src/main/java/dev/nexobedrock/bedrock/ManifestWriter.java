package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.config.PluginConfig;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ManifestWriter {
    public JsonObject write(String packHash, PluginConfig config, String pluginVersion) {
        JsonObject header = new JsonObject();
        header.addProperty("name", "NexoBedrock");
        header.addProperty("description", "Recursos do Nexo convertidos para Bedrock");
        // Bedrock caches packs by UUID + manifest version. Include the
        // converter version so a new conversion algorithm cannot be hidden by
        // a client-side cached pack when the Java source pack is unchanged.
        String packIdentity = packHash + ":" + pluginVersion;
        header.addProperty("uuid", uuid("header", packIdentity));
        header.add("version", array(1, 0, 0));
        header.add("min_engine_version", array(config.minEngineVersion()));

        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty("uuid", uuid("module", packIdentity));
        module.add("version", array(1, 0, 0));

        JsonArray modules = new JsonArray();
        modules.add(module);

        JsonArray authors = new JsonArray();
        authors.add("NexoBedrock");

        JsonObject generatedWith = new JsonObject();
        JsonArray versions = new JsonArray();
        versions.add(pluginVersion);
        generatedWith.add("nexobedrock", versions);

        JsonObject metadata = new JsonObject();
        metadata.add("authors", authors);
        metadata.add("generated_with", generatedWith);

        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        manifest.add("header", header);
        manifest.add("modules", modules);
        manifest.add("metadata", metadata);
        return manifest;
    }

    private static String uuid(String salt, String packHash) {
        UUID uuid = UUID.nameUUIDFromBytes(("nexobedrock:" + salt + ":" + packHash)
                .getBytes(StandardCharsets.UTF_8));
        return uuid.toString();
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
