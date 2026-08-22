package com.zizazr.kjsgen.codegen;

import com.zizazr.kjsgen.codegen.handlers.BrewingRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.CookingRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.CreateMechanicalCraftingCodegen;
import com.zizazr.kjsgen.codegen.handlers.CreateSequencedAssemblyCodegen;
import com.zizazr.kjsgen.codegen.handlers.ModernIndustrializationRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.ShapedRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.ShapelessRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.SmithingRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.SpectrumRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.StonecuttingRecipeCodegen;
import com.zizazr.kjsgen.codegen.handlers.TemplateRecipeCodegen;
import com.zizazr.kjsgen.templates.IntegrationRecipeTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of codegen handlers keyed by the {@code codegenId} referenced from
 * {@link com.zizazr.kjsgen.core.RecipeTypeDefinition}.
 */
public final class CodegenRegistry {
    private static final Map<String, RecipeCodegen> HANDLERS = new LinkedHashMap<>();

    private CodegenRegistry() {
    }

    public static synchronized void register(String id, RecipeCodegen codegen) {
        HANDLERS.put(id, codegen);
    }

    public static Optional<RecipeCodegen> get(String id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }

    public static void registerBuiltins() {
        register("kjsgen:shaped", new ShapedRecipeCodegen());
        register("kjsgen:shapeless", new ShapelessRecipeCodegen());
        register("kjsgen:smelting", new CookingRecipeCodegen("smelting", 200));
        register("kjsgen:blasting", new CookingRecipeCodegen("blasting", 100));
        register("kjsgen:smoking", new CookingRecipeCodegen("smoking", 100));
        register("kjsgen:campfire_cooking", new CookingRecipeCodegen("campfireCooking", 600));
        register("kjsgen:stonecutting", new StonecuttingRecipeCodegen());
        register("kjsgen:smithing_transform", new SmithingRecipeCodegen(false));
        register("kjsgen:smithing_trim", new SmithingRecipeCodegen(true));
        register("kjsgen:brewing", new BrewingRecipeCodegen());
        // Create recipe types that need a custom generator (variable grid / stage chain).
        register("kjsgen:create_mechanical_crafting", new CreateMechanicalCraftingCodegen());
        register("kjsgen:create_sequenced_assembly", new CreateSequencedAssemblyCodegen());
        // Modern Industrialization and Spectrum need custom codegen because their
        // recipe formats cannot be represented by one template string.
        register("kjsgen:modern_industrialization", new ModernIndustrializationRecipeCodegen());
        register("kjsgen:spectrum", new SpectrumRecipeCodegen());
        // Data-driven codegen for addon types declared purely in JSON.
        register("kjsgen:template", new TemplateRecipeCodegen());
        // Built-in UI layouts for MI and Spectrum (gated by requiresMod).
        IntegrationRecipeTypes.register();
    }
}
