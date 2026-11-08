package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.ModelEngineBlueprintReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts basic Blockbench bone keyframes to Bedrock entity animation channels. */
public final class ModelEngineAnimationWriter {
    public record Result(JsonObject animations, Map<String, String> identifiers,
                         List<String> warnings) {
        public Result {
            identifiers = Map.copyOf(identifiers);
            warnings = List.copyOf(new java.util.LinkedHashSet<>(warnings));
        }
    }

    public Result write(String modelId, ModelEngineBlueprintReader.Blueprint blueprint) {
        JsonObject definitions = new JsonObject();
        java.util.LinkedHashMap<String, String> identifiers = new java.util.LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (JsonElement rawClip : blueprint.animationData()) {
            if (!rawClip.isJsonObject()) continue;
            JsonObject clip = rawClip.getAsJsonObject();
            String name = string(clip, "name");
            if (name == null || name.isBlank()) continue;
            String safeName = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            String identifier = "animation.nexobedrock." + safeId(modelId) + "." + safeId(safeName);
            JsonObject animation = new JsonObject();
            animation.addProperty("animation_length", number(clip, "length", 0));
            String loop = string(clip, "loop");
            if ("loop".equalsIgnoreCase(loop)) animation.addProperty("loop", true);
            else if ("hold".equalsIgnoreCase(loop)) animation.addProperty("loop", "hold_on_last_frame");
            else animation.addProperty("loop", false);

            JsonObject bones = new JsonObject();
            JsonObject animators = object(clip, "animators");
            if (animators != null) {
                for (Map.Entry<String, JsonElement> animatorEntry : animators.entrySet()) {
                    String boneName = blueprint.boneNamesByUuid().get(animatorEntry.getKey());
                    if (boneName == null || !animatorEntry.getValue().isJsonObject()) {
                        warnings.add("Animation " + name + " references an unknown bone UUID.");
                        continue;
                    }
                    JsonObject animator = animatorEntry.getValue().getAsJsonObject();
                    JsonArray keyframes = array(animator, "keyframes");
                    if (keyframes == null) continue;
                    JsonObject bone = new JsonObject();
                    for (String channel : List.of("rotation", "position", "scale")) {
                        JsonObject track = channel(keyframes, channel, warnings, name);
                        if (!track.isEmpty()) bone.add(channel, track);
                    }
                    if (!bone.isEmpty()) bones.add(boneName, bone);
                }
            }
            if (!bones.isEmpty()) animation.add("bones", bones);
            definitions.add(identifier, animation);
            identifiers.put(name, identifier);
        }

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", definitions);
        return new Result(root, identifiers, warnings);
    }

    private JsonObject channel(JsonArray keyframes, String channel, List<String> warnings,
                               String clipName) {
        JsonObject track = new JsonObject();
        for (JsonElement rawKeyframe : keyframes) {
            if (!rawKeyframe.isJsonObject()) continue;
            JsonObject keyframe = rawKeyframe.getAsJsonObject();
            if (!channel.equals(string(keyframe, "channel"))) continue;
            JsonArray points = array(keyframe, "data_points");
            if (points == null || points.isEmpty() || !points.get(0).isJsonObject()) {
                warnings.add("Animation " + clipName + " has a keyframe without vector data.");
                continue;
            }
            JsonObject point = points.get(0).getAsJsonObject();
            float x = coordinate(point, "x", channel.equals("scale") ? 1 : 0);
            float y = coordinate(point, "y", channel.equals("scale") ? 1 : 0);
            float z = coordinate(point, "z", channel.equals("scale") ? 1 : 0);
            if (channel.equals("rotation")) {
                x = -x;
                y = -y;
            } else if (channel.equals("position")) {
                y = -y;
            }
            JsonArray value = new JsonArray();
            value.add(x);
            value.add(y);
            value.add(z);
            String interpolation = string(keyframe, "interpolation");
            if (interpolation != null && !interpolation.equalsIgnoreCase("linear")
                    && !interpolation.equalsIgnoreCase("step")) {
                warnings.add("Animation " + clipName + " interpolation '" + interpolation
                        + "' approximated as linear.");
            }
            track.add(Double.toString(number(keyframe, "time", 0)), value);
        }
        return track;
    }

    private String safeId(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private float coordinate(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return Float.parseFloat(element.getAsString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double number(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    private JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
