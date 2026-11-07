package dev.nexobedrock.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared finite glyph palette used to render dynamic RGB chat on Bedrock. */
public final class ChatGradientGlyphs {
    private static final int[] CHANNEL_4 = {0, 85, 170, 255};
    private static final int[] CHANNEL_3 = {0, 127, 255};
    private static final String CHARACTERS =
            " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

    private ChatGradientGlyphs() { }

    /** All source strings that must be baked into the Bedrock font atlas. */
    public static List<String> catalog() {
        List<String> result = new ArrayList<>(CHARACTERS.length() * 48);
        for (int red : CHANNEL_4) {
            for (int green : CHANNEL_4) {
                for (int blue : CHANNEL_3) {
                    String color = String.format("%02x%02x%02x", red, green, blue);
                    for (int i = 0; i < CHARACTERS.length(); i++) {
                        result.add(legacyHex(color) + CHARACTERS.charAt(i));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    /** Converts RGB-formatted text to the nearest pre-baked character glyphs. */
    public static String rewrite(String legacyText, Map<String, String> glyphMap) {
        if (legacyText == null || legacyText.isEmpty() || glyphMap == null || glyphMap.isEmpty()) {
            return legacyText;
        }
        // Older cached packs do not contain the reserved chat catalog; do not
        // strip their RGB formatting until the corresponding font is installed.
        if (!glyphMap.containsKey(legacyHex("ffffff") + 'A')
                || !glyphMap.containsKey(legacyHex("00aaff") + 'B')) {
            return legacyText;
        }
        StringBuilder output = new StringBuilder(legacyText.length());
        int rgb = 0xFFFFFF;
        boolean hasHex = false;
        for (int i = 0; i < legacyText.length();) {
            if (legacyText.charAt(i) == '\u00a7' && i + 1 < legacyText.length()) {
                char code = Character.toLowerCase(legacyText.charAt(i + 1));
                if (code == 'x' && i + 13 < legacyText.length()) {
                    StringBuilder hex = new StringBuilder(6);
                    boolean valid = true;
                    for (int n = 0; n < 6; n++) {
                        int at = i + 2 + n * 2;
                        if (legacyText.charAt(at) != '\u00a7') { valid = false; break; }
                        hex.append(legacyText.charAt(at + 1));
                    }
                    if (valid) {
                        try {
                            rgb = Integer.parseInt(hex.toString(), 16);
                            hasHex = true;
                            i += 14;
                            continue;
                        } catch (NumberFormatException ignored) { }
                    }
                }
                if (code == 'r') rgb = 0xFFFFFF;
                i += 2; // Formatting controls are represented by the baked glyph itself.
                continue;
            }

            int codePoint = legacyText.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                output.appendCodePoint(codePoint);
                continue;
            }
            if (!Character.isBmpCodePoint(codePoint)) {
                output.appendCodePoint(codePoint);
                continue;
            }
            int nearest = nearestPaletteColor(rgb);
            String key = legacyHex(String.format("%06x", nearest)) + (char) codePoint;
            String glyph = glyphMap.get(key);
            output.append(glyph == null ? Character.toString(codePoint) : glyph);
        }
        return hasHex ? output.toString() : legacyText;
    }

    private static int nearestPaletteColor(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int nr = nearest(r, CHANNEL_4);
        int ng = nearest(g, CHANNEL_4);
        int nb = nearest(b, CHANNEL_3);
        return nr << 16 | ng << 8 | nb;
    }

    private static int nearest(int channel, int[] levels) {
        int best = levels[0];
        int distance = Math.abs(channel - best);
        for (int i = 1; i < levels.length; i++) {
            int nextDistance = Math.abs(channel - levels[i]);
            if (nextDistance < distance) {
                best = levels[i];
                distance = nextDistance;
            }
        }
        return best;
    }

    private static String legacyHex(String color) {
        StringBuilder result = new StringBuilder("\u00a7x");
        for (int i = 0; i < color.length(); i++) result.append('\u00a7').append(color.charAt(i));
        return result.toString();
    }
}
