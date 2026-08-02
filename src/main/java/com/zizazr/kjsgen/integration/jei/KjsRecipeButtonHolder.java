package com.zizazr.kjsgen.integration.jei;

// Duck-type interface the RecipeLayoutWithButtons mixin implements, so the RecipeGuiLayouts mixin can
// ask each on-screen recipe to draw its kjsgen buttons. MUST live OUTSIDE the mixin package
// (com.zizazr.kjsgen.integration.jei.mixin): Mixin forbids referencing classes in a mixin-owned
// package directly, and both the mixins and the injected target code reference this at runtime.
//? if <1.21 {
import net.minecraft.client.gui.GuiGraphics;

public interface KjsRecipeButtonHolder {
    /** Positions (above JEI's bookmark button) and renders this recipe's kjsgen edit/remove buttons. */
    void kjsgen$drawButtons(GuiGraphics graphics, int mouseX, int mouseY);
}
//?}
