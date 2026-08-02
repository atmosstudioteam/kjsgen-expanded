package com.zizazr.kjsgen.integration.jei;

// JEI 15 (1.20.1) helper: builds the actual JEI button objects (GuiIconButton — the same widget class
// JEI's own bookmark/transfer buttons use) for a recipe, and captures the shown recipe into a
// RecipeInstance. The buttons are wired into JEI's render + input flow by the mixins in ../mixin, so
// they behave exactly like JEI's native recipe buttons. Only classloaded when JEI is present.
//? if <1.21 {
import com.zizazr.kjsgen.KjsGenClient;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.elements.GuiIconButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class KjsJeiButtonFactory {
    private KjsJeiButtonFactory() {
    }

    /** A native JEI "edit in kjsgen" button for this recipe, or null if the category is not mapped. */
    @Nullable
    public static <T> GuiIconButton editButton(IRecipeLayoutDrawable<T> layout) {
        IRecipeCategory<T> category = layout.getRecipeCategory();
        T recipe = layout.getRecipe();
        RecipeTypeDefinition type = mappedTypeFor(category, recipe);
        com.zizazr.kjsgen.KjsGen.LOGGER.info("kjsgen editButton category={} mapped={}",
                category.getRecipeType().getUid(), type != null ? type.id() : "<none>");
        if (type == null) {
            return null;
        }
        IDrawable icon = new KjsEditIcon("jei_edit.png", 16);
        return new GuiIconButton(icon, button -> {
            RecipeInstance captured = capture(type, layout.getRecipeSlotsView());
            if (captured != null) {
                KjsGenClient.openEditorWithCapturedRecipe(captured);
            }
        });
    }

    /** A native JEI "remove with kjsgen" button, or null if not mapped or the recipe has no id. */
    @Nullable
    public static <T> GuiIconButton removeButton(IRecipeLayoutDrawable<T> layout) {
        IRecipeCategory<T> category = layout.getRecipeCategory();
        T recipe = layout.getRecipe();
        if (mappedTypeFor(category, recipe) == null) {
            return null;
        }
        ResourceLocation id = registryId(category, recipe);
        if (id == null) {
            return null;
        }
        IDrawable icon = new KjsEditIcon("jei_delete.png", 8);
        String idStr = id.toString();
        return new GuiIconButton(icon, button -> KjsGenClient.openRemovalsForRecipeId(idStr));
    }

    @Nullable
    private static <T> ResourceLocation registryId(IRecipeCategory<T> category, T recipe) {
        try {
            return category.getRegistryName(recipe);
        } catch (Exception e) {
            return null; // some categories throw for recipes without a real registry id
        }
    }

    // ------------------------------------------------------------------ category -> kjsgen type

    @Nullable
    private static <T> RecipeTypeDefinition mappedTypeFor(IRecipeCategory<T> category, T recipe) {
        String uid = category.getRecipeType().getUid().toString();
        Optional<RecipeTypeDefinition> mapped = RecipeTypeRegistry.getByJeiCategory(uid);
        if (mapped.isEmpty()) {
            return null;
        }
        String refinedId = refineSharedCategory(uid, recipe);
        if (refinedId != null) {
            Optional<RecipeTypeDefinition> refined = RecipeTypeRegistry.get(refinedId);
            if (refined.isPresent()) {
                return refined.get();
            }
        }
        return mapped.get();
    }

    @Nullable
    private static String refineSharedCategory(String jeiUid, Object recipe) {
        // 1.20.1 hands JEI the recipe object directly (no RecipeHolder wrapper like 1.21+).
        return switch (jeiUid) {
            case "minecraft:crafting" ->
                    recipe instanceof ShapelessRecipe ? "kjsgen:shapeless" : "kjsgen:shaped";
            case "minecraft:smithing" ->
                    recipe instanceof SmithingTrimRecipe ? "kjsgen:smithing_trim" : "kjsgen:smithing_transform";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ capture (items only on JEI 15)

    @Nullable
    private static RecipeInstance capture(RecipeTypeDefinition type, IRecipeSlotsView slotsView) {
        List<SlotContent> inputs = new ArrayList<>();
        List<SlotContent> outputs = new ArrayList<>();
        List<SlotContent> catalysts = new ArrayList<>();
        for (IRecipeSlotView view : slotsView.getSlotViews()) {
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

    private static SlotContent contentOf(IRecipeSlotView view) {
        Optional<ItemStack> item = view.getDisplayedItemStack();
        if (item.isPresent() && !item.get().isEmpty()) {
            ItemStack stack = item.get();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return SlotContent.item(id, stack.getCount());
        }
        return SlotContent.EMPTY;
    }
}
//?}
