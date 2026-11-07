package dev.nexobedrock.bedrock;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientGlyphAtlasTest {
    @Test
    void bakesRgbStopsAsDistinctPrivateUseGlyphsAndPngPages() throws Exception {
        var result = new GradientGlyphAtlas().build(List.of(
                "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70A"
                        + "\u00a7x\u00a70\u00a70\u00a7f\u00a7f\u00a7f\u00a7fB"));

        String rewritten = result.rewritten().values().iterator().next();
        assertEquals(2, rewritten.codePointCount(0, rewritten.length()));
        assertNotEquals(rewritten.codePointAt(0), rewritten.codePointAt(1));
        assertTrue(result.pages().containsKey("font/glyph_E2.png"));
        var png = ImageIO.read(new ByteArrayInputStream(result.pages().get("font/glyph_E2.png")));
        assertEquals(256, png.getWidth());
        assertEquals(256, png.getHeight());
        assertTrue((png.getRGB(1, 1) >>> 24) > 0 || countVisible(png) > 0);
    }

    private int countVisible(java.awt.image.BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) count++;
        }
        return count;
    }
}
