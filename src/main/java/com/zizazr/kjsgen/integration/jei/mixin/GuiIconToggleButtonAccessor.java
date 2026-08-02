package com.zizazr.kjsgen.integration.jei.mixin;

// Exposes the private, absolute screen `area` of JEI's toggle buttons (the bookmark button is one), so
// we can stack our edit/remove buttons exactly on top of it. getRecipeBookmarkButtonArea() on the
// layout returns recipe-relative coords (our buttons ended up in the screen corner); the button's own
// `area` is the real drawn position.
//? if <1.21 {
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.GuiIconToggleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiIconToggleButton.class, remap = false)
public interface GuiIconToggleButtonAccessor {
    @Accessor("area")
    ImmutableRect2i kjsgen$getArea();
}
//?}
