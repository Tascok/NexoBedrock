package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.pack.JavaItemEntry;
import dev.nexobedrock.pack.JavaModelResolver;
import dev.nexobedrock.pack.JavaPack;
import dev.nexobedrock.pack.ResolvedModel;
import dev.nexobedrock.util.Json;
import dev.nexobedrock.util.Names;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adds Nexo bow/crossbow pull poses to the existing attachable. */
public final class RangedItemWriter {
    private final JavaModelResolver resolver = new JavaModelResolver();
    private final TextureAtlasBuilder atlasBuilder = new TextureAtlasBuilder();
    private final GeometryWriter geometryWriter = new GeometryWriter();

    public Map<String, byte[]> add(JavaPack pack, PluginConfig config, JavaItemEntry entry,
                                   String modelKey, String id, JsonObject attachable,
                                   List<String> warnings) throws IOException {
        boolean crossbow = "minecraft:crossbow".equals(entry.javaItem());
        if (!crossbow && !"minecraft:bow".equals(entry.javaItem())) return Map.of();
        String[] poses = crossbow
                ? new String[]{"0", "1", "2", "charged", "firework"}
                : new String[]{"0", "1", "2"};
        String[] modelSuffix = crossbow
                ? new String[]{"_0", "_1", "_2", "_2", "_charged"}
                : new String[]{"_0", "_1", "_2"};
        PoseDefinition poseDefinition = modelsFromDefinition(pack, entry, modelKey, crossbow);
        String[] models = poseDefinition == null ? null : poseDefinition.models();
        Map<String, byte[]> assets = new LinkedHashMap<>();
        JsonObject description = attachable.getAsJsonObject("minecraft:attachable")
                .getAsJsonObject("description");
        JsonObject geometries = new JsonObject();
        JsonObject textures = new JsonObject();
        List<String> available = new ArrayList<>();
        available.add("default");
        for (int i = 0; i < poses.length; i++) {
            String pose = poses[i];
            String variantKey = models == null ? Names.normalizeKey(modelKey + modelSuffix[i], "minecraft")
                    : models[i];
            ResolvedModel variant = resolver.resolve(pack, variantKey);
            if (variant == null || !variant.hasElements()) {
                if (i < 3) {
                    warnings.add("Modelo de carregamento ausente: " + variantKey);
                    return Map.of();
                }
                break;
            }
            AtlasResult atlas = atlasBuilder.build(pack, variant, config);
            if (atlas.uvs().isEmpty() || !atlasBuilder.hasVisiblePixel(atlas.png())) {
                warnings.add("Modelo de carregamento sem UV/textura: " + variantKey);
                return Map.of();
            }
            String key = "pose_" + pose;
            String texturePath = "textures/nexobedrock/atlas/" + id + "_" + key;
            String geoId = Names.geometryIdentifier(id + "_" + key);
            String geoPath = "models/entity/"
                    + geoId.substring("geometry.nexobedrock.".length()) + ".geo.json";
            textures.addProperty(key, texturePath);
            geometries.addProperty(key, geoId);
            assets.put(texturePath + ".png", atlas.png());
            assets.put(geoPath, Json.prettyBytes(geometryWriter.write(geoId, variant, atlas)));
            available.add(key);
            warnings.addAll(atlas.warnings());
        }
        if (available.size() < 4) return Map.of();
        for (Map.Entry<String, com.google.gson.JsonElement> reference : geometries.entrySet()) {
            description.getAsJsonObject("geometry").add(reference.getKey(), reference.getValue());
        }
        for (Map.Entry<String, com.google.gson.JsonElement> reference : textures.entrySet()) {
            description.getAsJsonObject("textures").add(reference.getKey(), reference.getValue());
        }
        JsonObject scripts = description.getAsJsonObject("scripts");
        JsonArray preAnimation = scripts.getAsJsonArray("pre_animation");
        // The official Bedrock bow attachable uses these *remaining-duration*
        // queries, not item_in_use_duration. They are ticks, as also evidenced
        // by its 10-tick charge divisor. Java bow's use_duration is scaled by
        // .05 (ticks -> seconds), whereas crossbow/pull is normalized 0..1.
        float first = poseDefinition == null ? 0.65f : poseDefinition.firstThreshold();
        float second = poseDefinition == null ? 0.9f : poseDefinition.secondThreshold();
        float tickMultiplier = crossbow ? 25f : 20f;
        preAnimation.add("v.nexo_pull_ticks = math.max(0.0, q.main_hand_item_max_duration - (q.main_hand_item_use_duration - q.frame_alpha + 1.0));");
        preAnimation.add("v.nexo_pose = q.main_hand_item_use_duration <= 0.0 ? 0 : "
                + "(v.nexo_pull_ticks < " + decimal(first * tickMultiplier) + " ? 1 : "
                + "(v.nexo_pull_ticks < " + decimal(second * tickMultiplier) + " ? 2 : 3));");
        if (crossbow) {
            preAnimation.add("v.nexo_pose = q.item_is_charged(c.item_slot == 'off_hand' ? 'off_hand' : 'main_hand') ? 4 : v.nexo_pose;");
            // The Nexo firework model is present but Bedrock has no documented
            // attachable query to distinguish firework ammunition from arrows.
            warnings.add("Besta: estado carregado com fogos utiliza pose de flecha (sem consulta Molang para tipo de municao)");
        }
        String controllerId = "controller.render.nexobedrock." + id;
        JsonObject controller = new JsonObject();
        JsonObject arrays = new JsonObject();
        JsonObject textureArrays = new JsonObject();
        JsonObject geometryArrays = new JsonObject();
        JsonArray textureFrames = new JsonArray();
        JsonArray geometryFrames = new JsonArray();
        for (String key : available) {
            textureFrames.add("texture." + key);
            geometryFrames.add("geometry." + key);
        }
        textureArrays.add("array.nexo_textures", textureFrames);
        geometryArrays.add("array.nexo_geometries", geometryFrames);
        arrays.add("textures", textureArrays);
        arrays.add("geometries", geometryArrays);
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "array.nexo_geometries[v.nexo_pose]");
        JsonArray materials = new JsonArray();
        JsonObject material = new JsonObject();
        material.addProperty("*", "v.is_enchanted ? material.enchanted : material.default");
        materials.add(material);
        controller.add("materials", materials);
        JsonArray controllerTextures = new JsonArray();
        controllerTextures.add("array.nexo_textures[v.nexo_pose]");
        controllerTextures.add("texture.enchanted");
        controller.add("textures", controllerTextures);
        JsonObject controllers = new JsonObject();
        controllers.add(controllerId, controller);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("render_controllers", controllers);
        JsonArray references = new JsonArray();
        references.add(controllerId);
        description.add("render_controllers", references);
        assets.put("render_controllers/" + id + ".render_controllers.json", Json.prettyBytes(root));
        return Map.copyOf(assets);
    }

    private PoseDefinition modelsFromDefinition(JavaPack pack, JavaItemEntry entry,
                                                String standbyKey, boolean crossbow) {
        JsonObject definition = pack.itemDefinitions().get(entry.itemModel());
        if (definition == null) {
            definition = pack.itemDefinitions().get("nexo:" + entry.id());
        }
        if (definition == null) {
            for (Map.Entry<String, JsonObject> candidate : pack.itemDefinitions().entrySet()) {
                if ("minecraft".equals(Names.namespaceOf(candidate.getKey()))) continue;
                JsonObject root = object(candidate.getValue(), "model");
                JsonObject standby = crossbow ? object(object(root, "fallback"), "on_false")
                        : object(root, "on_false");
                if (standbyKey.equals(modelId(standby, standbyKey))) {
                    definition = candidate.getValue();
                    break;
                }
            }
        }
        if (definition == null) return null;
        JsonObject node = object(definition, "model");
        if (node == null) return null;
        if (crossbow) {
            JsonObject select = node;
            JsonObject fallback = object(select, "fallback");
            JsonObject pulling = object(fallback, "on_true");
            PoseDefinition range = rangeModels(pulling, standbyKey, 5);
            if (range == null) return null;
            String[] variants = range.models();
            JsonArray cases = array(select, "cases");
            if (cases != null) for (JsonElement caseElement : cases) {
                if (!caseElement.isJsonObject()) continue;
                JsonObject caseNode = caseElement.getAsJsonObject();
                String when = string(caseNode, "when");
                String model = modelId(object(caseNode, "model"), standbyKey);
                if ("arrow".equals(when)) variants[3] = model;
                if ("rocket".equals(when)) variants[4] = model;
            }
            if (variants[3] == null) variants[3] = variants[2];
            if (variants[4] == null) variants[4] = variants[3];
            return range;
        }
        return rangeModels(object(node, "on_true"), standbyKey, 3);
    }

    private PoseDefinition rangeModels(JsonObject range, String standby, int length) {
        JsonArray entries = array(range, "entries");
        if (entries == null || entries.size() < 3) return null;
        String[] paths = new String[length];
        for (int i = 0; i < 3; i++) {
            if (!entries.get(i).isJsonObject()) return null;
            paths[i] = modelId(object(entries.get(i).getAsJsonObject(), "model"), standby);
            if (paths[i] == null) return null;
        }
        float first = threshold(entries.get(1).getAsJsonObject());
        float second = threshold(entries.get(2).getAsJsonObject());
        if (!Float.isFinite(first) || !Float.isFinite(second) || first <= 0 || second <= first) {
            return null;
        }
        return new PoseDefinition(paths, first, second);
    }

    private float threshold(JsonObject entry) {
        JsonElement value = entry.get("threshold");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsFloat() : Float.NaN;
    }

    private String decimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String modelId(JsonObject node, String standby) {
        String id = string(node, "model");
        return id == null ? null : Names.normalizeKey(id, Names.namespaceOf(standby));
    }

    private JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private JsonArray array(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private String string(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private record PoseDefinition(String[] models, float firstThreshold, float secondThreshold) {}
}
