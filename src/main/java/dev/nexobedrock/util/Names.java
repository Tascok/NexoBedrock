package dev.nexobedrock.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Names {
    private static final Pattern VALID_ID = Pattern.compile("[^a-z0-9_.-]");
    private static final Pattern VALID_PATH = Pattern.compile("[^a-z0-9_./-]");
    private static final Pattern MINI_MESSAGE = Pattern.compile("<[^>]+>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9a-fk-or]");

    private Names() {
    }

    public static String normalizeKey(String raw, String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace('\\', '/');
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        if (value.endsWith(".png")) {
            value = value.substring(0, value.length() - 4);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : defaultNamespace;
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (namespace.isBlank()) {
            namespace = defaultNamespace;
        }
        if (path.isBlank()) {
            return null;
        }
        return namespace.toLowerCase(Locale.ROOT) + ":" + path;
    }

    public static String pathOf(String key) {
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    public static String namespaceOf(String key) {
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(0, colon) : "minecraft";
    }

    public static String sanitizeId(String raw) {
        String value = raw == null ? "item" : raw.toLowerCase(Locale.ROOT);
        value = value.replace(':', '_').replace('/', '_').replace('\\', '_');
        value = VALID_ID.matcher(value).replaceAll("_");
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+|_+$", "");
        if (value.isBlank()) {
            value = "item";
        }
        if (!Character.isLetter(value.charAt(0))) {
            value = "item_" + value;
        }
        if (value.length() > 48) {
            String hash = Hashing.shortHash(Hashing.sha256Hex(raw == null ? value : raw));
            value = value.substring(0, 39) + "_" + hash;
        }
        return value;
    }

    public static String sanitizePath(String raw) {
        String value = raw == null ? "texture" : raw.toLowerCase(Locale.ROOT).replace('\\', '/');
        value = VALID_PATH.matcher(value).replaceAll("_");
        value = value.replaceAll("/+", "/");
        value = value.replaceAll("^\\.+/", "");
        value = value.replaceAll("^/+", "");
        if (value.isBlank()) {
            value = "texture";
        }
        return value;
    }

    public static String bedrockIdentifier(String namespace, String id) {
        String ns = sanitizeId(namespace);
        if ("minecraft".equals(ns)) {
            ns = "nexobedrock";
        }
        return ns + ":" + sanitizeId(id);
    }

    public static String geometryIdentifier(String id) {
        return "geometry.nexobedrock." + sanitizeId(id);
    }

    public static String animationIdentifier(String id, String slot) {
        return "animation.nexobedrock." + sanitizeId(id) + "." + sanitizeId(slot);
    }

    /**
     * Chave de item_texture/icon. O Geyser troca ':' por '.' e '/' por '_'
     * quando o icon nao e explicito; usamos o mesmo formato para evitar
     * ambiguidade.
     */
    public static String iconKey(String namespace, String id) {
        return sanitizeId(namespace) + "." + sanitizeId(id).replace('/', '_');
    }

    public static String stripFormatting(String value) {
        if (value == null) {
            return "";
        }
        String withoutMini = MINI_MESSAGE.matcher(value).replaceAll("");
        String withoutLegacy = LEGACY_COLOR.matcher(withoutMini).replaceAll("");
        return withoutLegacy.trim();
    }

    public static String utf8(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static String displayName(String raw, String fallback) {
        String stripped = stripFormatting(raw);
        return stripped.isBlank() ? fallback : stripped;
    }
}
