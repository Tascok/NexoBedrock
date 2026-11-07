package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.conversion.ConvertedItem;
import dev.nexobedrock.util.Json;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Structural validation for generated Bedrock attachables and their atlases. */
public final class BedrockPackValidator {
    private static final float UV_EPSILON = 0.05f;

    public void validate(Path mcpack, Iterable<ConvertedItem> items) throws IOException {
        try (ZipFile zip = new ZipFile(mcpack.toFile())) {
            Map<String, byte[]> cache = new HashMap<>();
            require(zip, cache, "manifest.json");
            require(zip, cache, "textures/item_texture.json");

            for (ConvertedItem item : items) {
                byte[] iconBytes = require(zip, cache, item.iconPath() + ".png");
                BufferedImage icon = ImageIO.read(new ByteArrayInputStream(iconBytes));
                if (icon == null || icon.getWidth() < 1 || icon.getHeight() < 1) {
                    throw invalid(item, "icone nao e PNG valido");
                }
                if (item.geometryPath() == null && item.attachablePath() != null) {
                    validateArmor(item, zip, cache);
                    continue;
                }
                if (item.flat()) {
                    continue;
                }
                byte[] atlasBytes = require(zip, cache, item.atlasPath() + ".png");
                BufferedImage atlas = ImageIO.read(new ByteArrayInputStream(atlasBytes));
                if (atlas == null || atlas.getWidth() < 1 || atlas.getHeight() < 1) {
                    throw invalid(item, "atlas de textura nao e PNG decodificavel");
                }
                boolean hasVisiblePixel = false;
                for (int y = 0; y < atlas.getHeight() && !hasVisiblePixel; y++) {
                    for (int x = 0; x < atlas.getWidth(); x++) {
                        if ((atlas.getRGB(x, y) >>> 24) != 0) {
                            hasVisiblePixel = true;
                            break;
                        }
                    }
                }
                if (!hasVisiblePixel) {
                    throw invalid(item, "atlas de textura esta totalmente transparente");
                }

                JsonObject geometry = json(zip, cache, item.geometryPath());
                validateGeometry(item, geometry, item.geometryId(), atlas.getWidth(), atlas.getHeight());
                JsonObject attachable = json(zip, cache, item.attachablePath());
                validateAttachable(item, attachable);
                validateRanged(item, zip, cache, attachable);
                JsonObject animations = json(zip, cache, item.animationPath());
                validateAnimationReferences(item, attachable, animations);
            }
        }
    }

    private void validateArmor(ConvertedItem item, ZipFile zip, Map<String, byte[]> cache)
            throws IOException {
        String slot = dev.nexobedrock.pack.NexoArmorIndex.slot(item.entry().javaItem());
        if (slot == null) throw invalid(item, "slot de armadura invalido");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                require(zip, cache, item.atlasPath() + ".png")));
        if (image == null || image.getWidth() != 64 || image.getHeight() != 32) {
            throw invalid(item, "camada de armadura deve ter dimensoes 64x32");
        }
        boolean visible = false;
        for (int y = 0; y < image.getHeight() && !visible; y++)
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) != 0) { visible = true; break; }
        if (!visible) throw invalid(item, "camada de armadura transparente");
        JsonObject desc = object(json(zip, cache, item.attachablePath()),
                "minecraft:attachable", item, "attachable ausente");
        desc = object(desc, "description", item, "description ausente");
        if (!item.bedrockId().equals(string(desc, "identifier"))
                || !item.atlasPath().equals(string(object(desc, "textures", item,
                "textura ausente"), "default"))) throw invalid(item, "referencia de armadura invalida");
        String piece = switch (slot) {
            case "head" -> "helmet";
            case "chest" -> "chestplate";
            case "legs" -> "leggings";
            default -> "boots";
        };
        if (!("geometry.player.armor." + piece).equals(string(object(desc, "geometry", item,
                 "geometry ausente"), "default"))
                || !"controller.render.armor".equals(array(desc, "render_controllers", item,
                "controller ausente").get(0).getAsString())) {
            throw invalid(item, "geometria/controller do slot incorreto");
        }
    }

    public void validateMappings(Iterable<ConvertedItem> items, byte[] mappingBytes)
            throws IOException {
        JsonObject root;
        try {
            root = Json.parseObject(mappingBytes);
        } catch (RuntimeException exception) {
            throw new IOException("NexoBedrock: mappings Geyser nao sao JSON valido", exception);
        }
        if (!root.has("format_version") || root.get("format_version").getAsInt() != 2) {
            throw new IOException("NexoBedrock: mappings Geyser devem usar format_version 2");
        }
        JsonElement mappedItemsElement = root.get("items");
        if (mappedItemsElement == null || !mappedItemsElement.isJsonObject()) {
            throw new IOException("NexoBedrock: objeto items ausente nos mappings Geyser");
        }
        JsonObject mappedItems = mappedItemsElement.getAsJsonObject();
        for (ConvertedItem item : items) {
            JsonElement definitionsElement = mappedItems.get(item.entry().javaItem());
            if (definitionsElement == null || !definitionsElement.isJsonArray()) {
                throw invalid(item, "item base ausente nos mappings Geyser");
            }
            boolean found = false;
            for (JsonElement definitionElement : definitionsElement.getAsJsonArray()) {
                if (!definitionElement.isJsonObject()) {
                    continue;
                }
                JsonObject definition = definitionElement.getAsJsonObject();
                if (!item.bedrockId().equals(string(definition, "bedrock_identifier"))) {
                    continue;
                }
                JsonElement optionsElement = definition.get("bedrock_options");
                if (optionsElement == null || !optionsElement.isJsonObject()
                        || !item.iconKey().equals(string(
                        optionsElement.getAsJsonObject(), "icon"))) {
                    throw invalid(item, "icon Geyser nao aponta para o item_texture gerado");
                }
                if (item.attachablePath() != null && item.geometryPath() == null) {
                    JsonObject components = object(definition, "components", item,
                            "componentes de armadura ausentes");
                    if (!dev.nexobedrock.pack.NexoArmorIndex.slot(item.entry().javaItem())
                            .equals(string(object(components, "minecraft:equippable", item,
                                    "equippable ausente"), "slot"))) {
                        throw invalid(item, "slot Geyser incorreto");
                    }
                }
                found = true;
                break;
            }
            if (!found) {
                throw invalid(item, "bedrock_identifier ausente dos mappings Geyser");
            }
        }
    }

    private void validateGeometry(ConvertedItem item, JsonObject root, String expectedId,
                                  int atlasWidth, int atlasHeight) throws IOException {
        JsonArray geometries = array(root, "minecraft:geometry", item, "geometrias ausentes");
        if (geometries.size() != 1) {
            throw invalid(item, "esperava exatamente uma geometria Bedrock");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = object(geometry, "description", item, "description ausente");
        if (!expectedId.equals(string(description, "identifier"))) {
            throw invalid(item, "identifier da geometria nao corresponde ao attachable");
        }
        if (integer(description, "texture_width") != atlasWidth
                || integer(description, "texture_height") != atlasHeight) {
            throw invalid(item, "dimensoes declaradas da geometria diferem do atlas PNG");
        }

        JsonArray bones = array(geometry, "bones", item, "bones ausentes");
        boolean hasBinding = false;
        int cubeCount = 0;
        for (JsonElement boneElement : bones) {
            if (!boneElement.isJsonObject()) {
                throw invalid(item, "bone nao e objeto JSON");
            }
            JsonObject bone = boneElement.getAsJsonObject();
            hasBinding |= bone.has("binding") && bone.get("binding").isJsonPrimitive();
            JsonElement cubesElement = bone.get("cubes");
            if (cubesElement == null) {
                continue;
            }
            if (!cubesElement.isJsonArray()) {
                throw invalid(item, "cubes do bone nao sao array");
            }
            for (JsonElement cubeElement : cubesElement.getAsJsonArray()) {
                if (!cubeElement.isJsonObject()) {
                    throw invalid(item, "cubo nao e objeto JSON");
                }
                JsonObject cube = cubeElement.getAsJsonObject();
                JsonArray size = array(cube, "size", item, "cubo sem size");
                if (size.size() != 3 || number(size, 0) <= 0f
                        || number(size, 1) <= 0f || number(size, 2) <= 0f) {
                    throw invalid(item, "cubo sem volume 3D positivo");
                }
                JsonObject faces = object(cube, "uv", item, "cubo sem UV por face");
                for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                    if (!faceEntry.getValue().isJsonObject()) {
                        throw invalid(item, "UV de face nao e objeto");
                    }
                    JsonObject face = faceEntry.getValue().getAsJsonObject();
                    JsonArray uv = array(face, "uv", item, "face sem uv");
                    JsonArray uvSize = array(face, "uv_size", item, "face sem uv_size");
                    if (uv.size() != 2 || uvSize.size() != 2) {
                        throw invalid(item, "uv/uv_size devem ter dois valores");
                    }
                    assertUvBounds(item, number(uv, 0), number(uvSize, 0), atlasWidth);
                    assertUvBounds(item, number(uv, 1), number(uvSize, 1), atlasHeight);
                }
                cubeCount++;
            }
        }
        if (!hasBinding) {
            throw invalid(item, "geometria nao possui bone de item-slot binding");
        }
        if (cubeCount == 0) {
            throw invalid(item, "geometria nao possui cubos; item nao e 3D");
        }
    }

    private void validateRanged(ConvertedItem item, ZipFile zip, Map<String, byte[]> cache,
                                JsonObject attachable) throws IOException {
        JsonObject description = attachable.getAsJsonObject("minecraft:attachable")
                .getAsJsonObject("description");
        JsonObject geometries = description.getAsJsonObject("geometry");
        JsonObject textures = description.getAsJsonObject("textures");
        if (geometries.size() == 1) return;
        for (Map.Entry<String, JsonElement> variant : geometries.entrySet()) {
            if (variant.getKey().equals("default")) continue;
            String geometryId = variant.getValue().getAsString();
            String atlasPath = string(textures, variant.getKey());
            if (atlasPath == null || !atlasPath.startsWith("textures/nexobedrock/atlas/")) {
                throw invalid(item, "pose sem atlas de textura: " + variant.getKey());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                    require(zip, cache, atlasPath + ".png")));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw invalid(item, "atlas de pose invalido: " + variant.getKey());
            }
            boolean visible = false;
            for (int y = 0; y < image.getHeight() && !visible; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        visible = true;
                        break;
                    }
                }
            }
            if (!visible) throw invalid(item, "atlas de pose transparente: " + variant.getKey());
            if (!geometryId.startsWith("geometry.nexobedrock.")) {
                throw invalid(item, "identifier de geometria invalido: " + geometryId);
            }
            String path = "models/entity/" + geometryId.substring("geometry.nexobedrock.".length())
                    + ".geo.json";
            validateGeometry(item, json(zip, cache, path), geometryId,
                    image.getWidth(), image.getHeight());
        }
        JsonArray controllers = array(description, "render_controllers", item,
                "render controller ausente");
        if (controllers.size() != 1) throw invalid(item, "render controller nao e exclusivo");
        String controllerId = controllers.get(0).getAsString();
        String id = controllerId.substring("controller.render.nexobedrock.".length());
        JsonObject controller = object(json(zip, cache,
                "render_controllers/" + id + ".render_controllers.json"),
                "render_controllers", item, "render controllers ausentes");
        JsonObject definition = object(controller, controllerId, item,
                "render controller nao encontrado");
        JsonObject arrays = object(definition, "arrays", item, "arrays ausentes");
        JsonArray geoFrames = array(object(arrays, "geometries", item, "geometries ausentes"),
                "array.nexo_geometries", item, "frames de geometria ausentes");
        JsonArray texFrames = array(object(arrays, "textures", item, "textures ausentes"),
                "array.nexo_textures", item, "frames de textura ausentes");
        if (geoFrames.size() != texFrames.size() || geoFrames.size() != geometries.size()) {
            throw invalid(item, "frames de geometria e atlas nao correspondem");
        }
        for (int i = 0; i < geoFrames.size(); i++) {
            String geoKey = geoFrames.get(i).getAsString().replaceFirst("^geometry\\.", "");
            String texKey = texFrames.get(i).getAsString().replaceFirst("^texture\\.", "");
            if (!geometries.has(geoKey) || !textures.has(texKey) || !geoKey.equals(texKey)) {
                throw invalid(item, "frame " + i + " sem geometria/atlas correspondente");
            }
        }
        if (!description.getAsJsonObject("scripts").getAsJsonArray("pre_animation")
                .toString().contains("v.nexo_pose")) {
            throw invalid(item, "pose de carregamento sem logica de selecao");
        }
    }

    private void validateAttachable(ConvertedItem item, JsonObject root) throws IOException {
        JsonObject description = object(object(root, "minecraft:attachable", item,
                "minecraft:attachable ausente"), "description", item, "description ausente");
        if (!item.bedrockId().equals(string(description, "identifier"))) {
            throw invalid(item, "identifier do attachable difere do bedrock_identifier");
        }
        if (!item.geometryId().equals(string(object(description, "geometry", item,
                "geometry ref ausente"), "default"))) {
            throw invalid(item, "attachable referencia geometria diferente");
        }
        if (!(item.atlasPath()).equals(string(object(description, "textures", item,
                "textures ref ausente"), "default"))) {
            throw invalid(item, "attachable nao referencia o atlas convertido");
        }
    }

    private void validateAnimationReferences(ConvertedItem item, JsonObject attachable,
                                             JsonObject animationFile) throws IOException {
        JsonObject description = object(attachable.getAsJsonObject("minecraft:attachable"),
                "description", item, "description ausente");
        JsonObject refs = object(description, "animations", item, "animations ausentes");
        JsonObject definitions = object(animationFile, "animations", item,
                "arquivo de animacoes sem definitions");
        for (Map.Entry<String, JsonElement> entry : refs.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                throw invalid(item, "referencia de animacao nao e string");
            }
            String name = entry.getValue().getAsString();
            if (name.endsWith(".disable")) {
                continue;
            }
            if (!definitions.has(name)) {
                throw invalid(item, "animacao referenciada nao existe: " + name);
            }
        }
    }

    private void assertUvBounds(ConvertedItem item, float start, float size, int limit)
            throws IOException {
        float end = start + size;
        float min = Math.min(start, end);
        float max = Math.max(start, end);
        if (!Float.isFinite(min) || !Float.isFinite(max)
                || min < -UV_EPSILON || max > limit + UV_EPSILON) {
            throw invalid(item, "UV fora do atlas: [" + min + ", " + max
                    + "] de " + limit);
        }
    }

    private byte[] require(ZipFile zip, Map<String, byte[]> cache, String path)
            throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("NexoBedrock: caminho vazio em artefato do mcpack");
        }
        byte[] existing = cache.get(path);
        if (existing != null) {
            return existing;
        }
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("NexoBedrock: arquivo ausente no mcpack: " + path);
        }
        try (var input = zip.getInputStream(entry)) {
            byte[] bytes = input.readAllBytes();
            cache.put(path, bytes);
            return bytes;
        }
    }

    private JsonObject json(ZipFile zip, Map<String, byte[]> cache, String path)
            throws IOException {
        try {
            return Json.parseObject(require(zip, cache, path));
        } catch (RuntimeException exception) {
            throw new IOException("NexoBedrock: JSON invalido no mcpack: " + path, exception);
        }
    }

    private JsonArray array(JsonObject object, String key, ConvertedItem item, String reason)
            throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw invalid(item, reason + " (" + key + ")");
        }
        return value.getAsJsonArray();
    }

    private JsonObject object(JsonObject object, String key, ConvertedItem item, String reason)
            throws IOException {
        JsonElement value = object == null ? null : object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw invalid(item, reason + " (" + key + ")");
        }
        return value.getAsJsonObject();
    }

    private String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private int integer(JsonObject object, String key) throws IOException {
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            throw new IOException("NexoBedrock: dimensao ausente/invalida: " + key, exception);
        }
    }

    private float number(JsonArray array, int index) throws IOException {
        try {
            float value = array.get(index).getAsFloat();
            if (!Float.isFinite(value)) {
                throw new NumberFormatException("non-finite");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IOException("NexoBedrock: valor numerico invalido em geometria/UV", exception);
        }
    }

    private IOException invalid(ConvertedItem item, String reason) {
        return new IOException("NexoBedrock: artefato 3D invalido para "
                + item.bedrockId() + ": " + reason);
    }
}
