package dev.nexobedrock.bedrock;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class IconFactory {
    private static final int SIZE = 64;

    public byte[] packIcon(byte[] itemIcon) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setColor(new Color(0x1B, 0x1F, 0x2B));
        graphics.fillRect(0, 0, SIZE, SIZE);

        boolean drawn = false;
        if (itemIcon != null) {
            try {
                BufferedImage icon = ImageIO.read(new ByteArrayInputStream(itemIcon));
                if (icon != null) {
                    graphics.drawImage(icon, 8, 8, SIZE - 16, SIZE - 16, null);
                    drawn = true;
                }
            } catch (IOException ignored) {
                // Cai no icone padrao.
            }
        }
        if (!drawn) {
            graphics.setColor(new Color(0x5B, 0x84, 0xFF));
            graphics.fillRoundRect(12, 12, SIZE - 24, SIZE - 24, 12, 12);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(28, 20, 8, 24);
            graphics.fillRect(28, 20, 18, 8);
        }
        graphics.dispose();

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao gerar pack_icon.png", exception);
        }
    }
}
