package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One recipe-removal filter, exported as {@code event.remove({...})}. Every
 * non-empty field is one filter key ({@code output} / {@code input} / {@code mod}
 * / {@code id}); filled fields combine (all must match, as on the KubeJS wiki).
 * Several inputs expand to one filter object per input (any of them matches).
 */
public final class RemovalRule {
    private final String uid;
    /** Output item id or {@code #tag}; empty = not filtered by output. */
    private String output = "";
    /** Comma-separated input item ids / {@code #tag}s; empty = not filtered by input. */
    private String input = "";
    /** Mod namespace ({@code mekanism}); empty = not filtered by mod. */
    private String mod = "";
    /** Full recipe id ({@code minecraft:glowstone}); empty = not filtered by id. */
    private String recipeId = "";

    public RemovalRule() {
        this(UUID.randomUUID().toString());
    }

    private RemovalRule(String uid) {
        this.uid = uid;
    }

    public String uid() {
        return uid;
    }

    public String output() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output == null ? "" : output.trim();
    }

    public String input() {
        return input;
    }

    public void setInput(String input) {
        this.input = input == null ? "" : input.trim();
    }

    public String mod() {
        return mod;
    }

    public void setMod(String mod) {
        this.mod = mod == null ? "" : mod.trim();
    }

    public String recipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId == null ? "" : recipeId.trim();
    }

    /** The input field split on commas, blanks dropped. */
    public List<String> inputs() {
        List<String> list = new ArrayList<>();
        for (String part : input.split(",")) {
            if (!part.isBlank()) {
                list.add(part.trim());
            }
        }
        return list;
    }

    /** True when no filter field is set — such a rule must never be exported. */
    public boolean isEmpty() {
        return output.isEmpty() && inputs().isEmpty() && mod.isEmpty() && recipeId.isEmpty();
    }

    /** Short one-line label for the rule list. */
    public String describe() {
        if (!output.isEmpty()) {
            return output;
        }
        if (!input.isEmpty()) {
            return input;
        }
        if (!mod.isEmpty()) {
            return "@" + mod;
        }
        if (!recipeId.isEmpty()) {
            return recipeId;
        }
        return "?";
    }

    /** The item id used for the list icon: the output, else the first input (tags keep their '#'). */
    public String iconId() {
        if (!output.isEmpty()) {
            return output;
        }
        List<String> ins = inputs();
        return ins.isEmpty() ? "" : ins.get(0);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uid", uid);
        json.addProperty("output", output);
        json.addProperty("input", input);
        json.addProperty("mod", mod);
        json.addProperty("recipeId", recipeId);
        return json;
    }

    public static RemovalRule fromJson(JsonObject json) {
        RemovalRule rule = new RemovalRule(json.has("uid")
                ? json.get("uid").getAsString() : UUID.randomUUID().toString());
        if (json.has("output")) rule.setOutput(json.get("output").getAsString());
        if (json.has("input")) rule.setInput(json.get("input").getAsString());
        if (json.has("mod")) rule.setMod(json.get("mod").getAsString());
        if (json.has("recipeId")) rule.setRecipeId(json.get("recipeId").getAsString());
        return rule;
    }
}
