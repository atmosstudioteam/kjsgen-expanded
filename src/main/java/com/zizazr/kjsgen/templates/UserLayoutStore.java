package com.zizazr.kjsgen.templates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Recipe type layouts stored in {@code <game dir>/kjsgen/layouts/*.json} —
 * mainly the ones imported from JEI plugins, so they survive restarts and can
 * be hand-edited (e.g. to fix the KubeJS {@code template} parameter guess).
 * Same JSON format as the bundled {@code kjsgen_layouts} assets.
 */
public final class UserLayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UserLayoutStore() {
    }

    public static Path layoutsDir() {
        return Platform.getGameFolder().resolve("kjsgen").resolve("layouts");
    }

    /** Persists a definition so it is available in future sessions (idempotent overwrite). */
    public static void save(RecipeTypeDefinition definition) {
        try {
            Path file = layoutsDir().resolve(definition.id().replace(':', '_').replace('/', '_') + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(definition.toJson()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KjsGen.LOGGER.error("kjsgen: failed to persist layout {}", definition.id(), e);
        }
    }

    /** Loads and registers all stored layouts; called once at client setup. */
    public static void loadAll() {
        Path dir = layoutsDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        int loaded = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    RecipeTypeRegistry.register(RecipeTypeDefinition.fromJson(JsonParser.parseString(text).getAsJsonObject()));
                    loaded++;
                } catch (Exception e) {
                    KjsGen.LOGGER.error("kjsgen: invalid stored layout {}", file, e);
                }
            }
        } catch (IOException e) {
            KjsGen.LOGGER.error("kjsgen: failed to list stored layouts", e);
        }
        if (loaded > 0) {
            KjsGen.LOGGER.info("kjsgen: loaded {} stored recipe layouts", loaded);
        }
    }
}
