package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Uses the vanilla player armor geometry/controller pair (as in Geyser Rainbow). */
public final class ArmorAttachableWriter {
    public JsonObject write(String identifier, String slot, String texture) {
        String piece = switch (slot) {
            case "head" -> "helmet";
            case "chest" -> "chestplate";
            case "legs" -> "leggings";
            case "feet" -> "boots";
            default -> throw new IllegalArgumentException("Slot de armadura invalido: " + slot);
        };
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "armor");
        materials.addProperty("enchanted", "armor_enchanted");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texture);
        textures.addProperty("enchanted", "textures/misc/enchanted_actor_glint");
        description.add("textures", textures);
        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", "geometry.player.armor." + piece);
        description.add("geometry", geometry);
        JsonObject scripts = new JsonObject();
        scripts.addProperty("parent_setup", "v." + (slot.equals("head") ? "helmet" :
                slot.equals("chest") ? "chest" : slot.equals("legs") ? "leg" : "boot") + "_layer_visible = false;");
        description.add("scripts", scripts);
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.armor");
        description.add("render_controllers", controllers);
        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }
}
