package com.zizazr.kjsgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A working session: a named set of {@link RecipeInstance}s plus export options.
 * Persisted as JSON independently from the KubeJS export.
 */
public final class RecipeProject {
    public static final int FORMAT_VERSION = 1;

    private String name;
    /** Default export file name (without .js extension). */
    private String defaultTargetFile = "kjsgen_recipes";
    /** Emit a human readable comment above each generated recipe. */
    private boolean exportComments = true;
    /** Run the vanilla "/reload" command right after a successful export. */
    private boolean reloadOnExport = false;
    private final List<RecipeInstance> recipes = new ArrayList<>();
    /** Recipe-removal filters, exported as {@code event.remove(...)} lines. */
    private final List<RemovalRule> removals = new ArrayList<>();

    public RecipeProject(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String defaultTargetFile() {
        return defaultTargetFile;
    }

    public void setDefaultTargetFile(String defaultTargetFile) {
        if (defaultTargetFile != null && !defaultTargetFile.isBlank()) {
            this.defaultTargetFile = defaultTargetFile.trim();
        }
    }

    public boolean exportComments() {
        return exportComments;
    }

    public void setExportComments(boolean exportComments) {
        this.exportComments = exportComments;
    }

    public boolean reloadOnExport() {
        return reloadOnExport;
    }

    public void setReloadOnExport(boolean reloadOnExport) {
        this.reloadOnExport = reloadOnExport;
    }

    public List<RecipeInstance> recipes() {
        return recipes;
    }

    public Optional<RecipeInstance> recipeByUid(String uid) {
        return recipes.stream().filter(r -> r.uid().equals(uid)).findFirst();
    }

    public void add(RecipeInstance recipe) {
        recipes.add(recipe);
    }

    public void remove(RecipeInstance recipe) {
        recipes.removeIf(r -> r.uid().equals(recipe.uid()));
    }

    /**
     * Replace the recipe with the same uid in place (preserving its list position), or append
     * it when no recipe with that uid exists. Used by undo/redo to restore a recipe snapshot
     * without disturbing the surrounding order.
     */
    public void replace(RecipeInstance recipe) {
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).uid().equals(recipe.uid())) {
                recipes.set(i, recipe);
                return;
            }
        }
        recipes.add(recipe);
    }

    /**
     * Adds {@code recipe}, first dropping any existing recipe of the same type that
     * produces the same primary output — so re-capturing the same JEI recipe updates
     * the entry instead of piling up duplicates. Returns the added recipe's uid.
     */
    public String addOrReplaceByOutput(RecipeInstance recipe) {
        String output = primaryOutputId(recipe);
        if (!output.isEmpty()) {
            recipes.removeIf(r -> r.typeId().equals(recipe.typeId()) && primaryOutputId(r).equals(output));
        }
        recipes.add(recipe);
        return recipe.uid();
    }

    /** Registry id of the recipe's first non-empty output slot, or "" when it has none. */
    private static String primaryOutputId(RecipeInstance recipe) {
        return recipe.slots().entrySet().stream()
                .filter(e -> e.getKey().startsWith("output") && !e.getValue().isEmpty())
                .map(e -> e.getValue().id())
                .findFirst()
                .orElse("");
    }

    public List<RemovalRule> removals() {
        return removals;
    }

    public Optional<RemovalRule> removalByUid(String uid) {
        return removals.stream().filter(r -> r.uid().equals(uid)).findFirst();
    }

    /** The removal rule targeting exactly this recipe id, if one exists (JEI button dedupe). */
    public Optional<RemovalRule> removalByRecipeId(String recipeId) {
        return removals.stream().filter(r -> r.recipeId().equals(recipeId)).findFirst();
    }

    public void addRemoval(RemovalRule rule) {
        removals.add(rule);
    }

    public void removeRemoval(String uid) {
        removals.removeIf(r -> r.uid().equals(uid));
    }

    /** Replace the rule with the same uid in place, or append it (upsert; mirrors {@link #replace}). */
    public void replaceRemoval(RemovalRule rule) {
        for (int i = 0; i < removals.size(); i++) {
            if (removals.get(i).uid().equals(rule.uid())) {
                removals.set(i, rule);
                return;
            }
        }
        removals.add(rule);
    }

    /** Effective export file of a recipe (recipe override or project default). */
    public String targetFileOf(RecipeInstance recipe) {
        return recipe.targetFile().isEmpty() ? defaultTargetFile : recipe.targetFile();
    }

    /** Recipes grouped by their effective export file, insertion-ordered. */
    public Map<String, List<RecipeInstance>> recipesByTargetFile() {
        Map<String, List<RecipeInstance>> byFile = new LinkedHashMap<>();
        for (RecipeInstance recipe : recipes) {
            byFile.computeIfAbsent(targetFileOf(recipe), f -> new ArrayList<>()).add(recipe);
        }
        return byFile;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT_VERSION);
        json.addProperty("name", name);
        json.addProperty("defaultTargetFile", defaultTargetFile);
        json.addProperty("exportComments", exportComments);
        json.addProperty("reloadOnExport", reloadOnExport);
        JsonArray arr = new JsonArray();
        recipes.forEach(r -> arr.add(r.toJson()));
        json.add("recipes", arr);
        JsonArray rem = new JsonArray();
        removals.forEach(r -> rem.add(r.toJson()));
        json.add("removals", rem);
        return json;
    }

    public static RecipeProject fromJson(JsonObject json) {
        RecipeProject project = new RecipeProject(json.has("name") ? json.get("name").getAsString() : "unnamed");
        if (json.has("defaultTargetFile")) project.setDefaultTargetFile(json.get("defaultTargetFile").getAsString());
        if (json.has("exportComments")) project.exportComments = json.get("exportComments").getAsBoolean();
        if (json.has("reloadOnExport")) project.reloadOnExport = json.get("reloadOnExport").getAsBoolean();
        if (json.has("recipes")) {
            json.getAsJsonArray("recipes").forEach(e -> {
                RecipeInstance recipe = RecipeInstance.fromJson(e.getAsJsonObject());
                RecipeTypeRegistry.get(recipe.typeId()).ifPresent(recipe::migrateLegacyListSlots);
                project.recipes.add(recipe);
            });
        }
        if (json.has("removals")) {
            json.getAsJsonArray("removals").forEach(e ->
                    project.removals.add(RemovalRule.fromJson(e.getAsJsonObject())));
        }
        return project;
    }
}
