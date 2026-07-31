package com.zizazr.kjsgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Describes an editable recipe type: its layout (slots + decorations), the
 * parameters the user can tweak and which codegen handler turns instances of
 * it into KubeJS code.
 *
 * @param id           unique id, e.g. "kjsgen:shaped" or "kjsgen:create_pressing"
 * @param modId        mod the recipe type belongs to ("minecraft", "create", ...) — used for filtering
 * @param iconItem     item id shown on the picker card
 * @param canvasWidth  layout canvas size in px (JEI category style, e.g. 116x54 for crafting)
 * @param canvasHeight layout canvas size in px
 * @param slots        slot layout
 * @param decorations  arrows/flames/labels
 * @param parameters   editable parameters
 * @param codegenId    id of the registered codegen handler
 * @param requiresMod  optional mod id that must be present for the exported script to work (adds Platform.isLoaded hint)
 * @param editorKind   special editor UI for this type: {@code ""} = the standard fixed-slot canvas,
 *                     {@code "grid"} = a resizable WxH crafting grid (Create mechanical crafting),
 *                     {@code "sequence"} = a vertical, reorderable stage list (Create sequenced assembly)
 * @param jeiCategory  optional JEI category UID this type mirrors (e.g. "create:pressing"), letting the
 *                     "Edit in kjsgen" JEI button map a shown recipe onto this hand-authored layout; empty
 *                     means the type has no JEI mapping (the button is hidden for that category)
 */
public record RecipeTypeDefinition(
        String id,
        String modId,
        String iconItem,
        int canvasWidth,
        int canvasHeight,
        List<SlotDefinition> slots,
        List<LayoutDecoration> decorations,
        List<ParameterDefinition> parameters,
        String codegenId,
        String requiresMod,
        String editorKind,
        String jeiCategory
) {
    /** Backwards-compatible constructor for the common standard-canvas type. */
    public RecipeTypeDefinition(String id, String modId, String iconItem, int canvasWidth, int canvasHeight,
                                List<SlotDefinition> slots, List<LayoutDecoration> decorations,
                                List<ParameterDefinition> parameters, String codegenId, String requiresMod) {
        this(id, modId, iconItem, canvasWidth, canvasHeight, slots, decorations, parameters,
                codegenId, requiresMod, "", "");
    }

    /** Backwards-compatible constructor for a type with a custom editor kind but no JEI mapping. */
    public RecipeTypeDefinition(String id, String modId, String iconItem, int canvasWidth, int canvasHeight,
                                List<SlotDefinition> slots, List<LayoutDecoration> decorations,
                                List<ParameterDefinition> parameters, String codegenId, String requiresMod,
                                String editorKind) {
        this(id, modId, iconItem, canvasWidth, canvasHeight, slots, decorations, parameters,
                codegenId, requiresMod, editorKind, "");
    }
    /** Translation key of the display name, e.g. "kjsgen.recipe_type.kjsgen.shaped". */
    public String translationKey() {
        return "kjsgen.recipe_type." + id.replace(':', '.');
    }

    public Optional<SlotDefinition> slot(String key) {
        return slots.stream().filter(s -> s.key().equals(key)).findFirst();
    }

    public List<SlotDefinition> slotsByRole(SlotRole role) {
        return slots.stream().filter(s -> s.role() == role).toList();
    }

    public Optional<ParameterDefinition> parameter(String key) {
        return parameters.stream().filter(p -> p.key().equals(key)).findFirst();
    }

    /**
     * Whether the mod this type's syntax depends on is currently loaded, so the
     * type picker can hide entries whose generated script would fail to load
     * (e.g. the {@code mekanism_*} templates when Mekanism isn't installed).
     * "minecraft" and "morejs" are pseudo-values that don't gate visibility,
     * matching {@code ScriptAssembler}'s export-time condition logic.
     */
    public boolean isAvailable() {
        if (requiresMod.isEmpty() || requiresMod.equals("minecraft") || requiresMod.equals("morejs")) {
            return true;
        }
        return dev.architectury.platform.Platform.isModLoaded(requiresMod);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("mod", modId);
        json.addProperty("icon", iconItem);
        json.addProperty("width", canvasWidth);
        json.addProperty("height", canvasHeight);
        JsonArray slotArr = new JsonArray();
        slots.forEach(s -> slotArr.add(s.toJson()));
        json.add("slots", slotArr);
        JsonArray decoArr = new JsonArray();
        decorations.forEach(d -> decoArr.add(d.toJson()));
        json.add("decorations", decoArr);
        JsonArray paramArr = new JsonArray();
        parameters.forEach(p -> paramArr.add(p.toJson()));
        json.add("parameters", paramArr);
        json.addProperty("codegen", codegenId);
        if (!requiresMod.isEmpty()) json.addProperty("requiresMod", requiresMod);
        if (!editorKind.isEmpty()) json.addProperty("editorKind", editorKind);
        if (!jeiCategory.isEmpty()) json.addProperty("jei", jeiCategory);
        return json;
    }

    public static RecipeTypeDefinition fromJson(JsonObject json) {
        List<SlotDefinition> slots = new ArrayList<>();
        if (json.has("slots")) {
            json.getAsJsonArray("slots").forEach(e -> slots.add(SlotDefinition.fromJson(e.getAsJsonObject())));
        }
        List<LayoutDecoration> decorations = new ArrayList<>();
        if (json.has("decorations")) {
            json.getAsJsonArray("decorations").forEach(e -> decorations.add(LayoutDecoration.fromJson(e.getAsJsonObject())));
        }
        List<ParameterDefinition> parameters = new ArrayList<>();
        if (json.has("parameters")) {
            json.getAsJsonArray("parameters").forEach(e -> parameters.add(ParameterDefinition.fromJson(e.getAsJsonObject())));
        }
        return new RecipeTypeDefinition(
                json.get("id").getAsString(),
                json.has("mod") ? json.get("mod").getAsString() : "minecraft",
                json.has("icon") ? json.get("icon").getAsString() : "minecraft:crafting_table",
                json.has("width") ? json.get("width").getAsInt() : 116,
                json.has("height") ? json.get("height").getAsInt() : 54,
                List.copyOf(slots),
                List.copyOf(decorations),
                List.copyOf(parameters),
                json.has("codegen") ? json.get("codegen").getAsString() : "",
                json.has("requiresMod") ? json.get("requiresMod").getAsString() : "",
                json.has("editorKind") ? json.get("editorKind").getAsString() : "",
                json.has("jei") ? json.get("jei").getAsString() : ""
        );
    }
}
