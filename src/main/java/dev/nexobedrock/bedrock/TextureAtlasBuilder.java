package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.ResolvedModel;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta um atlas unico por modelo. Cada combinacao (textura, rotacao de face)
 * vira um tile separado, porque a rotacao de face do Java nao existe no
 * formato de geometria do Bedrock.
 */
public final class TextureAtlasBuilder {
    public static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
    private static final int HARD_MAX_ATLAS_DIMENSION = 4096;
    private static final float UV_INSET_RATIO = 0.016f / 16f;

    public AtlasResult build(JavaPack pack, ResolvedModel model, PluginConfig config) {
        List<String> warnings = new ArrayList<>();
        int texW = Math.max(1, model.textureWidth());
        int texH = Math.max(1, model.textureHeight());

        JsonArray elements = model.elements() == null ? new JsonArray() : model.elements();
        Map<TileKey, Tile> tiles = new LinkedHashMap<>();

        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            JsonElement element = elements.get(elementIndex);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject faces = object(element.getAsJsonObject(), "faces");
            if (faces == null) {
                continue;
            }
            for (String faceName : FACE_NAMES) {
                JsonObject face = object(faces, faceName);
                if (face == null) {
                    continue;
                }
                String textureKey = resolveTexture(model, string(face, "texture"));
                int rotation = normalizeRotation(intValue(face, "rotation", 0));
                TileKey key = new TileKey(textureKey, rotation);
                tiles.computeIfAbsent(key, ignored -> loadTile(pack, key, warnings));
            }
        }

        if (tiles.isEmpty()) {
            tiles.put(new TileKey(null, 0), Tile.transparent());
        }
        if (tiles.size() > config.maxAtlasTiles()) {
            throw new IllegalArgumentException("Modelo usa " + tiles.size()
                    + " tiles de atlas; limite configurado e " + config.maxAtlasTiles());
        }

        int tileWidth = texW;
        int tileHeight = texH;
        for (Tile tile : tiles.values()) {
            tileWidth = Math.max(tileWidth, tile.rotatedWidth());
            tileHeight = Math.max(tileHeight, tile.rotatedHeight());
        }

        int maxTileSize = config.maxAtlasTileSize();
        if (tileWidth > maxTileSize || tileHeight > maxTileSize) {
            float scale = Math.min((float) maxTileSize / tileWidth, (float) maxTileSize / tileHeight);
            tileWidth = Math.max(1, Math.round(tileWidth * scale));
            tileHeight = Math.max(1, Math.round(tileHeight * scale));
            warnings.add("Atlas reduzido para " + tileWidth + "x" + tileHeight
                    + " (limite de tile: " + maxTileSize + ")");
        }

        int count = tiles.size();
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        int rows = Math.max(1, (int) Math.ceil((double) count / columns));
        int atlasWidth = columns * tileWidth;
        int atlasHeight = rows * tileHeight;

        if (atlasWidth > HARD_MAX_ATLAS_DIMENSION || atlasHeight > HARD_MAX_ATLAS_DIMENSION) {
            throw new IllegalArgumentException("Atlas resultante grande demais: "
                    + atlasWidth + "x" + atlasHeight);
        }

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        Map<TileKey, Placement> placements = new LinkedHashMap<>();
        int tileIndex = 0;
        for (Map.Entry<TileKey, Tile> entry : tiles.entrySet()) {
            int column = tileIndex % columns;
            int row = tileIndex / columns;
            int x = column * tileWidth;
            int y = row * tileHeight;
            graphics.drawImage(entry.getValue().image(), x, y, tileWidth, tileHeight, null);
            placements.put(entry.getKey(), new Placement(x, y, entry.getValue()));
            tileIndex++;
        }
        graphics.dispose();

        Map<FaceKey, UvRect> uvs = new LinkedHashMap<>();
        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            JsonElement element = elements.get(elementIndex);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject faces = object(element.getAsJsonObject(), "faces");
            if (faces == null) {
                continue;
            }
            for (String faceName : FACE_NAMES) {
                JsonObject face = object(faces, faceName);
                if (face == null) {
                    continue;
                }
                String textureKey = resolveTexture(model, string(face, "texture"));
                int rotation = normalizeRotation(intValue(face, "rotation", 0));
                Placement placement = placements.get(new TileKey(textureKey, rotation));
                if (placement == null) {
                    continue;
                }
                UvRect uv = computeUv(faceName, face, placement, texW, texH, tileWidth, tileHeight,
                        atlasWidth, atlasHeight, rotation);
                if (uv != null) {
                    uvs.put(new FaceKey(elementIndex, faceName), uv);
                }
            }
        }

        byte[] icon = icon(pack, model, warnings);
        return new AtlasResult(png(atlas), atlasWidth, atlasHeight, Map.copyOf(uvs), icon,
                List.copyOf(warnings));
    }

    private UvRect computeUv(String faceName, JsonObject face, Placement placement,
                             int texWidth, int texHeight, int tileWidth, int tileHeight,
                             int atlasWidth, int atlasHeight, int rotation) {
        float[] uv = floats(face, "uv");
        if (uv == null || uv.length < 4) {
            uv = new float[]{0f, 0f, 16f, 16f};
        }

        Tile tile = placement.tile();
        float sourceWidth = Math.max(1, tile.originalWidth());
        float sourceHeight = Math.max(1, tile.originalHeight());
        float rotatedWidth = Math.max(1, tile.rotatedWidth());
        float rotatedHeight = Math.max(1, tile.rotatedHeight());

        // No formato Java as UVs usam o espaco 0..16, independente da
        // resolucao real da textura. texture_size serve apenas para escalar
        // esse espaco para os pixels da imagem.
        float sx1 = uv[0] * sourceWidth / 16f;
        float sy1 = uv[1] * sourceHeight / 16f;
        float sx2 = uv[2] * sourceWidth / 16f;
        float sy2 = uv[3] * sourceHeight / 16f;

        float rx1;
        float ry1;
        float rx2;
        float ry2;
        switch (rotation) {
            case 90 -> {
                rx1 = sourceHeight - sy2;
                ry1 = sx1;
                rx2 = sourceHeight - sy1;
                ry2 = sx2;
            }
            case 180 -> {
                rx1 = sourceWidth - sx2;
                ry1 = sourceHeight - sy2;
                rx2 = sourceWidth - sx1;
                ry2 = sourceHeight - sy1;
            }
            case 270 -> {
                rx1 = sy1;
                ry1 = sourceWidth - sx2;
                rx2 = sy2;
                ry2 = sourceWidth - sx1;
            }
            default -> {
                rx1 = sx1;
                ry1 = sy1;
                rx2 = sx2;
                ry2 = sy2;
            }
        }

        float u1 = placement.x() + rx1 * tileWidth / rotatedWidth;
        float v1 = placement.y() + ry1 * tileHeight / rotatedHeight;
        float u2 = placement.x() + rx2 * tileWidth / rotatedWidth;
        float v2 = placement.y() + ry2 * tileHeight / rotatedHeight;

        float xSign = Math.max(-1f, Math.min(1f, u2 - u1));
        float ySign = Math.max(-1f, Math.min(1f, v2 - v1));
        // O inset evita bleeding entre tiles, mas nunca pode consumir mais de
        // 25% de uma face pequena (UVs minusculas sao comuns em modelos 3D).
        float insetX = Math.min(UV_INSET_RATIO * atlasWidth,
                Math.abs(u2 - u1) * 0.25f);
        float insetY = Math.min(UV_INSET_RATIO * atlasHeight,
                Math.abs(v2 - v1) * 0.25f);

        float u;
        float v;
        float width;
        float height;
        if ("up".equals(faceName) || "down".equals(faceName)) {
            u = u2 - insetX * xSign;
            v = v2 - insetY * ySign;
            width = u1 - u2 + insetX * xSign;
            height = v1 - v2 + insetY * ySign;
        } else {
            u = u1 + insetX * xSign;
            v = v1 + insetY * ySign;
            width = u2 - u1 - insetX * xSign;
            height = v2 - v1 - insetY * ySign;
        }

        if (Math.abs(width) < 0.0001f || Math.abs(height) < 0.0001f) {
            return null;
        }
        return new UvRect(round(u), round(v), round(width), round(height));
    }

    private Tile loadTile(JavaPack pack, TileKey key, List<String> warnings) {
        if (key.textureKey() == null) {
            return Tile.transparent();
        }
        byte[] bytes = pack.texture(key.textureKey());
        if (bytes == null) {
            warnings.add("Textura ausente: " + key.textureKey());
            return Tile.transparent();
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                warnings.add("Textura invalida: " + key.textureKey());
                return Tile.transparent();
            }
            BufferedImage argb = toArgb(original);
            if (pack.isAnimated(key.textureKey())
                    && argb.getHeight() > argb.getWidth()
                    && argb.getHeight() % argb.getWidth() == 0) {
                // Textura animada do Java: tira vertical de frames. O MVP nao
                // suporta animacao, entao usamos o primeiro frame para manter
                // as UVs e o atlas corretos.
                argb = argb.getSubimage(0, 0, argb.getWidth(), argb.getWidth());
            }
            BufferedImage rotated = rotate(argb, key.rotation());
            return new Tile(rotated, argb.getWidth(), argb.getHeight(),
                    rotated.getWidth(), rotated.getHeight());
        } catch (IOException exception) {
            warnings.add("Falha ao ler textura " + key.textureKey() + ": " + exception.getMessage());
            return Tile.transparent();
        }
    }

    public byte[] icon(JavaPack pack, ResolvedModel model, List<String> warnings) {
        String textureKey = model.iconTexture();
        if (textureKey == null) {
            warnings.add("Modelo sem textura principal; usando icone transparente");
            return transparentPng();
        }
        byte[] bytes = pack.texture(textureKey);
        if (bytes == null) {
            warnings.add("Textura de icone ausente: " + textureKey);
            return transparentPng();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return transparentPng();
            }
            BufferedImage argb = toArgb(image);
            if (pack.isAnimated(textureKey)
                    && argb.getHeight() > argb.getWidth()
                    && argb.getHeight() % argb.getWidth() == 0) {
                argb = argb.getSubimage(0, 0, argb.getWidth(), argb.getWidth());
            }
            return png(argb);
        } catch (IOException exception) {
            warnings.add("Falha ao gerar icone: " + exception.getMessage());
            return transparentPng();
        }
    }

    public boolean hasVisiblePixel(byte[] pngBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BufferedImage rotate(BufferedImage source, int degrees) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (degrees == 0) {
            return source;
        }
        BufferedImage result;
        if (degrees == 90 || degrees == 270) {
            result = new BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB);
        } else {
            result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = source.getRGB(x, y);
                switch (degrees) {
                    case 90 -> result.setRGB(height - 1 - y, x, pixel);
                    case 180 -> result.setRGB(width - 1 - x, height - 1 - y, pixel);
                    case 270 -> result.setRGB(y, width - 1 - x, pixel);
                    default -> result.setRGB(x, y, pixel);
                }
            }
        }
        return result;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao codificar PNG", exception);
        }
    }

    private static byte[] transparentPng() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return png(image);
    }

    private static String resolveTexture(ResolvedModel model, String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        if ("#missing".equals(variable)) {
            return null;
        }
        if (variable.startsWith("#")) {
            String resolved = model.texture(variable.substring(1));
            return resolved == null || resolved.startsWith("#") ? null : resolved;
        }
        return variable;
    }

    private static int normalizeRotation(int rotation) {
        int value = rotation % 360;
        if (value < 0) {
            value += 360;
        }
        return switch (value) {
            case 90, 180, 270 -> value;
            default -> 0;
        };
    }

    private static float round(float value) {
        return Math.round(value * 10000f) / 10000f;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : fallback;
    }

    private static float[] floats(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        float[] result = new float[array.size()];
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            result[index] = value.getAsFloat();
        }
        return result;
    }

    private record TileKey(String textureKey, int rotation) {
    }

    private record Tile(BufferedImage image, int originalWidth, int originalHeight,
                        int rotatedWidth, int rotatedHeight) {
        static Tile transparent() {
            return new Tile(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 1, 1, 1, 1);
        }
    }

    private record Placement(int x, int y, Tile tile) {
    }
}
