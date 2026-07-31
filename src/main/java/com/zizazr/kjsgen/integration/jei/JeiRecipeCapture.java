package com.zizazr.kjsgen.integration.jei;

import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
//? if neoforge
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
//? if neoforge
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the JEI recipe currently shown under an "edit" button into a populated
 * {@link RecipeInstance}. The JEI category is mapped to a hand-authored kjsgen
 * {@link RecipeTypeDefinition} (via {@link JeiLayoutImporter#mappedTypeFor}); the
 * captured slots fill that type's keys by role in order — the i-th displayed
 * input/output/catalyst slot lines up with the i-th key of that role.
 *
 * <p>Items and fluids are read from what each slot is currently displaying;
 * Mekanism chemicals (and any other non-item/fluid ingredient) are left empty for
 * the user to fill in the editor.
 */
public final class JeiRecipeCapture {
    private JeiRecipeCapture() {
    }

    /** Captures the displayed recipe, or {@code null} when no kjsgen layout maps to its category. */
    @Nullable
    public static RecipeInstance capture(IRecipeLayoutDrawable<?> layout) {
        RecipeTypeDefinition type = JeiLayoutImporter.mappedTypeFor(layout).orElse(null);
        if (type == null) {
            return null;
        }
        // Collect the displayed contents per role, in JEI display order, keeping empty
        // slots so fixed grids (e.g. the 3x3 crafting grid) stay positionally aligned.
        List<SlotContent> inputs = new ArrayList<>();
        List<SlotContent> outputs = new ArrayList<>();
        List<SlotContent> catalysts = new ArrayList<>();
        for (IRecipeSlotView view : layout.getRecipeSlotsView().getSlotViews()) {
            // mirror JeiLayoutImporter.buildDefinition's filter so ordering stays aligned
            if (!(view instanceof IRecipeSlotDrawable)) {
                continue;
            }
            RecipeIngredientRole role = view.getRole();
            if (role == RecipeIngredientRole.RENDER_ONLY) {
                continue;
            }
            SlotContent content = contentOf(view);
            switch (role) {
                case OUTPUT -> outputs.add(content);
                case CATALYST -> catalysts.add(content);
                default -> inputs.add(content);
            }
        }

        RecipeInstance recipe = new RecipeInstance(type.id());
        assignRole(recipe, slotsByRole(type, SlotRole.INPUT), inputs);
        assignRole(recipe, slotsByRole(type, SlotRole.OUTPUT), outputs);
        assignRole(recipe, slotsByRole(type, SlotRole.CATALYST), catalysts);
        return recipe;
    }

    /**
     * Fills a role's slot definitions from the captured contents. A fixed slot takes
     * exactly one content (preserving position, so empty grid cells keep later slots
     * aligned); a list slot ("in" for shapeless) absorbs all remaining contents,
     * skipping empties, into its contiguous {@code key + index} entries.
     */
    private static void assignRole(RecipeInstance recipe, List<SlotDefinition> defs, List<SlotContent> contents) {
        int ci = 0;
        for (SlotDefinition def : defs) {
            if (def.list()) {
                int idx = 0;
                for (; ci < contents.size(); ci++) {
                    SlotContent content = contents.get(ci);
                    if (!content.isEmpty()) {
                        recipe.setListSlot(def.key(), idx++, content);
                    }
                }
            } else if (ci < contents.size()) {
                SlotContent content = contents.get(ci++);
                if (!content.isEmpty()) {
                    recipe.setSlot(def.key(), content);
                }
            }
        }
    }

    private static List<SlotDefinition> slotsByRole(RecipeTypeDefinition type, SlotRole role) {
        List<SlotDefinition> defs = new ArrayList<>();
        for (SlotDefinition slot : type.slots()) {
            if (slot.role() == role) {
                defs.add(slot);
            }
        }
        return defs;
    }

    /** Reads the item or fluid a slot is currently displaying; empty for anything else. */
    private static SlotContent contentOf(IRecipeSlotView view) {
        Optional<ItemStack> item = view.getDisplayedItemStack();
        if (item.isPresent() && !item.get().isEmpty()) {
            ItemStack stack = item.get();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return SlotContent.item(id, stack.getCount());
        }
        // Fluid capture uses JEI's NeoForge fluid ingredient type. JEI Fabric exposes fluids through a
        // different API, so on Fabric fluid slots are left empty for the user to fill in the editor
        // (items still capture normally). See docs/adding-recipe-types.md for the Fabric follow-up.
        //? if neoforge {
        Optional<FluidStack> fluid = view.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            FluidStack stack = fluid.get();
            String id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
            return SlotContent.fluid(id, stack.getAmount());
        }
        //?}
        return SlotContent.EMPTY;
    }
}
