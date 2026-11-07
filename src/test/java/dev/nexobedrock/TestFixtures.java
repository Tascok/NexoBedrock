package dev.nexobedrock;

import dev.nexobedrock.config.HandheldMode;
import dev.nexobedrock.config.MappingMode;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.conversion.ConvertedItem;
import dev.nexobedrock.pack.JavaItemEntry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static PluginConfig config() {
        return new PluginConfig(
                true, true,
                "plugins/Nexo/pack/pack.zip", "plugins/Nexo/pack/assets", true,
                "plugins/Geyser-Spigot/packs", "plugins/Geyser-Spigot/custom_mappings",
                "nexobedrock", false, "geyser reload", true, true, 10,
                true, true, 100, false,
                MappingMode.LEGACY, "nexobedrock", "items", HandheldMode.AUTO,
                new int[]{1, 20, 0}, 64, 256, true, "entity_alphatest_one_sided",
                true, dev.nexobedrock.config.BedrockTextMode.LEGACY
        );
    }

    public static byte[] png(int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    public static JavaItemEntry legacy(String id, String javaItem, int cmd, String modelPath) {
        return new JavaItemEntry(id, javaItem, MappingMode.LEGACY, cmd, null, modelPath,
                id, Set.of(), "test");
    }

    public static JavaItemEntry modern(String id, String javaItem, String itemModel,
                                       String modelPath) {
        return new JavaItemEntry(id, javaItem, MappingMode.MODERN, null, itemModel,
                modelPath, id, Set.of(), "test");
    }

    public static ConvertedItem converted(JavaItemEntry entry, String bedrockId,
                                          String iconKey, boolean handheld) {
        return new ConvertedItem(entry, bedrockId, iconKey, "textures/icons/" + iconKey,
                null, null, null, null, null, new byte[0], null, null, null,
                true, handheld, List.of());
    }
}
