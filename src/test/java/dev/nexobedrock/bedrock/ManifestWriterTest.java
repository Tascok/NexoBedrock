package dev.nexobedrock.bedrock;

import com.google.gson.JsonObject;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ManifestWriterTest {
    @Test
    void changesPackIdentityWhenConverterVersionChanges() {
        PluginConfig config = TestFixtures.config();
        ManifestWriter writer = new ManifestWriter();

        JsonObject previous = writer.write("same-source-hash", config, "0.1.1");
        JsonObject updated = writer.write("same-source-hash", config, "0.1.2");

        JsonObject oldHeader = previous.getAsJsonObject("header");
        JsonObject newHeader = updated.getAsJsonObject("header");
        assertNotEquals(oldHeader.get("uuid").getAsString(),
                newHeader.get("uuid").getAsString());
        assertNotEquals(previous.getAsJsonArray("modules").get(0).getAsJsonObject()
                        .get("uuid").getAsString(),
                updated.getAsJsonArray("modules").get(0).getAsJsonObject()
                        .get("uuid").getAsString());
    }
}
