package com.zizazr.kjsgen.integration.jei;

import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.LayoutDecoration;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;
import com.zizazr.kjsgen.templates.UserLayoutStore;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
//? if neoforge
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the recipe categories other mods registered in their JEI plugins and
 * converts them into kjsgen {@link RecipeTypeDefinition}s: for every category
 * a sample recipe is laid out through JEI's own
 * {@link IRecipeManager#createRecipeLayoutDrawable}, which runs the mod's real
 * {@code IRecipeCategory#setRecipe} logic — so slot positions and roles match
 * what the player sees in JEI exactly.
 *
 * The KubeJS syntax for imported types cannot be known from JEI, so each
 * imported type gets an editable {@code template} parameter with a best-guess
 * default ({@code event.recipes.<mod>.<type>([{outputs}], [{inputs}])}).
 */
public final class JeiLayoutImporter {
    /** Namespaces whose categories are skipped: covered by builtins or not real recipes. */
    private static final Set<String> SKIPPED_NAMESPACES = Set.of("minecraft", "jei");

    public record ImportReport(int imported, int skipped, List<String> errors) {
    }

    private JeiLayoutImporter() {
    }

    /** Whether the JEI runtime is up (JEI installed and world loaded). */
    public static boolean isRuntimeAvailable() {
        return KjsgenJeiPlugin.runtime() != null;
    }

    /** Imports all foreign JEI categories, registers them and persists them as JSON layouts. */
    public static ImportReport importAll() {
        IJeiRuntime runtime = KjsgenJeiPlugin.runtime();
        if (runtime == null) {
            return new ImportReport(0, 0, List.of("JEI runtime not available"));
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            String namespace = category.getRecipeType().getUid().getNamespace();
            if (SKIPPED_NAMESPACES.contains(namespace)) {
                skipped++;
                continue;
            }
            try {
                Optional<RecipeTypeDefinition> definition = importCategory(recipeManager, category, emptyFocus);
                if (definition.isPresent()) {
                    RecipeTypeRegistry.register(definition.get());
                    UserLayoutStore.save(definition.get());
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                KjsGen.LOGGER.error("kjsgen: failed to import JEI category {}", category.getRecipeType().getUid(), e);
                errors.add(category.getRecipeType().getUid().toString());
            }
        }
        KjsGen.LOGGER.info("kjsgen: imported {} JEI layouts ({} skipped)", imported, skipped);
        return new ImportReport(imported, skipped, errors);
    }

    private static <T> Optional<RecipeTypeDefinition> importCategory(IRecipeManager recipeManager,
                                                                     IRecipeCategory<T> category,
                                                                     IFocusGroup emptyFocus) {
        // a sample recipe is required to run the category's layout code
        Optional<T> sample = recipeManager.createRecipeLookup(category.getRecipeType()).get().findFirst();
        if (sample.isEmpty()) {
            return Optional.empty();
        }
        Optional<IRecipeLayoutDrawable<T>> layout =
                recipeManager.createRecipeLayoutDrawable(category, sample.get(), emptyFocus);
        if (layout.isEmpty()) {
            return Optional.empty();
        }
        return buildDefinition(category, layout.get());
    }

    /**
     * Maps a laid-out JEI recipe onto the hand-authored kjsgen layout registered for its
     * category — looked up by the layout's {@code jeiCategory} field (e.g. "create:pressing").
     * Returns empty when no layout implements that category, so the "Edit in kjsgen" button
     * only appears for recipes we can actually generate KubeJS for (no on-the-fly generation).
     */
    public static <T> Optional<RecipeTypeDefinition> mappedTypeFor(IRecipeLayoutDrawable<T> layout) {
        String jeiUid = layout.getRecipeCategory().getRecipeType().getUid().toString();
        Optional<RecipeTypeDefinition> mapped = RecipeTypeRegistry.getByJeiCategory(jeiUid);
        if (mapped.isEmpty()) {
            return mapped;
        }
        // A couple of vanilla JEI categories host two kjsgen types each (crafting =
        // shaped|shapeless, smithing = transform|trim). Refine by the actual recipe
        // subtype so the editor opens on the right one; fall back to the default match.
        String refinedId = refineSharedCategory(jeiUid, layout.getRecipe());
        if (refinedId != null) {
            Optional<RecipeTypeDefinition> refined = RecipeTypeRegistry.get(refinedId);
            if (refined.isPresent()) {
                return refined;
            }
        }
        return mapped;
    }

    /** Picks the specific kjsgen type id for JEI categories that back two of them, else null. */
    private static String refineSharedCategory(String jeiUid, Object recipe) {
        Object value = recipe instanceof RecipeHolder<?> holder ? holder.value() : recipe;
        return switch (jeiUid) {
            case "minecraft:crafting" ->
                    value instanceof ShapelessRecipe ? "kjsgen:shapeless" : "kjsgen:shaped";
            case "minecraft:smithing" ->
                    value instanceof SmithingTrimRecipe ? "kjsgen:smithing_trim" : "kjsgen:smithing_transform";
            default -> null;
        };
    }

    /** Derives a kjsgen {@link RecipeTypeDefinition} from a laid-out JEI recipe (slot positions + roles). */
    public static <T> Optional<RecipeTypeDefinition> buildDefinition(IRecipeCategory<T> category,
                                                                     IRecipeLayoutDrawable<T> layout) {
        List<IRecipeSlotView> slotViews = layout.getRecipeSlotsView().getSlotViews();
        List<SlotDefinition> slots = new ArrayList<>();
        String iconItem = "";
        int inputs = 0;
        int outputs = 0;
        int catalysts = 0;
        for (IRecipeSlotView slotView : slotViews) {
            // positions only exist on the drawable implementation
            if (!(slotView instanceof IRecipeSlotDrawable drawable)) {
                continue;
            }
            RecipeIngredientRole jeiRole = slotView.getRole();
            if (jeiRole == RecipeIngredientRole.RENDER_ONLY) {
                continue;
            }
            SlotRole role = switch (jeiRole) {
                case OUTPUT -> SlotRole.OUTPUT;
                case CATALYST -> SlotRole.CATALYST;
                default -> SlotRole.INPUT;
            };
            String key = switch (role) {
                case INPUT -> "in" + inputs++;
                case OUTPUT -> (outputs++ == 0 ? "output" : "output" + outputs);
                case CATALYST -> "catalyst" + catalysts++;
            };

            Rect2i area = drawable.getAreaIncludingBackground();
            // normalize to our 18x18 slot boxes (JEI backgrounds are 18x18, bare ingredients 16x16)
            int x = area.getX() - (18 - area.getWidth()) / 2;
            int y = area.getY() - (18 - area.getHeight()) / 2;

            boolean hasItems = slotView.getItemStacks().findAny().isPresent();
            // JEI Fabric exposes fluids through a different API; on Fabric we treat slots as item-only
            // when importing a category layout (the editor still lets the user enable fluids per slot).
            //? if neoforge {
            boolean hasFluids = slotView.getIngredients(NeoForgeTypes.FLUID_STACK).findAny().isPresent();
            //?}
            //? if fabric {
            /*boolean hasFluids = false;*/
            //?}
            if (!hasItems && !hasFluids) {
                hasItems = true; // empty sample slot: assume items
            }
            boolean required = (role == SlotRole.OUTPUT && outputs == 1) || (role == SlotRole.INPUT && inputs == 1);
            slots.add(new SlotDefinition(key, role, x, y, required,
                    hasItems,
                    role != SlotRole.OUTPUT, // tags make sense for inputs/catalysts
                    hasFluids,
                    true,
                    role == SlotRole.OUTPUT));

            if (iconItem.isEmpty() && role == SlotRole.OUTPUT) {
                iconItem = slotView.getDisplayedItemStack().map(JeiLayoutImporter::stackId).orElse("");
            }
        }
        if (slots.isEmpty()) {
            return Optional.empty(); // info-style category, nothing editable
        }
        if (iconItem.isEmpty()) {
            iconItem = slotViews.stream()
                    .flatMap(IRecipeSlotView::getItemStacks)
                    .findFirst()
                    .map(JeiLayoutImporter::stackId)
                    .orElse("minecraft:crafting_table");
        }

        // shift everything into positive canvas coordinates
        int minX = slots.stream().mapToInt(SlotDefinition::x).min().orElse(0);
        int minY = slots.stream().mapToInt(SlotDefinition::y).min().orElse(0);
        if (minX < 0 || minY < 0) {
            int dx = Math.max(0, -minX);
            int dy = Math.max(0, -minY);
            slots = slots.stream()
                    .map(s -> new SlotDefinition(s.key(), s.role(), s.x() + dx, s.y() + dy, s.required(),
                            s.allowsItem(), s.allowsTag(), s.allowsFluid(), s.allowsChemical(),
                            s.allowsCount(), s.allowsChance(), s.list()))
                    .toList();
        }

        int canvasWidth;
        int canvasHeight;
        try {
            canvasWidth = category.getWidth();
            canvasHeight = category.getHeight();
        } catch (Exception e) {
            canvasWidth = 0;
            canvasHeight = 0;
        }
        int slotsMaxX = slots.stream().mapToInt(s -> s.x() + 18).max().orElse(18);
        int slotsMaxY = slots.stream().mapToInt(s -> s.y() + 18).max().orElse(18);
        canvasWidth = Math.max(canvasWidth, slotsMaxX);
        canvasHeight = Math.max(canvasHeight, slotsMaxY);

        var uid = category.getRecipeType().getUid();
        String template = "event.recipes." + uid.getNamespace() + "." + uid.getPath()
                + "([{outputs}], [{inputs}])";

        return Optional.of(new RecipeTypeDefinition(
                uid.toString(),
                uid.getNamespace(),
                iconItem,
                canvasWidth,
                canvasHeight,
                List.copyOf(slots),
                List.of(arrowBetween(slots)),
                List.of(ParameterDefinition.ofString("template", template)),
                "kjsgen:template",
                uid.getNamespace()
        ));
    }

    /** A JEI-style arrow centered in the gap between inputs and outputs (best effort). */
    private static LayoutDecoration arrowBetween(List<SlotDefinition> slots) {
        int inputsRight = slots.stream()
                .filter(s -> s.role() != SlotRole.OUTPUT)
                .mapToInt(s -> s.x() + 18).max().orElse(0);
        int outputsLeft = slots.stream()
                .filter(s -> s.role() == SlotRole.OUTPUT)
                .mapToInt(SlotDefinition::x).min().orElse(inputsRight + 28);
        int centerY = (int) slots.stream()
                .mapToInt(SlotDefinition::y).average().orElse(0);
        int x = inputsRight + Math.max(0, (outputsLeft - inputsRight - 24) / 2);
        return LayoutDecoration.arrow(x, centerY + 1);
    }

    private static String stackId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
