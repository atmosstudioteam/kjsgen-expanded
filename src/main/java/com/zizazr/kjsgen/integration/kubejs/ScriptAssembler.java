package com.zizazr.kjsgen.integration.kubejs;

import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.RemovalRule;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the JS text for a set of recipes: groups them by their event wrapper
 * ({@code ServerEvents.recipes}, MoreJS brewing, ...), applies per-recipe
 * comments and {@code Platform.isLoaded} conditions.
 */
public final class ScriptAssembler {
    public static final String INDENT = "  ";

    private ScriptAssembler() {
    }

    /** JS text of one export block (without the kjsgen start/end markers). */
    public static String assemble(RecipeProject project, List<RecipeInstance> recipes) {
        return assemble(project, recipes, List.of());
    }

    /** JS text of one export block; {@code removals} are emitted first, inside the recipes event. */
    public static String assemble(RecipeProject project, List<RecipeInstance> recipes,
                                  List<RemovalRule> removals) {
        // group by wrapper, preserving recipe order
        Map<String, StringBuilder> byWrapper = new LinkedHashMap<>();
        Map<String, String> footers = new LinkedHashMap<>();
        appendRemovals(project, removals, byWrapper, footers);
        for (RecipeInstance recipe : recipes) {
            RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
            if (type == null) {
                continue;
            }
            RecipeCodegen codegen = CodegenRegistry.get(type.codegenId()).orElse(null);
            if (codegen == null) {
                continue;
            }
            StringBuilder body = byWrapper.computeIfAbsent(codegen.wrapperHeader(), h -> new StringBuilder());
            footers.put(codegen.wrapperHeader(), codegen.wrapperFooter());

            String statement = codegen.generate(recipe, type);
            String condition = effectiveCondition(recipe, type);
            String indent = INDENT;
            if (!condition.isEmpty()) {
                body.append(indent).append("if (Platform.isLoaded('").append(condition).append("')) {\n");
                indent = INDENT + INDENT;
            }
            if (project.exportComments()) {
                String comment = recipe.comment().isEmpty() ? recipe.describe() : recipe.comment();
                for (String line : comment.split("\n")) {
                    body.append(indent).append("// ").append(line).append("\n");
                }
            }
            if (recipe.replaceRecipe()) {
                String removeIndent = indent;
                removeStatement(recipe, type, codegen)
                        .ifPresent(line -> body.append(removeIndent).append(line).append("\n"));
            }
            for (String line : statement.split("\n")) {
                body.append(indent).append(line).append("\n");
            }
            if (!condition.isEmpty()) {
                body.append(INDENT).append("}\n");
            }
        }

        StringBuilder script = new StringBuilder();
        byWrapper.forEach((header, body) -> {
            if (script.length() > 0) {
                script.append("\n");
            }
            script.append(header).append("\n");
            script.append(body);
            script.append(footers.get(header)).append("\n");
        });
        return script.toString();
    }

    /** Open the recipes-event wrapper (even with zero recipes) and emit one line per removal rule. */
    private static void appendRemovals(RecipeProject project, List<RemovalRule> removals,
                                       Map<String, StringBuilder> byWrapper, Map<String, String> footers) {
        List<String> statements = new ArrayList<>();
        for (RemovalRule rule : removals) {
            removalStatement(rule).ifPresent(statements::add);
        }
        if (statements.isEmpty()) {
            return;
        }
        StringBuilder body = byWrapper.computeIfAbsent(RecipeCodegen.RECIPES_EVENT_HEADER,
                h -> new StringBuilder());
        footers.put(RecipeCodegen.RECIPES_EVENT_HEADER, RecipeCodegen.RECIPES_EVENT_FOOTER);
        if (project.exportComments()) {
            body.append(INDENT).append("// recipe removals\n");
        }
        for (String statement : statements) {
            body.append(INDENT).append(statement).append("\n");
        }
    }

    /**
     * One {@code event.remove(...)} line for a removal rule: the filled fields become one
     * filter object (all must match); several inputs expand into an array of filter objects
     * (any input matches). Empty rules produce nothing — a bare {@code event.remove({})}
     * would wipe every recipe.
     */
    public static Optional<String> removalStatement(RemovalRule rule) {
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        List<String> inputs = rule.inputs();
        List<String> filters = new ArrayList<>();
        if (inputs.isEmpty()) {
            filters.add(removalFilter(rule, null));
        } else {
            for (String input : inputs) {
                filters.add(removalFilter(rule, input));
            }
        }
        return Optional.of(filters.size() == 1
                ? "event.remove(" + filters.get(0) + ")"
                : "event.remove([" + String.join(", ", filters) + "])");
    }

    /** {@code { output: '...', input: '...', mod: '...', id: '...' }} from the rule's filled fields. */
    private static String removalFilter(RemovalRule rule, String input) {
        List<String> pairs = new ArrayList<>();
        if (!rule.output().isEmpty()) {
            pairs.add("output: " + JsUtil.quote(rule.output()));
        }
        if (input != null && !input.isEmpty()) {
            pairs.add("input: " + JsUtil.quote(input));
        }
        if (!rule.mod().isEmpty()) {
            pairs.add("mod: " + JsUtil.quote(rule.mod()));
        }
        if (!rule.recipeId().isEmpty()) {
            pairs.add("id: " + JsUtil.quote(rule.recipeId()));
        }
        return "{ " + String.join(", ", pairs) + " }";
    }

    /**
     * {@code event.remove({ output: '...', type: '...' })} for the recipe's primary output,
     * so re-exporting the same recipe replaces it instead of creating a duplicate. Only emitted
     * when the codegen handler knows the recipe's bare KubeJS type id and the output is a plain
     * item (chemical/fluid outputs aren't reliably matched by the {@code output} filter).
     */
    private static Optional<String> removeStatement(RecipeInstance recipe, RecipeTypeDefinition type,
                                                      RecipeCodegen codegen) {
        Optional<String> removeType = codegen.removeTypeId(recipe, type);
        if (removeType.isEmpty()) {
            return Optional.empty();
        }
        SlotContent output = type.slotsByRole(SlotRole.OUTPUT).stream()
                .map(slot -> recipe.slot(slot.key()))
                .filter(content -> !content.isEmpty())
                .findFirst()
                .orElse(SlotContent.EMPTY);
        if (output.isEmpty() || output.kind() != ContentKind.ITEM) {
            return Optional.empty();
        }
        return Optional.of("event.remove({ output: " + JsUtil.quote(output.id()) + " })");
    }

    private static String effectiveCondition(RecipeInstance recipe, RecipeTypeDefinition type) {
        if (!recipe.conditionModLoaded().isEmpty()) {
            return recipe.conditionModLoaded();
        }
        // addon recipe types know which mod their syntax needs
        if (!type.requiresMod().isEmpty() && !type.requiresMod().equals("minecraft")
                && !type.requiresMod().equals("morejs")) {
            return type.requiresMod();
        }
        return "";
    }

    /** Preview text for a single recipe (used by the code preview panel). */
    public static String previewSingle(RecipeProject project, RecipeInstance recipe) {
        return assemble(project, List.of(recipe));
    }
}
