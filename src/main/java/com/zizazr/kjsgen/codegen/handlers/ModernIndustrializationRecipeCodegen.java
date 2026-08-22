package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;

import java.util.Optional;
import java.util.regex.Pattern;

/** KubeJS codegen for Modern Industrialization machine recipes and the forge hammer. */
public final class ModernIndustrializationRecipeCodegen implements RecipeCodegen {
    private static final Pattern JS_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        if (type.id().equals("kjsgen:mi_forge_hammer")) {
            return forgeHammer(recipe, type);
        }

        String machine = recipe.param(type, "__machine").trim();
        if (machine.isEmpty()) {
            machine = recipe.param(type, "machineType").trim();
        }
        if (machine.isEmpty()) {
            machine = "modern_industrialization:macerator";
        }
        if (!machine.contains(":")) {
            machine = "modern_industrialization:" + machine;
        }

        int colon = machine.indexOf(':');
        String namespace = machine.substring(0, colon);
        String path = machine.substring(colon + 1);

        StringBuilder js = new StringBuilder("event.recipes");
        appendProperty(js, namespace);
        appendProperty(js, path);
        js.append('(')
                .append(recipe.paramInt(type, "eu", 8))
                .append(", ")
                .append(recipe.paramInt(type, "duration", 200))
                .append(')');

        appendMachineSlots(js, recipe, "itemIn", true, true);
        appendMachineSlots(js, recipe, "fluidIn", false, true);
        appendMachineSlots(js, recipe, "itemOut", true, false);
        appendMachineSlots(js, recipe, "fluidOut", false, false);

        appendProcessConditions(js, recipe, type);
        appendId(js, recipe);
        return js.toString();
    }

    private static void appendMachineSlots(StringBuilder js, RecipeInstance recipe, String key,
                                           boolean item, boolean input) {
        for (SlotContent content : recipe.listSlots(key)) {
            String method = switch (key) {
                case "itemIn" -> "itemIn";
                case "fluidIn" -> "fluidIn";
                case "itemOut" -> "itemOut";
                case "fluidOut" -> "fluidOut";
                default -> throw new IllegalArgumentException("Unknown MI slot " + key);
            };
            js.append("\n    .").append(method).append('(')
                    .append(item ? miItem(content) : miFluid(content));
            if (content.chance() < 1.0f) {
                js.append(", ").append(JsUtil.trimFloat(content.chance()));
            }
            js.append(')');
        }
    }

    /** MI uses sized string syntax for both item and fluid stacks/ingredients. */
    private static String miItem(SlotContent content) {
        String id = switch (content.kind()) {
            case ITEM -> content.id();
            case ITEM_TAG -> "#" + content.id();
            default -> content.id();
        };
        return JsUtil.quote((content.count() > 1 ? content.count() + "x " : "") + id);
    }

    private static String miFluid(SlotContent content) {
        String id = switch (content.kind()) {
            case FLUID -> content.id();
            case FLUID_TAG -> "#" + content.id();
            default -> content.id();
        };
        return JsUtil.quote(content.amount() + "x " + id);
    }

    private static void appendProcessConditions(StringBuilder js, RecipeInstance recipe, RecipeTypeDefinition type) {
        String dimension = recipe.param(type, "dimension").trim();
        if (!dimension.isEmpty()) js.append("\n    .dimension(").append(JsUtil.quote(dimension)).append(')');

        String biome = recipe.param(type, "biome").trim();
        if (!biome.isEmpty()) js.append("\n    .biome(").append(JsUtil.quote(biome)).append(')');

        String biomeTag = recipe.param(type, "biomeTag").trim();
        if (!biomeTag.isEmpty()) js.append("\n    .biomeTag(").append(JsUtil.quote(biomeTag)).append(')');

        String adjacentBlock = recipe.param(type, "adjacentBlock").trim();
        String adjacentPosition = recipe.param(type, "adjacentPosition").trim();
        if (!adjacentBlock.isEmpty() && !adjacentPosition.isEmpty() && !adjacentPosition.equals("none")) {
            js.append("\n    .adjacentBlock(")
                    .append(JsUtil.quote(adjacentBlock)).append(", ")
                    .append(JsUtil.quote(adjacentPosition)).append(')');
        }

        String registeredCondition = recipe.param(type, "registeredConditionJson").trim();
        if (!registeredCondition.isEmpty()) {
            js.append("\n    .registeredCondition(").append(registeredCondition).append(')');
        }
    }

    private static String forgeHammer(RecipeInstance recipe, RecipeTypeDefinition type) {
        SlotContent input = recipe.slot("input");
        SlotContent output = recipe.slot("output");
        StringBuilder js = new StringBuilder("event.custom({\n")
                .append("  type: 'modern_industrialization:forge_hammer',\n")
                .append("  ingredient: ").append(ingredientJson(input)).append(",\n")
                .append("  result: ").append(resultJson(output));

        int damage = recipe.paramInt(type, "damage", 0);
        int hammerCount = recipe.paramInt(type, "hammerCount", 1);
        if (damage > 0) js.append(",\n  damage: ").append(damage);
        if (hammerCount > 1) js.append(",\n  count: ").append(hammerCount);
        js.append("\n})");
        appendId(js, recipe);
        return js.toString();
    }

    private static String ingredientJson(SlotContent content) {
        if (content.kind() == ContentKind.ITEM_TAG) {
            return "{ tag: " + JsUtil.quote(content.id()) + " }";
        }
        return "{ item: " + JsUtil.quote(content.id()) + " }";
    }

    private static String resultJson(SlotContent content) {
        return "{ id: " + JsUtil.quote(content.id())
                + (content.count() > 1 ? ", count: " + content.count() : "") + " }";
    }

    private static void appendProperty(StringBuilder js, String name) {
        if (JS_IDENTIFIER.matcher(name).matches()) {
            js.append('.').append(name);
        } else {
            js.append('[').append(JsUtil.quote(name)).append(']');
        }
    }

    private static void appendId(StringBuilder js, RecipeInstance recipe) {
        if (!recipe.recipeId().isEmpty()) {
            js.append(".id(").append(JsUtil.quote(recipe.recipeId())).append(')');
        }
    }

    @Override
    public Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        if (type.id().equals("kjsgen:mi_forge_hammer")) {
            return Optional.of("modern_industrialization:forge_hammer");
        }
        String machine = recipe.param(type, "__machine").trim();
        if (machine.isEmpty()) machine = recipe.param(type, "machineType").trim();
        if (machine.isEmpty()) return Optional.empty();
        return Optional.of(machine.contains(":") ? machine : "modern_industrialization:" + machine);
    }
}
