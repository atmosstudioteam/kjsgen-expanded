package com.zizazr.kjsgen.integration.jei;

import com.zizazr.kjsgen.KjsGen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Captures the JEI runtime so {@link JeiLayoutImporter} can read the recipe
 * categories (and their layouts) that other mods register in their JEI
 * plugins.
 *
 * This class is only ever classloaded by JEI itself (via the {@code @JeiPlugin}
 * annotation scan) or from code paths guarded by a "jei is loaded" check, so
 * the hard references to the JEI API are safe.
 */
@JeiPlugin
public class KjsgenJeiPlugin implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(KjsGen.MODID, "jei_plugin");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new JeiEditButtonController.Factory());
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
