package dev.nexobedrock.geyser.extension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Parses opt-in Java entity type to Bedrock custom entity replacements. */
public final class EntityMappingConfig {
    private static final Pattern IDENTIFIER =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private EntityMappingConfig() {
    }

    public static Map<String, String> read(InputStream input) throws IOException {
        Map<String, String> mappings = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                int separator = entry.indexOf('=');
                if (separator < 1) {
                    throw new IOException("Expected JavaEntityId=BedrockEntityId at line "
                            + lineNumber);
                }
                String rawJavaId = entry.substring(0, separator).trim();
                String javaId = rawJavaId.toLowerCase(java.util.Locale.ROOT);
                String bedrockId = entry.substring(separator + 1).trim()
                        .toLowerCase(java.util.Locale.ROOT);
                if (!IDENTIFIER.matcher(javaId).matches()) {
                    throw new IOException("Invalid Java entity identifier: " + rawJavaId);
                }
                if (!IDENTIFIER.matcher(bedrockId).matches()
                        || bedrockId.startsWith("minecraft:")) {
                    throw new IOException("Bedrock custom entity id must be namespaced and non-vanilla: "
                            + bedrockId);
                }
                if (mappings.putIfAbsent(javaId, bedrockId) != null) {
                    throw new IOException("Duplicate Java entity mapping: " + javaId);
                }
            }
        }
        return Map.copyOf(mappings);
    }
}
