package com.zizazr.kjsgen.templates;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import dev.architectury.platform.Platform;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads data-driven recipe type definitions from
 * {@code assets/<namespace>/kjsgen_layouts/*.json} of every loaded resource
 * pack / mod jar. This is the no-code half of the addon API: a mod (or a
 * resource pack) can add new recipe types with the built-in template codegen
 * without writing any Java.
 */
public final class JsonLayoutLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    public static final String FOLDER = "kjsgen_layouts";

    /**
     * Registers the layouts bundled inside our own mod jar, read straight from the jar's
     * filesystem instead of a {@link ResourceManager}. The reload-listener path only runs
     * on the client (it lives in {@code RegisterClientReloadListenersEvent}) and reads from
     * {@code assets/}, which a dedicated server never loads — so without this the server's
     * {@link RecipeTypeRegistry} would only hold the vanilla built-ins and export (which runs
     * server-side) would silently drop every Create/Mekanism recipe. Call this at common setup
     * so both physical sides have the same types.
     */
    public static void loadBundled() {
        // Architectury resolves the resource inside our own mod jar on both loaders. Our mod is
        // always present, so getMod never throws here; findResource is empty if the folder is absent.
        Optional<Path> resource = Platform.getMod(KjsGen.MODID).findResource("assets", KjsGen.MODID, FOLDER);
        if (resource.isEmpty()) {
            return;
        }
        Path dir = resource.get();
        if (!Files.isDirectory(dir)) {
            return;
        }
        int loaded = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    RecipeTypeRegistry.register(
                            RecipeTypeDefinition.fromJson(JsonParser.parseString(text).getAsJsonObject()));
                    loaded++;
                } catch (Exception e) {
                    KjsGen.LOGGER.error("Invalid bundled kjsgen layout {}", file, e);
                }
            }
        } catch (Exception e) {
            KjsGen.LOGGER.error("kjsgen: failed to list bundled layouts", e);
        }
        if (loaded > 0) {
            KjsGen.LOGGER.info("kjsgen loaded {} bundled JSON recipe layouts", loaded);
        }
    }

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> jsons = new HashMap<>();
        resourceManager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                jsons.put(location, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                KjsGen.LOGGER.error("Failed to read kjsgen layout {}", location, e);
            }
        });
        return jsons;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : jsons.entrySet()) {
            try {
                RecipeTypeDefinition definition = RecipeTypeDefinition.fromJson(entry.getValue());
                RecipeTypeRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                KjsGen.LOGGER.error("Invalid kjsgen layout {}", entry.getKey(), e);
            }
        }
        if (loaded > 0) {
            KjsGen.LOGGER.info("kjsgen loaded {} JSON recipe layouts", loaded);
        }
    }
}
