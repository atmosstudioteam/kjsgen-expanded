package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.CreateSpecialModel;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;

import java.util.Optional;

/**
 * Create sequenced assembly:
 * <pre>
 * event.recipes.create.sequenced_assembly([results], input, [
 *     event.recipes.create.deploying(trans, [trans, appliedItem]),
 *     event.recipes.create.pressing(trans, trans),
 *     ...
 * ]).transitionalItem(trans).loops(n)
 * </pre>
 *
 * <p>The transitional item is used as both the output and the (first) input of
 * every in-sequence step, matching Create's "in-progress item" placeholder. The
 * ordered stage list is read from {@link CreateSpecialModel}.
 */
public class CreateSequencedAssemblyCodegen implements RecipeCodegen {
    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        String trans = JsUtil.ingredient(recipe.slot("transitional"));
        String input = JsUtil.ingredient(recipe.slot("input"));

        StringBuilder outs = new StringBuilder();
        for (SlotContent content : recipe.listSlots("output")) {
            if (content.isEmpty()) {
                continue;
            }
            if (outs.length() > 0) {
                outs.append(", ");
            }
            outs.append(JsUtil.output(content));
        }

        int n = CreateSpecialModel.seqLen(recipe, type);
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String stepId = CreateSpecialModel.stepTypeId(recipe, type, i);
            SlotContent item = CreateSpecialModel.stepItem(recipe, i);
            String stepJs = switch (stepId) {
                case "deploying" -> "event.recipes.create.deploying(" + trans
                        + ", [" + trans + ", " + JsUtil.ingredient(item) + "])";
                case "filling" -> "event.recipes.create.filling(" + trans
                        + ", [" + trans + ", " + JsUtil.ingredient(item) + "])";
                case "cutting" -> "event.recipes.create.cutting(" + trans + ", " + trans + ")";
                default -> "event.recipes.create.pressing(" + trans + ", " + trans + ")";
            };
            steps.append("\n    ").append(stepJs);
            if (i < n - 1) {
                steps.append(",");
            }
        }

        StringBuilder js = new StringBuilder();
        js.append("event.recipes.create.sequenced_assembly([").append(outs).append("], ")
                .append(input).append(", [").append(steps);
        if (n > 0) {
            js.append("\n  ");
        }
        js.append("]).transitionalItem(").append(trans).append(")");
        int loops = recipe.paramInt(type, "loops", 1);
        if (loops > 0) {
            js.append(".loops(").append(loops).append(")");
        }
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }

    @Override
    public Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Optional.of("sequenced_assembly");
    }
}
