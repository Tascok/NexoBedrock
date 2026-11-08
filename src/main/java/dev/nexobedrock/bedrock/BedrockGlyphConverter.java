package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Converts Java bitmap font providers into Bedrock's 16x16 Unicode glyph pages. */
public final class BedrockGlyphConverter {
    private static final int FIRST_SAFE_CODEPOINT = 0xE200;
    private static final int SAFE_PAGE_COUNT = 0xF9 - 0xE2;
    private static final int MAX_CELL_SIZE = 256;

    public record Result(Map<String, byte[]> pages, Map<String, Integer> codepoints,
                         List<String> warnings) {
        public Result {
            pages = Map.copyOf(pages);
            codepoints = Map.copyOf(codepoints);
            warnings = List.copyOf(warnings);
        }
    }

    private record Glyph(String font, int sourceCodepoint, BufferedImage image) {
    }

    private record SourceGlyph(String font, int codepoint) {
    }

    private record Dimensions(int width, int height) {
    }

    private static final class Page {
        private final int cellWidth;
        private final int cellHeight;
        private final BufferedImage image;
        private int glyphCount;

        private Page(int cellWidth, int cellHeight) {
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            int cellSize = Math.max(cellWidth, cellHeight);
            this.image = new BufferedImage(cellSize * 16, cellSize * 16,
                    BufferedImage.TYPE_INT_ARGB);
        }
    }

    public Result convert(JavaPack pack) {
        List<String> warnings = new ArrayList<>();
        List<Glyph> glyphs = readGlyphs(pack, warnings);
        glyphs.sort(Comparator.comparing(Glyph::font)
                .thenComparingInt(Glyph::sourceCodepoint));

        Map<SourceGlyph, Glyph> bySource = new LinkedHashMap<>();
        for (Glyph glyph : glyphs) {
            SourceGlyph key = new SourceGlyph(glyph.font(), glyph.sourceCodepoint());
            Glyph existing = bySource.putIfAbsent(key, glyph);
            if (existing != null && !samePixels(existing.image(), glyph.image())) {
                warnings.add("Codepoint U+%04X tem imagens conflitantes na fonte %s; "
                        .formatted(glyph.sourceCodepoint(), glyph.font()));
            }
        }

        Map<Dimensions, List<Glyph>> bySize = new LinkedHashMap<>();
        Map<Integer, List<Glyph>> directPages = new LinkedHashMap<>();
        Map<Integer, Long> sourcesPerCharacter = bySource.values().stream().collect(
                java.util.stream.Collectors.groupingBy(Glyph::sourceCodepoint,
                        java.util.stream.Collectors.counting()));
        Set<Integer> spacePages = sourceSpacePages(pack);
        sourcesPerCharacter.forEach((source, count) -> {
            if (count > 1) spacePages.add(source >> 8);
        });
        for (Glyph glyph : bySource.values()) {
            int source = glyph.sourceCodepoint();
            if (source >= FIRST_SAFE_CODEPOINT && source < 0xF900
                    && sourcesPerCharacter.get(source) == 1
                    && !spacePages.contains(source >> 8)) {
                directPages.computeIfAbsent(source >> 8, ignored -> new ArrayList<>()).add(glyph);
                continue;
            }
            Dimensions dimensions = new Dimensions(
                    Math.min(MAX_CELL_SIZE, glyph.image().getWidth()),
                    Math.min(MAX_CELL_SIZE, glyph.image().getHeight()));
            bySize.computeIfAbsent(dimensions, ignored -> new ArrayList<>()).add(glyph);
        }

        Map<String, byte[]> output = new LinkedHashMap<>();
        Map<String, Integer> codepoints = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Glyph>> entry : directPages.entrySet()) {
            int maxSize = entry.getValue().stream().mapToInt(glyph -> Math.max(
                    Math.min(MAX_CELL_SIZE, glyph.image().getWidth()),
                    Math.min(MAX_CELL_SIZE, glyph.image().getHeight()))).max().orElseThrow();
            Page page = new Page(maxSize, maxSize);
            for (Glyph glyph : entry.getValue()) {
                int target = glyph.sourceCodepoint();
                int cell = target & 0xff;
                int width = Math.min(MAX_CELL_SIZE, glyph.image().getWidth());
                int height = Math.min(MAX_CELL_SIZE, glyph.image().getHeight());
                int x = (cell % 16) * maxSize + (maxSize - width) / 2;
                int y = (cell / 16) * maxSize + (maxSize - height) / 2;
                Graphics2D graphics = page.image.createGraphics();
                graphics.setComposite(java.awt.AlphaComposite.Src);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(glyph.image(), x, y, width, height, null);
                graphics.dispose();
                codepoints.put(glyph.font() + ":" + String.format("%04X", target), target);
            }
            output.put(String.format("font/glyph_%02X.png", entry.getKey()), png(page.image));
        }
        Set<Integer> reservedPages = sourceFontPages(pack);
        int pageIndex = 0;
        for (Map.Entry<Dimensions, List<Glyph>> group : bySize.entrySet()) {
            List<Glyph> groupGlyphs = group.getValue();
            for (int start = 0; start < groupGlyphs.size(); start += 256) {
                while (pageIndex < SAFE_PAGE_COUNT
                        && reservedPages.contains((FIRST_SAFE_CODEPOINT >> 8) + pageIndex)) {
                    pageIndex++;
                }
                if (pageIndex >= SAFE_PAGE_COUNT) {
                    throw new IllegalStateException("Sem páginas Unicode livres suficientes para "
                            + "converter glyphs sem colisões com os caracteres do pack.");
                }
                Dimensions dimensions = group.getKey();
                Page page = new Page(dimensions.width(), dimensions.height());
                int end = Math.min(start + 256, groupGlyphs.size());
                for (int index = start; index < end; index++) {
                    Glyph glyph = groupGlyphs.get(index);
                    int target = FIRST_SAFE_CODEPOINT + pageIndex * 256 + index - start;
                    codepoints.put(glyph.font() + ":"
                                    + String.format("%04X", glyph.sourceCodepoint()), target);
                    int cell = target & 0xFF;
                    int cellSize = Math.max(page.cellWidth, page.cellHeight);
                    int x = (cell % 16) * cellSize + (cellSize - page.cellWidth) / 2;
                    int y = (cell / 16) * cellSize + (cellSize - page.cellHeight) / 2;
                    Graphics2D graphics = page.image.createGraphics();
                    graphics.setComposite(java.awt.AlphaComposite.Src);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    graphics.drawImage(glyph.image(), x, y,
                            x + page.cellWidth, y + page.cellHeight,
                            0, 0, glyph.image().getWidth(), glyph.image().getHeight(), null);
                    graphics.dispose();
                    page.glyphCount++;
                }
                if (page.glyphCount > 0) {
                    output.put(String.format("font/glyph_%02X.png", 0xE2 + pageIndex),
                            png(page.image));
                }
                pageIndex++;
            }
        }

        if (!output.isEmpty()) {
            warnings.add("Providers bitmap foram convertidos em páginas Bedrock E2-F8; "
                    + "as métricas Java de ascent/avanço não são reproduzidas exatamente.");
        }
        return new Result(output, codepoints, warnings);
    }

    private Set<Integer> sourceSpacePages(JavaPack pack) {
        Set<Integer> result = new HashSet<>();
        for (JsonObject font : pack.fonts().values()) {
            JsonArray providers = font.getAsJsonArray("providers");
            if (providers == null) continue;
            for (JsonElement entry : providers) {
                if (!entry.isJsonObject()) continue;
                JsonObject advances = entry.getAsJsonObject().getAsJsonObject("advances");
                if (advances != null) {
                    for (String key : advances.keySet()) {
                        key.codePoints().forEach(cp -> result.add(cp >> 8));
                    }
                }
            }
        }
        return result;
    }

    /** Reserve entire pages: a glyph page replaces every character on that page. */
    private Set<Integer> sourceFontPages(JavaPack pack) {
        Set<Integer> pages = new HashSet<>();
        for (JsonObject font : pack.fonts().values()) {
            JsonArray providers = font.getAsJsonArray("providers");
            if (providers == null) continue;
            for (JsonElement entry : providers) {
                if (!entry.isJsonObject()) continue;
                JsonObject provider = entry.getAsJsonObject();
                JsonArray rows = provider.getAsJsonArray("chars");
                if (rows != null) {
                    for (JsonElement row : rows) {
                        if (row.isJsonPrimitive() && row.getAsJsonPrimitive().isString()) {
                            row.getAsString().codePoints().filter(cp -> cp != 0)
                                    .forEach(cp -> pages.add(cp >> 8));
                        }
                    }
                }
                JsonObject advances = provider.getAsJsonObject("advances");
                if (advances != null) {
                    for (String key : advances.keySet()) {
                        key.codePoints().forEach(cp -> pages.add(cp >> 8));
                    }
                }
            }
        }
        return pages;
    }

    private List<Glyph> readGlyphs(JavaPack pack, List<String> warnings) {
        List<Glyph> result = new ArrayList<>();
        for (Map.Entry<String, JsonObject> font : pack.fonts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            JsonArray providers = font.getValue().getAsJsonArray("providers");
            if (providers == null) {
                warnings.add("Fonte sem array providers: " + font.getKey());
                continue;
            }
            for (int index = 0; index < providers.size(); index++) {
                JsonElement element = providers.get(index);
                if (!element.isJsonObject()) continue;
                JsonObject provider = element.getAsJsonObject();
                if (!"bitmap".equals(string(provider, "type"))) {
                    String type = string(provider, "type");
                    if ("space".equals(type) || "reference".equals(type)) {
                        warnings.add("Provider %s[%d] (%s) não é bitmap; conversão visual ignorada"
                                .formatted(font.getKey(), index, type));
                    }
                    continue;
                }
                readBitmapProvider(pack, font.getKey(), index, provider, result, warnings);
            }
        }
        return result;
    }

    private void readBitmapProvider(JavaPack pack, String fontKey, int index,
                                    JsonObject provider, List<Glyph> output,
                                    List<String> warnings) {
        String rawFile = string(provider, "file");
        JsonArray chars = provider.getAsJsonArray("chars");
        if (rawFile == null || chars == null || chars.isEmpty()) {
            warnings.add("Bitmap provider incompleto: " + fontKey + "[" + index + "]");
            return;
        }
        String textureKey = Names.normalizeKey(rawFile, "minecraft");
        if (textureKey == null) {
            warnings.add("Caminho de bitmap inválido: " + rawFile);
            return;
        }
        String path = Names.pathOf(textureKey);
        if (path.startsWith("textures/")) {
            textureKey = Names.namespaceOf(textureKey) + ":" + path.substring("textures/".length());
        }
        byte[] bytes = pack.texture(textureKey);
        if (bytes == null) {
            warnings.add("Textura de fonte ausente: " + textureKey + " (" + fontKey + ")");
            return;
        }
        BufferedImage sheet;
        try {
            sheet = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            warnings.add("PNG de fonte inválido: " + textureKey);
            return;
        }
        if (sheet == null || sheet.getWidth() < 1 || sheet.getHeight() < 1) {
            warnings.add("PNG de fonte inválido: " + textureKey);
            return;
        }

        List<String> rows = new ArrayList<>();
        for (JsonElement row : chars) {
            if (!row.isJsonPrimitive() || !row.getAsJsonPrimitive().isString()) {
                warnings.add("Linha chars inválida: " + fontKey + "[" + index + "]");
                return;
            }
            rows.add(row.getAsString());
        }
        int columns = rows.getFirst().codePointCount(0, rows.getFirst().length());
        if (columns == 0 || rows.stream().anyMatch(row ->
                row.codePointCount(0, row.length()) != columns)
                || sheet.getWidth() % columns != 0 || sheet.getHeight() % rows.size() != 0) {
            warnings.add("Grade bitmap incompatível com chars: " + fontKey + "[" + index + "]");
            return;
        }

        int cellWidth = sheet.getWidth() / columns;
        int cellHeight = sheet.getHeight() / rows.size();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String row = rows.get(rowIndex);
            int column = 0;
            for (int offset = 0; offset < row.length();) {
                int codepoint = row.codePointAt(offset);
                if (codepoint != 0) {
                    BufferedImage glyph = sheet.getSubimage(column * cellWidth,
                            rowIndex * cellHeight, cellWidth, cellHeight);
                    output.add(new Glyph(fontKey, codepoint, copy(glyph)));
                }
                offset += Character.charCount(codepoint);
                column++;
            }
        }
    }

    private boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) return false;
            }
        }
        return true;
    }

    private BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode Bedrock glyph page", exception);
        }
    }

    private String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }
}
