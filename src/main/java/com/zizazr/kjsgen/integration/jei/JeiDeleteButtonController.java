package com.zizazr.kjsgen.integration.jei;

import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.KjsGenClient;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The per-recipe "remove with kjsgen" button JEI draws next to each recipe layout
 * (stacked with the {@link JeiEditButtonController "edit"} button). Pressing it
 * adds the shown recipe's id to the current project's removal rules and opens
 * the removals screen on that rule.
 *
 * @see KjsGenClient#openRemovalsForRecipeId(String)
 */
final class JeiDeleteButtonController implements IIconButtonController {
    private final IDrawable icon;
    private final ResourceLocation recipeId;

    private JeiDeleteButtonController(IDrawable icon, ResourceLocation recipeId) {
        this.icon = icon;
        this.recipeId = recipeId;
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(icon);
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("kjsgen.jei.delete_button"));
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        // isSimulate() is the mouse-down probe: claim the click so JEI shows the pressed state.
        if (input.isSimulate()) {
            return true;
        }
        try {
            KjsGenClient.openRemovalsForRecipeId(recipeId.toString());
        } catch (Exception e) {
            KjsGen.LOGGER.error("kjsgen: failed to open the removals screen from JEI", e);
        }
        return true;
    }

    /** Registered with JEI in {@code registerAdvanced}; makes one controller per recipe layout. */
    static final class Factory implements IRecipeButtonControllerFactory {
        private final IDrawable icon = new KjsEditIcon("jei_delete.png", 8);

        @Override
        public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
            // Only datapack recipes with a registry id can be targeted by event.remove({ id }):
            // returning null tells JEI not to draw a button for this recipe.
            ResourceLocation id = recipeLayoutDrawable.getRecipeCategory()
                    .getRegistryName(recipeLayoutDrawable.getRecipe());
            if (id == null) {
                return null;
            }
            return new JeiDeleteButtonController(icon, id);
        }
    }
}
