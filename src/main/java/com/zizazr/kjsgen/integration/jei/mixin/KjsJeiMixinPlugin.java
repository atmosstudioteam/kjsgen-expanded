package com.zizazr.kjsgen.integration.jei.mixin;

// Applies the JEI recipe-button mixins only when JEI is actually installed. Checks for JEI's class as
// a classpath RESOURCE (not Class.forName) so we neither load the target early nor depend on the mod
// list being ready this early in startup. Loader-agnostic.
//? if <1.21 {
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class KjsJeiMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("kjsgen-mixin");
    private static final String JEI_MARKER = "mezz/jei/gui/recipes/RecipeLayoutWithButtons.class";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("kjsgen JEI mixin config loaded (package {})", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean jeiPresent = getClass().getClassLoader().getResource(JEI_MARKER) != null;
        LOGGER.info("kjsgen shouldApplyMixin target={} mixin={} jeiPresent={}",
                targetClassName, mixinClassName, jeiPresent);
        return jeiPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
//?}
