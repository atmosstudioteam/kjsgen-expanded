package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven codegen for recipe types declared purely in JSON (addon layouts,
 * bundled Create/Mekanism templates). The generated statement comes from the
 * hidden {@code __template} parameter of the type definition, e.g.
 *
 * <pre>
 * event.recipes.create.pressing({slot:output}, {slot:input})
 * </pre>
 *
 * Placeholders:
 * <ul>
 *   <li>{@code {slot:KEY}} — the slot content as an ingredient (inputs/catalysts)
 *       or result expression (outputs)</li>
 *   <li>{@code {opt_slot:KEY}} — like {@code {slot:KEY}} but for an optional
 *       argument: expands to {@code ", EXPR"} (with a leading comma) when the
 *       slot is filled, and to nothing when it is empty. Lets a template omit a
 *       trailing optional argument entirely (e.g. the sawmill's extra output).</li>
 *   <li>{@code {opt_slot_chance:SLOT:PARAM}} — an optional output paired with a
 *       chance parameter: expands to {@code ", OUTPUT, PARAM"} when the slot is
 *       filled and to nothing when empty. For syntaxes whose secondary output and
 *       its chance are two adjacent positional args (e.g. Mekanism sawing).</li>
 *   <li>{@code {param:KEY}} — raw parameter value (numbers, booleans)</li>
 *   <li>{@code {param_str:KEY}} — parameter value as a quoted JS string</li>
 *   <li>{@code {opt_call:KEY}} — a chained modifier whose method name IS the
 *       parameter value: expands to {@code .VALUE()} unless the value is empty,
 *       {@code "none"} or {@code "false"} (used for Create's mixing/compacting
 *       heat: an enum param {@code heat} of {@code none|heated|superheated}).</li>
 *   <li>{@code {flag_call:METHOD:KEY}} — a chained no-arg modifier gated on a
 *       boolean param: expands to {@code .METHOD()} when param {@code KEY} is
 *       truthy and to nothing otherwise (e.g. the deployer's
 *       {@code .keepHeldItem()}).</li>
 *   <li>{@code {id}} — the recipe id (quoted), {@code {group}} — the group (quoted)</li>
 *   <li>{@code {inputs}} / {@code {outputs}} — comma-joined list of all
 *       <b>filled</b> input/output slots (for machines with a variable number
 *       of results, e.g. Create crushing byproducts)</li>
 * </ul>
 * An optional hidden {@code __suffix_id} parameter set to "false" disables the
 * automatic {@code .id(...)} suffix for syntaxes that don't support it.
 */
public class TemplateRecipeCodegen implements RecipeCodegen {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(slot|opt_slot|param|param_str|opt_call):([A-Za-z0-9_.\\-]+)}|\\{opt_slot_chance:([A-Za-z0-9_.\\-]+):([A-Za-z0-9_.\\-]+)}|\\{flag_call:([A-Za-z0-9_.\\-]+):([A-Za-z0-9_.\\-]+)}|\\{id}|\\{group}|\\{inputs}|\\{outputs}");
    /** Pulls the recipe type suffix out of an {@code event.recipes.MOD.TYPE(} template call — every
     * bundled addon template names its JS method after the actual (already snake_case) recipe type. */
    private static final Pattern TEMPLATE_METHOD = Pattern.compile("event\\.recipes\\.[A-Za-z0-9_]+\\.([A-Za-z0-9_]+)\\(");

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        String template = recipe.param(type, "__template");
        if (template.isEmpty()) {
            // JEI-imported types expose the template as a visible, per-recipe editable parameter
            template = recipe.param(type, "template");
        }
        if (template.isEmpty()) {
            return "// kjsgen: recipe type " + type.id() + " has no __template parameter";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder js = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(3) != null) {
                // {opt_slot_chance:SLOT:PARAM}
                String slotKey = matcher.group(3);
                if (recipe.slot(slotKey).isEmpty()) {
                    replacement = "";
                } else {
                    replacement = ", " + slotExpr(recipe, type, slotKey)
                            + ", " + recipe.param(type, matcher.group(4));
                }
            } else if (matcher.group(5) != null) {
                // {flag_call:METHOD:KEY}
                replacement = truthy(recipe.param(type, matcher.group(6)))
                        ? "." + matcher.group(5) + "()" : "";
            } else if (matcher.group(1) == null) {
                replacement = switch (matcher.group()) {
                    case "{id}" -> JsUtil.quote(recipe.recipeId());
                    case "{group}" -> JsUtil.quote(recipe.group());
                    case "{inputs}" -> joinSlots(recipe, type, SlotRole.INPUT);
                    case "{outputs}" -> joinSlots(recipe, type, SlotRole.OUTPUT);
                    default -> matcher.group();
                };
            } else {
                String key = matcher.group(2);
                replacement = switch (matcher.group(1)) {
                    case "slot" -> slotExpr(recipe, type, key);
                    case "opt_slot" -> recipe.slot(key).isEmpty() ? "" : ", " + slotExpr(recipe, type, key);
                    case "param" -> recipe.param(type, key);
                    case "param_str" -> JsUtil.quote(recipe.param(type, key));
                    case "opt_call" -> {
                        String value = recipe.param(type, key).trim();
                        yield truthy(value) ? "." + value + "()" : "";
                    }
                    default -> matcher.group();
                };
            }
            matcher.appendReplacement(js, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(js);
        if (!recipe.param(type, "__suffix_id").equals("false")) {
            ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        }
        return js.toString();
    }

    @Override
    public Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        String template = recipe.param(type, "__template");
        if (template.isEmpty()) {
            template = recipe.param(type, "template");
        }
        Matcher matcher = TEMPLATE_METHOD.matcher(template);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** A parameter value counts as "on" unless empty / "false" / "none" / "0". */
    private static boolean truthy(String value) {
        String v = value.trim();
        return !v.isEmpty() && !v.equalsIgnoreCase("false")
                && !v.equalsIgnoreCase("none") && !v.equals("0");
    }

    /** Ingredient expression for input/catalyst slots, result expression for output slots. */
    private static String slotExpr(RecipeInstance recipe, RecipeTypeDefinition type, String key) {
        boolean isOutput = type.slot(key).map(s -> s.role() == SlotRole.OUTPUT).orElse(false);
        return isOutput ? JsUtil.output(recipe.slot(key)) : JsUtil.ingredient(recipe.slot(key));
    }

    private static String joinSlots(RecipeInstance recipe, RecipeTypeDefinition type, SlotRole role) {
        return type.slotsByRole(role).stream()
                // a list slot contributes all of its entries; a fixed slot its single content
                .flatMap(slot -> slot.list()
                        ? recipe.listSlots(slot.key()).stream()
                        : java.util.stream.Stream.of(recipe.slot(slot.key())))
                .filter(content -> !content.isEmpty())
                .map(content -> role == SlotRole.OUTPUT ? JsUtil.output(content) : JsUtil.ingredient(content))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
