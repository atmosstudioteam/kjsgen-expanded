package com.zizazr.kjsgen.integration.jei;

// JEI 19 only (bundled with 1.21.x); compiled out on 1.20.1-forge's JEI 15. See KjsgenJeiPlugin.
//? if >=1.21 {
/*import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.KjsGenClient;
import com.zizazr.kjsgen.core.RecipeInstance;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.network.chat.Component;

/^*
 * The per-recipe "edit in kjsgen" button JEI draws next to each recipe layout.
 * Because JEI stacks extra buttons upward from the transfer button, this lands
 * directly above the bookmark ("favorites") button. Pressing it captures the
 * recipe shown in that layout into the current kjsgen project and opens the
 * vanilla editor on it (adding a new entry, or replacing an existing recipe of
 * the same type + output).
 *
 * @see JeiRecipeCapture
 * @see KjsGenClient#openEditorWithCapturedRecipe(RecipeInstance)
 ^/
final class JeiEditButtonController implements IIconButtonController {
    private final IDrawable icon;
    private final IRecipeLayoutDrawable<?> layout;

    private JeiEditButtonController(IDrawable icon, IRecipeLayoutDrawable<?> layout) {
        this.icon = icon;
        this.layout = layout;
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(icon);
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("kjsgen.jei.edit_button"));
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        // isSimulate() is the mouse-down probe: claim the click so JEI shows the pressed state.
        if (input.isSimulate()) {
            return true;
        }
        try {
            RecipeInstance recipe = JeiRecipeCapture.capture(layout);
            if (recipe != null) {
                KjsGenClient.openEditorWithCapturedRecipe(recipe);
            }
        } catch (Exception e) {
            KjsGen.LOGGER.error("kjsgen: failed to open the editor from JEI", e);
        }
        return true;
    }

    /^* Registered with JEI in {@code registerAdvanced}; makes one controller per recipe layout. ^/
    static final class Factory implements IRecipeButtonControllerFactory {
        private final IDrawable icon = new KjsEditIcon("jei_edit.png", 16);

        @Override
        public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
            // Only offer the button for categories a hand-authored kjsgen layout maps to;
            // returning null tells JEI not to draw a button for this recipe.
            if (JeiLayoutImporter.mappedTypeFor(recipeLayoutDrawable).isEmpty()) {
                return null;
            }
            return new JeiEditButtonController(icon, recipeLayoutDrawable);
        }
    }
}
*///?}
