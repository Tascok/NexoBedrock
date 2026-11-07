package dev.nexobedrock.bedrock;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bakes RGB-colored text glyphs into Bedrock's private-use glyph pages. */
public final class GradientGlyphAtlas {
    private static final int CELL = 16;
    private static final int GRID = 16;
    private static final int PAGE_SIZE = CELL * GRID;
    private static final int FIRST = 0xE200;
    private static final int LAST = 0xF8FF;
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    public record Result(Map<String, String> rewritten, Map<String, byte[]> pages) { }

    public Result build(Iterable<String> names) throws IOException {
        Map<String, String> rewritten = new LinkedHashMap<>();
        Map<String, Integer> glyphIds = new LinkedHashMap<>();
        List<Glyph> glyphs = new ArrayList<>();
        for (String name : names) {
            if (name == null || rewritten.containsKey(name)) continue;
            StringBuilder output = new StringBuilder();
            int color = 0xFFFFFF;
            for (int i = 0; i < name.length();) {
                char current = name.charAt(i);
                if (current == '\u00a7' && i + 1 < name.length()) {
                    if (Character.toLowerCase(name.charAt(i + 1)) == 'r') color = 0xFFFFFF;
                    ColorCode code = colorCode(name, i);
                    if (code != null) {
                        color = code.rgb();
                        i += code.length();
                        continue;
                    }
                    i += 2; // legacy style controls do not affect the baked glyph
                    continue;
                }
                int codePoint = name.codePointAt(i);
                i += Character.charCount(codePoint);
                if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
                    output.appendCodePoint(codePoint);
                    continue;
                }
                String key = new String(Character.toChars(codePoint)) + ':' + color;
                Integer glyph = glyphIds.get(key);
                if (glyph == null) {
                    glyph = FIRST + glyphs.size();
                    if (glyph > LAST) throw new IOException("Too many unique colored glyphs (limit 5888)");
                    glyphIds.put(key, glyph);
                    glyphs.add(new Glyph(codePoint, color));
                }
                output.appendCodePoint(glyph);
            }
            rewritten.put(name, output.toString());
        }

        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (int i = 0; i < glyphs.size(); i++) {
            int page = (FIRST + i) >>> 8;
            String pageName = String.format("font/glyph_%02X.png", page & 0xFF);
            BufferedImage image = images.computeIfAbsent(pageName,
                    ignored -> new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB));
            Glyph glyph = glyphs.get(i);
            int cellIndex = (FIRST + i) & 0xFF;
            int x = (cellIndex & 0x0F) * CELL;
            int y = (cellIndex >>> 4) * CELL;
            BufferedImage mask = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
            var graphics = mask.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(FONT);
            graphics.setColor(Color.WHITE);
            graphics.drawString(new String(Character.toChars(glyph.character())), 1, 13);
            graphics.dispose();
            int rgb = glyph.rgb();
            for (int py = 0; py < CELL; py++) {
                for (int px = 0; px < CELL; px++) {
                    int alpha = mask.getRGB(px, py) >>> 24;
                    if (alpha != 0) image.setRGB(x + px, y + py, (alpha << 24) | rgb);
                }
            }
        }

        Map<String, byte[]> pages = new LinkedHashMap<>();
        for (var entry : images.entrySet()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(entry.getValue(), "png", bytes);
            pages.put(entry.getKey(), bytes.toByteArray());
        }
        return new Result(Collections.unmodifiableMap(rewritten), Collections.unmodifiableMap(pages));
    }

    private static ColorCode colorCode(String text, int offset) {
        char code = Character.toLowerCase(text.charAt(offset + 1));
        if (code == 'x' && offset + 13 < text.length()) {
            StringBuilder hex = new StringBuilder(6);
            for (int n = 0; n < 6; n++) {
                int position = offset + 2 + n * 2;
                if (text.charAt(position) != '\u00a7') return null;
                hex.append(text.charAt(position + 1));
            }
            try { return new ColorCode(Integer.parseInt(hex.toString(), 16), 14); }
            catch (NumberFormatException ignored) { return null; }
        }
        String legacy = "0123456789abcdef";
        int index = legacy.indexOf(code);
        if (index < 0) return null;
        int color = new int[]{0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
                0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF}[index];
        return new ColorCode(color, 2);
    }

    private record ColorCode(int rgb, int length) { }
    private record Glyph(int character, int rgb) { }
}
