package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One user-created recipe: filled slots + parameter values, referencing a
 * {@link RecipeTypeDefinition} by id.
 */
public final class RecipeInstance {
    /** Stable identity inside the project, survives renames of recipeId. */
    private final String uid;
    private String typeId;
    /** Optional explicit recipe id ("mypack:my_recipe"); empty = let KubeJS auto-generate. */
    private String recipeId = "";
    /** Optional recipe group. */
    private String group = "";
    /** Optional human comment emitted above the generated line. */
    private String comment = "";
    /** Export target file name (without .js); empty = project default. */
    private String targetFile = "";
    /** Optional mod id: recipe gets wrapped in Platform.isLoaded(...) on export. */
    private String conditionModLoaded = "";
    /** Emit an event.remove(...) line before the recipe on export, so re-export replaces it instead of duplicating. */
    private boolean replaceRecipe = true;
    private final Map<String, SlotContent> slots = new LinkedHashMap<>();
    private final Map<String, String> parameters = new LinkedHashMap<>();

    public RecipeInstance(String typeId) {
        this(UUID.randomUUID().toString(), typeId);
    }

    private RecipeInstance(String uid, String typeId) {
        this.uid = uid;
        this.typeId = typeId;
    }

    public String uid() {
        return uid;
    }

    public String typeId() {
        return typeId;
    }

    public String recipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId == null ? "" : recipeId.trim();
    }

    public String group() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group.trim();
    }

    public String comment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment == null ? "" : comment;
    }

    public String targetFile() {
        return targetFile;
    }

    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile == null ? "" : targetFile.trim();
    }

    public String conditionModLoaded() {
        return conditionModLoaded;
    }

    public void setConditionModLoaded(String modId) {
        this.conditionModLoaded = modId == null ? "" : modId.trim();
    }

    public boolean replaceRecipe() {
        return replaceRecipe;
    }

    public void setReplaceRecipe(boolean replaceRecipe) {
        this.replaceRecipe = replaceRecipe;
    }

    public SlotContent slot(String key) {
        return slots.getOrDefault(key, SlotContent.EMPTY);
    }

    public void setSlot(String key, SlotContent content) {
        if (content == null || content.isEmpty()) {
            slots.remove(key);
        } else {
            slots.put(key, content);
        }
    }

    public Map<String, SlotContent> slots() {
        return slots;
    }

    /**
     * Ordered, gap-free contents of a list slot. Entries live under the keys
     * {@code baseKey + index} ("in0", "in1", ...) and are always contiguous
     * because {@link #setListSlot} compacts them on every edit.
     */
    public java.util.List<SlotContent> listSlots(String baseKey) {
        java.util.List<SlotContent> out = new java.util.ArrayList<>();
        for (int i = 0; slots.containsKey(baseKey + i); i++) {
            out.add(slots.get(baseKey + i));
        }
        return out;
    }

    /**
     * Set (or append, when {@code index} is at/after the end) one entry of a list
     * slot. Empty content removes the entry; remaining entries are re-indexed so
     * the keys stay contiguous. An empty slot is thus dropped automatically — the
     * editor simply renders one placeholder slot when the list is empty.
     */
    public void setListSlot(String baseKey, int index, SlotContent content) {
        java.util.List<SlotContent> entries = listSlots(baseKey);
        if (index >= 0 && index < entries.size()) {
            entries.set(index, content);
        } else if (content != null && !content.isEmpty()) {
            entries.add(content);
        }
        entries.removeIf(c -> c == null || c.isEmpty());
        // wipe the old contiguous keys, then rewrite the compacted list
        for (int i = 0; slots.remove(baseKey + i) != null; i++) {
            // remove until the first missing key (keys are contiguous)
        }
        for (int i = 0; i < entries.size(); i++) {
            slots.put(baseKey + i, entries.get(i));
        }
    }

    /**
     * Fold legacy fixed-slot data into a list slot after a layout was converted from
     * several numbered slots ({@code output}, {@code output2}, {@code output3}) into a
     * single {@code list} slot. Runs on load; a no-op when the slot already holds list
     * entries ({@code key0}, {@code key1}, ... — e.g. the former {@code in0..in3} keys
     * which already match the list encoding) or when there is no legacy data.
     */
    public void migrateLegacyListSlots(RecipeTypeDefinition type) {
        for (SlotDefinition def : type.slots()) {
            String base = def.key();
            if (!def.list() || !listSlots(base).isEmpty()) {
                continue;
            }
            // legacy keys, in order: the un-indexed base, then base+"2", base+"3", ...
            java.util.List<SlotContent> legacy = new java.util.ArrayList<>();
            if (slots.containsKey(base)) {
                legacy.add(slots.get(base));
            }
            for (int i = 2; slots.containsKey(base + i); i++) {
                legacy.add(slots.get(base + i));
            }
            if (legacy.isEmpty()) {
                continue;
            }
            slots.remove(base);
            for (int i = 2; slots.remove(base + i) != null; i++) {
                // drop the old contiguous numbered keys
            }
            for (int i = 0; i < legacy.size(); i++) {
                slots.put(base + i, legacy.get(i));
            }
        }
    }

    /** Parameter value or the definition default when unset. */
    public String param(RecipeTypeDefinition type, String key) {
        String value = parameters.get(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return type.parameter(key).map(ParameterDefinition::defaultValue).orElse("");
    }

    public int paramInt(RecipeTypeDefinition type, String key, int fallback) {
        try {
            return Integer.parseInt(param(type, key).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public float paramFloat(RecipeTypeDefinition type, String key, float fallback) {
        try {
            return Float.parseFloat(param(type, key).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean paramBool(RecipeTypeDefinition type, String key, boolean fallback) {
        String value = param(type, key).trim();
        if (value.isEmpty()) return fallback;
        return value.equalsIgnoreCase("true");
    }

    /** Raw stored parameter value with no type-default fallback; empty when unset. */
    public String paramRaw(String key) {
        return parameters.getOrDefault(key, "");
    }

    public void setParam(String key, String value) {
        if (value == null || value.isEmpty()) {
            parameters.remove(key);
        } else {
            parameters.put(key, value);
        }
    }

    public Map<String, String> parameters() {
        return parameters;
    }

    /** Deep copy with a fresh uid (for the "duplicate recipe" button). */
    public RecipeInstance copy() {
        RecipeInstance copy = new RecipeInstance(typeId);
        copy.recipeId = recipeId.isEmpty() ? "" : recipeId + "_copy";
        copy.group = group;
        copy.comment = comment;
        copy.targetFile = targetFile;
        copy.conditionModLoaded = conditionModLoaded;
        copy.replaceRecipe = replaceRecipe;
        copy.slots.putAll(slots);
        copy.parameters.putAll(parameters);
        return copy;
    }

    /** Short description for lists: the primary output, or the type id. */
    public String describe() {
        return slots.entrySet().stream()
                .filter(e -> e.getKey().startsWith("output"))
                .map(e -> e.getValue().describe())
                .filter(s -> !s.equals("-"))
                .findFirst()
                .orElseGet(() -> slots.values().stream()
                        .findFirst().map(SlotContent::describe).orElse("(empty)"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uid", uid);
        json.addProperty("type", typeId);
        if (!recipeId.isEmpty()) json.addProperty("recipeId", recipeId);
        if (!group.isEmpty()) json.addProperty("group", group);
        if (!comment.isEmpty()) json.addProperty("comment", comment);
        if (!targetFile.isEmpty()) json.addProperty("targetFile", targetFile);
        if (!conditionModLoaded.isEmpty()) json.addProperty("ifModLoaded", conditionModLoaded);
        if (!replaceRecipe) json.addProperty("replaceRecipe", false);
        JsonObject slotJson = new JsonObject();
        slots.forEach((key, content) -> slotJson.add(key, content.toJson()));
        json.add("slots", slotJson);
        JsonObject paramJson = new JsonObject();
        parameters.forEach(paramJson::addProperty);
        json.add("params", paramJson);
        return json;
    }

    public static RecipeInstance fromJson(JsonObject json) {
        String uid = json.has("uid") ? json.get("uid").getAsString() : UUID.randomUUID().toString();
        RecipeInstance recipe = new RecipeInstance(uid, json.get("type").getAsString());
        if (json.has("recipeId")) recipe.recipeId = json.get("recipeId").getAsString();
        if (json.has("group")) recipe.group = json.get("group").getAsString();
        if (json.has("comment")) recipe.comment = json.get("comment").getAsString();
        if (json.has("targetFile")) recipe.targetFile = json.get("targetFile").getAsString();
        if (json.has("ifModLoaded")) recipe.conditionModLoaded = json.get("ifModLoaded").getAsString();
        if (json.has("replaceRecipe")) recipe.replaceRecipe = json.get("replaceRecipe").getAsBoolean();
        if (json.has("slots")) {
            JsonObject slotJson = json.getAsJsonObject("slots");
            slotJson.entrySet().forEach(e ->
                    recipe.slots.put(e.getKey(), SlotContent.fromJson(e.getValue().getAsJsonObject())));
        }
        if (json.has("params")) {
            JsonObject paramJson = json.getAsJsonObject("params");
            paramJson.entrySet().forEach(e -> recipe.parameters.put(e.getKey(), e.getValue().getAsString()));
        }
        return recipe;
    }
}
