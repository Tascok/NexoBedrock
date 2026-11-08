package dev.nexobedrock.bedrock;

import dev.nexobedrock.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardUiWriterTest {
    @TempDir Path temporary;

    @Test
    void hidesNumberLabelsWithoutRemovingScoreboardData() throws Exception {
        new ScoreboardUiWriter().write(new TextureStore(temporary));
        var ui = Json.parseObject(Files.readAllBytes(temporary.resolve("ui/scoreboards.json")));
        assertEquals("scoreboard", ui.get("namespace").getAsString());
        assertFalse(ui.getAsJsonObject("scoreboard_sidebar_score").get("visible").getAsBoolean());
        assertEquals(0.8, ui.getAsJsonObject("scoreboard_sidebar_player")
                .get("font_scale_factor").getAsDouble());
    }
}
