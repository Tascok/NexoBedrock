package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;

import java.util.Map;

public final class ItemTextureWriter {
    public JsonObject write(Map<String, String> textureData, String packName) {
        JsonObject root = new JsonObject();
        root.addProperty("resource_pack_name", packName);
        root.addProperty("texture_name", "atlas.items");

        JsonObject data = new JsonObject();
        for (Map.Entry<String, String> entry : textureData.entrySet()) {
            String path = entry.getValue();
            if (path == null || path.isBlank()) {
                continue;
            }
            JsonObject texture = new JsonObject();
            texture.addProperty("textures", path);
            data.add(entry.getKey(), texture);
        }
        root.add("texture_data", data);
        return root;
    }
}
