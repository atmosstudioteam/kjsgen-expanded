package com.zizazr.kjsgen.codegen;

import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

import java.util.Optional;

/**
 * Turns one {@link RecipeInstance} into a KubeJS statement. One handler per
 * recipe type family; addon mods register theirs through the addon API.
 */
@FunctionalInterface
public interface RecipeCodegen {
    /** The standard recipes-event wrapper (also hosts project-level {@code event.remove} lines). */
    String RECIPES_EVENT_HEADER = "ServerEvents.recipes(event => {";
    String RECIPES_EVENT_FOOTER = "})";

    /**
     * @return a single JS statement (may span multiple lines), without
     *         indentation and without a trailing newline, e.g.
     *         {@code event.smelting('minecraft:iron_ingot', '#c:ores/iron').xp(0.7)}
     */
    String generate(RecipeInstance recipe, RecipeTypeDefinition type);

    /**
     * The KubeJS event wrapper the generated statements must live in. Recipes
     * with different wrappers end up in separate blocks of the exported file
     * (e.g. brewing via MoreJS uses a different event than regular recipes).
     */
    default String wrapperHeader() {
        return RECIPES_EVENT_HEADER;
    }

    default String wrapperFooter() {
        return RECIPES_EVENT_FOOTER;
    }

    /**
     * The bare KubeJS recipe serializer type (no namespace, e.g. {@code "campfire_cooking"},
     * {@code "crafting_shaped"}, {@code "pressing"}), used to build the {@code event.remove(...)}
     * line emitted before this recipe when {@link RecipeInstance#replaceRecipe()} is set.
     * Empty when this handler's recipes can't be safely targeted by {@code event.remove}
     * (e.g. brewing, which isn't a datapack recipe).
     */
    default Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Optional.empty();
    }
}
