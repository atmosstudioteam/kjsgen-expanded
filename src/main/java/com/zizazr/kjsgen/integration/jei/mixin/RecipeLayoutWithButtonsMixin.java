package com.zizazr.kjsgen.integration.jei.mixin;

// Makes kjsgen's edit/remove buttons real JEI buttons on each recipe: creates two GuiIconButtons when
// the recipe's category maps to a kjsgen type, splices their input handlers into JEI's own
// CombinedInputHandler (so JEI routes clicks to them exactly like the bookmark button), and draws them
// stacked above JEI's bookmark button. Targets JEI 15's internal record; only applied when JEI is
// present (see KjsJeiMixinPlugin).
//? if <1.21 {
import com.zizazr.kjsgen.integration.jei.KjsJeiButtonFactory;
import com.zizazr.kjsgen.integration.jei.KjsRecipeButtonHolder;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.recipes.RecipeBookmarkButton;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// remap = false: the target and its @Inject methods are JEI's own (not Minecraft), so they must NOT
// go through the MC obfuscation refmap. MC calls in the method bodies are remapped by the normal jar
// reobfuscation, so they still resolve at runtime.
@Mixin(value = RecipeLayoutWithButtons.class, remap = false)
public abstract class RecipeLayoutWithButtonsMixin implements KjsRecipeButtonHolder {
    @Unique
    private RecipeBookmarkButton kjsgen$bookmark;
    @Unique
    private GuiIconButton kjsgen$edit;
    @Unique
    private GuiIconButton kjsgen$remove;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kjsgen$createButtons(IRecipeLayoutDrawable<?> recipeLayout, RecipeTransferButton transferButton,
                                      RecipeBookmarkButton bookmarkButton, CallbackInfo ci) {
        kjsgen$bookmark = bookmarkButton;
        kjsgen$edit = KjsJeiButtonFactory.editButton(recipeLayout);
        kjsgen$remove = KjsJeiButtonFactory.removeButton(recipeLayout);
    }

    @Inject(method = "createUserInputHandler", at = @At("RETURN"), cancellable = true)
    private void kjsgen$addInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        if (kjsgen$edit == null && kjsgen$remove == null) {
            return;
        }
        // Our buttons first so they win the click over the recipe area behind them, then JEI's own chain.
        List<IUserInputHandler> handlers = new ArrayList<>();
        if (kjsgen$edit != null) {
            handlers.add(kjsgen$edit.createInputHandler());
        }
        if (kjsgen$remove != null) {
            handlers.add(kjsgen$remove.createInputHandler());
        }
        handlers.add(cir.getReturnValue());
        cir.setReturnValue(new CombinedInputHandler("kjsgen", handlers));
    }

    @Override
    public void kjsgen$drawButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        if (kjsgen$bookmark == null || (kjsgen$edit == null && kjsgen$remove == null)) {
            return;
        }
        // Stack upward from JEI's bookmark button so the column reads [edit][remove][bookmark]. Use the
        // bookmark button's REAL drawn area (absolute screen coords) — the layout's
        // getRecipeBookmarkButtonArea() is recipe-relative and put the buttons in the screen corner.
        ImmutableRect2i bookmark = ((GuiIconToggleButtonAccessor) (Object) kjsgen$bookmark).kjsgen$getArea();
        int x = bookmark.getX();
        int w = bookmark.getWidth();
        int h = bookmark.getHeight();
        int y = bookmark.getY() - h - 1;
        if (kjsgen$remove != null) {
            kjsgen$remove.updateBounds(new Rect2i(x, y, w, h));
            kjsgen$remove.render(graphics, mouseX, mouseY, 0.0f);
            y -= h + 1;
        }
        if (kjsgen$edit != null) {
            kjsgen$edit.updateBounds(new Rect2i(x, y, w, h));
            kjsgen$edit.render(graphics, mouseX, mouseY, 0.0f);
        }
    }
}
//?}
