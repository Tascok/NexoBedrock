package dev.nexobedrock.pack;

import dev.nexobedrock.util.Names;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves Nexo texture references to the actual texture keys present in a generated pack. */
public final class PackTextureResolver {
    public String resolve(JavaPack pack, String rawReference) {
        String normalized = Names.normalizeKey(rawReference, "minecraft");
        if (normalized == null) return null;
        if (pack.texture(normalized) != null) return normalized;

        String referencePath = Names.pathOf(normalized);
        if (referencePath.startsWith("textures/")) {
            referencePath = referencePath.substring("textures/".length());
        }
        String suffix = "/" + referencePath.toLowerCase(java.util.Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        for (String key : pack.textures().keySet()) {
            String path = Names.pathOf(key).toLowerCase(java.util.Locale.ROOT);
            if (path.endsWith(suffix)) candidates.add(key);
        }
        if (candidates.isEmpty()) {
            String filename = referencePath.substring(referencePath.lastIndexOf('/') + 1)
                    .toLowerCase(java.util.Locale.ROOT);
            if (filename.endsWith(".png")) filename = filename.substring(0, filename.length() - 4);
            for (String key : pack.textures().keySet()) {
                String path = Names.pathOf(key).toLowerCase(java.util.Locale.ROOT);
                String stem = path.substring(path.lastIndexOf('/') + 1);
                if (stem.endsWith(".png")) stem = stem.substring(0, stem.length() - 4);
                if (stem.equals(filename)) candidates.add(key);
            }
        }
        if (candidates.isEmpty()) return null;
        String referenceNamespace = Names.namespaceOf(normalized);
        List<String> sameNamespace = candidates.stream()
                .filter(key -> Names.namespaceOf(key).equals(referenceNamespace)).toList();
        List<String> preferred = sameNamespace.isEmpty() ? candidates : sameNamespace;
        if (preferred.size() > 1) {
            preferred = preferred.stream().sorted(Comparator.naturalOrder()).toList();
            // Never choose arbitrarily between different images with the same basename.
            if (!java.util.Arrays.equals(pack.texture(preferred.get(0)),
                    pack.texture(preferred.get(1)))) return null;
        }
        return preferred.getFirst();
    }
}
