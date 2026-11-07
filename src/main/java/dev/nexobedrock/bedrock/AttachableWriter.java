package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Attachable que faz o item Bedrock usar a geometria convertida. O identifier
 * precisa ser exatamente o bedrock_identifier do item.
 */
public final class AttachableWriter {
    private static final String DEFAULT_MATERIAL = "entity_alphatest_one_sided";

    public JsonObject write(String bedrockIdentifier, String texturePath,
                            String geometryIdentifier, String animationPrefix,
                            String material) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", bedrockIdentifier);

        JsonObject materials = new JsonObject();
        String resolvedMaterial = material == null || material.isBlank()
                ? DEFAULT_MATERIAL : material;
        materials.addProperty("default", resolvedMaterial);
        materials.addProperty("enchanted", resolvedMaterial);
        description.add("materials", materials);

        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);
        textures.addProperty("enchanted", "textures/misc/enchanted_item_glint");
        description.add("textures", textures);

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", geometryIdentifier);
        description.add("geometry", geometry);

        JsonObject scripts = new JsonObject();
        JsonArray preAnimation = new JsonArray();
        preAnimation.add("v.main_hand = c.item_slot == 'main_hand';");
        preAnimation.add("v.off_hand = c.item_slot == 'off_hand';");
        preAnimation.add("v.head = c.item_slot == 'head';");
        scripts.add("pre_animation", preAnimation);

        JsonArray animate = new JsonArray();
        animate.add(conditional("thirdperson_main_hand", "v.main_hand && !c.is_first_person"));
        animate.add(conditional("thirdperson_off_hand", "v.off_hand && !c.is_first_person"));
        animate.add(conditional("thirdperson_head", "v.head && !c.is_first_person"));
        animate.add(conditional("firstperson_main_hand", "v.main_hand && c.is_first_person"));
        animate.add(conditional("firstperson_off_hand", "v.off_hand && c.is_first_person"));
        animate.add(conditional("firstperson_head", "c.is_first_person && v.head"));
        scripts.add("animate", animate);
        description.add("scripts", scripts);

        JsonObject animations = new JsonObject();
        animations.addProperty("thirdperson_main_hand",
                animationPrefix + ".thirdperson_main_hand");
        animations.addProperty("thirdperson_off_hand",
                animationPrefix + ".thirdperson_off_hand");
        animations.addProperty("thirdperson_head", animationPrefix + ".head");
        animations.addProperty("firstperson_main_hand",
                animationPrefix + ".firstperson_main_hand");
        animations.addProperty("firstperson_off_hand",
                animationPrefix + ".firstperson_off_hand");
        animations.addProperty("firstperson_head", "animation.nexobedrock.disable");
        description.add("animations", animations);

        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.item_default");
        description.add("render_controllers", renderControllers);

        JsonObject attachableDescription = new JsonObject();
        attachableDescription.add("description", description);

        JsonObject attachable = new JsonObject();
        attachable.addProperty("format_version", "1.10.0");
        attachable.add("minecraft:attachable", attachableDescription);
        return attachable;
    }

    private static JsonObject conditional(String name, String condition) {
        JsonObject object = new JsonObject();
        object.addProperty(name, condition);
        return object;
    }
}
