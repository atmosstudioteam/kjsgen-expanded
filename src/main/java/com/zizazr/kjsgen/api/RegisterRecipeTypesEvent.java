package com.zizazr.kjsgen.api;

import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Addon API entry point. Addon mods register a listener during their own initialization;
 * kjsgen fires the event once during common setup, letting addons contribute their own
 * recipe types and codegen handlers — no changes to kjsgen itself required:
 *
 * <pre>{@code
 * // in the addon's initializer (ModInitializer / @Mod constructor), on either loader:
 * RegisterRecipeTypesEvent.register(e -> {
 *     e.registerCodegen("mymod:press", new MyPressCodegen());
 *     e.registerType(new RecipeTypeDefinition("mymod:press", "mymod", "mymod:press", ...));
 * });
 * }</pre>
 *
 * Simple types can skip the Java codegen entirely and use the built-in template codegen
 * ({@code "kjsgen:template"}) driven by a {@code template} parameter, or ship a JSON layout
 * in {@code assets/<ns>/kjsgen_layouts/}.
 *
 * <p>This used to extend the NeoForge {@code Event}/{@code IModBusEvent} and be posted on the
 * mod bus. It is now loader-agnostic (a plain listener list fired from common setup) so the same
 * addon code works on Fabric and NeoForge — an intentional, documented API change.
 */
public class RegisterRecipeTypesEvent {
    private static final List<Consumer<RegisterRecipeTypesEvent>> LISTENERS = new ArrayList<>();

    /** Register a listener; called by addon mods during their own init, before common setup. */
    public static void register(Consumer<RegisterRecipeTypesEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Fires the event for every registered listener. Called once by kjsgen during common setup. */
    public static void fire() {
        RegisterRecipeTypesEvent event = new RegisterRecipeTypesEvent();
        for (Consumer<RegisterRecipeTypesEvent> listener : LISTENERS) {
            listener.accept(event);
        }
    }

    /** Register a recipe type shown in the recipe type picker. */
    public void registerType(RecipeTypeDefinition definition) {
        RecipeTypeRegistry.register(definition);
    }

    /** Register a codegen handler referenced by {@link RecipeTypeDefinition#codegenId()}. */
    public void registerCodegen(String id, RecipeCodegen codegen) {
        CodegenRegistry.register(id, codegen);
    }
}
