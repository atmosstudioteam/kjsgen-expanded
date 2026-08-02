package com.zizazr.kjsgen.integration.jei.mixin;

// Draws kjsgen's buttons in JEI's own recipe-draw loop: after JEI has drawn each recipe (and its
// bookmark/transfer buttons), ask each recipe to draw its kjsgen buttons on top, positioned relative
// to the bookmark button. Only applied when JEI is present (see KjsJeiMixinPlugin).
//? if <1.21 {
import com.zizazr.kjsgen.integration.jei.KjsRecipeButtonHolder;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

// remap = false: target + @Inject method + @Shadow field are all JEI's own (not Minecraft), so keep
// them out of the MC refmap. MC calls in the injected body are handled by the normal jar reobfuscation.
@Mixin(value = RecipeGuiLayouts.class, remap = false)
public class RecipeGuiLayoutsMixin {
    @Shadow
    @Final
    private List<RecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

    @Inject(method = "draw", at = @At("TAIL"))
    private void kjsgen$drawButtons(GuiGraphics graphics, int mouseX, int mouseY,
                                    CallbackInfoReturnable<Optional<IRecipeLayoutDrawable<?>>> cir) {
        for (RecipeLayoutWithButtons<?> layout : recipeLayoutsWithButtons) {
            // RecipeLayoutWithButtons is a final record; the mixin makes it implement the interface at
            // runtime, so go through Object to bypass the compile-time "cannot convert" check.
            if ((Object) layout instanceof KjsRecipeButtonHolder holder) {
                holder.kjsgen$drawButtons(graphics, mouseX, mouseY);
            }
        }
    }
}
//?}
