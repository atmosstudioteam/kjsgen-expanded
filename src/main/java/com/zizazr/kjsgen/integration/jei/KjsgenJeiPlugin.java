package com.zizazr.kjsgen.integration.jei;

import com.zizazr.kjsgen.KjsGen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The kjsgen JEI plugin: captures the JEI runtime (so the recipe importer can read other mods'
 * categories) and, on JEI 19 (1.21+), registers the per-recipe "edit"/"remove" buttons.
 *
 * <p>On JEI 19 the buttons are real recipe buttons ({@code IAdvancedRegistration#addRecipeButtonFactory}).
 * JEI 15 (1.20.1) has no button API, so there the buttons are drawn by {@link KjsJeiOverlay} (a
 * RENDER_POST hook that reads JEI's own bookmark-button position) — no plugin registration involved.
 *
 * <p>This class is only ever classloaded by JEI itself (via the {@code @JeiPlugin} annotation scan),
 * so the hard references to the JEI API are safe.
 */
@JeiPlugin
public class KjsgenJeiPlugin implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return KjsGen.rl("jei_plugin");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // On JEI 15 (1.20.1) there is no recipe-button API — the buttons are drawn by KjsJeiOverlay, so
        // nothing is registered here. On JEI 19 (1.21+) they are real recipe buttons, registered below.
        //? if >=1.21 {
        /*registration.addRecipeButtonFactory(new JeiEditButtonController.Factory());
        registration.addRecipeButtonFactory(new JeiDeleteButtonController.Factory());
        *///?}
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Nullable
    public static IJeiRuntime runtime() {
        return runtime;
    }
}
