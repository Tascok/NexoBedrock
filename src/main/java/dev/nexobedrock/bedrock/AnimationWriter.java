package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Gera as animacoes de display (mao, terceira pessoa, cabeca) a partir do
 * bloco "display" do modelo Java. Os nomes dos bones batem com o
 * GeometryWriter.
 */
public final class AnimationWriter {
    private static final float HEAD_SCALE = 0.625f;

    public JsonObject write(String animationPrefix, JsonObject display) {
        JsonObject animations = new JsonObject();

        animations.add(animationPrefix + ".thirdperson_main_hand",
                thirdPerson(display, "thirdperson_righthand", false));
        animations.add(animationPrefix + ".thirdperson_off_hand",
                thirdPerson(display, "thirdperson_lefthand", true));
        animations.add(animationPrefix + ".head", head(display));
        animations.add(animationPrefix + ".firstperson_main_hand",
                firstPerson(display, "firstperson_righthand", false));
        animations.add(animationPrefix + ".firstperson_off_hand",
                firstPerson(display, "firstperson_lefthand", true));

        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", animations);
        return root;
    }

    private JsonObject thirdPerson(JsonObject display, String slot, boolean leftHand) {
        JsonObject animation = animation();
        JsonObject bones = new JsonObject();

        JsonObject transform = object(display, slot);
        JsonObject x = new JsonObject();
        if (transform != null) {
            addPosition(x, transform, leftHand, false, false);
            addRotationX(x, transform);
            addScale(x, transform, 1f);
        }
        if (!x.isEmpty()) {
            bones.add("root_x", x);
        }
        addRotationY(bones, transform);
        addRotationZ(bones, transform);

        JsonObject root = new JsonObject();
        root.add("rotation", array(90f, 0f, 0f));
        root.add("position", array(0f, 13f, -3f));
        bones.add("root", root);

        animation.add("bones", bones);
        return animation;
    }

    private JsonObject firstPerson(JsonObject display, String slot, boolean leftHand) {
        JsonObject animation = animation();
        JsonObject bones = new JsonObject();

        JsonObject root = new JsonObject();
        root.add("rotation", array(90f, 60f, -40f));
        root.add("position", array(4f, 10f, 4f));
        root.add("scale", array(1.5f, 1.5f, 1.5f));
        bones.add("root", root);

        JsonObject transform = object(display, slot);
        JsonObject x = new JsonObject();
        if (transform != null) {
            addPosition(x, transform, leftHand, true, false);
            if (!addRotationX(x, transform)) {
                // j2b usa essa rotacao minima quando o slot existe mas nao
                // declara rotation, evitando que o item suma na tela.
                x.add("rotation", array(0.1f, 0.1f, 0.1f));
            }
            addScale(x, transform, 1f);
        } else {
            x.add("rotation", array(0.1f, 0.1f, 0.1f));
        }
        bones.add("root_x", x);
        addRotationY(bones, transform);
        addRotationZ(bones, transform);

        animation.add("bones", bones);
        return animation;
    }

    private JsonObject head(JsonObject display) {
        JsonObject animation = animation();
        JsonObject bones = new JsonObject();

        JsonObject transform = object(display, "head");
        JsonObject x = new JsonObject();
        if (transform != null) {
            addPosition(x, transform, false, false, true);
            addRotationX(x, transform);
            addScale(x, transform, HEAD_SCALE);
        } else {
            x.add("scale", array(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE));
        }
        bones.add("root_x", x);
        addRotationY(bones, transform);
        addRotationZ(bones, transform);

        JsonObject root = new JsonObject();
        root.add("position", array(0f, 19.9f, 0f));
        bones.add("root", root);

        animation.add("bones", bones);
        return animation;
    }

    private JsonObject animation() {
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", true);
        return animation;
    }

    private void addPosition(JsonObject bone, JsonObject transform, boolean leftHand,
                             boolean firstPerson, boolean head) {
        float[] translation = floats(transform, "translation");
        if (translation == null || translation.length < 3) {
            return;
        }
        float x = translation[0];
        float y = translation[1];
        float z = translation[2];
        if (head) {
            x *= HEAD_SCALE;
            y *= HEAD_SCALE;
            z *= HEAD_SCALE;
        }
        if (firstPerson) {
            // j2b: primeira pessoa direita inverte X e Z; a esquerda inverte
            // apenas Z.
            bone.add("position", array(leftHand ? x : -x, y, -z));
        } else if (leftHand) {
            bone.add("position", array(x, y, z));
        } else {
            bone.add("position", array(-x, y, z));
        }
    }

    private boolean addRotationX(JsonObject bone, JsonObject transform) {
        float[] rotation = floats(transform, "rotation");
        if (rotation == null || rotation.length < 3) {
            return false;
        }
        bone.add("rotation", array(-rotation[0], 0f, 0f));
        return true;
    }

    private void addRotationY(JsonObject bones, JsonObject transform) {
        if (transform == null) {
            return;
        }
        float[] rotation = floats(transform, "rotation");
        if (rotation == null || rotation.length < 3) {
            return;
        }
        JsonObject bone = new JsonObject();
        bone.add("rotation", array(0f, -rotation[1], 0f));
        bones.add("root_y", bone);
    }

    private void addRotationZ(JsonObject bones, JsonObject transform) {
        if (transform == null) {
            return;
        }
        float[] rotation = floats(transform, "rotation");
        if (rotation == null || rotation.length < 3) {
            return;
        }
        JsonObject bone = new JsonObject();
        bone.add("rotation", array(0f, 0f, rotation[2]));
        bones.add("root_z", bone);
    }

    private void addScale(JsonObject bone, JsonObject transform, float multiplier) {
        float[] scale = floats(transform, "scale");
        if (scale == null || scale.length < 3) {
            if (multiplier != 1f) {
                bone.add("scale", array(multiplier, multiplier, multiplier));
            }
            return;
        }
        bone.add("scale", array(scale[0] * multiplier, scale[1] * multiplier,
                scale[2] * multiplier));
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static float[] floats(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
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

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(Math.round(value * 10000f) / 10000f);
        }
        return array;
    }
}
