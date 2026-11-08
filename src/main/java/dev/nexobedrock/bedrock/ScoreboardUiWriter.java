package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexobedrock.util.Json;

import java.io.IOException;

/** Small Bedrock sidebar without visual score numbers; it does not change scores. */
public final class ScoreboardUiWriter {
    public void write(TextureStore textures) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", "scoreboard");
        JsonObject score = new JsonObject();
        score.addProperty("visible", false);
        JsonArray zero = new JsonArray(); zero.add(0); zero.add(0);
        score.add("size", zero);
        root.add("scoreboard_sidebar_score", score);
        JsonObject player = new JsonObject();
        player.addProperty("font_scale_factor", 0.8);
        JsonArray size = new JsonArray(); size.add("default"); size.add(8);
        player.add("size", size);
        JsonArray max = new JsonArray(); max.add(200); max.add(8);
        player.add("max_size", max);
        root.add("scoreboard_sidebar_player", player);
        JsonObject padding = new JsonObject();
        JsonArray paddingSize = new JsonArray(); paddingSize.add(0); paddingSize.add(0);
        padding.add("size", paddingSize);
        root.add("scoreboard_sidebar/main/lists/horizontal_padding", padding);
        textures.writeJson("ui/scoreboards.json", Json.prettyBytes(root));
    }
}
