package com.zizazr.kjsgen;

import com.mojang.logging.LogUtils;
import com.zizazr.kjsgen.api.RegisterRecipeTypesEvent;
import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.integration.net.KjsGenNet;
import com.zizazr.kjsgen.integration.net.ServerProjectStore;
import com.zizazr.kjsgen.templates.BuiltinRecipeTypes;
import com.zizazr.kjsgen.templates.JsonLayoutLoader;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;

/**
 * KubeJS Generator — a dev tool that provides a JEI-like visual recipe editor
 * and exports the created recipes as KubeJS scripts.
 *
 * <p>Loader-agnostic entry point: on NeoForge this is the {@code @Mod} class (constructed by FML),
 * on Fabric it is the {@code ModInitializer}. Both funnel into {@link #init()}, which wires up the
 * cross-loader events via the Architectury API.
 */
//? if neoforge {
/*@net.neoforged.fml.common.Mod(KjsGen.MODID)
*///?}
//? if forge {
@net.minecraftforge.fml.common.Mod(KjsGen.MODID)
//?}
public class KjsGen
        //? if fabric
        /*implements net.fabricmc.api.ModInitializer*/
{
    public static final String MODID = "kjsgen";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Builds a {@link net.minecraft.resources.ResourceLocation} in the kjsgen namespace.
     * The {@code fromNamespaceAndPath} factory is 1.21+, so this branches to the public
     * {@code new ResourceLocation(namespace, path)} constructor on 1.20.1 / 1.19.2.
     */
    public static net.minecraft.resources.ResourceLocation rl(String path) {
        //? if >=1.21 {
        /*return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, path);
        *///?}
        //? if <1.21 {
        return new net.minecraft.resources.ResourceLocation(MODID, path);
        //?}
    }

    //? if neoforge {
    /*public KjsGen(net.neoforged.bus.api.IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        init();
    }
    *///?}

    //? if forge {
    public KjsGen() {
        init();
        // Forge allows only one @Mod class per mod (unlike NeoForge/Fabric's separate client
        // entrypoint), so trigger the client bootstrap here, classloaded on the client dist only.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> KjsGenClient::initClient);
    }
    //?}

    //? if fabric {
    /*@Override
    public void onInitialize() {
        init();
    }*/
    //?}

    /** Common bootstrap, runs once on both loaders. */
    private static void init() {
        CodegenRegistry.registerBuiltins();
        BuiltinRecipeTypes.register();
        // Multiplayer sync: register the payload receivers (cross-loader; both sides).
        KjsGenNet.register();
        // Server-side viewer cleanup when a player disconnects.
        PlayerEvent.PLAYER_QUIT.register(ServerProjectStore::onLogout);
        // Register the bundled/addon recipe types during the common-setup phase (after every
        // mod's initializer has run, so addon RegisterRecipeTypesEvent listeners are already in).
        LifecycleEvent.SETUP.register(KjsGen::commonSetup);
    }

    private static void commonSetup() {
        // Register the bundled JSON recipe types (Create/Mekanism layouts) on both physical sides.
        // The client also loads these via a resource-reload listener, but a dedicated server never
        // loads assets/, so without this the server registry would only hold the vanilla built-ins
        // and server-side export would silently drop every non-vanilla recipe.
        JsonLayoutLoader.loadBundled();
        // Let addon mods contribute their own recipe types and codegen handlers.
        RegisterRecipeTypesEvent.fire();
        if (!isKubeJsLoaded()) {
            LOGGER.warn("KubeJS is not installed. Recipes can still be created and saved, "
                    + "but the exported scripts will only take effect once KubeJS is added to the instance.");
        }
    }

    public static boolean isKubeJsLoaded() {
        return Platform.isModLoaded("kubejs");
    }

    public static boolean isJeiLoaded() {
        return Platform.isModLoaded("jei");
    }
}
