package dev.nexobedrock.geyser.extension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Maps stable catalog indices to custom Bedrock entity identifiers. */
public final class FurnitureMappingConfig {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private FurnitureMappingConfig() {
    }

    public static Map<Integer, String> read(InputStream input) throws IOException {
        Map<Integer, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) continue;
                int separator = value.indexOf('=');
                if (separator <= 0) {
                    throw new IOException("Expected index=identifier at line " + lineNumber);
                }
                int index;
                try {
                    index = Integer.parseInt(value.substring(0, separator).trim());
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid furniture index at line " + lineNumber, exception);
                }
                String identifier = value.substring(separator + 1).trim().toLowerCase();
                if (index < 0 || !IDENTIFIER.matcher(identifier).matches()
                        || identifier.startsWith("minecraft:")) {
                    throw new IOException("Invalid furniture mapping at line " + lineNumber);
                }
                if (result.putIfAbsent(index, identifier) != null) {
                    throw new IOException("Duplicate furniture index: " + index);
                }
            }
        }
        return Map.copyOf(result);
    }
}
