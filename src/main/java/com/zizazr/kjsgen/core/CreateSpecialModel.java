package com.zizazr.kjsgen.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage conventions for the two Create recipe types that need a non-standard
 * editor ({@link RecipeTypeDefinition#editorKind()}), shared by the vanilla UI
 * and the codegen handlers so both agree on where data lives inside a
 * {@link RecipeInstance}.
 *
 * <h2>Mechanical crafting ({@code "grid"})</h2>
 * A resizable crafting grid: {@code gridW}/{@code gridH} params (1..9) plus one
 * item slot per cell keyed {@code cell0..cell{W*H-1}} (row-major) and a single
 * {@code output} slot.
 *
 * <h2>Sequenced assembly ({@code "sequence"})</h2>
 * A {@code input} slot (the ingredient the sequence starts from), a
 * {@code transitional} item slot (the in-progress item Create carries between
 * stages) and a dynamic {@code output} result list (entries keyed
 * {@code output0}, {@code output1}, ...), each with a chance. The ordered stage
 * list lives in the {@code seqLen} param
 * (step count) with one step per index: its type in param {@code seq{i}} and its
 * optional ingredient in slot {@code seqItem{i}}.
 */
public final class CreateSpecialModel {
    private CreateSpecialModel() {
    }

    // ================================================================ grid

    public static final int GRID_MAX = 9;

    public static int gridW(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Math.max(1, Math.min(GRID_MAX, recipe.paramInt(type, "gridW", 3)));
    }

    public static int gridH(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Math.max(1, Math.min(GRID_MAX, recipe.paramInt(type, "gridH", 3)));
    }

    /**
     * Storage key for the cell at {@code (row, col)}. Keyed by absolute position
     * (not a flattened {@code row*width+col} index) so resizing the grid never
     * reshuffles the items the user already placed.
     */
    public static String cellKey(int row, int col) {
        return "cell_" + row + "_" + col;
    }

    /** A synthetic slot definition for one grid cell (items + tags, no count). */
    public static SlotDefinition cellSlot(int row, int col) {
        return new SlotDefinition(cellKey(row, col), SlotRole.INPUT, 0, 0,
                false, true, true, false, false, false);
    }

    // ============================================================ sequence

    /** One kind of stage that can appear in a sequenced-assembly chain. */
    public record StepType(String id, boolean hasItem, boolean fluid) {
    }

    /**
     * The Create in-world processing steps a sequenced assembly may use, in the
     * order shown in the "add stage" picker. {@code hasItem} steps carry an extra
     * ingredient slot (the deployed item / the filled fluid); the others act on
     * the transitional item alone.
     */
    public static final List<StepType> STEP_TYPES = List.of(
            new StepType("deploying", true, false),
            new StepType("pressing", false, false),
            new StepType("cutting", false, false),
            new StepType("filling", true, true)
    );

    public static StepType stepType(String id) {
        for (StepType st : STEP_TYPES) {
            if (st.id().equals(id)) {
                return st;
            }
        }
        return STEP_TYPES.get(0);
    }

    public static int seqLen(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Math.max(0, recipe.paramInt(type, "__seqLen", 0));
    }

    /** Step type id at index {@code i} ("deploying", "pressing", ...). */
    public static String stepTypeId(RecipeInstance recipe, RecipeTypeDefinition type, int i) {
        String id = recipe.param(type, "seq" + i);
        return id.isEmpty() ? STEP_TYPES.get(0).id() : id;
    }

    public static SlotContent stepItem(RecipeInstance recipe, int i) {
        return recipe.slot("seqItem" + i);
    }

    /** A synthetic slot definition for one step's ingredient (item/tag or fluid). */
    public static SlotDefinition stepItemSlot(int i, StepType st) {
        if (st.fluid()) {
            return new SlotDefinition("seqItem" + i, SlotRole.INPUT, 0, 0,
                    true, false, false, true, false, false);
        }
        return new SlotDefinition("seqItem" + i, SlotRole.INPUT, 0, 0,
                true, true, true, false, false, false);
    }

    /** Append a new stage of the given type at the end of the chain. */
    public static void addStep(RecipeInstance recipe, RecipeTypeDefinition type, String stepTypeId) {
        int n = seqLen(recipe, type);
        recipe.setParam("seq" + n, stepTypeId);
        recipe.setParam("__seqLen", Integer.toString(n + 1));
    }

    /** Remove the stage at {@code index}, shifting the rest down and recompacting slots. */
    public static void removeStep(RecipeInstance recipe, RecipeTypeDefinition type, int index) {
        int n = seqLen(recipe, type);
        if (index < 0 || index >= n) {
            return;
        }
        List<String> types = new ArrayList<>();
        List<SlotContent> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i == index) {
                continue;
            }
            types.add(stepTypeId(recipe, type, i));
            items.add(stepItem(recipe, i));
        }
        writeSteps(recipe, types, items);
    }

    /** Move the stage at {@code from} to {@code to}, shifting the others. */
    public static void moveStep(RecipeInstance recipe, RecipeTypeDefinition type, int from, int to) {
        int n = seqLen(recipe, type);
        if (from < 0 || from >= n || to < 0 || to >= n || from == to) {
            return;
        }
        List<String> types = new ArrayList<>();
        List<SlotContent> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            types.add(stepTypeId(recipe, type, i));
            items.add(stepItem(recipe, i));
        }
        String movedType = types.remove(from);
        SlotContent movedItem = items.remove(from);
        types.add(to, movedType);
        items.add(to, movedItem);
        writeSteps(recipe, types, items);
    }

    private static void writeSteps(RecipeInstance recipe, List<String> types, List<SlotContent> items) {
        int old = 0;
        // wipe every existing seq*/seqItem* entry
        while (!recipe.paramRaw("seq" + old).isEmpty() || !recipe.slot("seqItem" + old).isEmpty()) {
            recipe.setParam("seq" + old, "");
            recipe.setSlot("seqItem" + old, SlotContent.EMPTY);
            old++;
            if (old > 512) {
                break;
            }
        }
        for (int i = 0; i < types.size(); i++) {
            recipe.setParam("seq" + i, types.get(i));
            recipe.setSlot("seqItem" + i, items.get(i));
        }
        recipe.setParam("__seqLen", Integer.toString(types.size()));
    }

    /** Set the ingredient/fluid of the step at {@code index}. */
    public static void setStepItem(RecipeInstance recipe, int index, SlotContent content) {
        recipe.setSlot("seqItem" + index, content);
    }

    /** Change the step type at {@code index}, clearing its ingredient (kinds differ). */
    public static void setStepType(RecipeInstance recipe, int index, String stepTypeId) {
        recipe.setParam("seq" + index, stepTypeId);
        recipe.setSlot("seqItem" + index, SlotContent.EMPTY);
    }
}
